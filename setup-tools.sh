#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p tools
if [[ ! -x tools/go/bin/go ]] && ! go version 2>/dev/null | grep -q '^go version go1.27'; then
 curl -fL --retry 3 https://go.dev/dl/go1.27.4.linux-amd64.tar.gz -o tools/go.tar.gz
 echo '7226736c2339719cda28fbf3a54d71aa63bcefe376e98cef00034268dab24840  tools/go.tar.gz' | sha256sum -c -
 tar -xzf tools/go.tar.gz -C tools
fi
[[ -f tools/ecj.jar ]] || curl -fL --retry 3 https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.41.0/ecj-3.41.0.jar -o tools/ecj.jar
[[ -f tools/json.jar ]] || curl -fL --retry 3 https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o tools/json.jar
sdk_root="${ANDROID_HOME:-$PWD/tools/android-sdk}"
sdk_manager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdk_manager" ]]; then
 if [[ ! -x tools/cmdline-tools/bin/sdkmanager ]]; then
  curl -fL --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o tools/sdk.zip
  unzip -qo tools/sdk.zip -d tools
 fi
 sdk_manager="$PWD/tools/cmdline-tools/bin/sdkmanager"
fi
if [[ "${CI:-false}" == "true" || "${ACCEPT_ANDROID_LICENSES:-0}" == "1" ]]; then
 # Avoid treating yes's expected SIGPIPE as a failed SDK installation.
 set +o pipefail
 yes | "$sdk_manager" --sdk_root="$sdk_root" --licenses >/dev/null
 status="${PIPESTATUS[1]}"
 set -o pipefail
 [[ "$status" == "0" ]]
else
 "$sdk_manager" --sdk_root="$sdk_root" --licenses
fi
"$sdk_manager" --sdk_root="$sdk_root" 'platforms;android-35' 'build-tools;35.0.0'
