# FalconGuard Layer 3 (native) — build guide

This layer moves the hash *comparison* itself into compiled native code
(C), so a Java decompiler can never see what value your hash is being
checked against — only compiled machine code holds that secret.

## Why this needs per-OS builds
Minecraft runs on Windows, macOS, and Linux. Each needs its own compiled
binary (`.dll`, `.dylib`, `.so`) — C doesn't cross-compile to a universal
format the way Java bytecode does. You ship all three inside your jar;
at runtime, `FalconGuardNative` picks the right one for whatever OS the
player is on.

## Recommended: let GitHub Actions build them
This repo includes `.github/workflows/build-natives.yml`, which builds
all 3 platforms automatically using free GitHub-hosted runners (which
already have the compilers and JDK headers you need).

1. Go to Actions tab → "Build FalconGuard Natives" → "Run workflow"
2. Enter the `expected_hash` input: the SHA-256 hex string from running
   `FalconGuard.main()` against your built jar (same hash you'd otherwise
   encrypt with StringCrypt for Layer 2)
3. Wait for the 3 matrix jobs to finish
4. Download each artifact (`falconguard-native-linux`,
   `falconguard-native-macos`, `falconguard-native-windows`)
5. Place each file into your repo at:
   ```
   src/main/resources/natives/linux/libfalconguard.so
   src/main/resources/natives/macos/libfalconguard.dylib
   src/main/resources/natives/windows/falconguard.dll
   ```
6. Commit those binary files, rebuild your mod jar normally — they'll be
   bundled as resources automatically.

## Manual builds (if you'd rather not use CI)
You need a JDK (not just a JRE — must include `include/jni.h`) and a C
compiler on each OS you want to target.

**Linux:**
```
gcc -shared -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -DFALCONGUARD_HASH="\"<your hash>\"" \
  natives/falconguard.c -o src/main/resources/natives/linux/libfalconguard.so
```

**macOS:**
```
gcc -shared -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" \
  -DFALCONGUARD_HASH="\"<your hash>\"" \
  natives/falconguard.c -o src/main/resources/natives/macos/libfalconguard.dylib
```

**Windows (Developer Command Prompt for VS):**
```
cl /LD /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
  /DFALCONGUARD_HASH=\"<your hash>\" ^
  natives\falconguard.c /Fe:src\main\resources\natives\windows\falconguard.dll
```

## Important
- Re-run this **every time** your watched classes' bytecode changes
  (same rule as Layer 2's hash) — the hash baked into the native lib
  needs to match what Java computes at runtime, or FalconGuard will
  flag your own legitimate build as tampered.
- This layer is additive, not a replacement — keep Layers 1 (ProGuard)
  and 2 (FalconGuard hash + StringCrypt) as they are.
