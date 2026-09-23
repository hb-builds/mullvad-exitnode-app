# Build and release

## Local build

Install Java 17 or later, Android SDK platform 37 and build-tools 37.0.0, Kotlin 2.4.10, Python 3.10+, `zip`, and OpenSSL. Set `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) and put `kotlinc` on PATH. If the Kotlin standard library is elsewhere, set `KOTLIN_STDLIB` to its JAR path.

```sh
tools/check.sh
exit-controller/build.sh
```

Output: `exit-controller/build/exit-controller.apk`. The build uses aapt2, javac, kotlinc, d8, zipalign and apksigner. No dependencies are downloaded by the build script itself.

The first local build creates a random signing key/password under `exit-controller/build/signing/`, ignored by Git. Keep them private and backed up: Android requires the same signing identity for updates. A locally generated key cannot update someone else's release build.

To use an existing key:

```sh
SIGNING_KEYSTORE=/secure/path/release.jks \
SIGNING_PASSWORD_FILE=/secure/path/password \
SIGNING_KEY_ALIAS=exit-controller \
  exit-controller/build.sh
```

Do not put these values or files in source control. To regenerate adapted Mullvad renderer source, run `python3 exit-controller/tools/adapt-mullvad.py` before building.

## GitHub Actions

**Check** runs tests and an APK build for pushes/PRs with read-only repository permissions and no signing secrets. Its test artifact uses a disposable key; use Releases for upgrades.

**Signed release** is manually run from `main`. It runs the checks, builds and signs an APK, and publishes it with a complete source ZIP and SHA256SUMS. Actions are pinned to commit hashes; the Kotlin compiler download is checked against a pinned SHA-256.

For a fork, create an environment named `release`, restrict it to the `main` branch, and add these environment secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 encoding of your private JKS signing file.
- `ANDROID_KEYSTORE_PASSWORD`: its password (alias `exit-controller`).

GitHub encrypts these secrets; base64 itself is only an encoding. Keep an offline key backup. Never give pull-request workflows access to release secrets, and review changes to workflows/build scripts before merging them.

For a new version:

1. Increase both `versionCode` and `versionName` in the Android manifest.
2. Push to `main` and wait for **Check** to pass.
3. Open **Actions → Signed release → Run workflow**, selecting `main`.
4. Install the APK from the new GitHub Release over the existing app. Pairing is preserved.

A version tag must not already exist. Forks use their own signing keys and pairing files; no private source repository is required. No Mullvad, Tailscale, SSH or pairing secrets belong in GitHub Actions.

## Source layout

- `exit-controller/app`: Android Java/Kotlin/resources.
- `exit-controller/vendor/mullvad`: retained upstream source and assets.
- `oci-exit-node`: gateway scripts, systemd units and tests.
- `tools`: checks, pinned CI setup and release packaging.
