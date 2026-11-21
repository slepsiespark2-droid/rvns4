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
 * How it works:
 *  - At build time, you generate a hash of your critical classes and
 *    hardcode it below (EXPECTED_HASH) - do this as the LAST build step,
 *    after obfuscation (if any) has already run.
 *  - At runtime, FalconGuard re-hashes those same classes from the
 *    running jar and compares. If someone edited/patched a class
 *    (e.g. removed a check), the hash won't match.
 *
 * Runs entirely on a background daemon thread with a jittered 15-45s
 * recheck interval - never touches the render/tick thread.
 *
 * Limitations (be aware):
 *  - Because everything needed to verify AND bypass this lives inside
 *    the jar itself, a determined cracker can find and patch this
 *    exact check out. It stops casual/random crackers, not experts.
 *  - There's no way to revoke access after distribution since nothing
 *    phones home.
 */
public final class FalconGuard {

    // Replace with the real hash of your compiled classes.
    // Generate it by running main() below after each build.
    private static final String EXPECTED_HASH =
            "PUT_YOUR_GENERATED_HASH_HERE";

    // Which classes to verify - list your most critical ones.
    private static final String[] WATCHED_CLASSES = {
            "ravens4/Raven.class",
            "ravens4/module/ModuleManager.class"
    };

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
            String actualHash = hashWatchedClasses();
            valid.set(actualHash.equals(EXPECTED_HASH));
        } catch (Exception e) {
            // Don't flip to invalid on an unexpected hashing error -
            // avoid false positives breaking things for legit users.
        }

        long delaySeconds = ThreadLocalRandom.current().nextLong(15, 46);
        if (executor != null && !executor.isShutdown()) {
            executor.schedule(FalconGuard::performCheckAndReschedule, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private static String hashWatchedClasses() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String className : WATCHED_CLASSES) {
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

    /**
     * Run manually after your final build to print the hash you need to
     * paste into EXPECTED_HASH above, then rebuild once more with it baked in.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Watched-class hash: " + hashWatchedClasses());
    }
}
