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

-repackageclasses ''
-allowaccessmodification
-flattenpackagehierarchy ''
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable,*Annotation*,Signature

# --- Keep your mod entry point reachable by Forge ---
-keep class ravens4.Raven {
    public *;
}

# --- Keep anything Forge/FML loads by reflection/annotation ---
-keep @net.minecraftforge.fml.common.Mod class * { *; }
-keepclassmembers class * {
    @net.minecraftforge.fml.common.Mod.EventHandler *;
}

# --- Keep mixins untouched - Mixin matches classes/methods by exact
#     name against mixins.raven.json, so obfuscating mixin classes or
#     their targets will break the mixin applicator ---
-keep class ravens4.mixin.** { *; }

# --- Keep FalconGuard's public API, obfuscate its internals ---
-keep class ravens4.security.FalconGuard {
    public *;
}
-keepclassmembers class ravens4.security.FalconGuard {
    !public *;
}

# --- Don't warn about libs you don't control ---
# The shaded jar pulls in a lot of classes (LWJGL, Netty, mixin, etc.)
# that ProGuard can't fully resolve against a single rt.jar library set.
# Broadly suppressing warnings (not errors) is standard practice for
# shaded/fat jars like this - it only silences "can't find referenced
# class" notes, it doesn't skip real compile errors.
-dontwarn **
-ignorewarnings
-dontnote **
