# 🛡️ ravens4 Security

ravens4 ships with a three-layer protection system to keep the client from being casually cracked, forked, or tampered with.

## Layer 1 — ProGuard
Class/method/field names and string literals are scrambled at build time via ProGuard. Someone decompiling the jar gets a mess of meaningless names (`a`, `b`, `c`...) instead of readable source — raises the effort bar before anyone can even understand what they're looking at, let alone patch it.

## Layer 2 — FalconGuard (v2)
`ravens4.security.FalconGuard` is a lightweight runtime integrity guard:

- Hashes the client's own critical classes (`SHA-256`) and compares against a known-good value baked in at build time
- Watched class list and expected hash are stored **encrypted** (via `StringCrypt`), not as plaintext constants — defeats a quick grep/ctrl+F of decompiled source
- Two independent checks: class hash + jar/classloader sanity — patching one doesn't fully satisfy the guard
- Runs entirely on a background daemon thread — **zero cost to FPS or the render/tick loop**
- Re-checks on a random jittered interval (15–45s), so there's no fixed timing pattern to find and patch around
- Fails *soft* — if tampering is detected, it doesn't crash or print "CRACKED!" anywhere. It just quietly flips an internal flag that scattered call-sites check, so a cracker can't easily tell which check caught them or where
- **TripWire system (two tiers)**:
  - **Standard** — once invalid, 35% chance per check cycle to fire ONE randomly-picked tripwire (e.g. `TripWire.randomModuleToggle()`, `TripWire.tinyRenderStutter()`) — small, deniable disruptions that read as random bugs, not an anti-cheat trip.
  - **Extreme** — a separate, rarer pool (5% chance, and only after 3+ *consecutive* failed check cycles, to avoid acting on a one-off false positive). `TripWire.extremeModuleWipe()` disables every currently-enabled module at once — a severe, "last resort" response reserved for sustained tampering, kept rare so it isn't an instant giveaway either.

## StringCrypt
`ravens4.security.StringCrypt` - lightweight XOR+Base64 obfuscation for any sensitive constant (hashes, class paths, future license strings). Not cryptographically strong, but stops naive grep-based reading of decompiled source. Pairs with FalconGuard above.

## Layer 3 — Native integrity check
`ravens4.security.FalconGuardNative` + `natives/falconguard.c`. The actual hash *comparison* runs as compiled native code (`.so`/`.dll`/`.dylib`, one per OS), not Java bytecode — a decompiler can read every line of the Java side and still never see what value is being checked against, since that value only exists inside the compiled machine code.

- **Status: built but disabled by default** (`ENABLE_NATIVE_LAYER = false` in `FalconGuard.java`) until the native libraries are actually built and placed — see `natives/NATIVE_BUILD.md`. A `.github/workflows/build-natives.yml` workflow builds all 3 platforms for you via GitHub-hosted runners.
- Once armed, this becomes a 3rd independent check alongside the class-hash and jar-sanity checks — an attacker now has to defeat all three.

```
                 ┌────────────────────┐
   build time     │   ProGuard         │   scrambles names,
   ───────────▶   │   (Layer 1)        │   strips debug info
                 └─────────┬──────────┘
                           │
                 ┌─────────▼──────────┐
   runtime        │   FalconGuard      │   hashes own classes
   ───────────▶   │   (Layer 2)        │   (encrypted constants),
                 │                    │   jittered recheck loop
                 └─────────┬──────────┘
                           │
                 ┌─────────▼──────────┐
   runtime        │   Native check     │   comparison runs as
   ───────────▶   │   (Layer 3)        │   compiled C, not Java
                 └────────────────────┘
```

**Status:** Layers 1 & 2 armed ✅ — `FalconGuard.start()` runs automatically on client init (see `Raven.java`), ProGuard runs via the `buildProtected` Gradle task. Layer 3 is **built but not yet armed** — see `natives/NATIVE_BUILD.md` to finish setup, then flip `ENABLE_NATIVE_LAYER = true`.

> Three guards, one jar. 🦅
