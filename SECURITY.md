# Security policy

## Supported version

Only the latest release receives security fixes.

## Report a vulnerability

Do not open a public issue for a vulnerability that could expose a television to unauthenticated control, bypass platform authorization, install packages silently, write protected settings/properties or execute commands with elevated privileges.

Use GitHub's private vulnerability reporting feature for this repository. Include the affected version, firmware fingerprint, reproduction conditions and a minimal proof. Remove serial numbers, account identifiers, IP/MAC addresses and credentials.

We aim to acknowledge a report within 7 days. Publication should wait until a fix or a clear mitigation is available.

## Trust model

- The setup computer already has authorized ADB access to a TV owned by the operator.
- The helper has no network server and does not upload telemetry.
- The release app exposes only a small ADB-shell control surface protected by Android's `DUMP` permission.
- The public build contains no arbitrary shell, arbitrary property writer, general package installer or Root exploit.
- Network ADB is powerful. Keep the TV on a trusted LAN and remove old ADB authorizations when ownership changes.

