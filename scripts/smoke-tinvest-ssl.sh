#!/bin/sh
# Quick SSL+auth smoke for T-Invest from running api image context
set -e
cat > /tmp/Smoke.java <<'JAVA'
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;

public class Smoke {
  public static void main(String[] args) throws Exception {
    String token = System.getenv("TINVEST_TOKEN");
    KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
    try (InputStream in = new java.io.FileInputStream(System.getProperty("java.home") + "/lib/security/cacerts")) {
      ts.load(in, "changeit".toCharArray());
    }
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    for (String res : new String[]{"/certs/russian_trusted_root_ca.pem", "/certs/russian_trusted_sub_ca.pem"}) {
      try (InputStream in = Smoke.class.getResourceAsStream(res)) {
        if (in == null) continue;
        int i = 0;
        for (Object c : cf.generateCertificates(in)) {
          ts.setCertificateEntry(res + i++, (X509Certificate) c);
        }
      }
    }
    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ts);
    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(null, tmf.getTrustManagers(), null);
    HttpClient client = HttpClient.newBuilder().sslContext(ctx).connectTimeout(Duration.ofSeconds(15)).build();
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("https://invest-public-api.tinkoff.ru/rest/tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument"))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"SBER\",\"instrumentKind\":\"INSTRUMENT_TYPE_SHARE\"}"))
        .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    System.out.println("status=" + resp.statusCode());
    String body = resp.body();
    System.out.println("bytes=" + body.length());
    System.out.println("hasSber=" + body.contains("\"ticker\":\"SBER\""));
  }
}
JAVA
