package ravens4.utility;

import ravens4.Raven;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.client.rise.Interface;
import ravens4.module.impl.client.theme.ThemeSettings;
import ravens4.module.setting.Setting;
import ravens4.module.setting.impl.ColorSetting;
import ravens4.module.setting.impl.SliderSetting;

/**
 * The client-wide theme. The themes are the Rise interface themes; the one that is selected is shared by the
 * Rise interface, the Theme category of the click gui and (optionally) every render module that has colours.
 */
public final class ClientTheme {
    private ClientTheme() {
    }

    public static String[] getNames() {
        return Interface.themeNames();
    }

    public static int getSelectedIndex() {
        return Interface.activeThemeIndex();
    }

    public static boolean isSelected(int index) {
        return getSelectedIndex() == index;
    }

    /** Animated colour of the selected theme (RGB, no alpha); any module can use this for its own drawing. */
    public static int getColor(double position) {
        return Interface.themeAccentRgb(position);
    }

    /** Picks a theme (click in the Theme category) and sends it to the render modules. */
    public static void select(int index) {
        Interface.setActiveThemeIndex(index);
        applyToRenderModules();
        if (Raven.currentProfile != null) {
            Raven.currentProfile.getModule().saved = false;
        }
    }

    /**
     * Writes the selected theme into every render module: "Color"/"Line"/"Default" get the first colour,
     * "Color 2"/"Marker" the second one, and "Color mode" sliders switch to the matching gradient/rainbow
     * option (or to the option with the theme's own name, e.g. Target HUD's Cherry or Royal).
     */
    public static void applyToRenderModules() {
        if (!ThemeSettings.isApplyToRenderEnabled() || ModuleManager.modules == null) {
            return;
        }
        int index = getSelectedIndex();
        String name = getNames()[index];
        boolean rainbow = Interface.themeIsRainbow(index);
        int[] rgb = Interface.themeRgb(index);

        for (Module module : ModuleManager.modules) {
            if (module == null || module.moduleCategory() != Module.category.render) {
                continue;
            }
            boolean changed = false;
            for (Setting setting : module.getSettings()) {
                if (setting instanceof ColorSetting) {
                    String n = setting.getName();
                    ColorSetting color = (ColorSetting) setting;
                    if (n.equals("Color") || n.equals("Line") || n.equals("Default")) {
                        setRgb(color, rgb[0]);
                        changed = true;
                    }
                    else if (n.equals("Color 2") || n.equals("Marker")) {
                        setRgb(color, rgb[1]);
                        changed = true;
                    }
                }
                else if (setting instanceof SliderSetting && setting.getName().equals("Color mode")) {
                    int option = findColorMode((SliderSetting) setting, name, rainbow);
                    if (option >= 0) {
                        ((SliderSetting) setting).setValue(option);
                        changed = true;
                    }
                }
            }
            if (changed) {
                module.guiUpdate();
            }
        }
    }

    private static void setRgb(ColorSetting color, int rgb) {
        color.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static int findColorMode(SliderSetting slider, String themeName, boolean rainbow) {
        String[] options = slider.getOptions();
        if (options == null) {
            return -1;
        }
        String wanted = themeName.replace(" ", "").toLowerCase();
        for (int i = 0; i < options.length; i++) {
            if (options[i].replace(" ", "").toLowerCase().equals(wanted)) {
                return i;
            }
        }
        String[] preferred = rainbow ? new String[]{"Rainbow", "Chroma", "Gradient"} : new String[]{"Gradient"};
        for (String p : preferred) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equalsIgnoreCase(p)) {
                    return i;
                }
            }
        }
        return -1;
    }
}
