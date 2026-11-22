package ravens4.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * FalconGuard Layer 3 - native integrity check.
 *
 * The actual hash-compare logic lives in a small C file (falconguard.c),
 * compiled per-OS into a native library. A decompiler can read the Java
 * bytecode around this class all day - it will never see the comparison
 * logic itself, because it isn't Java bytecode at all.
 *
 * How this class works:
 *  1. The platform-specific native library is bundled as a resource
 *     (src/main/resources/natives/&lt;platform&gt;/libfalconguard.*)
 *  2. At runtime, it's extracted to a temp file and loaded via
 *     System.load()
 *  3. nativeCheck() calls into the C function, which does the real
 *     comparison and returns true/false - the expected hash lives only
 *     in the compiled native binary, never in Java bytecode or strings.
 *
 * Build the native library per-platform using the provided
 * falconguard.c + the build commands in NATIVE_BUILD.md. This class
 * fails CLOSED if the native lib can't be loaded (e.g. missing/corrupt
 * resource), since a missing native lib on legitimate builds should be
 * treated as a packaging bug you want to notice, not silently ignore.
 */
public final class FalconGuardNative {

    private static boolean loaded = false;
    private static boolean loadFailed = false;

    private FalconGuardNative() {
    }

    private static synchronized void ensureLoaded() {
        if (loaded || loadFailed) {
            return;
        }
        try {
            String platform = detectPlatform();
            String libName = platform.startsWith("windows") ? "falconguard.dll"
                    : platform.startsWith("mac") ? "libfalconguard.dylib"
                    : "libfalconguard.so";

            String resourcePath = "/natives/" + platform + "/" + libName;
            InputStream in = FalconGuardNative.class.getResourceAsStream(resourcePath);
            if (in == null) {
                loadFailed = true;
                return;
            }

            File temp = File.createTempFile("falconguard", libSuffix(libName));
            temp.deleteOnExit();
            try (OutputStream out = Files.newOutputStream(temp.toPath())) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
            } finally {
                in.close();
            }

            System.load(temp.getAbsolutePath());
            loaded = true;
        } catch (IOException | UnsatisfiedLinkError e) {
            loadFailed = true;
        }
    }

    private static String libSuffix(String libName) {
        int dot = libName.lastIndexOf('.');
        return dot >= 0 ? libName.substring(dot) : ".bin";
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        return "linux";
    }

    /**
     * Runs the native integrity check. Pass the current SHA-256 hash
     * (hex string) of your watched classes - computed in Java as before
     * (FalconGuard.hashWatchedClasses-equivalent) - and the native side
     * compares it against the value compiled into the library, without
     * that expected value ever existing as a Java string or constant.
     *
     * Returns false (fails closed) if the native library isn't loaded.
     */
    public static boolean nativeCheck(String currentHashHex) {
        ensureLoaded();
        if (!loaded) {
            return false;
        }
        try {
            return checkHash(currentHashHex);
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /** JNI native method - implemented in falconguard.c */
    private static native boolean checkHash(String currentHashHex);
}
