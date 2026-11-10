package ravens4.clickgui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import ravens4.script.ScriptDefaults;
import ravens4.script.model.*;
import ravens4.script.packet.clientbound.*;
import ravens4.script.packet.serverbound.*;

/**
 * "ClickGui2": the Solace menu that can replace the normal click GUI (Gui module, Mode = ClickGui2).
 *
 * This is the former "clickgui2" script. It still uses the script API helpers (render, gl, client, keybinds, util),
 * which it inherits from ScriptDefaults, and it keeps the script's callback names (onRenderTick, onMouse, onKeyPress,
 * onDisable). Its settings now live on the Gui module and its events are forwarded by {@link ClickGui2Host}.
 */
public class ClickGui2 extends ScriptDefaults {
    public static final ScriptDefaults.modules modules = new ScriptDefaults.modules("Gui");
    public static final String scriptName = "Gui";

private static final String lIll = "Open menu";
private static final String lII = "Accent color";
private static final String IllI = "Text color";
private static final String Illl = "Scale";
private static final String llII = "Menu font";
private static final String lllI = "Compact";
private static final String IlIl = "Width";
private static final String slinkyGuiTheme = "Theme";
private static final String slinkyGuiHeight = "Height";
private static final String llll = "Columns";
private static final String III = "Background color";
private static final String Ill = "Opacity";
private static final String llIl = "Background dim";
private static final String IlI = "Arraylist";
private static final String lIIl = "Notifications";
private static final String IIl = "Arraylist corner";
private static final String lIlI = "Notification corner";
private static final String slinkyArraylistMode = "Arraylist mode";
private static final String slinkyArraylistLowercase = "Arraylist lowercase";
private static final String slinkyArraylistBackgroundOpacity = "Arraylist opacity";
private static final String slinkyArraylistScale = "Arraylist scale";
private static final String slinkyArraylistSpacing = "Arraylist spacing";
private static final String slinkyArraylistRounding = "Arraylist rounding";
private static final String slinkyGuiOpenMode = "Open animation";
private java.util.HashMap slinkyToggleAnimations = new java.util.HashMap();
private java.util.HashMap slinkySliderAnimations = new java.util.HashMap();
private java.util.HashMap slinkyArraylistAnimations = new java.util.HashMap();
private java.util.HashMap slinkyHoverAnimations = new java.util.HashMap();
private float[] slinkyScrollTargets = new float[24];
private boolean slinkyMiddleDragging = false;
private static final String[] slinkySectionNames = new String[]{"Modules", "Client", "Profiles", "Scripts"};
private int slinkySection = 0;
private int slinkyLastModuleCategory = 0;
private float[] slinkySectionRects = new float[16];
private float slinkyPaperX = 0f;
private float slinkyPaperY = 0f;
private float slinkyPaperW = 0f;
private float slinkyPaperH = 0f;
private float slinkySidebarW = 0f;
private static final String slinkyMenuXName = "Menu X";
private static final String slinkyBlurName = "Blur";
// fixed values (no longer settings, so an old saved profile can't change them)
private static final boolean slinkyInvertScrollDrag = true;
private static final boolean slinkyInvertMoveDrag = false;
private static final float slinkyWatermarkSize = 2.5f;
private static final float slinkyWatermarkMoveX = 0f; // right minus left: 0 - 0 px
private static final float slinkyWatermarkMoveY = 8f; // down minus up: 8 - 0 px
private static final String slinkyMenuYName = "Menu Y";
private float slinkyMenuOffsetX = 0f;
private float slinkyMenuOffsetY = 0f;
private volatile boolean slinkyEditPosition = false;
private boolean slinkyEditDragging = false;
private float slinkyEditMouseX = 0f;
private float slinkyEditMouseY = 0f;
private float slinkyEditStartX = 0f;
private float slinkyEditStartY = 0f;
private float[] slinkyDoneRect = new float[4];
private float[] slinkyResetRect = new float[4];
private ravens4.module.setting.impl.SliderSetting slinkyMenuXSetting = null;
private ravens4.module.setting.impl.SliderSetting slinkyMenuYSetting = null;
private boolean slinkyInputLocked = false;
private float slinkySavedMouseX = 0f;
private float slinkySavedMouseY = 0f;
private boolean slinkySavedLeftDown = false;
private boolean slinkySavedLeftClick = false;
private boolean slinkySavedLeftRelease = false;
private boolean slinkySavedRightClick = false;
private boolean slinkyMiddleWasDown = false;
private float slinkyMiddleLastY = 0f;
private float[] slinkyScrollVelocities = new float[24];
private boolean[] slinkyScrollInitialized = new boolean[24];
private float[] slinkyScissorX = new float[8];
private float[] slinkyScissorY = new float[8];
private float[] slinkyScissorW = new float[8];
private float[] slinkyScissorH = new float[8];
private int slinkyScissorDepth = 0;
private boolean slinkyGuiClosing = false;
private long slinkyGuiAnimationStart = 0L;
private float slinkyGuiAnimationFrom = 0f;
private float slinkyGuiAnimationTo = 0f;
private float slinkyGuiAnimationProgress = 0f;
private boolean lllIlIll = false;
private int lIIIlIlI = 0;
private float[] lIlIllII = new float[24];
private java.util.HashSet lIlIlll = new java.util.HashSet();
private java.util.HashMap llIIII = new java.util.HashMap();
private float lllllIII = 0f;
private float lllllIIl = 0f;
private boolean lllllIlI = false;
private long IIllIlI = 0L;
private float lIIIllI = 0f;
private java.util.HashSet lIlIllI = new java.util.HashSet();
private float lIIIlIIl = 1f;
private int lIIlllll = 1;
private java.util.HashMap lIlll = new java.util.HashMap();
private String[] llllIIlI = new String[16];
private boolean[] llllIlII = new boolean[16];
private long[] llllIIll = new long[16];
private int llllIlIl = 0;
private java.util.HashMap llIlIlII = new java.util.HashMap();
private boolean lIllIlI = false;
private java.util.HashSet lIIlIll = new java.util.HashSet();
private java.util.HashMap IlIlllll = new java.util.HashMap();
private java.util.ArrayList IIIlIIl = new java.util.ArrayList();
private boolean lIlIlIIl = false;
private ravens4.module.Module IlIlI = null;
private ravens4.module.setting.impl.KeySetting IlIll = null;
private ravens4.module.setting.impl.TextSetting lIlllIl = null;
private String lIllllI = "";
private ravens4.module.setting.impl.SliderSetting lllIllI = null;
private boolean IllllllI = false;
private float IllllIlI = 0f;
private float IllllIIl = 0f;
private String IlllllIl = "";
private boolean lIIIIIIl = false;
private ravens4.module.setting.impl.ButtonSetting lllIIIII = null;
private ravens4.module.setting.impl.ColorSetting lllIlll = null;
private int llllIII = -1;
private boolean lIlIlIll = false;
private float lIlIlIlI = 0f;
private long IIllIIl = 0L;
private boolean llIlIIll = false;
private boolean llIlIIIl = false;
private boolean IIllIII = false;
private boolean llIllIII = false;
private boolean llIlIlll = false;
private boolean llIIIlIl = false;
private boolean llIlIIlI = false;
private float IIIIlII = 0f;
private float IIIIIll = 0f;
private int lIlIllIl = 1;
private int lIlIlllI = 1;
private int lIllIIII = 1;
private float lIllIIll = 1f;
private int IIll = 0xFFE7090A;
private boolean IIlllI = false;
private String lIIllll = "Sf-Regular";
private ravens4.utility.font.RavenFontRenderer lIlIlIl = null;
private ravens4.utility.font.RavenFontRenderer lIlIlII = null;
private ravens4.utility.font.RavenFontRenderer lIlIIll = null;
private float IlIlIIIl = 0f;
private float IlIlIIlI = 100f;
private float IIlIlI = 0f;
private float IIllII = 0f;
private float IlIIll = 40f;
private int IlIlIl = 2;
private float IlIlIIII = 0f;
private float llIll = -1f;
private int lllII = -1;
private float lllIlIlI = 0f;
private float IlIlII = 12f;
private float IlllIIIl = 0f;
private boolean IllIlI = false;
private int llIlll = 0xF2202020;
private int lllIII = 0xFF202020;
private int IllllIl = 255;
private int llIllI = 0xFFE3DAD3;
private int llIlIl = 0xFFA1A1A1;
private int llIlII = 0xFF6E6E72;
private static final int Il = 0xFF404040;
private static final int II = 0xFF4C4C4C;
private static final int lll = 0xFF2E2E2E;
private static final int llI = 0xFFFFFFFF;
private static final int ll = 0xFF333333;
private static final int lI = 0x1AFFFFFF;
/**
     * Registers the menu's settings on the Gui module (instead of on a script module) and returns them, so the Gui
     * module can show them only while the ClickGui2 mode is selected.
     */
    public java.util.List<ravens4.module.setting.Setting> register(ravens4.module.Module gui) {
        guiModule = gui;
        java.util.List<ravens4.module.setting.Setting> list = new java.util.ArrayList<ravens4.module.setting.Setting>();
        list.add(new ravens4.module.setting.impl.DescriptionSetting("Solace menu. Opens with the Gui key."));
        list.add(new ravens4.module.setting.impl.KeySetting(lIll, 0));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyGuiTheme, 0, ravens4.utility.ThemePalette.getThemeOptions()));
        list.add(new ravens4.module.setting.impl.ColorSetting(lII, 255, 115, 15));
        list.add(new ravens4.module.setting.impl.ColorSetting(III, 22, 22, 22));
        list.add(new ravens4.module.setting.impl.ColorSetting(IllI, 220, 220, 220));
        list.add(new ravens4.module.setting.impl.SliderSetting(Illl, "x", 0.75, 0.6, 1.6, 0.05));
        String[] fontOptions = ravens4.utility.font.FontManager.getHudFontOptions();
        int defaultFont = 0;
        for (int i = 0; i < fontOptions.length; i++) {
            if (fontOptions[i].equals("Sf-Regular")) {
                defaultFont = i;
            }
        }
        list.add(new ravens4.module.setting.impl.SliderSetting(llII, defaultFont, fontOptions));
        list.add(new ravens4.module.setting.impl.ButtonSetting(lllI, false));
        list.add(new ravens4.module.setting.impl.SliderSetting(IlIl, "%", 44.0, 30.0, 100.0, 1.0));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyGuiHeight, "%", 85.0, 40.0, 100.0, 1.0));
        list.add(new ravens4.module.setting.impl.SliderSetting(llll, "", 2.0, 1.0, 4.0, 1.0));
        list.add(new ravens4.module.setting.impl.SliderSetting(Ill, "%", 100.0, 40.0, 100.0, 1.0));
        list.add(new ravens4.module.setting.impl.SliderSetting(llIl, "", 100.0, 0.0, 220.0, 5.0));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyBlurName, "%", 50.0, 0.0, 100.0, 1.0));
        list.add(new ravens4.module.setting.impl.ButtonSetting("Edit position", new Runnable() {
            @Override
            public void run() {
                startEditPosition();
            }
        }));
        // the arraylist and the toggle notifications of the original script start off here: rvn already has its own
        // HUD arraylist
        list.add(new ravens4.module.setting.impl.ButtonSetting(IlI, false));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyArraylistMode, 1, new String[]{"Classic", "Solace"}));
        list.add(new ravens4.module.setting.impl.ButtonSetting(slinkyArraylistLowercase, true));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyArraylistBackgroundOpacity, "%", 88.0, 0.0, 100.0, 1.0));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyArraylistScale, "%", 100.0, 60.0, 160.0, 5.0));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyArraylistSpacing, " px", 0.0, -5.0, 10.0, 0.5));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyArraylistRounding, " px", 3.0, 0.0, 8.0, 0.5));
        list.add(new ravens4.module.setting.impl.SliderSetting(slinkyGuiOpenMode, 1, new String[]{"Side", "Top down"}));
        String[] corners = new String[]{"Top left", "Top right", "Bottom left", "Bottom right"};
        list.add(new ravens4.module.setting.impl.SliderSetting(IIl, 1, corners));
        list.add(new ravens4.module.setting.impl.ButtonSetting(lIIl, false));
        list.add(new ravens4.module.setting.impl.SliderSetting(lIlI, 3, corners));
        for (ravens4.module.setting.Setting setting : list) {
            gui.registerSetting(setting);
        }
        // where the menu was dragged to; saved with the profile but never shown in the menu
        slinkyMenuXSetting = new ravens4.module.setting.impl.SliderSetting(slinkyMenuXName, "", 0.0, -3000.0, 3000.0, 1.0);
        slinkyMenuYSetting = new ravens4.module.setting.impl.SliderSetting(slinkyMenuYName, "", 0.0, -3000.0, 3000.0, 1.0);
        gui.registerSetting(slinkyMenuXSetting);
        gui.registerSetting(slinkyMenuYSetting);
        slinkyMenuXSetting.visible = false;
        slinkyMenuYSetting.visible = false;
        return list;
    }

    /** Called by the "Edit position" button: lets you drag the menu around until you press Done (or ESC). */
    public void startEditPosition() {
        if (lllIlIll) {
            slinkyEditPosition = true;
        }
    }

    private boolean slinkyCategoryInSection(String name) {
        if (slinkySection == 0) {
            return name.equalsIgnoreCase("combat") || name.equalsIgnoreCase("movement") || name.equalsIgnoreCase("player")
                    || name.equalsIgnoreCase("render") || name.equalsIgnoreCase("other");
        }
        return name.equalsIgnoreCase(slinkySectionNames[slinkySection]);
    }

    private int slinkyFindCategoryIndex(String name) {
        java.util.ArrayList all = lIIlII();
        for (int i = 0; i < all.size(); i++) {
            if (((String) all.get(i)).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** Sidebar: 0 = Modules (Combat ... Other tabs), 1 = Client, 2 = Profiles, 3 = Scripts. */
    private void slinkySetSection(int section) {
        if (section == slinkySection) {
            return;
        }
        slinkySection = section;
        int target = section == 0 ? slinkyLastModuleCategory : slinkyFindCategoryIndex(slinkySectionNames[section]);
        if (target >= 0 && target != lIIIlIlI) {
            lIIlllll = target > lIIIlIlI ? 1 : -1;
            lIIIlIlI = target;
            lIIIlIIl = 0f;
            lIlIlIll = false;
        }
    }

    private boolean slinkyInsideAt(float[] rects, int offset, float x, float y) {
        return rects[offset + 2] > rects[offset] && x >= rects[offset] && x <= rects[offset + 2] && y >= rects[offset + 1] && y <= rects[offset + 3];
    }

    /** Clicks on the sidebar entries (Modules / Client / Profiles / Scripts). */
    private void slinkyHandleSidebarInput() {
        if (!lllIlIll || slinkyGuiClosing || slinkyEditPosition) {
            return;
        }
        if (llIllIII) {
            for (int i = 0; i < 4; i++) {
                if (slinkyInsideAt(slinkySectionRects, i * 4, IIIIlII, IIIIIll)) {
                    slinkySetSection(i);
                    // the click belongs to the sidebar, not to whatever is underneath
                    llIllIII = false;
                    break;
                }
            }
        }
    }

    /** The Modules / Client / Profiles / Scripts list on the left of the paper. */
    private void slinkyDrawSidebar(float top, float headerH) {
        for (int i = 0; i < 16; i++) {
            slinkySectionRects[i] = 0f;
        }
        float scale = lIllIIll;

        ravens4.utility.font.RavenFontRenderer itemFont = lIIlllI(1.15f);
        float itemH = 26f * scale;
        float itemGap = 4f * scale;
        float itemX0 = IlIlIIIl - slinkySidebarW;
        float itemX1 = IlIlIIIl - 6f * scale;
        float itemY = top + headerH + 16f * scale;
        float mouseDownY = IIIIIll; // already measured from the top of the screen, like the drawing
        for (int i = 0; i < 4; i++) {
            float y0 = itemY + i * (itemH + itemGap);
            float y1 = y0 + itemH;
            boolean selected = i == slinkySection;
            boolean hover = IIIIlII >= itemX0 && IIIIlII <= itemX1 && mouseDownY >= y0 && mouseDownY <= y1;
            if (selected) {
                render.roundedRect(itemX0, y0, itemX1, y1, 8f * scale, Il);
            }
            else if (hover) {
                render.roundedRect(itemX0, y0, itemX1, y1, 8f * scale, 0x14FFFFFF);
            }
            float textY = y0 + itemH / 2f - (float) itemFont.getFontHeight() / 2f;
            itemFont.drawString(slinkySectionNames[i], itemX0 + 12f * scale, textY, selected ? llIllI : llIlIl, false);
            slinkySectionRects[i * 4] = itemX0;
            slinkySectionRects[i * 4 + 1] = y0;
            slinkySectionRects[i * 4 + 2] = itemX1;
            slinkySectionRects[i * 4 + 3] = y1;
        }
    }

    /**
     * The "Solace" logo. By default it is centred over the sidebar and vertically centred on the row of category tabs;
     * its size and offset are fixed values (see the slinkyWatermark constants). It is drawn last so it stays on top.
     */
    private void slinkyDrawLogo(float top, float headerH) {
        float logoSize = slinkyWatermarkSize;
        ravens4.utility.font.RavenFontRenderer logoFont = lIIlllI(logoSize);
        String logo = "Solace";
        float logoW = IllIlIII(logoFont, logo);
        float logoH = (float) logoFont.getFontHeight();
        float sidebarLeft = IlIlIIIl - slinkySidebarW;
        float sidebarRight = IlIlIIIl - 6f * lIllIIll;
        float moveX = slinkyWatermarkMoveX * lIllIIll;
        float moveY = slinkyWatermarkMoveY * lIllIIll;
        float logoX = (sidebarLeft + sidebarRight) / 2f - logoW / 2f + moveX;
        float logoY = top + headerH / 2f - logoH / 2f + moveY;
        // never off the paper
        float minX = slinkyPaperX + 2f * lIllIIll;
        float maxX = Math.max(minX, slinkyPaperX + slinkyPaperW - logoW - 2f * lIllIIll);
        float minY = slinkyPaperY + 2f * lIllIIll;
        float maxY = Math.max(minY, slinkyPaperY + slinkyPaperH - logoH - 2f * lIllIIll);
        logoX = Math.max(minX, Math.min(maxX, logoX));
        logoY = Math.max(minY, Math.min(maxY, logoY));
        logoFont.drawString(logo, logoX, logoY, IIll, false);
    }

    private void slinkySaveOffsets() {
        if (slinkyMenuXSetting != null) {
            slinkyMenuXSetting.setValue(slinkyMenuOffsetX);
        }
        if (slinkyMenuYSetting != null) {
            slinkyMenuYSetting.setValue(slinkyMenuOffsetY);
        }
    }

    private boolean slinkyInside(float[] rect, float x, float y) {
        return rect[2] > rect[0] && x >= rect[0] && x <= rect[2] && y >= rect[1] && y <= rect[3];
    }

    /** Drag-to-move and the Reset / Done buttons of the position editor. */
    private void slinkyHandleEditInput() {
        if (!lllIlIll) {
            slinkyEditPosition = false;
        }
        if (!slinkyEditPosition || slinkyGuiClosing) {
            slinkyEditDragging = false;
            return;
        }
        float mouseDownY = IIIIIll; // already measured from the top of the screen, like the drawing
        if (llIllIII) {
            if (slinkyInside(slinkyDoneRect, IIIIlII, mouseDownY)) {
                slinkyEditPosition = false;
                slinkyEditDragging = false;
                slinkySaveOffsets();
                return;
            }
            if (slinkyInside(slinkyResetRect, IIIIlII, mouseDownY)) {
                slinkyMenuOffsetX = 0f;
                slinkyMenuOffsetY = 0f;
                slinkyEditDragging = false;
                slinkySaveOffsets();
                return;
            }
            slinkyEditDragging = true;
            slinkyEditMouseX = IIIIlII;
            slinkyEditMouseY = IIIIIll;
            slinkyEditStartX = slinkyMenuOffsetX;
            slinkyEditStartY = slinkyMenuOffsetY;
        }
        if (slinkyEditDragging) {
            if (IIllIII) {
                slinkyMenuOffsetX = slinkyEditStartX + (IIIIlII - slinkyEditMouseX);
                // both the mouse y and the menu offset grow downwards
                float slinkyMoveDeltaY = IIIIIll - slinkyEditMouseY;
                if (slinkyInvertMoveDrag) {
                    slinkyMoveDeltaY = -slinkyMoveDeltaY;
                }
                slinkyMenuOffsetY = slinkyEditStartY + slinkyMoveDeltaY;
            }
            else {
                slinkyEditDragging = false;
                slinkySaveOffsets();
            }
        }
    }

    /** While the position is being edited the menu itself is only drawn: the mouse is hidden from it. */
    private void slinkyLockInput() {
        slinkyInputLocked = slinkyEditPosition;
        if (!slinkyInputLocked) {
            return;
        }
        slinkySavedMouseX = IIIIlII;
        slinkySavedMouseY = IIIIIll;
        slinkySavedLeftDown = IIllIII;
        slinkySavedLeftClick = llIllIII;
        slinkySavedLeftRelease = llIIIlIl;
        slinkySavedRightClick = llIlIlll;
        IIIIlII = -10000f;
        IIIIIll = -10000f;
        IIllIII = false;
        llIllIII = false;
        llIIIlIl = false;
        llIlIlll = false;
    }

    private void slinkyUnlockInput() {
        if (!slinkyInputLocked) {
            return;
        }
        slinkyInputLocked = false;
        IIIIlII = slinkySavedMouseX;
        IIIIIll = slinkySavedMouseY;
        IIllIII = slinkySavedLeftDown;
        llIllIII = slinkySavedLeftClick;
        llIIIlIl = slinkySavedLeftRelease;
        llIlIlll = slinkySavedRightClick;
    }

    /** The rounded "paper" that holds the sidebar, the category tabs and all the module cards. */
    private void slinkyDrawPaper() {
        int base = modules.getColor(scriptName, III) & 0x00FFFFFF;
        int alpha = (int) ((float) IllllIl * 0.8f);
        render.roundedRect(slinkyPaperX, slinkyPaperY, slinkyPaperX + slinkyPaperW, slinkyPaperY + slinkyPaperH, 14f * lIllIIll, (alpha << 24) | (lIlIllll(base, 0.55f) & 0x00FFFFFF));
    }

    private void slinkyDrawEditOverlay() {
        slinkyDoneRect[0] = 0f;
        slinkyDoneRect[2] = 0f;
        slinkyResetRect[0] = 0f;
        slinkyResetRect[2] = 0f;
        if (!slinkyEditPosition) {
            return;
        }
        ravens4.utility.font.RavenFontRenderer fr = lIIlllI(1.1f);
        String hint = "Drag to move the menu";
        String reset = "Reset";
        String done = "Done";
        float padX = 9f * lIllIIll;
        float h = 22f * lIllIIll;
        float gap = 8f * lIllIIll;
        float hintW = IllIlIII(fr, hint);
        float resetW = IllIlIII(fr, reset) + 2f * padX;
        float doneW = IllIlIII(fr, done) + 2f * padX;
        float total = padX + hintW + gap + resetW + gap + doneW + padX;
        float x = slinkyPaperX + (slinkyPaperW - total) / 2f;
        float y = IIllII - h - 10f * lIllIIll;
        float textY = y + h / 2f - (float) fr.getFontHeight() / 2f;
        render.roundedRect(x, y, x + total, y + h, h / 2f, 0xF0101012);
        float cx = x + padX;
        fr.drawString(hint, cx, textY, llIllI, false);
        cx += hintW + gap;
        float by0 = y + 3f * lIllIIll;
        float by1 = y + h - 3f * lIllIIll;
        render.roundedRect(cx, by0, cx + resetW, by1, (by1 - by0) / 2f, 0x33FFFFFF);
        fr.drawString(reset, cx + padX, textY, llIllI, false);
        slinkyResetRect[0] = cx;
        slinkyResetRect[1] = by0;
        slinkyResetRect[2] = cx + resetW;
        slinkyResetRect[3] = by1;
        cx += resetW + gap;
        render.roundedRect(cx, by0, cx + doneW, by1, (by1 - by0) / 2f, IIll);
        fr.drawString(done, cx + padX, textY, 0xFF101010, false);
        slinkyDoneRect[0] = cx;
        slinkyDoneRect[1] = by0;
        slinkyDoneRect[2] = cx + doneW;
        slinkyDoneRect[3] = by1;
    }

    public boolean isMenuOpen() {
        return lllIlIll;
    }

    /** Opens the menu (used by the Gui module when its key is pressed in ClickGui2 mode). */
    public void openMenu() {
        if (lllIlIll) {
            return;
        }
        lllIlIIl();
        // the Gui key that opened the menu is still held: don't treat it as a press that closes the menu again
        llIlIIlI = isGuiBindDown();
        IIllIIl = client.time();
    }

    private ravens4.module.Module guiModule = null;

    private boolean isGuiBindDown() {
        if (guiModule == null) {
            return false;
        }
        int code = guiModule.getKeycode();
        return code > 0 && code < 1000 && keybinds.isKeyDown(code);
    }

public void onDisable() {
if (lllIlIll) {
IllIIl();
}
if (!lIIlIll.isEmpty()) {
llIIIIlI();
}
}
public boolean onMouse(int lllllI, boolean lIIlIlIl, int IlIIllll, int IlIIllII) {
return !lllIlIll;
}
public boolean onKeyPress(char IllIIIll, int IlIIlll) {
if (!lllIlIll) {
return true;
}
if (!keybinds.isKeyDown(IlIIlll)) {
return true;
}
if (IlIIlll == 54) {
return false;
}
if (slinkyEditPosition) {
// ESC leaves the position editor (instead of closing the whole menu)
if (IlIIlll == 1) {
slinkyEditPosition = false;
slinkyEditDragging = false;
slinkySaveOffsets();
return false;
}
return true;
}
if (lIlllIl != null) {
if (IlIIlll == 1) {
lIlllIl = null;
lIllllI = "";
}
else if (IlIIlll == 28 || IlIIlll == 156) {
lIlllIl.setText(lIllllI);
lIlllIl.submit();
lIlllIl = null;
lIllllI = "";
}
else if (IlIIlll == 14) {
if (lIllllI.length() > 0) {
lIllllI = lIllllI.substring(0, lIllllI.length() - 1);
}
}
else if (IllIIIll >= ' ' && IllIIIll != (char) 127 && lIllllI.length() < lIlllIl.getMaxLength()) {
lIllllI = lIllllI + IllIIIll;
}
return false;
}
if (IlIlI != null || IlIll != null) {
llIlI(IlIIlll == 1 ? 0 : IlIIlll);
return false;
}
if (IlIIlll == 1) {
IllIIl();
return false;
}
return true;
}
private void llIlI(int IlIlll) {
if (IlIll != null) {
IlIll.setKey(IlIlll);
}
else if (IlIlI != null) {
IlIlI.setBind(IlIlll);
}
IlIll = null;
IlIlI = null;
}
private void llIIIl() {
if (IlIlI == null && IlIll == null) {
return;
}
for (int lIlII = 2; lIlII <= 4; lIlII++) {
if (keybinds.isMouseDown(lIlII)) {
llIlI(1000 + lIlII);
return;
}
}
}
public void onRenderTick(float lllIIIIl) {
int[] llllIlI = client.getDisplaySize();
lIlIllIl = llllIlI[0];
lIlIlllI = llllIlI[1];
lIllIIII = llllIlI[2];
boolean IlIIlIl = modules.getKeyPressed(scriptName, lIll) || (lllIlIll && isGuiBindDown());
if (IlIIlIl && !llIlIIlI && client.time() - IIllIIl > 120L) {
IIllIIl = client.time();
if (lllIlIll) {
if (slinkyGuiClosing) {
slinkyStartGuiAnimation(1f, false);
}
else {
IllIIl();
}
}
else {
lllIlIIl();
}
}
llIlIIlI = IlIIlIl;
if (client.getScreen().equals("ClickGui")) {
client.closeScreen();
if (lllIlIll) {
render.showGui();
}
else {
lllIlIIl();
}
}
long llllIIII = client.time();
if (IIllIlI == 0L) {
IIllIlI = llllIIII;
}
lIIIllI = (float) (llllIIII - IIllIlI) / 1000f;
if (lIIIllI < 0f) {
lIIIllI = 0f;
}
if (lIIIllI > 0.1f) {
lIIIllI = 0.1f;
}
IIllIlI = llllIIII;
lIllIIll = lIlllII();
// Theme: the same theme list as the other theme sliders. "Default" keeps the Accent color setting, any other theme replaces it
int slinkyTheme = (int) Math.round(modules.getSlider(scriptName, slinkyGuiTheme));
if (slinkyTheme > 0) {
IIll = 0xFF000000 | (ravens4.utility.ThemePalette.getThemeColor(slinkyTheme, System.currentTimeMillis(), 0f) & 0x00FFFFFF);
}
else {
IIll = 0xFF000000 | (modules.getColor(scriptName, lII) & 0x00FFFFFF);
}
int IllIIlIl = modules.getColor(scriptName, IllI) & 0x00FFFFFF;
llIllI = 0xFF000000 | IllIIlIl;
llIlIl = 0xFF000000 | lIlIllll(IllIIlIl, 0.66f);
llIlII = 0xFF000000 | lIlIllll(IllIIlIl, 0.45f);
IllllIl = (int) (modules.getSlider(scriptName, Ill) / 100.0 * 255.0);
if (IllllIl < 0) {
IllllIl = 0;
}
if (IllllIl > 255) {
IllllIl = 255;
}
int IllIl = modules.getColor(scriptName, III) & 0x00FFFFFF;
llIlll = (IllllIl << 24) | IllIl;
lllIII = 0xFF000000 | IllIl;
String IlllIII = modules.getSliderString(scriptName, llII);
if (IlllIII != null && IlllIII.length() > 0) {
lIIllll = IlllIII;
}
lIlIlIl = lIIlllI(1.25f);
lIlIlII = lIIlllI(1.05f);
lIlIIll = lIIlllI(0.95f);
llIIIllI();
llllllI();
IIlIlII();
boolean lllIlIl = modules.getButton(scriptName, IlI);
boolean llIIlll = modules.getButton(scriptName, lIIl);
if (lllIlIl || llIIlll) {
gl.depth(false);
gl.depthMask(false);
if (lllIlIl) {
lllIlII();
}
if (llIIlll) {
llIlIII();
}
gl.depthMask(true);
gl.depth(true);
}
if (!lllIlIll) {
return;
}
if (!client.getScreen().equals("EmptyGuiScreen")) {
lllIlIll = false;
llIIIIll();
return;
}
llIllIll();
llIlIll();
}
private void lllIlIIl() {
lllIlIll = true;
slinkyGuiClosing = false;
IlIlIIIl = 0f;
IlIlIIII = 0f;
lllIlIlI = 0f;
llIIIIll();
llIlIIll = keybinds.isMouseDown(0);
llIlIIIl = keybinds.isMouseDown(1);
keybinds.getScroll();
slinkyGuiAnimationProgress = 0f;
slinkyGuiAnimationFrom = 0f;
slinkyGuiAnimationTo = 1f;
slinkyGuiAnimationStart = client.time();
render.showGui();
}
private void IllIIl() {
if (!lllIlIll || slinkyGuiClosing) {
return;
}
llIIIIll();
slinkyStartGuiAnimation(0f, true);
}
private void slinkyStartGuiAnimation(float target, boolean closing) {
slinkyGuiAnimationProgress = slinkyGetGuiAnimation();
slinkyGuiAnimationFrom = slinkyGuiAnimationProgress;
slinkyGuiAnimationTo = target;
slinkyGuiAnimationStart = client.time();
slinkyGuiClosing = closing;
}
private float slinkyGetGuiAnimation() {
if (slinkyGuiAnimationStart == 0L) {
return slinkyGuiAnimationTo;
}
long elapsed = client.time() - slinkyGuiAnimationStart;
float duration = slinkyGuiAnimationTo > slinkyGuiAnimationFrom ? 280f : 220f;
float t = (float) elapsed / duration;
if (t < 0f) {
t = 0f;
}
if (t > 1f) {
t = 1f;
}
float eased;
if (slinkyGuiAnimationTo > slinkyGuiAnimationFrom) {
float inv = 1f - t;
eased = 1f - inv * inv * inv;
}
else {
eased = t * t * (3f - 2f * t);
}
float value = slinkyGuiAnimationFrom + (slinkyGuiAnimationTo - slinkyGuiAnimationFrom) * eased;
if (t >= 1f) {
slinkyGuiAnimationProgress = slinkyGuiAnimationTo;
}
else {
slinkyGuiAnimationProgress = value;
}
return slinkyGuiAnimationProgress;
}
private void llIIIIll() {
IlIlI = null;
IlIll = null;
lIlllIl = null;
lIllllI = "";
lllIllI = null;
lllIlll = null;
llllIII = -1;
lIlIlIll = false;
IllIlI = false;
slinkyMiddleDragging = false;
slinkyEditPosition = false;
slinkyEditDragging = false;
}
private void llIllIll() {
int[] llIllIlI = keybinds.getMousePosition();
IIIIlII = (float) llIllIlI[0] / (float) lIllIIII;
IIIIIll = (float) lIlIlllI - ((float) llIllIlI[1] / (float) lIllIIII) - 1f;
IIllIII = keybinds.isMouseDown(0);
boolean lIlllllI = keybinds.isMouseDown(1);
llIllIII = IIllIII && !llIlIIll && !slinkyGuiClosing;
llIIIlIl = !IIllIII && llIlIIll;
llIlIlll = lIlllllI && !llIlIIIl && !slinkyGuiClosing;
llIlIIll = IIllIII;
llIlIIIl = lIlllllI;
// middle mouse drag-scroll (like the old click GUI): hold the middle button over the menu and drag the page up/down
boolean slinkyMiddleDown = keybinds.isMouseDown(2);
if (!lllIlIll || slinkyGuiClosing || !slinkyMiddleDown) {
slinkyMiddleDragging = false;
}
else if (!slinkyMiddleWasDown && IIIIlII >= IlIlIIIl && IIIIlII <= IlIlIIIl + IlIlIIlI) {
slinkyMiddleDragging = true;
slinkyMiddleLastY = IIIIIll;
}
slinkyMiddleWasDown = slinkyMiddleDown;
slinkyHandleEditInput();
slinkyHandleSidebarInput();
}
private void llIlIll() {
IIlllI = modules.getButton(scriptName, lllI);
if (!slinkyEditDragging) {
slinkyMenuOffsetX = (float) modules.getSlider(scriptName, slinkyMenuXName);
slinkyMenuOffsetY = (float) modules.getSlider(scriptName, slinkyMenuYName);
}
// background blur: the same blur (and strength formula) the normal click GUI draws behind its menu, faded in and out
// together with the menu
float slinkyBlurSetting = (float) modules.getSlider(scriptName, slinkyBlurName);
float slinkyBlurEase = slinkyGetGuiAnimation();
if (slinkyBlurSetting > 0f && slinkyBlurEase > 0.01f) {
ravens4.utility.shader.BlurUtils.prepareBlur();
ravens4.utility.shader.RoundedUtils.drawRound(0f, 0f, (float) lIlIllIl, (float) lIlIlllI, 0.0f, true, java.awt.Color.black);
ravens4.utility.shader.BlurUtils.blurEnd(2, 3f * ((slinkyBlurSetting + 35f) / 100f) * slinkyBlurEase);
}
int lllllIl = (int) modules.getSlider(scriptName, llIl);
if (lllllIl > 0) {
render.rect(0f, 0f, (float) lIlIllIl, (float) lIlIlllI, llIII(lllllIl, 0, 0, 0));
}
if (lllIllI == null || llIll < 0f) {
llIll = (float) modules.getSlider(scriptName, IlIl) / 100f;
}
IlIlIIlI = (float) lIlIllIl * llIll;
// the paper background goes slightly beyond the tabs and cards on every side
float slinkyPaperPad = 6f * lIllIIll;
// the paper is extended to the left by a sidebar (Solace logo + Modules / Client / Profiles / Scripts)
slinkySidebarW = 124f * lIllIIll;
if (IlIlIIlI > (float) lIlIllIl - 12f - 2f * slinkyPaperPad - slinkySidebarW) {
IlIlIIlI = (float) lIlIllIl - 12f - 2f * slinkyPaperPad - slinkySidebarW;
}
float lIIIIllI = ((float) lIlIllIl - IlIlIIlI - slinkySidebarW) / 2f + slinkySidebarW + slinkyMenuOffsetX;
float slinkyMinX = slinkyPaperPad + slinkySidebarW;
float slinkyMaxX = Math.max(slinkyMinX, (float) lIlIllIl - IlIlIIlI - slinkyPaperPad);
if (lIIIIllI < slinkyMinX) {
lIIIIllI = slinkyMinX;
}
if (lIIIIllI > slinkyMaxX) {
lIIIIllI = slinkyMaxX;
}
if (IlIlIIII <= 0f || slinkyEditDragging) {
IlIlIIII = lIIIIllI;
}
else {
IlIlIIII = llIIl(IlIlIIII, lIIIIllI, 14f);
}
int slinkyOpenMode = (int) Math.round(modules.getSlider(scriptName, slinkyGuiOpenMode));
float slinkyOpenEase = slinkyGetGuiAnimation();
lllIlIlI = slinkyOpenEase;
if (slinkyOpenMode > 0) {
IlIlIIIl = IlIlIIII;
}
else {
float lIIllllI = (1f - slinkyOpenEase) * ((float) lIlIllIl - IlIlIIII + 10f * lIllIIll);
IlIlIIIl = IlIlIIII + lIIllllI;
}
float lllllIll = 27f * lIllIIll;
float llllIlll = 9f * lIllIIll;
if (llllIlll > (float) lIlIlllI * 0.05f) {
llllIlll = (float) lIlIlllI * 0.05f;
}
llllIlll += slinkyPaperPad;
float slinkyFullHeightAvailable = ((float) lIlIlllI - 8f * lIllIIll) - llllIlll;
float slinkyMenuHeight = slinkyFullHeightAvailable * (float) modules.getSlider(scriptName, slinkyGuiHeight) / 100f;
float slinkyMinMenuHeight = lllllIll + 7f * lIllIIll + 20f;
if (slinkyMenuHeight < slinkyMinMenuHeight) {
slinkyMenuHeight = Math.min(slinkyMinMenuHeight, slinkyFullHeightAvailable);
}
// moved by Edit position; the paper always stays on screen
float slinkyBottomLimit = Math.max(slinkyPaperPad, (float) lIlIlllI - slinkyMenuHeight - slinkyPaperPad);
llllIlll += slinkyMenuOffsetY;
if (llllIlll < slinkyPaperPad) {
llllIlll = slinkyPaperPad;
}
if (llllIlll > slinkyBottomLimit) {
llllIlll = slinkyBottomLimit;
}
IIlIlI = llllIlll + lllllIll + 7f * lIllIIll;
IIllII = llllIlll + slinkyMenuHeight;
if (IIllII < IIlIlI + 20f) {
IIllII = IIlIlI + 20f;
}
slinkyPaperX = IlIlIIIl - slinkyPaperPad - slinkySidebarW;
slinkyPaperY = llllIlll - slinkyPaperPad;
slinkyPaperW = IlIlIIlI + 2f * slinkyPaperPad + slinkySidebarW;
slinkyPaperH = (IIllII - llllIlll) + 2f * slinkyPaperPad;
IlIlII = 12f * lIllIIll;
if (lllIllI == null || lllII < 0) {
lllII = (int) modules.getSlider(scriptName, llll);
if (lllII < 1) {
lllII = 1;
}
if (lllII > 4) {
lllII = 4;
}
}
IlIlIl = lllII;
IlIIll = (IlIlIIlI - IlIlII * (float) (IlIlIl - 1)) / (float) IlIlIl;
llIIIl();
boolean slinkyTopReveal = slinkyOpenMode > 0;
if (slinkyTopReveal) {
float slinkyFullHeight = (IIllII - llllIlll) + 2f * slinkyPaperPad;
float slinkyRevealHeight = slinkyFullHeight * slinkyOpenEase;
if (slinkyRevealHeight < 0f) {
slinkyRevealHeight = 0f;
}
if (slinkyRevealHeight > slinkyFullHeight) {
slinkyRevealHeight = slinkyFullHeight;
}
lIIII(slinkyPaperX, llllIlll - slinkyPaperPad, slinkyPaperW, slinkyRevealHeight);
}
slinkyDrawPaper();
slinkyLockInput();
slinkyDrawSidebar(llllIlll, lllllIll);
llIlIIl(llllIlll, lllllIll);
slinkyUnlockInput();
if (!lllIlIll) {
if (slinkyTopReveal) {
lIllIII();
}
return;
}
slinkyLockInput();
llIlllI();
slinkyUnlockInput();
slinkyDrawEditOverlay();
slinkyDrawLogo(llllIlll, lllllIll);
if (slinkyTopReveal) {
lIllIII();
}
llIllIII = false;
llIlIlll = false;
if (llIIIlIl) {
lllIllI = null;
lllIlll = null;
llllIII = -1;
lIlIlIll = false;
}
if (lllIIIII != null) {
ravens4.module.setting.impl.ButtonSetting lIllIllI = lllIIIII;
lllIIIII = null;
llIIIIlI();
lIl llllIIl = new lIl();
llllIIl.button = lIllIllI;
client.async(llllIIl);
}
if (slinkyGuiClosing && slinkyOpenEase <= 0.001f) {
slinkyGuiClosing = false;
lllIlIll = false;
slinkyGuiAnimationProgress = 0f;
llIIIIll();
if (client.getScreen().equals("EmptyGuiScreen")) {
client.closeScreen();
}
}
}
private void llIlIIl(float llllIlll, float lllllIll) {
java.util.ArrayList slinkyAllCategories = lIIlII();
java.util.ArrayList llllllII = new java.util.ArrayList();
java.util.ArrayList slinkyTabReal = new java.util.ArrayList();
for (int slinkyCat = 0; slinkyCat < slinkyAllCategories.size(); slinkyCat++) {
if (slinkyCategoryInSection((String) slinkyAllCategories.get(slinkyCat))) {
llllllII.add(slinkyAllCategories.get(slinkyCat));
slinkyTabReal.add(Integer.valueOf(slinkyCat));
}
}
int IIIIIlI = llllllII.size();
// the selected category always belongs to the section chosen in the sidebar
int slinkySelectedTab = -1;
for (int slinkyTab = 0; slinkyTab < IIIIIlI; slinkyTab++) {
if (((Integer) slinkyTabReal.get(slinkyTab)).intValue() == lIIIlIlI) {
slinkySelectedTab = slinkyTab;
}
}
if (slinkySelectedTab < 0 && IIIIIlI > 0) {
lIIIlIlI = ((Integer) slinkyTabReal.get(0)).intValue();
slinkySelectedTab = 0;
}
render.roundedRect(IlIlIIIl, llllIlll, IlIlIIIl + IlIlIIlI, llllIlll + lllllIll, lllllIll / 2f, llIlll);
if (IIIIIlI == 0) {
return;
}
float IllIIII = 4.5f * lIllIIll;
float IIIIll = llllIlll + lllllIll / 2f;
float llIllllI = lllllIll - 2f * IllIIII;
float llIlllII = llIllllI / 2f;
float llIlllIl = 10f * lIllIIll;
float lIIIIll = 4f * lIllIIll;
float lIlllll = IllIIII;
float lIlIl = IlIlIIlI - 2f * lIlllll;
float IIIIlIl = 1.25f;
ravens4.utility.font.RavenFontRenderer lIIlIIl = lIIlllI(IIIIlIl);
float IllIIIII = llIlllIl;
float IllIIIIl = lIIIIll;
for (int IlllllI = 0; IlllllI < 24; IlllllI++) {
float IlllIIlI = 0f;
for (int IllIlll = 0; IllIlll < IIIIIlI; IllIlll++) {
IlllIIlI += IllIlIII(lIIlIIl, llIlIllI((String) llllllII.get(IllIlll)));
}
IlllIIlI += 2f * IllIIIII * (float) IIIIIlI + IllIIIIl * (float) (IIIIIlI - 1);
if (IlllIIlI <= lIlIl || IIIIlIl <= 0.5f) {
break;
}
IIIIlIl *= (lIlIl / IlllIIlI);
if (IIIIlIl < 0.5f) {
IIIIlIl = 0.5f;
}
lIIlIIl = lIIlllI(IIIIlIl);
}
float[] llIIllII = new float[IIIIIlI];
float IlllIIII = 0f;
for (int IllIlll = 0; IllIlll < IIIIIlI; IllIlll++) {
llIIllII[IllIlll] = IllIlIII(lIIlIIl, llIlIllI((String) llllllII.get(IllIlll))) + 2f * IllIIIII;
IlllIIII += llIIllII[IllIlll];
}
IlllIIII += IllIIIIl * (float) (IIIIIlI - 1);
if (IlllIIII > lIlIl) {
float lIIIIlII = 0f;
for (int IllIlll = 0; IllIlll < IIIIIlI; IllIlll++) {
lIIIIlII += IllIlIII(lIIlIIl, llIlIllI((String) llllllII.get(IllIlll)));
}
float lIlIIIIl = lIlIl - lIIIIlII;
if (lIlIIIIl < 0f) {
lIlIIIIl = 0f;
}
float lIIIIlI = lIlIIIIl * 0.25f;
IllIIIIl = (IIIIIlI > 1) ? (lIIIIlI / (float) (IIIIIlI - 1)) : 0f;
IllIIIII = (lIlIIIIl - lIIIIlI) / (2f * (float) IIIIIlI);
if (IllIIIII < 0f) {
IllIIIII = 0f;
}
IlllIIII = lIIIIlII + 2f * IllIIIII * (float) IIIIIlI + IllIIIIl * (float) (IIIIIlI - 1);
for (int IllIlll = 0; IllIlll < IIIIIlI; IllIlll++) {
llIIllII[IllIlll] = IllIlIII(lIIlIIl, llIlIllI((String) llllllII.get(IllIlll))) + 2f * IllIIIII;
}
}
float IllIIll = IlIlIIIl + lIlllll;
float IllIIlI = IlIlIIIl + IlIlIIlI - lIlllll;
float IllIIIl = IllIIlI - IllIIll;
float lIIlIIII = IlllIIII - IllIIIIl * (float) (IIIIIlI - 1);
float IIIllII = IllIIIIl;
if (IIIIIlI > 1) {
IllIIIIl = (IllIIIl - lIIlIIII) / (float) (IIIIIlI - 1);
if (IllIIIIl < IIIllII) {
IllIIIIl = IIIllII;
}
}
float lIlllIII = lIIlIIII + IllIIIIl * (float) (IIIIIlI - 1);
float lIIlIllI = IllIIll;
if (lIlllIII < IllIIIl) {
lIIlIllI = IllIIll + (IllIIIl - lIlllIII) / 2f;
}
float[] llIIlIll = new float[IIIIIlI];
float IIIlIl = lIIlIllI;
for (int IllIlll = 0; IllIlll < IIIIIlI; IllIlll++) {
llIIlIll[IllIlll] = IIIlIl;
IIIlIl += llIIllII[IllIlll] + IllIIIIl;
}
if (slinkySelectedTab >= 0 && slinkySelectedTab < IIIIIlI) {
float lIIIIllI = llIIlIll[slinkySelectedTab];
float lIIIIlll = llIIllII[slinkySelectedTab];
if (!lllllIlI) {
lllllIII = lIIIIllI;
lllllIIl = lIIIIlll;
lllllIlI = true;
}
lllllIII = llIIl(lllllIII, lIIIIllI, 5.0f);
lllllIIl = llIIl(lllllIIl, lIIIIlll, 5.0f);
render.roundedRect(lllllIII, llllIlll + IllIIII, lllllIII + lllllIIl, llllIlll + IllIIII + llIllllI, llIlllII, Il);
}
for (int IllIlll = 0; IllIlll < IIIIIlI; IllIlll++) {
String llIIIlll = (String) llllllII.get(IllIlll);
String IIllllI = llIlIllI(llIIIlll);
boolean IlIlIll = llIIIlll.equalsIgnoreCase("scripts");
boolean lIlIIlll = IllIlll == slinkySelectedTab;
boolean IlllIlI = IlIllll(llIIlIll[IllIlll], llllIlll + IllIIII, llIIllII[IllIlll], llIllllI);
if (IlllIlI && !lIlIIlll) {
render.roundedRect(llIIlIll[IllIlll], llllIlll + IllIIII, llIIlIll[IllIlll] + llIIllII[IllIlll], llllIlll + IllIIII + llIllllI, llIlllII, 0x14FFFFFF);
}
float IIllIll = IllIlIII(lIIlIIl, IIllllI);
int IIlllIl = IlIlIll ? IIll : (lIlIIlll ? llIllI : llIlIl);
IllIIllI(lIIlIIl, IIllllI, llIIlIll[IllIlll] + llIIllII[IllIlll] / 2f - IIllIll / 2f, IIIIll, IIlllIl);
if (IllIll(llIIlIll[IllIlll], llllIlll, llIIllII[IllIlll], lllllIll) && IllIlll != slinkySelectedTab) {
lIIlllll = IllIlll > slinkySelectedTab ? 1 : -1;
lIIIlIlI = ((Integer) slinkyTabReal.get(IllIlll)).intValue();
if (slinkySection == 0) {
slinkyLastModuleCategory = lIIIlIlI;
}
lIIIlIIl = 0f;
lIlIlIll = false;
}
}
}
private void llIlllI() {
IllllllI = false;
lIIIlIIl = llIIl(lIIIlIIl, 1f, 4.0f);
float lIIIlllI = llIIIIl(lIIIlIIl);
float lIIlllIl = (1f - lIIIlllI) * (float) lIIlllll * (IlIlIIlI * 0.07f + 18f * lIllIIll);
java.util.ArrayList IIIlIlI = lIIlIl(lIIIlIlI);
int IIIlll = IIIlIlI.size();
int slinkyScrollIndex = lIIIlIlI % lIlIllII.length;
float lIllIIIl = lIlIllII[slinkyScrollIndex];
if (!slinkyScrollInitialized[slinkyScrollIndex]) {
slinkyScrollTargets[slinkyScrollIndex] = lIllIIIl;
slinkyScrollInitialized[slinkyScrollIndex] = true;
}
float slinkyScrollTarget = slinkyScrollTargets[slinkyScrollIndex];
float IIlIll = IIllII - IIlIlI;
float lIllIl = 6f * lIllIIll;
float[] lIlIIl = new float[IIIlll];
float[] lIlIII = new float[IIIlll];
float[] lIllII = new float[IIIlll];
int IIllll = IlIlIl;
float[] IlIIlI = new float[IIllll];
for (int IllIlll = 0; IllIlll < IIIlll; IllIlll++) {
ravens4.module.Module IIlIlIl = (ravens4.module.Module) IIIlIlI.get(IllIlll);
float lIIIII = lIlIll(IIlIlIl, lIIIlIlI);
int IlIllI = IllIlll % IIllll;
lIlIIl[IllIlll] = IlIlIIIl + (float) IlIllI * (IlIIll + IlIlII);
lIlIII[IllIlll] = IlIIlI[IlIllI];
lIllII[IllIlll] = lIIIII;
IlIIlI[IlIllI] += lIIIII + lIllIl;
}
IlllIIIl = 0f;
for (int lllIIl = 0; lllIIl < IIllll; lllIIl++) {
if (IlIIlI[lllIIl] > IlllIIIl) {
IlllIIIl = IlIIlI[lllIIl];
}
}
float IIlIIII = IlllIIIl - IIlIll;
if (IIlIIII < 0f) {
IIlIIII = 0f;
}
int IlIlIIll = keybinds.getScroll();
if (IlIlIIll != 0 && IIIIlII >= IlIlIIIl && IIIIlII <= IlIlIIIl + IlIlIIlI) {
float slinkyWheelStep = -(float) IlIlIIll / 6f;
float slinkyMaxWheelStep = 42f * lIllIIll;
if (slinkyWheelStep > slinkyMaxWheelStep) {
slinkyWheelStep = slinkyMaxWheelStep;
}
if (slinkyWheelStep < -slinkyMaxWheelStep) {
slinkyWheelStep = -slinkyMaxWheelStep;
}
slinkyScrollTarget += slinkyWheelStep;
}
if (slinkyMiddleDragging) {
// the mouse y grows downwards: dragging down raises the scroll offset, so the content moves up (and the other way round)
float slinkyScrollDelta = IIIIIll - slinkyMiddleLastY;
if (slinkyInvertScrollDrag) {
slinkyScrollDelta = -slinkyScrollDelta;
}
slinkyScrollTarget += slinkyScrollDelta;
slinkyMiddleLastY = IIIIIll;
}
if (slinkyScrollTarget > IIlIIII) {
slinkyScrollTarget = IIlIIII;
}
if (slinkyScrollTarget < 0f) {
slinkyScrollTarget = 0f;
}
if (lIllIIIl > IIlIIII) {
lIllIIIl = IIlIIII;
slinkyScrollVelocities[slinkyScrollIndex] = 0f;
}
if (lIllIIIl < 0f) {
lIllIIIl = 0f;
slinkyScrollVelocities[slinkyScrollIndex] = 0f;
}
if (!(lIlIlIll && IIllIII)) {
lIllIIIl = slinkySmoothScroll(lIllIIIl, slinkyScrollTarget, slinkyScrollIndex, slinkyMiddleDragging);
}
if (IIlIIII > 0f) {
float lIIlI = IlIlIIIl + IlIlIIlI + 3f * lIllIIll;
float lIIll = 3f * lIllIIll;
float lIIIIIll = IIlIll * (IIlIll / IlllIIIl);
if (lIIIIIll < 16f * lIllIIll) {
lIIIIIll = 16f * lIllIIll;
}
float lIIIIIlI = IIlIlI + (IIlIll - lIIIIIll) * (lIllIIIl / IIlIIII);
render.roundedRect(lIIlI, IIlIlI, lIIlI + lIIll, IIllII, lIIll / 2f, 0x22FFFFFF);
render.roundedRect(lIIlI, lIIIIIlI, lIIlI + lIIll, lIIIIIlI + lIIIIIll, lIIll / 2f, lIlIlIll ? IIll : 0x77FFFFFF);
if (llIllIII && IlIllll(lIIlI - 4f * lIllIIll, IIlIlI, lIIll + 8f * lIllIIll, IIlIll)) {
llIllIII = false;
lIlIlIll = true;
lIlIlIlI = (IIIIIll >= lIIIIIlI && IIIIIll <= lIIIIIlI + lIIIIIll) ? (IIIIIll - lIIIIIlI) : (lIIIIIll / 2f);
}
if (lIlIlIll && IIllIII) {
float lIIllIlI = IIlIll - lIIIIIll;
slinkyScrollTarget = lIIllIlI <= 0f ? 0f : ((IIIIIll - lIlIlIlI - IIlIlI) / lIIllIlI) * IIlIIII;
if (slinkyScrollTarget < 0f) {
slinkyScrollTarget = 0f;
}
if (slinkyScrollTarget > IIlIIII) {
slinkyScrollTarget = IIlIIII;
}
lIllIIIl = slinkySmoothScroll(lIllIIIl, slinkyScrollTarget, slinkyScrollIndex, true);
}
}
else {
lIlIlIll = false;
slinkyScrollTarget = 0f;
lIllIIIl = 0f;
slinkyScrollVelocities[slinkyScrollIndex] = 0f;
}
slinkyScrollTargets[slinkyScrollIndex] = slinkyScrollTarget;
lIlIllII[slinkyScrollIndex] = lIllIIIl;
if (IIIlll == 0) {
IllIIllI(lIlIlII, "No modules on this page.", IlIlIIIl + 4f * lIllIIll, IIlIlI + 10f * lIllIIll, llIlII);
return;
}
lIIII(IlIlIIIl - 4f * lIllIIll, IIlIlI, IlIlIIlI + 12f * lIllIIll, IIllII - IIlIlI);
try {
for (int IllIlll = 0; IllIlll < IIIlll; IllIlll++) {
float IlIIllII = IIlIlI + lIlIII[IllIlll] - lIllIIIl;
if (IlIIllII > IIllII || IlIIllII + lIllII[IllIlll] < IIlIlI) {
continue;
}
ravens4.module.Module IllIII = (ravens4.module.Module) IIIlIlI.get(IllIlll);
try {
lllIIIl(IllIII, lIlIIl[IllIlll] + lIIlllIl, IlIIllII, IlIIll, lIIIlIlI);
}
catch (Throwable lIIIlIll) {
lIlllI(IllIII, lIIIlIll);
}
}
}
finally {
lIllIII();
}
if (IllllllI) {
float Illlllll = 5f * lIllIIll;
float lIIIIIII = 12f * lIllIIll;
float IlllllII = IllIlIII(lIlIIll, IlllllIl) + 2f * Illlllll;
float IllIIlII = IllllIIl;
if (IllIIlII < IIlIlI) {
IllIIlII = IIlIlI;
}
if (IllIIlII + lIIIIIII > IIllII) {
IllIIlII = IIllII - lIIIIIII;
}
lllIIlII(IllllIlI, IllIIlII, IllllIlI + IlllllII, IllIIlII + lIIIIIII, 3f * lIllIIll, false);
IllIIllI(lIlIIll, IlllllIl, IllllIlI + Illlllll, IllIIlII + lIIIIIII / 2f, lIIIIIIl ? llIlII : llIllI);
}
}
private float slinkySmoothScroll(float current, float target, int index, boolean dragging) {
if (index < 0 || index >= slinkyScrollVelocities.length) {
return target;
}
float dt = lIIIllI;
if (dt <= 0f) {
return current;
}
if (dt > 0.05f) {
dt = 0.05f;
}
float smoothTime = dragging ? 0.055f : 0.145f;
float omega = 2f / smoothTime;
float x = omega * dt;
float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);
float change = current - target;
float velocity = slinkyScrollVelocities[index];
float temp = (velocity + omega * change) * dt;
velocity = (velocity - omega * temp) * exp;
float output = target + (change + temp) * exp;
if ((target - current > 0f) == (output > target)) {
output = target;
velocity = 0f;
}
if (Math.abs(target - output) < 0.01f && Math.abs(velocity) < 0.05f) {
output = target;
velocity = 0f;
}
slinkyScrollVelocities[index] = velocity;
return output;
}
private float llIIl(float IIIlII, float lIIIlIII, float lIIllIIl) {
float lIIIlIll = 1f - (float) Math.pow(0.0025, lIIIllI * lIIllIIl);
if (lIIIlIll < 0f) {
lIIIlIll = 0f;
}
if (lIIIlIll > 1f) {
lIIIlIll = 1f;
}
float llllIllI = IIIlII + (lIIIlIII - IIIlII) * lIIIlIll;
if (Math.abs(lIIIlIII - llllIllI) < 0.001f) {
llllIllI = lIIIlIII;
}
return llllIllI;
}
private float llIIIIl(float IlIIllll) {
if (IlIIllll < 0f) {
IlIIllll = 0f;
}
if (IlIIllll > 1f) {
IlIIllll = 1f;
}
return IlIIllll * IlIIllll * (3f - 2f * IlIIllll);
}
private float lIllll(String IlIlIII, boolean lllIlIll) {
Object lllIllIl = llIIII.get(IlIlIII);
float IIIlIl = lllIllIl == null ? (lllIlIll ? 1f : 0f) : ((Float) lllIllIl).floatValue();
float lIIIlIII = lllIlIll ? 1f : 0f;
IIIlIl = llIIl(IIIlIl, lIIIlIII, 3.2f);
if (IIIlIl <= 0.0005f && !lllIlIll) {
llIIII.remove(IlIlIII);
return 0f;
}
llIIII.put(IlIlIII, new Float(IIIlIl));
return IIIlIl;
}
private float lIlIll(ravens4.module.Module IIIlIll, int lIIllI) {
float IllllII = IIlllI ? (26f * lIllIIll) : (22f * lIllIIll + 20f * lIllIIll);
String IlIlIII = lIlIlI(lIIllI, IIIlIll.getName());
boolean lllIlIll = lIlIlll.contains(IlIlIII);
Object lllIllIl = llIIII.get(IlIlIII);
float llIIllll = lllIllIl == null ? (lllIlIll ? 1f : 0f) : ((Float) lllIllIl).floatValue();
float llIIIII = llIIIIl(llIIllll);
if (llIIIII > 0.0005f) {
IllllII += (lIlIIllI(IIIlIll) + 6f * lIllIIll) * llIIIII;
}
return IllllII;
}
private void lllIIIl(ravens4.module.Module IIIlIll, float IlIIllll, float IlIIllII, float IlIlIlll, int lIIllI) {
String IlIlIII = lIlIlI(lIIllI, IIIlIll.getName());
boolean IlIllIl = lIlIlll.contains(IlIlIII);
float IllllII = lIlIll(IIIlIll, lIIllI);
boolean slinkyCardHovered = IlIllll(IlIIllll, IlIIllII, IlIlIlll, IllllII);
// Solace-style hover: the card surface barely changes. The visible feedback
// is concentrated on the title, description and expand control.
float slinkyCardHover = slinkyAnimatedValue(
slinkyHoverAnimations,
"card:" + lIIllI + ":" + IIIlIll.getName(),
slinkyCardHovered ? 1f : 0f,
slinkyCardHovered ? 13.5f : 9.0f);
float slinkyCardHoverEase = llIIIIl(slinkyCardHover);
render.roundedRect(IlIIllll, IlIIllII, IlIIllll + IlIlIlll, IlIIllII + IllllII, 11f * lIllIIll, llIlll);
if (slinkyCardHoverEase > 0.001f) {
render.roundedRect(IlIIllll, IlIIllII, IlIIllll + IlIlIlll, IlIIllII + IllllII,
11f * lIllIIll, slinkyApplyAlpha(0x08FFFFFF, slinkyCardHoverEase));
}
float llllllll = 22f * lIllIIll;
float lllIIIlI = 11f * lIllIIll;
float lIllllIl = IlIIllII + llllllll / 2f + 1.5f * lIllIIll;
boolean llIIll = IIIlIll.canBeEnabled();
float IlllIllI = 26f * lIllIIll;
float IlllIlll = 13f * lIllIIll;
float IlllIlIl = IlIIllll + IlIlIlll - lllIIIlI - IlllIllI;
float IlllIlII = lIllllIl - IlllIlll / 2f;
if (llIIll) {
boolean lllIllII = IIIlIll.isEnabled();
float slinkyToggle = slinkyAnimatedValue(slinkyToggleAnimations, "module:" + IIIlIll.getName(), lllIllII ? 1f : 0f, 11.5f);
int slinkyTrack = slinkyMixColor(Il, IIll, slinkyToggle);
render.roundedRect(IlllIlIl, IlllIlII, IlllIlIl + IlllIllI, IlllIlII + IlllIlll, IlllIlll / 2f, slinkyTrack);
float IlIIIll = IlllIlll - 3f * lIllIIll;
float slinkyTravel = IlllIllI - IlIIIll - 3f * lIllIIll;
float IlIIIIl = IlllIlIl + 1.5f * lIllIIll + slinkyTravel * slinkyToggle;
render.roundedRect(IlIIIIl, IlllIlII + 1.5f * lIllIIll, IlIIIIl + IlIIIll, IlllIlII + IlllIlll - 1.5f * lIllIIll, IlIIIll / 2f, lllIII);
if (IllIll(IlllIlIl - 3f * lIllIIll, IlllIlII - 4f * lIllIIll, IlllIllI + 6f * lIllIIll, IlllIlll + 8f * lIllIIll)) {
IlllIIll(IIIlIll);
}
}
float IIllIl = 11f * lIllIIll;
float lIlllIlI = llIIll ? IlllIlIl : (IlIIllll + IlIlIlll - lllIIIlI);
if (IIlllI && llIIll) {
lIlllIlI = IlllIlIl - IIllIl - 8f * lIllIIll;
}
float llllllIl = IlIIllll + lllIIIlI;
float IIIIIII = lIlllIlI - llllllIl - 6f * lIllIIll;
String lIlIIIll = IllIlIll(lIlIlIl, IIIlIll.getName(), IIIIIII * 0.72f);
int slinkyModuleNameColor = slinkyMixColor(llIllI, 0xFFFFFFFF, slinkyCardHoverEase);
IllIIllI(lIlIlIl, lIlIIIll, llllllIl, lIllllIl, slinkyModuleNameColor);
boolean IlIII = IlIlI == IIIlIll && IlIll == null;
String IlIIl = IlIII ? "..." : IlIIllI(IIIlIll.getKeycode());
float IlllIl = 4f * lIllIIll;
float llllII = llllllIl + IllIlIII(lIlIlIl, lIlIIIll) + 6f * lIllIIll;
float llllIl = IllIlIII(lIlIIll, IlIIl) + 2f * IlllIl;
float IllII = 12f * lIllIIll;
float lllIlI = lIllllIl - IllII / 2f;
if (llIIll && llllII + llllIl < lIlllIlI - 4f * lIllIIll) {
boolean IIlIl = IIIlIll.getKeycode() != 0;
render.roundedRect(llllII, lllIlI, llllII + llllIl, lllIlI + IllII, 4f * lIllIIll, ll);
IllIIllI(lIlIIll, IlIIl, llllII + IlllIl, lIllllIl, IlIII ? llIllI : (IIlIl ? IIll : llIlIl));
if (IllIll(llllII - 2f * lIllIIll, lllIlI - 2f * lIllIIll, llllIl + 4f * lIllIIll, IllII + 4f * lIllIIll)) {
if (IlIII) {
IIIlIll.setBind(0);
IlIlI = null;
}
else {
IlIlI = IIIlIll;
IlIll = null;
lIlllIl = null;
}
}
else if (IlIII && llIlIlll && IlIllll(llllII - 2f * lIllIIll, lllIlI - 2f * lIllIIll, llllIl + 4f * lIllIIll, IllII + 4f * lIllIIll)) {
llIlIlll = false;
IIIlIll.setBind(0);
IlIlI = null;
}
}
float IIIll = IIlllI ? IIllIl : (13f * lIllIIll);
float IIIIl;
float IIIII;
if (IIlllI) {
IIIIl = IlllIlIl - IIIll - 8f * lIllIIll;
IIIII = lIllllIl - IIIll / 2f;
}
else {
IIIIl = IlIIllll + lllIIIlI;
IIIII = IlIIllII + llllllll + 1f * lIllIIll;
}
boolean IIlII = IlIllll(IIIIl, IIIII, IIIll, IIIll);
float slinkyExpandHover = Math.max(slinkyCardHoverEase, IIlII ? 1f : 0f);
int slinkyExpandBackground = slinkyMixColor(Il, II, slinkyExpandHover * 0.88f);
render.roundedRect(IIIIl, IIIII, IIIIl + IIIll, IIIII + IIIll, 3f * lIllIIll, slinkyExpandBackground);
String lIIIIIl = IlIllIl ? "-" : "+";
int slinkyExpandColor = slinkyMixColor(llIllI, 0xFFFFFFFF, slinkyExpandHover * 0.78f);
IllIIllI(lIlIlII, lIIIIIl, IIIIl + IIIll / 2f - IllIlIII(lIlIlII, lIIIIIl) / 2f, IIIII + IIIll / 2f, slinkyExpandColor);
if (!IIlllI) {
String IIIIII = lllllll(IIIlIll);
if (IIIIII.length() > 0) {
float llIIIlI = IIIIl + IIIll + 5f * lIllIIll;
int slinkyDescriptionColor = slinkyMixColor(llIlIl, llIllI, slinkyCardHoverEase);
IllIIllI(lIlIIll, IllIlIll(lIlIIll, IIIIII, IlIIllll + IlIlIlll - llIIIlI - lllIIIlI), llIIIlI, IIIII + IIIll / 2f, slinkyDescriptionColor);
}
}
if (IllIll(IIIIl - 3f * lIllIIll, IIIII - 3f * lIllIIll, IIIll + 6f * lIllIIll, IIIll + 6f * lIllIIll)) {
if (IlIllIl) {
lIlIlll.remove(IlIlIII);
if (!llIIII.containsKey(IlIlIII)) {
llIIII.put(IlIlIII, new Float(1f));
}
}
else {
lIlIlll.add(IlIlIII);
llIIII.put(IlIlIII, new Float(0f));
}
return;
}
float llIIllll = lIllll(IlIlIII, IlIllIl);
float llIIIII = llIIIIl(llIIllll);
if (llIIIII > 0.0005f) {
float lIIIllII = IIlllI ? (IlIIllII + llllllll + 4f * lIllIIll) : (IIIII + IIIll + 6f * lIllIIll);
float lIIIlIl = lIlIIllI(IIIlIll);
float lIlIIlII = lIIIlIl * llIIIII;
float lIllIIlI = lllIIIlI - 3f * lIllIIll;
render.rect(IlIIllll + lIllIIlI, lIIIllII - 3f * lIllIIll, IlIIllll + IlIlIlll - lIllIIlI, lIIIllII - 3f * lIllIIll + 1f, lI);
lIIII(IlIIllll, lIIIllII - 2f * lIllIIll, IlIlIlll, lIlIIlII + 2f * lIllIIll);
llIIllI(IIIlIll, IlIIllll + lIllIIlI, lIIIllII, IlIlIlll - 2f * lIllIIlI);
lIllIII();
}
}
private void IlllIIll(ravens4.module.Module IIIlIll) {
if (IIIlIll instanceof ravens4.utility.profile.ProfileModule) {
if (ravens4.Raven.profileManager == null) {
return;
}
String IIIIIIl = IIIlIll.getName();
ravens4.utility.profile.Profile llIlIIII = ravens4.Raven.profileManager.getProfile(IIIIIIl);
ravens4.Raven.profileManager.loadProfile(IIIIIIl);
if (llIlIIII != null) {
ravens4.Raven.currentProfile = llIlIIII;
}
llIIIlII();
((ravens4.utility.profile.ProfileModule) IIIlIll).saved = true;
client.print(util.color("&7Enabled profile: &b" + IIIIIIl));
return;
}
try {
IIIlIll.toggle();
}
catch (Throwable lIIIlIll) {
lIlllI(IIIlIll, lIIIlIll);
return;
}
IIlIIlI(IIIlIll);
}
private float lIlIIllI(ravens4.module.Module IIIlIll) {
float IllllII = 0f;
java.util.ArrayList IIlIllI = IIIlIll.getSettings();
for (int IllIlll = 0; IllIlll < IIlIllI.size(); IllIlll++) {
ravens4.module.setting.Setting lIIlIlll = (ravens4.module.setting.Setting) IIlIllI.get(IllIlll);
if (!IlIllIlI(lIIlIlll, IllIlll)) {
continue;
}
IllllII += lIlllIll(lIIlIlll);
}
if (lIlIIIlI(IIIlIll)) {
IllllII += 17f * lIllIIll;
}
return IllllII;
}
private boolean lIlIIIlI(ravens4.module.Module IIIlIll) {
if (!IIIlIll.canBeEnabled()) {
return false;
}
if (IIIlIll.getName().equals("HUD")) {
return false;
}
return !(IIIlIll instanceof ravens4.utility.profile.ProfileModule);
}
private float lIlllIll(ravens4.module.setting.Setting lIIlIlll) {
if (lIIlIlll instanceof ravens4.module.setting.impl.SliderSetting) {
ravens4.module.setting.impl.SliderSetting lIIllIII = (ravens4.module.setting.impl.SliderSetting) lIIlIlll;
return lIIllIII.isString ? 17f * lIllIIll : 18f * lIllIIll;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.ColorSetting) {
ravens4.module.setting.impl.ColorSetting IIIllI = (ravens4.module.setting.impl.ColorSetting) lIIlIlll;
float lIIIl = 17f * lIllIIll;
if (lIlIllI.contains(IlIIII(IIIllI))) {
lIIIl += (IIIllI.hasAlpha() ? 4f : 3f) * (12f * lIllIIll);
}
return lIIIl;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.DescriptionSetting) {
return 15f * lIllIIll;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.TextSetting) {
return 19f * lIllIIll;
}
return 17f * lIllIIll;
}
private boolean IlIllIlI(ravens4.module.setting.Setting lIIlIlll, int IllIlII) {
if (!lIIlIlll.visible) {
return false;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.DescriptionSetting && IllIlII == 0) {
return false;
}
ravens4.module.setting.impl.GroupSetting lIIIlII = lIIIIII(lIIlIlll);
if (lIIIlII != null && !lIIIlII.isOpened()) {
return false;
}
return true;
}
private void llIIllI(ravens4.module.Module IIIlIll, float IlIIllll, float IlIIllII, float IlIlIlll) {
java.util.ArrayList IIlIllI = IIIlIll.getSettings();
float IIIIll = IlIIllII;
for (int IllIlll = 0; IllIlll < IIlIllI.size(); IllIlll++) {
ravens4.module.setting.Setting lIIlIlll = (ravens4.module.setting.Setting) IIlIllI.get(IllIlll);
if (!IlIllIlI(lIIlIlll, IllIlll)) {
continue;
}
float llIIIIII = lIlllIll(lIIlIlll);
float IllIlIl = lIIIIII(lIIlIlll) != null ? 7f * lIllIIll : 0f;
float lIllIlII = IlIIllll + IllIlIl;
float lIllIlIl = IlIlIlll - IllIlIl;
if (lIIlIlll instanceof ravens4.module.setting.impl.GroupSetting) {
llIllIl((ravens4.module.setting.impl.GroupSetting) lIIlIlll, lIllIlII, IIIIll, lIllIlIl, llIIIIII);
}
else if (lIIlIlll instanceof ravens4.module.setting.impl.DescriptionSetting) {
ravens4.module.setting.impl.DescriptionSetting llIIIll = (ravens4.module.setting.impl.DescriptionSetting) lIIlIlll;
IllIIllI(lIlIIll, IllIlIll(lIlIIll, llIIIll.getDesc(), lIllIlIl), lIllIlII, IIIIll + llIIIIII / 2f, llIlII);
}
else if (lIIlIlll instanceof ravens4.module.setting.impl.ButtonSetting) {
lllIIlI(IIIlIll, (ravens4.module.setting.impl.ButtonSetting) lIIlIlll, lIllIlII, IIIIll, lIllIlIl, llIIIIII);
}
else if (lIIlIlll instanceof ravens4.module.setting.impl.SliderSetting) {
ravens4.module.setting.impl.SliderSetting lIIllIII = (ravens4.module.setting.impl.SliderSetting) lIIlIlll;
if (lIIllIII.isString) {
llIlIlI(IIIlIll, lIIllIII, lIllIlII, IIIIll, lIllIlIl, llIIIIII);
}
else {
llIIlIl(IIIlIll, lIIllIII, lIllIlII, IIIIll, lIllIlIl, llIIIIII);
}
}
else if (lIIlIlll instanceof ravens4.module.setting.impl.ColorSetting) {
llIllll((ravens4.module.setting.impl.ColorSetting) lIIlIlll, lIllIlII, IIIIll, lIllIlIl, llIIIIII);
}
else if (lIIlIlll instanceof ravens4.module.setting.impl.KeySetting) {
llIllII((ravens4.module.setting.impl.KeySetting) lIIlIlll, lIllIlII, IIIIll, lIllIlIl, llIIIIII);
}
else if (lIIlIlll instanceof ravens4.module.setting.impl.TextSetting) {
llIIlII((ravens4.module.setting.impl.TextSetting) lIIlIlll, lIllIlII, IIIIll, lIllIlIl, llIIIIII);
}
else {
IllIIllI(lIlIIll, IllIlIll(lIlIIll, lIIlIlll.getName(), lIllIlIl * 0.6f), lIllIlII, IIIIll + llIIIIII / 2f, llIlIl);
String IlllIll = "clickgui";
IllIIllI(lIlIIll, IlllIll, lIllIlII + lIllIlIl - IllIlIII(lIlIIll, IlllIll), IIIIll + llIIIIII / 2f, llIlII);
}
IIIIll += llIIIIII;
}
if (lIlIIIlI(IIIlIll)) {
lllIIll(IIIlIll, IlIIllll, IIIIll, IlIlIlll, 17f * lIllIIll);
}
}
private void lllIIll(ravens4.module.Module IIIlIll, float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
float IIIIll = IlIIllII + IllllII / 2f;
boolean lIlIIlII = !IlIllllI(IIIlIll);
float IlllIllI = 22f * lIllIIll;
float IlllIlll = 11f * lIllIIll;
float IllIIlll = IlIIllll + IlIlIlll - IlllIllI;
float IllIIlII = IIIIll - IlllIlll / 2f;
float slinkyToggle = slinkyAnimatedValue(slinkyToggleAnimations, "array-visible:" + IIIlIll.getName(), lIlIIlII ? 1f : 0f, 12.5f);
render.roundedRect(IllIIlll, IllIIlII, IllIIlll + IlllIllI, IllIIlII + IlllIlll, IlllIlll / 2f, slinkyMixColor(Il, IIll, slinkyToggle));
float IlIIIll = IlllIlll - 2.5f * lIllIIll;
float slinkyTravel = IlllIllI - IlIIIll - 2.5f * lIllIIll;
float IIlllll = IllIIlll + 1.25f * lIllIIll + slinkyTravel * slinkyToggle;
render.roundedRect(IIlllll, IllIIlII + 1.25f * lIllIIll, IIlllll + IlIIIll, IllIIlII + IlllIlll - 1.25f * lIllIIll, IlIIIll / 2f, lllIII);
IllIIllI(lIlIIll, IllIlIll(lIlIIll, "Show in arraylist", IlIlIlll - IlllIllI - 6f * lIllIIll), IlIIllll, IIIIll, lIlIIlII ? llIllI : llIlIl);
if (IllIll(IlIIllll, IlIIllII, IlIlIlll, IllllII)) {
boolean lllIllll = lIlIIlII;
IlIlllll.put(IIIlIll.getName(), new Boolean(lllIllll));
lIIlIll.remove(IIIlIll.getName());
IIIlIll.setHidden(lllIllll);
}
}
private void llIllIl(ravens4.module.setting.impl.GroupSetting Illllll, float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
String lIllI = Illllll.isOpened() ? "-" : "+";
float IIIIll = IlIIllII + IllllII / 2f;
IllIIllI(lIlIIll, lIllI, IlIIllll, IIIIll, IIll);
IllIIllI(lIlIIll, IllIlIll(lIlIIll, Illllll.getName(), IlIlIlll - 11f * lIllIIll), IlIIllll + 9f * lIllIIll, IIIIll, llIllI);
if (IllIll(IlIIllll, IlIIllII, IlIlIlll, IllllII)) {
Illllll.setOpened(!Illllll.isOpened());
}
}
private void lllIIlI(ravens4.module.Module IIIlIll, ravens4.module.setting.impl.ButtonSetting llllll, float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
float IIIIll = IlIIllII + IllllII / 2f;
if (llllll.isMethodButton) {
String IIllllI = "RUN";
float llIIllII = IllIlIII(lIlIIll, IIllllI) + 10f * lIllIIll;
float llIlllll = 12f * lIllIIll;
float llIIlIll = IlIIllll + IlIlIlll - llIIllII;
float llIIlIlI = IIIIll - llIlllll / 2f;
boolean IlllIlI = IlIllll(llIIlIll, llIIlIlI, llIIllII, llIlllll);
lllIIlII(llIIlIll, llIIlIlI, llIIlIll + llIIllII, llIIlIlI + llIlllll, llIlllll / 2f, IlllIlI);
IllIIllI(lIlIIll, IIllllI, llIIlIll + llIIllII / 2f - IllIlIII(lIlIIll, IIllllI) / 2f, IIIIll, IlllIlI ? IIll : llIllI);
IllIIllI(lIlIIll, IllIlIll(lIlIIll, llllll.getName(), IlIlIlll - llIIllII - 6f * lIllIIll), IlIIllll, IIIIll, llIllI);
if (IllIll(llIIlIll, llIIlIlI - 2f * lIllIIll, llIIllII, llIlllll + 4f * lIllIIll)) {
lllIIIII = llllll;
}
return;
}
float IlllIllI = 22f * lIllIIll;
float IlllIlll = 11f * lIllIIll;
float IllIIlll = IlIIllll + IlIlIlll - IlllIllI;
float IllIIlII = IIIIll - IlllIlll / 2f;
boolean lllIllII = llllll.isToggled();
String slinkyButtonKey = "button:" + IIIlIll.getName() + ":" + Integer.toHexString(llllll.hashCode());
float slinkyToggle = slinkyAnimatedValue(slinkyToggleAnimations, slinkyButtonKey, lllIllII ? 1f : 0f, 12.5f);
render.roundedRect(IllIIlll, IllIIlII, IllIIlll + IlllIllI, IllIIlII + IlllIlll, IlllIlll / 2f, slinkyMixColor(Il, IIll, slinkyToggle));
float IlIIIll = IlllIlll - 2.5f * lIllIIll;
float slinkyTravel = IlllIllI - IlIIIll - 2.5f * lIllIIll;
float IIlllll = IllIIlll + 1.25f * lIllIIll + slinkyTravel * slinkyToggle;
render.roundedRect(IIlllll, IllIIlII + 1.25f * lIllIIll, IIlllll + IlIIIll, IllIIlII + IlllIlll - 1.25f * lIllIIll, IlIIIll / 2f, lllIII);
IllIIllI(lIlIIll, IllIlIll(lIlIIll, llllll.getName(), IlIlIlll - IlllIllI - 6f * lIllIIll), IlIIllll, IIIIll, lllIllII ? llIllI : llIlIl);
if (IllIll(IlIIllll, IlIIllII, IlIlIlll, IllllII)) {
llllll.toggle();
IIIlIll.guiButtonToggled(llllll);
IIlIIlI(IIIlIll);
}
}
private void llIlIlI(ravens4.module.Module IIIlIll, ravens4.module.setting.impl.SliderSetting lIIllIII, float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
String[] lllIlIII = lIIllIII.getOptions();
if (lllIlIII == null || lllIlIII.length == 0) {
return;
}
int IllIllI = (int) Math.round(lIIllIII.getInput());
if (IllIllI < 0) {
IllIllI = 0;
}
if (IllIllI >= lllIlIII.length) {
IllIllI = lllIlIII.length - 1;
}
String IlIlllII = lllIlIII[IllIllI];
float IIIIll = IlIIllII + IllllII / 2f;
float IIlllII = IlIlIlll * 0.42f;
float llIIlIll = IlIIllll + IIlllII + 8f * lIllIIll;
float llIIllII = IlIIllll + IlIlIlll - llIIlIll;
float llIlllll = 13f * lIllIIll;
float llIIlIlI = IIIIll - llIlllll / 2f;
boolean IlllIlI = IlIllll(llIIlIll, llIIlIlI, llIIllII, llIlllll);
lllIIlII(llIIlIll, llIIlIlI, llIIlIll + llIIllII, llIIlIlI + llIlllll, llIlllll / 2f, IlllIlI);
String lIlIIlII = IllIlIll(lIlIIll, IlIlllII, llIIllII - 7f * lIllIIll);
IllIIllI(lIlIIll, lIlIIlII, llIIlIll + llIIllII / 2f - IllIlIII(lIlIIll, lIlIIlII) / 2f, IIIIll, llIllI);
IllIIllI(lIlIIll, IllIlIll(lIlIIll, lIIllIII.getName(), IIlllII), IlIIllll, IIIIll, llIlIl);
if (IllIll(llIIlIll, llIIlIlI - 1f * lIllIIll, llIIllII, llIlllll + 2f * lIllIIll)) {
lIIllIII.setValue((double) ((IllIllI + 1) % lllIlIII.length));
IIlIIlI(IIIlIll);
}
else if (llIlIlll && IlIllll(llIIlIll, llIIlIlI - 1f * lIllIIll, llIIllII, llIlllll + 2f * lIllIIll)) {
llIlIlll = false;
lIIllIII.setValue((double) ((IllIllI - 1 + lllIlIII.length) % lllIlIII.length));
IIlIIlI(IIIlIll);
}
}
private void llIIlIl(ravens4.module.Module IIIlIll, ravens4.module.setting.impl.SliderSetting lIIllIII, float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
double IlIlllII = lIIllIII.getInput();
boolean lllllII = lIIllIII.canBeDisabled && IlIlllII == -1.0D;
String IlIllIll;
if (lllllII) {
String IIIIlll = null; // rvn sliders have no custom "disabled" label
IlIllIll = IIIIlll == null ? "OFF" : IIIIlll;
}
else {
IlIllIll = lIlIIII(IlIlllII) + lIIllIII.getSuffix();
}
float IIIIll = IlIIllII + IllllII / 2f;
float IIlllII = IlIlIlll * 0.42f;
IllIIllI(lIlIIll, IllIlIll(lIlIIll, lIIllIII.getName(), IIlllII), IlIIllll, IIIIll, llIllI);
float IllIllIl = IlIIllll + IIlllII + 8f * lIllIIll;
float IllIlllI = IlIIllll + IlIlIlll - IllIllIl;
float IllIllII = IIIIll - 1.5f * lIllIIll;
float IllIllll = 3f * lIllIIll;
render.roundedRect(IllIllIl, IllIllII, IllIllIl + IllIlllI, IllIllII + IllIllll, IllIllll / 2f, lll);
double IIIllIl = lIIllIII.getMin();
double IIlIIIl = lIIllIII.getMax();
double lIIllIlI = IIlIIIl - IIIllIl;
if (lIIllIlI <= 0.0D) {
lIIllIlI = 1.0D;
}
float lIIlIII = lllllII ? 0f : (float) ((IlIlllII - IIIllIl) / lIIllIlI);
if (lIIlIII < 0f) {
lIIlIII = 0f;
}
if (lIIlIII > 1f) {
lIIlIII = 1f;
}
String slinkySliderKey = "slider:" + IIIlIll.getName() + ":" + Integer.toHexString(lIIllIII.hashCode());
boolean IlllIIl = IlIllll(IllIllIl - 2f * lIllIIll, IllIllII - 6f * lIllIIll, IllIlllI + 4f * lIllIIll, IllIllll + 12f * lIllIIll);
float slinkyDisplay = slinkyAnimatedValue(slinkySliderAnimations, slinkySliderKey, lIIlIII, lllIllI == lIIllIII ? 7.25f : 5.75f);
float slinkySliderHover = slinkyAnimatedValue(slinkyHoverAnimations, "slider-hover:" + slinkySliderKey, (lllIllI == lIIllIII || IlllIIl) ? 1f : 0f, 11f);
if (slinkyDisplay > 0f) {
render.roundedRect(IllIllIl, IllIllII, IllIllIl + IllIlllI * slinkyDisplay, IllIllII + IllIllll, IllIllll / 2f, IIll);
}
float IlIIIlI = (3.5f + 0.45f * slinkySliderHover) * lIllIIll;
float IIlllll = IllIllIl + IllIlllI * slinkyDisplay;
float slinkyOuter = IlIIIlI + (1.15f + 0.35f * slinkySliderHover) * lIllIIll;
render.roundedRect(IIlllll - slinkyOuter, IllIllII + IllIllll / 2f - slinkyOuter, IIlllll + slinkyOuter, IllIllII + IllIllll / 2f + slinkyOuter, slinkyOuter, slinkyApplyAlpha(0x66000000, 0.75f + 0.25f * slinkySliderHover));
render.roundedRect(IIlllll - IlIIIlI, IllIllII + IllIllll / 2f - IlIIIlI, IIlllll + IlIIIlI, IllIllII + IllIllll / 2f + IlIIIlI, IlIIIlI, IIll);
if (lllIllI == lIIllIII || IlllIIl) {
float Illlllll = 5f * lIllIIll;
float IlllllII = IllIlIII(lIlIIll, IlIllIll) + 2f * Illlllll;
float IllllIll = IIlllll - IlllllII / 2f;
if (IllllIll < IllIllIl) {
IllllIll = IllIllIl;
}
if (IllllIll + IlllllII > IllIllIl + IllIlllI) {
IllllIll = IllIllIl + IllIlllI - IlllllII;
}
IllllllI = true;
IllllIlI = IllllIll;
IllllIIl = IllIllII - 12f * lIllIIll - 4f * lIllIIll;
IlllllIl = IlIllIll;
lIIIIIIl = lllllII;
}
if (llIllIII && IlIllll(IllIllIl - 2f * lIllIIll, IllIllII - 6f * lIllIIll, IllIlllI + 4f * lIllIIll, IllIllll + 12f * lIllIIll)) {
llIllIII = false;
lllIllI = lIIllIII;
}
if (lllIllI == lIIllIII && IIllIII) {
float IIIIlI = IIIIlII - IllIllIl;
if (IIIIlI < 0f) {
IIIIlI = 0f;
}
if (IIIIlI > IllIlllI) {
IIIIlI = IllIlllI;
}
if (IIIIlI <= 0.5f && lIIllIII.canBeDisabled) {
lIIllIII.setValueRaw(-1.0D);
}
else {
lIIllIII.setValue(IIIllIl + (double) (IIIIlI / IllIlllI) * lIIllIlI);
}
IIlIIlI(IIIlIll);
}
}
private void llIllll(ravens4.module.setting.impl.ColorSetting IIIllI, float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
String IlIlIII = IlIIII(IIIllI);
boolean IlIllIl = lIlIllI.contains(IlIlIII);
float lIllllII = 17f * lIllIIll;
float IIIIll = IlIIllII + lIllllII / 2f;
float lIIIllll = 20f * lIllIIll;
float lIlIIlIl = 10f * lIllIIll;
float lIIIllIl = IlIIllll + IlIlIlll - lIIIllll;
int lIIIlll = 0xFF000000 | IIlll(llIlll & 0x00FFFFFF, 20);
float lIIlIlI = 1.5f * lIllIIll;
render.roundedRect(lIIIllIl - lIIlIlI, IIIIll - lIlIIlIl / 2f - lIIlIlI, lIIIllIl + lIIIllll + lIIlIlI, IIIIll + lIlIIlIl / 2f + lIIlIlI, (lIlIIlIl / 2f) + lIIlIlI, lIIIlll);
render.roundedRect(lIIIllIl, IIIIll - lIlIIlIl / 2f, lIIIllIl + lIIIllll, IIIIll + lIlIIlIl / 2f, lIlIIlIl / 2f, llIII(255, IIIllI.getRed(), IIIllI.getGreen(), IIIllI.getBlue()));
IllIIllI(lIlIIll, IllIlIll(lIlIIll, IIIllI.getName(), IlIlIlll - lIIIllll - 8f * lIllIIll), IlIIllll, IIIIll, llIllI);
if (IllIll(IlIIllll, IlIIllII, IlIlIlll, lIllllII)) {
if (IlIllIl) {
lIlIllI.remove(IlIlIII);
}
else {
lIlIllI.add(IlIlIII);
}
return;
}
if (!IlIllIl) {
return;
}
int IllllI = IIIllI.hasAlpha() ? 4 : 3;
float IlllII = IlIIllII + lIllllII;
for (int lllIIl = 0; lllIIl < IllllI; lllIIl++) {
lllIIII(IIIllI, lllIIl, IlIIllll + 7f * lIllIIll, IlllII, IlIlIlll - 7f * lIllIIll);
IlllII += 12f * lIllIIll;
}
}
private void lllIIII(ravens4.module.setting.impl.ColorSetting IIIllI, int Illlll, float IlIIllll, float IlIIllII, float IlIlIlll) {
String IIIIIIl = Illlll == 0 ? "R" : (Illlll == 1 ? "G" : (Illlll == 2 ? "B" : "A"));
int IlIlllII = Illlll == 0 ? IIIllI.getRed() : (Illlll == 1 ? IIIllI.getGreen() : (Illlll == 2 ? IIIllI.getBlue() : IIIllI.getAlpha()));
float IIIIll = IlIIllII + 6f * lIllIIll;
IllIIllI(lIlIIll, IIIIIIl, IlIIllll, IIIIll, llIlIl);
String IlIllIIl = Integer.toString(IlIlllII);
float IlIllIII = IllIlIII(lIlIIll, IlIllIIl);
IllIIllI(lIlIIll, IlIllIIl, IlIIllll + IlIlIlll - IlIllIII, IIIIll, llIlIl);
float IllIllIl = IlIIllll + 11f * lIllIIll;
float IllIlllI = IlIlIlll - 11f * lIllIIll - IlIllIII - 5f * lIllIIll;
if (IllIlllI < 10f * lIllIIll) {
IllIlllI = 10f * lIllIIll;
}
float IllIllll = 3f * lIllIIll;
float IllIllII = IIIIll - IllIllll / 2f;
int lIIIlIl = Illlll == 0 ? llIII(255, 255, 60, 60) : (Illlll == 1 ? llIII(255, 60, 220, 90) : (Illlll == 2 ? llIII(255, 70, 130, 255) : llI));
render.roundedRect(IllIllIl, IllIllII, IllIllIl + IllIlllI, IllIllII + IllIllll, IllIllll / 2f, lll);
float slinkyColorTarget = (float) IlIlllII / 255f;
String slinkyColorKey = "color-slider:" + Integer.toHexString(IIIllI.hashCode()) + ":" + Illlll;
float slinkyColorDisplay = slinkyAnimatedValue(slinkySliderAnimations, slinkyColorKey, slinkyColorTarget, (lllIlll == IIIllI && llllIII == Illlll) ? 7.25f : 5.75f);
render.roundedRect(IllIllIl, IllIllII, IllIllIl + IllIlllI * slinkyColorDisplay, IllIllII + IllIllll, IllIllll / 2f, lIIIlIl);
float slinkyColorKnob = 2.75f * lIllIIll;
float slinkyColorX = IllIllIl + IllIlllI * slinkyColorDisplay;
render.roundedRect(slinkyColorX - slinkyColorKnob, IllIllII + IllIllll / 2f - slinkyColorKnob, slinkyColorX + slinkyColorKnob, IllIllII + IllIllll / 2f + slinkyColorKnob, slinkyColorKnob, lIIIlIl);
if (llIllIII && IlIllll(IllIllIl - 2f * lIllIIll, IllIllII - 5f * lIllIIll, IllIlllI + 4f * lIllIIll, IllIllll + 10f * lIllIIll)) {
llIllIII = false;
lllIlll = IIIllI;
llllIII = Illlll;
}
if (lllIlll == IIIllI && llllIII == Illlll && IIllIII) {
float IIIIlI = IIIIlII - IllIllIl;
if (IIIIlI < 0f) {
IIIIlI = 0f;
}
if (IIIIlI > IllIlllI) {
IIIIlI = IllIlllI;
}
int lllIlllI = (int) ((IIIIlI / IllIlllI) * 255f);
if (Illlll == 0) {
IIIllI.setColor(lllIlllI, IIIllI.getGreen(), IIIllI.getBlue());
}
else if (Illlll == 1) {
IIIllI.setColor(IIIllI.getRed(), lllIlllI, IIIllI.getBlue());
}
else if (Illlll == 2) {
IIIllI.setColor(IIIllI.getRed(), IIIllI.getGreen(), lllIlllI);
}
else {
IIIllI.setAlpha(lllIlllI);
}
}
}
private void llIllII(ravens4.module.setting.impl.KeySetting IlIIIII, float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
boolean IlIII = IlIll == IlIIIII;
String lIIIIlIl = IlIII ? "..." : IlIIllI(IlIIIII.getKey());
float IIIIll = IlIIllII + IllllII / 2f;
float llIIllII = IllIlIII(lIlIIll, lIIIIlIl) + 10f * lIllIIll;
float llIlllll = 13f * lIllIIll;
float llIIlIll = IlIIllll + IlIlIlll - llIIllII;
float llIIlIlI = IIIIll - llIlllll / 2f;
lllIIlII(llIIlIll, llIIlIlI, llIIlIll + llIIllII, llIIlIlI + llIlllll, 4f * lIllIIll, false);
IllIIllI(lIlIIll, lIIIIlIl, llIIlIll + llIIllII / 2f - IllIlIII(lIlIIll, lIIIIlIl) / 2f, IIIIll, IlIII ? llIllI : (IlIIIII.getKey() != 0 ? IIll : llIlII));
IllIIllI(lIlIIll, IllIlIll(lIlIIll, IlIIIII.getName(), IlIlIlll - llIIllII - 6f * lIllIIll), IlIIllll, IIIIll, llIllI);
if (IllIll(llIIlIll, llIIlIlI - 1f * lIllIIll, llIIllII, llIlllll + 2f * lIllIIll)) {
if (IlIII) {
IlIIIII.setKey(0);
IlIll = null;
}
else {
IlIll = IlIIIII;
IlIlI = null;
lIlllIl = null;
}
}
}
private void llIIlII(ravens4.module.setting.impl.TextSetting IllIlIIl, float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
boolean IIlI = lIlllIl == IllIlIIl;
float IIIIll = IlIIllII + IllllII / 2f;
float IIllIll = IllIlIII(lIlIIll, IllIlIIl.getName()) + 6f * lIllIIll;
if (IIllIll > IlIlIlll * 0.42f) {
IIllIll = IlIlIlll * 0.42f;
}
IllIIllI(lIlIIll, IllIlIll(lIlIIll, IllIlIIl.getName(), IIllIll - 4f * lIllIIll), IlIIllll, IIIIll, llIllI);
float lllIll = IlIIllll + IIllIll;
float llllIl = IlIlIlll - IIllIll;
float IllII = 14f * lIllIIll;
float lllIlI = IIIIll - IllII / 2f;
render.roundedRect(lllIll, lllIlI, lllIll + llllIl, lllIlI + IllII, IllII / 2f, IIlI ? II : Il);
String lIlIIlII;
int IlIIIl;
if (IIlI) {
boolean lIIlll = (client.time() / 500L) % 2L == 0L;
lIlIIlII = lIllllI + (lIIlll ? "_" : "");
IlIIIl = llIllI;
}
else if (IllIlIIl.getText() != null && IllIlIIl.getText().length() > 0) {
lIlIIlII = IllIlIIl.getText();
IlIIIl = llIllI;
}
else {
lIlIIlII = IllIlIIl.getPlaceholder();
IlIIIl = llIlII;
}
IllIIllI(lIlIIll, IllIlIlI(lIlIIll, lIlIIlII, llllIl - 8f * lIllIIll), lllIll + 4f * lIllIIll, IIIIll, IlIIIl);
if (IllIll(lllIll, lllIlI - 2f * lIllIIll, llllIl, IllII + 4f * lIllIIll)) {
if (IIlI) {
lIlllIl.setText(lIllllI);
lIlllIl.submit();
lIlllIl = null;
lIllllI = "";
}
else {
lIlllIl = IllIlIIl;
lIllllI = IllIlIIl.getText() == null ? "" : IllIlIIl.getText();
IlIll = null;
IlIlI = null;
}
}
}
private void llllllI() {
java.util.ArrayList lllll = llllI();
boolean llllIIIl = modules.getButton(scriptName, lIIl);
for (int IllIlll = 0; IllIlll < lllll.size(); IllIlll++) {
ravens4.module.Module IIlIlIl = (ravens4.module.Module) lllll.get(IllIlll);
if (!IIlIlIl.canBeEnabled()) {
continue;
}
String IIIIIIl = IIlIlIl.getName();
boolean llllIIII = IIlIlIl.isEnabled();
Object llIlIlIl = llIlIlII.get(IIIIIIl);
boolean IlIlIlIl = llIlIlIl != null && ((Boolean) llIlIlIl).booleanValue();
if (lIllIlI && llIlIlIl != null && llllIIII != IlIlIlIl && llllIIIl) {
llIIllIl(IIIIIIl, llllIIII);
}
llIlIlII.put(IIIIIIl, new Boolean(llllIIII));
}
lIllIlI = true;
}
private void IIlIlII() {
java.util.ArrayList lllll = llllI();
boolean lIlIlIII = !lIlIlIIl;
lIlIlIIl = true;
boolean IlIlIllI = modules.getButton(scriptName, IlI);
lIIlIll.clear();
for (int IllIlll = 0; IllIlll < lllll.size(); IllIlll++) {
ravens4.module.Module IIlIlIl = (ravens4.module.Module) lllll.get(IllIlll);
String IIIIIIl = IIlIlIl.getName();
if (lIlIlIII) {
if (IIlIlIl.ignoreOnSave) {
IIlIlIl.ignoreOnSave = false;
}
IlIlllll.put(IIIIIIl, new Boolean(IIlIlIl.isHidden()));
}
else if (!IlIlllll.containsKey(IIIIIIl)) {
IlIlllll.put(IIIIIIl, new Boolean(IIlIlIl.isHidden()));
}
if (IIIIIIl.equals("HUD")) {
continue;
}
boolean IlIlllI = IlIllllI(IIlIlIl);
boolean lIIllII = IlIlIllI && IIlIlIl.isEnabled() && !IlIlllI;
if (lIIllII) {
lIIlIll.add(IIIIIIl);
}
boolean lIIIlIII = IlIlllI || lIIllII;
if (IIlIlIl.isHidden() != lIIIlIII) {
IIlIlIl.setHidden(lIIIlIII);
}
}
}
private boolean IlIllllI(ravens4.module.Module IIlIlIl) {
Object lllIllIl = IlIlllll.get(IIlIlIl.getName());
return lllIllIl != null && ((Boolean) lllIllIl).booleanValue();
}
private void llIIIlII() {
IlIlllll.clear();
lIIlIll.clear();
llIIIllI();
java.util.ArrayList lllll = llllI();
for (int IllIlll = 0; IllIlll < lllll.size(); IllIlll++) {
ravens4.module.Module IIlIlIl = (ravens4.module.Module) lllll.get(IllIlll);
IlIlllll.put(IIlIlIl.getName(), new Boolean(IIlIlIl.isHidden()));
}
}
private void llIIIIlI() {
llIIIllI();
java.util.ArrayList lllll = llllI();
for (int IllIlll = 0; IllIlll < lllll.size(); IllIlll++) {
ravens4.module.Module IIlIlIl = (ravens4.module.Module) lllll.get(IllIlll);
boolean IlIlllI = IlIllllI(IIlIlIl);
if (IIlIlIl.isHidden() != IlIlllI) {
IIlIlIl.setHidden(IlIlllI);
}
}
lIIlIll.clear();
}
private void lIlllI(ravens4.module.Module IIIlIll, Throwable lIIIlIll) {
String IIIIIIl = IIIlIll == null ? "module" : IIIlIll.getName();
String IIIIllI = lIIIlIll == null ? "unknown error" : lIIIlIll.getClass().getSimpleName();
client.print(util.color("&cSolace: '" + IIIIIIl + "' errored (" + IIIIllI + ") - skipped to avoid a crash."));
}
private void llIIllIl(String IIIlIII, boolean lIllIll) {
if (llllIlIl >= llllIIlI.length) {
for (int IllIlll = 1; IllIlll < llllIlIl; IllIlll++) {
llllIIlI[IllIlll - 1] = llllIIlI[IllIlll];
llllIlII[IllIlll - 1] = llllIlII[IllIlll];
llllIIll[IllIlll - 1] = llllIIll[IllIlll];
}
llllIlIl--;
}
llllIIlI[llllIlIl] = IIIlIII;
llllIlII[llllIlIl] = lIllIll;
llllIIll[llllIlIl] = client.time();
llllIlIl++;
}
private void llIlIII() {
if (llllIlIl == 0) {
return;
}
long life = 2600L;
float slide = 260f;
float rowH = 24f * lIllIIll;
float pad = 10f * lIllIIll;
float gap = 5f * lIllIIll;
float margin = 10f * lIllIIll;
float textGap = 9f * lIllIIll;
float tagPadX = 4f * lIllIIll;
float tagH = Math.max(12f * lIllIIll, rowH - 6f * lIllIIll);
int tagBg = (0x55 << 24) | (IIll & 0x00FFFFFF);

int corner = (int) Math.round(modules.getSlider(scriptName, lIlI));
boolean right = corner == 1 || corner == 3;
boolean bottom = corner == 2 || corner == 3;

ravens4.utility.font.RavenFontRenderer fr = lIIlllI(0.95f);
long now = client.time();

while (llllIlIl > 0 && now - llllIIll[0] > life) {
for (int i = 1; i < llllIlIl; i++) {
llllIIlI[i - 1] = llllIIlI[i];
llllIlII[i - 1] = llllIlII[i];
llllIIll[i - 1] = llllIIll[i];
}
llllIlIl--;
}

float y = bottom ? ((float) lIlIlllI - margin - rowH) : margin;
for (int i = llllIlIl - 1; i >= 0; i--) {
long age = now - llllIIll[i];
float appear = age < slide ? (float) age / slide : 1f;
float disappear = age > life - slide ? (float) (life - age) / slide : 1f;
if (disappear < 0f) {
disappear = 0f;
}
float t = llIIIIl(appear) * llIIIIl(disappear);

String tag = "SOLACE";
String label = (llllIlII[i] ? "Enabled " : "Disabled ") + llllIIlI[i];
float boxW = IllIlIII(fr, tag) + IllIlIII(fr, label) + 2f * pad + textGap;
float textY = y + rowH / 2f - (float) fr.getFontHeight() / 2f;

float x;
if (right) {
x = (float) lIlIllIl - margin - boxW * t;
render.roundedRect(x, y, x + boxW, y + rowH, rowH / 2f, llIlll);
float tagX = x + pad - tagPadX;
float tagY = y + (rowH - tagH) / 2f;
render.roundedRect(tagX, tagY, tagX + IllIlIII(fr, tag) + 2f * tagPadX,
tagY + tagH, tagH / 2f, tagBg);
fr.drawString(tag, x + pad, textY, IIll, false);
fr.drawString(label, x + pad + IllIlIII(fr, tag) + textGap, textY, llIllI, false);
}
else {
x = margin - (boxW * (1f - t));
render.roundedRect(x, y, x + boxW, y + rowH, rowH / 2f, llIlll);
float tagX = x + pad - tagPadX;
float tagY = y + (rowH - tagH) / 2f;
render.roundedRect(tagX, tagY, tagX + IllIlIII(fr, tag) + 2f * tagPadX,
tagY + tagH, tagH / 2f, tagBg);
fr.drawString(tag, x + pad, textY, IIll, false);
fr.drawString(label, x + pad + IllIlIII(fr, tag) + textGap, textY, llIllI, false);
}

if (bottom) {
y -= rowH + gap;
if (y < 0f) {
break;
}
}
else {
y += rowH + gap;
if (y > (float) lIlIlllI) {
break;
}
}
}
}
private void lllIlII() {
int slinkyMode = (int) Math.round(modules.getSlider(scriptName, slinkyArraylistMode));
if (slinkyMode <= 0) {
slinkyRenderClassicArraylist();
}
else {
slinkyRenderModernArraylist();
}
}
private void slinkyRenderClassicArraylist() {
java.util.ArrayList lIlIIlII = lIllIIl();
int IIIIIlI = lIlIIlII.size();
if (IIIIIlI == 0) {
return;
}
ravens4.utility.font.RavenFontRenderer lIIlIIl = lIlIlII;
float lIllllII = 15f * lIllIIll;
float lllIIIlI = 6f * lIllIIll;
float IIlIIll = 3f * lIllIIll;
float llIIlI = 2.5f * lIllIIll;
int IIlIIl = (int) Math.round(modules.getSlider(scriptName, IIl));
boolean lIllllll = IIlIIl == 1 || IIlIIl == 3;
boolean IIllI = IIlIIl == 2 || IIlIIl == 3;
lIIllIll(lIlIIlII, lIIlIIl);
float lIlllIIl = lIllllII + 1.5f * lIllIIll;
float IlIIllII = IIllI ? ((float) lIlIlllI - IIlIIll - lIllllII) : IIlIIll;
for (int IllIlll = 0; IllIlll < IIIIIlI; IllIlll++) {
ravens4.module.Module IIlIlIl = (ravens4.module.Module) lIlIIlII.get(IllIlll);
String IIIIIIl = IIlIlIl.getName();
String lIIlIIIl = lIIlIIll(IIlIlIl);
float lllllllI = IllIlIII(lIIlIIl, IIIIIIl);
float lIIlIIlI = lIIlIIIl.length() > 0 ? IllIlIII(lIIlIIl, " " + lIIlIIIl) : 0f;
float lIIIlIII = lllllllI + lIIlIIlI + 2f * lllIIIlI + llIIlI;
Object lllIllIl = lIlll.get(IIIIIIl);
float IIIlIl = lllIllIl == null ? lIIIlIII : ((Float) lllIllIl).floatValue();
IIIlIl = llIIl(IIIlIl, lIIIlIII, 8f);
lIlll.put(IIIIIIl, new Float(IIIlIl));
float IlIIllll = lIllllll ? ((float) lIlIllIl - IIlIIll - IIIlIl) : IIlIIll;
render.roundedRect(IlIIllll, IlIIllII, IlIIllll + IIIlIl, IlIIllII + lIllllII, 3.5f * lIllIIll, llIlll);
if (lIllllll) {
render.roundedRect(IlIIllll + IIIlIl - llIIlI, IlIIllII, IlIIllll + IIIlIl, IlIIllII + lIllllII, 1.5f * lIllIIll, IIll);
}
else {
render.roundedRect(IlIIllll, IlIIllII, IlIIllll + llIIlI, IlIIllII + lIllllII, 1.5f * lIllIIll, IIll);
}
float IllIIlll = lIllllll ? (IlIIllll + lllIIIlI) : (IlIIllll + lllIIIlI + llIIlI);
IllIIllI(lIIlIIl, IIIIIIl, IllIIlll, IlIIllII + lIllllII / 2f, llIllI);
if (lIIlIIIl.length() > 0) {
IllIIllI(lIIlIIl, lIIlIIIl, IllIIlll + lllllllI + IllIlIII(lIIlIIl, " "), IlIIllII + lIllllII / 2f, llIlIl);
}
IlIIllII += IIllI ? -lIlllIIl : lIlllIIl;
if (IlIIllII < 0f || IlIIllII > (float) lIlIlllI) {
break;
}
}
llIIlllI(lIlIIlII);
}
private void slinkyRenderModernArraylist() {
java.util.ArrayList shown = lIllIIl();
int n = shown.size();
if (n == 0) {
return;
}

// Keep the original Solace arraylist logic/data source.  Only the visual
// construction is changed to the connected HUD-v1 staircase style.
float slinkyArrayScale = (float) modules.getSlider(scriptName, slinkyArraylistScale) / 100f;
if (slinkyArrayScale < 0.6f) {
slinkyArrayScale = 0.6f;
}
if (slinkyArrayScale > 1.6f) {
slinkyArrayScale = 1.6f;
}
float slinkyArrayFontScale = 1.05f * slinkyArrayScale;
if (slinkyArrayFontScale < 0.5f) {
slinkyArrayFontScale = 0.5f;
}
if (slinkyArrayFontScale > 2.0f) {
slinkyArrayFontScale = 2.0f;
}
ravens4.utility.font.RavenFontRenderer fr = ravens4.utility.font.FontManager.getHudRenderer(lIIllll, slinkyArrayFontScale);

// HUD-v1 proportions: tighter and cleaner than the old pill-per-row renderer.
float padX = 2f * slinkyArrayScale;
float padY = 1f * slinkyArrayScale;
float rowH = (float) fr.getFontHeight() + 2f * padY;
float margin = 0f;

int bgAlpha = (int) (modules.getSlider(scriptName, slinkyArraylistBackgroundOpacity) / 100.0 * 255.0);
if (bgAlpha < 0) {
bgAlpha = 0;
}
if (bgAlpha > 255) {
bgAlpha = 255;
}
// Theme sync stays native to Solace: use the currently selected GUI background RGB.
int arrayBg = (bgAlpha << 24) | (lllIII & 0x00FFFFFF);

int corner = (int) Math.round(modules.getSlider(scriptName, IIl));
boolean right = corner == 1 || corner == 3;
boolean bottom = corner == 2 || corner == 3;

// Keep the original ordering and module-info behavior.
slinkySortArraylist(shown, fr);

// Negative spacing used to create alpha-overlap/dark seams.  Treat it as zero
// for the connected style; positive values still deliberately add a gap.
float requestedSpacing = (float) modules.getSlider(scriptName, slinkyArraylistSpacing) * slinkyArrayScale;
float visualGap = requestedSpacing > 0f ? requestedSpacing : 0f;
float rowStep = rowH + visualGap;
float arrayRadius = (float) modules.getSlider(scriptName, slinkyArraylistRounding) * slinkyArrayScale;
if (arrayRadius < 0f) {
arrayRadius = 0f;
}

float[] widths = new float[n];
for (int i = 0; i < n; i++) {
ravens4.module.Module m = (ravens4.module.Module) shown.get(i);
String key = m.getName();
String name = slinkyArrayText(key);
String suffix = slinkyArrayText(lIIlIIll(m));
float target = IllIlIII(fr, name) +
(suffix.length() > 0 ? IllIlIII(fr, " " + suffix) : 0f) + 2f * padX;
Object o = lIlll.get(key);
float cur = o == null ? target : ((Float) o).floatValue();
cur = llIIl(cur, target, 8f);
lIlll.put(key, new Float(cur));
widths[i] = cur;
}

float screenW = (float) lIlIllIl;
float screenH = (float) lIlIlllI;
float y = bottom ? (screenH - margin - rowH) : margin;

for (int i = 0; i < n; i++) {
ravens4.module.Module m = (ravens4.module.Module) shown.get(i);
String name = slinkyArrayText(m.getName());
String suffix = slinkyArrayText(lIIlIIll(m));
float nameW = IllIlIII(fr, name);
float cur = widths[i];

float left = right ? (screenW - margin - cur) : margin;
float rightEdge = right ? (screenW - margin) : (margin + cur);
float cy = y + rowH / 2f;
float tx = left + padX;

// The radius of a step may never be larger than the horizontal difference to
// the next row.  This is what makes the staircase join cleanly instead of
// looking like a stack of separate rounded boxes.
float stepRadius = arrayRadius;
if (i + 1 < n) {
float stepDifference = widths[i] - widths[i + 1];
if (stepDifference < 0f) {
stepDifference = 0f;
}
if (stepRadius > stepDifference) {
stepRadius = stepDifference;
}
}
float maxRadius = Math.min(rowH, cur);
if (stepRadius > maxRadius) {
stepRadius = maxRadius;
}

slinkyDrawConnectedArrayRow(left, y, rightEdge, rowH, stepRadius, arrayBg, right, bottom);

// Theme sync: module name = Solace accent, details = Solace secondary text.
fr.drawString(name, tx, cy - (float) fr.getFontHeight() / 2f, IIll, true);
if (suffix.length() > 0) {
fr.drawString(suffix, tx + nameW + IllIlIII(fr, " "),
cy - (float) fr.getFontHeight() / 2f, llIlIl, true);
}

y += bottom ? -rowStep : rowStep;
if (y + rowH < 0f || y > screenH) {
break;
}
}

llIIlllI(shown);
}

private void slinkyDrawConnectedArrayRow(float left, float top, float right, float height,
float requestedRadius, int color, boolean rightAligned, boolean bottomLayout) {
float width = right - left;
if (width <= 0f || height <= 0f) {
return;
}
float radius = requestedRadius;
if (radius < 0f) {
radius = 0f;
}
float maxRadius = Math.min(width, height);
if (radius > maxRadius) {
radius = maxRadius;
}
if (radius <= 0.001f) {
render.rect(left, top, right, top + height, color);
return;
}

float bottom = top + height;
float centerX;
float centerY;
double startAngle;
double endAngle;

if (!bottomLayout) {
// Top -> bottom list: keep the upper join square and round only the step
// leading into the next (narrower) row.
if (rightAligned) {
// Bottom-left corner.
render.rect(left, top, right, bottom - radius, color);
render.rect(left + radius, bottom - radius, right, bottom, color);
centerX = left + radius;
centerY = bottom - radius;
startAngle = Math.PI;
endAngle = Math.PI / 2.0;
}
else {
// Bottom-right corner.
render.rect(left, top, right, bottom - radius, color);
render.rect(left, bottom - radius, right - radius, bottom, color);
centerX = right - radius;
centerY = bottom - radius;
startAngle = 0.0;
endAngle = Math.PI / 2.0;
}
}
else {
// Bottom -> top list: mirror the shape vertically.
if (rightAligned) {
// Top-left corner.
render.rect(left, top + radius, right, bottom, color);
render.rect(left + radius, top, right, top + radius, color);
centerX = left + radius;
centerY = top + radius;
startAngle = Math.PI;
endAngle = Math.PI * 1.5;
}
else {
// Top-right corner.
render.rect(left, top + radius, right, bottom, color);
render.rect(left, top, right - radius, top + radius, color);
centerX = right - radius;
centerY = top + radius;
startAngle = 0.0;
endAngle = -Math.PI / 2.0;
}
}

slinkyDrawArrayQuarterCircle(centerX, centerY, radius, startAngle, endAngle, color);
}

private void slinkyDrawArrayQuarterCircle(float centerX, float centerY, float radius,
double startAngle, double endAngle, int color) {
int red = (color >> 16) & 255;
int green = (color >> 8) & 255;
int blue = color & 255;
int alpha = (color >> 24) & 255;

gl.push();
gl.texture2d(false);
gl.blend(true);
gl.alpha(false);
gl.cull(false);
gl.color(red, green, blue, alpha);
gl.begin(6);
gl.vertex2(centerX, centerY);
for (int point = 0; point <= 48; point++) {
double t = (double) point / 48.0;
double angle = startAngle + (endAngle - startAngle) * t;
gl.vertex2((float) (centerX + Math.cos(angle) * radius),
(float) (centerY + Math.sin(angle) * radius));
}
gl.end();
gl.cull(true);
gl.alpha(true);
gl.texture2d(true);
gl.resetColor();
gl.pop();
}
private void slinkySortArraylist(java.util.ArrayList list, ravens4.utility.font.RavenFontRenderer fr) {
int n = list.size();
for (int i = 0; i < n - 1; i++) {
int best = i;
float bestW = slinkyArrayEntryWidth((ravens4.module.Module) list.get(i), fr);
for (int j = i + 1; j < n; j++) {
float w = slinkyArrayEntryWidth((ravens4.module.Module) list.get(j), fr);
if (w > bestW) {
bestW = w;
best = j;
}
}
if (best != i) {
Object tmp = list.get(i);
list.set(i, list.get(best));
list.set(best, tmp);
}
}
}
private float slinkyArrayEntryWidth(ravens4.module.Module mod, ravens4.utility.font.RavenFontRenderer fr) {
String suffix = slinkyArrayText(lIIlIIll(mod));
float w = IllIlIII(fr, slinkyArrayText(mod.getName()));
if (suffix.length() > 0) {
w += IllIlIII(fr, " " + suffix);
}
return w;
}
private String slinkyArrayText(String text) {
if (text == null) {
return "";
}
if (modules.getButton(scriptName, slinkyArraylistLowercase)) {
return text.toLowerCase(java.util.Locale.ROOT);
}
return text;
}
private String lIIlIIll(ravens4.module.Module IIIlIll) {
java.util.ArrayList IIlIllI = IIIlIll.getSettings();
for (int IllIlll = 0; IllIlll < IIlIllI.size(); IllIlll++) {
ravens4.module.setting.Setting lIIlIlll = (ravens4.module.setting.Setting) IIlIllI.get(IllIlll);
if (!lIIlIlll.visible) {
continue;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.SliderSetting) {
ravens4.module.setting.impl.SliderSetting lIIllIII = (ravens4.module.setting.impl.SliderSetting) lIIlIlll;
if (lIIllIII.isString) {
String[] lllIIlll = lIIllIII.getOptions();
if (lllIIlll != null && lllIIlll.length > 0) {
int IllIllI = (int) Math.round(lIIllIII.getInput());
if (IllIllI < 0) {
IllIllI = 0;
}
if (IllIllI >= lllIIlll.length) {
IllIllI = lllIIlll.length - 1;
}
return lllIIlll[IllIllI];
}
}
else {
double IlIlllIl = lIIllIII.getInput();
if (!(lIIllIII.canBeDisabled && IlIlllIl == -1.0D)) {
return lIlIIII(IlIlllIl) + lIIllIII.getSuffix();
}
}
}
}
return "";
}
private void llIIIllI() {
IIIlIIl.clear();
if (ravens4.clickgui.ClickGui.categories == null) {
return;
}
for (ravens4.clickgui.components.impl.CategoryComponent lIIIlI : ravens4.clickgui.ClickGui.categories) {
if (lIIIlI == null) {
continue;
}
for (ravens4.clickgui.components.impl.ModuleComponent IIIlllI : lIIIlI.modules) {
if (IIIlllI != null && IIIlllI.mod != null) {
IIIlIIl.add(IIIlllI.mod);
}
}
}
}
private java.util.ArrayList llllI() {
return IIIlIIl;
}
private java.util.ArrayList lIllIIl() {
java.util.ArrayList lllll = llllI();
java.util.ArrayList lllIIllI = new java.util.ArrayList();
for (int IllIlll = 0; IllIlll < lllll.size(); IllIlll++) {
ravens4.module.Module IIlIlIl = (ravens4.module.Module) lllll.get(IllIlll);
boolean IllIIIlI = IlIllllI(IIlIlIl);
boolean IlIllII = IIlIlIl instanceof ravens4.utility.profile.ProfileModule;
if (IIlIlIl.isEnabled() && IIlIlIl.canBeEnabled() && !IllIIIlI && !IlIllII && !IIlIlIl.getName().equals("HUD")) {
lllIIllI.add(IIlIlIl);
}
}
return lllIIllI;
}
private void lIIllIll(java.util.ArrayList IIlIllI, ravens4.utility.font.RavenFontRenderer lIIlIIl) {
int IIIIIlI = IIlIllI.size();
for (int IllIlll = 0; IllIlll < IIIIIlI - 1; IllIlll++) {
int Illll = IllIlll;
float IlllI = lIllIlll((ravens4.module.Module) IIlIllI.get(IllIlll), lIIlIIl);
for (int IlIlIlI = IllIlll + 1; IlIlIlI < IIIIIlI; IlIlIlI++) {
float IlIlIlll = lIllIlll((ravens4.module.Module) IIlIllI.get(IlIlIlI), lIIlIIl);
if (IlIlIlll > IlllI) {
IlllI = IlIlIlll;
Illll = IlIlIlI;
}
}
if (Illll != IllIlll) {
Object IllllIII = IIlIllI.get(IllIlll);
IIlIllI.set(IllIlll, IIlIllI.get(Illll));
IIlIllI.set(Illll, IllllIII);
}
}
}
private float lIllIlll(ravens4.module.Module IIlIlIl, ravens4.utility.font.RavenFontRenderer lIIlIIl) {
String lIIlIIIl = lIIlIIll(IIlIlIl);
float IlIlIlll = IllIlIII(lIIlIIl, IIlIlIl.getName());
if (lIIlIIIl.length() > 0) {
IlIlIlll += IllIlIII(lIIlIIl, " " + lIIlIIIl);
}
return IlIlIlll;
}
private void llIIlllI(java.util.ArrayList lIlIIlII) {
java.util.ArrayList IlIIlII = new java.util.ArrayList(lIlll.keySet());
for (int IllIlll = 0; IllIlll < IlIIlII.size(); IllIlll++) {
String IlIlIIl = (String) IlIIlII.get(IllIlll);
boolean llIllIIl = false;
for (int IlIlIlI = 0; IlIlIlI < lIlIIlII.size(); IlIlIlI++) {
if (((ravens4.module.Module) lIlIIlII.get(IlIlIlI)).getName().equals(IlIlIIl)) {
llIllIIl = true;
break;
}
}
if (!llIllIIl) {
lIlll.remove(IlIlIIl);
}
}
}
private float lIlllII() {
float lIIlllII = (float) modules.getSlider(scriptName, Illl);
if (lIIlllII <= 0.1f) {
lIIlllII = 1f;
}
return lIIlllII;
}
private ravens4.utility.font.RavenFontRenderer lIIlllI(float IIIIlIl) {
float lIllIIIl = IIIIlIl * lIllIIll;
if (lIllIIIl < 0.5f) {
lIllIIIl = 0.5f;
}
if (lIllIIIl > 2.0f) {
lIllIIIl = 2.0f;
}
return ravens4.utility.font.FontManager.getHudRenderer(lIIllll, lIllIIIl);
}
private void IllIIllI(ravens4.utility.font.RavenFontRenderer lIIlIIl, String lIIIIlIl, float IlIIllll, float lIIIIl, int IlIIIl) {
if (lIIlIIl == null || lIIIIlIl == null || lIIIIlIl.length() == 0) {
return;
}
lIIlIIl.drawString(lIIIIlIl, IlIIllll, lIIIIl - (float) lIIlIIl.getFontHeight() / 2f, IlIIIl, false);
}
private float IllIlIII(ravens4.utility.font.RavenFontRenderer lIIlIIl, String lIIIIlIl) {
if (lIIlIIl == null || lIIIIlIl == null) {
return 0f;
}
return (float) lIIlIIl.getStringWidth(lIIIIlIl);
}
private String IllIlIll(ravens4.utility.font.RavenFontRenderer lIIlIIl, String lIIIIlIl, float IIIllll) {
if (lIIlIIl == null || lIIIIlIl == null || IIIllll <= 0f) {
return "";
}
if (IllIlIII(lIIlIIl, lIIIIlIl) <= IIIllll) {
return lIIIIlIl;
}
String lllIIllI = lIIIIlIl;
while (lllIIllI.length() > 1 && IllIlIII(lIIlIIl, lllIIllI + "..") > IIIllll) {
lllIIllI = lllIIllI.substring(0, lllIIllI.length() - 1);
}
return lllIIllI + "..";
}
private String IllIlIlI(ravens4.utility.font.RavenFontRenderer lIIlIIl, String lIIIIlIl, float IIIllll) {
if (lIIlIIl == null || lIIIIlIl == null || IIIllll <= 0f) {
return "";
}
String lllIIllI = lIIIIlIl;
while (lllIIllI.length() > 1 && IllIlIII(lIIlIIl, lllIIllI) > IIIllll) {
lllIIllI = lllIIllI.substring(1);
}
return lllIIllI;
}
private java.util.ArrayList lIIlII() {
java.util.ArrayList lllIIllI = new java.util.ArrayList();
if (ravens4.clickgui.ClickGui.categories == null) {
return lllIIllI;
}
for (ravens4.clickgui.components.impl.CategoryComponent lIIIlI : ravens4.clickgui.ClickGui.categories) {
if (lIIIlI != null && lIIIlI.category != null) {
lllIIllI.add(lIIIlI.category.name());
}
}
return lllIIllI;
}
private java.util.ArrayList lIIlIl(int IllIlII) {
java.util.ArrayList lllIIllI = new java.util.ArrayList();
if (ravens4.clickgui.ClickGui.categories == null) {
return lllIIllI;
}
int IllIlll = 0;
for (ravens4.clickgui.components.impl.CategoryComponent lIIIlI : ravens4.clickgui.ClickGui.categories) {
if (IllIlll == IllIlII && lIIIlI != null) {
for (ravens4.clickgui.components.impl.ModuleComponent IIIlllI : lIIIlI.modules) {
if (IIIlllI != null && IIIlllI.mod != null) {
lllIIllI.add(IIIlllI.mod);
}
}
}
IllIlll++;
}
return lllIIllI;
}
private String lllllll(ravens4.module.Module IIIlIll) {
java.util.ArrayList IIlIllI = IIIlIll.getSettings();
if (!IIlIllI.isEmpty()) {
Object lIlIIIl = IIlIllI.get(0);
if (lIlIIIl instanceof ravens4.module.setting.impl.DescriptionSetting) {
String IIIIlI = ((ravens4.module.setting.impl.DescriptionSetting) lIlIIIl).getDesc();
if (IIIIlI != null && IIIIlI.length() > 0) {
return IIIIlI;
}
}
}
String lIIllI = lIIIll(IIIlIll);
if (lIIllI.length() > 0) {
return IIIlIll.getName() + " - " + lIIllI.toLowerCase() + " module.";
}
return "Toggle to use " + IIIlIll.getName() + ".";
}
private String lIIIll(ravens4.module.Module IIIlIll) {
if (ravens4.clickgui.ClickGui.categories == null) {
return "";
}
for (ravens4.clickgui.components.impl.CategoryComponent lIIIlI : ravens4.clickgui.ClickGui.categories) {
if (lIIIlI == null || lIIIlI.category == null) {
continue;
}
for (ravens4.clickgui.components.impl.ModuleComponent IIIlllI : lIIIlI.modules) {
if (IIIlllI != null && IIIlllI.mod == IIIlIll) {
return llIlIllI(lIIIlI.category.name());
}
}
}
return "";
}
private ravens4.module.setting.impl.GroupSetting lIIIIII( ravens4.module.setting.Setting lIIlIlll) {
if (lIIlIlll instanceof ravens4.module.setting.impl.SliderSetting) {
return ((ravens4.module.setting.impl.SliderSetting) lIIlIlll).groupSetting;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.ButtonSetting) {
return ((ravens4.module.setting.impl.ButtonSetting) lIIlIlll).group;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.ColorSetting) {
return ((ravens4.module.setting.impl.ColorSetting) lIIlIlll).groupSetting;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.KeySetting) {
return ((ravens4.module.setting.impl.KeySetting) lIIlIlll).group;
}
if (lIIlIlll instanceof ravens4.module.setting.impl.TextSetting) {
return ((ravens4.module.setting.impl.TextSetting) lIIlIlll).group;
}
return null;
}
private void IIlIIlI(ravens4.module.Module IIIlIll) {
if (ravens4.Raven.currentProfile == null || IIIlIll.ignoreOnSave) {
return;
}
if (ravens4.Raven.currentProfile.getModule() != null) {
ravens4.Raven.currentProfile.getModule().saved = false;
}
}
private String lIlIlI(int lIIllI, String IIIIIIl) {
return lIIllI + ":" + IIIIIIl;
}
private String IlIIII(ravens4.module.setting.impl.ColorSetting IIIllI) {
return "c" + Integer.toHexString(IIIllI.hashCode());
}
private String llIlIllI(String llIIlIII) {
if (llIIlIII == null || llIIlIII.length() == 0) {
return "";
}
return llIIlIII.substring(0, 1).toUpperCase() + llIIlIII.substring(1);
}
private String lIlIIII(double IlIlllIl) {
if (IlIlllIl == Math.rint(IlIlllIl) && Math.abs(IlIlllIl) < 1.0E9D) {
return Integer.toString((int) IlIlllIl);
}
return Double.toString(util.round(IlIlllIl, 2));
}
private float slinkyAnimatedValue(java.util.HashMap map, String key, float target, float speed) {
Object old = map.get(key);
float value = old == null ? target : ((Float) old).floatValue();
value = llIIl(value, target, speed);
if (Math.abs(value - target) < 0.0005f) {
value = target;
}
map.put(key, new Float(value));
return value;
}
private int slinkyMixColor(int from, int to, float t) {
if (t < 0f) t = 0f;
if (t > 1f) t = 1f;
int a = (int) (((from >>> 24) & 255) + ((((to >>> 24) & 255) - ((from >>> 24) & 255)) * t));
int r = (int) (((from >> 16) & 255) + ((((to >> 16) & 255) - ((from >> 16) & 255)) * t));
int g = (int) (((from >> 8) & 255) + ((((to >> 8) & 255) - ((from >> 8) & 255)) * t));
int b = (int) ((from & 255) + (((to & 255) - (from & 255)) * t));
return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
}
private int slinkyApplyAlpha(int color, float alpha) {
if (alpha < 0f) alpha = 0f;
if (alpha > 1f) alpha = 1f;
int baseA = (color >>> 24) & 255;
int a = (int) ((float) baseA * alpha);
return (color & 0x00FFFFFF) | ((a & 255) << 24);
}
private int lIlIllll(int llIIIIIl, float IIIIlIl) {
int llIIlIIl = (int) (((llIIIIIl >> 16) & 255) * IIIIlIl);
int lIIIlII = (int) (((llIIIIIl >> 8) & 255) * IIIIlIl);
int lIlII = (int) ((llIIIIIl & 255) * IIIIlIl);
if (llIIlIIl > 255) {
llIIlIIl = 255;
}
if (lIIIlII > 255) {
lIIIlII = 255;
}
if (lIlII > 255) {
lIlII = 255;
}
return (llIIlIIl << 16) | (lIIIlII << 8) | lIlII;
}
private void lllIIlII(float IlIIlllI, float IlIIlIll, float IlIIllIl, float IlIIlIlI, float llIIlIIl, boolean IlllIlI) {
int lllIIlIl = 0xFF000000 | IIlll(llIlll & 0x00FFFFFF, IlllIlI ? 34 : 22);
int lIlIIlI = 0xFF000000 | (llIlll & 0x00FFFFFF);
float lIIIlIll = 1f * lIllIIll;
render.roundedRect(IlIIlllI, IlIIlIll, IlIIllIl, IlIIlIlI, llIIlIIl, lllIIlIl);
render.roundedRect(IlIIlllI + lIIIlIll, IlIIlIll + lIIIlIll, IlIIllIl - lIIIlIll, IlIIlIlI - lIIIlIll, llIIlIIl - lIIIlIll, lIlIIlI);
}
private int IIlll(int llIIIIIl, int IIIl) {
int llIIlIIl = ((llIIIIIl >> 16) & 255) + IIIl;
int lIIIlII = ((llIIIIIl >> 8) & 255) + IIIl;
int lIlII = (llIIIIIl & 255) + IIIl;
if (llIIlIIl > 255) {
llIIlIIl = 255;
}
if (lIIIlII > 255) {
lIIIlII = 255;
}
if (lIlII > 255) {
lIlII = 255;
}
if (llIIlIIl < 0) {
llIIlIIl = 0;
}
if (lIIIlII < 0) {
lIIIlII = 0;
}
if (lIlII < 0) {
lIlII = 0;
}
return (llIIlIIl << 16) | (lIIIlII << 8) | lIlII;
}
private int llIII(int IlII, int llIIlIIl, int lIIIlII, int lIlII) {
return ((IlII & 255) << 24) | ((llIIlIIl & 255) << 16) | ((lIIIlII & 255) << 8) | (lIlII & 255);
}
private boolean IlIllll(float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
if (IIIIlII < IlIIllll || IIIIlII > IlIIllll + IlIlIlll || IIIIIll < IlIIllII || IIIIIll > IlIIllII + IllllII) {
return false;
}
if (slinkyScissorDepth > 0) {
float sx = slinkyScissorX[slinkyScissorDepth - 1];
float sy = slinkyScissorY[slinkyScissorDepth - 1];
float sw = slinkyScissorW[slinkyScissorDepth - 1];
float sh = slinkyScissorH[slinkyScissorDepth - 1];
if (IIIIlII < sx || IIIIlII > sx + sw || IIIIIll < sy || IIIIIll > sy + sh) {
return false;
}
}
return true;
}
private boolean IllIll(float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
if (!llIllIII || !IlIllll(IlIIllll, IlIIllII, IlIlIlll, IllllII)) {
return false;
}
llIllIII = false;
return true;
}
private void lIIII(float IlIIllll, float IlIIllII, float IlIlIlll, float IllllII) {
if (IlIlIlll < 0f) {
IlIlIlll = 0f;
}
if (IllllII < 0f) {
IllllII = 0f;
}
float x = IlIIllll;
float y = IlIIllII;
float w = IlIlIlll;
float h = IllllII;
if (slinkyScissorDepth > 0) {
float px = slinkyScissorX[slinkyScissorDepth - 1];
float py = slinkyScissorY[slinkyScissorDepth - 1];
float pw = slinkyScissorW[slinkyScissorDepth - 1];
float ph = slinkyScissorH[slinkyScissorDepth - 1];
float x2 = Math.min(x + w, px + pw);
float y2 = Math.min(y + h, py + ph);
x = Math.max(x, px);
y = Math.max(y, py);
w = Math.max(0f, x2 - x);
h = Math.max(0f, y2 - y);
}
if (slinkyScissorDepth >= slinkyScissorX.length) {
slinkyScissorDepth = slinkyScissorX.length - 1;
}
slinkyScissorX[slinkyScissorDepth] = x;
slinkyScissorY[slinkyScissorDepth] = y;
slinkyScissorW[slinkyScissorDepth] = w;
slinkyScissorH[slinkyScissorDepth] = h;
slinkyScissorDepth++;
gl.scissor(true);
gl.scissor((int) (x * (float) lIllIIII), (int) (((float) lIlIlllI - (y + h)) * (float) lIllIIII), (int) (w * (float) lIllIIII), (int) (h * (float) lIllIIII));
IllIlI = true;
}
private void lIllIII() {
if (slinkyScissorDepth > 0) {
slinkyScissorDepth--;
}
if (slinkyScissorDepth <= 0) {
slinkyScissorDepth = 0;
IllIlI = false;
gl.scissor(false);
return;
}
float x = slinkyScissorX[slinkyScissorDepth - 1];
float y = slinkyScissorY[slinkyScissorDepth - 1];
float w = slinkyScissorW[slinkyScissorDepth - 1];
float h = slinkyScissorH[slinkyScissorDepth - 1];
gl.scissor(true);
gl.scissor((int) (x * (float) lIllIIII), (int) (((float) lIlIlllI - (y + h)) * (float) lIllIIII), (int) (w * (float) lIllIIII), (int) (h * (float) lIllIIII));
IllIlI = true;
}
private String IlIIllI(int IlIlll) {
if (IlIlll == 0) {
return "NONE";
}
if (IlIlll == 1069) {
return "SCROLL UP";
}
if (IlIlll == 1070) {
return "SCROLL DOWN";
}
if (IlIlll >= 1000) {
return "MOUSE" + (IlIlll - 999);
}
switch (IlIlll) {
case 1: return "ESC";
case 2: return "1";
case 3: return "2";
case 4: return "3";
case 5: return "4";
case 6: return "5";
case 7: return "6";
case 8: return "7";
case 9: return "8";
case 10: return "9";
case 11: return "0";
case 12: return "-";
case 13: return "=";
case 14: return "BACK";
case 15: return "TAB";
case 16: return "Q";
case 17: return "W";
case 18: return "E";
case 19: return "R";
case 20: return "T";
case 21: return "Y";
case 22: return "U";
case 23: return "I";
case 24: return "O";
case 25: return "P";
case 26: return "[";
case 27: return "]";
case 28: return "ENTER";
case 29: return "LCTRL";
case 30: return "A";
case 31: return "S";
case 32: return "D";
case 33: return "F";
case 34: return "G";
case 35: return "H";
case 36: return "J";
case 37: return "K";
case 38: return "L";
case 39: return ";";
case 40: return "'";
case 41: return "`";
case 42: return "LSHIFT";
case 43: return "\\";
case 44: return "Z";
case 45: return "X";
case 46: return "C";
case 47: return "V";
case 48: return "B";
case 49: return "N";
case 50: return "M";
case 51: return ",";
case 52: return ".";
case 53: return "/";
case 54: return "RSHIFT";
case 55: return "NUM*";
case 56: return "LALT";
case 57: return "SPACE";
case 58: return "CAPS";
case 59: return "F1";
case 60: return "F2";
case 61: return "F3";
case 62: return "F4";
case 63: return "F5";
case 64: return "F6";
case 65: return "F7";
case 66: return "F8";
case 67: return "F9";
case 68: return "F10";
case 69: return "NUMLOCK";
case 70: return "SCRLOCK";
case 71: return "NUM7";
case 72: return "NUM8";
case 73: return "NUM9";
case 74: return "NUM-";
case 75: return "NUM4";
case 76: return "NUM5";
case 77: return "NUM6";
case 78: return "NUM+";
case 79: return "NUM1";
case 80: return "NUM2";
case 81: return "NUM3";
case 82: return "NUM0";
case 83: return "NUM.";
case 87: return "F11";
case 88: return "F12";
case 156: return "NUMENTER";
case 157: return "RCTRL";
case 181: return "NUM/";
case 184: return "RALT";
case 197: return "PAUSE";
case 199: return "HOME";
case 200: return "UP";
case 201: return "PGUP";
case 203: return "LEFT";
case 205: return "RIGHT";
case 207: return "END";
case 208: return "DOWN";
case 209: return "PGDN";
case 210: return "INSERT";
case 211: return "DELETE";
default: return "KEY" + IlIlll;
}
}
private static class lIl implements java.lang.Runnable {
ravens4.module.setting.impl.ButtonSetting button;

@Override
public void run() {
if (button != null) {
button.runMethod();
}
}
}

}
