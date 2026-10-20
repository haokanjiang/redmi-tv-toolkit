# Redmi TV Toolkit

Redmi TV Toolkit is an independent Android TV launcher manager. It does not depend on CleanYourTV and never changes Android's default Home, disables system packages, or uses root.

## Supported profiles

- **MiTV-MFTR0 / freeguy / Android 14**: Accessibility Home Override and boot recovery only. Xiaomi Home and FallbackHome remain enabled. Vendor TVManager, `start_3rd_app`, PeriodicCleaner and ADB recovery are disabled by profile.
- **blueplanet**: legacy recovery components remain guarded by the blueplanet profile; MFTR0 can never enter those private paths.

Install any HOME-capable launcher separately (Emotn UI, FLauncher, Projectivy, or another launcher), then install the helper APK and grant the one-time secure-settings permission through ADB:

```powershell
.\scripts\setup.ps1 -Device 192.168.0.106:5555
.\scripts\manage-home.ps1 -Device 192.168.0.106:5555 -List
.\scripts\manage-home.ps1 -Device 192.168.0.106:5555
```

The manager only lists activities resolved for HOME/LEANBACK_LAUNCHER/LAUNCHER, de-duplicates by package, and starts the selected component. It preserves all existing Accessibility services while adding or removing only the Toolkit service.

To inspect or restore:

```powershell
.\scripts\manage-home.ps1 -Device 192.168.0.106:5555 -Status
.\scripts\manage-home.ps1 -Device 192.168.0.106:5555 -Restore
```

Restore disables Toolkit recovery and returns control to the Xiaomi system Home without changing the system default Home setting.

## Build

The repository uses a dependency-free Android build script. Set `JAVA_HOME` to a JDK 17+ (Android Studio JBR is preferred) and `ANDROID_SDK_ROOT`/`ANDROID_HOME` to an SDK containing platform 34 and build-tools, or use the standard Windows SDK location. Then run:

```powershell
powershell -ExecutionPolicy Bypass -File app\build.ps1
```

Output: `app\dist\redmi-tv-toolkit.apk`.

## Verification status

Local source safety tests and APK compilation are automated. Cold reboot, physical Home-key interception, launcher crash rollback, and Android 14 background-start behavior still require validation on the target TV; a successful build is not device compatibility proof.
