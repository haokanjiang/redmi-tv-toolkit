# Troubleshooting

Use `scripts\diagnose.ps1 -Device HOST:5555` and `scripts\manage-home.ps1 -Device HOST:5555 -Status`. The Toolkit does not modify Android's default Home. If the target launcher cannot be confirmed in the foreground, the helper makes at most three target attempts in ten seconds, tries the last confirmed previous launcher at most twice, then disables its own override.

A cold reboot can clear Accessibility settings and network debugging on this Xiaomi firmware. The persisted status journal distinguishes a received boot broadcast, a JobScheduler run, the ADB setting before/after, network adbd state, and an Accessibility repair. A live ADB connection after manually enabling debugging cannot prove that Toolkit restored it. If `adb_enabled=1` but the active TCP port is not 5555 or `init.svc.adbd` is not `running`, MFTR0 does not attempt a private restart; the limitation must be confirmed from the next cold-boot journal.
