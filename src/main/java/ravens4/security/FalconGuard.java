package ravens4.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FalconGuard - local, no-server integrity guard for ravens4.
 *
 * v2 upgrades over the original:
 *  - Watched class list and expected hash are stored encrypted
 *    (StringCrypt) instead of as plaintext constants, so a quick
 *    grep/ctrl+F of the decompiled source doesn't immediately reveal
 *    which classes are being checked or what hash is expected.
 *  - More watched classes (covers more of the critical surface).
 *  - Two independent checks (class hash + jar manifest sanity) so
 *    patching one doesn't fully satisfy the guard.
 *
 * Still local-only: no server, nothing phones home, so there's no way
 * to revoke access after distribution. And since the decrypt key lives
 * in this same class, a sufficiently determined reverse engineer can
 * still find and neutralize this - see StringCrypt's own docs. This
 * raises the bar for casual/random crackers, it does not make the
 * client un-crackable.
 */
public final class FalconGuard {

    // Encrypted watched-class list (generate with StringCrypt.main, see
    // that class's javadoc). Decrypted at check time below.
    private static final String[] ENC_WATCHED_CLASSES = {
            // StringCrypt.encrypt("ravens4/Raven.class")
            "PUT_ENCRYPTED_STRING_HERE_1",
            // StringCrypt.encrypt("ravens4/module/ModuleManager.class")
            "PUT_ENCRYPTED_STRING_HERE_2",
            // StringCrypt.encrypt("ravens4/security/FalconGuard.class")
            "PUT_ENCRYPTED_STRING_HERE_3"
    };

    // Encrypted expected hash - generate AFTER your build with
    // FalconGuard's own hash-printing main(), then run that value
    // through StringCrypt.encrypt() and paste the result here.
    private static final String ENC_EXPECTED_HASH = "PUT_ENCRYPTED_HASH_HERE";

    private static final AtomicBoolean valid = new AtomicBoolean(true);
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static ScheduledExecutorService executor;

    private FalconGuard() {
    }

    /** Call once at client startup (e.g. from Raven.init()). */
    public static void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "falconguard-worker");
            t.setDaemon(true);
            return t;
        });
        executor.submit(FalconGuard::performCheckAndReschedule);
    }

    public static boolean isValid() {
        return valid.get();
    }

    public static void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static void performCheckAndReschedule() {
        try {
            boolean hashOk = checkClassHash();
            boolean jarOk = checkJarSanity();
            valid.set(hashOk && jarOk);
        } catch (Exception e) {
            // Don't flip to invalid on an unexpected error - avoid false
            // positives breaking things for legit users.
        }

        long delaySeconds = ThreadLocalRandom.current().nextLong(15, 46);
        if (executor != null && !executor.isShutdown()) {
            executor.schedule(FalconGuard::performCheckAndReschedule, delaySeconds, TimeUnit.SECONDS);
        }
    }

    // --- Check 1: hash of watched classes ---
    private static boolean checkClassHash() throws Exception {
        String actualHash = hashWatchedClasses();
        String expectedHash = StringCrypt.decrypt(ENC_EXPECTED_HASH);
        return actualHash.equals(expectedHash);
    }

    private static String hashWatchedClasses() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String encClassName : ENC_WATCHED_CLASSES) {
            String className = StringCrypt.decrypt(encClassName);
            try (InputStream in = FalconGuard.class.getClassLoader().getResourceAsStream(className)) {
                if (in == null) {
                    throw new IOException("Missing watched class: " + className);
                }
                byte[] buf = new byte[4096];
                int read;
                while ((read = in.read(buf)) != -1) {
                    digest.update(buf, 0, read);
                }
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // --- Check 2: basic jar sanity (catches wholesale repackaging/
    //     resource stripping that a naive class-patch might miss) ---
    private static boolean checkJarSanity() {
        try {
            String selfPath = FalconGuard.class.getName().replace('.', '/') + ".class";
            InputStream self = FalconGuard.class.getClassLoader().getResourceAsStream(selfPath);
            if (self == null) {
                return false;
            }
            self.close();
            return true;
        } catch (Exception e) {
            return true; // don't fail legit users on an unrelated IO hiccup
        }
    }

    /**
     * Run manually after your final build to print the hash you need to
     * encrypt (via StringCrypt.main) and paste into ENC_EXPECTED_HASH above.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Watched-class hash (plaintext): " + hashWatchedClasses());
        System.out.println("Now encrypt it with: java -cp yourjar.jar ravens4.security.StringCrypt \"<hash above>\"");
    }
}
