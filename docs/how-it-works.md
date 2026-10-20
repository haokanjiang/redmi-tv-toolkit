# How it works

The Windows manager queries Android package activities and keeps only launcher-capable activities. A selected component is stored in device-protected preferences in the helper APK. `HomeOverrideService` watches `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOWS_CHANGED`, redirects only Xiaomi Home/FallbackHome windows, and uses a bounded three-attempt/5-second retry window. If target and previous fallback both fail, it disables its own override and leaves system Home available.

BootReceiver restores only the helper Accessibility service when configuration is enabled. It merges the secure setting rather than replacing other services. No default Home mutation occurs.
