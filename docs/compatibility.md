# Compatibility

MFTR0 is identified only when `ro.product.model=MiTV-MFTR0` or `ro.product.device=freeguy`. Its profile enables the Accessibility override for `com.mitv.tvhome` and the exact Xiaomi `FallbackHome` activity. Normal Xiaomi Settings activities are not redirected.

The blueplanet profile is retained separately for existing deployments. There is no generic bypass and no vendor-private call on MFTR0.
