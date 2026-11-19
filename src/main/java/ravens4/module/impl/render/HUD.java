package ravens4.module.impl.render;

import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.combat.Velocity;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.ColorSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.RenderUtils;
import ravens4.utility.Theme;
import ravens4.utility.ThemePalette;
import ravens4.utility.Utils;
import ravens4.utility.font.FontManager;
import ravens4.utility.font.RavenFontRenderer;
import ravens4.utility.shader.KawaseBloom;
import ravens4.utility.shader.KawaseBlur;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import ravens4.bus.SubscribeEvent;
import ravens4.bus.TickEvent;
import ravens4.bus.TickEvent.RenderTickEvent;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HUD extends Module {
    private static final String[] COLOR_MODES = new String[] { "Static", "Gradient", "Rainbow" };
    private static final String[] WAVE_AXES = new String[] { "Vertical", "Horizontal" };
    private static final String[] VERTICAL_WAVE_DIRECTIONS = new String[] { "Down", "Up" };
    private static final String[] HORIZONTAL_WAVE_DIRECTIONS = new String[] { "Left", "Right" };
    /** Horizontal wave: scales screen X (center of row) into phase; larger = faster change across X. */
    private static final double HUD_WAVE_HORIZONTAL_X_SCALE = 0.35;
    private static final long HUD_RAINBOW_PERIOD_MS = 7500L;
    private static final double HUD_WAVE_ANGLE_SCALE = 0.12;

    public static SliderSetting colorMode;
    public static SliderSetting mode;
    public static ColorSetting hudColor;
    public static ColorSetting hudColor2;
    public static SliderSetting waveAxis;
    public static SliderSetting verticalWaveDirection;
    public static SliderSetting horizontalWaveDirection;
    public static SliderSetting waveSpeed;
    public static SliderSetting waveLength;
    public static SliderSetting font;
    public static SliderSetting fontSize;
    private static SliderSetting outline;
    public static ButtonSetting alphabeticalSort;
    private static ButtonSetting drawBackground;
    private static ButtonSetting textShadow;
    private static ButtonSetting alignRight;
    private static ButtonSetting lowercase;
    public static ButtonSetting showInfo;
    private static final float DEFAULT_POS_X = 5.0f;
    private static final float DEFAULT_POS_Y = 70.0f;
    public static float posX = DEFAULT_POS_X;
    public static float posY = DEFAULT_POS_Y;
    private static float relativePosX = Float.NaN;
    private static float relativePosY = Float.NaN;

    private static final String[] OUTLINE_MODES = new String[] { "None", "Full", "Side" };
    private static final String[] HUD_FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 110).getRGB();
    private static final String[] MODES = new String[] { "Ordinary", "DLayout1", "DLayout2" };
    // Categories DLayout1 was originally written against also included "NetWork" and "Fun",
    // which don't exist in this codebase's Module.category enum - only the categories that
    // are actually real here are exposed as toggles.
    private static final Module.category[] DLAYOUT1_CATEGORIES = new Module.category[] {
            Module.category.combat, Module.category.movement, Module.category.player,
            Module.category.render, Module.category.other,
            Module.category.client, Module.category.scripts
    };

    public static ButtonSetting dlayout1Combat;
    public static ButtonSetting dlayout1Movement;
    public static ButtonSetting dlayout1Player;
    public static ButtonSetting dlayout1Render;
    public static ButtonSetting dlayout1Other;
    public static ButtonSetting dlayout1Client;
    public static ButtonSetting dlayout1Scripts;
    public static SliderSetting dlayout1PaddingX;
    public static SliderSetting dlayout1PaddingY;
    public static SliderSetting dlayout1Rounding;
    public static SliderSetting dlayout1Theme;
    public static ColorSetting dlayout1DetailsColor;
    public static ColorSetting dlayout1BackgroundColor;
    public static SliderSetting dlayout2BackgroundOpacity;
    public static ButtonSetting dlayout2Blur;
    public static ButtonSetting dlayout2Bloom;
    public static SliderSetting dlayout2BloomStrength;

    private boolean isAlphabeticalSort;
    private boolean canShowInfo;
    private String lastHudFontName = "";
    private float lastHudFontScale = -1.0f;

    public HUD() {
        super("HUD", Module.category.render);
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(colorMode = new SliderSetting("Color mode", 0, COLOR_MODES));
        this.registerSetting(hudColor = new ColorSetting("Color", 255, 255, 255));
        this.registerSetting(hudColor2 = new ColorSetting("Color 2", 85, 85, 255));
        this.registerSetting(waveAxis = new SliderSetting("Wave axis", 0, WAVE_AXES));
        this.registerSetting(verticalWaveDirection = new SliderSetting("Wave direction", 0, VERTICAL_WAVE_DIRECTIONS));
        this.registerSetting(horizontalWaveDirection = new SliderSetting("Wave direction", 0, HORIZONTAL_WAVE_DIRECTIONS));
        this.registerSetting(waveSpeed = new SliderSetting("Wave speed", 1.0, 0.1, 5.0, 0.1));
        this.registerSetting(waveLength = new SliderSetting("Wave length", 1.0, 0.5, 5.0, 0.1));
        this.registerSetting(font = new SliderSetting("Font", 0, HUD_FONT_OPTIONS));
        this.registerSetting(fontSize = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        this.registerSetting(outline = new SliderSetting("Outline", 0, OUTLINE_MODES));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.displayGuiScreen(new EditScreen())));
        this.registerSetting(alignRight = new ButtonSetting("Align right", false));
        this.registerSetting(alphabeticalSort = new ButtonSetting("Alphabetical sort", false));
        this.registerSetting(drawBackground = new ButtonSetting("Draw background", false));
        this.registerSetting(textShadow = new ButtonSetting("Text shadow", true));
        this.registerSetting(lowercase = new ButtonSetting("Lowercase", false));
        this.registerSetting(showInfo = new ButtonSetting("Show module info", true));
        this.registerSetting(dlayout1Combat = new ButtonSetting("Combat", true));
        this.registerSetting(dlayout1Movement = new ButtonSetting("Movement", true));
        this.registerSetting(dlayout1Player = new ButtonSetting("Player", true));
        this.registerSetting(dlayout1Render = new ButtonSetting("Render", true));
        this.registerSetting(dlayout1Other = new ButtonSetting("Other", true));
        this.registerSetting(dlayout1Client = new ButtonSetting("Client", true));
        this.registerSetting(dlayout1Scripts = new ButtonSetting("Scripts", true));
        this.registerSetting(dlayout1PaddingX = new SliderSetting("Padding X", "px", 2, 0, 10, 0.5));
        this.registerSetting(dlayout1PaddingY = new SliderSetting("Padding Y", "px", 1, 0, 10, 0.5));
        this.registerSetting(dlayout1Rounding = new SliderSetting("Rounding", "px", 3, 0, 16, 0.5));
        this.registerSetting(dlayout1Theme = new SliderSetting("Theme", 0, ThemePalette.getThemeOptions()));
        this.registerSetting(dlayout1DetailsColor = new ColorSetting("Details color", 190, 190, 190));
        this.registerSetting(dlayout1BackgroundColor = new ColorSetting("Background color", 0, 0, 0, 110));
        this.registerSetting(dlayout2BackgroundOpacity = new SliderSetting("Background opacity", "%", 70, 0, 100, 1));
        this.registerSetting(dlayout2Blur = new ButtonSetting("Blur", false));
        this.registerSetting(dlayout2Bloom = new ButtonSetting("Bloom", false));
        this.registerSetting(dlayout2BloomStrength = new SliderSetting("Bloom strength", "%", 100, 10, 300, 10));
    }

    @Override
    public void guiUpdate() {
        boolean dlayout1Selected = mode != null && (int) mode.getInput() == 1;
        boolean dlayout2Selected = mode != null && (int) mode.getInput() == 2;
        boolean layoutSelected = dlayout1Selected || dlayout2Selected;
        if (colorMode != null) colorMode.setVisible(!dlayout1Selected, this);
        if (alignRight != null) alignRight.setVisible(!layoutSelected, this);
        if (alphabeticalSort != null) alphabeticalSort.setVisible(!layoutSelected, this);
        if (drawBackground != null) drawBackground.setVisible(!layoutSelected, this);
        if (textShadow != null) textShadow.setVisible(!dlayout1Selected, this);
        if (outline != null) outline.setVisible(!layoutSelected, this);
        if (dlayout1Combat != null) dlayout1Combat.setVisible(layoutSelected, this);
        if (dlayout1Movement != null) dlayout1Movement.setVisible(layoutSelected, this);
        if (dlayout1Player != null) dlayout1Player.setVisible(layoutSelected, this);
        if (dlayout1Render != null) dlayout1Render.setVisible(layoutSelected, this);
        if (dlayout1Other != null) dlayout1Other.setVisible(layoutSelected, this);
        if (dlayout1Client != null) dlayout1Client.setVisible(layoutSelected, this);
        if (dlayout1Scripts != null) dlayout1Scripts.setVisible(layoutSelected, this);
        if (dlayout1PaddingX != null) dlayout1PaddingX.setVisible(layoutSelected, this);
        if (dlayout1PaddingY != null) dlayout1PaddingY.setVisible(layoutSelected, this);
        if (dlayout1Rounding != null) dlayout1Rounding.setVisible(layoutSelected, this);
        if (dlayout1Theme != null) dlayout1Theme.setVisible(layoutSelected, this);
        if (dlayout1DetailsColor != null) dlayout1DetailsColor.setVisible(layoutSelected, this);
        if (dlayout1BackgroundColor != null) dlayout1BackgroundColor.setVisible(layoutSelected, this);
        if (dlayout2BackgroundOpacity != null) dlayout2BackgroundOpacity.setVisible(dlayout2Selected, this);
        if (dlayout2Blur != null) dlayout2Blur.setVisible(dlayout2Selected, this);
        if (dlayout2Bloom != null) dlayout2Bloom.setVisible(dlayout2Selected, this);
        if (dlayout2BloomStrength != null) dlayout2BloomStrength.setVisible(dlayout2Selected && dlayout2Bloom != null && dlayout2Bloom.isToggled(), this);

        int colorModeValue = colorMode == null ? 0 : (int) colorMode.getInput();
        if (hudColor != null) {
            hudColor.setVisible(!dlayout1Selected && (colorModeValue == 0 || colorModeValue == 1), this);
        }
        if (hudColor2 != null) {
            hudColor2.setVisible(!dlayout1Selected && colorModeValue == 1, this);
        }
        boolean showWaveSettings = !dlayout1Selected && (colorModeValue == 1 || colorModeValue == 2);
        boolean verticalAxis = hudWaveIsVertical();
        if (waveAxis != null) {
            waveAxis.setVisible(showWaveSettings, this);
        }
        if (verticalWaveDirection != null) {
            verticalWaveDirection.setVisible(showWaveSettings && verticalAxis, this);
        }
        if (horizontalWaveDirection != null) {
            horizontalWaveDirection.setVisible(showWaveSettings && !verticalAxis, this);
        }
        if (waveSpeed != null) {
            waveSpeed.setVisible(showWaveSettings, this);
        }
        if (waveLength != null) {
            waveLength.setVisible(showWaveSettings, this);
        }
    }

    @Override
    public void onEnable() {
        guiUpdate();
        ModuleManager.sort();
        d2Rows.clear();
        d2ByModule.clear();
        d2LastFrameNs = 0L;
        d2Signature = Integer.MIN_VALUE;
        d2Measure = true;
    }

    @Override
    public void onDisable() {
        d2Rows.clear();
        d2ByModule.clear();
    }

    @Override
    public void guiButtonToggled(ButtonSetting buttonSetting) {
        if (buttonSetting == alphabeticalSort || buttonSetting == showInfo) {
            ModuleManager.sort();
        }
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }

        if (isAlphabeticalSort != alphabeticalSort.isToggled()) {
            isAlphabeticalSort = alphabeticalSort.isToggled();
            ModuleManager.sort();
        }

        if (canShowInfo != showInfo.isToggled()) {
            canShowInfo = showInfo.isToggled();
            ModuleManager.sort();
        }

        String currentFontName = getSelectedFontName();
        float currentFontScale = getSelectedFontScale();
        if (!currentFontName.equals(lastHudFontName) || Float.compare(currentFontScale, lastHudFontScale) != 0) {
            lastHudFontName = currentFontName;
            lastHudFontScale = currentFontScale;
            ModuleManager.sort();
        }

        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }

        syncPositionToResolution();

        for (Module module : ModuleManager.organizedModules) {
            module.getInfoUpdate();
            if (Module.sort) {
                break;
            }
        }

        if (Module.sort) {
            ModuleManager.sort();
        }
        Module.sort = false;

        if (mode != null && (int) mode.getInput() == 1) {
            renderDLayout1();
            return;
        }
        if (mode != null && (int) mode.getInput() == 2) {
            renderDLayout2();
            return;
        }

        RavenFontRenderer hudFont = getHudFontRenderer();
        int textTopOffset = hudFont.getTextTopOffset();
        int textBottomOffset = hudFont.getTextBottomOffset();
        int horizontalTextPadding = getHudHorizontalTextPadding();
        int textTopPadding = getHudTextTopPadding();
        int textBottomPadding = getHudTextBottomPadding();
        int outlineThickness = getHudOutlineThickness();
        int rowHeight = getHudRowHeight(textTopOffset, textBottomOffset, textTopPadding, textBottomPadding);
        float yPos = posY;
        double verticalWaveAccum = 0.0;
        boolean firstVisibleRow = true;
        String previousModule = "";
        double lastOutlineLeft = 0.0;
        double lastOutlineRight = 0.0;
        double lastBackgroundBottom = 0.0;
        boolean removeVelocity = false;

        try {
            for (Module module : ModuleManager.organizedModules) {
                if (!module.isEnabled() || module == this || shouldSkipModule(module, removeVelocity)) {
                    continue;
                }

                String moduleName = getHudRenderText(module);
                int moduleWidth = hudFont.getStringWidth(moduleName);
                float xPos = posX;
                float textY = getHudTextY(yPos, textTopOffset, textTopPadding);
                double backgroundLeft = xPos - horizontalTextPadding;
                double backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                double backgroundTop = yPos;
                double backgroundBottom = yPos + rowHeight;
                double outlineLeft = backgroundLeft - outlineThickness;
                double outlineRight = backgroundRight + outlineThickness;
                double outlineTop = backgroundTop - outlineThickness;

                if (alignRight.isToggled()) {
                    xPos -= moduleWidth;
                    backgroundLeft = xPos - horizontalTextPadding;
                    backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                    outlineLeft = backgroundLeft - outlineThickness;
                    outlineRight = backgroundRight + outlineThickness;
                }

                double rowCenterX = (backgroundLeft + backgroundRight) * 0.5;
                double wavePhase = hudWavePhase(verticalWaveAccum, rowCenterX);
                int color = getHudColor(wavePhase);

                if (drawBackground.isToggled()) {
                    RenderUtils.drawRect(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
                }

                if (outline.getInput() == 1 && firstVisibleRow) {
                    RenderUtils.drawRect(outlineLeft, outlineTop, outlineRight, backgroundTop, color);
                }

                if (hudWaveIsVertical()) {
                    verticalWaveAccum += getVerticalWaveStep();
                }
                firstVisibleRow = false;

                if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                    double difference = hudFont.getStringWidth(previousModule) - moduleWidth;
                    if (alphabeticalSort.isToggled() && difference < 0) {
                        RenderUtils.drawRect(outlineLeft, outlineTop, xPos - difference + horizontalTextPadding + outlineThickness, backgroundTop, color);
                    }
                    else if (alignRight.isToggled()) {
                        RenderUtils.drawRect(xPos - difference - horizontalTextPadding - outlineThickness, outlineTop, backgroundLeft, backgroundTop, color);
                    }
                    else {
                        RenderUtils.drawRect(backgroundRight, outlineTop, xPos + difference + moduleWidth + horizontalTextPadding + outlineThickness, backgroundTop, color);
                    }
                }

                if (outline.getInput() > 0) {
                    if (alignRight.isToggled()) {
                        RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                    }
                    else {
                        RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                    }
                }

                if (outline.getInput() == 1) {
                    if (alignRight.isToggled()) {
                        RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                    }
                    else {
                        RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                    }
                }

                drawHudText(hudFont, moduleName, xPos, textY, color);
                previousModule = moduleName;
                lastOutlineLeft = outlineLeft;
                lastOutlineRight = outlineRight;
                lastBackgroundBottom = backgroundBottom;
                yPos += rowHeight;
            }
        }
        catch (Exception exception) {
            Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
            exception.printStackTrace();
        }

        if (outline.getInput() == 1 && !previousModule.isEmpty()) {
            double bottomCenterX = (lastOutlineLeft + lastOutlineRight) * 0.5;
            double bottomPhase = hudWavePhase(verticalWaveAccum, bottomCenterX);
            RenderUtils.drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, getHudColor(bottomPhase));
        }
    }

    // ============================== DLayout1 mode ==============================
    // Adapted from a "Visible Categories HUD" script written for a different client
    // (package keystrokesmod, not ravens4 - different script facade entirely, so this is a
    // from-scratch port of its behavior rather than a copy). Right-aligned, per-category
    // toggleable list with connected rounded-corner row backgrounds. Reuses the Font,
    // Lowercase, and Show module info settings already shared with Ordinary mode.

    private void renderDLayout1() {
        RavenFontRenderer hudFont = getHudFontRenderer();
        boolean showDetails = showInfo != null && showInfo.isToggled();
        boolean toLower = lowercase != null && lowercase.isToggled();

        List<String[]> entries = new ArrayList<>();
        try {
            for (Module module : ModuleManager.organizedModules) {
                if (module == this || !module.isEnabled() || module.isHidden()) continue;
                if (!isDlayout1CategoryEnabled(module.moduleCategory())) continue;

                String title = getHudText(module);
                String info = module.getInfo();
                String details = (showDetails && info != null && !info.isEmpty()) ? " " + info : "";
                if (toLower) details = details.toLowerCase();
                entries.add(new String[]{title, details});
            }
        }
        catch (Exception exception) {
            Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
            exception.printStackTrace();
            return;
        }

        entries.sort((a, b) -> {
            int widthA = hudFont.getStringWidth(a[0] + a[1]);
            int widthB = hudFont.getStringWidth(b[0] + b[1]);
            return Integer.compare(widthB, widthA);
        });

        ScaledResolution resolution = new ScaledResolution(mc);
        float right = resolution.getScaledWidth();
        float padX = (float) dlayout1PaddingX.getInput();
        float padY = (float) dlayout1PaddingY.getInput();
        float rounding = (float) dlayout1Rounding.getInput();
        int textColor = hudColor != null ? hudColor.getColor() : Color.WHITE.getRGB();
        int detailColor = dlayout1DetailsColor.getColor();
        int backgroundColor = dlayout1BackgroundColor.getColor();
        float rowHeight = hudFont.getFontHeight() + (padY * 2.0f);
        // Theme: same theme list as the other theme sliders. Default keeps the plain colour, any other theme colours the module names
        // and is offset a little per row so the colour flows down the list.
        int themeIndex = dlayout1Theme == null ? 0 : (int) dlayout1Theme.getInput();
        long themeTime = System.currentTimeMillis();

        List<Float> rowXs = new ArrayList<>();
        for (String[] entry : entries) {
            float width = hudFont.getStringWidth(entry[0] + entry[1]);
            rowXs.add(right - width - (padX * 2.0f));
        }
        drawDlayout1ConnectedBackground(rowXs, right, rowHeight, rounding, backgroundColor);

        float y = 0.0f;
        for (int index = 0; index < entries.size(); index++) {
            String[] entry = entries.get(index);
            float titleWidth = hudFont.getStringWidth(entry[0]);
            float x = rowXs.get(index);

            int rowColor = themeIndex > 0 ? ThemePalette.getThemeColor(themeIndex, themeTime, index * 0.06f) : textColor;
            hudFont.drawString(entry[0], x + padX, y + padY, rowColor, shouldDrawTextShadow());
            if (!entry[1].isEmpty()) {
                hudFont.drawString(entry[1], x + padX + titleWidth, y + padY, detailColor, shouldDrawTextShadow());
            }
            y += rowHeight;
        }
    }

    private static boolean isDlayout1CategoryEnabled(Module.category cat) {
        if (cat == Module.category.combat) return dlayout1Combat.isToggled();
        if (cat == Module.category.movement) return dlayout1Movement.isToggled();
        if (cat == Module.category.player) return dlayout1Player.isToggled();
        if (cat == Module.category.render) return dlayout1Render.isToggled();
        if (cat == Module.category.other) return dlayout1Other.isToggled();
        if (cat == Module.category.client) return dlayout1Client.isToggled();
        if (cat == Module.category.scripts) return dlayout1Scripts.isToggled();
        return false;
    }

    // Each row: square top-left, smoothly rounded bottom-left, flush with the screen edge on
    // the right. Drawn once per row (never overlapping), so translucent backgrounds never
    // double up and joins between rows of different widths stay at right angles.
    private static void drawDlayout1ConnectedBackground(List<Float> rowXs, float right, float rowHeight, float requestedRadius, int color) {
        for (int i = 0; i < rowXs.size(); i++) {
            float top = i * rowHeight;
            float radius = requestedRadius;
            if (i + 1 < rowXs.size()) {
                radius = Math.min(radius, Math.max(0.0f, rowXs.get(i + 1) - rowXs.get(i)));
            }
            drawDlayout1RowBackground(rowXs.get(i), top, right, rowHeight, radius, color);
        }
    }

    private static void drawDlayout1RowBackground(float left, float top, float right, float height, float requestedRadius, int color) {
        float radius = Math.min(requestedRadius, height);
        if (radius <= 0.0f) {
            RenderUtils.drawRect(left, top, right, top + height, color);
            return;
        }

        float bottom = top + height;
        // Two disjoint rectangles - translucent backgrounds never darken from overlap.
        RenderUtils.drawRect(left, top, right, bottom - radius, color);
        RenderUtils.drawRect(left + radius, bottom - radius, right, bottom, color);

        // Lower-left quarter circle, filled exactly once.
        float centerX = left + radius;
        float centerY = bottom - radius;
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        int alpha = (color >> 24) & 255;

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableAlpha();
        net.minecraft.client.renderer.GlStateManager.color(red / 255.0f, green / 255.0f, blue / 255.0f, alpha / 255.0f);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN);
        org.lwjgl.opengl.GL11.glVertex2f(centerX, centerY);
        for (int point = 0; point <= 96; point++) {
            double angle = Math.PI - (Math.PI / 2.0) * point / 96.0;
            org.lwjgl.opengl.GL11.glVertex2f((float) (centerX + Math.cos(angle) * radius), (float) (centerY + Math.sin(angle) * radius));
        }
        org.lwjgl.opengl.GL11.glEnd();
        net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.resetColor();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    // ============================== DLayout2 mode ==============================
    // Port of the "Mod Overlay" script (DLayout2): the enabled modules of the chosen categories as a list that
    // slides in and out, longest first, on rounded translucent backgrounds. The list sits at the HUD position
    // (Edit position) and grows to the left when that is on the right half of the screen, to the right otherwise.
    // Name colour, gradient/rainbow, theme, font and scale come from the shared HUD settings.

    private static final float D2_ANIM_SPEED = 6.0F;
    private static final float D2_GAP = 4.0F;
    private static final float D2_CORNER_SMOOTHING = 16.0F;
    private static final float D2_BLUR_OFFSET = 3.0F;
    private static final float D2_BLOOM_OFFSET = 3.0F;
    private static final int D2_MAX_SEGMENTS = 48;
    private static final float[][] D2_COS = new float[D2_MAX_SEGMENTS + 1][];
    private static final float[][] D2_SIN = new float[D2_MAX_SEGMENTS + 1][];

    private static final class D2Row {
        final Module module;
        String name = "";
        String status = "";
        float nameWidth;
        float width;
        float height;
        float progress;
        boolean enabled = true;
        float rx, ry, rw, rh, rc, ra;
        float cornerCur;
        boolean cornerInit;
        int textColor;
        boolean visible;

        D2Row(Module module) {
            this.module = module;
        }
    }

    private final List<D2Row> d2Rows = new ArrayList<D2Row>();
    private final Map<Module, D2Row> d2ByModule = new HashMap<Module, D2Row>();
    private long d2LastFrameNs;
    private boolean d2Measure = true;
    private int d2Signature = Integer.MIN_VALUE;
    private boolean d2RoundRight;
    private net.minecraft.client.shader.Framebuffer d2GlowBuffer;
    private int d2ScaleFactor = 1;

    private static float d2Smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static int d2Argb(int color, float alpha) {
        return ((int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24) | (color & 0xFFFFFF);
    }

    private void renderDLayout2() {
        long nano = System.nanoTime();
        float elapsed = d2LastFrameNs == 0L ? 0.0F : (float) ((nano - d2LastFrameNs) / 1.0E9);
        d2LastFrameNs = nano;
        if (elapsed > 0.1F) {
            elapsed = 0.1F;
        }

        RavenFontRenderer font = getHudFontRenderer();
        boolean showDetails = showInfo != null && showInfo.isToggled();
        boolean toLower = lowercase != null && lowercase.isToggled();
        int signature = java.util.Arrays.hashCode(new Object[] { showDetails, toLower, getSelectedFontName(), getSelectedFontScale() });

        // which modules should be on the list right now
        Set<Module> wanted = new HashSet<Module>();
        try {
            for (Module module : ModuleManager.organizedModules) {
                if (module == this || !module.isEnabled() || module.isHidden() || module == ModuleManager.commandLine) {
                    continue;
                }
                if (!isDlayout1CategoryEnabled(module.moduleCategory())) {
                    continue;
                }
                wanted.add(module);
            }
        } catch (Exception exception) {
            return;
        }
        for (D2Row row : d2Rows) {
            row.enabled = wanted.contains(row.module);
        }
        for (Module module : wanted) {
            if (!d2ByModule.containsKey(module)) {
                D2Row row = new D2Row(module);
                d2Rows.add(row);
                d2ByModule.put(module, row);
                d2Measure = true;
            }
        }
        boolean settingsChanged = signature != d2Signature;
        d2Signature = signature;
        for (D2Row row : d2Rows) {
            if (!row.enabled) {
                continue;
            }
            String name = getHudText(row.module);
            String info = showDetails && row.module.getInfo() != null ? row.module.getInfo() : "";
            if (toLower) {
                info = info.toLowerCase();
            }
            if (settingsChanged || !name.equals(row.name) || !info.equals(row.status)) {
                row.name = name;
                row.status = info;
                d2Measure = true;
            }
        }
        if (d2Rows.isEmpty()) {
            return;
        }

        // slide in and out
        float step = elapsed * D2_ANIM_SPEED;
        java.util.Iterator<D2Row> it = d2Rows.iterator();
        while (it.hasNext()) {
            D2Row row = it.next();
            if (row.enabled) {
                if (row.progress != 1.0F) {
                    row.progress = Math.min(1.0F, row.progress + step);
                }
            } else {
                row.progress -= step;
                if (!(row.progress > 0.0F)) {
                    it.remove();
                    d2ByModule.remove(row.module);
                }
            }
        }
        if (d2Rows.isEmpty()) {
            return;
        }

        float gap = D2_GAP * getSelectedFontScale();
        if (d2Measure) {
            float fontHeight = font.getFontHeight();
            for (D2Row row : d2Rows) {
                row.nameWidth = font.getStringWidth(row.name);
                row.height = fontHeight;
                row.width = row.nameWidth;
                if (!row.status.isEmpty()) {
                    row.width = font.getStringWidth(row.status) + gap + row.nameWidth;
                }
            }
            Collections.sort(d2Rows, new java.util.Comparator<D2Row>() {
                @Override
                public int compare(D2Row a, D2Row b) {
                    return b.width > a.width ? 1 : (a.width > b.width ? -1 : a.name.compareTo(b.name));
                }
            });
            d2Measure = false;
        }

        ScaledResolution resolution = new ScaledResolution(mc);
        float screenWidth = resolution.getScaledWidth();
        float screenHeight = resolution.getScaledHeight();
        d2ScaleFactor = Math.max(1, resolution.getScaleFactor());
        float px = (float) dlayout1PaddingX.getInput();
        float py = (float) dlayout1PaddingY.getInput();
        float rounding = (float) dlayout1Rounding.getInput();
        float cornerLimit = (px + py) + (float) Math.sqrt(2.0 * px * py);
        float opacity = dlayout2BackgroundOpacity == null ? 0.7F : (float) (dlayout2BackgroundOpacity.getInput() / 100.0);
        int backgroundRgb = dlayout1BackgroundColor == null ? 0 : (dlayout1BackgroundColor.getColor() & 0xFFFFFF);
        int detailRgb = dlayout1DetailsColor == null ? 0xAAAAAA : (dlayout1DetailsColor.getColor() & 0xFFFFFF);
        int themeIndex = dlayout1Theme == null ? 0 : (int) dlayout1Theme.getInput();
        long themeTime = System.currentTimeMillis();

        float twiceX = px + px;
        float anchorX = Math.max(0.0F, Math.min(screenWidth, posX));
        float anchorY = Math.max(0.0F, Math.min(screenHeight, posY));
        boolean rightAligned = anchorX >= screenWidth * 0.5F;
        d2RoundRight = !rightAligned;
        int count = d2Rows.size();
        float y = 0.0F;
        for (int i = 0; i < count; i++) {
            D2Row row = d2Rows.get(i);
            float alpha = row.progress == 1.0F ? 1.0F : d2Smooth(Math.max(0.0F, row.progress));
            row.ra = alpha;
            row.rx = rightAligned ? anchorX - twiceX - row.width * alpha : anchorX - row.width * (1.0F - alpha);
            row.ry = anchorY + y;
            row.rw = twiceX + row.width;
            float baseRowHeight = py + py + row.height;
            float rowOverlap = rounding > 0.0F ? 1.0F / d2ScaleFactor : 0.0F;
            row.rh = Math.max(0.0F, baseRowHeight - rowOverlap);
            row.visible = (int) (alpha * 255.0F) > 3;
            int color = themeIndex > 0
                    ? ThemePalette.getThemeColor(themeIndex, themeTime, i * 0.06F)
                    : getHudColor(hudWavePhase(i * getVerticalWaveStep(), row.rx + row.rw * 0.5));
            row.textColor = color & 0xFFFFFF;
            y += alpha * row.rh;
        }

        // rows share their corners smoothly: a row never rounds further than the step to the next one
        float blend = Math.min(1.0F, elapsed * D2_CORNER_SMOOTHING);
        for (int i = 0; i < count; i++) {
            D2Row row = d2Rows.get(i);
            float target = Math.min(rounding, cornerLimit);
            if (i + 1 < count) {
                D2Row next = d2Rows.get(i + 1);
                float edgeDifference = rightAligned ? next.rx - row.rx : (row.rx + row.rw) - (next.rx + next.rw);
                target = Math.min(edgeDifference, target);
            }
            target = Math.max(0.0F, Math.min(target, Math.min(row.rw, row.rh)));
            if (!row.cornerInit) {
                row.cornerCur = target;
                row.cornerInit = true;
            } else {
                row.cornerCur += (target - row.cornerCur) * blend;
            }
            row.rc = row.cornerCur;
        }

        if (dlayout2Blur != null && dlayout2Blur.isToggled()) {
            net.minecraft.client.shader.Framebuffer blurred = KawaseBlur.blurToBuffer(2, D2_BLUR_OFFSET);
            d2DrawBlurredShapes(blurred, screenWidth, screenHeight);
        }
        if (dlayout2Bloom != null && dlayout2Bloom.isToggled()) {
            d2DrawBloom(screenWidth, screenHeight);
        }
        d2DrawShapes(backgroundRgb, opacity);

        boolean shadow = shouldDrawTextShadow();
        for (int i = 0; i < count; i++) {
            D2Row row = d2Rows.get(i);
            if (!row.visible) {
                continue;
            }
            float textY = row.ry + py;
            float textX = rightAligned ? anchorX - px - row.width * row.ra : anchorX + px - row.width * (1.0F - row.ra);
            font.drawString(row.name, textX, textY, d2Argb(row.textColor, row.ra), shadow);
            if (!row.status.isEmpty()) {
                font.drawString(row.status, textX + row.nameWidth + gap, textY, d2Argb(detailRgb, row.ra), shadow);
            }
        }
    }

    private void d2DrawShapes(int rgb, float opacity) {
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.GlStateManager.disableAlpha();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_TRIANGLES);
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        for (D2Row row : d2Rows) {
            if (!row.visible) {
                continue;
            }
            d2Shape(row.rx, row.ry, row.rw, row.rh, row.rc, red, green, blue, opacity * row.ra);
        }
        org.lwjgl.opengl.GL11.glEnd();
        net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.resetColor();
    }

    // Glow: the rows in their text colour are drawn into a buffer, blurred, and the rows themselves are cut out
    // of the blurred picture again, so only the halo around them is added to the screen (no mask texture needed).
    private void d2DrawBloom(float screenWidth, float screenHeight) {
        d2GlowBuffer = RenderUtils.createFrameBuffer(d2GlowBuffer);
        d2GlowBuffer.framebufferClear();
        d2GlowBuffer.bindFramebuffer(false);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.GlStateManager.disableAlpha();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_TRIANGLES);
        for (D2Row row : d2Rows) {
            if (!row.visible) {
                continue;
            }
            d2Shape(row.rx, row.ry, row.rw, row.rh, row.rc, ((row.textColor >> 16) & 0xFF) / 255.0F,
                    ((row.textColor >> 8) & 0xFF) / 255.0F, (row.textColor & 0xFF) / 255.0F, row.ra);
        }
        org.lwjgl.opengl.GL11.glEnd();
        mc.getFramebuffer().bindFramebuffer(true);

        net.minecraft.client.shader.Framebuffer glow = KawaseBloom.blurTextureToBuffer(d2GlowBuffer.framebufferTexture, 2, D2_BLOOM_OFFSET);

        // erase the rows from the blurred glow
        glow.bindFramebuffer(true);
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_ZERO, org.lwjgl.opengl.GL11.GL_ZERO);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_TRIANGLES);
        for (D2Row row : d2Rows) {
            if (!row.visible) {
                continue;
            }
            d2Shape(row.rx, row.ry, row.rw, row.rh, row.rc, 0.0F, 0.0F, 0.0F, 1.0F);
        }
        org.lwjgl.opengl.GL11.glEnd();
        mc.getFramebuffer().bindFramebuffer(true);

        // add the halo to the screen, as often as the strength asks for
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.GlStateManager.bindTexture(glow.framebufferTexture);
        float strength = dlayout2BloomStrength == null ? 1.0F : (float) (dlayout2BloomStrength.getInput() / 100.0);
        for (int pass = 0; pass < 3; pass++) {
            float amount = Math.min(1.0F, strength - pass);
            if (amount <= 0.0F) {
                break;
            }
            org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, amount);
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            org.lwjgl.opengl.GL11.glTexCoord2f(0.0F, 1.0F);
            org.lwjgl.opengl.GL11.glVertex2f(0.0F, 0.0F);
            org.lwjgl.opengl.GL11.glTexCoord2f(0.0F, 0.0F);
            org.lwjgl.opengl.GL11.glVertex2f(0.0F, screenHeight);
            org.lwjgl.opengl.GL11.glTexCoord2f(1.0F, 0.0F);
            org.lwjgl.opengl.GL11.glVertex2f(screenWidth, screenHeight);
            org.lwjgl.opengl.GL11.glTexCoord2f(1.0F, 1.0F);
            org.lwjgl.opengl.GL11.glVertex2f(screenWidth, 0.0F);
            org.lwjgl.opengl.GL11.glEnd();
        }
        net.minecraft.client.renderer.GlStateManager.bindTexture(0);
        net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.resetColor();
    }

    // the blurred frame, cut to the row shapes (no mask texture needed)
    private void d2DrawBlurredShapes(net.minecraft.client.shader.Framebuffer blurred, float screenWidth, float screenHeight) {
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableAlpha();
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.GlStateManager.bindTexture(blurred.framebufferTexture);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_TRIANGLES);
        for (D2Row row : d2Rows) {
            if (!row.visible) {
                continue;
            }
            org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, row.ra);
            d2ShapeTextured(row.rx, row.ry, row.rw, row.rh, row.rc, screenWidth, screenHeight);
        }
        org.lwjgl.opengl.GL11.glEnd();
        net.minecraft.client.renderer.GlStateManager.bindTexture(0);
        net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.resetColor();
    }

    private static void d2Vertex(float x, float y) {
        org.lwjgl.opengl.GL11.glVertex2f(x, y);
    }

    private static void d2TexVertex(float x, float y, float screenWidth, float screenHeight) {
        org.lwjgl.opengl.GL11.glTexCoord2f(x / screenWidth, 1.0F - y / screenHeight);
        org.lwjgl.opengl.GL11.glVertex2f(x, y);
    }

    private static void d2Quad(float x1, float y1, float x2, float y2) {
        d2Vertex(x1, y1);
        d2Vertex(x1, y2);
        d2Vertex(x2, y2);
        d2Vertex(x1, y1);
        d2Vertex(x2, y2);
        d2Vertex(x2, y1);
    }

    private static void d2TexQuad(float x1, float y1, float x2, float y2, float sw, float sh) {
        d2TexVertex(x1, y1, sw, sh);
        d2TexVertex(x1, y2, sw, sh);
        d2TexVertex(x2, y2, sw, sh);
        d2TexVertex(x1, y1, sw, sh);
        d2TexVertex(x2, y2, sw, sh);
        d2TexVertex(x2, y1, sw, sh);
    }

    private static void d2BuildTables(int n) {
        if (D2_COS[n] != null) {
            return;
        }
        float[] cs = new float[n + 1];
        float[] sn = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double angle = Math.PI - (Math.PI / 2.0) * i / n;
            cs[i] = (float) Math.cos(angle);
            sn[i] = (float) Math.sin(angle);
        }
        D2_COS[n] = cs;
        D2_SIN[n] = sn;
    }

    // one row: square on the screen side, one smoothly rounded corner at the bottom on the free side,
    // with a thin faded fringe on the curve so the edge is smooth
    private void d2Shape(float x, float y, float w, float h, float corner, float r, float g, float b, float a) {
        float x2 = x + w;
        float y2 = y + h;
        org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
        if (corner < 0.5F) {
            d2Quad(x, y, x2, y2);
            return;
        }
        corner = Math.min(corner, Math.min(w, h));
        if (d2RoundRight) {
            d2Quad(x, y, x2 - corner, y2);
            d2Quad(x2 - corner, y, x2, y2 - corner);
        } else {
            d2Quad(x + corner, y, x2, y2);
            d2Quad(x, y, x + corner, y2 - corner);
        }
        int n = (int) Math.ceil(corner * d2ScaleFactor * 2.0F);
        n = Math.max(12, Math.min(D2_MAX_SEGMENTS, n));
        d2BuildTables(n);
        float[] cs = D2_COS[n];
        float[] sn = D2_SIN[n];

        float cx = d2RoundRight ? x2 - corner : x + corner;
        float cy = y2 - corner;
        float dir = d2RoundRight ? -1.0F : 1.0F;
        float half = 0.75F / d2ScaleFactor;
        float inner = Math.max(0.0F, corner - half);
        float outer = corner + half;
        for (int i = 0; i < n; i++) {
            d2Vertex(cx, cy);
            d2Vertex(cx + dir * inner * cs[i], cy + inner * sn[i]);
            d2Vertex(cx + dir * inner * cs[i + 1], cy + inner * sn[i + 1]);
        }
        for (int i = 0; i < n; i++) {
            float ix0 = cx + dir * inner * cs[i];
            float iy0 = cy + inner * sn[i];
            float ix1 = cx + dir * inner * cs[i + 1];
            float iy1 = cy + inner * sn[i + 1];
            float ox0 = cx + dir * outer * cs[i];
            float oy0 = cy + outer * sn[i];
            float ox1 = cx + dir * outer * cs[i + 1];
            float oy1 = cy + outer * sn[i + 1];

            org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
            d2Vertex(ix0, iy0);
            org.lwjgl.opengl.GL11.glColor4f(r, g, b, 0.0F);
            d2Vertex(ox0, oy0);
            d2Vertex(ox1, oy1);

            d2Vertex(ox1, oy1);
            org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
            d2Vertex(ix1, iy1);
            d2Vertex(ix0, iy0);
        }
        org.lwjgl.opengl.GL11.glColor4f(r, g, b, a);
    }

    // the same row shape, textured with the blurred frame (without the fringe)
    private void d2ShapeTextured(float x, float y, float w, float h, float corner, float sw, float sh) {
        float x2 = x + w;
        float y2 = y + h;
        if (corner < 0.5F) {
            d2TexQuad(x, y, x2, y2, sw, sh);
            return;
        }
        corner = Math.min(corner, Math.min(w, h));
        if (d2RoundRight) {
            d2TexQuad(x, y, x2 - corner, y2, sw, sh);
            d2TexQuad(x2 - corner, y, x2, y2 - corner, sw, sh);
        } else {
            d2TexQuad(x + corner, y, x2, y2, sw, sh);
            d2TexQuad(x, y, x + corner, y2 - corner, sw, sh);
        }
        int n = (int) Math.ceil(corner * d2ScaleFactor * 2.0F);
        n = Math.max(12, Math.min(D2_MAX_SEGMENTS, n));
        d2BuildTables(n);
        float[] cs = D2_COS[n];
        float[] sn = D2_SIN[n];
        float cx = d2RoundRight ? x2 - corner : x + corner;
        float cy = y2 - corner;
        float dir = d2RoundRight ? -1.0F : 1.0F;
        for (int i = 0; i < n; i++) {
            d2TexVertex(cx, cy, sw, sh);
            d2TexVertex(cx + dir * corner * cs[i], cy + corner * sn[i], sw, sh);
            d2TexVertex(cx + dir * corner * cs[i + 1], cy + corner * sn[i + 1], sw, sh);
        }
    }

    public static int getLongestModule() {
        RavenFontRenderer hudFont = getHudFontRenderer();
        int length = 0;

        for (Module module : ModuleManager.organizedModules) {
            if (module.isEnabled()) {
                length = Math.max(length, hudFont.getStringWidth(getHudRenderText(module)));
            }
        }

        return length;
    }

    private static boolean shouldSkipModule(Module module, boolean removeVelocity) {
        if (module.isHidden()) {
            return true;
        }
        if (module == ModuleManager.commandLine) {
            return true;
        }
        return module instanceof Velocity && removeVelocity;
    }

    private static boolean isLastVisibleModule(Module currentModule, boolean removeVelocity) {
        boolean foundCurrent = false;

        for (Module module : ModuleManager.organizedModules) {
            if (!foundCurrent) {
                if (module == currentModule) {
                    foundCurrent = true;
                }
                continue;
            }

            if (module.isEnabled() && !(module instanceof HUD) && !shouldSkipModule(module, removeVelocity)) {
                return false;
            }
        }

        return true;
    }

    static class EditScreen extends GuiScreen {
        private static final String EXAMPLE = "This is an-Example-HUD";

        private GuiButtonExt resetPosition;
        private boolean dragging = false;
        private float minX = 0.0f;
        private float minY = 0.0f;
        private float maxX = 0.0f;
        private float maxY = 0.0f;
        private float actualX = 5.0f;
        private float actualY = 70.0f;
        private float lastActualX = 0.0f;
        private float lastActualY = 0.0f;
        private int lastMouseX = 0;
        private int lastMouseY = 0;
        private float clickMinX = 0.0f;

        @Override
        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            HUD.syncPositionToResolution(new ScaledResolution(this.mc));
            this.actualX = HUD.posX;
            this.actualY = HUD.posY;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            ScaledResolution resolution = new ScaledResolution(this.mc);
            if (!this.dragging) {
                HUD.syncPositionToResolution(resolution);
                this.actualX = HUD.posX;
                this.actualY = HUD.posY;
            }
            drawRect(0, 0, this.width, this.height, -1308622848);
            float previewX = this.actualX;
            float previewY = this.actualY;
            float previewMaxX = previewX + 50.0f;
            float previewMaxY = previewY + 32.0f;
            float[] clickPos = this.getPreviewBounds(EXAMPLE);

            this.minX = previewX;
            this.minY = previewY;

            if (clickPos == null) {
                this.maxX = previewMaxX;
                this.maxY = previewMaxY;
                this.clickMinX = previewX;
            }
            else {
                this.maxX = clickPos[0];
                this.maxY = clickPos[1];
                this.clickMinX = clickPos[2];
            }

            HUD.setAbsolutePosition(previewX, previewY, resolution);

            int textX = resolution.getScaledWidth() / 2 - 84;
            int textY = resolution.getScaledHeight() / 2 - 20;
            RenderUtils.drawColoredString("Edit the HUD position by dragging.", '-', textX, textY, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            }
            catch (IOException ignored) {
            }

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        private float[] getPreviewBounds(String text) {
            RavenFontRenderer hudFont = HUD.getHudFontRenderer();

            if (empty()) {
                float x = this.minX;
                float y = this.minY;
                String[] lines = text.split("-");
                int localTextTopPadding = getHudTextTopPadding();
                int localTextBottomPadding = getHudTextBottomPadding();
                int localRowHeight = getHudRowHeight(hudFont.getTextTopOffset(), hudFont.getTextBottomOffset(), localTextTopPadding, localTextBottomPadding);

                for (String line : lines) {
                    if (HUD.alignRight.isToggled()) {
                        x += hudFont.getStringWidth(lines[0]) - hudFont.getStringWidth(line);
                    }
                    float textY = getHudTextY(y, hudFont.getTextTopOffset(), localTextTopPadding);
                    drawHudText(hudFont, line, x, textY, Color.white.getRGB());
                    y += localRowHeight;
                }
                return null;
            }

            int longestModule = getLongestModule();
            float y = this.minY;
            double verticalWaveAccum = 0.0;
            boolean firstVisibleRow = true;
            String previousModule = "";
            double lastOutlineLeft = 0.0;
            double lastOutlineRight = 0.0;
            double lastBackgroundBottom = 0.0;
            boolean removeVelocity = false;
            int textTopOffset = hudFont.getTextTopOffset();
            int textBottomOffset = hudFont.getTextBottomOffset();
            int horizontalTextPadding = getHudHorizontalTextPadding();
            int textTopPadding = getHudTextTopPadding();
            int textBottomPadding = getHudTextBottomPadding();
            int outlineThickness = getHudOutlineThickness();
            int rowHeight = getHudRowHeight(textTopOffset, textBottomOffset, textTopPadding, textBottomPadding);

            try {
                for (Module module : ModuleManager.organizedModules) {
                    if (!module.isEnabled() || module instanceof HUD || shouldSkipModule(module, removeVelocity)) {
                        continue;
                    }

                    String moduleName = getHudRenderText(module);
                    int moduleWidth = hudFont.getStringWidth(moduleName);
                    float xPos = posX;
                    float textY = getHudTextY(y, textTopOffset, textTopPadding);
                    double backgroundLeft = xPos - horizontalTextPadding;
                    double backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                    double backgroundTop = y;
                    double backgroundBottom = y + rowHeight;
                    double outlineLeft = backgroundLeft - outlineThickness;
                    double outlineRight = backgroundRight + outlineThickness;
                    double outlineTop = backgroundTop - outlineThickness;

                    if (alignRight.isToggled()) {
                        xPos -= moduleWidth;
                        backgroundLeft = xPos - horizontalTextPadding;
                        backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                        outlineLeft = backgroundLeft - outlineThickness;
                        outlineRight = backgroundRight + outlineThickness;
                    }

                    double rowCenterX = (backgroundLeft + backgroundRight) * 0.5;
                    double wavePhase = hudWavePhase(verticalWaveAccum, rowCenterX);
                    int color = getHudColor(wavePhase);

                    if (outline.getInput() == 1 && firstVisibleRow) {
                        RenderUtils.drawRect(outlineLeft, outlineTop, outlineRight, backgroundTop, color);
                    }

                    if (hudWaveIsVertical()) {
                        verticalWaveAccum += getVerticalWaveStep();
                    }
                    firstVisibleRow = false;

                    if (drawBackground.isToggled()) {
                        RenderUtils.drawRect(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
                    }

                    if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                        double difference = hudFont.getStringWidth(previousModule) - moduleWidth;
                        if (alphabeticalSort.isToggled() && difference < 0) {
                            RenderUtils.drawRect(outlineLeft, outlineTop, xPos - difference + horizontalTextPadding + outlineThickness, backgroundTop, color);
                        }
                        else if (alignRight.isToggled()) {
                            RenderUtils.drawRect(xPos - difference - horizontalTextPadding - outlineThickness, outlineTop, backgroundLeft, backgroundTop, color);
                        }
                        else {
                            RenderUtils.drawRect(backgroundRight, outlineTop, xPos + difference + moduleWidth + horizontalTextPadding + outlineThickness, backgroundTop, color);
                        }
                    }

                    if (outline.getInput() > 0) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                        }
                        else {
                            RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                        }
                    }

                    if (outline.getInput() == 1) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                        }
                        else {
                            RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                        }
                    }

                    drawHudText(hudFont, moduleName, xPos, textY, color);
                    previousModule = moduleName;
                    lastOutlineLeft = outlineLeft;
                    lastOutlineRight = outlineRight;
                    lastBackgroundBottom = backgroundBottom;
                    y += rowHeight;
                }
            }
            catch (Exception exception) {
                Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
                exception.printStackTrace();
            }

            if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                double bottomCenterX = (lastOutlineLeft + lastOutlineRight) * 0.5;
                double bottomPhase = hudWavePhase(verticalWaveAccum, bottomCenterX);
                RenderUtils.drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, getHudColor(bottomPhase));
            }

            return new float[]{this.minX + longestModule, (float) Math.ceil(Math.max(y, lastBackgroundBottom)), this.minX - longestModule};
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);

            if (button != 0) {
                return;
            }

            if (this.dragging) {
                this.actualX = this.lastActualX + (mouseX - this.lastMouseX);
                this.actualY = this.lastActualY + (mouseY - this.lastMouseY);
            }
            else if (mouseX > this.clickMinX && mouseX < this.maxX && mouseY > this.minY && mouseY < this.maxY) {
                this.dragging = true;
                this.lastMouseX = mouseX;
                this.lastMouseY = mouseY;
                this.lastActualX = this.actualX;
                this.lastActualY = this.actualY;
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        @Override
        public void actionPerformed(GuiButton button) {
            if (button == this.resetPosition) {
                HUD.resetPosition(new ScaledResolution(this.mc));
                this.actualX = HUD.posX;
                this.actualY = HUD.posY;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        private boolean empty() {
            for (Module module : ModuleManager.organizedModules) {
                if (module.isEnabled() && !module.getName().equals("HUD")) {
                    if (module.isHidden()) {
                        continue;
                    }
                    if (module == ModuleManager.commandLine) {
                        continue;
                    }
                    return false;
                }
            }
            return true;
        }
    }

    public static RavenFontRenderer getHudFontRenderer() {
        return FontManager.getHudRenderer(getSelectedFontName(), getSelectedFontScale());
    }

    public static String getHudText(Module module) {
        String moduleName = module.getNameInHud();
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase();
        }
        return moduleName;
    }

    public static String getHudRenderText(Module module) {
        String moduleName = getHudText(module);
        if (showInfo != null && showInfo.isToggled() && !module.getInfo().isEmpty()) {
            moduleName += " \u00a77" + module.getInfo();
        }
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase();
        }
        return moduleName;
    }

    public static String getSelectedFontName() {
        if (font == null) {
            return HUD_FONT_OPTIONS[0];
        }
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    public static float getSelectedFontScale() {
        if (fontSize == null) {
            return 1.0f;
        }
        return (float) fontSize.getInput();
    }

    public static float getRelativePosX() {
        syncPositionToResolution();
        return relativePosX;
    }

    public static float getRelativePosY() {
        syncPositionToResolution();
        return relativePosY;
    }

    public static void setRelativePosition(float normalizedX, float normalizedY) {
        relativePosX = normalizedX;
        relativePosY = normalizedY;
        syncPositionToResolution();
    }

    public static void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new ScaledResolution(mc));
    }

    public static void resetPosition() {
        resetPosition(new ScaledResolution(mc));
    }

    private static void syncPositionToResolution() {
        syncPositionToResolution(new ScaledResolution(mc));
    }

    private static void syncPositionToResolution(ScaledResolution resolution) {
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());

        if (Float.isNaN(relativePosX) || Float.isNaN(relativePosY)) {
            relativePosX = posX / scaledWidth;
            relativePosY = posY / scaledHeight;
        }

        posX = relativePosX * scaledWidth;
        posY = relativePosY * scaledHeight;
    }

    private static void setAbsolutePosition(float absoluteX, float absoluteY, ScaledResolution resolution) {
        posX = absoluteX;
        posY = absoluteY;

        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());
        relativePosX = absoluteX / scaledWidth;
        relativePosY = absoluteY / scaledHeight;
    }

    private static void resetPosition(ScaledResolution resolution) {
        setAbsolutePosition(DEFAULT_POS_X, DEFAULT_POS_Y, resolution);
    }

    private static int getHudHorizontalTextPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudTextTopPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudTextBottomPadding() {
        return 0;
    }

    private static int getHudOutlineThickness() {
        return getScaledHudPixels(1.0f);
    }

    private static int getHudRowHeight(int textTopOffset, int textBottomOffset, int textTopPadding, int textBottomPadding) {
        int textBoxHeight = Math.max(1, textBottomOffset - textTopOffset);
        return Math.max(1, textBoxHeight + textTopPadding + textBottomPadding);
    }

    private static float getHudTextY(float rowTop, int textTopOffset, int textTopPadding) {
        return rowTop + textTopPadding - textTopOffset;
    }

    private static int getScaledHudPixels(float basePixels) {
        return Math.max(1, Math.round(basePixels * getSelectedFontScale()));
    }

    private static boolean shouldDrawTextShadow() {
        return textShadow == null || textShadow.isToggled();
    }

    private static boolean hudWaveIsVertical() {
        return waveAxis == null || (int) waveAxis.getInput() == 0;
    }

    private static double hudWavePhase(double verticalAccum, double rowCenterX) {
        if (hudWaveIsVertical()) {
            return verticalAccum;
        }
        return rowCenterX * (HUD_WAVE_HORIZONTAL_X_SCALE / getWaveLengthMultiplier()) * getHorizontalWaveDirectionSign();
    }

    private static void drawHudText(RavenFontRenderer hudFont, String moduleName, float xPos, float textY, int fallbackColor) {
        if (!shouldUseHorizontalWaveText()) {
            hudFont.drawString(moduleName, xPos, textY, fallbackColor, shouldDrawTextShadow());
            return;
        }

        hudFont.drawGlyphString(moduleName, xPos, textY, (character, xOffset, width, formattingColor) -> {
            if (formattingColor != null) {
                return formattingColor;
            }
            return getHudColor(hudWavePhase(0.0, xPos + xOffset + width * 0.5f));
        }, shouldDrawTextShadow());
    }

    private static boolean shouldUseHorizontalWaveText() {
        return colorMode != null && (int) colorMode.getInput() != 0 && !hudWaveIsVertical();
    }

    private static double getVerticalWaveStep() {
        return (12.0 / getWaveLengthMultiplier()) * getVerticalWaveDirectionSign();
    }

    private static int getVerticalWaveDirectionSign() {
        return verticalWaveDirection == null || (int) verticalWaveDirection.getInput() == 0 ? -1 : 1;
    }

    private static int getHorizontalWaveDirectionSign() {
        return horizontalWaveDirection == null || (int) horizontalWaveDirection.getInput() == 0 ? -1 : 1;
    }

    /**
     * Accent color for HUD rows/outlines. Other modules can match HUD when enabled.
     */
    public static int getHudColor(double gradientOffset) {
        if (colorMode == null || hudColor == null) {
            return 0xFFFFFF;
        }
        int mode = (int) colorMode.getInput();
        if (mode == 2) {
            return getRainbowWaveColor(gradientOffset);
        }
        if (mode == 1 && hudColor2 != null) {
            java.awt.Color c1 = new java.awt.Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue());
            java.awt.Color c2 = new java.awt.Color(hudColor2.getRed(), hudColor2.getGreen(), hudColor2.getBlue());
            return getGradientWaveColor(c1, c2, gradientOffset);
        }
        return hudColor.getRGB();
    }

    private static int getGradientWaveColor(java.awt.Color c1, java.awt.Color c2, double gradientOffset) {
        double animationProgress = (Math.sin(getAnimatedWaveAngle(gradientOffset)) + 1.0) * 0.5;
        return Theme.convert(c1, c2, animationProgress).getRGB();
    }

    private static int getRainbowWaveColor(double gradientOffset) {
        double hue = getAnimatedWaveAngle(gradientOffset) / (Math.PI * 2.0);
        hue -= Math.floor(hue);
        return Color.getHSBColor((float) hue, 1.0F, 1.0F).getRGB();
    }

    private static double getAnimatedWaveAngle(double gradientOffset) {
        return System.currentTimeMillis() / (double) HUD_RAINBOW_PERIOD_MS * (Math.PI * 2.0) * getWaveSpeedMultiplier()
                + gradientOffset * HUD_WAVE_ANGLE_SCALE;
    }

    private static double getWaveSpeedMultiplier() {
        return waveSpeed == null ? 1.0 : Math.max(0.1, waveSpeed.getInput());
    }

    private static double getWaveLengthMultiplier() {
        return waveLength == null ? 1.0 : Math.max(0.5, waveLength.getInput());
    }

}
