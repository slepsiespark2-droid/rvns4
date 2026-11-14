package ravens4.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import ravens4.Raven;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.setting.Setting;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.ColorSetting;
import ravens4.module.setting.impl.GroupSetting;
import ravens4.module.setting.impl.KeySetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.module.setting.impl.TextSetting;
import ravens4.utility.RenderUtils;
import ravens4.utility.ThemePalette;
import ravens4.utility.font.FontManager;
import ravens4.utility.font.RavenFontRenderer;
import ravens4.utility.shader.BlurUtils;
import ravens4.utility.profile.Profile;
import ravens4.utility.profile.ProfileManager;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "Panel" click GUI, ported from the Atlas client: one dark rounded panel per category, a title with the number of
 * modules that are on, an accent line under it, modules listed by name (with an accent tick and colour when on) and a
 * "+" that unrolls their settings in place, plus a Configs panel and a small info bar at the bottom left.
 *
 * Works with single taps: tap a title to fold the panel (drag it to move it), tap a module to switch it,
 * tap its "+" for the settings. A right click on a module opens its settings too, a middle click binds it.
 */
public class PanelClickGui extends GuiScreen {
    private static final float WIDTH = 108.0F;
    private static final float HEADER = 24.0F;
    private static final float MODULE_H = 18.0F;
    private static final float SETTING_H = 15.0F;
    private static final float SLIDER_H = 22.0F;
    private static final float RADIUS = 8.0F;
    private static final int MAX_ROWS = 16;

    private static final int BG = 0xC8121217;
    private static final int SETTINGS_BG = 0x40000000;
    private static final int TEXT = 0xFFE6E6EC;
    private static final int DIM = 0xFF8E8E9A;
    private static final int FAINT = 0xFF5E5E68;

    private enum Kind { HEADER, MODULE, EXPAND, BIND, BOOL, BUTTON, NUM, MODE, COLOR, TEXT, KEY, GROUP, PROFILE, NAME, LOAD, SAVE, SCROLL }

    private static final class Hit {
        final Kind kind;
        final Panel panel;
        final Object target;
        final float x1, y1, x2, y2;
        float trackX, trackW;

