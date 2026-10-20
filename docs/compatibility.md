# Compatibility

MFTR0 is identified only when both `ro.product.model=MiTV-MFTR0` and `ro.product.device=freeguy`; conflicting identities fail closed. Its profile enables the Accessibility override for Xiaomi Home and the exact Xiaomi `FallbackHome` activity. Normal Xiaomi Settings activities are not redirected. It may repair `adb_enabled` but has no verified private interface to restart adbd or set a missing TCP listener.

The blueplanet profile retains its guarded ADB, TVManager, startup mediator and PeriodicCleaner paths in a separate target-SDK-29 APK. MFTR0 uses the target-SDK-34 APK and cannot enter those private paths.
