/*
 * FalconGuard Layer 3 - native integrity check.
 *
 * This is deliberately tiny: it takes the hash Java computed (the
 * SHA-256 of your watched .class files, same as FalconGuard.java
 * already does) and compares it against a value that is baked into
 * THIS compiled binary at build time - never into Java bytecode,
 * never into a Java string. A decompiler reading the jar simply
 * cannot see what it's being compared against.
 *
 * The expected hash is supplied at COMPILE time via the
 * FALCONGUARD_HASH macro (see build commands in NATIVE_BUILD.md / the
 * CI workflow), so it never appears in this source file in plaintext
 * either - only in your build command / CI secret.
 *
 * Build once per platform (Windows .dll, macOS .dylib, Linux .so) -
 * see NATIVE_BUILD.md.
 */

#include <jni.h>
#include <string.h>

#ifndef FALCONGUARD_HASH
/* Fallback so local test builds still compile - ALWAYS override this
 * with the real hash via -DFALCONGUARD_HASH=\"...\" in your real build.
 * Leaving this fallback in place means the check always fails closed
 * (never matches), which is safe but non-functional. */
#define FALCONGUARD_HASH "0000000000000000000000000000000000000000000000000000000000000000"
#endif

JNIEXPORT jboolean JNICALL Java_ravens4_security_FalconGuardNative_checkHash
  (JNIEnv *env, jclass clazz, jstring currentHashHex) {

    if (currentHashHex == NULL) {
        return JNI_FALSE;
    }

    const char *current = (*env)->GetStringUTFChars(env, currentHashHex, NULL);
    if (current == NULL) {
        return JNI_FALSE;
    }

    jboolean result = (strcmp(current, FALCONGUARD_HASH) == 0) ? JNI_TRUE : JNI_FALSE;

    (*env)->ReleaseStringUTFChars(env, currentHashHex, current);
    return result;
}
