#!/bin/sh
# Probe T-Invest readonly endpoints useful for fin-flow catalog import.
WGET="wget --no-check-certificate -qO-"
AUTH="Authorization: Bearer $TINVEST_TOKEN"
CT="Content-Type: application/json"
BASE=https://invest-public-api.tinkoff.ru/rest

if [ -z "$TINVEST_TOKEN" ]; then
  echo TOKEN_EMPTY
  exit 1
fi

post() {
  name="$1"
  path="$2"
  body="$3"
  out="/tmp/ti_$name.json"
  code_file="/tmp/ti_$name.code"
  # busybox wget has no --server-response easily; just body
  if $WGET --header="$AUTH" --header="$CT" --post-data="$body" "$BASE$path" >"$out" 2>/tmp/ti_$name.err; then
    bytes=$(wc -c <"$out")
    echo "OK $name bytes=$bytes"
    head -c 220 "$out"; echo
    return 0
  else
    echo "FAIL $name"
    head -c 200 /tmp/ti_$name.err; echo
    head -c 200 "$out" 2>/dev/null; echo
    return 1
  fi
}

echo "=== Users ==="
post getinfo /tinkoff.public.invest.api.contract.v1.UsersService/GetInfo '{}'

echo "=== Find SBER share ==="
post find_sber /tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument \
  '{"query":"SBER","instrumentKind":"INSTRUMENT_TYPE_SHARE"}'

# extract uid for SBER TQBR-ish
UID=$(sed 's/{"figi"/\n{"figi"/g' /tmp/ti_find_sber.json 2>/dev/null | grep '"ticker":"SBER"' | head -1 | sed -n 's/.*"uid":"\([^"]*\)".*/\1/p')
if [ -z "$UID" ]; then
  UID=$(sed -n 's/.*"uid":"\([^"]*\)".*/\1/p' /tmp/ti_find_sber.json | head -1)
fi
echo "SBER_uid_len=${#UID}"

if [ -n "$UID" ]; then
  FROM=$(date -u -d '10 years ago' +%Y-%m-%dT00:00:00Z 2>/dev/null || echo '2016-01-01T00:00:00Z')
  TO=$(date -u +%Y-%m-%dT23:59:59Z)

  echo "=== GetDividends SBER ==="
  post div_sber /tinkoff.public.invest.api.contract.v1.InstrumentsService/GetDividends \
    "{\"instrumentId\":\"$UID\",\"from\":\"$FROM\",\"to\":\"$TO\"}"
  echo "dividendNet_count=$(grep -o '"dividendNet"' /tmp/ti_div_sber.json | wc -l)"

  echo "=== ShareBy uid ==="
  post share_by /tinkoff.public.invest.api.contract.v1.InstrumentsService/ShareBy \
    "{\"idType\":\"INSTRUMENT_ID_TYPE_UID\",\"id\":\"$UID\"}"
fi

echo "=== Find renamed/problematic tickers ==="
for q in YDEX T OZON X5 HEAD MDMG; do
  post "find_$q" /tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument \
    "{\"query\":\"$q\",\"instrumentKind\":\"INSTRUMENT_TYPE_SHARE\"}"
  cnt=$(grep -o '"ticker":"' /tmp/ti_find_$q.json | wc -l)
  echo "find_$q tickers~$cnt"
done

echo "=== Bond coupons sample (OFZ SU26238RMFS4) ==="
post find_bond /tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument \
  '{"query":"SU26238RMFS4","instrumentKind":"INSTRUMENT_TYPE_BOND"}'
BUID=$(sed -n 's/.*"uid":"\([^"]*\)".*/\1/p' /tmp/ti_find_bond.json | head -1)
echo "bond_uid_len=${#BUID}"
if [ -n "$BUID" ]; then
  FROM=$(date -u -d '2 years ago' +%Y-%m-%dT00:00:00Z 2>/dev/null || echo '2024-01-01T00:00:00Z')
  TO=$(date -u -d '3 years' +%Y-%m-%dT23:59:59Z 2>/dev/null || echo '2029-01-01T00:00:00Z')
  post coupons /tinkoff.public.invest.api.contract.v1.InstrumentsService/GetBondCoupons \
    "{\"instrumentId\":\"$BUID\",\"from\":\"$FROM\",\"to\":\"$TO\"}"
  echo "coupon_hits=$(grep -o '"couponDate\|"payOneBond\|"coupon"' /tmp/ti_coupons.json | wc -l)"
fi

echo "=== BrandBy (logo) if share has brand ==="
BRAND=$(sed -n 's/.*"brand\":{\"logoName\":\"\([^"]*\)\".*/\1/p' /tmp/ti_share_by.json | head -1)
if [ -z "$BRAND" ]; then
  BRAND=$(sed -n 's/.*"logoName\":\"\([^"]*\)\".*/\1/p' /tmp/ti_share_by.json | head -1)
fi
echo "logoName=${BRAND:-none}"

echo DONE
