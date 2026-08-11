# Roadmap

## 0.1.x — hardening

- Collect cold-boot and soft-power results from additional `blueplanet` firmware builds.
- Improve checksum/signing documentation and release reproducibility.
- Add safer guided recovery when ADB is temporarily unavailable.

## 0.2 — compatibility profiles

- Move firmware-specific component names and capability checks into reviewed profiles.
- Require diagnosis evidence and rollback tests before a profile becomes supported.
- Add Linux/macOS setup tooling without changing the TV-side trust model.

## Research track

Root remains a separate evidence-gated research track. It will not block the non-Root toolkit and will not be advertised as complete without repeatable UID 0, SELinux, persistence and cold-boot proof.

