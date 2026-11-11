#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$PWD/tools/android-sdk}"
export PATH="$PWD/tools/go/bin:$PATH"
export GOMAXPROCS="${GOMAXPROCS:-2}"
export GOFLAGS="${GOFLAGS:--p=2}"
sdk="$ANDROID_HOME/build-tools/35.0.0"
platform="$ANDROID_HOME/platforms/android-35/android.jar"
# Always rebuild Java output so obsolete synthetic classes cannot ship.
rm -rf build/classes build/dex build/generated build/node-tests
mkdir -p app/lib/arm64-v8a build/classes build/dex build/generated build/node-tests out
java -jar tools/ecj.jar -encoding UTF-8 -source 8 -target 8 -classpath tools/json.jar -d build/node-tests app/src/net/miotest/client/NodeBook.java tests/NodeBookTest.java
java -cp build/node-tests:tools/json.jar net.miotest.client.NodeBookTest
(cd core && go test ./... github.com/orgmio/mio/protocol github.com/orgmio/mio/client && CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -trimpath -ldflags='-s -w -R 0x4000' -o ../app/lib/arm64-v8a/libmiocore.so .)
"$sdk/aapt2" compile --dir app/res -o build/resources.zip
"$sdk/aapt2" link -I "$platform" --auto-add-overlay -R build/resources.zip --manifest app/AndroidManifest.xml --java build/generated -o build/base.apk --min-sdk-version 26 --target-sdk-version 35
java -jar tools/ecj.jar -encoding UTF-8 -source 8 -target 8 -classpath "$platform" -d build/classes app/src/net/miotest/client/*.java build/generated/net/miotest/client/R.java
find build/classes -name '*.class' -print0 | xargs -0 "$sdk/d8" --lib "$platform" --min-api 26 --output build/dex
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
(cd app && zip -q -0 -u ../build/unsigned.apk lib/arm64-v8a/libmiocore.so)
"$sdk/zipalign" -P 16 -f 4 build/unsigned.apk build/aligned.apk
signing_key="${PRE_SMIO_KEYSTORE:-$PWD/tools/local-signing.p12}"
if [[ ! -f "$signing_key" ]]; then
 if [[ "${PRE_SMIO_RELEASE:-0}" == "1" ]]; then
  echo 'Release signing key is missing; refusing to generate an incompatible update key.' >&2
  exit 1
 fi
 mkdir -p "$(dirname "$signing_key")"
 keytool -genkeypair -keystore "$signing_key" -storepass android -keypass android -alias mio-test -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=pre-smio Local Development' >/dev/null 2>&1
fi
export PRE_SMIO_STORE_PASSWORD="${PRE_SMIO_STORE_PASSWORD:-android}"
export PRE_SMIO_KEY_PASSWORD="${PRE_SMIO_KEY_PASSWORD:-$PRE_SMIO_STORE_PASSWORD}"
"$sdk/apksigner" sign --ks "$signing_key" --ks-key-alias "${PRE_SMIO_KEY_ALIAS:-mio-test}" --ks-pass env:PRE_SMIO_STORE_PASSWORD --key-pass env:PRE_SMIO_KEY_PASSWORD --out out/pre-smio-arm64.apk build/aligned.apk
"$sdk/apksigner" verify --verbose out/pre-smio-arm64.apk
"$sdk/zipalign" -c -P 16 4 out/pre-smio-arm64.apk
(cd out && sha256sum pre-smio-arm64.apk > SHA256SUMS)
cat out/SHA256SUMS
