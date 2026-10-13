# libxposed API 102 migration

The api102-migration branch is rebuilt directly from public GitHub source. The master branch is unchanged. No locally encrypted source was read or uploaded.

## Requirements

- A framework implementing libxposed API 102, with system-server hooking capability.
- Module minimum Android API 26 (Android 8), required by service 102. The original demo remains API 24.
- Remote preferences capability is recommended. Without it, the hook uses read-only defaults and logs a warning; UI configuration cannot propagate through unsupported framework services.
- Compile SDK 37.0. Existing AGP 9.3.3 / Gradle 9.8.1 / Kotlin 2.4.21 are retained; Actions uses JDK 21.

## Changes

- Replaced legacy Yuki/Xposed entry and hooks with modern XposedModule, onSystemServerStarting and interceptor-chain hooks.
- Modern META-INF/xposed entry and static `system` scope; hot reload is deliberately rejected. Reboot after installing or updating a system-server module.
- Private typed preferences, compatibility copying from the old application preferences store (retained), and service102 remote preferences mirroring on a worker thread.
- UID/package/signature-checked Binder RPC discovered through a signature-protected ordered broadcast. All existing record/mute/open-app/config/app-list operations remain.
- Stable UID-bound snapshots and bounded chunks instead of broadcasting complete crash/log lists. Oversized transfers report an error; they are not silently truncated.
- Retained system-server crash storage at /data/misc/app_errors_records and UI log export.
- Six production Hook adapter regression tests plus the original unit test.

## Build and install

Use Actions workflow path `.github/workflows/commit_ci.yml`. The sidebar may retain the master name `Automatic Build on Commit`; select branch `api102-migration` when manually dispatching. Pushes to this branch build automatically after Fork workflows are enabled.

The workflow tests and builds the module Debug APK, then uploads `AppErrorsTracking-API102-debug` and `API102-test-reports`. It does not require Telegram or App Center secrets and does not build the native demo.

Current upstream public signing configuration is retained. Do not use the public signing key as a production security boundary; Binder additionally validates the precise module UID/package identity. Existing installations must use compatible signing or be uninstalled after backing up settings.

Enable the module for system server in the framework manager, open its Settings to publish preferences, then reboot. A settings connection alone is not evidence of working crash hooks; exercise a demo crash, record retrieval, mute/unmute, log export and locale changes on-device.

## Validation limits

GitHub Actions proves build/test results only. It cannot validate vendor-specific crash method names, SELinux access to original record storage, protected bootstrap broadcasts, cross-user activity startup, notification/dialog presentation or Android framework lifecycle behavior. Device tests remain necessary.