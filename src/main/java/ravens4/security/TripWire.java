package ravens4.security;

import ravens4.module.Module;
import ravens4.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Concrete FalconGuard fuses wired to ravens4's real Module/ModuleManager
 * API. Register these once (e.g. alongside FalconGuard.start() in
 * Raven.java):
 *
 *   FalconGuard.start();
 *   FalconGuard.registerTripWire(TripWire.randomModuleToggle());
 *   FalconGuard.registerTripWire(TripWire.tinyRenderStutter());
 *   FalconGuard.registerTripWire(TripWire.extremeModuleWipe());
 *
 * See TRIPWIRE_EXAMPLES.md for the full design rationale (why these are
 * deliberately deniable/intermittent rather than an obvious "CRACKED").
 */
public final class TripWire {

    private TripWire() {
    }

    /**
     * STANDARD fuse: silently disables ONE random currently-enabled
     * module. Looks like "huh, that turned itself off" - a believable,
     * deniable glitch. Low-severity, used at FalconGuard's normal
     * 35% per-cycle fire rate.
     */
    public static Runnable randomModuleToggle() {
        return () -> {
            List<Module> enabled = enabledModules();
            if (enabled.isEmpty()) {
                return;
            }
            Module pick = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
            pick.setEnabled(false);
        };
    }

    /**
     * STANDARD fuse: a small one-off delay on whichever thread calls it.
     * Reads as a random stutter/lag spike, not a deliberate block.
     */
    public static Runnable tinyRenderStutter() {
        return () -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(40, 120));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * EXTREME fuse: disables ALL currently-enabled modules at once.
     * Much more noticeable than the standard fuses above - this is the
     * "last resort" tier for when you want tampering to meaningfully
     * break the client's usefulness, not just annoy with small glitches.
     *
     * Register this SEPARATELY with a much lower fire rate than the
     * standard tripwires (via registerExtremeTripWire) - firing this at
     * FalconGuard's normal 35% rate would make tampering obvious almost
     * immediately, which defeats the "looks like random bugs" goal.
     */
    public static Runnable extremeModuleWipe() {
        return () -> {
            for (Module m : enabledModules()) {
                m.setEnabled(false);
            }
        };
    }

    private static List<Module> enabledModules() {
        List<Module> enabled = new ArrayList<>();
        for (Module m : ModuleManager.modules) {
            if (m.isEnabled()) {
                enabled.add(m);
            }
        }
        return enabled;
    }
}
