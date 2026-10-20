# Troubleshooting

Use `scripts\diagnose.ps1` and `scripts\manage-home.ps1 -Status`. The Toolkit does not modify Android's default Home. If the target launcher cannot be started, the helper makes at most three attempts in five seconds, tries the stored previous component, then disables its own override.

A cold reboot can clear Accessibility settings on some Xiaomi firmware. BootReceiver restores only the Toolkit service when recovery is enabled. It preserves other services; if Android rejects the secure-setting write, use the one-time ADB grant again.
