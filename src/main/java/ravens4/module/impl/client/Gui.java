package ravens4.module.impl.client;

import ravens4.bus.MinecraftForge;
import ravens4.Raven;
import ravens4.clickgui.ClickGui2;
import ravens4.clickgui.ClickGui2Host;
import ravens4.clickgui.PanelClickGui;
import ravens4.module.Module;
import ravens4.utility.ClientTheme;
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
    private static final String[] MODES = new String[]{"ClickGui", "ClickGui2", "Panel", "RClickGui"};
    private static final int MODE_CLICKGUI2 = 1;
    private static final int MODE_PANEL = 2;
    private static final int MODE_RCLICKGUI = 3;

    /** The Gui module (its key opens whichever GUI mode is selected). */
    public static Gui instance;
    /** Settings of RClickGui (the Rise "Modern" click GUI); also used by the DLayout3 HUD, which draws with the same fonts. */
    public static SliderSetting riseSize;
    public static SliderSetting riseFont;
    public static ButtonSetting riseFontPrefix;
    private final List<Setting> rClickGuiSettings = new ArrayList<Setting>();

    public static SliderSetting guiScale;
    public static SliderSetting font;
    public static SliderSetting backgroundBlur;
    public static SliderSetting scrollSpeed;
    public static ButtonSetting removePlayerModel;
    public static ButtonSetting darkBackground;
    public static ButtonSetting removeWatermark;
    public static SliderSetting mode;

    private final ClickGui2Host clickGui2Host;
    /** Settings of the normal click GUI (hidden in ClickGui2 mode) and of ClickGui2 (hidden in ClickGui mode). */
    private final List<Setting> clickGuiSettings = new ArrayList<Setting>();
    private final List<Setting> clickGui2Settings;
    /** Looks of the Panel mode, only shown there. */
    private final List<Setting> panelSettings = new ArrayList<Setting>();
    public static SliderSetting panelOpacity;
    public static SliderSetting panelShadow;
    public static SliderSetting panelBlur;
    public static SliderSetting panelTextOpacity;

    public Gui() {
        super("Gui", category.client, 54);
        instance = this;
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(guiScale = new SliderSetting("Gui scale", "x", 1.0, 0.5, 2.0, 0.01));
        this.registerSetting(font = new SliderSetting("Font", 0, GUI_FONT_OPTIONS));
        this.registerSetting(backgroundBlur = new SliderSetting("Background blur", "%", 0, 0, 100, 1));
        this.registerSetting(scrollSpeed = new SliderSetting("Scroll speed", 20, 2, 90, 1));
        this.registerSetting(darkBackground = new ButtonSetting("Dark background", true));
        this.registerSetting(removePlayerModel = new ButtonSetting("Remove player model", false));
        this.registerSetting(removeWatermark = new ButtonSetting("Remove watermark", false));
        this.registerSetting(panelOpacity = new SliderSetting("Opacity", "%", 78, 1, 100, 1));
        this.registerSetting(panelShadow = new SliderSetting("Shadow", 0, 0, 8, 1)); // 0 = off
        this.registerSetting(panelBlur = new SliderSetting("Panel blur", 0, 0, 30, 0.1));
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
        panelSettings.add(panelBlur);
        panelSettings.add(panelTextOpacity);
        panelSettings.add(resetLayout);
        for (Setting setting : panelSettings) {
            setting.visible = false; // shown by guiUpdate once the Panel mode is selected
        }
        // RClickGui: Size 100 is the normal 400x300 window, 1 is 30% of it
        this.registerSetting(riseSize = new SliderSetting("Size", 100.0, 1.0, 100.0, 1.0));
        this.registerSetting(riseFont = new SliderSetting("Rise font", 0, GUI_FONT_OPTIONS));
        this.registerSetting(riseFontPrefix = new ButtonSetting("Font prefix", false));
        rClickGuiSettings.add(riseSize);
        rClickGuiSettings.add(riseFont);
        rClickGuiSettings.add(riseFontPrefix);
        for (Setting setting : rClickGuiSettings) {
            setting.visible = false;
        }
        clickGuiSettings.add(guiScale);
        clickGuiSettings.add(font);
        clickGuiSettings.add(backgroundBlur);
        clickGuiSettings.add(scrollSpeed);
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
        boolean rise = isRClickGui();
        for (Setting setting : clickGuiSettings) {
            // the background blur is shared with the Panel mode, everything else only belongs to the normal ClickGui
            boolean accentOnly = setting == backgroundBlur;
            setting.setVisible(accentOnly ? (!clickGui2 && !rise) : (!clickGui2 && !panel && !rise), this);
        }
        for (Setting setting : rClickGuiSettings) {
            setting.setVisible(rise, this);
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

    public static boolean isRClickGui() {
        return mode != null && (int) mode.getInput() == MODE_RCLICKGUI;
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
        else if (isRClickGui()) {
            if (Utils.nullCheck()) {
                ravens4.module.impl.client.rise.Interface.openFromGui();
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

    /**
     * The two colours of the category outline gradient: the colours of the theme selected in the Theme category,
     * at opposite points of its animation, so the outline blends from one end of the theme to the other and keeps flowing.
     */
    public static int[] getOutlineThemeColors() {
        long now = System.currentTimeMillis();
        return new int[]{ClientTheme.getAnimatedColor(now, 0.0f) | 0xFF000000, ClientTheme.getAnimatedColor(now, 0.5f) | 0xFF000000};
    }

    public static float getClickGuiScale() {
        if (guiScale == null) {
            return 1.0F;
        }

        return (float) Math.max(0.5D, Math.min(2.0D, guiScale.getInput()));
    }
}
