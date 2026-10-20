# Redmi TV Toolkit

Redmi TV Toolkit is an independent Android TV launcher manager. It does not depend on CleanYourTV and never changes Android's default Home, disables system packages, or uses root.

## Supported profiles

- **MiTV-MFTR0 / freeguy / Android 14**: Accessibility Home Override, bounded boot recovery, and a secure-setting-only attempt to restore `adb_enabled`. Xiaomi Home and FallbackHome remain enabled. Vendor TVManager, `start_3rd_app` writes, PeriodicCleaner, and private adbd restart are disabled by profile.
- **blueplanet**: legacy recovery components remain guarded by the blueplanet profile; MFTR0 can never enter those private paths.

Install any HOME-capable launcher separately (Emotn UI, FLauncher, Projectivy, or another launcher), then install the helper APK and grant the one-time secure-settings permission through ADB:

```powershell
.\scripts\setup.ps1 -Device 192.168.0.106:5555
.\scripts\manage-home.ps1 -Device 192.168.0.106:5555 -List
.\scripts\manage-home.ps1 -Device 192.168.0.106:5555
```

The manager lists HOME and LEANBACK_LAUNCHER activities, labels TV app entries separately, and selects an exact component. It preserves existing Accessibility services while adding or removing only the Toolkit service. Selection enables both Home Override and boot recovery.

To inspect or restore:

```powershell
.\scripts\manage-home.ps1 -Device 192.168.0.106:5555 -Status
.\scripts\manage-home.ps1 -Device 192.168.0.106:5555 -Restore
.\scripts\diagnose.ps1 -Device 192.168.0.106:5555
```

Restore disables Toolkit recovery and returns control to the Xiaomi system Home without changing the system default Home setting. The diagnostic reads `adb_enabled`, the persistent and active TCP ports, `init.svc.adbd`, `start_3rd_app`, Accessibility settings, and the helper's durable recovery journal. It requires a working ADB connection; if the firmware disables network adbd before it can connect, turn on debugging manually before collecting the journal.

## Build

The repository uses a dependency-free Android build script. Set `JAVA_HOME` to a JDK 17+ (Android Studio JBR is preferred) and `ANDROID_SDK_ROOT`/`ANDROID_HOME` to an SDK containing platform 34 and build-tools, or use the standard Windows SDK location. Then run:

```powershell
powershell -ExecutionPolicy Bypass -File app\build.ps1
```

Output: `app\dist\redmi-tv-toolkit.apk`.

## Verification status

Local behavior tests and APK compilation are automated. MFTR0 has no verified privileged interface to restart adbd or restore a lost TCP port: writing `adb_enabled=1` alone does not prove network ADB is available. Cold reboot, the recovery journal, actual network listening, Accessibility binding, and the physical Home key still require validation on the target TV; a successful build is not device compatibility proof.
