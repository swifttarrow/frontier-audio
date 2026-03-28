# Spec 002: Secure storage — deviceId and sessionToken

## Summary

Persist a **stable `deviceId`** (UUID generated on first launch) and optional **`sessionToken`** from `session.ack` using **EncryptedSharedPreferences** or **DataStore Encrypted**. Values must survive process death and be cleared on **app data clear** only (not each launch).

**Plan:** [Phase 4](../../1-mvp.md#phase-4-kotlin-android-client) · **Spec:** `docs/specs/1-initial.md` §7

## Scope

### In scope

- `SessionStore` interface: `fun getOrCreateDeviceId(): String`, `fun getSessionToken(): String?`, `fun setSessionToken(token: String)`, `fun clearSession()`
- Use AndroidX Security crypto (`EncryptedSharedPreferences`) with master key from `MasterKey`
- Thread-safe access

### Out of scope

- Biometric lock
- Multi-profile switching

## Dependencies

- **Prior specs:** [m4/001](./001-android-gradle-compose-permissions.md)
- **External:** `androidx.security:security-crypto`

## Interfaces & contracts

### Public API

```kotlin
interface SessionStore {
  fun getOrCreateDeviceId(): String
  fun getSessionToken(): String?
  fun setSessionToken(token: String)
  fun clearSession()
}
```

## Behavior

### Acceptance criteria

1. First launch generates UUID v4 `deviceId`; second launch returns same id (instrumentation test).
2. `sessionToken` not readable from plain `SharedPreferences` file (encrypted blob).
3. `clearSession()` removes token only; `deviceId` remains unless full data clear—**document product choice** (recommend keep deviceId for memory continuity).

### Edge cases & errors

- EncryptedSharedPreferences failure on broken devices → fallback documented (regenerate deviceId and show error)—rare path

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `apps/android/app/src/main/.../session/SessionStore.kt` | Impl |

## Verification

### Automated

- [ ] Robolectric or instrumented test for persistence

### Manual

- [ ] Force-stop app; relaunch; same device id in server logs

## Notes

- Align `deviceId` field with m0 `session.start` payload.
