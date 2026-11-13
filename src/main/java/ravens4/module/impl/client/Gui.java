package ravens4.module.impl.client;

import net.minecraftforge.common.MinecraftForge;
import ravens4.Raven;
import ravens4.clickgui.ClickGui2;
import ravens4.clickgui.ClickGui2Host;
import ravens4.clickgui.PanelClickGui;
import ravens4.module.Module;
import ravens4.utility.ThemePalette;
import ravens4.module.setting.Setting;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.Utils;
import ravens4.utility.font.FontManager;
import ravens4.utility.font.RavenFontRenderer;

import java.util.ArrayList;
import java.util.List;

public class Gui extends Module {
    private static final String[] GUI_FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final String[] OUTLINE_THEME_OPTIONS = buildOutlineThemeOptions();
    private static final String[] MODES = new String[]{"ClickGui", "ClickGui2", "Panel"};
    private static final int MODE_CLICKGUI2 = 1;
    private static final int MODE_PANEL = 2;
    /** Last option: the former "Rainbow outlines" button (chroma outline on open / hovered categories). */
    private static final int OLD_RAINBOW_INDEX = OUTLINE_THEME_OPTIONS.length - 1;

    public static SliderSetting guiScale;
    public static SliderSetting font;
    public static SliderSetting backgroundBlur;
    public static SliderSetting scrollSpeed;
    public static ButtonSetting removePlayerModel;
    public static ButtonSetting darkBackground;
    public static ButtonSetting removeWatermark;
    public static SliderSetting outlineTheme;
    public static SliderSetting mode;

    private final ClickGui2Host clickGui2Host;
    /** Settings of the normal click GUI (hidden in ClickGui2 mode) and of ClickGui2 (hidden in ClickGui mode). */
    private final List<Setting> clickGuiSettings = new ArrayList<Setting>();
    private final List<Setting> clickGui2Settings;
    /** Looks of the Panel mode, only shown there. */
    private final List<Setting> panelSettings = new ArrayList<Setting>();
    public static SliderSetting panelOpacity;
    public static ButtonSetting panelShadow;
    public static SliderSetting panelShadowSize;
    public static SliderSetting panelTextOpacity;

    public Gui() {
        super("Gui", category.client, 54);
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(guiScale = new SliderSetting("Gui scale", "x", 1.0, 0.5, 2.0, 0.01));
        this.registerSetting(font = new SliderSetting("Font", 0, GUI_FONT_OPTIONS));
        this.registerSetting(backgroundBlur = new SliderSetting("Background blur", "%", 0, 0, 100, 1));
        this.registerSetting(scrollSpeed = new SliderSetting("Scroll speed", 20, 2, 90, 1));
        this.registerSetting(outlineTheme = new SliderSetting("Outline theme", OLD_RAINBOW_INDEX, OUTLINE_THEME_OPTIONS));
        this.registerSetting(darkBackground = new ButtonSetting("Dark background", true));
        this.registerSetting(removePlayerModel = new ButtonSetting("Remove player model", false));
        this.registerSetting(removeWatermark = new ButtonSetting("Remove watermark", false));
        this.registerSetting(panelOpacity = new SliderSetting("Opacity", "%", 78, 1, 100, 1));
        this.registerSetting(panelShadow = new ButtonSetting("Shadow", true));
        this.registerSetting(panelShadowSize = new SliderSetting("Shadow size", 8, 1, 50, 1));
        this.registerSetting(panelTextOpacity = new SliderSetting("Text opacity", "%", 100, 1, 100, 1));
        ButtonSetting resetLayout = new ButtonSetting("Reset panel layout", new Runnable() {
            @Override
            public void run() {
                PanelClickGui.get().resetLayout();
            }
        });
        this.registerSetting(resetLayout);
        panelSettings.add(panelOpacity);
        panelSettings.add(panelShadow);
        panelSettings.add(panelShadowSize);
        panelSettings.add(panelTextOpacity);
        panelSettings.add(resetLayout);
        for (Setting setting : panelSettings) {
            setting.visible = false; // shown by guiUpdate once the Panel mode is selected
        }
        clickGuiSettings.add(guiScale);
        clickGuiSettings.add(font);
        clickGuiSettings.add(backgroundBlur);
        clickGuiSettings.add(scrollSpeed);
        clickGuiSettings.add(outlineTheme);
        clickGuiSettings.add(darkBackground);
        clickGuiSettings.add(removePlayerModel);
        clickGuiSettings.add(removeWatermark);

        // ClickGui2 (the Solace menu): its settings live here and only show in that mode
        ClickGui2 clickGui2 = new ClickGui2();
        this.clickGui2Settings = clickGui2.register(this);
        for (Setting setting : clickGui2Settings) {
            setting.visible = false;
        }
        this.clickGui2Host = new ClickGui2Host(clickGui2);
        MinecraftForge.EVENT_BUS.register(clickGui2Host);
    }

