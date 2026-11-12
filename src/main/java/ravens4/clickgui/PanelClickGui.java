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
import ravens4.utility.font.RavenFontRenderer;
import ravens4.utility.profile.Profile;
import ravens4.utility.profile.ProfileManager;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "Panel" click GUI: one floating panel per module category plus a Configs panel, ported from the Atlas client.
 * Everything works with a single tap or click (no right or middle click needed), so it also works on a phone:
 * tap a header to fold it, drag a header to move it, tap a module to switch it on, tap its [+] to open its settings.
 */
public class PanelClickGui extends GuiScreen {
    private static final float PANEL_W = 112f;
    private static final float HEADER_H = 18f;
    private static final float MODULE_H = 16f;
    private static final float ROW_H = 14f;
    private static final float SLIDER_H = 21f;
    private static final float GAP = 8f;
    private static final float SCROLLBAR_W = 5f;

    private enum Kind {
        HEADER, MODULE, EXPAND, BIND, BOOL, BUTTON, SLIDER, MODE, COLOR, TEXT, KEY, GROUP, CONFIG_LOAD, CONFIG_NAME, CONFIG_SAVE, SCROLL
    }

    private static final class Panel {
        final String title;
        final Module.category category;
        final boolean configs;
        final List<Module> modules = new ArrayList<Module>();
        float x;
        float y;
        boolean collapsed = true;
        float scroll;
        float maxScroll;
        float bodyTop;
        float bodyH;
        float totalH;

        Panel(String title, Module.category category, boolean configs) {
            this.title = title;
            this.category = category;
            this.configs = configs;
        }
    }

    private static final class Row {
        final Kind kind;
        final Module module;
        final Setting setting;
        final String label;
        final float h;
        final boolean child;

        Row(Kind kind, Module module, Setting setting, String label, float h, boolean child) {
            this.kind = kind;
            this.module = module;
            this.setting = setting;
            this.label = label;
            this.h = h;
            this.child = child;
        }
    }

    // where something was drawn this frame, used to find out what a click landed on
    private static final class Hit {
        final Kind kind;
        final Panel panel;
        final Module module;
        final Setting setting;
        final String label;
        final float x;
        final float y;
        final float w;
        final float h;
        final float rx;
        final float ry;
        final float rw;
        final float rh;

        Hit(Kind kind, Panel panel, Module module, Setting setting, String label, float x, float y, float w, float h,
            float rx, float ry, float rw, float rh) {
            this.kind = kind;
            this.panel = panel;
            this.module = module;
            this.setting = setting;
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.rx = rx;
            this.ry = ry;
            this.rw = rw;
            this.rh = rh;
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static PanelClickGui instance;

    private final List<Panel> panels = new ArrayList<Panel>();
    private final List<Hit> hits = new ArrayList<Hit>();
    private final Set<Module> expanded = new HashSet<Module>();
    private final Map<Module, Float> toggleAnim = new HashMap<Module, Float>();
    private final List<int[]> clips = new ArrayList<int[]>();
    private final StringBuilder edit = new StringBuilder();

    private Panel dragPanel;
    private float dragOffX;
    private float dragOffY;
    private float pressX;
    private float pressY;
    private boolean dragMoved;
    private Hit dragHit;
    private Panel scrollPanel;
    private Module bindingModule;
    private KeySetting bindingKey;
    private TextSetting editingText;
    private boolean editingConfig;
    private String configName = "";
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

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (panels.isEmpty()) {
            buildPanels();
        }
        keepOnScreen();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        commitEdit();
        bindingModule = null;
        bindingKey = null;
        dragPanel = null;
        dragHit = null;
        scrollPanel = null;
        saveState();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void buildPanels() {
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
            Panel p = new Panel(n.substring(0, 1).toUpperCase() + n.substring(1), c, false);
            p.modules.addAll(list);
            panels.add(p);
        }
        panels.add(new Panel("Configs", null, true));

        // default layout: rows of folded panels, nothing overlaps until you open one
        int cols = Math.max(1, (int) ((width - 8f) / (PANEL_W + GAP)));
        for (int i = 0; i < panels.size(); i++) {
            Panel p = panels.get(i);
            p.x = 8f + (i % cols) * (PANEL_W + GAP);
            p.y = 24f + (i / cols) * (HEADER_H + 6f);
        }
        if (!stateLoaded) {
            stateLoaded = true;
            loadState();
        }
    }

