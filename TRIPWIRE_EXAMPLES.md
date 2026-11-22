# FalconGuard TripWires — how they work

A "tripwire" is a small, subtle disruption that fires occasionally once
tampering is detected. The real implementations live in
`ravens4/security/TripWire.java`, wired to your actual `Module`/
`ModuleManager` API.

## Two tiers

**Standard** (35% chance per check cycle, ~every 15-45s):
```java
FalconGuard.registerTripWire(TripWire.randomModuleToggle());
FalconGuard.registerTripWire(TripWire.tinyRenderStutter());
```
- `randomModuleToggle()` — silently disables one random currently-enabled
  module. Reads as "huh, that turned off" rather than a crash.
- `tinyRenderStutter()` — a 40-120ms sleep. Reads as lag, not a block.

**Extreme** (5% chance, only after 3+ *consecutive* failed checks):
```java
FalconGuard.registerExtremeTripWire(TripWire.extremeModuleWipe());
```
- `extremeModuleWipe()` — disables every currently-enabled module at
  once. Much more disruptive; kept rare and gated behind sustained
  failure so a one-off false positive can't trigger it.

All three are already registered in `Raven.java` right next to
`FalconGuard.start()`.

## Good tripwire qualities
- **Deniable** — could plausibly be a real bug, lag spike, or glitch
- **Non-destructive** — never corrupts the player's world/account/files
- **Unrelated to each other** — don't all touch the same system, or a
  cracker can bisect which check they trace back to
- **Intermittent** — the probabilities above are deliberate; a guaranteed,
  instant, consistent symptom is itself a fingerprint a cracker can bisect

## Bad tripwire qualities (avoid)
- Anything that prints "CRACKED" / "TAMPERED" / references FalconGuard
- Anything that crashes the game outright (too obvious, too easy to
  correlate to a specific recent change)
- Anything that could look like malware to someone scanning network/file
  activity (never touch the player's files outside your own mod, never
  make unexpected network calls)

## Adding more
Add a new method to `TripWire.java` returning a `Runnable`, then register
it with `FalconGuard.registerTripWire(...)` (standard) or
`FalconGuard.registerExtremeTripWire(...)` (extreme) in `Raven.java`.
Keep new ones decorrelated from existing ones — don't have two tripwires
touch the same system, or a cracker who notices one pattern effectively
notices both.