    @Override
    public void guiUpdate() {
        boolean clickGui2 = isClickGui2();
        boolean panel = isPanel();
        for (Setting setting : clickGuiSettings) {
            // the accent theme is shared with the Panel mode, everything else only belongs to the normal ClickGui
            boolean accentOnly = setting == outlineTheme;
            setting.setVisible(accentOnly ? !clickGui2 : (!clickGui2 && !panel), this);
        }
        for (Setting setting : clickGui2Settings) {
            setting.setVisible(clickGui2, this);
        }
        for (Setting setting : panelSettings) {
            setting.setVisible(panel, this);
        }
    }

    public static boolean isClickGui2() {
        return mode != null && (int) mode.getInput() == MODE_CLICKGUI2;
    }

    public static boolean isPanel() {
        return mode != null && (int) mode.getInput() == MODE_PANEL;
    }

    /** Used when ClickGui2 fails at runtime. */
    public static void resetToClickGui() {
        if (mode != null) {
            mode.setValue(0);
        }
    }

    @Override
    public void onEnable() {
        if (isClickGui2()) {
            if (Utils.nullCheck()) {
                clickGui2Host.open();
            }
        }
        else if (isPanel()) {
            if (Utils.nullCheck()) {
                mc.displayGuiScreen(PanelClickGui.get());
            }
        }
        else if (Utils.nullCheck() && mc.currentScreen != Raven.clickGui) {
            mc.displayGuiScreen(Raven.clickGui);
            Raven.clickGui.initMain();
        }

        this.disable();
    }

    public static String getSelectedFontName() {
        if (font == null) {
            return GUI_FONT_OPTIONS[0];
        }

        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    public static RavenFontRenderer getClickGuiHeaderFontRenderer() {
        return FontManager.getClickGuiHeaderRenderer(getSelectedFontName());
    }

    public static RavenFontRenderer getClickGuiSettingFontRenderer() {
        return FontManager.getClickGuiSettingRenderer(getSelectedFontName());
    }

    private static String[] buildOutlineThemeOptions() {
        String[] themes = ThemePalette.getThemeOptions();
        String[] options = new String[themes.length + 1];
        System.arraycopy(themes, 0, options, 0, themes.length);
        options[themes.length] = "Old Rainbow";
        return options;
    }

    private static int getOutlineThemeIndex() {
        if (outlineTheme == null) {
            return 0;
        }
        return Math.max(0, Math.min(OUTLINE_THEME_OPTIONS.length - 1, (int) outlineTheme.getInput()));
    }

    /** True for "Old Rainbow": open or hovered categories get a chroma outline, the others the regular blue/purple one. */
    public static boolean isOldRainbowOutline() {
        return getOutlineThemeIndex() == OLD_RAINBOW_INDEX;
    }

    /** True when one of the animated themes (everything except Default and Old Rainbow) is selected. */
    public static boolean hasOutlineTheme() {
        int index = getOutlineThemeIndex();
        return index > 0 && index != OLD_RAINBOW_INDEX;
    }

    /**
     * The two colours of the category outline gradient for the selected theme: the same theme colour at opposite
     * points of its animation, so the outline blends from one end of the theme to the other and keeps flowing.
     */
    public static int[] getOutlineThemeColors() {
        int theme = getOutlineThemeIndex();
        long now = System.currentTimeMillis();
        return new int[]{ThemePalette.getThemeColor(theme, now, 0.0f), ThemePalette.getThemeColor(theme, now, 0.5f)};
    }

    public static float getClickGuiScale() {
        if (guiScale == null) {
            return 1.0F;
        }

        return (float) Math.max(0.5D, Math.min(2.0D, guiScale.getInput()));
    }
}
