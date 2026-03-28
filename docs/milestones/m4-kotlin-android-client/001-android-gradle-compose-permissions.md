# Spec 001: Android Gradle project, Compose, permissions, BuildConfig

## Summary

Create **`apps/android`** Gradle project with **minSdk** per team (default **26** in this spec), **Jetpack Compose**, Material3 baseline, **`INTERNET`** and **`RECORD_AUDIO`** in manifest, and **`BuildConfig.VOICE_GATEWAY_WS_URL`** supplied from `local.properties` key `voiceGatewayWsUrl` so secrets/base URLs stay out of VCS.

**Plan:** [Phase 4](../../1-mvp.md#phase-4-kotlin-android-client)

## Scope

### In scope

- Single `app` module, applicationId `com.jarvis.voice` (adjust if org differs)
- Compose BOM, Kotlin 2.x, Java 17 toolchain
- `MainActivity` hosts root composable placeholder
- ProGuard/R8 rules stub for release (optional for MVP)
- README snippet: SDK install, `./gradlew :app:installDebug`

### Out of scope

- Play Store signing
- Tablet-specific layouts

## Dependencies

- **Prior specs:** [m0/003](../m0-contracts-and-repo-skeleton/003-repo-layout-makefile-tooling.md)
- **External:** Android SDK, JDK 17+

## Interfaces & contracts

### Data & config

| Key | Source | Purpose |
|-----|--------|---------|
| `voiceGatewayWsUrl` | `local.properties` | Injected into `BuildConfig.VOICE_GATEWAY_WS_URL` |

`local.properties.example`:

```properties
voiceGatewayWsUrl=wss://10.0.2.2:8080/v1/voice
```

## Behavior

### Acceptance criteria

1. `./gradlew :app:assembleDebug` succeeds on clean machine with SDK.
2. App launches to empty **Hello** screen without crashing.
3. Manifest contains `RECORD_AUDIO`; no dangerous permission requested at install time only at runtime (m4-006).

### Edge cases & errors

- Missing `voiceGatewayWsUrl` → build fails with clear Gradle message OR defaults to emulator localhost—**pick fail-fast**

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `apps/android/settings.gradle.kts` | Project |
| Create | `apps/android/app/build.gradle.kts` | App module + BuildConfig field |
| Create | `apps/android/app/src/main/AndroidManifest.xml` | Permissions |
| Create | `apps/android/app/src/main/.../MainActivity.kt` | Entry |
| Create | `apps/android/local.properties.example` | Template |

## Verification

### Automated

- [ ] `./gradlew :app:lintDebug` passes with baseline empty

### Manual

- [ ] Install on emulator; app opens

## Notes

- Emulator uses `10.0.2.2` for host machine localhost.
