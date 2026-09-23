#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
BT="$SDK/build-tools/37.0.0"
PLATFORM=${ANDROID_PLATFORM:-android-37}
if [[ ! -f "$SDK/platforms/$PLATFORM/android.jar" && "$PLATFORM" == android-37 ]]; then PLATFORM=android-37.0; fi
JAR="$SDK/platforms/$PLATFORM/android.jar"
OUT="$ROOT/build"
mkdir -p "$OUT" "$OUT/signing"
chmod 700 "$OUT/signing"
KEYSTORE=${SIGNING_KEYSTORE:-$OUT/signing/release.jks}
PASSWORD_FILE=${SIGNING_PASSWORD_FILE:-$OUT/signing/password}
KEY_ALIAS=${SIGNING_KEY_ALIAS:-exit-controller}
if [[ -n ${SIGNING_KEYSTORE:-} ]]; then
    [[ -f "$KEYSTORE" && -f "$PASSWORD_FILE" ]] || { echo 'Supplied signing files are missing.' >&2; exit 1; }
elif [[ ! -f "$KEYSTORE" ]]; then
    openssl rand -base64 32 > "$OUT/signing/password"
    chmod 600 "$OUT/signing/password"
    keytool -genkeypair -keystore "$OUT/signing/release.jks" -storepass:file "$OUT/signing/password" \
      -keypass:file "$OUT/signing/password" -alias exit-controller -dname 'CN=HBX Exit Controller' \
      -keyalg RSA -keysize 3072 -validity 10000 >/dev/null 2>&1
    chmod 600 "$OUT/signing/release.jks"
fi
rm -rf "$OUT/classes" "$OUT/dex" "$OUT/generated" "$OUT/compiled"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated" "$OUT/compiled"
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/compiled/resources.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --java "$OUT/generated" --min-sdk-version 35 --target-sdk-version 37 \
  -o "$OUT/resources.apk" "$OUT/compiled/resources.zip"
javac --release 17 -classpath "$JAR" -d "$OUT/classes" "$OUT/generated/one/hbx/exitcontroller/R.java"
if [[ -n ${KOTLIN_STDLIB:-} ]]; then
    KOTLIN_LIB=$KOTLIN_STDLIB
elif [[ -f /opt/homebrew/opt/kotlin/libexec/lib/kotlin-stdlib.jar ]]; then
    KOTLIN_LIB=/opt/homebrew/opt/kotlin/libexec/lib/kotlin-stdlib.jar
else
    KOTLIN_LIB=$(cd "$(dirname "$(command -v kotlinc)")/../lib" && pwd)/kotlin-stdlib.jar
fi
find "$ROOT/app/src/main/kotlin" -name '*.kt' > "$OUT/kotlin-sources.txt"
kotlinc -J-Djava.io.tmpdir="$OUT" -jvm-target 17 -classpath "$JAR:$OUT/classes" -d "$OUT/classes" @"$OUT/kotlin-sources.txt"
find "$ROOT/app/src/main/java" "$OUT/generated" -name '*.java' > "$OUT/sources.txt"
javac --release 17 -classpath "$JAR:$OUT/classes:$KOTLIN_LIB" -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$JAR" --min-api 35 --output "$OUT/dex" @"$OUT/classes.txt" "$KOTLIN_LIB"
cp "$OUT/resources.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -P 16 -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
 --ks-pass "file:$PASSWORD_FILE" \
 --out "$OUT/exit-controller.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/exit-controller.apk"
echo "Built $OUT/exit-controller.apk"
