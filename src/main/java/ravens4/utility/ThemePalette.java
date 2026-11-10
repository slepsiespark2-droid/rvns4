package ravens4.utility;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * The shared list of animated colour themes (Default, Rainbow, Aurora, ...) used by the theme sliders of the Gui,
 * HUD (DLayout1) and ClickGui2.
 */
public final class ThemePalette {
    private static final String[] THEME_OPTIONS = new String[]{
            "Default", "Rainbow", "Aurora", "Cherry", "Cotton Candy",
            "Flare", "Flower", "Forest", "Frost", "Gold",
            "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
    };

    /** Two-stop gradients (indexed by lower-case theme name) that pulse back and forth every 5 seconds. */
    private static final Map<String, int[]> GRADIENTS = new HashMap<String, int[]>();
    static {
        GRADIENTS.put("aurora", new int[]{0xFF7301C2, 0xFF17F0B1});
        GRADIENTS.put("cherry", new int[]{0xFFDD3D69, 0xFFE0B3B7});
        GRADIENTS.put("cotton candy", new int[]{0xFF92DAE8, 0xFFED68B8});
        GRADIENTS.put("flare", new int[]{0xFFF26B16, 0xFFE4A61D});
        GRADIENTS.put("flower", new int[]{0xFFC89AD8, 0xFFAC59B9});
        GRADIENTS.put("forest", new int[]{0xFF1F7617, 0xFF60A623});
        GRADIENTS.put("frost", new int[]{0xFFDFE3E3, 0xFFBCC5CA});
        GRADIENTS.put("gold", new int[]{0xFFE5DF30, 0xFFDADAB6});
        GRADIENTS.put("grayscale", new int[]{0xFF616368, 0xFFE7E8EA});
        GRADIENTS.put("inferno", new int[]{0xFF350000, 0xFFC03912});
        GRADIENTS.put("royal", new int[]{0xFF85BFE8, 0xFF1D3D87});
        GRADIENTS.put("sandstorm", new int[]{0xFF9D9369, 0xFFF5E3B4});
        GRADIENTS.put("sky", new int[]{0xFF81EAF8, 0xFF15BCD3});
        GRADIENTS.put("vine", new int[]{0xFF27E439, 0xFF9AF8A1});
    }

    private static final int DEFAULT_ACCENT = 0xFFFF6A1A;
    private static final long THEME_PERIOD_MS = 5000L;

    private ThemePalette() {
    }

    /** The theme names (index 0 is "Default"). */
    public static String[] getThemeOptions() {
        return THEME_OPTIONS.clone();
    }

    /**
     * Colour of a theme at the current time. {@code phase} (0..1) shifts the animation by a fraction of its 5 second
     * cycle, so two calls with 0 and 0.5 give the opposite ends of a pulsing gradient.
     */
    public static int getThemeColor(int themeIndex, long now, float phase) {
        int index = Math.max(0, Math.min(THEME_OPTIONS.length - 1, themeIndex));
        return getThemeColor(index, now + (long) (phase * THEME_PERIOD_MS));
    }

    private static int getThemeColor(int themeIndex, long now) {
        if (themeIndex <= 0) {
            return DEFAULT_ACCENT;
        }

        String name = THEME_OPTIONS[themeIndex].toLowerCase();
        if (name.equals("rainbow")) {
            return Color.HSBtoRGB((now % THEME_PERIOD_MS) / (float) THEME_PERIOD_MS, 1.0f, 1.0f);
        }

        int[] gradient = GRADIENTS.get(name);
        if (gradient == null) {
            return DEFAULT_ACCENT;
        }
        return lerpColor(gradient[0], gradient[1], getWaveRatio(now));
    }

    /** Triangle wave 0 -> 1 -> 0 over the theme period. */
    private static double getWaveRatio(long now) {
        float time = (now % THEME_PERIOD_MS) / (float) THEME_PERIOD_MS;
        return time <= 0.5f ? time * 2.0 : 2.0 - time * 2.0;
    }

    private static int lerpColor(int c1, int c2, double t) {
        int r = clampInt((int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t)), 0, 255);
        int g = clampInt((int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t)), 0, 255);
        int b = clampInt((int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t)), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
