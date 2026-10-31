# Release signing

Dolphy release APKs are signed with one stable key so every new build installs
as an in-place update over the previous one. `assembleRelease` fails fast when
no release keystore is configured, so a debug-signed APK can never be published
by accident.

> **One-time switch:** if the copy currently installed on a device was signed
> with a debug key, Android will reject the first release-signed update with
> `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall that old copy once. Every
> build after that installs over the previous one without uninstalling and
> without losing app data.

## GitHub Actions secrets

The repository `Alaa91H/Dolphy-App` stores the signing material as encrypted
Actions secrets. Never commit these values.

| Secret | Meaning |
|---|---|
| `DOLPHY_KEYSTORE_BASE64` | Base64 encoding of the `.jks` file (`base64 -w0 dolphy-release.jks`) |
| `DOLPHY_KEYSTORE_PASSWORD` | Keystore password |
| `DOLPHY_KEY_ALIAS` | Key alias (`dolphy`) |
| `DOLPHY_KEY_PASSWORD` | Key password |

The `.github/workflows/release.yml` workflow decodes the keystore into
`$RUNNER_TEMP` and exports `DOLPHY_KEYSTORE_FILE` for Gradle. The keystore is
never written into the repository or uploaded as an artifact.

## Publishing a release

### From a tag (recommended)

```bash
git tag v2.7.0
git push origin v2.7.0
```

The workflow builds `Dolphy-2.7.0.apk`, verifies its certificate, uploads it as
a workflow artifact, and attaches it to the GitHub release for the tag.

### Manually

Run the **Release** workflow from the Actions tab (`workflow_dispatch`). It
accepts an optional `version_name` and `version_code`; otherwise it uses
`2.6.0` and derives the code from the version name.

### Version code

`versionCode` is derived from the version name as
`major * 10000 + minor * 100 + patch`, for example `2.7.0` → `20700`. It is
always greater than the historical `2.6` (`26`) build, so updates move forward.
Local builds can override it:

```bash
./gradlew :app:assembleRelease \
  -Pdolphy.versionName=2.7.0 \
  -Pdolphy.versionCode=20700
```

## Configure locally

`keystore.properties` in the repository root is git-ignored and points at the
local keystore:

```properties
storeFile=/absolute/path/to/dolphy-release.jks
storePassword=...
keyAlias=dolphy
keyPassword=...
```

Environment variables take precedence over this file:

```bash
export DOLPHY_KEYSTORE_FILE=/absolute/path/to/dolphy-release.jks
export DOLPHY_KEYSTORE_PASSWORD=...
export DOLPHY_KEY_ALIAS=dolphy
export DOLPHY_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

## Create a release key (once, offline)

```bash
keytool -genkeypair -v \
  -keystore dolphy-release.jks \
  -alias dolphy \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Dolphy, O=Dolphy, C=SA"
```

Back up the keystore and both passwords in a secure vault. Losing them prevents
upgrading installed users; rotating the key requires a new application ID or a
user uninstall.

The current key's certificate fingerprint (SHA-256) is:

```
78:D9:1C:EE:3C:F0:FF:2F:10:D4:C5:F5:69:2A:7B:63:83:88:97:4D:33:25:71:DF:D5:DE:0C:52:1B:25:90:2C
```

## Verify a build

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

The `SHA-256 digest` must match the fingerprint above. The release workflow
runs the same check before publishing.
