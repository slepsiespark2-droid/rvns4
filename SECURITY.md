# 🛡️ ravens4 Security

ravens4 ships with a two-layer protection system to keep the client from being casually cracked, forked, or tampered with.

## Layer 1 — ProGuard
Class/method/field names and string literals are scrambled at build time via ProGuard. Someone decompiling the jar gets a mess of meaningless names (`a`, `b`, `c`...) instead of readable source — raises the effort bar before anyone can even understand what they're looking at, let alone patch it.

## Layer 2 — FalconGuard
`ravens4.security.FalconGuard` is a lightweight runtime integrity guard:

- Hashes the client's own critical classes (`SHA-256`) and compares against a known-good value baked in at build time
- Runs entirely on a background daemon thread — **zero cost to FPS or the render/tick loop**
- Re-checks on a random jittered interval (15–45s), so there's no fixed timing pattern to find and patch around
- Fails *soft* — if tampering is detected, it doesn't crash or print "CRACKED!" anywhere. It just quietly flips an internal flag that scattered call-sites check, so a cracker can't easily tell which check caught them or where

```
                 ┌────────────────────┐
   build time     │   ProGuard         │   scrambles names,
   ───────────▶   │   (Layer 1)        │   strips debug info
                 └─────────┬──────────┘
                           │
                 ┌─────────▼──────────┐
   runtime        │   FalconGuard      │   hashes own classes,
   ───────────▶   │   (Layer 2)        │   jittered recheck loop
                 └────────────────────┘
```

**Status:** armed ✅ — `FalconGuard.start()` runs automatically on client init (see `Raven.java`). ProGuard runs via the `buildProtected` Gradle task (see `build.gradle.kts` + `proguard-rules.pro`), producing `<modid>-protected.jar` in `build/libs/`. CI builds and uploads this protected jar automatically.

> Two guards, one jar. 🦅
