# Support

Use GitHub Discussions for setup questions and GitHub Issues for reproducible bugs.

Before posting, run:

```powershell
pwsh -NoProfile -File scripts/diagnose.ps1 -Device 192.0.2.10:5555 -OutputFile diagnosis.json
```

Review the JSON and redact local addresses, device names and any identifiers before attaching it. Do not attach full `logcat`, account databases, ADB keys or firmware partitions.

