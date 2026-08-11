# Contributing

Thank you for helping improve Redmi TV Toolkit.

## Before opening a PR

1. Open an issue for behavior changes or new device support.
2. Never commit APKs copied from a TV, firmware images, signing keys, ADB keys, account data, IP/MAC addresses or unredacted logs.
3. Keep the default path reversible. New device-specific mutations need detection, backup, verification and rollback.
4. Do not add generic command execution, arbitrary property writes, arbitrary package installation or unpublished exploit chains.
5. Run `pwsh -NoProfile -File scripts/self-test.ps1` and build the APK when the Android SDK is available.

## Pull requests

- Use a focused branch and descriptive commits.
- Explain the tested TV model, firmware, Android version and exact validation.
- Mark untested firmware behavior clearly.
- Update `CHANGELOG.md` for user-visible changes.
- Add no dependency unless the benefit and license are documented.

By contributing, you agree that your contribution is licensed under Apache-2.0.

