# FalconGuard Layer 1 - ProGuard rules for ravens4
# Applied to the already-remapped (deobfuscated to MCP names) jar.

-dontoptimize
# ^ NOTE: Forge 1.8.9 + mixins are picky about aggressive optimization
# reordering/inlining code around reflection and Forge's classloading.
# Start safe (no optimize), then try removing this line once you've
# verified a build actually launches correctly.

-dontshrink
# ^ Shrinking removes "unused" code, but Forge/Mixin use a lot of
# reflection and annotation-driven loading that looks unused to
# ProGuard. Safer to just obfuscate names, not delete anything, unless
# you want to spend time writing -keep rules for every reflective path.

# -repackageclasses, -flattenpackagehierarchy, and -allowaccessmodification
# removed: these aggressively move/merge classes across package
# boundaries and relax access checks, which has repeatedly broken
# mixin-woven bytecode in this project (multiple VerifyErrors even
# after keeping net.minecraft/forge/lwjgl). Mixin weaving happens at
# RUNTIME (Mixin's launch-time transformer re-reads class bytecode and
# injects it into the target), so anything that changes package layout
# or access modifiers between build and that weave step is risky here.
# Dropping these three is a meaningfully weaker Layer 1 (names still
# get shortened, but classes stay in their original packages and
# access levels are untouched) in exchange for actually launching.
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable,*Annotation*,Signature

# --- Keep your ENTIRE codebase untouched for now ---
# Mixin-woven bytecode can reference any ravens4 class by exact type
# (casts, field types, method params) - not just the mixin classes
# themselves. Renaming any of those causes the same VerifyError we've
# been chasing. This is a meaningfully weaker Layer 1 than originally
# planned (your own code isn't renamed at all right now), but it's the
# safe baseline to get an actually-launching protected build. Once this
# launches clean, we can try narrowing this back down to specific
# "known safe to rename" classes one at a time.
-keep class ravens4.** {
    public *;
}

# --- Keep anything Forge/FML loads by reflection/annotation ---
-keep @net.minecraftforge.fml.common.Mod class * { *; }
-keepclassmembers class * {
    @net.minecraftforge.fml.common.Mod.EventHandler *;
}

# --- Keep the Mixin FRAMEWORK library itself untouched ---
# Forge's tweaker chain loads org.spongepowered.asm.launch.MixinTweaker
# by its exact class name (referenced from the jar's manifest /
# FMLTweaker config), not just by reflection inside your own code.
# Renaming anything under org.spongepowered.asm.** breaks launch
# entirely with a ClassNotFoundException, since the loader is looking
# for that literal name and it no longer exists under it.
-keep class org.spongepowered.asm.** { *; }
-keepclassmembers class org.spongepowered.asm.** { *; }

# --- Same reasoning for other tweaker-chain / reflection-sensitive
#     third-party libs bundled into the shaded jar ---
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# Note: FalconGuard, StringCrypt, FalconGuardNative, TripWire, and all
# mixin classes are already covered by the broad `ravens4.**` keep
# above - no need for separate rules for them right now. (Once we get
# a clean launch, we can look at carving out narrower obfuscation for
# specific classes that nothing else references by exact type.)

# --- NEVER rename Minecraft/Forge classes ---
# Mixins are woven into Minecraft's bytecode at compile/remap time,
# BEFORE ProGuard runs - that woven bytecode hardcodes exact type
# references (e.g. net.minecraft.util.IChatComponent). If ProGuard
# later renames those classes, the already-woven mixin bytecode still
# points at the old names, and the JVM verifier rejects it at launch
# with a VerifyError ("Bad type on operand stack"). Keep the ENTIRE
# Minecraft/Forge/LWJGL surface completely untouched.
-keep class net.minecraft.** { *; }
-keep class net.minecraftforge.** { *; }
-keep class org.lwjgl.** { *; }
-keepnames class net.minecraft.** { *; }
-keepnames class net.minecraftforge.** { *; }

# --- Don't warn about libs you don't control ---
# The shaded jar pulls in a lot of classes (LWJGL, Netty, mixin, etc.)
# that ProGuard can't fully resolve against a single rt.jar library set.
# Broadly suppressing warnings (not errors) is standard practice for
# shaded/fat jars like this - it only silences "can't find referenced
# class" notes, it doesn't skip real compile errors.
-dontwarn **
-ignorewarnings
-dontnote **
