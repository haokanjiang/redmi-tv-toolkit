# TV helper app

This directory contains the complete source of the Android helper installed by `scripts/setup.ps1`.

The app intentionally targets SDK 29 because the verified Xiaomi Android 11 TVManager provider path is target-SDK sensitive. It contains no third-party dependencies and is built directly with Android SDK command-line tools.

## Build requirements

- Windows PowerShell 7;
- JDK 17 (`JAVA_HOME` or tools on `PATH`);
- Android SDK platform 30;
- Android build-tools with `aapt`, `d8`, `zipalign` and `apksigner`.

```powershell
sdkmanager "platforms;android-30" "build-tools;35.0.0"
pwsh -NoProfile -File build.ps1
```

Output: `dist/redmi-tv-toolkit.apk` and its SHA-256 file.

The first build creates a signing key under `%USERPROFILE%\.redmi-tv-toolkit`. Keep that key private and backed up; Android requires future upgrades to use the same signing key. CI artifacts use an ephemeral community-test key and must not be used as an upgrade channel.

## Release safety

The source must not gain generic system-provider methods. `XiaomiBridge` is deliberately limited to:

- setting this package as the Xiaomi TVHome startup mediator;
- restoring the one startup setting during rollback;
- rate-limited `ctl.restart=adbd`.

Research probes, arbitrary properties, silent package installation and HIDL experiments belong outside release builds.

