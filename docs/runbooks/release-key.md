# Release key leaked or lost

## Symptom

One of these, about the key that signs every release
([`CONTRIBUTING.md#the-release-key`](../../CONTRIBUTING.md#the-release-key)):

- **Leaked.** The keystore and its password may be in someone else's hands. Examples: a backup copy was exposed, the
  maintainer's machine was compromised, a workflow change could have printed the `RELEASE_*` secrets, or a report
  arrived through [`SECURITY.md`](../../SECURITY.md) ("for a leaked secret: where you found it").
- **Lost.** The backup keystore or its password is gone, so the key can't be used outside CI. Or the
  `RELEASE_KEYSTORE_BASE64` secret was overwritten or deleted too, and no copy of the key is left anywhere.

Every release so far, v0.1.0 to v0.1.3, is signed by the same certificate: `CN=MinimalUI`, RSA 4096, with a v2
signature only. Its SHA-256 digest is:

```text
8c6a340f653f3e481059c2a84b53462735bbf1c659b9ed2ccd45935839306fb2
```

To check any APK against it, use `apksigner` from the Android SDK build-tools:

```bash
apksigner verify --print-certs minimalui-<tag>.apk | grep 'certificate SHA-256'
```

## Why this app is different

Android only installs an update when its signing certificate matches the installed copy's, or descends from it
through a rotation lineage. Two things follow:

- **A leaked key is a working update channel for an attacker.** An APK they sign installs over MinimalUI and keeps
  its data. MinimalUI can be the HOME app, and it sees every installed app. New passwords or new repository secrets
  don't help, because the key itself is the secret. Only rotating to a new key does. MinimalUI's `minSdk` is 28
  (Android 9), and APK Signature Scheme v3 key rotation works from Android 9 on, so every supported phone can accept
  a rotated key without uninstalling.
- **A lost key cannot be rotated away from.** Rotation needs the old key to sign the lineage. Without it, no later
  release installs over the ones people have. Each user then has to uninstall, which erases the setup
  ([`docs/usage.md`](../usage.md#where-your-setup-is-kept)). **Export setup** and **Import setup** exist only from
  v0.1.3 on.

## Response

### Leaked: contain

1. Delete the signing secrets so nothing more is signed in CI:

   ```bash
   gh secret delete RELEASE_KEYSTORE_BASE64
   gh secret delete RELEASE_KEYSTORE_PASSWORD
   gh secret delete RELEASE_KEY_PASSWORD
   gh secret delete RELEASE_KEY_ALIAS
   ```

   The Release workflow then stops at **Check signing secrets**, names the missing secrets and publishes nothing.
   Expect a failed Release run on any merge that touches `app/build.gradle.kts` until the rotated release is out.
2. Check what the key has signed here. Every asset on **Releases** should be one the Release workflow uploaded, and
   it should carry the digest under [Symptom](#symptom):

   ```bash
   gh release list
   gh run list --workflow release.yml --limit 20   # look for runs nobody started
   ```

   Download each APK and check it with `apksigner verify --print-certs`. Delete any asset or release you can't account
   for. You can't see APKs signed and passed around somewhere else, so assume one exists.
3. Draft a security advisory: **Security → Advisories → New draft security advisory**. It holds the details until
   the rotated release is out, as `SECURITY.md` describes for any report.

### Leaked: rotate the key

Use the Android SDK's `apksigner` (build-tools 33 or later) and the backup copy of the old keystore.

1. Create the new key the same way as the first, under a new file name, and back it up and its password
   ([`CONTRIBUTING.md#the-release-key`](../../CONTRIBUTING.md#the-release-key)):

   ```bash
   keytool -genkeypair -keystore ~/minimalui-release-2.jks -alias minimalui \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=MinimalUI"
   ```

2. Sign the lineage, the proof that the old key hands over to the new one. This is the last time the old key is used.
   The lineage holds only certificates and signatures, no private key; keep a copy with the key backups:

   ```bash
   apksigner rotate --out ~/minimalui.lineage \
     --old-signer --ks ~/minimalui-release.jks \
     --new-signer --ks ~/minimalui-release-2.jks
   apksigner lineage --in ~/minimalui.lineage --print-certs -v   # signer #1: rollback capability false
   ```

   Keep `rollback` off, as it is by default. With it off, a phone that has installed the rotated release refuses any
   later update signed with the old key alone. That refusal is what makes the leaked key useless.
3. Open the fix-forward pull request that raises `versionName` and `versionCode`
   ([`CONTRIBUTING.md#cutting-a-release`](../../CONTRIBUTING.md#cutting-a-release)), and merge it. With the secrets
   deleted, the Release workflow publishes nothing.
4. On a clean checkout of that merge commit, build the unsigned release APK and sign it with the new key and the
   lineage:

   ```bash
   ./gradlew check assembleRelease   # no RELEASE_* variables set: builds app-release-unsigned.apk
   apksigner sign --ks ~/minimalui-release-2.jks --lineage ~/minimalui.lineage \
     --v1-signing-enabled false --v2-signing-enabled false --rotation-min-sdk-version 28 \
     --out minimalui-v<versionName>.apk app/build/outputs/apk/release/app-release-unsigned.apk
   apksigner verify -v --print-certs minimalui-v<versionName>.apk
   ```

   **Always pass `--rotation-min-sdk-version 28`.** Without it, `apksigner` uses the new key only on Android 13 and
   later. Android 9 to 12 then stay on the leaked key: the verifier lists a second signer for `minSdkVersion=24,
   maxSdkVersion=32` with the old certificate. With it, `verify` must show **one** signer with the new key's digest and
   `Verified using v3 scheme: true`. Turning v1 and v2 off is safe because nothing below Android 9 installs MinimalUI,
   and it means the old key isn't needed for this or any later release.
5. Install it over the current release on a phone, the oldest Android you can reach (`adb install -r`). Check that
   the setup is still there. Then publish it on the merge commit, in the same form the workflow uses:

   ```bash
   gh release create v<versionName> minimalui-v<versionName>.apk --target <merge-commit-sha> \
     --title "MinimalUI v<versionName>" --generate-notes \
     --notes "Signed with a new release key; it installs over earlier releases and keeps your settings. <why>."
   ```

   Once that release exists, the Release workflow treats the version as released and stops there.

**Don't put the new key alone into the `RELEASE_*` secrets.** `signingConfigs` in `app/build.gradle.kts` takes one
keystore and has no lineage setting. So CI would sign with the new key and no lineage, and no installed copy would
accept that APK. Until `release.yml` signs with the lineage as in step 4, keep the secrets deleted and publish each
release by hand as above.

### Leaked: tell users

In the advisory, and in a pinned issue once it is public:

- Install MinimalUI only from this repository's **Releases** page, and install the rotated release now.
- State the new key's SHA-256 digest beside the old one, so anyone can check an APK with `apksigner verify
  --print-certs`.
- Be clear about what remains. A phone that installs the rotated release refuses old-key updates from then on. A
  phone that stays on an older release still accepts an APK signed with the leaked key. MinimalUI has no auto-update
  channel, so that APK has to be installed by hand.

### Lost: the secrets still work

GitHub never shows a secret's value again, so the secrets are not a backup. While they still sign, CI is the only
place the old key exists. Rotate before anything else touches them. That means running `apksigner rotate` (step 2
above) once in a job that gets the old key from the current secrets and the new key from new ones, and uploading only
the lineage. Then follow steps 3 to 5. Such a job is a `.github/workflows/` change: review it line by line
([`docs/risk-tiers.md`](../risk-tiers.md)) and remove it after the one run.

### Lost: the key is gone everywhere

There's no way back to this signing identity. The next release starts a new one:

1. Create a new key and set the four secrets, as in
   [`CONTRIBUTING.md#the-release-key`](../../CONTRIBUTING.md#the-release-key), and raise `versionName` and
   `versionCode` as usual.
2. Keep the last release signed with the old key on **Releases**. Users who are still on v0.1.2 or earlier have no
   **Export setup**, so they first update to it (it installs over theirs), then export.
3. In the new release's notes and a pinned issue, tell users the order: **MinimalUI settings → Export setup**, then
   uninstall MinimalUI, install the new release, and **Import setup**. Without the uninstall, Android refuses the
   install (`adb` reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the phone's installer refuses it). Before
   uninstalling, a user whose default home app is MinimalUI should set another one in Android's Settings.

## Prevention

- **Check the backup now, not during an incident.** It should open with its password and hold the certificate that
  signed the releases:

  ```bash
  keytool -list -v -keystore ~/minimalui-release.jks | grep -m1 'SHA256:' | sed 's/.*SHA256: //' | tr -d : | tr A-F a-f
  ```

  The output must equal the digest under [Symptom](#symptom). Keep a second copy of the keystore and its password in
  a separate place.
- **Not guarded:** nothing checks that a published APK's signer is the expected certificate. A replaced
  `RELEASE_KEYSTORE_BASE64` would publish a release that no existing install accepts. And after a rotation, CI can't
  sign with the lineage at all. Both are `release.yml` changes, which are high tier and human-reviewed
  ([`docs/risk-tiers.md`](../risk-tiers.md)).