        Hit(Kind kind, Panel panel, Object target, float x1, float y1, float x2, float y2) {
            this.kind = kind;
            this.panel = panel;
            this.target = target;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        boolean contains(float x, float y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private static final class Panel {
        final String title;
        final List<Module> modules;
        final boolean configs;
        float x, y;
        boolean folded = true;
        float scroll;
        float maxScroll;
        float shownScroll;
        float drawY, body, content, total;
        float shownHeight = HEADER;

        Panel(String title, List<Module> modules, boolean configs) {
            this.title = title;
            this.modules = modules;
            this.configs = configs;
        }

        boolean over(float mx, float my) {
            return mx >= x && mx <= x + WIDTH && my >= y && my <= y + shownHeight;
        }
    }

    private static PanelClickGui instance;

    private final List<Panel> panels = new ArrayList<Panel>();
    private final List<Hit> hits = new ArrayList<Hit>();
    private final Set<Module> expanded = new HashSet<Module>();
    private final Map<String, Float> anims = new HashMap<String, Float>();
    private final List<int[]> clips = new ArrayList<int[]>();

    private Panel dragging;
    private float dragX, dragY, pressX, pressY;
    private boolean dragMoved;
    private Hit slider;
    private Hit hue;
    private Panel scrolling;

    private Module listeningModule;
    private KeySetting listeningKey;
    private TextSetting editingText;
    private boolean editingName;
    private String buffer = "";

    private String configName = "";
    private List<String> configs = new ArrayList<String>();
    private long configsRead;
    private String status = "";
    private long statusAt;

    private long openedAt;
    private long lastFrame;
    private float dt = 0.016F;
    private float fade = 1.0F;
    private boolean built;
    private boolean stateLoaded;

    private PanelClickGui() {
    }

    public static PanelClickGui get() {
        if (instance == null) {
            instance = new PanelClickGui();
        }
        return instance;
    }

    // ------------------------------------------------------------------ setup

    private void build() {
        panels.clear();
        for (Module.category c : Module.category.values()) {
            if (c == Module.category.profiles) {
                continue;
            }
            List<Module> list = new ArrayList<Module>();
            for (Module m : ModuleManager.modules) {
                if (m.moduleCategory() == c && !m.isHidden()) {
                    list.add(m);
                }
            }
            if (list.isEmpty()) {
                continue;
            }
            Collections.sort(list, new Comparator<Module>() {
                @Override
                public int compare(Module a, Module b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            String n = c.name();
            panels.add(new Panel(n.substring(0, 1).toUpperCase() + n.substring(1), list, false));
        }
        panels.add(new Panel("Configs", Collections.<Module>emptyList(), true));
        final java.util.List<String> order = java.util.Arrays.asList("Combat", "Player", "Movement", "Render", "Client", "Other", "Scripts");
        Collections.sort(panels, new Comparator<Panel>() {
            @Override
            public int compare(Panel a, Panel b) {
                return rank(a) - rank(b);
            }

            private int rank(Panel p) {
                if (p.configs) {
                    return 1000;
                }
                int i = order.indexOf(p.title);
                return i < 0 ? 100 : i;
            }
        });
        built = true;
    }

    // folded panels in one column at the left (a second column if the screen is too short)
    private void layout() {
        float x = 4f;
        float y = 2f;
        for (Panel p : panels) {
            if (y + HEADER > height - 26f && y > 2f) {
                x += WIDTH + 6f;
                y = 2f;
            }
            p.x = x;
            p.y = y;
            y += HEADER + 1f;
        }
    }

    /** Back to the default: every panel folded, in one column at the left. */
    public void resetLayout() {
        if (!built) {
            build();
        }
        for (Panel p : panels) {
            p.folded = true;
            p.scroll = 0f;
        }
        layout();
        saveState();
        tell("Panel layout reset");
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        if (!built) {
            build();
            layout();
        }
        if (!stateLoaded) {
            stateLoaded = true;
            loadState();
        }
        openedAt = System.currentTimeMillis();
        configsRead = 0L;
        slider = null;
        hue = null;
        dragging = null;
        lastFrame = 0L;
        for (Panel p : panels) {
            p.shownScroll = p.scroll;
            p.x = Math.max(0f, Math.min(width - WIDTH, p.x));
            p.y = Math.max(0f, Math.min(height - HEADER, p.y));
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        commitEdit();
        listeningModule = null;
        listeningKey = null;
        dragging = null;
        slider = null;
        hue = null;
        scrolling = null;
        saveState();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static File stateFile() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "rvns4_panel_gui_v2.txt");
    }

    private void saveState() {
        try (PrintWriter w = new PrintWriter(new FileWriter(stateFile()))) {
            for (Panel p : panels) {
                w.println(p.title + "|" + p.x + "|" + p.y + "|" + p.folded);
            }
        } catch (Exception ignored) {
        }
    }

    private void loadState() {
        File f = stateFile();
        if (!f.exists()) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length < 4) {
                    continue;
                }
                for (Panel p : panels) {
                    if (p.title.equals(parts[0])) {
                        p.x = Float.parseFloat(parts[1]);
                        p.y = Float.parseFloat(parts[2]);
                        p.folded = Boolean.parseBoolean(parts[3]);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ look

    private static int accent() {
        try {
            if (ravens4.module.impl.client.Gui.outlineTheme != null) {
                int idx = (int) ravens4.module.impl.client.Gui.outlineTheme.getInput();
                if (idx >= 0 && idx < ThemePalette.getThemeOptions().length) {
                    return ThemePalette.getThemeColor(idx, System.currentTimeMillis(), 0.0F) | 0xFF000000;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0xFF9B5CFF;
    }

    private static float opacity() {
        ravens4.module.setting.impl.SliderSetting s = ravens4.module.impl.client.Gui.panelOpacity;
        return s == null ? 0.78F : (float) (s.getInput() / 100.0);
    }

    private static float textOpacity() {
        ravens4.module.setting.impl.SliderSetting s = ravens4.module.impl.client.Gui.panelTextOpacity;
        return s == null ? 1.0F : (float) (s.getInput() / 100.0);
    }

    // the panel background with the chosen opacity
    private static int bgColor() {
        return (Math.max(1, Math.min(255, Math.round(255.0F * opacity()))) << 24) | 0x121217;
    }

    // the edge fades follow the panel background
    private static int fadeColor() {
        int alpha = Math.min(255, Math.round(255.0F * opacity() * 0xB0 / 0xC8));
        return (alpha << 24) | 0x121217;
    }

    // text colour with the opening fade and the text opacity applied
    private int ta(int color) {
        int alpha = Math.round((color >>> 24) * fade * textOpacity());
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    // colour with its alpha scaled by the opening fade
    private int a(int color) {
        int alpha = Math.round((color >>> 24) * fade);
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private static RavenFontRenderer titleFont() {
        return FontManager.getHudRenderer("Sf-Bold", 1.15F);
    }

    private static RavenFontRenderer rowFont() {
        return FontManager.getHudRenderer("Sf-Regular", 0.95F);
    }

    private static RavenFontRenderer smallFont() {
        return FontManager.getHudRenderer("Sf-Regular", 0.82F);
    }

    private void text(RavenFontRenderer font, String s, float x, float top, float height, int color) {
        if (font == null) {
            mc.fontRendererObj.drawString(s, (int) x, (int) (top + (height - 8f) / 2f), ta(color));
            return;
        }
        font.drawString(s, x, top + (height - font.getFontHeight()) / 2.0F + 1.0F, ta(color));
    }

    private void textRight(RavenFontRenderer font, String s, float right, float top, float height, int color) {
        text(font, s, right - width(font, s), top, height, color);
    }

    private static float width(RavenFontRenderer font, String s) {
        return font == null ? Minecraft.getMinecraft().fontRendererObj.getStringWidth(s) : (float) font.getStringWidth(s);
    }

    private static String trim(RavenFontRenderer font, String s, float max) {
        if (width(font, s) <= max) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && width(font, t + "..") > max) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "..";
    }

    private float ease(String key, float target, float speed) {
        Float now = anims.get(key);
        float v = now == null ? target : now;
        v += (target - v) * Math.min(1.0F, dt * speed);
        if (Math.abs(target - v) < 0.002F) {
            v = target;
        }
        anims.put(key, v);
        return v;
    }

    private float peek(String key, float fallback) {
        Float v = anims.get(key);
        return v == null ? fallback : v;
    }

    private static int mix(int from, int to, float t) {
        int a1 = from >>> 24, r1 = from >> 16 & 255, g1 = from >> 8 & 255, b1 = from & 255;
        int a2 = to >>> 24, r2 = to >> 16 & 255, g2 = to >> 8 & 255, b2 = to & 255;
        return Math.round(a1 + (a2 - a1) * t) << 24 | Math.round(r1 + (r2 - r1) * t) << 16
                | Math.round(g1 + (g2 - g1) * t) << 8 | Math.round(b1 + (b2 - b1) * t);
    }

    // ------------------------------------------------------------------ shapes

    private static void shapePath(float x, float y, float w, float h, float r) {
        GL11.glVertex2f(x, y + r);
        arc(x + r, y + r, r, 180, 270);
        arc(x + w - r, y + r, r, 270, 360);
        arc(x + w - r, y + h - r, r, 0, 90);
        arc(x + r, y + h - r, r, 90, 180);
    }

    private static void arc(float cx, float cy, float r, int from, int to) {
        int steps = r < 0.5f ? 1 : 8;
        for (int i = 0; i <= steps; i++) {
            double ang = Math.toRadians(from + (to - from) * (double) i / steps);
            GL11.glVertex2f((float) (cx + Math.cos(ang) * r), (float) (cy + Math.sin(ang) * r));
        }
    }

    private static void begin(int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        // the alpha test throws away anything fainter than about 10%, which is exactly what a shadow is made of
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
    }

    private static void end() {
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void rrect(float x, float y, float w, float h, float radius, int color) {
        if (w <= 0f || h <= 0f || (color >>> 24) == 0) {
            return;
        }
        float r = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
        begin(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + w / 2f, y + h / 2f);
        shapePath(x, y, w, h, r);
        GL11.glVertex2f(x, y + r);
        GL11.glEnd();
        end();
    }

    private static void rim(float x, float y, float w, float h, float radius, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        float r = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
        begin(color);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        shapePath(x, y, w, h, r);
        GL11.glEnd();
        end();
    }

    private static void rect(float x1, float y1, float x2, float y2, int color) {
        if ((color >>> 24) == 0 || x2 <= x1 || y2 <= y1) {
            return;
        }
        begin(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        end();
    }

    // a soft shadow: translucent rounded rectangles, each a little larger and fainter. The Shadow setting
    // goes from 0 (off) to 8 (widest).
    private void shadow(float x, float y, float w, float h, float radius) {
        ravens4.module.setting.impl.SliderSetting level = ravens4.module.impl.client.Gui.panelShadow;
        if (level == null || level.getInput() <= 0.0) {
            return;
        }
        float size = (float) level.getInput() * 2.0F;
        int layers = 6;
        for (int i = layers; i >= 1; i--) {
            float grow = size * i / layers;
            rrect(x - grow, y - grow + 2f, w + grow * 2f, h + grow * 2f, radius + grow, a(0x14000000));
        }
    }

    private void pushClip(float x, float y, float w, float h) {
        ScaledResolution sr = new ScaledResolution(mc);
        int f = sr.getScaleFactor();
        int left = (int) Math.floor(x * f);
        int bottom = (int) Math.floor((sr.getScaledHeight() - (y + h)) * f);
        int cw = Math.max(0, (int) Math.ceil(w * f));
        int ch = Math.max(0, (int) Math.ceil(h * f));
        if (!clips.isEmpty()) {
            int[] p = clips.get(clips.size() - 1);
            int nl = Math.max(left, p[0]);
            int nb = Math.max(bottom, p[1]);
            int nr = Math.min(left + cw, p[0] + p[2]);
            int nt = Math.min(bottom + ch, p[1] + p[3]);
            left = nl;
            bottom = nb;
            cw = Math.max(0, nr - nl);
            ch = Math.max(0, nt - nb);
        }
        clips.add(new int[]{left, bottom, cw, ch});
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, cw, ch);
    }

    private void popClip() {
        if (clips.isEmpty()) {
            return;
        }
        clips.remove(clips.size() - 1);
        if (clips.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            int[] p = clips.get(clips.size() - 1);
            GL11.glScissor(p[0], p[1], p[2], p[3]);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void updateScreen() {
        // modules show and hide their own settings in guiUpdate, which the old click GUI calls while it is open
        try {
            for (Module m : new ArrayList<Module>(ModuleManager.modules)) {
                m.guiUpdate();
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        dt = lastFrame == 0L ? 0.016F : Math.min(0.05F, (now - lastFrame) / 1.0E9F);
        lastFrame = now;
        long elapsed = System.currentTimeMillis() - openedAt;
        hits.clear();
        clips.clear();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        ScaledResolution sr = new ScaledResolution(mc);
        int accent = accent();

        if (dragging != null && dragMoved) {
            dragging.x = Math.max(0.0F, Math.min(sr.getScaledWidth() - WIDTH, mouseX - dragX));
            dragging.y = Math.max(0.0F, Math.min(sr.getScaledHeight() - HEADER, mouseY - dragY));
        }
        if (slider != null && Mouse.isButtonDown(0)) {
            setFromSlider(slider, mouseX);
        } else {
            slider = null;
        }
        if (hue != null && Mouse.isButtonDown(0)) {
            setHue(hue, mouseX);
        } else {
            hue = null;
        }
        if (scrolling != null && Mouse.isButtonDown(0)) {
            setScrollFromMouse(scrolling, mouseY);
        } else {
            scrolling = null;
        }

        float blurEase = Math.min(1.0F, elapsed / 250.0F);
        // blur of the whole world behind the menu: the same blur and strength as the normal click GUI
        ravens4.module.setting.impl.SliderSetting backgroundBlur = ravens4.module.impl.client.Gui.backgroundBlur;
        if (backgroundBlur != null && backgroundBlur.getInput() > 0.0) {
            BlurUtils.prepareBlur();
            rect(0.0F, 0.0F, sr.getScaledWidth(), sr.getScaledHeight(), 0xFF000000);
            BlurUtils.blurEnd(2, (float) (3.0 * ((backgroundBlur.getInput() + 35.0) / 100.0)) * blurEase);
        }

        // the world dims, with a faint wash of the accent from below
        fade = Math.min(1.0F, elapsed / 180.0F);
        drawGradientRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), a(0x70000000), a(0x30000000 | accent & 0xFFFFFF));

        // each panel drops in a little after the one before it
        float[] shown = new float[panels.size()];
        for (int i = 0; i < panels.size(); i++) {
            float t = Math.max(0.0F, Math.min(1.0F, (elapsed - i * 22.0F) / 240.0F));
            t = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            shown[i] = t;
            measure(panels.get(i), sr, (1.0F - t) * -10.0F);
        }
        // frosted glass: blur only what is behind the panels
        ravens4.module.setting.impl.SliderSetting panelBlur = ravens4.module.impl.client.Gui.panelBlur;
        if (panelBlur != null && panelBlur.getInput() > 0.0) {
            BlurUtils.prepareBlur();
            for (int i = 0; i < panels.size(); i++) {
                Panel p = panels.get(i);
                if (shown[i] > 0.02F) {
                    rrect(p.x, p.drawY, WIDTH, p.total, RADIUS, 0xFF000000);
                }
            }
            BlurUtils.blurEnd(2, (float) (3.0 * ((panelBlur.getInput() + 35.0) / 100.0)) * blurEase);
        }
        for (int i = 0; i < panels.size(); i++) {
            fade = shown[i];
            if (fade > 0.02F) {
                drawPanel(panels.get(i), mouseX, mouseY, accent);
            }
        }
        fade = Math.min(1.0F, elapsed / 180.0F);
        drawInfoBar(sr, accent);

        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 2500L) {
            RavenFontRenderer font = rowFont();
            float w = width(font, status) + 20.0F;
            float x = (sr.getScaledWidth() - w) / 2.0F;
            float y = sr.getScaledHeight() - 34.0F;
            rrect(x, y, w, 18.0F, 6.0F, a(bgColor()));
            rect(x + 6.0F, y + 16.5F, x + w - 6.0F, y + 17.5F, a(accent));
            text(font, status, x + 10.0F, y, 18.0F, TEXT);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.color(1f, 1f, 1f, 1f);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // where the panel is this frame, how tall, and how far it scrolls
    private void measure(Panel panel, ScaledResolution sr, float offset) {
        panel.drawY = panel.y + offset;
        float content = panel.folded ? 0.0F : contentHeight(panel);
        float room = Math.max(0.0F, sr.getScaledHeight() - panel.y - HEADER - 8.0F);
        float cap = panel.configs ? room : MAX_ROWS * MODULE_H + 4.0F;
        float body = Math.min(content, Math.min(room, cap));
        panel.content = content;
        panel.body = ease("body:" + panel.title, body, 18.0F);
        panel.maxScroll = Math.max(0.0F, content - body);
        panel.scroll = Math.max(0.0F, Math.min(panel.maxScroll, panel.scroll));
        panel.shownScroll += (panel.scroll - panel.shownScroll) * Math.min(1.0F, dt * 16.0F);
        panel.shownScroll = Math.max(0.0F, Math.min(panel.maxScroll, panel.shownScroll));
        panel.total = HEADER + (panel.body > 0.5F ? panel.body + 4.0F : 0.0F);
        panel.shownHeight = panel.total;
    }

    private void drawPanel(Panel panel, float mx, float my, int accent) {
        float x = panel.x;
        float y = panel.drawY;
        float total = panel.total;
        float body = panel.body;

        shadow(x, y, WIDTH, total, RADIUS);
        rrect(x, y, WIDTH, total, RADIUS, a(bgColor()));
        // a hairline rim catches the light along the edge
        rim(x, y, WIDTH, total, RADIUS, a(0x1EFFFFFF));

        text(titleFont(), panel.title, x + 10.0F, y + 1.0F, HEADER - 4.0F, TEXT);
        if (!panel.folded) {
            String count = panel.configs ? String.valueOf(configs.size()) : String.valueOf(enabledIn(panel));
            textRight(smallFont(), count, x + WIDTH - 10.0F, y + 1.0F, HEADER - 4.0F, FAINT);
        }
        hits.add(new Hit(Kind.HEADER, panel, null, x, y, x + WIDTH, y + HEADER));
        if (body <= 0.5F) {
            return;
        }

        float clipTop = y + HEADER + 1.0F;
        float clipBottom = y + HEADER + body + 2.0F;
        pushClip(x, clipTop, WIDTH, clipBottom - clipTop);
        float rowY = clipTop + 2.0F - panel.shownScroll;
        if (panel.configs) {
            drawConfigs(panel, x, rowY, clipTop, clipBottom, mx, my, accent);
        } else {
            for (Module module : panel.modules) {
                rowY = drawModule(panel, module, x, rowY, clipTop, clipBottom, mx, my, accent);
            }
        }
        popClip();

        if (panel.maxScroll > 0.5F) {
            // fades at the cut edges, so rows slide under instead of being chopped
            if (panel.shownScroll > 1.0F) {
                drawGradientRect((int) x + 2, (int) clipTop, (int) (x + WIDTH) - 2, (int) clipTop + 8, a(fadeColor()), 0x00121217);
            }
            if (panel.shownScroll < panel.maxScroll - 1.0F) {
                drawGradientRect((int) x + 2, (int) clipBottom - 8, (int) (x + WIDTH) - 2, (int) clipBottom, 0x00121217, a(fadeColor()));
            }
            float track = body - 4.0F;
            float bar = Math.max(14.0F, track * body / panel.content);
            float barY = clipTop + 2.0F + (track - bar) * (panel.shownScroll / panel.maxScroll);
            rrect(x + WIDTH - 3.5F, barY, 2.0F, bar, 1.0F, a(0x50FFFFFF));
            // a wider invisible strip to grab with a finger
            hits.add(new Hit(Kind.SCROLL, panel, null, x + WIDTH - 9.0F, clipTop, x + WIDTH, clipBottom));
        }
    }

    private static int enabledIn(Panel panel) {
        int on = 0;
        for (Module module : panel.modules) {
            if (module.isEnabled()) {
                on++;
            }
        }
        return on;
    }

    // a small bar at the bottom left: client, server, FPS and the time
    private void drawInfoBar(ScaledResolution sr, int accent) {
        RavenFontRenderer font = smallFont();
        String server = mc.getCurrentServerData() == null || mc.isSingleplayer() ? "Singleplayer" : mc.getCurrentServerData().serverIP;
        String[] parts = {"rvns4", server, Minecraft.getDebugFPS() + " FPS", new SimpleDateFormat("HH:mm").format(new Date())};
        float w = 12.0F;
        for (String part : parts) {
            w += width(font, part) + 12.0F;
        }
        float x = 6.0F;
        float y = sr.getScaledHeight() - 22.0F;
        rrect(x, y, w, 16.0F, 8.0F, a(bgColor()));
        float cx = x + 10.0F;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                rrect(cx - 7.5F, y + 6.5F, 3.0F, 3.0F, 1.5F, a(accent));
            }
            text(font, parts[i], cx, y, 16.0F, i == 0 ? accent : TEXT);
            cx += width(font, parts[i]) + 12.0F;
        }
    }

    private boolean visible(float top, float height, float clipTop, float clipBottom) {
        return top + height > clipTop && top < clipBottom;
    }

    private void addHit(Kind kind, Panel panel, Object target, float x1, float y1, float x2, float y2, float clipTop, float clipBottom) {
        float top = Math.max(y1, clipTop);
        float bottom = Math.min(y2, clipBottom);
        if (bottom > top) {
            hits.add(new Hit(kind, panel, target, x1, top, x2, bottom));
        }
    }

    private float drawModule(Panel panel, Module module, float x, float rowY, float clipTop, float clipBottom, float mx, float my, int accent) {
        String id = module.getName();
        boolean open = expanded.contains(module);
        float opened = ease("open:" + id, open ? 1.0F : 0.0F, 16.0F);
        if (visible(rowY, MODULE_H, clipTop, clipBottom)) {
            boolean hovered = mx >= x && mx <= x + WIDTH && my >= Math.max(rowY, clipTop) && my <= Math.min(rowY + MODULE_H, clipBottom);
            float glow = ease("hv:" + id, hovered ? 1.0F : 0.0F, 14.0F);
            float on = ease("on:" + id, module.isEnabled() ? 1.0F : 0.0F, 12.0F);
            if (glow > 0.01F) {
                rrect(x + 4.0F, rowY + 1.0F, WIDTH - 8.0F, MODULE_H - 2.0F, 4.0F, a(Math.round(0x16 * glow) << 24 | 0xFFFFFF));
            }
            if (on > 0.01F) {
                // a short accent tick at the left of what is on
                float h = (MODULE_H - 8.0F) * on;
                rrect(x + 5.0F, rowY + MODULE_H / 2.0F - h / 2.0F, 2.0F, h, 1.0F, a(accent));
            }
            int colour = mix(TEXT, accent, on);
            text(rowFont(), trim(rowFont(), module.getName(), WIDTH - 32.0F), x + 11.0F + on * 1.5F, rowY, MODULE_H, colour);
            int sign = mix(FAINT, TEXT, Math.max(glow * 0.6F, opened));
            textRight(rowFont(), opened > 0.5F ? "-" : "+", x + WIDTH - 10.0F, rowY, MODULE_H, sign);
            addHit(Kind.MODULE, panel, module, x, rowY, x + WIDTH - 22.0F, rowY + MODULE_H, clipTop, clipBottom);
            addHit(Kind.EXPAND, panel, module, x + WIDTH - 22.0F, rowY, x + WIDTH, rowY + MODULE_H, clipTop, clipBottom);
        }
        rowY += MODULE_H;
        if (opened <= 0.01F) {
            return rowY;
        }

        List<Setting> settings = propertiesOf(module);
        float start = rowY;
        float full = settingsHeight(module, settings);
        float height = full * opened;
        // the settings unroll: drawn inside their own clip, inside the panel's
        float innerTop = Math.max(clipTop, start);
        float innerBottom = Math.min(clipBottom, start + height);
        if (innerBottom <= innerTop) {
            return start + height + 2.0F * opened;
        }
        pushClip(x, innerTop, WIDTH, innerBottom - innerTop);
        rrect(x + 4.0F, start, WIDTH - 8.0F, full, 4.0F, a(SETTINGS_BG));
        clipTop = innerTop;
        clipBottom = innerBottom;
        float left = x + 11.0F;
        float right = x + WIDTH - 10.0F;

        // the module's own key first
        if (module.canBeEnabled()) {
            if (visible(rowY, SETTING_H, clipTop, clipBottom)) {
                String key = listeningModule == module && listeningKey == null ? "..." : keyName(module.getKeycode());
                text(smallFont(), "Bind", left, rowY, SETTING_H, DIM);
                textRight(smallFont(), key, right, rowY, SETTING_H, listeningModule == module && listeningKey == null ? accent : TEXT);
                addHit(Kind.BIND, panel, module, x, rowY, x + WIDTH, rowY + SETTING_H, clipTop, clipBottom);
            }
            rowY += SETTING_H;
        }

        for (Setting setting : settings) {
            float h = rowHeight(setting);
            if (visible(rowY, h, clipTop, clipBottom)) {
                drawSetting(panel, module, setting, x, left, right, rowY, clipTop, clipBottom, accent);
            }
            rowY += h;
        }
        // back to the panel's own clip
        popClip();
        return start + height + 2.0F * opened;
    }

    private static boolean isNumeric(Setting s) {
        return s instanceof SliderSetting && !(((SliderSetting) s).isString && ((SliderSetting) s).getOptions() != null);
    }

    private static float rowHeight(Setting s) {
        return isNumeric(s) || s instanceof ColorSetting ? SLIDER_H : SETTING_H;
    }

    private static GroupSetting groupOf(Setting s) {
        if (s instanceof SliderSetting) {
            return ((SliderSetting) s).groupSetting;
        }
        if (s instanceof ButtonSetting) {
            return ((ButtonSetting) s).group;
        }
        if (s instanceof KeySetting) {
            return ((KeySetting) s).group;
        }
        if (s instanceof ColorSetting) {
            return ((ColorSetting) s).groupSetting;
        }
        if (s instanceof TextSetting) {
            return ((TextSetting) s).group;
        }
        return null;
    }

    private static List<Setting> propertiesOf(Module module) {
        List<Setting> out = new ArrayList<Setting>();
        for (Setting s : module.getSettings()) {
            if (!s.visible) {
                continue;
            }
            boolean supported = s instanceof SliderSetting || s instanceof ButtonSetting || s instanceof ColorSetting
                    || s instanceof TextSetting || s instanceof KeySetting || s instanceof GroupSetting;
            if (!supported) {
                continue;
            }
            GroupSetting g = groupOf(s);
            if (g != null && !g.isOpened()) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private float settingsHeight(Module module, List<Setting> settings) {
        float h = module.canBeEnabled() ? SETTING_H : 0.0F;
        for (Setting s : settings) {
            h += rowHeight(s);
        }
        return Math.max(h, SETTING_H);
    }

    private void drawSetting(Panel panel, Module module, Setting setting, float x, float left, float right, float rowY,
                             float clipTop, float clipBottom, int accent) {
        RavenFontRenderer font = smallFont();
        String label = setting.getName();
        boolean child = groupOf(setting) != null;
        float l = child ? left + 5.0F : left;

        if (setting instanceof ButtonSetting) {
            ButtonSetting b = (ButtonSetting) setting;
            if (b.isMethodButton) {
                text(font, trim(font, label, right - l), l, rowY, SETTING_H, accent);
                addHit(Kind.BUTTON, panel, setting, x, rowY, x + WIDTH, rowY + SETTING_H, clipTop, clipBottom);
                return;
            }
            boolean on = b.isToggled();
            text(font, trim(font, label, right - l - 20.0F), l, rowY, SETTING_H, on ? TEXT : DIM);
            float t = ease("sw:" + module.getName() + "/" + label, on ? 1.0F : 0.0F, 14.0F);
            float sw = 14.0F;
            float sh = 7.0F;
            float bx = right - sw;
            float by = rowY + (SETTING_H - sh) / 2.0F;
            rrect(bx, by, sw, sh, sh / 2.0F, a(mix(0x30FFFFFF, accent, t)));
            rrect(bx + 1.0F + (sw - sh) * t, by + 1.0F, sh - 2.0F, sh - 2.0F, (sh - 2.0F) / 2.0F, a(0xFFFFFFFF));
            addHit(Kind.BOOL, panel, setting, x, rowY, x + WIDTH, rowY + SETTING_H, clipTop, clipBottom);
        } else if (isNumeric(setting)) {
            SliderSetting sl = (SliderSetting) setting;
            double value = sl.getInput();
            String vs = value == -1 ? "Off" : number(value) + sl.getSuffix();
            text(font, trim(font, label, right - l - width(font, vs) - 6.0F), l, rowY, 12.0F, DIM);
            textRight(font, vs, right, rowY, 12.0F, TEXT);
            double range = sl.getMax() - sl.getMin();
            float t = value == -1 || range <= 0 ? 0.0F : (float) ((value - sl.getMin()) / range);
            t = Math.max(0.0F, Math.min(1.0F, t));
            float trackY = rowY + 14.0F;
            rect(l, trackY, right, trackY + 2.0F, a(0x30FFFFFF));
            rect(l, trackY, l + (right - l) * t, trackY + 2.0F, a(accent));
            rrect(l + (right - l) * t - 2.0F, trackY - 1.0F, 4.0F, 4.0F, 2.0F, a(TEXT));
            Hit hit = new Hit(Kind.NUM, panel, setting, x, Math.max(rowY, clipTop), x + WIDTH, Math.min(rowY + SLIDER_H, clipBottom));
            hit.trackX = l;
            hit.trackW = right - l;
            if (hit.y2 > hit.y1) {
                hits.add(hit);
            }
        } else if (setting instanceof SliderSetting) {
            SliderSetting sl = (SliderSetting) setting;
            String[] o = sl.getOptions();
            int i = (int) Math.max(0, Math.min(o.length - 1, sl.getInput()));
            String value = o[i];
            text(font, trim(font, label, right - l - width(font, value) - 6.0F), l, rowY, SETTING_H, DIM);
            textRight(font, value, right, rowY, SETTING_H, accent);
            addHit(Kind.MODE, panel, setting, x, rowY, x + WIDTH, rowY + SETTING_H, clipTop, clipBottom);
        } else if (setting instanceof ColorSetting) {
            ColorSetting c = (ColorSetting) setting;
            int rgb = c.getRGB() & 0xFFFFFF;
            text(font, trim(font, label, right - l - 14.0F), l, rowY, 12.0F, DIM);
            rrect(right - 8.0F, rowY + 2.5F, 8.0F, 7.0F, 2.0F, a(0xFF000000 | rgb));
            // a hue bar: drag along it to pick the colour
            float trackY = rowY + 13.5F;
            float w = right - l;
            int steps = 24;
            for (int i = 0; i < steps; i++) {
                int col = Color.HSBtoRGB(i / (float) steps, 1.0F, 1.0F);
                rect(l + w * i / steps, trackY, l + w * (i + 1) / steps, trackY + 3.0F, a(col | 0xFF000000));
            }
            float[] hsb = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
            rect(l + w * hsb[0] - 0.5F, trackY - 1.0F, l + w * hsb[0] + 0.5F, trackY + 4.0F, a(TEXT));
            Hit hit = new Hit(Kind.COLOR, panel, setting, x, Math.max(rowY, clipTop), x + WIDTH, Math.min(rowY + SLIDER_H, clipBottom));
            hit.trackX = l;
            hit.trackW = w;
            if (hit.y2 > hit.y1) {
                hits.add(hit);
            }
        } else if (setting instanceof TextSetting) {
            TextSetting t = (TextSetting) setting;
            boolean editing = editingText == t;
            String value = editing ? buffer + (blink() ? "_" : "") : t.getText();
            float labelW = Math.min(width(font, label), (right - l) * 0.45F);
            text(font, trim(font, label, labelW), l, rowY, SETTING_H, DIM);
            textRight(font, trim(font, value, right - l - labelW - 6.0F), right, rowY, SETTING_H, editing ? accent : TEXT);
            addHit(Kind.TEXT, panel, setting, x, rowY, x + WIDTH, rowY + SETTING_H, clipTop, clipBottom);
        } else if (setting instanceof KeySetting) {
            KeySetting k = (KeySetting) setting;
            boolean listening = listeningKey == k;
            String value = listening ? "..." : keyName(k.getKey());
            text(font, trim(font, label, right - l - width(font, value) - 6.0F), l, rowY, SETTING_H, DIM);
            textRight(font, value, right, rowY, SETTING_H, listening ? accent : TEXT);
            addHit(Kind.KEY, panel, setting, x, rowY, x + WIDTH, rowY + SETTING_H, clipTop, clipBottom);
        } else if (setting instanceof GroupSetting) {
            GroupSetting g = (GroupSetting) setting;
            text(font, trim(font, label, right - left - 14.0F), left, rowY, SETTING_H, accent);
            textRight(font, g.isOpened() ? "-" : "+", right, rowY, SETTING_H, FAINT);
            addHit(Kind.GROUP, panel, setting, x, rowY, x + WIDTH, rowY + SETTING_H, clipTop, clipBottom);
        }
    }

    private void drawConfigs(Panel panel, float x, float rowY, float clipTop, float clipBottom, float mx, float my, int accent) {
        refreshConfigs();
        String current = Raven.currentProfile == null ? "default" : Raven.currentProfile.getName();
        for (String name : configs) {
            if (visible(rowY, MODULE_H, clipTop, clipBottom)) {
                boolean hovered = mx >= x && mx <= x + WIDTH && my >= Math.max(rowY, clipTop) && my <= Math.min(rowY + MODULE_H, clipBottom);
                boolean picked = name.equalsIgnoreCase(configName);
                if (hovered || picked) {
                    rect(x + 3.0F, rowY, x + WIDTH - 3.0F, rowY + MODULE_H, a(picked ? 0x22FFFFFF : 0x14FFFFFF));
                }
                int colour = name.equalsIgnoreCase(current) ? accent : TEXT;
                text(rowFont(), trim(rowFont(), name, WIDTH - 18.0F), x + 9.0F, rowY, MODULE_H, colour);
                addHit(Kind.PROFILE, panel, name, x, rowY, x + WIDTH, rowY + MODULE_H, clipTop, clipBottom);
            }
            rowY += MODULE_H;
        }
        rowY += 4.0F;

        RavenFontRenderer font = smallFont();
        text(font, "Name", x + 9.0F, rowY, 10.0F, FAINT);
        rowY += 10.0F;
        rrect(x + 6.0F, rowY, WIDTH - 12.0F, MODULE_H, 4.0F, a(editingName ? 0x30FFFFFF : 0x1CFFFFFF));
        String shown = editingName ? buffer + (blink() ? "_" : "") : configName;
        text(rowFont(), trim(rowFont(), shown.isEmpty() && !editingName ? "..." : shown, WIDTH - 24.0F), x + 10.0F, rowY, MODULE_H,
                shown.isEmpty() ? FAINT : TEXT);
        addHit(Kind.NAME, panel, null, x, rowY, x + WIDTH, rowY + MODULE_H, clipTop, clipBottom);
        rowY += MODULE_H + 4.0F;

        drawButton(panel, Kind.LOAD, "Load", x, rowY, clipTop, clipBottom, mx, my);
        rowY += MODULE_H + 2.0F;
        drawButton(panel, Kind.SAVE, "Save", x, rowY, clipTop, clipBottom, mx, my);
    }

    private void drawButton(Panel panel, Kind kind, String label, float x, float rowY, float clipTop, float clipBottom, float mx, float my) {
        boolean hovered = mx >= x + 6.0F && mx <= x + WIDTH - 6.0F && my >= rowY && my <= rowY + MODULE_H;
        rrect(x + 6.0F, rowY, WIDTH - 12.0F, MODULE_H, 4.0F, a(hovered ? 0x26FFFFFF : 0x12FFFFFF));
        text(rowFont(), label, x + 10.0F, rowY, MODULE_H, TEXT);
        addHit(kind, panel, null, x + 6.0F, rowY, x + WIDTH - 6.0F, rowY + MODULE_H, clipTop, clipBottom);
    }

    private boolean blink() {
        return System.currentTimeMillis() / 500L % 2L == 0L;
    }

    private float contentHeight(Panel panel) {
        if (panel.configs) {
            refreshConfigs();
            return configs.size() * MODULE_H + 4.0F + 10.0F + MODULE_H + 4.0F + MODULE_H * 2.0F + 2.0F + 2.0F;
        }
        float h = 2.0F;
        for (Module module : panel.modules) {
            h += MODULE_H;
            float opened = peek("open:" + module.getName(), expanded.contains(module) ? 1.0F : 0.0F);
            if (opened > 0.01F) {
                h += (settingsHeight(module, propertiesOf(module)) + 2.0F) * opened;
            }
        }
        return h;
    }

    private void refreshConfigs() {
        long now = System.currentTimeMillis();
        if (now - configsRead < 1000L) {
            return;
        }
        configsRead = now;
        List<String> out = new ArrayList<String>();
        ProfileManager pm = Raven.profileManager;
        if (pm != null) {
            for (Profile p : pm.profiles) {
                out.add(p.getName());
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        configs = out;
    }

    private static String number(double v) {
        if (Math.abs(v - Math.round(v)) < 0.0001) {
            return String.valueOf((long) Math.round(v));
        }
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String keyName(int code) {
        if (code == 0) {
            return "None";
        }
        if (code >= 1000) {
            return "Mouse" + (code - 1000 + 1);
        }
        String n = Keyboard.getKeyName(code);
        return n == null ? "?" : n;
    }

    // ------------------------------------------------------------------ input

    private void markUnsaved(Module m) {
        if (Raven.currentProfile != null && m != null && !m.ignoreOnSave) {
            Raven.currentProfile.getModule().saved = false;
        }
    }

    private void tell(String message) {
        status = message;
        statusAt = System.currentTimeMillis();
    }

    private void commitEdit() {
        if (editingText != null) {
            editingText.setText(buffer);
            editingText.submit();
            editingText = null;
        } else if (editingName) {
            configName = buffer;
            editingName = false;
        }
    }

    private void setFromSlider(Hit hit, float mouseX) {
        SliderSetting sl = (SliderSetting) hit.target;
        float t = Math.max(0.0F, Math.min(1.0F, (mouseX - hit.trackX) / Math.max(1.0F, hit.trackW)));
        sl.setValue(sl.getMin() + t * (sl.getMax() - sl.getMin()));
    }

    private void setHue(Hit hit, float mouseX) {
        ColorSetting c = (ColorSetting) hit.target;
        float t = Math.max(0.0F, Math.min(1.0F, (mouseX - hit.trackX) / Math.max(1.0F, hit.trackW)));
        float s = c.getSaturation();
        float b = c.getBrightness();
        c.setFromHSB(t * 360f, s < 0.05f ? 1f : s, b < 0.05f ? 1f : b);
    }

    private void setScrollFromMouse(Panel p, float my) {
        if (p.maxScroll <= 0f) {
            return;
        }
        float top = p.drawY + HEADER + 1.0F;
        float track = p.body - 4.0F;
        float bar = Math.max(14.0F, track * p.body / p.content);
        float t = (my - top - bar / 2.0F) / Math.max(1.0F, track - bar);
        p.scroll = Math.max(0f, Math.min(p.maxScroll, t * p.maxScroll));
        p.shownScroll = p.scroll;
    }

    private void bringToFront(Panel p) {
        panels.remove(p);
        panels.add(p);
    }

    private Module moduleOf(Setting s) {
        for (Module m : ModuleManager.modules) {
            if (m.getSettings().contains(s)) {
                return m;
            }
        }
        return null;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        // a click while waiting for a key: other mouse buttons become the bind, anything else cancels
        if (listeningModule != null || listeningKey != null) {
            if (button > 1) {
                if (listeningKey != null) {
                    listeningKey.setKey(button + 1000);
                } else {
                    listeningModule.setBind(button + 1000);
                }
                markUnsaved(listeningModule);
            }
            listeningModule = null;
            listeningKey = null;
            return;
        }
        Hit hit = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) {
                hit = h;
                break;
            }
        }
        boolean onText = hit != null && (hit.kind == Kind.TEXT || hit.kind == Kind.NAME);
        if (!onText) {
            commitEdit();
        }
        if (hit == null) {
            return;
        }
        bringToFront(hit.panel);

        switch (hit.kind) {
            case HEADER:
                if (button == 1) {
                    hit.panel.folded = !hit.panel.folded;
                    saveState();
                } else {
                    dragging = hit.panel;
                    dragX = mx - hit.panel.x;
                    dragY = my - hit.panel.drawY;
                    pressX = mx;
                    pressY = my;
                    dragMoved = false;
                }
                break;
            case MODULE: {
                Module m = (Module) hit.target;
                if (button == 0 && m.canBeEnabled()) {
                    m.toggle();
                    markUnsaved(m);
                } else if (button == 1) {
                    toggleExpanded(m);
                } else if (button == 2) {
                    listeningModule = m;
                }
                break;
            }
            case EXPAND:
                toggleExpanded((Module) hit.target);
                break;
            case BIND:
                listeningModule = (Module) hit.target;
                listeningKey = null;
                break;
            case BOOL: {
                ButtonSetting b = (ButtonSetting) hit.target;
                b.toggle();
                Module m = moduleOf(b);
                if (m != null) {
                    m.guiButtonToggled(b);
                    markUnsaved(m);
                }
                break;
            }
            case BUTTON: {
                ButtonSetting b = (ButtonSetting) hit.target;
                b.runMethod();
                markUnsaved(moduleOf(b));
                break;
            }
            case NUM:
                slider = hit;
                setFromSlider(hit, mx);
                break;
            case MODE: {
                SliderSetting sl = (SliderSetting) hit.target;
                int n = sl.getOptions().length;
                int cur = (int) sl.getInput();
                sl.setValue(button == 1 ? (cur - 1 + n) % n : (cur + 1) % n);
                markUnsaved(moduleOf(sl));
                break;
            }
            case COLOR:
                hue = hit;
                setHue(hit, mx);
                break;
            case TEXT: {
                TextSetting t = (TextSetting) hit.target;
                if (editingText != t) {
                    commitEdit();
                    editingText = t;
                    buffer = t.getText();
                }
                break;
            }
            case KEY:
                listeningKey = (KeySetting) hit.target;
                listeningModule = moduleOf(listeningKey);
                break;
            case GROUP: {
                GroupSetting g = (GroupSetting) hit.target;
                g.setOpened(!g.isOpened());
                break;
            }
            case PROFILE:
                configName = (String) hit.target;
                break;
            case NAME:
                if (!editingName) {
                    commitEdit();
                    editingName = true;
                    buffer = configName;
                }
                break;
            case LOAD: {
                ProfileManager pm = Raven.profileManager;
                String n = configName.trim();
                if (pm == null || n.isEmpty()) {
                    tell("Pick or type a config name first");
                } else if (pm.getProfile(n) == null) {
                    tell("No config called " + n);
                } else {
                    pm.loadProfile(n);
                    tell("Loaded " + n);
                }
                break;
            }
            case SAVE: {
                ProfileManager pm = Raven.profileManager;
                String n = configName.trim();
                if (pm == null) {
                    break;
                }
                if (n.isEmpty()) {
                    if (Raven.currentProfile != null) {
                        pm.saveProfile(Raven.currentProfile);
                        tell("Saved " + Raven.currentProfile.getName());
                    } else {
                        tell("Type a config name first");
                    }
                } else {
                    Profile existing = pm.getProfile(n);
                    if (existing == null) {
                        pm.createProfile(n, 0);
                    } else {
                        pm.saveProfile(existing);
                    }
                    tell("Saved " + n);
                }
                configsRead = 0L;
                break;
            }
            case SCROLL:
                scrolling = hit.panel;
                setScrollFromMouse(hit.panel, my);
                break;
            default:
                break;
        }
    }

    private void toggleExpanded(Module m) {
        if (!expanded.remove(m)) {
            expanded.add(m);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long timeSinceLastClick) {
        if (dragging != null && !dragMoved && (Math.abs(mx - pressX) > 3f || Math.abs(my - pressY) > 3f)) {
            dragMoved = true;
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragging != null) {
            if (!dragMoved) {
                dragging.folded = !dragging.folded; // a tap on the title folds or opens the panel
            }
            dragging = null;
            saveState();
        }
        if (slider != null) {
            markUnsaved(moduleOf((Setting) slider.target));
            slider = null;
        }
        if (hue != null) {
            markUnsaved(moduleOf((Setting) hue.target));
            hue = null;
        }
        scrolling = null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (p.over(mx, my)) {
                p.scroll = Math.max(0f, Math.min(p.maxScroll, p.scroll - Math.signum(wheel) * 18f));
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (listeningModule != null || listeningKey != null) {
            int value = (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) ? 0 : keyCode;
            if (listeningKey != null) {
                listeningKey.setKey(value);
            } else {
                listeningModule.setBind(value);
            }
            markUnsaved(listeningModule);
            listeningModule = null;
            listeningKey = null;
            return;
        }
        if (editingText != null || editingName) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitEdit();
            } else if (keyCode == Keyboard.KEY_ESCAPE) {
                editingText = null;
                editingName = false;
            } else if (keyCode == Keyboard.KEY_BACK) {
                if (buffer.length() > 0) {
                    buffer = buffer.substring(0, buffer.length() - 1);
                }
            } else if (ChatAllowedCharacters.isAllowedCharacter(typed)) {
                int max = editingText != null ? editingText.getMaxLength() : 32;
                if (buffer.length() < max) {
                    buffer += typed;
                }
            }
            return;
        }
        Module guiModule = Module.getModule(ravens4.module.impl.client.Gui.class);
        if (keyCode == Keyboard.KEY_ESCAPE || (guiModule != null && guiModule.getKeycode() != 0 && keyCode == guiModule.getKeycode())) {
            mc.displayGuiScreen(null);
        }
    }
}
