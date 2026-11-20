#!/usr/bin/env bash
# Shows that a change of the contract on either side makes the contract test fail.
#
#   0. baseline: consumer test writes the pact, provider verification passes
#   1. PROVIDER change: auth-service renames the JSON field displayName -> display_name in its internal OpenAPI
#      (the Java getter stays getDisplayName(), so auth-service still compiles and its unit tests pass)
#      -> provider verification of the unchanged pact FAILS
#   2. CONSUMER change: catalog-service starts to require avatarUrl in every user
#      -> the consumer test still passes and writes a new pact, but provider verification FAILS
#   3. everything restored -> both PASS again
#
# Every change is reverted at the end (also on Ctrl+C). Logs go to testing/contract/results/.
# Usage (repo root, Git Bash): bash testing/contract/demo-breaking-change.sh
set -u
cd "$(dirname "$0")/../.."
ROOT=$(pwd)
OUT="$ROOT/testing/contract/results"
BAK="$OUT/.backup"
mkdir -p "$OUT" "$BAK"

SPEC=api-contracts/auth-service/internal-openapi.yaml
CONSUMER_TEST=track-service/src/test/java/com/epam/java/specialization/trackservice/contract/AuthServiceContractTest.java
PROVIDER_XML=auth-service/build/test-results/contractTest

cp "$SPEC" "$BAK/spec"
cp "$CONSUMER_TEST" "$BAK/consumer"
restore() {
  cp "$BAK/spec" "$SPEC"
  cp "$BAK/consumer" "$CONSUMER_TEST"
}
trap 'restore; rm -rf "$BAK"' EXIT

consumer() { (cd track-service && ./gradlew contractTest --console=plain --rerun >"$OUT/$1.log" 2>&1); }
provider() { (cd auth-service && ./gradlew contractTest --console=plain --rerun >"$OUT/$1.log" 2>&1); }
result() { if [ "$1" -eq 0 ]; then echo "PASSED"; else echo "FAILED"; fi; }
mismatches() {
  # The Pact verifier writes every mismatch into the JUnit XML of the provider test
  grep -ohE '(Expected [^&<]{0,160}|Actual map is missing[^&<]{0,120}|[a-zA-Z]+Mismatch[^&<]{0,160})' \
    "$PROVIDER_XML"/*.xml 2>/dev/null | sort -u | head -6 | sed 's/^/      | /'
}

echo "== 0. Baseline"
consumer 0-consumer; c=$?; echo "   consumer (catalog-service) test : $(result $c)"
provider 0-provider; p=$?; echo "   provider (auth-service) verify  : $(result $p)"
cp pacts/catalog-service-auth-service.json "$OUT/pact-baseline.json"

echo "== 1. Provider renames the JSON field displayName -> display_name (auth-service internal-openapi.yaml)"
sed -i 's/^        - displayName$/        - display_name/; s/^        displayName:$/        display_name:/' "$SPEC"
provider 1-provider; p=$?; echo "   provider (auth-service) verify  : $(result $p)   <- expected FAILED"
mismatches
cp "$BAK/spec" "$SPEC"

echo "== 2. Consumer starts to require avatarUrl (catalog-service consumer test)"
sed -i 's|u.stringType("displayName", "Artist Seven");|u.stringType("displayName", "Artist Seven");\n                            u.stringType("avatarUrl", "https://cdn.spotty.local/a/7.png");|' "$CONSUMER_TEST"
consumer 2-consumer; c=$?; echo "   consumer (catalog-service) test : $(result $c)   (the consumer itself is fine)"
cp pacts/catalog-service-auth-service.json "$OUT/pact-with-avatarUrl.json"
provider 2-provider; p=$?; echo "   provider (auth-service) verify  : $(result $p)   <- expected FAILED"
mismatches

echo "== 3. Restore and re-verify"
restore
consumer 3-consumer; c=$?; echo "   consumer (catalog-service) test : $(result $c)"
provider 3-provider; p=$?; echo "   provider (auth-service) verify  : $(result $p)"
