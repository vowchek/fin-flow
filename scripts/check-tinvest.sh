#!/bin/sh
if [ -z "$TINVEST_TOKEN" ]; then
  echo TOKEN_EMPTY
  exit 1
fi
echo TOKEN_SET

# Auth check ignoring CA (only to validate token). App will use Java truststore.
wget --no-check-certificate -qO /tmp/ti.json \
  --header="Authorization: Bearer $TINVEST_TOKEN" \
  --header="Content-Type: application/json" \
  --post-data='{}' \
  'https://invest-public-api.tinkoff.ru/rest/tinkoff.public.invest.api.contract.v1.UsersService/GetInfo'
echo "GetInfo_insecure_wget=$?"
echo "GetInfo_bytes=$(wc -c < /tmp/ti.json 2>/dev/null || echo 0)"
head -c 280 /tmp/ti.json; echo

cat > /tmp/TiCheck.java <<'JAVA'
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TiCheck {
  public static void main(String[] args) throws Exception {
    String token = System.getenv("TINVEST_TOKEN");
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("https://invest-public-api.tinkoff.ru/rest/tinkoff.public.invest.api.contract.v1.UsersService/GetInfo"))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    System.out.println("JavaGetInfo_status=" + resp.statusCode());
    String body = resp.body() == null ? "" : resp.body();
    System.out.println("JavaGetInfo_bytes=" + body.length());
    System.out.println(body.substring(0, Math.min(240, body.length())));
  }
}
JAVA

javac /tmp/TiCheck.java
java -cp /tmp TiCheck
