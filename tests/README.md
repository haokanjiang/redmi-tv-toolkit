# Local tests

`tests/run-java.ps1` compiles the production policy classes on a host JDK and runs timeline, configuration rollback, capability isolation, ADB readiness, Accessibility-list, and Home-window-hint assertions. `tests/scripts.tests.ps1` executes the real PowerShell scripts against `mock-adb.py`, which refuses to run without a fixture state file. `scripts/self-test.ps1` runs manifest safety checks and parses PowerShell scripts. None of these tests connect to a TV.

Device-only behavior still requires a controlled cold reboot and observation of the durable boot journal, `adbd` listener, Accessibility binding, and physical Home key.