    private void keepOnScreen() {
        for (Panel p : panels) {
            p.x = Math.max(2f, Math.min(p.x, width - PANEL_W - 2f));
            p.y = Math.max(2f, Math.min(p.y, height - HEADER_H - 2f));
        }
    }

    // ------------------------------------------------------------------ saved layout

    private static File stateFile() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "rvns4_panel_gui.txt");
    }

    private void saveState() {
        try (PrintWriter w = new PrintWriter(new FileWriter(stateFile()))) {
            for (Panel p : panels) {
                w.println(p.title + "|" + p.x + "|" + p.y + "|" + p.collapsed);
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
                        p.collapsed = Boolean.parseBoolean(parts[3]);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ drawing helpers

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

    private static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int ar = a >> 16 & 255;
        int ag = a >> 8 & 255;
        int ab = a & 255;
        int br = b >> 16 & 255;
        int bg = b >> 8 & 255;
        int bb = b & 255;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static void rrect(float x, float y, float w, float h, float radius, int color) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        float r = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >> 24 & 255) / 255f);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + w / 2f, y + h / 2f);
        arc(x + r, y + r, r, 180, 270);
        arc(x + w - r, y + r, r, 270, 360);
        arc(x + w - r, y + h - r, r, 0, 90);
        arc(x + r, y + h - r, r, 90, 180);
        GL11.glVertex2f(x, y + r);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void arc(float cx, float cy, float r, int from, int to) {
        int steps = r < 0.5f ? 1 : 6;
        for (int i = 0; i <= steps; i++) {
            double ang = Math.toRadians(from + (to - from) * (double) i / steps);
            GL11.glVertex2f((float) (cx + Math.cos(ang) * r), (float) (cy + Math.sin(ang) * r));
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

    private RavenFontRenderer rowFont() {
        try {
            return ravens4.module.impl.client.Gui.getClickGuiSettingFontRenderer();
        } catch (Throwable t) {
            return null;
        }
    }

    private RavenFontRenderer titleFont() {
        try {
            return ravens4.module.impl.client.Gui.getClickGuiHeaderFontRenderer();
        } catch (Throwable t) {
            return null;
        }
    }

    private float fh(RavenFontRenderer f) {
        return f == null ? mc.fontRendererObj.FONT_HEIGHT : f.getFontHeight();
    }

    private float sw(RavenFontRenderer f, String s) {
        return f == null ? mc.fontRendererObj.getStringWidth(s) : f.getStringWidth(s);
    }

    private void text(RavenFontRenderer f, String s, float x, float y, int color) {
        if (f == null) {
            mc.fontRendererObj.drawString(s, (int) x, (int) y, color);
        } else {
            f.drawString(s, x, y, color);
        }
    }

    // text vertically centered in a row
    private void textIn(RavenFontRenderer f, String s, float x, float rowY, float rowH, int color) {
        text(f, s, x, rowY + (rowH - fh(f)) / 2f, color);
    }

    private String fit(RavenFontRenderer f, String s, float maxW) {
        if (sw(f, s) <= maxW) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && sw(f, t + "..") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "..";
    }

    // ------------------------------------------------------------------ rows

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

    private List<Row> buildRows(Panel p) {
        List<Row> rows = new ArrayList<Row>();
        if (p.configs) {
            ProfileManager pm = Raven.profileManager;
            if (pm != null) {
                for (Profile profile : pm.profiles) {
                    rows.add(new Row(Kind.CONFIG_LOAD, null, null, profile.getName(), MODULE_H, false));
                }
            }
            rows.add(new Row(Kind.CONFIG_NAME, null, null, "", ROW_H + 2f, false));
            rows.add(new Row(Kind.CONFIG_SAVE, null, null, "Save", ROW_H + 2f, false));
            return rows;
        }
        for (Module m : p.modules) {
            rows.add(new Row(Kind.MODULE, m, null, m.getName(), MODULE_H, false));
            if (!expanded.contains(m)) {
                continue;
            }
            for (Setting s : m.getSettings()) {
                if (!s.visible) {
                    continue;
                }
                GroupSetting g = groupOf(s);
                if (g != null && !g.isOpened()) {
                    continue;
                }
                boolean child = g != null;
                if (s instanceof SliderSetting) {
                    SliderSetting sl = (SliderSetting) s;
                    boolean mode = sl.isString && sl.getOptions() != null;
                    rows.add(new Row(mode ? Kind.MODE : Kind.SLIDER, m, s, s.getName(), mode ? ROW_H : SLIDER_H, child));
                } else if (s instanceof ButtonSetting) {
                    boolean method = ((ButtonSetting) s).isMethodButton;
                    rows.add(new Row(method ? Kind.BUTTON : Kind.BOOL, m, s, s.getName(), ROW_H, child));
                } else if (s instanceof ColorSetting) {
                    rows.add(new Row(Kind.COLOR, m, s, s.getName(), SLIDER_H, child));
                } else if (s instanceof TextSetting) {
                    rows.add(new Row(Kind.TEXT, m, s, s.getName(), ROW_H, child));
                } else if (s instanceof KeySetting) {
                    rows.add(new Row(Kind.KEY, m, s, s.getName(), ROW_H, child));
                } else if (s instanceof GroupSetting) {
                    rows.add(new Row(Kind.GROUP, m, s, s.getName(), ROW_H, false));
                }
            }
            if (m.canBeEnabled()) {
                rows.add(new Row(Kind.BIND, m, null, "Bind", ROW_H, false));
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ screen

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
        drawGradientRect(0, 0, width, height, 0x66000000, 0xB0000000);
        hits.clear();
        clips.clear();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        drawInfoBar();
        for (Panel p : panels) {
            drawPanel(p, mouseX, mouseY);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawInfoBar() {
        RavenFontRenderer f = rowFont();
        int on = 0;
        for (Module m : ModuleManager.modules) {
            if (m.isEnabled() && !m.isHidden()) {
                on++;
            }
        }
        String s = "rvns4   " + on + " on   " + Minecraft.getDebugFPS() + " fps";
        float w = sw(f, s) + 14f;
        rrect(6f, 4f, w, 14f, 5f, 0xCC101116);
        RenderUtils.drawRect(10f, 16.5f, 6f + w - 4f, 17.2f, alpha(accent(), 0xA0));
        textIn(f, s, 13f, 4f, 13f, 0xFFE4E6EE);
    }

    private void drawPanel(Panel p, int mx, int my) {
        RavenFontRenderer f = rowFont();
        RavenFontRenderer tf = titleFont();
        float x = p.x;
        float y = p.y;
        float w = PANEL_W;
        int accent = accent();

        List<Row> rows = p.collapsed ? Collections.<Row>emptyList() : buildRows(p);
        float content = 0f;
        for (Row r : rows) {
            content += r.h;
        }
        float maxBody = Math.max(40f, height - (y + HEADER_H) - 6f);
        float bodyH = Math.min(content, maxBody);
        p.maxScroll = Math.max(0f, content - bodyH);
        p.scroll = Math.max(0f, Math.min(p.scroll, p.maxScroll));
        p.totalH = HEADER_H + (rows.isEmpty() ? 0f : bodyH + 3f);
        p.bodyTop = y + HEADER_H + 1f;
        p.bodyH = bodyH;

        rrect(x - 1f, y - 1f, w + 2f, p.totalH + 2f, 7f, 0x50000000);
        rrect(x, y, w, p.totalH, 6f, 0xF2101116);
        rrect(x, y, w, HEADER_H, 6f, 0xFF171922);
        RenderUtils.drawRect(x + 5f, y + HEADER_H - 1.5f, x + w - 5f, y + HEADER_H - 0.5f, alpha(accent, 0xA0));
        textIn(tf, p.title, x + 8f, y, HEADER_H, 0xFFFFFFFF);
        String mark = p.collapsed ? "+" : "-";
        textIn(tf, mark, x + w - 8f - sw(tf, mark), y, HEADER_H, 0xFFB4B6C2);
        hits.add(new Hit(Kind.HEADER, p, null, null, "", x, y, w, HEADER_H, x, y, w, HEADER_H));

        if (rows.isEmpty()) {
            return;
        }
        float bodyTop = p.bodyTop;
        float bodyBottom = bodyTop + bodyH;
        pushClip(x, bodyTop, w, bodyH);
        float ry = bodyTop - p.scroll;
        for (Row r : rows) {
            if (ry + r.h >= bodyTop && ry <= bodyBottom) {
                drawRow(p, r, x, ry, w, bodyTop, bodyBottom, mx, my, f, accent);
            }
            ry += r.h;
        }
        popClip();

        if (p.maxScroll > 0f) {
            float trackX = x + w - SCROLLBAR_W - 1f;
            float thumbH = Math.max(14f, bodyH * bodyH / content);
            float thumbY = bodyTop + (bodyH - thumbH) * (p.scroll / p.maxScroll);
            rrect(trackX, bodyTop, SCROLLBAR_W, bodyH, 2f, 0x33FFFFFF);
            rrect(trackX, thumbY, SCROLLBAR_W, thumbH, 2f, alpha(accent, 0xCC));
            hits.add(new Hit(Kind.SCROLL, p, null, null, "", trackX - 2f, bodyTop, SCROLLBAR_W + 4f, bodyH, trackX, bodyTop, SCROLLBAR_W, bodyH));
        }
    }

    private void addHit(Kind kind, Panel p, Module m, Setting s, String label, float x, float y, float w, float h, float bodyTop, float bodyBottom) {
        float top = Math.max(y, bodyTop);
        float bottom = Math.min(y + h, bodyBottom);
        if (bottom <= top) {
            return;
        }
        hits.add(new Hit(kind, p, m, s, label, x, top, w, bottom - top, x, y, w, h));
    }

    private boolean over(float mx, float my, float x, float y, float w, float h, float bodyTop, float bodyBottom) {
        return mx >= x && mx <= x + w && my >= Math.max(y, bodyTop) && my <= Math.min(y + h, bodyBottom);
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

    private void drawRow(Panel p, Row r, float x, float y, float w, float bodyTop, float bodyBottom, int mx, int my, RavenFontRenderer f, int accent) {
        float h = r.h;
        float pad = r.child ? 13f : 9f;
        float rightEdge = x + w - (p.maxScroll > 0f ? SCROLLBAR_W + 4f : 6f);
        boolean hover = over(mx, my, x, y, w, h, bodyTop, bodyBottom);
        int text = 0xFFD0D2DC;
        int dim = 0xFF8A8D9B;

        switch (r.kind) {
            case MODULE: {
                Module m = r.module;
                boolean on = m.isEnabled();
                Float a = toggleAnim.get(m);
                float cur = a == null ? (on ? 1f : 0f) : a;
                cur += ((on ? 1f : 0f) - cur) * 0.25f;
                toggleAnim.put(m, cur);
                if (cur > 0.03f) {
                    rrect(x + 3f, y + 1f, w - 6f, h - 2f, 4f, alpha(accent, (int) (0x66 * cur)));
                } else if (hover) {
                    rrect(x + 3f, y + 1f, w - 6f, h - 2f, 4f, 0x22FFFFFF);
                }
                textIn(f, fit(f, r.label, w - 32f), x + 8f, y, h, mix(0xFFB4B6C2, 0xFFFFFFFF, cur));
                String sign = expanded.contains(m) ? "-" : "+";
                textIn(f, sign, rightEdge - sw(f, sign) - 1f, y, h, dim);
                addHit(Kind.MODULE, p, m, null, "", x, y, w - 20f, h, bodyTop, bodyBottom);
                addHit(Kind.EXPAND, p, m, null, "", x + w - 22f, y, 22f, h, bodyTop, bodyBottom);
                break;
            }
            case BIND: {
                boolean listening = bindingModule == r.module;
                textIn(f, "Bind", x + pad, y, h, text);
                String v = listening ? "press a key..." : keyName(r.module.getKeycode());
                textIn(f, v, rightEdge - sw(f, v), y, h, listening ? accent : dim);
                addHit(Kind.BIND, p, r.module, null, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case BOOL: {
                ButtonSetting b = (ButtonSetting) r.setting;
                boolean on = b.isToggled();
                textIn(f, fit(f, r.label, w - pad - 34f), x + pad, y, h, on ? 0xFFFFFFFF : text);
                float tx = rightEdge - 15f;
                float ty = y + (h - 7f) / 2f;
                rrect(tx, ty, 15f, 7f, 3.5f, on ? alpha(accent, 0xFF) : 0xFF3A3D4A);
                rrect(on ? tx + 9f : tx + 1f, ty + 1f, 5f, 5f, 2.5f, 0xFFFFFFFF);
                addHit(Kind.BOOL, p, r.module, b, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case BUTTON: {
                rrect(x + 8f, y + 1f, w - 16f, h - 2f, 4f, hover ? 0xFF303444 : 0xFF262935);
                String l = fit(f, r.label, w - 24f);
                textIn(f, l, x + (w - sw(f, l)) / 2f, y, h, 0xFFFFFFFF);
                addHit(Kind.BUTTON, p, r.module, r.setting, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case SLIDER: {
                SliderSetting sl = (SliderSetting) r.setting;
                double value = sl.getInput();
                String vs = value == -1 ? "Off" : number(value) + sl.getSuffix();
                textIn(f, fit(f, r.label, w - pad - sw(f, vs) - 12f), x + pad, y, 12f, text);
                text(f, vs, rightEdge - sw(f, vs), y + (12f - fh(f)) / 2f, accent);
                float tx = x + pad;
                float tw = rightEdge - tx;
                float ty = y + h - 7f;
                double range = sl.getMax() - sl.getMin();
                float frac = value == -1 || range <= 0 ? 0f : (float) ((value - sl.getMin()) / range);
                frac = Math.max(0f, Math.min(1f, frac));
                RenderUtils.drawRect(tx, ty, tx + tw, ty + 2.5f, 0xFF2A2D39);
                RenderUtils.drawRect(tx, ty, tx + tw * frac, ty + 2.5f, alpha(accent, 0xFF));
                rrect(tx + tw * frac - 2f, ty - 2f, 4f, 6.5f, 2f, 0xFFFFFFFF);
                addHit(Kind.SLIDER, p, r.module, sl, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case MODE: {
                SliderSetting sl = (SliderSetting) r.setting;
                String[] o = sl.getOptions();
                int i = (int) Math.max(0, Math.min(o.length - 1, sl.getInput()));
                String v = o[i];
                textIn(f, fit(f, r.label, w - pad - sw(f, v) - 14f), x + pad, y, h, text);
                textIn(f, v, rightEdge - sw(f, v), y, h, accent);
                addHit(Kind.MODE, p, r.module, sl, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case COLOR: {
                ColorSetting c = (ColorSetting) r.setting;
                textIn(f, fit(f, r.label, w - pad - 24f), x + pad, y, 12f, text);
                rrect(rightEdge - 12f, y + 2f, 12f, 8f, 2.5f, 0xFF000000 | (c.getRGB() & 0xFFFFFF));
                float tx = x + pad;
                float tw = rightEdge - tx;
                float ty = y + h - 7f;
                int segs = 24;
                for (int i = 0; i < segs; i++) {
                    int col = Color.HSBtoRGB(i / (float) segs, 1f, 1f) | 0xFF000000;
                    RenderUtils.drawRect(tx + tw * i / segs, ty, tx + tw * (i + 1) / segs + 0.5f, ty + 3f, col);
                }
                float frac = Math.max(0f, Math.min(1f, c.getHue() / 360f));
                rrect(tx + tw * frac - 1.5f, ty - 2f, 3f, 7f, 1.5f, 0xFFFFFFFF);
                addHit(Kind.COLOR, p, r.module, c, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case TEXT: {
                TextSetting t = (TextSetting) r.setting;
                boolean editing = editingText == t;
                float boxW = Math.min(52f, w * 0.48f);
                float bx = rightEdge - boxW;
                rrect(bx, y + 1.5f, boxW, h - 3f, 3f, editing ? 0xFF2D3040 : 0xFF21242F);
                String shown = editing ? edit.toString() + ((System.currentTimeMillis() / 500L) % 2L == 0L ? "_" : "") : t.getText();
                if (shown.isEmpty() && !editing) {
                    shown = t.getPlaceholder() == null ? "" : t.getPlaceholder();
                }
                while (shown.length() > 1 && sw(f, shown) > boxW - 6f) {
                    shown = shown.substring(1);
                }
                textIn(f, fit(f, r.label, bx - x - pad - 3f), x + pad, y, h, text);
                textIn(f, shown, bx + 3f, y, h, editing ? 0xFFFFFFFF : dim);
                addHit(Kind.TEXT, p, r.module, t, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case KEY: {
                KeySetting k = (KeySetting) r.setting;
                boolean listening = bindingKey == k;
                textIn(f, fit(f, r.label, w - pad - 50f), x + pad, y, h, text);
                String v = listening ? "press a key..." : keyName(k.getKey());
                textIn(f, v, rightEdge - sw(f, v), y, h, listening ? accent : dim);
                addHit(Kind.KEY, p, r.module, k, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case GROUP: {
                GroupSetting g = (GroupSetting) r.setting;
                textIn(f, fit(f, r.label, w - 30f), x + 8f, y, h, accent);
                String sign = g.isOpened() ? "-" : "+";
                textIn(f, sign, rightEdge - sw(f, sign) - 1f, y, h, accent);
                RenderUtils.drawRect(x + 8f, y + h - 0.5f, rightEdge, y + h, 0x22FFFFFF);
                addHit(Kind.GROUP, p, r.module, g, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case CONFIG_LOAD: {
                boolean current = Raven.currentProfile != null && Raven.currentProfile.getName().equalsIgnoreCase(r.label);
                if (current) {
                    rrect(x + 3f, y + 1f, w - 6f, h - 2f, 4f, alpha(accent, 0x55));
                } else if (hover) {
                    rrect(x + 3f, y + 1f, w - 6f, h - 2f, 4f, 0x22FFFFFF);
                }
                textIn(f, fit(f, r.label, w - 20f), x + 8f, y, h, current ? 0xFFFFFFFF : 0xFFB4B6C2);
                addHit(Kind.CONFIG_LOAD, p, null, null, r.label, x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case CONFIG_NAME: {
                rrect(x + 6f, y + 1.5f, w - 12f, h - 3f, 3f, editingConfig ? 0xFF2D3040 : 0xFF21242F);
                String shown = editingConfig ? edit.toString() + ((System.currentTimeMillis() / 500L) % 2L == 0L ? "_" : "") : configName;
                boolean placeholder = shown.isEmpty();
                if (placeholder) {
                    shown = "config name";
                }
                while (shown.length() > 1 && sw(f, shown) > w - 22f) {
                    shown = shown.substring(1);
                }
                textIn(f, shown, x + 10f, y, h, placeholder ? 0xFF656879 : 0xFFFFFFFF);
                addHit(Kind.CONFIG_NAME, p, null, null, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            case CONFIG_SAVE: {
                rrect(x + 6f, y + 1.5f, w - 12f, h - 3f, 4f, hover ? alpha(accent, 0xAA) : alpha(accent, 0x70));
                String l = configName.trim().isEmpty() ? "Save current" : "Save as " + configName.trim();
                l = fit(f, l, w - 22f);
                textIn(f, l, x + (w - sw(f, l)) / 2f, y, h, 0xFFFFFFFF);
                addHit(Kind.CONFIG_SAVE, p, null, null, "", x, y, w, h, bodyTop, bodyBottom);
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ input

    private void markUnsaved(Module m) {
        if (Raven.currentProfile != null && m != null && !m.ignoreOnSave) {
            Raven.currentProfile.getModule().saved = false;
        }
    }

    private void commitEdit() {
        if (editingText != null) {
            editingText.setText(edit.toString());
            editingText.submit();
            editingText = null;
        } else if (editingConfig) {
            configName = edit.toString();
            editingConfig = false;
        }
    }

    private void setSliderFromMouse(SliderSetting sl, Hit h, float mx) {
        float pad = 9f;
        float tx = h.rx + pad;
        float tw = (h.rx + h.rw - 6f) - tx;
        float frac = Math.max(0f, Math.min(1f, (mx - tx) / Math.max(1f, tw)));
        sl.setValue(sl.getMin() + frac * (sl.getMax() - sl.getMin()));
    }

    private void setColorFromMouse(ColorSetting c, Hit h, float mx) {
        float tx = h.rx + 9f;
        float tw = (h.rx + h.rw - 6f) - tx;
        float frac = Math.max(0f, Math.min(1f, (mx - tx) / Math.max(1f, tw)));
        float s = c.getSaturation();
        float b = c.getBrightness();
        c.setFromHSB(frac * 360f, s < 0.05f ? 1f : s, b < 0.05f ? 1f : b);
    }

    private void setScrollFromMouse(Panel p, float my) {
        if (p.maxScroll <= 0f) {
            return;
        }
        float content = p.maxScroll + p.bodyH;
        float thumbH = Math.max(14f, p.bodyH * p.bodyH / content);
        float frac = (my - p.bodyTop - thumbH / 2f) / Math.max(1f, p.bodyH - thumbH);
        p.scroll = Math.max(0f, Math.min(p.maxScroll, frac * p.maxScroll));
    }

    private void bringToFront(Panel p) {
        panels.remove(p);
        panels.add(p);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        // a click while waiting for a key: mouse buttons other than left/right become the bind, anything else cancels
        if (bindingModule != null || bindingKey != null) {
            if (button > 1) {
                if (bindingKey != null) {
                    bindingKey.setKey(button + 1000);
                    markUnsaved(bindingModule);
                } else {
                    bindingModule.setBind(button + 1000);
                    markUnsaved(bindingModule);
                }
            }
            bindingModule = null;
            bindingKey = null;
            return;
        }
        boolean hitText = false;
        Hit hit = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) {
                hit = h;
                break;
            }
        }
        if (hit != null) {
            hitText = hit.kind == Kind.TEXT || hit.kind == Kind.CONFIG_NAME;
        }
        if (!hitText) {
            commitEdit();
        }
        if (hit == null) {
            return;
        }
        bringToFront(hit.panel);

        switch (hit.kind) {
            case HEADER:
                dragPanel = hit.panel;
                dragOffX = mx - hit.panel.x;
                dragOffY = my - hit.panel.y;
                pressX = mx;
                pressY = my;
                dragMoved = false;
                if (button == 1) {
                    hit.panel.collapsed = !hit.panel.collapsed;
                    dragPanel = null;
                }
                break;
            case MODULE:
                if (button == 0 && hit.module.canBeEnabled()) {
                    hit.module.toggle();
                    markUnsaved(hit.module);
                } else if (button == 1) {
                    toggleExpanded(hit.module);
                } else if (button == 2) {
                    bindingModule = hit.module;
                }
                break;
            case EXPAND:
                toggleExpanded(hit.module);
                break;
            case BIND:
                bindingModule = hit.module;
                break;
            case BOOL: {
                ButtonSetting b = (ButtonSetting) hit.setting;
                b.toggle();
                hit.module.guiButtonToggled(b);
                markUnsaved(hit.module);
                break;
            }
            case BUTTON: {
                ((ButtonSetting) hit.setting).runMethod();
                markUnsaved(hit.module);
                break;
            }
            case SLIDER: {
                SliderSetting sl = (SliderSetting) hit.setting;
                dragHit = hit;
                setSliderFromMouse(sl, hit, mx);
                break;
            }
            case MODE: {
                SliderSetting sl = (SliderSetting) hit.setting;
                int n = sl.getOptions().length;
                int cur = (int) sl.getInput();
                int next = button == 1 ? (cur - 1 + n) % n : (cur + 1) % n;
                sl.setValue(next);
                markUnsaved(hit.module);
                break;
            }
            case COLOR: {
                dragHit = hit;
                setColorFromMouse((ColorSetting) hit.setting, hit, mx);
                break;
            }
            case TEXT: {
                TextSetting t = (TextSetting) hit.setting;
                if (editingText != t) {
                    commitEdit();
                    editingText = t;
                    edit.setLength(0);
                    edit.append(t.getText());
                }
                break;
            }
            case KEY:
                bindingKey = (KeySetting) hit.setting;
                bindingModule = hit.module;
                break;
            case GROUP: {
                GroupSetting g = (GroupSetting) hit.setting;
                g.setOpened(!g.isOpened());
                break;
            }
            case CONFIG_LOAD: {
                ProfileManager pm = Raven.profileManager;
                if (pm != null) {
                    pm.loadProfile(hit.label);
                }
                break;
            }
            case CONFIG_NAME:
                if (!editingConfig) {
                    commitEdit();
                    editingConfig = true;
                    edit.setLength(0);
                    edit.append(configName);
                }
                break;
            case CONFIG_SAVE:
                saveConfig();
                break;
            case SCROLL:
                scrollPanel = hit.panel;
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

    private void saveConfig() {
        ProfileManager pm = Raven.profileManager;
        if (pm == null) {
            return;
        }
        String n = configName.trim();
        if (n.isEmpty()) {
            if (Raven.currentProfile != null) {
                pm.saveProfile(Raven.currentProfile);
            }
            return;
        }
        Profile existing = pm.getProfile(n);
        if (existing == null) {
            pm.createProfile(n, 0);
        } else {
            pm.saveProfile(existing);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long timeSinceLastClick) {
        if (dragPanel != null) {
            if (!dragMoved && (Math.abs(mx - pressX) > 3f || Math.abs(my - pressY) > 3f)) {
                dragMoved = true;
            }
            if (dragMoved) {
                dragPanel.x = Math.max(0f, Math.min(mx - dragOffX, width - PANEL_W));
                dragPanel.y = Math.max(0f, Math.min(my - dragOffY, height - HEADER_H));
            }
        } else if (dragHit != null) {
            if (dragHit.kind == Kind.SLIDER) {
                setSliderFromMouse((SliderSetting) dragHit.setting, dragHit, mx);
            } else if (dragHit.kind == Kind.COLOR) {
                setColorFromMouse((ColorSetting) dragHit.setting, dragHit, mx);
            }
        } else if (scrollPanel != null) {
            setScrollFromMouse(scrollPanel, my);
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragPanel != null) {
            if (!dragMoved) {
                dragPanel.collapsed = !dragPanel.collapsed; // a tap on the header folds or opens the panel
            }
            dragPanel = null;
            saveState();
        }
        if (dragHit != null) {
            markUnsaved(dragHit.module);
            dragHit = null;
        }
        scrollPanel = null;
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (mx >= p.x && mx <= p.x + PANEL_W && my >= p.y && my <= p.y + p.totalH) {
                p.scroll = Math.max(0f, Math.min(p.maxScroll, p.scroll - Math.signum(wheel) * 14f));
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws java.io.IOException {
        if (bindingModule != null || bindingKey != null) {
            int value = (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) ? 0 : keyCode;
            if (bindingKey != null) {
                bindingKey.setKey(value);
            } else {
                bindingModule.setBind(value);
            }
            markUnsaved(bindingModule);
            bindingModule = null;
            bindingKey = null;
            return;
        }
        if (editingText != null || editingConfig) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitEdit();
            } else if (keyCode == Keyboard.KEY_ESCAPE) {
                editingText = null;
                editingConfig = false;
            } else if (keyCode == Keyboard.KEY_BACK) {
                if (edit.length() > 0) {
                    edit.setLength(edit.length() - 1);
                }
            } else if (ChatAllowedCharacters.isAllowedCharacter(typed)) {
                int max = editingText != null ? editingText.getMaxLength() : 32;
                if (edit.length() < max) {
                    edit.append(typed);
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
