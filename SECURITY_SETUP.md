# Arming FalconGuard v2 — one-time setup

FalconGuard ships "soft" by default (always reports valid) until you fill in
the encrypted constants below. Do this once per release.

## Step 1 — Build normally first (no ProGuard yet)
```
./gradlew build
```

## Step 2 — Get the plaintext class paths encrypted
Run StringCrypt against each watched class path to get its encrypted form:
```
java -cp build/libs/<modid>-non-obfuscated-with-deps.jar ravens4.security.StringCrypt "ravens4/Raven.class"
java -cp build/libs/<modid>-non-obfuscated-with-deps.jar ravens4.security.StringCrypt "ravens4/module/ModuleManager.class"
java -cp build/libs/<modid>-non-obfuscated-with-deps.jar ravens4.security.StringCrypt "ravens4/security/FalconGuard.class"
```
Each prints an `Encrypted : ...` line. Paste those three values into
`FalconGuard.java`, replacing `PUT_ENCRYPTED_STRING_HERE_1/2/3` in
`ENC_WATCHED_CLASSES`, in the same order.

## Step 3 — Get the real class hash
Run the remapped jar (after Loom's remapJar, before ProGuard) through
FalconGuard's own hash printer:
```
java -cp build/libs/<modid>-<version>.jar ravens4.security.FalconGuard
```
This prints `Watched-class hash (plaintext): <hex>`.

## Step 4 — Encrypt the hash
```
java -cp build/libs/<modid>-non-obfuscated-with-deps.jar ravens4.security.StringCrypt "<hex hash from step 3>"
```
Paste the resulting `Encrypted : ...` value into `FalconGuard.java`,
replacing `PUT_ENCRYPTED_HASH_HERE` in `ENC_EXPECTED_HASH`.

## Step 5 — Rebuild everything, including the protected jar
```
./gradlew buildProtected
```
`build/libs/<modid>-protected.jar` is now armed: FalconGuard will flip
`isValid()` to `false` within 15–45s if any watched class is modified
after this point.

## Step 6 (optional) — Arm Layer 3 (native)
Layer 3 ships disabled (`ENABLE_NATIVE_LAYER = false`) until you've built
native libraries for all 3 platforms. Full instructions: `natives/NATIVE_BUILD.md`.
Short version:
1. Go to your repo's Actions tab → "Build FalconGuard Natives" → Run workflow
2. Paste in the same plaintext hash from Step 3 above
3. Download the 3 resulting artifacts, place them at
   `src/main/resources/natives/{linux,macos,windows}/...`
4. Commit those binary files
5. In `FalconGuard.java`, flip `ENABLE_NATIVE_LAYER` to `true`
6. Rebuild

## Important
Repeat steps 1–6 **every time you change** `Raven.java`, `ModuleManager.java`,
or `FalconGuard.java` itself — their compiled bytecode changes, so the hash
from a previous build will no longer match and FalconGuard will (correctly)
flag your own legitimate new build as tampered. If Layer 3 is armed, you
must also rebuild the native libs (step 6) whenever the hash changes, since
the old binaries would be checking against a stale value.
