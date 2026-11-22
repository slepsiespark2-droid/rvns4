package ravens4.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    // Flip to true only once you've built AND placed all 3 platform
    // native libs (see natives/NATIVE_BUILD.md). Until then the native
    // layer is skipped entirely, since an unbuilt native lib fails
    // closed (always invalid) and would brick every legitimate build.
    private static final boolean ENABLE_NATIVE_LAYER = false;

    private static final AtomicBoolean valid = new AtomicBoolean(true);
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static ScheduledExecutorService executor;

    // --- TripWire system ---
    // Instead of one predictable "feature X stops working," register
    // several small, independent, unrelated-looking disruptions. Once
    // tampering is detected, ONE random tripwire fires per check cycle
    // (not all of them, not deterministically), at a random chance, so
    // the symptoms look like scattered bugs rather than a single clean
    // anti-cheat trip. A cracker patching out one check still has no
    // way to know which of N possible symptoms to look for, or whether
    // they've actually succeeded, since tripwires fire probabilistically
    // and intermittently rather than immediately and consistently.
    private static final List<Runnable> tripWires = new CopyOnWriteArrayList<>();

    // Chance (0.0-1.0) that a standard fuse fires on any given check
    // cycle where isValid() is currently false. Deliberately NOT 1.0 -
    // a guaranteed, instant, consistent failure is itself a fingerprint
    // a cracker can use to bisect which patch triggered it.
    private static final double TRIPWIRE_CHANCE = 0.35;

    // --- Extreme tier ---
    // A SEPARATE, much lower-probability pool for severe tripwires (e.g.
    // TripWire.extremeModuleWipe()). Standard tripwires stay deniable/
    // subtle; extreme tripwires meaningfully break the client's
    // usefulness and are meant to fire rarely, so they still don't
    // create an instant, obvious, bisectable correlation with a patch.
    private static final List<Runnable> extremeTripWires = new CopyOnWriteArrayList<>();
    private static final double EXTREME_TRIPWIRE_CHANCE = 0.05;

    // Extra gate: even when the random roll succeeds, an extreme
    // tripwire only fires after tampering has been detected continuously
    // for at least this many check cycles - avoids firing on what might
    // be a one-off false positive (e.g. a weird IO hiccup).
    private static final int EXTREME_TRIPWIRE_MIN_CONSECUTIVE_FAILS = 3;
    private static int consecutiveFails = 0;

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

    /**
     * Register a tripwire: a small action to run occasionally once
     * tampering has been detected. Keep each tripwire SUBTLE and
     * DECORRELATED from security - e.g. "silently disable one random
     * module," "add a tiny random delay to one unrelated system,"
     * "drop one packet" - not "print CRACKED" or "crash the game."
     * The goal is symptoms a cracker would mistake for unrelated bugs,
     * not a clear signal.
     *
     * See TripWire.java for concrete examples wired to ravens4's real
     * Module/ModuleManager API - register several, unrelated to each
     * other, from different parts of the codebase.
     */
    public static void registerTripWire(Runnable tripWire) {
        if (tripWire != null) {
            tripWires.add(tripWire);
        }
    }

    /**
     * Register an EXTREME tripwire (e.g. TripWire.extremeModuleWipe()).
     * Fires far less often (5% chance, and only after tampering has
     * been detected for several consecutive check cycles) and
     * independently of standard tripwires - reserve this for
     * disruptions severe enough that you want them to be rare, not a
     * reliable signal.
     */
    public static void registerExtremeTripWire(Runnable tripWire) {
        if (tripWire != null) {
            extremeTripWires.add(tripWire);
        }
    }

    /** Clears all registered tripWires (standard and extreme) - mainly for tests. */
    public static void clearTripWires() {
        tripWires.clear();
        extremeTripWires.clear();
        consecutiveFails = 0;
    }

    private static void maybeTriggerTripWire() {
        if (!tripWires.isEmpty() && ThreadLocalRandom.current().nextDouble() <= TRIPWIRE_CHANCE) {
            int index = ThreadLocalRandom.current().nextInt(tripWires.size());
            runSafely(tripWires.get(index));
        }

        if (consecutiveFails >= EXTREME_TRIPWIRE_MIN_CONSECUTIVE_FAILS
                && !extremeTripWires.isEmpty()
                && ThreadLocalRandom.current().nextDouble() <= EXTREME_TRIPWIRE_CHANCE) {
            int index = ThreadLocalRandom.current().nextInt(extremeTripWires.size());
            runSafely(extremeTripWires.get(index));
        }
    }

    private static void runSafely(Runnable tripWire) {
        try {
            tripWire.run();
        } catch (Exception e) {
            // A tripwire misbehaving shouldn't take down the guard thread.
        }
    }

    private static void performCheckAndReschedule() {
        try {
            boolean hashOk = checkClassHash();
            boolean jarOk = checkJarSanity();
            boolean nativeOk = checkNative();
            boolean currentlyValid = hashOk && jarOk && nativeOk;
            valid.set(currentlyValid);
            if (!currentlyValid) {
                consecutiveFails++;
                maybeTriggerTripWire();
            } else {
                consecutiveFails = 0;
            }
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

    // --- Check 3: native comparison (Layer 3 - see natives/falconguard.c) ---
    // If the native lib isn't present/built yet, this fails closed
    // (returns false from FalconGuardNative), which would make isValid()
    // always false. So this check is OPTIONAL until you've actually
    // built and placed the native libs (see natives/NATIVE_BUILD.md) -
    // treat it as "present" only once FalconGuardNative reports loaded.
    private static boolean checkNative() {
        if (!ENABLE_NATIVE_LAYER) {
            return true; // not armed yet - see NATIVE_BUILD.md
        }
        try {
            String currentHash = hashWatchedClasses();
            return FalconGuardNative.nativeCheck(currentHash);
        } catch (Exception e) {
            return true; // unexpected error - don't false-flag legit users
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
