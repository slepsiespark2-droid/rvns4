package ravens4.module.impl.client.rise;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;
import java.util.regex.Matcher;
import ravens4.script.ScriptDefaults;
import ravens4.script.model.*;
import ravens4.script.packet.clientbound.*;
import ravens4.script.packet.serverbound.*;

/**
 * Rise interface (click GUI, target info, FPS/BPS counters, themes). Originally a runtime script;
 * now compiled with the client and driven by {@link ravens4.module.impl.client.Interface}.
 */
public class Interface extends ScriptDefaults {
    public static final ScriptDefaults.modules modules = new ScriptDefaults.modules("Interface");
    public static final String scriptName = "Interface";

static final String CLIENT_NAME = "Rise";
static final String CLIENT_VERSION = "6.9.7";

static RiseClickGuiModule clickGuiModule;
static RiseInterfaceModule interfaceModule;
static RiseTargetInfoModule targetInfoModule;
static RiseFpsCounterModule fpsCounterModule;
static RiseBpsCounterModule bpsCounterModule;
static RiseNotificationsModule notificationsModule;
static ModernGui modernGui;

static RiseTheme activeTheme = RiseTheme.BLEND;
static RiseLang activeLang = RiseLang.EN_US;

static boolean guiScreenOpen = false;
static boolean prevMouse0 = false;
static boolean prevMouse1 = false;
static boolean prevMouse2 = false;
static boolean prevMouse3 = false;
static boolean prevMouse4 = false;
static int lastGuiMouseX = 0;
static int lastGuiMouseY = 0;
static ModCard bindingTargetCard = null;
static int currentGuiBind = 0;
static boolean prevGuiBindDown = false;
static final StopWatch keyToggleCooldown = new StopWatch();

static final Map<ravens4.module.Module, Boolean> lastModuleStates = new HashMap<ravens4.module.Module, Boolean>();
static boolean moduleStatesInitialized = false;
static Entity lastAttackedTarget = null;
static long lastAttackTimeMs = 0L;

public void onLoad() {
    int savedBind = readSavedBindFromConfig();
    currentGuiBind = savedBind;
    modules.registerButton("Font Prefix", false);
    registerFontSetting();
    registerToggleSettings();
    modules.registerKey("GUI Keybind", savedBind);
    loadPersistedState();
}

public void onEnable() {
    keyToggleCooldown.millis = 0L;
    loadPersistedState();
    ravens4.module.setting.impl.KeySetting scriptKs = findSelfScriptKeySetting();
    if (scriptKs != null) {
        currentGuiBind = Math.max(0, scriptKs.getKey());
    }
    ensureModulesCreated();
    applyGuiBindEverywhere(currentGuiBind, true);
    prevGuiBindDown = isGuiBindPressedRaw();
    registerRenderModules();
    syncModuleStatesSnapshot();
}

public void onDisable() {
    syncClickGuiKeybind();
    prevGuiBindDown = false;
    if (guiScreenOpen) {
        guiScreenOpen = false;
        bindingTargetCard = null;
        if ("EmptyGuiScreen".equals(client.getScreen())) {
            client.closeScreen();
        }
    }
    if (clickGuiModule != null && clickGuiModule.isEnabled()) {
        clickGuiModule.setEnabled(false);
    }
    unregisterRenderModules();
    savePersistedState();
}

public void onPreUpdate() {
    pollGuiKeybindInGame();
    if (interfaceModule != null) {
        interfaceModule.guiUpdate();
    }
    if (clickGuiModule != null) {
        clickGuiModule.guiUpdate();
    }
    if (targetInfoModule != null) {
        targetInfoModule.guiUpdate();
    }

    int bgAlpha = (interfaceModule != null && interfaceModule.shaders.isToggled())
            ? (int) Math.round(interfaceModule.backgroundAlpha.getInput())
            : 150;
    RiseTheme.setBackgroundShade(new Color(0, 0, 0, clampInt(bgAlpha, 0, 255)));

    checkModuleToggleNotifications();
    ToggleToastManager.onTick();

    if (lastAttackedTarget != null) {
        if (client.time() - lastAttackTimeMs > 3000L) {
            lastAttackedTarget = null;
        } else if (lastAttackedTarget.isDead() || lastAttackedTarget.getHealth() <= 0.0f) {
            if (interfaceModule != null && interfaceModule.isEnabled()
                    && interfaceModule.getModeName().equals("Modern")
                    && interfaceModule.modernParticlesOnKill.isToggled()) {
                int[] disp = client.getDisplaySize();
                float cx = disp[0] / 2.0f;
                float cy = disp[1] / 2.0f;
                for (int i = 0; i <= 10; i++) {
                    float vx = (float) ((Math.random() - 0.5) * 4.0);
                    float vy = (float) ((Math.random() - 0.5) * 4.0);
                    KillParticleManager.spawn(new KillParticle(new Vec2f(cx, cy), new Vec2f(vx, vy)));
                }
            }
            lastAttackedTarget = null;
        }
    }

    if (interfaceModule != null && interfaceModule.isEnabled()) {
        InterfaceEngine.onTick(interfaceModule);
    }
    if (bpsCounterModule != null && bpsCounterModule.isEnabled()) {
        bpsCounterModule.onTickUpdate();
    }
    if (targetInfoModule != null && targetInfoModule.isEnabled()) {
        TargetInfoEngine.onTick(targetInfoModule);
    }
}

public boolean onAttackEntity(Entity target, Entity attacker) {
    if (target != null && attacker != null && attacker.isUser) {
        lastAttackedTarget = target;
        lastAttackTimeMs = client.time();
        if (target.isLiving) {
            TargetInfoEngine.onAttack(target);
        }
    }
    return true;
}

public void onRenderWorld(float partialTicks) {
    if (targetInfoModule != null && targetInfoModule.isEnabled() && targetInfoModule.followPlayer.isToggled()) {
        TargetInfoEngine.captureProjectedBounds(partialTicks);
    }
}

public void onRenderTick(float partialTicks) {
    pollGuiKeybindInGame();
    ShaderPipeline.clearAll();
    boolean shadersOn = interfaceModule != null && interfaceModule.shaders.isToggled();

    if (interfaceModule != null && interfaceModule.isEnabled()) {
        InterfaceEngine.onRender2D(interfaceModule, partialTicks);
    }
    if (fpsCounterModule != null && fpsCounterModule.isEnabled()) {
        fpsCounterModule.onRender2D();
    }
    if (bpsCounterModule != null && bpsCounterModule.isEnabled()) {
        bpsCounterModule.onRender2D();
    }
    if (targetInfoModule != null && targetInfoModule.isEnabled()) {
        TargetInfoEngine.onRender2D(targetInfoModule, partialTicks);
    }

    KillParticleManager.renderAndUpdate();
    ToggleToastManager.onRender2D();
    HudDragManager.onRender2D();

    if (!guiScreenOpen && modernGui != null && modernGui.closingRenderActive) {
        modernGui.renderClosingOverlay(lastGuiMouseX, lastGuiMouseY, partialTicks);
    }

    ShaderPipeline.executeAll(shadersOn, getBlurPasses(), getBlurOffset(), getBloomPasses(), getBloomOffset());
    DrawUtil.resetGlState();
}

public void onScreenRender(String screenName, int mouseX, int mouseY, float partialTicks) {
    syncClickGuiKeybind();
    if (!guiScreenOpen) {
        if (isChatScreenName(screenName)) {
            HudDragManager.handleChatMouse(mouseX, mouseY);
        } else {
            HudDragManager.releaseDrag();
        }
        return;
    }
    HudDragManager.releaseDrag();
    if (!"EmptyGuiScreen".equals(screenName)) {
        guiScreenOpen = false;
        bindingTargetCard = null;
        prevMouse0 = false;
        prevMouse1 = false;
        prevMouse2 = false;
        prevMouse3 = false;
        prevMouse4 = false;
        if (clickGuiModule != null && clickGuiModule.isEnabled()) {
            clickGuiModule.setEnabled(false);
        }
        return;
    }

    lastGuiMouseX = mouseX;
    lastGuiMouseY = mouseY;

    boolean m0 = keybinds.isMouseDown(0);
    boolean m1 = keybinds.isMouseDown(1);
    boolean m2 = keybinds.isMouseDown(2);
    boolean m3 = keybinds.isMouseDown(3);
    boolean m4 = keybinds.isMouseDown(4);
    // The wheel comes from the mouse events (see feedWheel): the client's own MouseHelper drains Mouse.getDWheel()
    // every frame, so reading it here would always give 0.
    int drainedWheel = keybinds.getScroll();
    int wheel = takePendingWheel();
    if (wheel == 0) {
        wheel = drainedWheel;
    }

    ensureGuisCreated();
    int pressedExtraMouse = -1;
    if (m2 && !prevMouse2) pressedExtraMouse = 2;
    else if (m3 && !prevMouse3) pressedExtraMouse = 3;
    else if (m4 && !prevMouse4) pressedExtraMouse = 4;

    if (pressedExtraMouse >= 2 && bindingTargetCard != null) {
        bindingTargetCard.mod.setBind(1000 + pressedExtraMouse);
        bindingTargetCard = null;
        keyToggleCooldown.reset();
        syncClickGuiKeybind();
        prevMouse0 = m0;
        prevMouse1 = m1;
        prevMouse2 = m2;
        prevMouse3 = m3;
        prevMouse4 = m4;
        return;
    }

    if (pressedExtraMouse >= 2
            && currentGuiBind == (1000 + pressedExtraMouse)
            && keyToggleCooldown.finished(80L)
            && !isAnyGuiTextFocused()) {
        prevMouse0 = m0;
        prevMouse1 = m1;
        prevMouse2 = m2;
        prevMouse3 = m3;
        prevMouse4 = m4;
        closeRiseClickGui();
        return;
    }

    if (m0 && !prevMouse0) modernGui.mouseClicked(mouseX, mouseY, 0);
    if (m1 && !prevMouse1) modernGui.mouseClicked(mouseX, mouseY, 1);
    if (m2 && !prevMouse2) modernGui.mouseClicked(mouseX, mouseY, 2);
    if (m3 && !prevMouse3) modernGui.mouseClicked(mouseX, mouseY, 3);
    if (m4 && !prevMouse4) modernGui.mouseClicked(mouseX, mouseY, 4);
    if (!m0 && prevMouse0) modernGui.mouseReleased(mouseX, mouseY, 0);
    if (!m1 && prevMouse1) modernGui.mouseReleased(mouseX, mouseY, 1);
    if (!m2 && prevMouse2) modernGui.mouseReleased(mouseX, mouseY, 2);
    prevMouse0 = m0;
    prevMouse1 = m1;
    prevMouse2 = m2;
    prevMouse3 = m3;
    prevMouse4 = m4;
    syncClickGuiKeybind();

    ShaderPipeline.clearAll();
    boolean shadersOn = interfaceModule != null && interfaceModule.shaders.isToggled();

    modernGui.drawGui(mouseX, mouseY, partialTicks, wheel, shadersOn);

    ShaderPipeline.executeAll(shadersOn, getBlurPasses(), getBlurOffset(), getBloomPasses(), getBloomOffset());
    DrawUtil.resetGlState();
}

public boolean onKeyPress(char typedChar, int keyCode) {
    if (!guiScreenOpen || !"EmptyGuiScreen".equals(client.getScreen())) {
        return true;
    }

    boolean pressed = keyCode == 0 ? typedChar >= ' ' : keybinds.isKeyDown(keyCode);
    if (!pressed) {
        return true;
    }

    if (bindingTargetCard != null) {
        if (keyCode == 1 || keyCode == 14 || keyCode == 211) {
            bindingTargetCard.mod.setBind(0);
        } else if (keyCode > 0) {
            bindingTargetCard.mod.setBind(keyCode);
        }
        bindingTargetCard = null;
        keyToggleCooldown.reset();
        syncClickGuiKeybind();
        return false;
    }

    ensureGuisCreated();
    syncClickGuiKeybind();

    if (currentGuiBind > 0 && currentGuiBind < 1000
            && keyCode == currentGuiBind
            && keyToggleCooldown.finished(50L)
            && !isAnyGuiTextFocused()) {
        closeRiseClickGui();
        return false;
    }

    if (keyCode == 1) {
        if (modernGui.unfocusAnyText()) {
            syncClickGuiKeybind();
            return false;
        }
        closeRiseClickGui();
        return false;
    }

    modernGui.keyTyped(typedChar, keyCode);
    syncClickGuiKeybind();
    return false;
}

void onKey(String keyName, int keyCode, boolean state, boolean inGui) {
    syncClickGuiKeybind();
    if (!state || inGui || clickGuiModule == null) {
        return;
    }
    int bind = currentGuiBind;
    if (bind > 0 && bind < 1000 && keyCode == bind && client.getScreen().isEmpty()) {
        if (keyToggleCooldown.finished(100L)) {
            keyToggleCooldown.reset();
            if (!clickGuiModule.isEnabled()) {
                clickGuiModule.enable();
            } else {
                openRiseClickGui();
            }
        }
    }
}

public boolean onMouse(int button, boolean state, int x, int y) {
    syncClickGuiKeybind();
    if (clickGuiModule == null || guiScreenOpen || !client.getScreen().isEmpty()) {
        return true;
    }
    int bind = currentGuiBind;
    if (bind < 1000) {
        return true;
    }
    boolean matched = false;
    if (button == -1) {
        if (bind == 1069 && state) matched = true;
        else if (bind == 1070 && !state) matched = true;
    } else if (state && button >= 0 && bind == (1000 + button)) {
        matched = true;
    }
    if (matched && keyToggleCooldown.finished(100L)) {
        keyToggleCooldown.reset();
        if (!clickGuiModule.isEnabled()) {
            clickGuiModule.enable();
        } else {
            openRiseClickGui();
        }
        return false;
    }
    return true;
}

public void onGuiUpdate(String guiName, boolean opened) {
    if (!opened && isChatScreenName(guiName)) {
        HudDragManager.releaseDrag();
    }
    if (!opened && "EmptyGuiScreen".equals(guiName) && guiScreenOpen) {
        guiScreenOpen = false;
        bindingTargetCard = null;
        prevMouse0 = false;
        prevMouse1 = false;
        prevMouse2 = false;
        prevMouse3 = false;
        prevMouse4 = false;
        if (modernGui != null) {
            modernGui.onGuiClosed();
        }
        savePersistedState();
    }
}

static void ensureModulesCreated() {
    if (interfaceModule == null) {
        interfaceModule = new RiseInterfaceModule();
        String savedEnabled = config.get("rise_interface_enabled");
        if (savedEnabled == null || "true".equalsIgnoreCase(savedEnabled.trim())) {
            interfaceModule.setEnabled(true);
        }
        interfaceModule.guiUpdate();
    }
    if (clickGuiModule == null) {
        clickGuiModule = new RiseClickGuiModule();
    }
    if (targetInfoModule == null) {
        targetInfoModule = new RiseTargetInfoModule();
        targetInfoModule.loadConfig();
        targetInfoModule.guiUpdate();
    }
    if (fpsCounterModule == null) {
        fpsCounterModule = new RiseFpsCounterModule();
        fpsCounterModule.loadConfig();
    }
    if (bpsCounterModule == null) {
        bpsCounterModule = new RiseBpsCounterModule();
        bpsCounterModule.loadConfig();
    }
    if (notificationsModule == null) {
        notificationsModule = new RiseNotificationsModule();
        notificationsModule.loadConfig();
    }
    ensureGuisCreated();
}

static void ensureGuisCreated() {
    if (modernGui == null) {
        modernGui = new ModernGui();
    }
}

static void addRenderModIfAbsent(ravens4.module.ModuleManager mm, List<ravens4.module.Module> list, ravens4.module.Module mod) {
    if (mod == null) return;
    if (!list.contains(mod)) {
        mm.addModule(mod);
    }
    if (mod.isEnabled() && !ravens4.module.ModuleManager.organizedModules.contains(mod)) {
        ravens4.module.ModuleManager.organizedModules.add(mod);
    }
}

static void removeRenderMod(ravens4.module.ModuleManager mm, ravens4.module.Module mod) {
    if (mod == null) return;
    mm.getModules().remove(mod);
    ravens4.module.ModuleManager.organizedModules.remove(mod);
}

static void registerRenderModules() {
    ravens4.module.ModuleManager mm = ravens4.Raven.getModuleManager();
    if (mm == null) {
        return;
    }
    List<ravens4.module.Module> list = mm.getModules();
    addRenderModIfAbsent(mm, list, clickGuiModule);
    addRenderModIfAbsent(mm, list, interfaceModule);
    addRenderModIfAbsent(mm, list, targetInfoModule);
    addRenderModIfAbsent(mm, list, fpsCounterModule);
    addRenderModIfAbsent(mm, list, bpsCounterModule);
    addRenderModIfAbsent(mm, list, notificationsModule);
    Collections.sort(list, new Comparator<ravens4.module.Module>() {
        public int compare(ravens4.module.Module a, ravens4.module.Module b) {
            return a.getName().compareTo(b.getName());
        }
    });
    reloadRenderCategoryInRavenGui();
    ravens4.script.ScriptDefaults.reloadModules();
    InterfaceEngine.rebuildEntries();
    if (modernGui != null) {
        modernGui.rebuildModuleComponents();
    }
}

static void unregisterRenderModules() {
    ravens4.module.ModuleManager mm = ravens4.Raven.getModuleManager();
    if (mm != null) {
        removeRenderMod(mm, clickGuiModule);
        removeRenderMod(mm, interfaceModule);
        removeRenderMod(mm, targetInfoModule);
        removeRenderMod(mm, fpsCounterModule);
        removeRenderMod(mm, bpsCounterModule);
        removeRenderMod(mm, notificationsModule);
    }
    reloadRenderCategoryInRavenGui();
    ravens4.script.ScriptDefaults.reloadModules();
}

static void reloadRenderCategoryInRavenGui() {
    if (!"ClickGui".equals(client.getScreen()) || client.getPlayer() == null) {
        return;
    }
    ravens4.module.ModuleManager mm = ravens4.Raven.getModuleManager();
    if (mm == null) {
        return;
    }
    for (ravens4.module.Module m : mm.getModules()) {
        if (m != null && "Gui".equals(m.getName()) && m != clickGuiModule) {
            client.closeScreen();
            m.enable();
            break;
        }
    }
}

static void openRiseClickGui() {
    if (!toggleOn(showClickGuiSetting)) {
        return;
    }
    pendingWheel = 0;
    ensureGuisCreated();
    prevMouse0 = keybinds.isMouseDown(0);
    prevMouse1 = keybinds.isMouseDown(1);
    prevMouse2 = keybinds.isMouseDown(2);
    prevMouse3 = keybinds.isMouseDown(3);
    prevMouse4 = keybinds.isMouseDown(4);
    prevGuiBindDown = true;
    bindingTargetCard = null;
    keyToggleCooldown.reset();
    guiScreenOpen = true;

    int[] disp = client.getDisplaySize();
    int[] rawMouse = keybinds.getMousePosition();
    if (disp[2] > 0) {
        lastGuiMouseX = rawMouse[0] / disp[2];
        lastGuiMouseY = disp[1] - rawMouse[1] / disp[2] - 1;
    }

    modernGui.initGui();
    render.showGui();
}

static void closeRiseClickGui() {
    guiScreenOpen = false;
    bindingTargetCard = null;
    prevGuiBindDown = true;
    keyToggleCooldown.reset();
    savePersistedState();
    if (modernGui != null) {
        modernGui.onGuiClosed();
        modernGui.closingRenderActive = true;
    }
    if ("EmptyGuiScreen".equals(client.getScreen())) {
        client.closeScreen();
    }
}

static boolean isAnyGuiTextFocused() {
    return modernGui != null && modernGui.hasActiveTextInput();
}

static void syncModuleStatesSnapshot() {
    lastModuleStates.clear();
    for (ravens4.module.Module m : getAllClientModules()) {
        lastModuleStates.put(m, Boolean.valueOf(m.isEnabled()));
    }
    moduleStatesInitialized = true;
}

static void checkModuleToggleNotifications() {
    List<ravens4.module.Module> all = getAllClientModules();
    if (!moduleStatesInitialized) {
        for (ravens4.module.Module m : all) {
            lastModuleStates.put(m, Boolean.valueOf(m.isEnabled()));
        }
        moduleStatesInitialized = true;
        return;
    }
    boolean notify = notificationsModule != null && notificationsModule.isEnabled();
    for (ravens4.module.Module m : all) {
        if (m == clickGuiModule) {
            continue;
        }
        boolean cur = m.isEnabled();
        Boolean prev = lastModuleStates.get(m);
        if (prev != null && prev.booleanValue() != cur) {
            if (notify) {
                ToggleToastManager.push("Toggled", "Toggled " + m.getName() + " " + (cur ? "on" : "off"), 900);
            }
        }
        lastModuleStates.put(m, Boolean.valueOf(cur));
    }
}

static List<ravens4.module.Module> getAllClientModules() {
    List<ravens4.module.Module> result = new ArrayList<ravens4.module.Module>();
    if (ravens4.Raven.getModuleManager() != null) {
        for (ravens4.module.Module m : ravens4.Raven.getModuleManager().getModules()) {
            if (m != null && m.moduleCategory() != ravens4.module.Module.category.profiles) {
                result.add(m);
            }
        }
    }
    if (ravens4.Raven.scriptManager != null && ravens4.Raven.scriptManager.scripts != null) {
        for (ravens4.module.Module sm : selfCandidates()) {
            if (sm != null && !result.contains(sm)) {
                result.add(sm);
            }
        }
    }
    return result;
}

static int getBlurPasses() {
    if (interfaceModule == null) return 2;
    return clampInt((int) Math.round(interfaceModule.blurCompression.getInput()), 1, 3);
}

static float getBlurOffset() {
    if (interfaceModule == null) return 2.0f;
    return Math.max(0.5f, (float) (interfaceModule.blurRadius.getInput() / 6.0));
}

static int getBloomPasses() {
    if (interfaceModule == null) return 3;
    return clampInt((int) Math.round(interfaceModule.bloomCompression.getInput()) + 1, 2, 4);
}

static float getBloomOffset() {
    if (interfaceModule == null) return 2.0f;
    return Math.max(0.5f, (float) (interfaceModule.bloomRadius.getInput() / 7.0));
}

static List<ravens4.module.Module> selfCandidates() {
    List<ravens4.module.Module> out = new ArrayList<ravens4.module.Module>();
    if (ravens4.module.ModuleManager.interfaceModule != null) {
        out.add(ravens4.module.ModuleManager.interfaceModule);
    }
    if (ravens4.Raven.scriptManager != null && ravens4.Raven.scriptManager.scripts != null) {
        out.addAll(ravens4.Raven.scriptManager.scripts.values());
    }
    return out;
}

static int readSavedBindFromConfig() {
    try {
        String savedBind = config.get("rise_clickgui_bind");
        if (savedBind != null && !savedBind.trim().isEmpty()) {
            return Math.max(0, Integer.parseInt(savedBind.trim()));
        }
    } catch (Exception ignored) {
    }
    return 0;
}

static boolean isGuiBindPressedRaw() {
    if (currentGuiBind <= 0) {
        return false;
    }
    try {
        if (modules.getKeyPressed(scriptName, "GUI Keybind")) {
            return true;
        }
    } catch (Exception ignored) {
    }
    if (currentGuiBind < 1000) {
        return keybinds.isKeyDown(currentGuiBind);
    }
    if (currentGuiBind >= 1000 && currentGuiBind < 1069) {
        return keybinds.isMouseDown(currentGuiBind - 1000);
    }
    return false;
}

static void pollGuiKeybindInGame() {
    syncClickGuiKeybind();
    if (clickGuiModule == null) {
        prevGuiBindDown = false;
        return;
    }
    String screen = client.getScreen();
    if (guiScreenOpen || (screen != null && !screen.isEmpty())) {
        prevGuiBindDown = isGuiBindPressedRaw();
        return;
    }
    boolean down = isGuiBindPressedRaw();
    if (!toggleOn(showClickGuiSetting)) {
        prevGuiBindDown = down;
        return;
    }
    if (down && !prevGuiBindDown && keyToggleCooldown.finished(100L)) {
        prevGuiBindDown = true;
        keyToggleCooldown.reset();
        if (!clickGuiModule.isEnabled()) {
            clickGuiModule.enable();
        } else {
            openRiseClickGui();
        }
        return;
    }
    prevGuiBindDown = down;
}

static ravens4.module.setting.impl.KeySetting findSelfScriptKeySetting() {
    try {
        if (ravens4.Raven.scriptManager != null && ravens4.Raven.scriptManager.scripts != null) {
            ravens4.module.setting.impl.KeySetting fallback = null;
            for (ravens4.module.Module sm : selfCandidates()) {
                if (sm == null) continue;
                boolean hasFontPrefix = false;
                ravens4.module.setting.impl.KeySetting foundKs = null;
                for (ravens4.module.setting.Setting s : sm.getSettings()) {
                    if ("Font Prefix".equals(s.getName())) {
                        hasFontPrefix = true;
                    } else if (s instanceof ravens4.module.setting.impl.KeySetting
                            && "GUI Keybind".equals(s.getName())) {
                        foundKs = (ravens4.module.setting.impl.KeySetting) s;
                    }
                }
                if (foundKs != null && scriptName.equals(sm.getName())) {
                    return foundKs;
                }
                if (foundKs != null && hasFontPrefix) {
                    fallback = foundKs;
                }
            }
            return fallback;
        }
    } catch (Exception ignored) {
    }
    return null;
}

static void applyGuiBindEverywhere(int nextBind, boolean persist) {
    int safe = Math.max(0, nextBind);
    currentGuiBind = safe;
    ravens4.module.setting.impl.KeySetting scriptKs = findSelfScriptKeySetting();
    if (scriptKs != null && scriptKs.getKey() != safe) {
        scriptKs.setKey(safe);
    }
    if (clickGuiModule != null) {
        if (clickGuiModule.keybindSetting.getKey() != safe) {
            clickGuiModule.keybindSetting.setKey(safe);
        }
        if (clickGuiModule.getKeycode() != safe) {
            clickGuiModule.setBind(safe);
        }
    }
    if (persist) {
        try {
            config.set("rise_clickgui_bind", String.valueOf(safe));
        } catch (Exception ignored) {
        }
    }
}

static void syncClickGuiKeybind() {
    ravens4.module.setting.impl.KeySetting scriptKs = findSelfScriptKeySetting();
    if (scriptKs != null && scriptKs.getKey() != currentGuiBind) {
        applyGuiBindEverywhere(scriptKs.getKey(), true);
        return;
    }
    if (clickGuiModule != null) {
        if (clickGuiModule.keybindSetting.getKey() != currentGuiBind) {
            applyGuiBindEverywhere(clickGuiModule.keybindSetting.getKey(), true);
            return;
        }
        if (clickGuiModule.getKeycode() != currentGuiBind) {
            applyGuiBindEverywhere(clickGuiModule.getKeycode(), true);
        }
    }
}

public static String[] themeNames() {
    RiseTheme[] all = RiseTheme.values();
    String[] names = new String[all.length];
    for (int i = 0; i < all.length; i++) {
        names[i] = all[i].themeName;
    }
    return names;
}

public static int activeThemeIndex() {
    return activeTheme == null ? 0 : activeTheme.ordinal();
}

public static void setActiveThemeIndex(int index) {
    RiseTheme[] all = RiseTheme.values();
    if (index < 0 || index >= all.length) {
        return;
    }
    activeTheme = all[index];
    savePersistedState();
}

/** {first, second} colours of a theme as RGB ints; rainbow themes sample the rainbow. */
public static int[] themeRgb(int index) {
    RiseTheme[] all = RiseTheme.values();
    RiseTheme t = all[Math.max(0, Math.min(all.length - 1, index))];
    return new int[]{t.firstColor().getRGB() & 0xFFFFFF, t.secondColor().getRGB() & 0xFFFFFF};
}

public static boolean themeIsRainbow(int index) {
    RiseTheme[] all = RiseTheme.values();
    return all[Math.max(0, Math.min(all.length - 1, index))].rainbow;
}

/** Animated accent colour of the active theme (RGB). */
public static int themeAccentRgb(double position) {
    return activeTheme.accentAt(position, 0.0).getRGB() & 0xFFFFFF;
}

static int pendingWheel = 0;

/** Called by the Interface module for every mouse event; collects the wheel while a Rise GUI is open. */
public static void feedWheel(int delta) {
    if (delta != 0 && guiScreenOpen) {
        pendingWheel += delta;
    }
}

static int takePendingWheel() {
    int value = pendingWheel;
    pendingWheel = 0;
    return value;
}

static void loadPersistedState() {
    try {
        String savedTheme = config.get("rise_theme");
        if (savedTheme != null && !savedTheme.trim().isEmpty()) {
            RiseTheme found = RiseTheme.byName(savedTheme.trim());
            if (found != null) {
                activeTheme = found;
            }
        }
        String savedLang = config.get("rise_lang");
        if (savedLang != null && !savedLang.trim().isEmpty()) {
            RiseLang lang = RiseLang.byCode(savedLang.trim());
            if (lang != null) {
                activeLang = lang;
            }
        }
    } catch (Exception ignored) {
    }
}

static void savePersistedState() {
    try {
        if (activeTheme != null) {
            config.set("rise_theme", activeTheme.themeName);
        }
        if (activeLang != null) {
            config.set("rise_lang", activeLang.code);
        }
        if (interfaceModule != null) {
            config.set("rise_interface_enabled", String.valueOf(interfaceModule.isEnabled()));
        }
        if (targetInfoModule != null) {
            targetInfoModule.saveConfig();
        }
        if (fpsCounterModule != null) {
            fpsCounterModule.saveConfig();
        }
        if (bpsCounterModule != null) {
            bpsCounterModule.saveConfig();
        }
        if (notificationsModule != null) {
            notificationsModule.saveConfig();
        }
        config.set("rise_clickgui_bind", String.valueOf(currentGuiBind));
        if (modernGui != null && modernGui.position.x >= 0 && modernGui.position.y >= 0) {
            config.set("rise_gui_x", String.valueOf((int) modernGui.position.x));
            config.set("rise_gui_y", String.valueOf((int) modernGui.position.y));
        }
    } catch (Exception ignored) {
    }
}

static boolean isChatScreenName(String scr) {
    if (scr == null || scr.isEmpty()) {
        return false;
    }
    String lower = scr.toLowerCase();
    return lower.contains("chat") || "guisleepmp".equals(lower);
}

static boolean isChatScreenOpen() {
    return isChatScreenName(client.getScreen());
}

static double getInterfaceRounding() {
    if (interfaceModule != null) {
        return interfaceModule.roundingRadius.getInput();
    }
    return 5.0;
}

static double readConfigDouble(String key, double defVal) {
    try {
        String s = config.get(key);
        if (s != null && !s.trim().isEmpty()) {
            return Double.parseDouble(s.trim());
        }
    } catch (Exception ignored) {
    }
    return defVal;
}

static boolean readConfigBool(String key, boolean defVal) {
    try {
        String s = config.get(key);
        if (s != null && !s.trim().isEmpty()) {
            return "true".equalsIgnoreCase(s.trim());
        }
    } catch (Exception ignored) {
    }
    return defVal;
}

static ravens4.module.setting.impl.SliderSetting fontSetting = null;

static ravens4.module.setting.impl.ButtonSetting showClickGuiSetting = null;
static ravens4.module.setting.impl.ButtonSetting showHudSetting = null;
static ravens4.module.setting.impl.ButtonSetting showWatermarkSetting = null;

static void registerToggleSettings() {
    ravens4.module.Module host = ravens4.module.ModuleManager.interfaceModule;
    if (host == null || showClickGuiSetting != null) {
        return;
    }
    host.registerSetting(showClickGuiSetting = new ravens4.module.setting.impl.ButtonSetting("ClickGUI", true));
    host.registerSetting(showHudSetting = new ravens4.module.setting.impl.ButtonSetting("HUD", true));
    host.registerSetting(showWatermarkSetting = new ravens4.module.setting.impl.ButtonSetting("Watermark", true));
}

static boolean toggleOn(ravens4.module.setting.impl.ButtonSetting setting) {
    return setting == null || setting.isToggled();
}

static void registerFontSetting() {
    ravens4.module.Module host = ravens4.module.ModuleManager.interfaceModule;
    if (host == null || fontSetting != null) {
        return;
    }
    fontSetting = new ravens4.module.setting.impl.SliderSetting("Font", 0, ravens4.utility.font.FontManager.getHudFontOptions());
    host.registerSetting(fontSetting);
}

/** Selected custom font family, or null when the Minecraft font is selected. */
static String currentFontFamily() {
    try {
        if (fontSetting == null) {
            return null;
        }
        String[] opts = fontSetting.getOptions();
        if (opts == null || opts.length == 0) {
            return null;
        }
        String family = opts[clampInt((int) Math.round(fontSetting.getInput()), 0, opts.length - 1)];
        return ravens4.utility.font.FontManager.isMinecraftFont(family) ? null : family;
    } catch (Exception e) {
        return null;
    }
}

static boolean isFontPrefixEnabled() {
    try {
        return modules.getButton(scriptName, "Font Prefix");
    } catch (Exception e) {
        return false;
    }
}

static String applyFontPrefix(String text) {
    if (text == null || text.isEmpty()) {
        return "";
    }
    if (isFontPrefixEnabled() && !text.startsWith("{g}")) {
        return "{g}" + text;
    }
    return text;
}

static int clampInt(int val, int min, int max) {
    return Math.max(min, Math.min(max, val));
}

static float clampFloat(float val, float min, float max) {
    return Math.max(min, Math.min(max, val));
}

static double clampDouble(double val, double min, double max) {
    return Math.max(min, Math.min(max, val));
}

static double lerpDouble(double start, double end, double factor) {
    return start + factor * (end - start);
}

static double snapStep(double val, double step) {
    if (step <= 0.0) return val;
    double snapped = Math.round(val / step) * step;
    return Math.round(snapped * 1000.0) / 1000.0;
}

static final class Vec2f {
    float x;
    float y;

    Vec2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    void set(float x, float y) {
        this.x = x;
        this.y = y;
    }
}

static final class Vec2d {
    double x;
    double y;

    Vec2d(double x, double y) {
        this.x = x;
        this.y = y;
    }
}

static final class StopWatch {
    long millis = client.time();

    boolean finished(long delay) {
        return client.time() - this.millis >= delay;
    }

    void reset() {
        this.millis = client.time();
    }

    long elapsed() {
        return Math.max(0L, client.time() - this.millis);
    }

    void setTime(long time) {
        this.millis = time;
    }
}

static enum Easing {
    LINEAR,
    EASE_IN_QUAD,
    EASE_OUT_QUAD,
    EASE_IN_OUT_QUAD,
    EASE_OUT_CUBIC,
    EASE_IN_OUT_CUBIC,
    EASE_OUT_QUART,
    EASE_OUT_QUINT,
    EASE_IN_SINE,
    EASE_OUT_SINE,
    EASE_IN_EXPO,
    EASE_OUT_EXPO,
    EASE_IN_OUT_EXPO,
    EASE_OUT_CIRC,
    EASE_OUT_ELASTIC,
    EASE_IN_BACK;

    double apply(double x) {
        x = clampDouble(x, 0.0, 1.0);
        switch (this) {
            case LINEAR:
                return x;
            case EASE_IN_QUAD:
                return x * x;
            case EASE_OUT_QUAD:
                return x * (2.0 - x);
            case EASE_IN_OUT_QUAD:
                return x < 0.5 ? 2.0 * x * x : -1.0 + (4.0 - 2.0 * x) * x;
            case EASE_OUT_CUBIC: {
                double t = x - 1.0;
                return t * t * t + 1.0;
            }
            case EASE_IN_OUT_CUBIC: {
                if (x < 0.5) {
                    return 4.0 * x * x * x;
                }
                double t = 2.0 * x - 2.0;
                return (x - 1.0) * t * t + 1.0;
            }
            case EASE_OUT_QUART: {
                double t = x - 1.0;
                return 1.0 - t * t * t * t;
            }
            case EASE_OUT_QUINT: {
                double t = x - 1.0;
                return 1.0 + t * t * t * t * t;
            }
            case EASE_IN_SINE:
                return 1.0 - Math.cos(x * Math.PI / 2.0);
            case EASE_OUT_SINE:
                return Math.sin(x * Math.PI / 2.0);
            case EASE_IN_EXPO:
                return x == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * x - 10.0);
            case EASE_OUT_EXPO:
                return x == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * x);
            case EASE_IN_OUT_EXPO:
                return x == 0.0 ? 0.0 : (x == 1.0 ? 1.0 : (x < 0.5 ? Math.pow(2.0, 20.0 * x - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * x + 10.0)) / 2.0));
            case EASE_OUT_CIRC: {
                double t = x - 1.0;
                return Math.sqrt(Math.max(0.0, 1.0 - t * t));
            }
            case EASE_OUT_ELASTIC:
                return x == 0.0 ? 0.0 : (x == 1.0 ? 1.0 : Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * 2.0943951023931953) * 0.5 + 1.0);
            case EASE_IN_BACK:
                return 2.70158 * x * x * x - 1.70158 * x * x;
            default:
                return x;
        }
    }
}

static final class Animation {
    Easing easing;
    long duration;
    long startTime;
    double startValue;
    double destinationValue;
    double value;
    boolean finished;

    Animation(Easing easing, long duration) {
        this.easing = easing;
        this.duration = Math.max(1L, duration);
        this.startTime = client.time();
    }

    void run(double destination) {
        long now = client.time();
        if (this.destinationValue != destination) {
            this.destinationValue = destination;
            this.reset();
        } else {
            this.finished = (now - this.duration) > this.startTime;
            if (this.finished) {
                this.value = destination;
                return;
            }
        }
        double progress = (double) (now - this.startTime) / (double) Math.max(1L, this.duration);
        double result = this.easing.apply(progress);
        if (this.value > destination) {
            this.value = this.startValue - (this.startValue - destination) * result;
        } else {
            this.value = this.startValue + (destination - this.startValue) * result;
        }
    }

    void reset() {
        this.startTime = client.time();
        this.startValue = this.value;
        this.finished = false;
    }

    void setValue(double v) {
        this.value = v;
        this.startValue = v;
        this.destinationValue = v;
    }

    void setDuration(long d) {
        this.duration = Math.max(1L, d);
    }

    void setEasing(Easing e) {
        this.easing = e;
    }
}

static final class Palette {
    static final Color BACK_MAIN = new Color(23, 26, 33, 254);
    static final Color BACK_SIDEBAR = new Color(18, 20, 25, 255);
    static final Color TEXT_BRIGHT = new Color(255, 255, 255, 255);
    static final Color TEXT_SUB = new Color(255, 255, 255, 220);
    static final Color TEXT_MUTED = new Color(255, 255, 255, 130);
    static final Color CARD_BG = new Color(0, 0, 0, 50);

    static Color withAlpha(Color c, int alpha) {
        int a = clampInt(alpha, 0, 255);
        if (c.getAlpha() == a) return c;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    static int rgbWithAlpha(Color c, int alpha) {
        return withAlpha(c, alpha).getRGB();
    }
}

static enum KeyColor {
    RED(new Color(255, 50, 50)),
    ORANGE(new Color(255, 128, 50)),
    YELLOW(new Color(255, 255, 50)),
    LIME(new Color(128, 255, 50)),
    DARK_GREEN(new Color(50, 128, 50)),
    AQUA(new Color(50, 200, 255)),
    DARK_BLUE(new Color(50, 100, 200)),
    PURPLE(new Color(128, 50, 255)),
    PINK(new Color(255, 128, 255)),
    GRAY(new Color(100, 100, 110));

    final Color color;

    KeyColor(Color color) {
        this.color = color;
    }
}

static enum RiseTheme {
    AUBERGINE("Aubergine", new Color(170, 7, 107), new Color(97, 4, 95), null, false, KeyColor.PURPLE, KeyColor.RED),
    AQUA("Aqua", new Color(185, 250, 255), new Color(79, 199, 200), null, false, KeyColor.AQUA),
    BANANA("Banana", new Color(253, 236, 177), new Color(255, 255, 255), null, false, KeyColor.YELLOW),
    BLEND("Blend", new Color(71, 148, 253), new Color(71, 253, 160), null, false, KeyColor.AQUA, KeyColor.LIME),
    BLOSSOM("Blossom", new Color(226, 208, 249), new Color(49, 119, 115), null, false, KeyColor.PINK, KeyColor.GRAY),
    BUBBLEGUM("Bubblegum", new Color(243, 145, 216), new Color(152, 165, 243), null, false, KeyColor.PINK, KeyColor.PURPLE),
    CANDY_CANE("Candy Cane", new Color(255, 0, 0), new Color(255, 255, 255), null, false, KeyColor.RED),
    CHERRY("Cherry", new Color(187, 55, 125), new Color(251, 211, 233), null, false, KeyColor.RED, KeyColor.PURPLE, KeyColor.PINK),
    CHRISTMAS("Christmas", new Color(255, 64, 64), new Color(255, 255, 255), new Color(64, 255, 64), false, KeyColor.RED, KeyColor.LIME),
    CORAL("Coral", new Color(244, 168, 150), new Color(52, 133, 151), null, false, KeyColor.PINK, KeyColor.ORANGE, KeyColor.DARK_BLUE),
    DIGITAL_HORIZON("Digital Horizon", new Color(95, 195, 228), new Color(229, 93, 135), null, false, KeyColor.AQUA, KeyColor.RED, KeyColor.PINK),
    EXPRESS("Express", new Color(173, 83, 137), new Color(60, 16, 83), null, false, KeyColor.PURPLE, KeyColor.PINK),
    LIME_WATER("Lime Water", new Color(18, 255, 247), new Color(179, 255, 171), null, false, KeyColor.AQUA, KeyColor.LIME),
    LUSH("Lush", new Color(168, 224, 99), new Color(86, 171, 47), null, false, KeyColor.LIME, KeyColor.DARK_GREEN),
    HALOGEN("Halogen", new Color(255, 65, 108), new Color(255, 75, 43), null, false, KeyColor.RED, KeyColor.ORANGE),
    HYPER("Hyper", new Color(236, 110, 173), new Color(52, 148, 230), null, false, KeyColor.PINK, KeyColor.DARK_BLUE, KeyColor.AQUA),
    MAGIC("Magic", new Color(74, 0, 224), new Color(142, 45, 226), null, false, KeyColor.DARK_BLUE, KeyColor.PURPLE),
    MAY("May", new Color(238, 79, 238), new Color(253, 219, 245), null, false, KeyColor.PINK, KeyColor.PURPLE),
    ORANGE_JUICE("Orange Juice", new Color(252, 74, 26), new Color(247, 183, 51), null, false, KeyColor.ORANGE, KeyColor.YELLOW),
    PASTEL("Pastel", new Color(243, 155, 178), new Color(207, 196, 243), null, false, KeyColor.PINK),
    PUMPKIN("Pumpkin", new Color(241, 166, 98), new Color(255, 216, 169), new Color(227, 139, 42), false, KeyColor.ORANGE),
    SATIN("Satin", new Color(215, 60, 67), new Color(140, 23, 39), null, false, KeyColor.RED),
    SNOWY_SKY("Snowy Sky", new Color(1, 171, 179), new Color(234, 234, 234), new Color(18, 232, 232), false, KeyColor.AQUA, KeyColor.GRAY),
    STEEL_FADE("Steel Fade", new Color(66, 134, 244), new Color(55, 59, 68), null, false, KeyColor.DARK_BLUE, KeyColor.GRAY),
    SUNDAE("Sundae", new Color(206, 74, 126), new Color(122, 44, 77), null, false, KeyColor.PINK, KeyColor.PURPLE, KeyColor.RED),
    SUNKIST("Sunkist", new Color(242, 201, 76), new Color(242, 153, 74), null, false, KeyColor.YELLOW, KeyColor.ORANGE),
    WATER("Water", new Color(12, 232, 199), new Color(12, 163, 232), null, false, KeyColor.AQUA, KeyColor.DARK_BLUE),
    LEGACY("Legacy", new Color(7393023), new Color(7393023), null, false, KeyColor.AQUA, KeyColor.DARK_BLUE),
    WINTER("Winter", Color.WHITE, Color.WHITE, null, false, KeyColor.GRAY),
    PEONY("Peony", new Color(226, 208, 249), new Color(207, 171, 255), null, false, KeyColor.PINK, KeyColor.GRAY),
    SHADOW("Shadow", new Color(97, 131, 255), new Color(206, 212, 255), null, false, KeyColor.AQUA),
    WOOD("Wood", new Color(79, 109, 81), new Color(170, 139, 87), new Color(240, 235, 206), false, KeyColor.DARK_GREEN),
    CREIDA("Creida", new Color(-11644304).brighter().brighter(), new Color(-11644304).darker(), null, false, KeyColor.GRAY),
    CREIDA_TWO("Creida Two", new Color(-6632725), new Color(-8406042).darker(), null, false, KeyColor.GRAY),
    GOTHIC("Gothic", new Color(31, 30, 30), new Color(196, 190, 190), null, false, KeyColor.GRAY),
    RUE("Rue", new Color(234, 118, 176), new Color(31, 30, 30), null, false, KeyColor.PINK),
    PURPLE_THEME("Purple", new Color(5391249), new Color(5391249).brighter(), null, false),
    RAINBOW("Rainbow", null, null, null, true),
    NORD("Nord", new Color(143, 188, 187), new Color(163, 190, 140), new Color(236, 239, 244), false, KeyColor.AQUA, KeyColor.GRAY);

    static Color backgroundShade = new Color(0, 0, 0, 110);

    final String themeName;
    final Color c1;
    final Color c2;
    final Color c3;
    final boolean triColor;
    final boolean rainbow;
    final List<KeyColor> keyColors;

    RiseTheme(String name, Color c1, Color c2, Color c3, boolean rainbow, KeyColor... keys) {
        this.themeName = name;
        this.c1 = c1;
        this.c2 = c2;
        this.c3 = c3 != null ? c3 : c1;
        this.triColor = (c3 != null) || rainbow;
        this.rainbow = rainbow;
        this.keyColors = new ArrayList<KeyColor>(Arrays.asList(keys));
    }

    Color firstColor() {
        return rainbow ? accentAt(0.0, 0.0) : c1;
    }

    Color secondColor() {
        return rainbow ? accentAt(0.0, 50.0) : c2;
    }

    Color thirdColor() {
        return rainbow ? accentAt(0.0, 100.0) : c3;
    }

    Color accentColor() {
        return accentAt(0.0, 0.0);
    }

    Color dropShadowColor() {
        return new Color(0, 0, 0, 190);
    }

    double blendFactor(double x, double y) {
        return Math.sin((double) client.time() / 600.0 + x * 0.005 + y * 0.06) * 0.5 + 0.5;
    }

    Color accentAt(double x, double y) {
        if (rainbow) {
            return DrawUtil.rainbowColor((int) ((x + y) * 10.0));
        }
        if (triColor) {
            double f = blendFactor(x, y);
            if (f <= 0.5) {
                return DrawUtil.mixColors(secondColor(), firstColor(), f * 2.0);
            }
            return DrawUtil.mixColors(thirdColor(), secondColor(), (f - 0.5) * 2.0);
        }
        return DrawUtil.mixColors(firstColor(), secondColor(), blendFactor(x, y));
    }

    static void setBackgroundShade(Color c) {
        backgroundShade = c;
    }

    static RiseTheme byName(String name) {
        for (RiseTheme t : values()) {
            if (t.themeName.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}

static final class RiseFont {
    static final RiseFont VANILLA = new RiseFont(18, true);
    static final RiseFont F10 = new RiseFont(10, false);
    static final RiseFont F11 = new RiseFont(11, false);
    static final RiseFont F12 = new RiseFont(12, false);
    static final RiseFont F13 = new RiseFont(13, false);
    static final RiseFont F15 = new RiseFont(15, false);
    static final RiseFont F16 = new RiseFont(16, false);
    static final RiseFont F17 = new RiseFont(17, false);
    static final RiseFont F18 = new RiseFont(18, false);
    static final RiseFont F20 = new RiseFont(20, false);
    static final RiseFont F22 = new RiseFont(22, false);
    static final RiseFont F24 = new RiseFont(24, false);
    static final RiseFont F32 = new RiseFont(32, false);
    static final RiseFont F36 = new RiseFont(36, false);
    static final RiseFont F42 = new RiseFont(42, false);

    private static final Map<Integer, RiseFont> dynamicCache = new HashMap<Integer, RiseFont>();

    final int ptSize;
    final float scale;
    final boolean vanillaRef;

    RiseFont(int ptSize, boolean vanillaRef) {
        this.ptSize = Math.max(6, ptSize);

        this.scale = vanillaRef ? 1.0f : (this.ptSize / 18.0f);
        this.vanillaRef = vanillaRef;
    }

    static RiseFont of(int ptSize) {
        int clamped = clampInt(ptSize, 6, 64);
        switch (clamped) {
            case 10: return F10;
            case 11: return F11;
            case 12: return F12;
            case 13: return F13;
            case 15: return F15;
            case 16: return F16;
            case 17: return F17;
            case 18: return F18;
            case 20: return F20;
            case 22: return F22;
            case 24: return F24;
            case 32: return F32;
            case 36: return F36;
            case 42: return F42;
            default: {
                RiseFont f = dynamicCache.get(Integer.valueOf(clamped));
                if (f == null) {
                    f = new RiseFont(clamped, false);
                    dynamicCache.put(Integer.valueOf(clamped), f);
                }
                return f;
            }
        }
    }

    float width(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        ravens4.utility.font.RavenFontRenderer custom = customRenderer();
        if (custom != null) {
            return custom.getStringWidth(text);
        }
        String formatted = applyFontPrefix(text);
        return render.getFontWidth(formatted) * this.scale;
    }

    float height() {
        ravens4.utility.font.RavenFontRenderer custom = customRenderer();
        if (custom != null) {
            return custom.getFontHeight();
        }
        return render.getFontHeight() * this.scale;
    }

    /** Bundled-font renderer for this size, or null to use the Minecraft font. */
    private ravens4.utility.font.RavenFontRenderer customRenderer() {
        String family = currentFontFamily();
        if (family == null) {
            return null;
        }
        return ravens4.utility.font.FontManager.getRendererForHeight(family, this.ptSize / 2.0f);
    }

    void draw(String text, double x, double y, int color) {
        drawInternal(text, x, y, color, false);
    }

    void drawShadow(String text, double x, double y, int color) {
        drawInternal(text, x, y, color, true);
    }

    void drawCentered(String text, double x, double y, int color) {
        drawInternal(text, x - width(text) / 2.0, y, color, false);
    }

    void drawCenteredShadow(String text, double x, double y, int color) {
        drawInternal(text, x - width(text) / 2.0, y, color, true);
    }

    void drawRight(String text, double x, double y, int color) {
        drawInternal(text, x - width(text), y, color, false);
    }

    private void drawInternal(String text, double x, double y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int alpha = (color >>> 24) & 0xFF;

        if (alpha <= 4) {
            return;
        }
        ravens4.utility.RenderUtils.setAlphaLimit(0.0f);
        ravens4.utility.font.RavenFontRenderer custom = customRenderer();
        if (custom != null) {
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            custom.drawString(text, (float) x, (float) y, color, shadow);
            net.minecraft.client.renderer.GlStateManager.disableBlend();
        } else {
            render.text2d(applyFontPrefix(text), (float) x, (float) y, this.scale, color, shadow);
        }
        gl.resetColor();
    }
}

static final class DrawUtil {

    private static final int[] POTION_ICON_INDEX = new int[]{
            -1,
            0,
            1,
            2,
            3,
            4,
            -1,
            -1,
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            18,
            19,
            20,
            21,
            22,
            -1
    };

    static boolean hovered(double x, double y, double width, double height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    static Color mixColors(Color c1, Color c2, double factor) {
        double f = clampDouble(factor, 0.0, 1.0);
        double inv = 1.0 - f;
        int r = clampInt((int) (c1.getRed() * f + c2.getRed() * inv), 0, 255);
        int g = clampInt((int) (c1.getGreen() * f + c2.getGreen() * inv), 0, 255);
        int b = clampInt((int) (c1.getBlue() * f + c2.getBlue() * inv), 0, 255);
        return new Color(r, g, b);
    }

    static Color mixColorsWithAlpha(Color c1, Color c2, double factor) {
        double f = clampDouble(factor, 0.0, 1.0);
        double inv = 1.0 - f;
        int r = clampInt((int) (c1.getRed() * f + c2.getRed() * inv), 0, 255);
        int g = clampInt((int) (c1.getGreen() * f + c2.getGreen() * inv), 0, 255);
        int b = clampInt((int) (c1.getBlue() * f + c2.getBlue() * inv), 0, 255);
        int a = clampInt((int) (c1.getAlpha() * f + c2.getAlpha() * inv), 0, 255);
        return new Color(r, g, b, a);
    }

    static Color rainbowColor(int offset) {
        double hue = Math.ceil((double) (client.time() + (long) offset) / 10.0);
        hue %= 360.0;
        return Color.getHSBColor((float) (hue / 360.0), 0.6f, 1.0f);
    }

    static void rect(double x, double y, double width, double height, Color color) {
        if (color == null || width <= 0.0 || height <= 0.0 || color.getAlpha() <= 0) {
            return;
        }
        ravens4.utility.RenderUtils.drawRect(x, y, x + width, y + height, color.getRGB());
    }

    static void roundedRect(double x, double y, double width, double height, double radius, Color color) {
        roundedRectEdges(x, y, width, height, radius, color, true, true, true, true);
    }

    static void roundedRectEdges(double x, double y, double width, double height, double radius, Color color,
                                 boolean leftTop, boolean rightTop, boolean rightBottom, boolean leftBottom) {
        if (color == null || width <= 0.0 || height <= 0.0 || color.getAlpha() <= 0) {
            return;
        }
        float r = (float) Math.max(0.0, Math.min(radius, Math.min(width, height) / 2.0));
        ravens4.utility.shader.RoundedUtils.drawRoundedRectRise(
                (float) x, (float) y, (float) width, (float) height, r,
                color.getRGB(), leftTop, rightTop, rightBottom, leftBottom
        );
        gl.resetColor();
    }

    static void roundedOutline(double x, double y, double width, double height, double radius, double thickness, Color outlineColor) {
        if (outlineColor == null || width <= 0.0 || height <= 0.0 || outlineColor.getAlpha() <= 0) {
            return;
        }
        ravens4.utility.shader.RoundedUtils.drawRoundOutline(
                (float) x, (float) y, (float) width, (float) height,
                (float) radius, (float) thickness, new Color(0, 0, 0, 0), outlineColor
        );
        gl.resetColor();
    }

    static void roundedGradientH(double x, double y, double width, double height, double radius, Color left, Color right) {
        if (left == null || right == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        if (left.getAlpha() <= 0 && right.getAlpha() <= 0) {
            return;
        }
        ravens4.utility.shader.RoundedUtils.drawGradientHorizontal(
                (float) x, (float) y, (float) width, (float) height, (float) radius, left, right
        );
        gl.resetColor();
    }

    static void roundedGradientV(double x, double y, double width, double height, double radius, Color top, Color bottom) {
        if (top == null || bottom == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        if (top.getAlpha() <= 0 && bottom.getAlpha() <= 0) {
            return;
        }
        ravens4.utility.shader.RoundedUtils.drawGradientVertical(
                (float) x, (float) y, (float) width, (float) height, (float) radius, top, bottom
        );
        gl.resetColor();
    }

    static void gradientRectH(double x, double y, double width, double height, Color left, Color right) {
        if (left == null || right == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        if (left.getAlpha() <= 0 && right.getAlpha() <= 0) {
            return;
        }
        ravens4.utility.RenderUtils.drawHorizontalGradientRect(
                (float) x, (float) y, (float) (x + width), (float) (y + height), left.getRGB(), right.getRGB()
        );
        gl.resetColor();
    }

    static void gradientRectV(double x, double y, double width, double height, Color top, Color bottom) {
        if (top == null || bottom == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        if (top.getAlpha() <= 0 && bottom.getAlpha() <= 0) {
            return;
        }
        ravens4.utility.RenderUtils.drawVerticalGradientRect(
                (float) x, (float) y, (float) (x + width), (float) (y + height), top.getRGB(), bottom.getRGB()
        );
        gl.resetColor();
    }

    static void circle(double centerX, double centerY, double radius, Color color) {
        if (color == null || radius <= 0.0 || color.getAlpha() <= 0) {
            return;
        }
        roundedRect(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0, radius, color);
    }

    static void dropShadow(int loops, double x, double y, double width, double height, double opacity, double radius) {
        ravens4.utility.RenderUtils.setAlphaLimit(0.0f);
        for (float f = 0.0f; f <= (float) loops / 2.0f; f += 0.5f) {
            int a = clampInt((int) Math.max(1.0, (opacity - (double) f * 1.2) / 5.5), 0, 255);
            roundedRect(x - f / 2.0, y - f / 2.0, width + f, height + f, radius, new Color(0, 0, 0, a));
        }
    }

    static void rainbowRectangle(double x, double y, double width, double height) {
        if (width <= 0.0 || height <= 0.0) return;
        gl.push();
        gl.blend(true);
        gl.texture2d(false);
        gl.begin(7);
        for (double px = x; px < x + width; px += 0.5) {
            double next = Math.min(x + width, px + 0.5);
            Color c = Color.getHSBColor((float) ((px - x) / width), 1.0f, 1.0f);
            gl.color(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, 1.0f);
            gl.vertex2((float) px, (float) y);
            gl.vertex2((float) px, (float) (y + height));
            gl.vertex2((float) next, (float) (y + height));
            gl.vertex2((float) next, (float) y);
        }
        gl.end();
        gl.texture2d(true);
        gl.blend(false);
        gl.resetColor();
        gl.pop();
    }

    static void drawThemeGradientText(RiseFont font, String text, double x, double y, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        float offset = 0.0f;
        Color first = activeTheme.firstColor();
        Color second = activeTheme.secondColor();
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            Color c = mixColors(first, second, Math.sin((double) i * 0.095) * 0.5 + 0.5);
            if (shadow) {
                font.drawShadow(ch, x + offset, y, c.getRGB());
            } else {
                font.draw(ch, x + offset, y, c.getRGB());
            }
            offset += font.width(ch) + 0.5f;
        }
    }

    static void enableScissor(double x, double y, double width, double height) {
        int[] disp = client.getDisplaySize();
        int sw = disp[0];
        int sh = disp[1];
        int scale = Math.max(1, disp[2]);
        double clampedW = Math.max(0.0, width);
        double clampedH = Math.max(0.0, height);
        int sx = (int) Math.floor(x * scale);
        int sy = (int) Math.floor((sh - (y + clampedH)) * scale);
        int sWidth = Math.max(0, (int) Math.ceil(clampedW * scale));
        int sHeight = Math.max(0, (int) Math.ceil(clampedH * scale));
        gl.scissor(true);
        gl.scissor(sx, sy, sWidth, sHeight);
    }

    static void disableScissor() {
        gl.scissor(false);
    }

    static void drawPotionIcon(int potionId, float x, float y, float size) {
        if (potionId < 0 || potionId >= POTION_ICON_INDEX.length) {
            return;
        }
        int idx = POTION_ICON_INDEX[potionId];
        if (idx < 0) {
            return;
        }
        int u = (idx % 8) * 18;
        int v = 198 + (idx / 8) * 18;
        float u1 = u / 256.0f;
        float v1 = v / 256.0f;
        float u2 = (u + 18) / 256.0f;
        float v2 = (v + 18) / 256.0f;

        gl.push();
        gl.texture2d(true);
        gl.blend(true);
        gl.alpha(true);
        gl.resetColor();
        gl.bindTexture("textures/gui/container/inventory.png");
        gl.begin(7);
        gl.texCoord2(u1, v1);
        gl.vertex2(x, y);
        gl.texCoord2(u1, v2);
        gl.vertex2(x, y + size);
        gl.texCoord2(u2, v2);
        gl.vertex2(x + size, y + size);
        gl.texCoord2(u2, v1);
        gl.vertex2(x + size, y);
        gl.end();
        gl.pop();
    }

    static void drawCategoryIcon(SidebarCategory cat, float x, float y, int color) {
        int a = (color >>> 24) & 0xFF;
        if (a <= 4) return;
        float r = ((color >>> 16) & 0xFF) / 255.0f;
        float g = ((color >>> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float af = a / 255.0f;
        Color col = new Color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, a);

        switch (cat) {
            case SEARCH: {

                ravens4.utility.RenderUtils.draw2DCircle(x + 3.2f, y + 3.0f, 2.5f, 18, 1.3f, r, g, b, af);
                render.line2D(x + 5.0, y + 4.8, x + 7.2, y + 7.0, 1.4f, color);
                break;
            }
            case COMBAT: {

                render.line2D(x + 1.2, y + 6.8, x + 6.8, y + 1.2, 1.5f, color);
                render.line2D(x + 1.6, y + 4.8, x + 3.6, y + 6.8, 1.3f, color);
                render.line2D(x + 1.0, y + 7.2, x + 2.2, y + 6.0, 1.5f, color);
                break;
            }
            case MOVEMENT: {

                render.line2D(x + 1.2, y + 1.8, x + 4.0, y + 4.2, 1.4f, color);
                render.line2D(x + 4.0, y + 4.2, x + 1.2, y + 6.6, 1.4f, color);
                render.line2D(x + 4.2, y + 1.8, x + 7.0, y + 4.2, 1.4f, color);
                render.line2D(x + 7.0, y + 4.2, x + 4.2, y + 6.6, 1.4f, color);
                break;
            }
            case PLAYER: {

                circle(x + 4.0, y + 2.4, 1.6, col);
                roundedRectEdges(x + 1.4, y + 4.6, 5.2, 2.8, 1.8, col, true, true, false, false);
                break;
            }
            case RENDER: {

                ravens4.utility.RenderUtils.draw2DCircle(x + 4.0f, y + 4.2f, 2.8f, 18, 1.2f, r, g, b, af);
                circle(x + 4.0, y + 4.2, 1.1, col);
                break;
            }
            case EXPLOIT: {

                render.line2D(x + 4.8, y + 0.8, x + 2.0, y + 4.3, 1.4f, color);
                render.line2D(x + 2.0, y + 4.3, x + 5.6, y + 4.3, 1.3f, color);
                render.line2D(x + 5.6, y + 4.3, x + 3.0, y + 7.6, 1.4f, color);
                break;
            }
            case GHOST: {

                ravens4.utility.RenderUtils.draw2DCircle(x + 4.0f, y + 4.2f, 2.7f, 18, 1.2f, r, g, b, af);
                render.line2D(x + 4.0, y + 0.8, x + 4.0, y + 2.2, 1.2f, color);
                render.line2D(x + 4.0, y + 6.2, x + 4.0, y + 7.6, 1.2f, color);
                render.line2D(x + 0.6, y + 4.2, x + 2.0, y + 4.2, 1.2f, color);
                render.line2D(x + 6.0, y + 4.2, x + 7.4, y + 4.2, 1.2f, color);
                break;
            }
            case CONFIGS: {

                render.line2D(x + 0.8, y + 1.8, x + 7.2, y + 1.8, 1.1f, color);
                render.line2D(x + 0.8, y + 4.2, x + 7.2, y + 4.2, 1.1f, color);
                render.line2D(x + 0.8, y + 6.6, x + 7.2, y + 6.6, 1.1f, color);
                circle(x + 2.6, y + 1.8, 1.2, col);
                circle(x + 5.4, y + 4.2, 1.2, col);
                circle(x + 3.4, y + 6.6, 1.2, col);
                break;
            }
            case SCRIPTS: {

                render.line2D(x + 2.8, y + 2.0, x + 0.8, y + 4.2, 1.3f, color);
                render.line2D(x + 0.8, y + 4.2, x + 2.8, y + 6.4, 1.3f, color);
                render.line2D(x + 5.2, y + 2.0, x + 7.2, y + 4.2, 1.3f, color);
                render.line2D(x + 7.2, y + 4.2, x + 5.2, y + 6.4, 1.3f, color);
                break;
            }
            case THEMES: {

                circle(x + 2.5, y + 2.7, 1.3, col);
                circle(x + 5.5, y + 2.7, 1.3, col);
                circle(x + 4.0, y + 5.6, 1.3, col);
                break;
            }
            case LANGUAGE: {

                ravens4.utility.RenderUtils.draw2DCircle(x + 4.0f, y + 4.2f, 3.0f, 18, 1.2f, r, g, b, af);
                render.line2D(x + 1.1, y + 4.2, x + 6.9, y + 4.2, 1.0f, color);
                render.line2D(x + 4.0, y + 1.3, x + 4.0, y + 7.1, 1.0f, color);
                break;
            }
        }
        gl.resetColor();
    }

    private static final Map<String, Image> skinImageByUuid = new HashMap<String, Image>();
    private static final Map<String, Long> skinRetryTimeByUuid = new HashMap<String, Long>();

    private static String extractSkinUrlFromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        int skinIdx = json.indexOf("\"SKIN\"");
        int searchFrom = skinIdx >= 0 ? skinIdx : 0;
        int urlKey = json.indexOf("\"url\"", searchFrom);
        if (urlKey < 0) return null;
        int colon = json.indexOf(':', urlKey + 5);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 <= q1) return null;
        String url = json.substring(q1 + 1, q2).trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return null;
    }

    private static float bindEntitySkinAndGetVScale(Entity target, boolean inWorld, float hp) {
        if (target == null || !inWorld || hp <= 0.0f) {
            gl.bindTexture("textures/entity/skeleton/skeleton.png");
            return 32.0f;
        }
        if (target.isPlayer) {
            String uuid = null;
            try {
                uuid = target.getUUID();
            } catch (Exception ignored) {
            }
            if (uuid != null && !uuid.isEmpty()) {
                Image img = skinImageByUuid.get(uuid);
                if (img == null) {
                    Long retryAt = skinRetryTimeByUuid.get(uuid);
                    long now = client.time();
                    if (retryAt == null || now >= retryAt.longValue()) {
                        if (skinImageByUuid.size() > 128) {
                            skinImageByUuid.clear();
                            skinRetryTimeByUuid.clear();
                        }
                        skinRetryTimeByUuid.put(uuid, Long.valueOf(now + 3000L));
                        try {
                            NetworkPlayer np = target.getNetworkPlayer();
                            if (np != null) {
                                String skinJson = np.getSkinData();
                                String url = extractSkinUrlFromJson(skinJson);
                                if (url != null) {
                                    img = new Image(url, true);
                                    skinImageByUuid.put(uuid, img);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (img != null && img.isLoaded()) {
                    if (img.textureId == -1) {
                        render.image(img, -10000.0f, -10000.0f, 0.0f, 0.0f);
                    }
                    if (img.textureId != -1) {
                        gl.bindTextureId(img.textureId);
                        if (img.height > 0 && img.width == img.height * 2) {
                            return 32.0f;
                        }
                        return 64.0f;
                    }
                }
                boolean slim = (uuid.hashCode() & 1) != 0;
                gl.bindTexture(slim ? "textures/entity/alex.png" : "textures/entity/steve.png");
                return 64.0f;
            }
            gl.bindTexture("textures/entity/steve.png");
            return 64.0f;
        }
        String t = target.type == null ? "" : target.type.toLowerCase();
        if (t.contains("zombie") && !t.contains("pig")) {
            gl.bindTexture("textures/entity/zombie/zombie.png");
            return 64.0f;
        }
        if (t.contains("skeleton")) {
            gl.bindTexture("textures/entity/skeleton/skeleton.png");
            return 32.0f;
        }
        if (t.contains("creeper")) {
            gl.bindTexture("textures/entity/creeper/creeper.png");
            return 32.0f;
        }
        if (t.contains("spider")) {
            gl.bindTexture("textures/entity/spider/spider.png");
            return 32.0f;
        }
        if (t.contains("enderman")) {
            gl.bindTexture("textures/entity/enderman/enderman.png");
            return 32.0f;
        }
        gl.bindTexture("textures/entity/steve.png");
        return 64.0f;
    }

    private static void drawRoundedUvFan(double x, double y, double w, double h, double radius,
                                         float u1, float v1, float u2, float v2) {
        if (w <= 0.0 || h <= 0.0) return;
        double r = Math.max(0.0, Math.min(radius, Math.min(w, h) / 2.0));
        double cx = x + w * 0.5;
        double cy = y + h * 0.5;
        float uSpan = u2 - u1;
        float vSpan = v2 - v1;

        gl.begin(6);
        gl.texCoord2(u1 + 0.5f * uSpan, v1 + 0.5f * vSpan);
        gl.vertex2((float) cx, (float) cy);

        if (r <= 0.25) {
            gl.texCoord2(u1, v1);
            gl.vertex2((float) x, (float) y);
            gl.texCoord2(u1, v2);
            gl.vertex2((float) x, (float) (y + h));
            gl.texCoord2(u2, v2);
            gl.vertex2((float) (x + w), (float) (y + h));
            gl.texCoord2(u2, v1);
            gl.vertex2((float) (x + w), (float) y);
            gl.texCoord2(u1, v1);
            gl.vertex2((float) x, (float) y);
            gl.end();
            return;
        }

        double[] cornerX = new double[]{x + r, x + r, x + w - r, x + w - r};
        double[] cornerY = new double[]{y + r, y + h - r, y + h - r, y + r};
        double[] startDeg = new double[]{90.0, 180.0, 270.0, 0.0};
        int segs = 10;
        double firstVx = 0.0;
        double firstVy = 0.0;
        boolean firstSet = false;

        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= segs; i++) {
                double ang = Math.toRadians(startDeg[c] + (90.0 * i / segs));
                double vx = cornerX[c] + Math.cos(ang) * r;
                double vy = cornerY[c] - Math.sin(ang) * r;
                if (!firstSet) {
                    firstVx = vx;
                    firstVy = vy;
                    firstSet = true;
                }
                float tu = (float) (u1 + ((vx - x) / w) * uSpan);
                float tv = (float) (v1 + ((vy - y) / h) * vSpan);
                gl.texCoord2(tu, tv);
                gl.vertex2((float) vx, (float) vy);
            }
        }
        if (firstSet) {
            float tu = (float) (u1 + ((firstVx - x) / w) * uSpan);
            float tv = (float) (v1 + ((firstVy - y) / h) * vSpan);
            gl.texCoord2(tu, tv);
            gl.vertex2((float) firstVx, (float) firstVy);
        }
        gl.end();
    }

    static void drawRoundedSkinHead(Entity target, boolean inWorld, float hp,
                                    double x, double y, double size, double radius,
                                    Color tintColor, boolean drawBorder, double borderRadius) {
        if (size <= 0.5) return;
        ravens4.utility.RenderUtils.setAlphaLimit(0.0f);
        gl.push();
        gl.texture2d(true);
        gl.blend(true);
        gl.alpha(true);
        float vScale = bindEntitySkinAndGetVScale(target, inWorld, hp);
        float cr = tintColor != null ? tintColor.getRed() / 255.0f : 1.0f;
        float cg = tintColor != null ? tintColor.getGreen() / 255.0f : 1.0f;
        float cb = tintColor != null ? tintColor.getBlue() / 255.0f : 1.0f;
        float ca = tintColor != null ? tintColor.getAlpha() / 255.0f : 1.0f;
        gl.color(cr, cg, cb, ca);

        drawRoundedUvFan(x, y, size, size, radius, 8.0f / 64.0f, 8.0f / vScale, 16.0f / 64.0f, 16.0f / vScale);
        if (target != null && target.isPlayer && inWorld && hp > 0.0f) {
            gl.color(cr, cg, cb, ca);
            drawRoundedUvFan(x, y, size, size, radius, 40.0f / 64.0f, 8.0f / vScale, 48.0f / 64.0f, 16.0f / vScale);
        }
        gl.resetColor();
        gl.pop();

        if (drawBorder) {
            roundedOutline(x - 0.5, y - 0.5, size + 1.0, size + 1.0, borderRadius, 0.5, new Color(0, 0, 0, 40));
        }
    }

    static void drawFlatSkinHead(Entity target, boolean inWorld, float hp, double x, double y, double size) {
        if (size <= 0.5) return;
        ravens4.utility.RenderUtils.setAlphaLimit(0.0f);
        gl.push();
        gl.texture2d(true);
        gl.blend(true);
        gl.alpha(true);
        float vScale = bindEntitySkinAndGetVScale(target, inWorld, hp);
        gl.color(1.0f, 1.0f, 1.0f, 1.0f);

        float u1 = 8.0f / 64.0f;
        float v1 = 8.0f / vScale;
        float u2 = 16.0f / 64.0f;
        float v2 = 16.0f / vScale;
        gl.begin(7);
        gl.texCoord2(u1, v1);
        gl.vertex2((float) x, (float) y);
        gl.texCoord2(u1, v2);
        gl.vertex2((float) x, (float) (y + size));
        gl.texCoord2(u2, v2);
        gl.vertex2((float) (x + size), (float) (y + size));
        gl.texCoord2(u2, v1);
        gl.vertex2((float) (x + size), (float) y);
        gl.end();

        if (target != null && target.isPlayer && inWorld && hp > 0.0f) {
            float hu1 = 40.0f / 64.0f;
            float hu2 = 48.0f / 64.0f;
            gl.begin(7);
            gl.texCoord2(hu1, v1);
            gl.vertex2((float) x, (float) y);
            gl.texCoord2(hu1, v2);
            gl.vertex2((float) x, (float) (y + size));
            gl.texCoord2(hu2, v2);
            gl.vertex2((float) (x + size), (float) (y + size));
            gl.texCoord2(hu2, v1);
            gl.vertex2((float) (x + size), (float) y);
            gl.end();
        }
        gl.resetColor();
        gl.pop();
    }

    static void roundedGradientOutline(double x, double y, double w, double h, double radius, double thickness, Color leftCol, Color rightCol) {
        if (leftCol == null || rightCol == null || w <= 0.0 || h <= 0.0 || thickness <= 0.0) return;
        double r = Math.max(0.5, Math.min(radius, Math.min(w, h) / 2.0));
        double halfT = Math.max(0.35, thickness * 0.5);
        double[] cornerX = new double[]{x + r, x + r, x + w - r, x + w - r};
        double[] cornerY = new double[]{y + r, y + h - r, y + h - r, y + r};
        double[] startDeg = new double[]{90.0, 180.0, 270.0, 0.0};
        int segs = 10;

        ravens4.utility.RenderUtils.setAlphaLimit(0.0f);
        gl.push();
        gl.blend(true);
        gl.texture2d(false);
        gl.lineSmooth(true);
        gl.begin(8);

        double firstInX = 0.0, firstInY = 0.0, firstOutX = 0.0, firstOutY = 0.0;
        Color firstCol = leftCol;
        boolean firstSet = false;

        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= segs; i++) {
                double ang = Math.toRadians(startDeg[c] + (90.0 * i / segs));
                double nx = Math.cos(ang);
                double ny = -Math.sin(ang);
                double inX = cornerX[c] + nx * Math.max(0.0, r - halfT);
                double inY = cornerY[c] + ny * Math.max(0.0, r - halfT);
                double outX = cornerX[c] + nx * (r + halfT);
                double outY = cornerY[c] + ny * (r + halfT);
                double t = clampDouble((cornerX[c] + nx * r - x) / Math.max(1.0, w), 0.0, 1.0);
                Color mc = mixColorsWithAlpha(rightCol, leftCol, t);
                if (!firstSet) {
                    firstInX = inX;
                    firstInY = inY;
                    firstOutX = outX;
                    firstOutY = outY;
                    firstCol = mc;
                    firstSet = true;
                }
                gl.color(mc.getRed() / 255.0f, mc.getGreen() / 255.0f, mc.getBlue() / 255.0f, mc.getAlpha() / 255.0f);
                gl.vertex2((float) outX, (float) outY);
                gl.vertex2((float) inX, (float) inY);
            }
        }
        if (firstSet) {
            gl.color(firstCol.getRed() / 255.0f, firstCol.getGreen() / 255.0f, firstCol.getBlue() / 255.0f, firstCol.getAlpha() / 255.0f);
            gl.vertex2((float) firstOutX, (float) firstOutY);
            gl.vertex2((float) firstInX, (float) firstInY);
        }
        gl.end();
        gl.lineSmooth(false);
        gl.texture2d(true);
        gl.blend(false);
        gl.resetColor();
        gl.pop();
    }

    static double scaledBoxX(double centerX, double x, double scale) {
        return centerX + (x - centerX) * scale;
    }

    static double scaledBoxY(double centerY, double y, double scale) {
        return centerY + (y - centerY) * scale;
    }

    static void resetGlState() {
        gl.scissor(false);
        gl.texture2d(true);
        gl.blend(false);
        gl.resetColor();
    }
}

static final class ShaderPipeline {
    private static final List<Runnable> blurTasks = new ArrayList<Runnable>();
    private static final List<Runnable> bloomTasks = new ArrayList<Runnable>();
    private static final List<Runnable> normalLayer0 = new ArrayList<Runnable>();
    private static final List<Runnable> normalLayer1 = new ArrayList<Runnable>();
    private static final List<Runnable> normalLayer2 = new ArrayList<Runnable>();

    static void clearAll() {
        blurTasks.clear();
        bloomTasks.clear();
        normalLayer0.clear();
        normalLayer1.clear();
        normalLayer2.clear();
    }

    static void addBlur(Runnable r) {
        if (r != null) blurTasks.add(r);
    }

    static void addBloom(Runnable r) {
        if (r != null) bloomTasks.add(r);
    }

    static void addNormal(int priority, Runnable r) {
        if (r == null) return;
        if (priority <= 0) normalLayer0.add(r);
        else if (priority == 1) normalLayer1.add(r);
        else normalLayer2.add(r);
    }

    private static net.minecraft.client.shader.Framebuffer blurMaskBuffer = null;
    private static net.minecraft.client.shader.Framebuffer bloomSourceBuffer = null;

    static void executeAll(boolean shadersEnabled, int blurPasses, float blurOffset, int bloomPasses, float bloomOffset) {
        if (shadersEnabled && !blurTasks.isEmpty()) {
            try {
                runBlur(blurPasses, blurOffset);
            } catch (Throwable t) {
                restoreGlState();
            }
        }

        if (shadersEnabled && !bloomTasks.isEmpty()) {
            try {
                runBloom(bloomPasses, bloomOffset);
            } catch (Throwable t) {
                restoreGlState();
            }
        }

        runList(normalLayer0);
        runList(normalLayer1);
        runList(normalLayer2);

        clearAll();
    }

    private static void restoreGlState() {
        net.minecraft.client.Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
        net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.bindTexture(0);
        net.minecraft.client.renderer.GlStateManager.resetColor();
    }

    private static void runTasks(List<Runnable> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            try {
                tasks.get(i).run();
            } catch (Exception ignored) {
            }
        }
    }

    /** Textured quad over the whole screen (scaled gui coordinates), drawn with whatever blend function is set. */
    private static void drawScreenTexture(int texture, float sw, float sh) {
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableAlpha();
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.bindTexture(texture);
        org.lwjgl.opengl.GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
        org.lwjgl.opengl.GL11.glTexCoord2f(0.0f, 1.0f);
        org.lwjgl.opengl.GL11.glVertex2f(0.0f, 0.0f);
        org.lwjgl.opengl.GL11.glTexCoord2f(0.0f, 0.0f);
        org.lwjgl.opengl.GL11.glVertex2f(0.0f, sh);
        org.lwjgl.opengl.GL11.glTexCoord2f(1.0f, 0.0f);
        org.lwjgl.opengl.GL11.glVertex2f(sw, sh);
        org.lwjgl.opengl.GL11.glTexCoord2f(1.0f, 1.0f);
        org.lwjgl.opengl.GL11.glVertex2f(sw, 0.0f);
        org.lwjgl.opengl.GL11.glEnd();
        net.minecraft.client.renderer.GlStateManager.bindTexture(0);
    }

    /**
     * Blur behind the shapes: the shapes are drawn into a mask buffer, the frame is blurred, the blurred
     * picture is multiplied by the mask and put over the screen. Same idea as HUD.java (no mask shader).
     */
    private static void runBlur(int passes, float offset) {
        int[] disp = client.getDisplaySize();
        float sw = disp[0];
        float sh = disp[1];
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();

        gl.scissor(false);
        blurMaskBuffer = ravens4.utility.RenderUtils.createFrameBuffer(blurMaskBuffer);
        blurMaskBuffer.framebufferClear();
        blurMaskBuffer.bindFramebuffer(false);
        runTasks(blurTasks);
        mc.getFramebuffer().bindFramebuffer(true);

        net.minecraft.client.shader.Framebuffer blurred = ravens4.utility.shader.KawaseBlur.blurToBuffer(passes, offset);

        // blurred picture * mask alpha, written into the mask buffer (alpha is kept)
        blurMaskBuffer.bindFramebuffer(false);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_DST_ALPHA, org.lwjgl.opengl.GL11.GL_ZERO,
                org.lwjgl.opengl.GL11.GL_ZERO, org.lwjgl.opengl.GL11.GL_ONE);
        drawScreenTexture(blurred.framebufferTexture, sw, sh);
        mc.getFramebuffer().bindFramebuffer(true);

        // masked blur over the screen (premultiplied by the mask)
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_ONE, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE, org.lwjgl.opengl.GL11.GL_ZERO);
        drawScreenTexture(blurMaskBuffer.framebufferTexture, sw, sh);
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        restoreGlState();
    }

    /**
     * Glow around the shapes: shapes are drawn into a source buffer, blurred, the shapes themselves are
     * cut out of the blur again (only the halo stays) and the halo is added to the screen.
     */
    private static void runBloom(int passes, float offset) {
        int[] disp = client.getDisplaySize();
        float sw = disp[0];
        float sh = disp[1];
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();

        gl.scissor(false);
        bloomSourceBuffer = ravens4.utility.RenderUtils.createFrameBuffer(bloomSourceBuffer);
        bloomSourceBuffer.framebufferClear();
        bloomSourceBuffer.bindFramebuffer(false);
        runTasks(bloomTasks);
        mc.getFramebuffer().bindFramebuffer(true);

        net.minecraft.client.shader.Framebuffer glow = ravens4.utility.shader.KawaseBloom.blurTextureToBuffer(bloomSourceBuffer.framebufferTexture, passes, offset);

        // erase the shapes from the blurred glow
        glow.bindFramebuffer(true);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_ZERO, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ZERO, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawScreenTexture(bloomSourceBuffer.framebufferTexture, sw, sh);
        mc.getFramebuffer().bindFramebuffer(true);

        // add the halo to the screen
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        drawScreenTexture(glow.framebufferTexture, sw, sh);
        restoreGlState();
    }

    private static void runList(List<Runnable> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            try {
                tasks.get(i).run();
            } catch (Exception ignored) {
            }
        }
    }
}

static enum RiseLang {
    EN_US("en_US", "English", "English", new Color(35, 75, 165), Color.WHITE, new Color(200, 40, 50)),
    FR_FR("fr_FR", "French", "Fran\u00e7ais", new Color(0, 85, 164), Color.WHITE, new Color(239, 65, 53)),
    ES_ES("es_ES", "Spanish", "Espa\u00f1ol", new Color(198, 11, 30), new Color(255, 196, 0), new Color(198, 11, 30)),
    SV_SE("sv_SE", "Swedish", "Svenska", new Color(0, 106, 167), new Color(254, 204, 2), new Color(0, 106, 167)),
    PL_PL("pl_PL", "Polish", "Polski", Color.WHITE, new Color(220, 20, 60), new Color(220, 20, 60)),
    RU_RU("ru_RU", "Russian", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439", Color.WHITE, new Color(0, 57, 166), new Color(213, 43, 30)),
    ZH_ZH("zh_ZH", "Chinese", "\u4e2d\u6587", new Color(222, 41, 16), new Color(255, 222, 0), new Color(222, 41, 16));

    final String code;
    final String englishName;
    final String localName;
    final Color flag1;
    final Color flag2;
    final Color flag3;

    RiseLang(String code, String englishName, String localName, Color f1, Color f2, Color f3) {
        this.code = code;
        this.englishName = englishName;
        this.localName = localName;
        this.flag1 = f1;
        this.flag2 = f2;
        this.flag3 = f3;
    }

    static RiseLang byCode(String code) {
        for (RiseLang l : values()) {
            if (l.code.equalsIgnoreCase(code)) return l;
        }
        return EN_US;
    }
}

static final class Loc {
    private static final Map<String, String> MODULE_DESCRIPTIONS = new HashMap<String, String>();

    static {
        MODULE_DESCRIPTIONS.put("ClickGUI", "Allows you to toggle modules and change their settings");
        MODULE_DESCRIPTIONS.put("Interface", "Displays information about the client");
        MODULE_DESCRIPTIONS.put("Target Info", "Displays information about your current target");
        MODULE_DESCRIPTIONS.put("FPS Counter", "Displays your frames per second");
        MODULE_DESCRIPTIONS.put("BPS Counter", "Displays your blocks per second");
        MODULE_DESCRIPTIONS.put("AimAssist", "Smoothly assists your aim towards nearby targets");
        MODULE_DESCRIPTIONS.put("AntiKnockback", "Modifies or cancels incoming knockback velocity");
        MODULE_DESCRIPTIONS.put("Velocity", "Reduces or modifies velocity taken from attacks");
        MODULE_DESCRIPTIONS.put("AutoClicker", "Automatically clicks when holding the mouse button");
        MODULE_DESCRIPTIONS.put("AutoWeapon", "Automatically switches to the strongest weapon");
        MODULE_DESCRIPTIONS.put("Autoblock", "Automatically blocks with your sword during combat");
        MODULE_DESCRIPTIONS.put("ClickAssist", "Adds extra clicks to increase your CPS");
        MODULE_DESCRIPTIONS.put("HitBox", "Expands entity hitboxes for easier hits");
        MODULE_DESCRIPTIONS.put("HitSelect", "Times attacks for optimal combo trades");
        MODULE_DESCRIPTIONS.put("JumpReset", "Jumps upon taking damage to reduce knockback");
        MODULE_DESCRIPTIONS.put("KBDisplacement", "Adjusts knockback displacement vectors");
        MODULE_DESCRIPTIONS.put("KillAura", "Automatically attacks nearby entities");
        MODULE_DESCRIPTIONS.put("KnockbackDelay", "Delays incoming velocity packets");
        MODULE_DESCRIPTIONS.put("Piercing", "Allows attacks through certain obstacles");
        MODULE_DESCRIPTIONS.put("RawInput", "Uses raw mouse input bypassing OS acceleration");
        MODULE_DESCRIPTIONS.put("Reach", "Increases your attack reach distance");
        MODULE_DESCRIPTIONS.put("Reduce", "Reduces sprint slowdown when attacking");
        MODULE_DESCRIPTIONS.put("RodAimbot", "Automatically aims fishing rod throws at targets");
        MODULE_DESCRIPTIONS.put("TPAura", "Teleports to attack distant targets");
        MODULE_DESCRIPTIONS.put("WTap", "Automatically resets sprint to deal more knockback");
        MODULE_DESCRIPTIONS.put("BHop", "Increases movement speed by bunny hopping");
        MODULE_DESCRIPTIONS.put("Boost", "Temporarily boosts your movement speed");
        MODULE_DESCRIPTIONS.put("Dolphin", "Propels you faster while swimming in water");
        MODULE_DESCRIPTIONS.put("Fly", "Grants the ability to fly in survival mode");
        MODULE_DESCRIPTIONS.put("InstantStop", "Stops horizontal momentum instantly");
        MODULE_DESCRIPTIONS.put("InvMove", "Allows movement while inventory screens are open");
        MODULE_DESCRIPTIONS.put("KeepSprint", "Prevents losing sprint when hitting players");
        MODULE_DESCRIPTIONS.put("LongJump", "Allows you to jump much further than normal");
        MODULE_DESCRIPTIONS.put("MovementFix", "Corrects movement direction to match server rotations");
        MODULE_DESCRIPTIONS.put("NoSlow", "Prevents slowdown when using items or blocking");
        MODULE_DESCRIPTIONS.put("NullMove", "Cancels opposing movement key inputs");
        MODULE_DESCRIPTIONS.put("Speed", "Increases your ground and air movement speed");
        MODULE_DESCRIPTIONS.put("Sprint", "Automatically sprints when moving forward");
        MODULE_DESCRIPTIONS.put("Stasis", "Freezes your player position in mid-air");
        MODULE_DESCRIPTIONS.put("StopMotion", "Halts player motion when toggled");
        MODULE_DESCRIPTIONS.put("Teleport", "Teleports you to targeted block positions");
        MODULE_DESCRIPTIONS.put("Timer", "Changes the client game tick speed");
        MODULE_DESCRIPTIONS.put("VClip", "Clips vertically through blocks");
        MODULE_DESCRIPTIONS.put("AntiAFK", "Prevents getting kicked for inactivity");
        MODULE_DESCRIPTIONS.put("AntiFireball", "Automatically deflects incoming fireballs");
        MODULE_DESCRIPTIONS.put("AutoJump", "Automatically jumps at block edges");
        MODULE_DESCRIPTIONS.put("AutoShop", "Quickly purchases items in BedWars shops");
        MODULE_DESCRIPTIONS.put("AutoSwap", "Automatically swaps hotbar items");
        MODULE_DESCRIPTIONS.put("AutoTool", "Selects the best tool when mining blocks");
        MODULE_DESCRIPTIONS.put("BedAura", "Automatically breaks nearby enemy beds");
        MODULE_DESCRIPTIONS.put("BedDefender", "Automatically places defense blocks around your bed");
        MODULE_DESCRIPTIONS.put("BlockIn", "Automatically surrounds yourself with blocks");
        MODULE_DESCRIPTIONS.put("BlockLadder", "Places ladders or blocks to clutch");
        MODULE_DESCRIPTIONS.put("BridgeAssist", "Assists with speedbridging at block edges");
        MODULE_DESCRIPTIONS.put("Clutch", "Automatically places blocks beneath you when falling");
        MODULE_DESCRIPTIONS.put("DelayRemover", "Removes the 1.8 left-click miss penalty delay");
        MODULE_DESCRIPTIONS.put("FastMine", "Speeds up block breaking");
        MODULE_DESCRIPTIONS.put("FastPlace", "Reduces right-click block placement delay");
        MODULE_DESCRIPTIONS.put("Freecam", "Detaches the camera to fly freely around");
        MODULE_DESCRIPTIONS.put("GhostHand", "Interacts with containers through walls");
        MODULE_DESCRIPTIONS.put("HideWindow", "Hides or repositions container windows");
        MODULE_DESCRIPTIONS.put("Inventory", "Manages, sorts and cleans your inventory");
        MODULE_DESCRIPTIONS.put("NoFall", "Prevents or reduces fall damage");
        MODULE_DESCRIPTIONS.put("NoRotate", "Prevents server packets from forcing head rotations");
        MODULE_DESCRIPTIONS.put("SafeWalk", "Prevents walking off the edges of blocks");
        MODULE_DESCRIPTIONS.put("Scaffold", "Automatically places blocks beneath your feet");
        MODULE_DESCRIPTIONS.put("WaterBucket", "Automatically MLGs with a water bucket");
        MODULE_DESCRIPTIONS.put("AntiBot", "Filters out anticheat bots from combat modules");
        MODULE_DESCRIPTIONS.put("Weather", "Customizes world time and weather conditions");
        MODULE_DESCRIPTIONS.put("Backtrack", "Allows hitting opponents at their previous positions");
        MODULE_DESCRIPTIONS.put("Blink", "Chokes outgoing packets to simulate lag");
        MODULE_DESCRIPTIONS.put("FakeLag", "Periodically delays packets to confuse opponents");
        MODULE_DESCRIPTIONS.put("LagRange", "Holds packets to gain a range advantage");
        MODULE_DESCRIPTIONS.put("HUD", "Renders the default Raven module list");
        MODULE_DESCRIPTIONS.put("Notifications", "Displays module toggle notifications");
        MODULE_DESCRIPTIONS.put("PotionHUD", "Displays active potion effects on screen");
        MODULE_DESCRIPTIONS.put("TargetHUD", "Displays combat target health and information");
        MODULE_DESCRIPTIONS.put("Nametags", "Renders enhanced nametags above players");
        MODULE_DESCRIPTIONS.put("PlayerESP", "Highlights players through walls");
        MODULE_DESCRIPTIONS.put("ChestESP", "Highlights storage containers through walls");
        MODULE_DESCRIPTIONS.put("BedESP", "Highlights beds through walls");
        MODULE_DESCRIPTIONS.put("BlockESP", "Highlights selected blocks through walls");
        MODULE_DESCRIPTIONS.put("ItemESP", "Highlights dropped items on the ground");
        MODULE_DESCRIPTIONS.put("MobESP", "Highlights mobs and creatures");
        MODULE_DESCRIPTIONS.put("Chams", "Renders entities through walls with flat shading");
        MODULE_DESCRIPTIONS.put("Tracers", "Draws lines towards nearby players");
        MODULE_DESCRIPTIONS.put("Trajectories", "Predicts projectile flight paths");
        MODULE_DESCRIPTIONS.put("Xray", "Makes non-ore blocks transparent");
        MODULE_DESCRIPTIONS.put("Freelook", "Allows 360-degree camera rotation without turning");
        MODULE_DESCRIPTIONS.put("Gui", "Opens the standard Raven ClickGUI");
        MODULE_DESCRIPTIONS.put("Settings", "Global client configuration options");
    }

    static String tr(String key) {
        if (key == null) return "";
        if ("category.config".equals(key)) return "CaS";
        switch (activeLang) {
            case FR_FR:
                if ("category.search".equals(key)) return "Recherche";
                if ("category.combat".equals(key)) return "Combat";
                if ("category.movement".equals(key)) return "Mouvement";
                if ("category.player".equals(key)) return "Joueur";
                if ("category.render".equals(key)) return "Rendu";
                if ("category.exploit".equals(key)) return "Exploit";
                if ("category.ghost".equals(key)) return "Fant\u00f4me";
                if ("category.script".equals(key)) return "Scripts";
                if ("category.themes".equals(key)) return "Th\u00e8mes";
                if ("category.language".equals(key)) return "Langue";
                if ("ui.search.text".equals(key)) return "Rechercher...";
                if ("ui.themes.text".equals(key)) return "Filtrer les th\u00e8mes";
                if ("ui.language.text".equals(key)) return "Choisir la langue";
                break;
            case ES_ES:
                if ("category.search".equals(key)) return "Buscar";
                if ("category.combat".equals(key)) return "Combate";
                if ("category.movement".equals(key)) return "Movimiento";
                if ("category.player".equals(key)) return "Jugador";
                if ("category.render".equals(key)) return "Visual";
                if ("category.exploit".equals(key)) return "Exploit";
                if ("category.ghost".equals(key)) return "Fantasma";
                if ("category.script".equals(key)) return "Scripts";
                if ("category.themes".equals(key)) return "Temas";
                if ("category.language".equals(key)) return "Idioma";
                if ("ui.search.text".equals(key)) return "Escribe para buscar...";
                if ("ui.themes.text".equals(key)) return "Filtrar temas por color";
                if ("ui.language.text".equals(key)) return "Seleccionar idioma";
                break;
            case SV_SE:
                if ("category.search".equals(key)) return "S\u00f6k";
                if ("category.combat".equals(key)) return "Strid";
                if ("category.movement".equals(key)) return "R\u00f6relse";
                if ("category.player".equals(key)) return "Spelare";
                if ("category.render".equals(key)) return "Visuellt";
                if ("category.exploit".equals(key)) return "Exploit";
                if ("category.ghost".equals(key)) return "Ghost";
                if ("category.script".equals(key)) return "Skript";
                if ("category.themes".equals(key)) return "Teman";
                if ("category.language".equals(key)) return "Spr\u00e5k";
                break;
            case PL_PL:
                if ("category.search".equals(key)) return "Szukaj";
                if ("category.combat".equals(key)) return "Walka";
                if ("category.movement".equals(key)) return "Ruch";
                if ("category.player".equals(key)) return "Gracz";
                if ("category.render".equals(key)) return "Wygl\u0105d";
                if ("category.exploit".equals(key)) return "Exploit";
                if ("category.ghost".equals(key)) return "Duch";
                if ("category.script".equals(key)) return "Skrypty";
                if ("category.themes".equals(key)) return "Motywy";
                if ("category.language".equals(key)) return "J\u0119zyk";
                break;
            case RU_RU:
                if ("category.search".equals(key)) return "Search";
                if ("category.combat".equals(key)) return "Combat";
                if ("category.movement".equals(key)) return "Movement";
                if ("category.player".equals(key)) return "Player";
                if ("category.render".equals(key)) return "Render";
                if ("category.exploit".equals(key)) return "Exploit";
                if ("category.ghost".equals(key)) return "Ghost";
                if ("category.script".equals(key)) return "Scripts";
                if ("category.themes".equals(key)) return "Themes";
                if ("category.language".equals(key)) return "Language";
                break;
            case ZH_ZH:
                if ("category.search".equals(key)) return "Search";
                if ("category.combat".equals(key)) return "Combat";
                if ("category.movement".equals(key)) return "Movement";
                if ("category.player".equals(key)) return "Player";
                if ("category.render".equals(key)) return "Render";
                if ("category.exploit".equals(key)) return "Exploit";
                if ("category.ghost".equals(key)) return "Ghost";
                if ("category.script".equals(key)) return "Scripts";
                if ("category.themes".equals(key)) return "Themes";
                if ("category.language".equals(key)) return "Language";
                break;
            default:
                break;
        }
        if ("category.search".equals(key)) return "Search";
        if ("category.combat".equals(key)) return "Combat";
        if ("category.movement".equals(key)) return "Movement";
        if ("category.player".equals(key)) return "Player";
        if ("category.render".equals(key)) return "Render";
        if ("category.exploit".equals(key)) return "Exploit";
        if ("category.ghost".equals(key)) return "Ghost";
        if ("category.script".equals(key)) return "Scripts";
        if ("category.themes".equals(key)) return "Themes";
        if ("category.language".equals(key)) return "Language";
        if ("ui.search.text".equals(key)) return "Type to search...";
        if ("ui.themes.text".equals(key)) return "Click a color to filter themes";
        if ("ui.language.text".equals(key)) return "Select a language";
        if ("ui.targethud.name".equals(key)) return "Name:";
        return key;
    }

    static String moduleDescription(ravens4.module.Module mod) {
        if (mod == null) return "";
        String exact = MODULE_DESCRIPTIONS.get(mod.getName());
        if (exact != null) return exact;
        if (mod.moduleCategory() == ravens4.module.Module.category.scripts) {
            return "Custom Raven script module";
        }
        for (ravens4.module.setting.Setting s : mod.getSettings()) {
            if (s instanceof ravens4.module.setting.impl.DescriptionSetting) {
                String d = s.getName();
                if (d != null && !d.trim().isEmpty()) {
                    return d;
                }
            }
        }
        return "Enhances " + mod.moduleCategory().name() + " capabilities";
    }
}

static final class RiseClickGuiModule extends ravens4.module.Module {
    final ravens4.module.setting.impl.KeySetting keybindSetting;
    final ravens4.module.setting.impl.SliderSetting sizeSetting;

    RiseClickGuiModule() {
        super("ClickGUI", ravens4.module.Module.category.render, currentGuiBind);
        this.alwaysOn = true;
        this.registerSetting(this.sizeSetting = new ravens4.module.setting.impl.SliderSetting("Size", 100.0, 1.0, 100.0, 1.0));
        this.registerSetting(this.keybindSetting = new ravens4.module.setting.impl.KeySetting("Keybind", currentGuiBind));
    }

    /** Scale of the Modern GUI: Size 100 is the normal 400x300 window, Size 1 is 30% of it. */
    double getModernScale() {
        double size = Math.max(1.0, Math.min(100.0, this.sizeSetting.getInput()));
        return 0.3 + 0.7 * (size - 1.0) / 99.0;
    }

    public void onKeyBind() {
    }

    public void syncKeyBindState() {
    }

    String getModeName() {
        return "Modern";
    }

    @Override
    public String getInfo() {
        return getModeName();
    }

    @Override
    public void onEnable() {
        if (!guiScreenOpen) {
            openRiseClickGui();
        }
    }

    @Override
    public void onDisable() {
        if (guiScreenOpen) {
            closeRiseClickGui();
        }
    }
}

static final class RiseInterfaceModule extends ravens4.module.Module {
    static final String[] MODULES_TO_SHOW = new String[]{"All", "Exclude render", "Only bound"};
    static final String[] INFO_TYPES = new String[]{"Rise", "Traditional"};

    static final String[] MODERN_COLOR_MODES = new String[]{"Static", "Fade", "Breathe"};
    static final String[] MODERN_FONTS = new String[]{"Apple UI", "Minecraft", "Custom"};
    static final String[] MODERN_SHADER_EFFECTS = new String[]{"Glow", "Shadow"};
    static final String[] MODERN_BACKGROUNDS = new String[]{"Off", "Normal"};


    final ravens4.module.setting.impl.SliderSetting modernColorMode;
    final ravens4.module.setting.impl.SliderSetting modernFont;
    final ravens4.module.setting.impl.TextSetting modernCustomFont;
    final ravens4.module.setting.impl.SliderSetting modernShaderEffect;
    final ravens4.module.setting.impl.ButtonSetting modernDropShadow;
    final ravens4.module.setting.impl.ButtonSetting modernSidebar;
    final ravens4.module.setting.impl.ButtonSetting modernParticlesOnKill;
    final ravens4.module.setting.impl.SliderSetting modernBackground;
    final ravens4.module.setting.impl.TextSetting modernClientName;



    final ravens4.module.setting.impl.SliderSetting modulesToShow;
    final ravens4.module.setting.impl.ButtonSetting suffix;
    final ravens4.module.setting.impl.ButtonSetting lowercase;
    final ravens4.module.setting.impl.ButtonSetting removeSpaces;
    final ravens4.module.setting.impl.ButtonSetting shaders;
    final ravens4.module.setting.impl.SliderSetting blurRadius;
    final ravens4.module.setting.impl.SliderSetting blurCompression;
    final ravens4.module.setting.impl.SliderSetting bloomRadius;
    final ravens4.module.setting.impl.SliderSetting bloomCompression;
    final ravens4.module.setting.impl.SliderSetting backgroundAlpha;
    final ravens4.module.setting.impl.SliderSetting roundingRadius;
    final ravens4.module.setting.impl.SliderSetting informationType;

    RiseInterfaceModule() {
        super("Interface", ravens4.module.Module.category.render, 0);
        this.alwaysOn = true;

        this.registerSetting(this.modernColorMode = new ravens4.module.setting.impl.SliderSetting("ArrayList Color Mode", 1, MODERN_COLOR_MODES));
        this.registerSetting(this.modernFont = new ravens4.module.setting.impl.SliderSetting("ArrayList Font", 0, MODERN_FONTS));
        this.registerSetting(this.modernCustomFont = new ravens4.module.setting.impl.TextSetting("Custom Installed Font", "Arial", "Font name...", 48));
        this.registerSetting(this.modernShaderEffect = new ravens4.module.setting.impl.SliderSetting("Shader Effect", 1, MODERN_SHADER_EFFECTS));
        this.registerSetting(this.modernDropShadow = new ravens4.module.setting.impl.ButtonSetting("Drop Shadow", true));
        this.registerSetting(this.modernSidebar = new ravens4.module.setting.impl.ButtonSetting("Sidebar", true));
        this.registerSetting(this.modernParticlesOnKill = new ravens4.module.setting.impl.ButtonSetting("Particles on Kill", true));
        this.registerSetting(this.modernBackground = new ravens4.module.setting.impl.SliderSetting("BackGround", 1, MODERN_BACKGROUNDS));
        this.registerSetting(this.modernClientName = new ravens4.module.setting.impl.TextSetting("Custom Client Name", "", "Custom tag...", 48));



        this.registerSetting(this.modulesToShow = new ravens4.module.setting.impl.SliderSetting("Modules to Show", 1, MODULES_TO_SHOW));
        this.registerSetting(this.suffix = new ravens4.module.setting.impl.ButtonSetting("Suffix", true));
        this.registerSetting(this.lowercase = new ravens4.module.setting.impl.ButtonSetting("Lowercase", false));
        this.registerSetting(this.removeSpaces = new ravens4.module.setting.impl.ButtonSetting("Remove Spaces", false));
        this.registerSetting(this.shaders = new ravens4.module.setting.impl.ButtonSetting("Shaders", false));
        this.registerSetting(this.blurRadius = new ravens4.module.setting.impl.SliderSetting("Blur Radius", 12.0, 1.0, 30.0, 1.0));
        this.registerSetting(this.blurCompression = new ravens4.module.setting.impl.SliderSetting("Blur Compression", 3.0, 1.0, 10.0, 0.1));
        this.registerSetting(this.bloomRadius = new ravens4.module.setting.impl.SliderSetting("Bloom Radius", 14.0, 1.0, 30.0, 1.0));
        this.registerSetting(this.bloomCompression = new ravens4.module.setting.impl.SliderSetting("Bloom Compression", 2.0, 1.0, 10.0, 0.1));
        this.registerSetting(this.backgroundAlpha = new ravens4.module.setting.impl.SliderSetting("Background Alpha", 110.0, 0.0, 255.0, 1.0));
        this.registerSetting(this.roundingRadius = new ravens4.module.setting.impl.SliderSetting("Rounding Radius", 5.0, 0.0, 20.0, 0.5));
        this.registerSetting(this.informationType = new ravens4.module.setting.impl.SliderSetting("Information Type", 0, INFO_TYPES));
    }

    String getOption(ravens4.module.setting.impl.SliderSetting s, String[] arr) {
        int idx = clampInt((int) Math.round(s.getInput()), 0, arr.length - 1);
        return arr[idx];
    }

    String getModeName() {
        return "Modern";
    }

    @Override
    public String getInfo() {
        return getModeName();
    }

    @Override
    public void onEnable() {
        guiUpdate();
        InterfaceEngine.rebuildEntries();
        savePersistedState();
    }

    @Override
    public void onDisable() {
        savePersistedState();
    }

    @Override
    public void guiUpdate() {
        boolean isModern = true;
        boolean isCustomFont = isModern && "Custom".equals(getOption(this.modernFont, MODERN_FONTS));

        this.modernColorMode.setVisible(isModern, this);
        this.modernFont.setVisible(isModern, this);
        this.modernCustomFont.setVisible(isCustomFont, this);
        this.modernShaderEffect.setVisible(isModern, this);
        this.modernDropShadow.setVisible(isModern, this);
        this.modernSidebar.setVisible(isModern, this);
        this.modernParticlesOnKill.setVisible(isModern, this);
        this.modernBackground.setVisible(isModern, this);
        this.modernClientName.setVisible(isModern, this);

    }
}

static final class HudDragBox {
    final String configKeyPrefix;
    double x;
    double y;
    double targetX;
    double targetY;
    double w;
    double h;
    double prevW = -1.0;
    double prevH = -1.0;

    HudDragBox(String configKeyPrefix, double defaultX, double defaultY, double initW, double initH) {
        this.configKeyPrefix = configKeyPrefix;
        this.x = defaultX;
        this.y = defaultY;
        this.targetX = defaultX;
        this.targetY = defaultY;
        this.w = initW;
        this.h = initH;
    }

    void loadFromConfig() {
        this.targetX = readConfigDouble(this.configKeyPrefix + "_x", this.targetX);
        this.targetY = readConfigDouble(this.configKeyPrefix + "_y", this.targetY);
        this.x = this.targetX;
        this.y = this.targetY;
    }

    void saveToConfig() {
        config.set(this.configKeyPrefix + "_x", String.valueOf(Math.round(this.x * 10.0) / 10.0));
        config.set(this.configKeyPrefix + "_y", String.valueOf(Math.round(this.y * 10.0) / 10.0));
    }

    void updateSize(double newW, double newH) {
        int[] disp = client.getDisplaySize();
        double sw = Math.max(100.0, disp[0]);
        double sh = Math.max(100.0, disp[1]);
        this.w = newW;
        this.h = newH;
        if (this.prevW < 0.0 && this.prevH < 0.0) {
            this.prevW = newW;
            this.prevH = newH;
        }
        if (this.x > sw * 0.5 && Math.abs(this.prevW - newW) > 0.01) {
            this.targetX += (this.prevW - newW);
        }
        if (this.y > sh * 0.5 && Math.abs(this.prevH - newH) > 0.01) {
            this.targetY += (this.prevH - newH);
        }
        this.prevW = newW;
        this.prevH = newH;
        double edge = 3.0;
        this.targetX = clampDouble(this.targetX, edge, Math.max(edge, sw - newW - edge));
        this.targetY = clampDouble(this.targetY, edge, Math.max(edge, sh - newH - edge));
        this.x = this.targetX;
        this.y = this.targetY;
    }
}

static final class HudDragManager {
    private static HudDragBox activeDrag = null;
    private static double dragOffsetX = 0.0;
    private static double dragOffsetY = 0.0;
    private static boolean prevMouseDown = false;
    private static int lastChatMouseX = -1000;
    private static int lastChatMouseY = -1000;
    private static double snapLineX = -1.0;
    private static double snapLineY = -1.0;

    private static List<HudDragBox> collectActiveBoxes() {
        List<HudDragBox> list = new ArrayList<HudDragBox>();
        if (fpsCounterModule != null && fpsCounterModule.isEnabled()) {
            list.add(fpsCounterModule.dragBox);
        }
        if (bpsCounterModule != null && bpsCounterModule.isEnabled()) {
            list.add(bpsCounterModule.dragBox);
        }
        if (notificationsModule != null && notificationsModule.isEnabled()) {
            list.add(notificationsModule.dragBox);
        }
        if (targetInfoModule != null && targetInfoModule.isEnabled() && !targetInfoModule.followPlayer.isToggled()) {
            list.add(targetInfoModule.dragBox);
        }
        return list;
    }

    static void handleChatMouse(int mouseX, int mouseY) {
        lastChatMouseX = mouseX;
        lastChatMouseY = mouseY;
        boolean down = keybinds.isMouseDown(0);
        List<HudDragBox> boxes = collectActiveBoxes();

        if (down && !prevMouseDown) {
            for (int i = boxes.size() - 1; i >= 0; i--) {
                HudDragBox box = boxes.get(i);
                if (DrawUtil.hovered(box.x, box.y, box.w, box.h, mouseX, mouseY)) {
                    activeDrag = box;
                    dragOffsetX = box.x - mouseX;
                    dragOffsetY = box.y - mouseY;
                    break;
                }
            }
        } else if (!down && prevMouseDown) {
            releaseDrag();
        }

        if (down && activeDrag != null) {
            int[] disp = client.getDisplaySize();
            double sw = Math.max(100.0, disp[0]);
            double sh = Math.max(100.0, disp[1]);
            double edge = 5.0;
            double nx = mouseX + dragOffsetX;
            double ny = mouseY + dragOffsetY;

            snapLineX = -1.0;
            snapLineY = -1.0;
            double snapDist = 5.0;

            double[] vGuides = new double[]{sw * 0.5, edge, sw - edge};
            double bestDx = snapDist;
            for (int g = 0; g < vGuides.length; g++) {
                double gx = vGuides[g];
                boolean allowCenter = (g == 0);
                boolean allowLeft = (g == 0 || g == 1);
                boolean allowRight = (g == 0 || g == 2);
                if (allowLeft && Math.abs(nx - gx) < bestDx) {
                    bestDx = Math.abs(nx - gx);
                    nx = gx;
                    snapLineX = gx;
                }
                if (allowCenter && Math.abs((nx + activeDrag.w * 0.5) - gx) < bestDx) {
                    bestDx = Math.abs((nx + activeDrag.w * 0.5) - gx);
                    nx = gx - activeDrag.w * 0.5;
                    snapLineX = gx;
                }
                if (allowRight && Math.abs((nx + activeDrag.w) - gx) < bestDx) {
                    bestDx = Math.abs((nx + activeDrag.w) - gx);
                    nx = gx - activeDrag.w;
                    snapLineX = gx;
                }
            }

            for (int i = 0; i < boxes.size(); i++) {
                HudDragBox other = boxes.get(i);
                if (other == activeDrag) continue;
                if (Math.abs(nx - other.x) < bestDx) {
                    bestDx = Math.abs(nx - other.x);
                    nx = other.x;
                    snapLineX = other.x;
                }
                if (Math.abs((nx + activeDrag.w) - (other.x + other.w)) < bestDx) {
                    bestDx = Math.abs((nx + activeDrag.w) - (other.x + other.w));
                    nx = other.x + other.w - activeDrag.w;
                    snapLineX = other.x + other.w;
                }
            }

            double[] hGuides = new double[]{sh * 0.5, edge, sh - edge};
            double bestDy = snapDist;
            for (int g = 0; g < hGuides.length; g++) {
                double gy = hGuides[g];
                boolean allowCenter = (g == 0);
                boolean allowTop = (g == 0 || g == 1);
                boolean allowBottom = (g == 0 || g == 2);
                if (allowTop && Math.abs(ny - gy) < bestDy) {
                    bestDy = Math.abs(ny - gy);
                    ny = gy;
                    snapLineY = gy;
                }
                if (allowCenter && Math.abs((ny + activeDrag.h * 0.5) - gy) < bestDy) {
                    bestDy = Math.abs((ny + activeDrag.h * 0.5) - gy);
                    ny = gy - activeDrag.h * 0.5;
                    snapLineY = gy;
                }
                if (allowBottom && Math.abs((ny + activeDrag.h) - gy) < bestDy) {
                    bestDy = Math.abs((ny + activeDrag.h) - gy);
                    ny = gy - activeDrag.h;
                    snapLineY = gy;
                }
            }

            for (int i = 0; i < boxes.size(); i++) {
                HudDragBox other = boxes.get(i);
                if (other == activeDrag) continue;
                if (Math.abs(ny - (other.y + other.h + 4.0)) < bestDy) {
                    bestDy = Math.abs(ny - (other.y + other.h + 4.0));
                    ny = other.y + other.h + 4.0;
                    snapLineY = ny;
                }
                if (Math.abs((ny + activeDrag.h + 4.0) - other.y) < bestDy) {
                    bestDy = Math.abs((ny + activeDrag.h + 4.0) - other.y);
                    ny = other.y - activeDrag.h - 4.0;
                    snapLineY = other.y;
                }
            }

            activeDrag.targetX = clampDouble(nx, 3.0, Math.max(3.0, sw - activeDrag.w - 3.0));
            activeDrag.targetY = clampDouble(ny, 3.0, Math.max(3.0, sh - activeDrag.h - 3.0));
            activeDrag.x = activeDrag.targetX;
            activeDrag.y = activeDrag.targetY;
        }
        prevMouseDown = down;
    }

    static void releaseDrag() {
        if (activeDrag != null) {
            activeDrag = null;
            snapLineX = -1.0;
            snapLineY = -1.0;
            savePersistedState();
        }
        prevMouseDown = false;
    }

    static void onRender2D() {
        if (!isChatScreenOpen()) {
            if (activeDrag != null) {
                releaseDrag();
            }
            return;
        }
        final List<HudDragBox> boxes = collectActiveBoxes();
        if (boxes.isEmpty()) return;
        final int[] disp = client.getDisplaySize();
        final double sw = disp[0];
        final double sh = disp[1];
        final double gx = snapLineX;
        final double gy = snapLineY;
        final HudDragBox dragging = activeDrag;
        final int mx = lastChatMouseX;
        final int my = lastChatMouseY;

        ShaderPipeline.addNormal(2, new Runnable() {
            public void run() {
                Color guideCol = new Color(255, 255, 255, 65);
                if (dragging != null) {
                    if (gx >= 0.0) {
                        DrawUtil.rect(gx, 0.0, 0.5, sh, guideCol);
                    }
                    if (gy >= 0.0) {
                        DrawUtil.rect(0.0, gy, sw, 0.5, guideCol);
                    }
                }
                for (int i = 0; i < boxes.size(); i++) {
                    HudDragBox b = boxes.get(i);
                    boolean isAct = (b == dragging);
                    boolean isHov = DrawUtil.hovered(b.x, b.y, b.w, b.h, mx, my);
                    if (isAct || isHov) {
                        DrawUtil.roundedOutline(b.x - 1.0, b.y - 1.0, b.w + 2.0, b.h + 2.0,
                                Math.min(8.0, getInterfaceRounding() + 1.0), 0.7,
                                new Color(255, 255, 255, isAct ? 150 : 85));
                    }
                }
            }
        });
    }
}

static final class RiseNotificationsModule extends ravens4.module.Module {
    final ravens4.module.setting.impl.ButtonSetting editPosition;
    final ravens4.module.setting.impl.SliderSetting size;
    final ravens4.module.setting.impl.ButtonSetting bloom;
    final ravens4.module.setting.impl.ButtonSetting blur;
    final HudDragBox dragBox = new HudDragBox("rise_pos_notifications", 5.0, 126.0, 140.0, 30.0);

    RiseNotificationsModule() {
        super("Notifications", ravens4.module.Module.category.render, 0);
        this.alwaysOn = true;
        this.registerSetting(this.editPosition = new ravens4.module.setting.impl.ButtonSetting("Edit position", new Runnable() {
            public void run() {
                openNotificationPositionEditor();
            }
        }));
        this.registerSetting(this.size = new ravens4.module.setting.impl.SliderSetting("Size", 50.0, 1.0, 100.0, 1.0));
        this.registerSetting(this.bloom = new ravens4.module.setting.impl.ButtonSetting("Bloom", true));
        this.registerSetting(this.blur = new ravens4.module.setting.impl.ButtonSetting("Blur", true));
    }

    /** Size 50 is the normal notification, 1 is 40% of it and 100 is 160%. */
    double getScale() {
        double v = Math.max(1.0, Math.min(100.0, this.size.getInput()));
        return 0.4 + 1.2 * (v - 1.0) / 99.0;
    }

    void loadConfig() {
        this.dragBox.loadFromConfig();
        this.size.setValue(readConfigDouble("rise_notif_size", 50.0));
        this.bloom.setEnabled(readConfigBool("rise_notif_bloom", true));
        this.blur.setEnabled(readConfigBool("rise_notif_blur", true));
        if (readConfigBool("rise_notif_enabled", false)) {
            this.setEnabled(true);
        }
    }

    void saveConfig() {
        this.dragBox.saveToConfig();
        config.set("rise_notif_size", String.valueOf(this.size.getInput()));
        config.set("rise_notif_bloom", String.valueOf(this.bloom.isToggled()));
        config.set("rise_notif_blur", String.valueOf(this.blur.isToggled()));
        config.set("rise_notif_enabled", String.valueOf(this.isEnabled()));
    }

    @Override
    public void onEnable() {
        savePersistedState();
    }

    @Override
    public void onDisable() {
        savePersistedState();
    }
}

/** Closes the Rise GUI and opens the chat, where HUD boxes (and the notification preview) can be dragged. */
static void openNotificationPositionEditor() {
    if (notificationsModule != null && !notificationsModule.isEnabled()) {
        notificationsModule.setEnabled(true);
        savePersistedState();
    }
    if (guiScreenOpen) {
        closeRiseClickGui();
    }
    net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new net.minecraft.client.gui.GuiChat());
}

static final class RiseFpsCounterModule extends ravens4.module.Module {
    final ravens4.module.setting.impl.ButtonSetting showTitle;
    final HudDragBox dragBox = new HudDragBox("rise_pos_fps", 20.0, 60.0, 48.0, 21.0);
    private int lastFps = -1;
    private float cachedTextW = 24.0f;

    RiseFpsCounterModule() {
        super("FPS Counter", ravens4.module.Module.category.render, 0);
        this.alwaysOn = true;
        this.registerSetting(this.showTitle = new ravens4.module.setting.impl.ButtonSetting("Title", false));
    }

    void loadConfig() {
        this.dragBox.loadFromConfig();
        this.showTitle.setEnabled(readConfigBool("rise_fps_title", false));
        if (readConfigBool("rise_fps_enabled", false)) {
            this.setEnabled(true);
        }
    }

    void saveConfig() {
        this.dragBox.saveToConfig();
        config.set("rise_fps_title", String.valueOf(this.showTitle.isToggled()));
        config.set("rise_fps_enabled", String.valueOf(this.isEnabled()));
    }

    @Override
    public void onEnable() {
        savePersistedState();
    }

    @Override
    public void onDisable() {
        savePersistedState();
    }

    void onRender2D() {
        final String titleStr = this.showTitle.isToggled() ? "FPS " : "";
        final int fps = client.getFPS();
        final String valStr = String.valueOf(fps);
        final float titleW = RiseFont.F20.width(titleStr);
        if (fps != this.lastFps || this.cachedTextW <= 0.0f) {
            this.cachedTextW = titleW + RiseFont.F20.width(valStr);
            this.lastFps = fps;
        } else {
            this.cachedTextW = titleW + RiseFont.F20.width(valStr);
        }
        final double baseH = 22.0;
        final double boxW = this.cachedTextW + 6.0;
        final double boxH = baseH - 1.0;
        this.dragBox.updateSize(boxW, boxH);

        final double x = this.dragBox.x;
        final double y = this.dragBox.y;
        final double round = getInterfaceRounding();

        ShaderPipeline.addBlur(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(x, y, boxW, boxH, round + 1.0, Color.BLACK);
            }
        });
        ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(x + 0.5, y + 0.5, boxW - 1.0, boxH - 1.0, round + 1.0, activeTheme.dropShadowColor());
            }
        });
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                DrawUtil.roundedRect(x, y, boxW, boxH, round, RiseTheme.backgroundShade);
                double tx = x + 3.0;
                double ty = y + (baseH / 2.05) - (RiseFont.F20.height() / 2.0);
                if (!titleStr.isEmpty()) {
                    RiseFont.F20.drawShadow(titleStr, tx, ty, activeTheme.firstColor().getRGB());
                }
                RiseFont.F20.drawShadow(valStr, tx + titleW, ty, Color.WHITE.getRGB());
            }
        });
    }
}

static final class RiseBpsCounterModule extends ravens4.module.Module {
    final ravens4.module.setting.impl.ButtonSetting showTitle;
    final HudDragBox dragBox = new HudDragBox("rise_pos_bps", 20.0, 88.0, 56.0, 21.0);
    private String bpsText = "0.0";

    RiseBpsCounterModule() {
        super("BPS Counter", ravens4.module.Module.category.render, 0);
        this.alwaysOn = true;
        this.registerSetting(this.showTitle = new ravens4.module.setting.impl.ButtonSetting("Title", true));
    }

    void loadConfig() {
        this.dragBox.loadFromConfig();
        this.showTitle.setEnabled(readConfigBool("rise_bps_title", true));
        if (readConfigBool("rise_bps_enabled", false)) {
            this.setEnabled(true);
        }
    }

    void saveConfig() {
        this.dragBox.saveToConfig();
        config.set("rise_bps_title", String.valueOf(this.showTitle.isToggled()));
        config.set("rise_bps_enabled", String.valueOf(this.isEnabled()));
    }

    @Override
    public void onEnable() {
        savePersistedState();
    }

    @Override
    public void onDisable() {
        savePersistedState();
    }

    void onTickUpdate() {
        Entity p = client.getPlayer();
        double bps = (p != null) ? p.getBPS() : 0.0;
        this.bpsText = String.valueOf(util.round(bps, 2));
    }

    void onRender2D() {
        final String titleStr = this.showTitle.isToggled() ? "BPS " : "";
        final String valStr = this.bpsText;
        final float titleW = RiseFont.F20.width(titleStr);
        final float contentW = titleW + RiseFont.F20.width(valStr);
        final double baseH = 22.0;
        final double boxW = contentW + 6.0;
        final double boxH = baseH - 1.0;
        this.dragBox.updateSize(boxW, boxH);

        final double x = this.dragBox.x;
        final double y = this.dragBox.y;
        final double round = getInterfaceRounding();

        ShaderPipeline.addBlur(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(x, y, boxW, boxH, round, Color.BLACK);
            }
        });
        ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(x + 0.5, y + 0.5, boxW - 1.0, boxH - 1.0, round + 1.0, activeTheme.dropShadowColor());
            }
        });
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                DrawUtil.roundedRect(x, y, boxW, boxH, round, RiseTheme.backgroundShade);
                double tx = x + 3.0;
                double ty = y + (baseH / 2.05) - (RiseFont.F20.height() / 2.0);
                if (!titleStr.isEmpty()) {
                    RiseFont.F20.drawShadow(titleStr, tx, ty, activeTheme.firstColor().getRGB());
                }
                RiseFont.F20.drawShadow(valStr, tx + titleW, ty, Color.WHITE.getRGB());
            }
        });
    }
}

static final class RiseTargetInfoModule extends ravens4.module.Module {
    static final String[] MODES = new String[]{
            "BMS", "Compact", "Modern", "Novoline", "Godly", "Creida Modern", "Exhibition", "Wurst"
    };
    static final String[] BG_MODES = new String[]{"Glass", "Tint", "Solid"};

    final ravens4.module.setting.impl.SliderSetting mode;
    final ravens4.module.setting.impl.ButtonSetting particles;
    final ravens4.module.setting.impl.SliderSetting backgroundMode;
    final ravens4.module.setting.impl.ButtonSetting followPlayer;
    final HudDragBox dragBox = new HudDragBox("rise_pos_targetinfo", 200.0, 200.0, 140.0, 48.0);

    RiseTargetInfoModule() {
        super("Target Info", ravens4.module.Module.category.render, 0);
        this.alwaysOn = true;
        this.registerSetting(this.mode = new ravens4.module.setting.impl.SliderSetting("Mode", 2, MODES));
        this.registerSetting(this.particles = new ravens4.module.setting.impl.ButtonSetting("Particles", true));
        this.registerSetting(this.backgroundMode = new ravens4.module.setting.impl.SliderSetting("Background Mode", 0, BG_MODES));
        this.registerSetting(this.followPlayer = new ravens4.module.setting.impl.ButtonSetting("Follow Player", false));
    }

    String getModeName() {
        int idx = clampInt((int) Math.round(this.mode.getInput()), 0, MODES.length - 1);
        return MODES[idx];
    }

    String getBgModeName() {
        int idx = clampInt((int) Math.round(this.backgroundMode.getInput()), 0, BG_MODES.length - 1);
        return BG_MODES[idx];
    }

    void loadConfig() {
        this.dragBox.loadFromConfig();
        double savedMode = readConfigDouble("rise_targetinfo_mode", 2.0);
        this.mode.setValue(clampInt((int) Math.round(savedMode), 0, MODES.length - 1));
        this.particles.setEnabled(readConfigBool("rise_targetinfo_particles", true));
        double savedBg = readConfigDouble("rise_targetinfo_bg", 0.0);
        this.backgroundMode.setValue(clampInt((int) Math.round(savedBg), 0, BG_MODES.length - 1));
        this.followPlayer.setEnabled(readConfigBool("rise_targetinfo_follow", false));
        if (readConfigBool("rise_targetinfo_enabled", false)) {
            this.setEnabled(true);
        }
    }

    void saveConfig() {
        this.dragBox.saveToConfig();
        config.set("rise_targetinfo_mode", String.valueOf((int) Math.round(this.mode.getInput())));
        config.set("rise_targetinfo_particles", String.valueOf(this.particles.isToggled()));
        config.set("rise_targetinfo_bg", String.valueOf((int) Math.round(this.backgroundMode.getInput())));
        config.set("rise_targetinfo_follow", String.valueOf(this.followPlayer.isToggled()));
        config.set("rise_targetinfo_enabled", String.valueOf(this.isEnabled()));
    }

    @Override
    public String getInfo() {
        return getModeName();
    }

    @Override
    public void onEnable() {
        guiUpdate();
        savePersistedState();
    }

    @Override
    public void onDisable() {
        savePersistedState();
    }

    @Override
    public void guiUpdate() {
        String m = getModeName();
        boolean hasModernOpts = "Modern".equals(m) || "Creida Modern".equals(m);
        this.particles.setVisible(hasModernOpts, this);
        this.backgroundMode.setVisible(hasModernOpts, this);
    }
}

static final class TargetInfoEngine {
    private static Entity currentTarget = null;
    private static boolean targetInWorld = false;
    private static final StopWatch targetTimer = new StopWatch();

    private static final Animation openAnim = new Animation(Easing.EASE_OUT_ELASTIC, 500L);
    private static final Animation healthAnim = new Animation(Easing.EASE_OUT_QUINT, 250L);
    private static final Animation creidaHurtAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, 300L);
    private static final Animation novoLagAnim = new Animation(Easing.EASE_OUT_SINE, 500L);
    private static final Animation novoWidthAnim = new Animation(Easing.EASE_IN_SINE, 300L);
    private static int lastNovoEntityId = -1;
    private static double lastNovoWidth = 0.0;
    private static String lastRenderedMode = "";

    private static long lastWinCalcMs = 0L;
    private static float cachedWinAdvantage = 0.0f;

    private static boolean hasProjectedBounds = false;
    private static double projMaxX = 0.0;
    private static double projCenterY = 0.0;
    private static long lastProjFrameMs = 0L;

    private static double lastRenderPosX = 200.0;
    private static double lastRenderPosY = 200.0;

    static void onAttack(Entity attacked) {
        if (attacked != null && attacked.isLiving) {
            currentTarget = attacked;
            targetInWorld = true;
            targetTimer.reset();
        }
    }

    static void onTick(RiseTargetInfoModule mod) {
        if (isChatScreenOpen()) {
            Entity me = client.getPlayer();
            if (me != null) {
                currentTarget = me;
                targetInWorld = true;
                targetTimer.reset();
            }
        } else {
            Entity kaTarget = modules.getKillAuraTarget();
            if (kaTarget != null && kaTarget.isLiving) {
                currentTarget = kaTarget;
                targetInWorld = true;
                targetTimer.reset();
            }
        }

        if (currentTarget == null) {
            targetInWorld = false;
            return;
        }

        if (currentTarget.isUser) {
            Entity me = client.getPlayer();
            currentTarget = me;
            targetInWorld = (me != null && !me.isDead());
        } else {
            Entity resolved = world.getEntityById(currentTarget.entityId);
            if (resolved != null && !resolved.isDead()) {
                currentTarget = resolved;
                targetInWorld = true;
            } else {
                targetInWorld = false;
            }
        }

        String mode = mod.getModeName();
        boolean spawnParticles = ("Modern".equals(mode) || "Creida Modern".equals(mode)) && mod.particles.isToggled();
        if (spawnParticles && openAnim.value > 0.02 && currentTarget != null) {
            int ht = Math.max(0, currentTarget.getHurtTime());
            double hurtVal = ht * 0.5;
            if (hurtVal > 0.0) {
                int count = 0;
                while ((double) count < hurtVal * Math.random() / 2.0 && count < 6) {
                    float vx = (float) ((Math.random() - 0.5) * 1.7);
                    float vy = (float) ((Math.random() - 0.5) * 1.7);
                    KillParticleManager.spawn(new KillParticle(
                            new Vec2f((float) (lastRenderPosX + 20.0), (float) (lastRenderPosY + 20.0)),
                            new Vec2f(vx, vy)
                    ));
                    count++;
                }
            }
        }
    }

    static void captureProjectedBounds(float partialTicks) {
        hasProjectedBounds = false;
        Entity t = currentTarget;
        if (t == null || t.isUser || !targetInWorld) {
            return;
        }
        Vec3 pos = t.getPosition();
        Vec3 prev = t.getLastPosition();
        if (pos == null || prev == null) {
            return;
        }
        int[] disp = client.getDisplaySize();
        int scaleFactor = Math.max(1, disp[2]);
        double ix = prev.x + (pos.x - prev.x) * partialTicks;
        double iy = prev.y + (pos.y - prev.y) * partialTicks;
        double iz = prev.z + (pos.z - prev.z) * partialTicks;
        double hw = Math.max(0.2, t.getWidth() * 0.5) + 0.1;
        double hh = Math.max(0.5, t.getHeight()) + 0.2;

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        boolean anyValid = false;

        for (int xi = 0; xi < 2; xi++) {
            double vx = ix + (xi == 0 ? -hw : hw);
            for (int yi = 0; yi < 2; yi++) {
                double vy = iy + (yi == 0 ? -0.05 : hh);
                for (int zi = 0; zi < 2; zi++) {
                    double vz = iz + (zi == 0 ? -hw : hw);
                    Vec3 p = render.worldToScreen(vx, vy, vz, scaleFactor, partialTicks);
                    if (p != null && p.z >= 0.0 && p.z < 1.0) {
                        minX = Math.min(minX, p.x);
                        minY = Math.min(minY, p.y);
                        maxX = Math.max(maxX, p.x);
                        maxY = Math.max(maxY, p.y);
                        anyValid = true;
                    }
                }
            }
        }
        if (anyValid) {
            hasProjectedBounds = true;
            projMaxX = maxX;
            projCenterY = maxY - (maxY - minY) * 0.5;
            lastProjFrameMs = client.time();
        }
    }

    private static boolean resolveTargetPosition(RiseTargetInfoModule mod, double boxH, Vec2d outPos, float partialTicks) {
        Entity t = currentTarget;
        if (mod.followPlayer.isToggled() && t != null && !t.isUser) {
            if (client.time() - lastProjFrameMs > 50L) {
                captureProjectedBounds(partialTicks);
            }
            if (!hasProjectedBounds) {
                return false;
            }
            outPos.x = projMaxX;
            outPos.y = projCenterY - boxH * 0.5;
        } else {
            outPos.x = mod.dragBox.x;
            outPos.y = mod.dragBox.y;
        }
        lastRenderPosX = outPos.x;
        lastRenderPosY = outPos.y;
        return true;
    }

    static void onRender2D(RiseTargetInfoModule mod, float partialTicks) {
        if (isChatScreenOpen() && currentTarget == null) {
            Entity me = client.getPlayer();
            if (me != null) {
                currentTarget = me;
                targetInWorld = true;
                targetTimer.reset();
            }
        }
        Entity target = currentTarget;
        if (target == null) {
            return;
        }

        String mode = mod.getModeName();
        if (!mode.equals(lastRenderedMode)) {
            lastRenderedMode = mode;
            healthAnim.setValue(0.0);
        }

        boolean expired = !targetInWorld || targetTimer.finished(1000L);
        float rawHp = Math.max(0.0f, target.getHealth());
        float maxHp = Math.max(1.0f, target.getMaxHealth());
        double displayHp = Math.min(!targetInWorld ? 0.0 : util.round(rawHp, 1), (double) maxHp);
        int hurtTicks = Math.max(0, target.getHurtTime());
        double hurtOffset = (hurtTicks == 0) ? 0.0 : clampDouble((hurtTicks - partialTicks) * 0.5, 0.0, 9.0);
        Color hurtTint = DrawUtil.mixColors(Color.RED, Color.WHITE, clampDouble(hurtOffset / 9.0, 0.0, 1.0));
        String name = target.getName();
        if (name == null || name.isEmpty()) {
            name = "Target";
        }

        if ("BMS".equals(mode)) {
            renderBms(mod, target, name, rawHp, maxHp, displayHp, expired, partialTicks);
        } else if ("Compact".equals(mode)) {
            renderCompact(mod, target, name, rawHp, maxHp, displayHp, hurtOffset, hurtTint, expired, partialTicks);
        } else if ("Modern".equals(mode)) {
            renderModern(mod, target, name, rawHp, maxHp, displayHp, hurtOffset, hurtTint, expired, partialTicks);
        } else if ("Novoline".equals(mode)) {
            renderNovoline(mod, target, name, rawHp, maxHp, displayHp, expired, partialTicks);
        } else if ("Godly".equals(mode)) {
            renderGodly(mod, target, name, rawHp, maxHp, displayHp, hurtOffset, hurtTint, expired, partialTicks);
        } else if ("Creida Modern".equals(mode)) {
            renderCreidaModern(mod, target, name, rawHp, maxHp, displayHp, hurtOffset, hurtTint, expired, partialTicks);
        } else if ("Exhibition".equals(mode)) {
            renderExhibition(mod, target, name, rawHp, maxHp, displayHp, expired, partialTicks);
        } else if ("Wurst".equals(mode)) {
            renderWurst(mod, target, name, maxHp, displayHp, expired, partialTicks);
        }
    }

    private static void renderBms(final RiseTargetInfoModule mod, final Entity target, final String name,
                                  final float rawHp, final float maxHp, final double displayHp,
                                  boolean expired, float partialTicks) {
        openAnim.setDuration(expired ? 400L : 850L);
        openAnim.setEasing(expired ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        openAnim.run(expired ? 0.0 : 1.0);
        final double scale = openAnim.value;
        if (scale <= 0.005) return;

        final double barMaxW = 100.0;
        healthAnim.setEasing(Easing.EASE_OUT_QUINT);
        healthAnim.setDuration(250L);
        healthAnim.run((displayHp / (double) maxHp) * barMaxW);
        final double barCurW = clampDouble(healthAnim.value, 0.0, barMaxW);

        final double boxW = 4.0 + 32.0 + 4.0 + barMaxW + 4.0 + 4.0;
        final double boxH = 40.0;
        mod.dragBox.updateSize(boxW, boxH);

        Vec2d pos = new Vec2d(0.0, 0.0);
        if (!resolveTargetPosition(mod, boxH, pos, partialTicks)) return;
        final double x = pos.x;
        final double y = pos.y;
        final double cx = x + boxW * 0.5;
        final double cy = y + boxH * 0.5;
        final boolean inWorld = targetInWorld;
        final long pctRound = Math.round((displayHp / (double) maxHp) * 100.0);

        ShaderPipeline.addBlur(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        (boxW - 4.5) * scale, boxH * scale, 6.0 * scale, Color.BLACK);
            }
        });
        ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x + 0.5, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        (boxW - 4.5) * scale, boxH * scale, 7.0 * scale, activeTheme.dropShadowColor());
            }
        });
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                Color bg = RiseTheme.backgroundShade;
                Color c1 = activeTheme.firstColor();
                DrawUtil.roundedGradientV(DrawUtil.scaledBoxX(cx, x, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        (boxW - 4.0) * scale, boxH * scale, 6.0 * scale, bg, bg);

                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                RiseFont.F22.drawShadow(name, x + 40.0, y + 8.0, Color.WHITE.getRGB());
                DrawUtil.dropShadow(3, x + 4.0, y + 4.0, 32.0, 32.0, 20.0, 5.0);
                DrawUtil.drawRoundedSkinHead(target, inWorld, rawHp, x + 4.0, y + 4.0, 32.0, 3.0, Color.WHITE, false, 3.0);
                DrawUtil.roundedRect(x + 40.0, y + 22.0, barMaxW, 12.0, 2.0, Color.darkGray.darker());
                gl.pop();

                Color barCol = Palette.withAlpha(c1, 100);
                DrawUtil.roundedGradientH(DrawUtil.scaledBoxX(cx, x + 40.0, scale), DrawUtil.scaledBoxY(cy, y + 22.0, scale),
                        barCurW * scale, 12.0 * scale, 2.0 * scale, barCol, barCol);

                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                RiseFont.F22.drawCenteredShadow(pctRound + "%", x + 94.0, y + 24.0, Color.WHITE.getRGB());
                gl.pop();
            }
        });
    }

    private static void renderCompact(final RiseTargetInfoModule mod, final Entity target, final String name,
                                      final float rawHp, final float maxHp, final double displayHp,
                                      final double hurtOffset, final Color hurtTint,
                                      boolean expired, float partialTicks) {
        openAnim.setDuration(expired ? 400L : 850L);
        openAnim.setEasing(expired ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        openAnim.run(expired ? 0.0 : 1.0);
        final double scale = openAnim.value;
        if (scale <= 0.005) return;

        double nameW = RiseFont.F18.width(name);
        final double barMaxW = Math.max(nameW + 15.0, 70.0);
        healthAnim.setEasing(Easing.EASE_OUT_QUINT);
        healthAnim.setDuration(250L);
        healthAnim.run((displayHp / (double) maxHp) * barMaxW);
        final double barCurW = clampDouble(healthAnim.value, 0.0, barMaxW);

        final double boxW = 6.0 + 35.0 + 6.0 + barMaxW + 4.0 + 6.0;
        final double boxH = 47.0;
        final double drawW = boxW - 3.5;
        final double drawH = boxH - 5.0;
        mod.dragBox.updateSize(drawW, drawH);

        Vec2d pos = new Vec2d(0.0, 0.0);
        if (!resolveTargetPosition(mod, drawH, pos, partialTicks)) return;
        final double x = pos.x;
        final double y = pos.y;
        final double cx = x + boxW * 0.5;
        final double cy = y + boxH * 0.5;
        final boolean inWorld = targetInWorld;
        final boolean shadersOn = interfaceModule != null && interfaceModule.shaders.isToggled();

        ShaderPipeline.addBlur(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        drawW * scale, drawH * scale, 8.0 * scale, Color.BLACK);
            }
        });
        ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                DrawUtil.roundedGradientOutline(DrawUtil.scaledBoxX(cx, x, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        drawW * scale, drawH * scale, 7.0 * scale, 3.0 * scale,
                        activeTheme.firstColor(), activeTheme.secondColor());
            }
        });
        ShaderPipeline.addNormal(0, new Runnable() {
            public void run() {
                Color bg = RiseTheme.backgroundShade;
                Color c1 = activeTheme.firstColor();
                Color c2 = activeTheme.secondColor();
                double sx = DrawUtil.scaledBoxX(cx, x, scale);
                double sy = DrawUtil.scaledBoxY(cy, y, scale);
                if (!shadersOn) {
                    DrawUtil.roundedRect(sx, sy, drawW * scale, drawH * scale, 8.0 * scale, bg);
                }
                DrawUtil.roundedGradientOutline(sx, sy, drawW * scale, drawH * scale, 8.0 * scale, 0.75 * scale,
                        Palette.withAlpha(c1, 200), Palette.withAlpha(c2, 200));

                double barX = x + 45.5;
                double barY = y + 27.0;
                Color barBgTop = Palette.withAlpha(bg, (int) (bg.getAlpha() / 1.7f));
                DrawUtil.roundedGradientV(DrawUtil.scaledBoxX(cx, barX, scale), DrawUtil.scaledBoxY(cy, barY, scale),
                        barMaxW * scale, 6.0 * scale, 3.0 * scale, barBgTop, bg);
                DrawUtil.roundedGradientH(DrawUtil.scaledBoxX(cx, barX, scale), DrawUtil.scaledBoxY(cy, barY, scale),
                        barCurW * scale, 6.0 * scale, 3.0 * scale, c2, c1);

                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                RiseFont.F18.drawShadow(name, barX, y + 11.0, Color.WHITE.getRGB());
                RiseFont.F18.drawCenteredShadow(String.valueOf(Math.round(rawHp)), x + 35.0 + barMaxW + 4.5, y + 11.0, c2.getRGB());
                gl.pop();
            }
        });
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                double hx = x + 3.5 + hurtOffset * 0.5;
                double hy = y + 3.5 + hurtOffset * 0.5;
                double hs = 35.0 - hurtOffset;
                double r2 = getInterfaceRounding() * 2.0;
                DrawUtil.dropShadow(3, hx, hy, hs, hs, 20.0, r2);
                DrawUtil.drawRoundedSkinHead(target, inWorld, rawHp, hx, hy, hs, r2, hurtTint, true, r2);
                gl.pop();
            }
        });
    }

    private static void renderModern(final RiseTargetInfoModule mod, final Entity target, final String name,
                                     final float rawHp, final float maxHp, final double displayHp,
                                     final double hurtOffset, final Color hurtTint,
                                     boolean expired, float partialTicks) {
        openAnim.setDuration(expired ? 400L : 850L);
        openAnim.setEasing(expired ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        openAnim.run(expired ? 0.0 : 1.0);
        final double scale = openAnim.value;
        if (scale <= 0.005) return;

        float winScore = getWinAdvantage(target);
        final String statusStr = winScore > 0.0f ? "Winning:" : "Losing:";
        final double statusW = RiseFont.F22.width(statusStr);
        final double nameW = RiseFont.F22.width(name);
        final String hpStr = String.valueOf(displayHp);
        final double hpW = RiseFont.F22.width(hpStr);
        final double barMaxW = Math.max(statusW + nameW + 35.0 - hpW, 65.0);

        healthAnim.setEasing(Easing.EASE_OUT_QUINT);
        healthAnim.setDuration(250L);
        healthAnim.run((displayHp / (double) maxHp) * barMaxW);
        final double barCurW = clampDouble(healthAnim.value, 0.0, barMaxW);

        final double boxW = 8.0 + 32.0 + 8.0 + barMaxW + 4.0 + hpW + 8.0;
        final double boxH = 48.0;
        mod.dragBox.updateSize(boxW, boxH);

        Vec2d pos = new Vec2d(0.0, 0.0);
        if (!resolveTargetPosition(mod, boxH, pos, partialTicks)) return;
        final double x = pos.x;
        final double y = pos.y;
        final double cx = x + boxW * 0.5;
        final double cy = y + boxH * 0.5;
        final boolean inWorld = targetInWorld;
        final String bgMode = mod.getBgModeName();

        ShaderPipeline.addBlur(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        (boxW - 1.0) * scale, boxH * scale, 16.0 * scale, Color.BLACK);
            }
        });
        ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x + 0.5, scale), DrawUtil.scaledBoxY(cy, y + 0.5, scale),
                        (boxW - 2.0) * scale, (boxH - 1.0) * scale, 17.0 * scale, activeTheme.dropShadowColor());
            }
        });
        ShaderPipeline.addNormal(0, new Runnable() {
            public void run() {
                Color bg1 = RiseTheme.backgroundShade;
                Color bg2 = RiseTheme.backgroundShade;
                Color c1 = activeTheme.firstColor();
                Color c2 = activeTheme.secondColor();
                if ("Tint".equals(bgMode)) {
                    Color t1 = activeTheme.accentAt(x, y);
                    Color t2 = activeTheme.accentAt(x, y + boxH);
                    bg1 = new Color(t1.getRed() / 5, t1.getGreen() / 5, t1.getBlue() / 5, 128);
                    bg2 = new Color(t2.getRed() / 5, t2.getGreen() / 5, t2.getBlue() / 5, 128);
                } else if ("Solid".equals(bgMode)) {
                    Color f1 = activeTheme.firstColor();
                    Color f2 = activeTheme.secondColor();
                    bg1 = new Color(f1.getRed(), f1.getGreen(), f1.getBlue(), 128);
                    bg2 = new Color(f2.getRed(), f2.getGreen(), f2.getBlue(), 128);
                    c1 = new Color(255, 255, 255);
                    c2 = new Color(164, 164, 164);
                }

                DrawUtil.roundedGradientV(DrawUtil.scaledBoxX(cx, x, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        (boxW - 1.0) * scale, boxH * scale, 16.0 * scale, bg1, bg2);

                double barX = x + 47.0;
                double barY = y + 29.0;
                Color barBgTop = Palette.withAlpha(RiseTheme.backgroundShade, (int) (RiseTheme.backgroundShade.getAlpha() / 1.7f));
                DrawUtil.roundedGradientV(DrawUtil.scaledBoxX(cx, barX, scale), DrawUtil.scaledBoxY(cy, barY, scale),
                        barMaxW * scale, 6.0 * scale, 3.0 * scale, barBgTop, RiseTheme.backgroundShade);
                DrawUtil.roundedGradientV(DrawUtil.scaledBoxX(cx, barX, scale), DrawUtil.scaledBoxY(cy, barY, scale),
                        barCurW * scale, 6.0 * scale, 3.0 * scale, c2, c1);

                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                RiseFont.F22.drawShadow(statusStr, barX, y + 14.0, Color.WHITE.getRGB());
                RiseFont.F22.drawShadow(name, barX + statusW + 3.0, y + 14.5, c1.getRGB());
                RiseFont.F22.drawShadow(hpStr, barX + barMaxW + 4.0, y + 28.0, c1.getRGB());
                gl.pop();
            }
        });
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                double hx = x + 8.0 + hurtOffset * 0.5;
                double hy = y + 8.0 + hurtOffset * 0.5;
                double hs = 32.0 - hurtOffset;
                double r2 = getInterfaceRounding() * 2.0;
                DrawUtil.dropShadow(3, hx, hy, hs, hs, 20.0, r2);
                DrawUtil.drawRoundedSkinHead(target, inWorld, rawHp, hx, hy, hs, r2, hurtTint, true, r2);
                gl.pop();
            }
        });
    }

    private static void renderNovoline(final RiseTargetInfoModule mod, final Entity target, final String name,
                                       final float rawHp, final float maxHp, final double displayHp,
                                       boolean expired, float partialTicks) {
        if (expired) {
            novoLagAnim.reset();
            novoWidthAnim.reset();
            lastNovoEntityId = -1;
            return;
        }
        double nameW = RiseFont.VANILLA.width(name);
        double targetW = 74.0 + nameW;
        if (lastNovoEntityId != target.entityId) {
            novoWidthAnim.setValue(lastNovoWidth <= 1.0 ? targetW : lastNovoWidth);
            novoWidthAnim.reset();
            novoWidthAnim.run(targetW);
            lastNovoEntityId = target.entityId;
        } else {
            novoWidthAnim.run(targetW);
        }
        final double curW = novoWidthAnim.value <= 1.0 ? targetW : novoWidthAnim.value;
        lastNovoWidth = curW;

        final double boxH = 42.0;
        mod.dragBox.updateSize(targetW, boxH);

        Vec2d pos = new Vec2d(0.0, 0.0);
        if (!resolveTargetPosition(mod, boxH, pos, partialTicks)) return;
        final double x = pos.x;
        final double y = pos.y;

        final double barMaxW = 26.0 + nameW;
        final double barCurW = clampDouble(barMaxW * (displayHp / (double) maxHp), 0.0, barMaxW);
        novoLagAnim.setEasing(Easing.EASE_OUT_SINE);
        novoLagAnim.setDuration(500L);
        novoLagAnim.run(barCurW);
        final double lagW = clampDouble(novoLagAnim.value, 0.0, barMaxW);

        double pct = clampDouble((displayHp / (double) maxHp) * 100.0, 0.0, 100.0);
        long tenths = Math.round(pct * 10.0);
        final String pctStr = (tenths / 10L) + "." + Math.abs(tenths % 10L) + "%";
        final double pctW = RiseFont.VANILLA.width(pctStr);
        final boolean inWorld = targetInWorld;

        ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                Color lagCol = DrawUtil.mixColors(activeTheme.secondColor(), Color.BLACK, 0.5);
                DrawUtil.rect(x + 44.0, y + 22.0, barCurW, 11.0, activeTheme.firstColor());
                DrawUtil.rect(x + 44.0, y + 22.0, lagW, 11.0, lagCol);
            }
        });
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                Color lagCol = DrawUtil.mixColors(activeTheme.secondColor(), Color.BLACK, 0.5);
                DrawUtil.rect(x, y, curW, boxH, new Color(40, 40, 40, 255));
                RiseFont.VANILLA.drawShadow(name, x + 44.0, y + 10.0, Color.WHITE.getRGB());
                DrawUtil.rect(x + 44.0, y + 22.0, barMaxW, 11.0, new Color(21, 21, 21, 150));
                DrawUtil.rect(x + 44.0, y + 22.0, lagW, 11.0, lagCol);
                DrawUtil.rect(x + 44.0, y + 22.0, barCurW, 11.0, activeTheme.firstColor());
                RiseFont.VANILLA.drawShadow(pctStr, x + 44.0 + barMaxW * 0.5 - pctW * 0.5, y + 24.5, Color.WHITE.getRGB());
                DrawUtil.drawFlatSkinHead(target, inWorld, rawHp, (int) (x + 1.0), (int) (y + 1.0), 40.0);
            }
        });
    }

    private static void renderGodly(final RiseTargetInfoModule mod, final Entity target, final String name,
                                    final float rawHp, final float maxHp, final double displayHp,
                                    final double hurtOffset, final Color hurtTint,
                                    boolean expired, float partialTicks) {
        openAnim.setDuration(expired ? 400L : 850L);
        openAnim.setEasing(expired ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        openAnim.run(expired ? 0.0 : 1.0);
        final double scale = openAnim.value;
        if (scale <= 0.005) return;

        double nameW = RiseFont.F18.width(name);
        final double barMaxW = Math.max(nameW + 15.0, 70.0);
        healthAnim.setEasing(Easing.EASE_OUT_QUINT);
        healthAnim.setDuration(250L);
        healthAnim.run((displayHp / (double) maxHp) * barMaxW);
        final double barCurW = clampDouble(healthAnim.value, 0.0, barMaxW);

        final double boxW = 6.0 + 32.0 + 6.0 + barMaxW + 4.0 + 6.0;
        final double boxH = 44.0;
        final double drawW = boxW - 3.5;
        final double drawH = boxH - 4.0;
        mod.dragBox.updateSize(drawW, drawH);

        Vec2d pos = new Vec2d(0.0, 0.0);
        if (!resolveTargetPosition(mod, drawH, pos, partialTicks)) return;
        final double x = pos.x;
        final double y = pos.y;
        final double cx = x + boxW * 0.5;
        final double cy = y + boxH * 0.5;
        final boolean inWorld = targetInWorld;

        ShaderPipeline.addBlur(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        drawW * scale, drawH * scale, 8.0 * scale, Color.BLACK);
            }
        });
        ShaderPipeline.addNormal(0, new Runnable() {
            public void run() {
                Color bg = RiseTheme.backgroundShade;
                Color c1 = activeTheme.firstColor();
                Color c2 = activeTheme.secondColor();
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x, scale), DrawUtil.scaledBoxY(cy, y, scale),
                        drawW * scale, drawH * scale, 8.0 * scale, bg);

                double barX = x + 42.5;
                double barY = y + 28.0;
                Color barBgTop = Palette.withAlpha(bg, (int) (bg.getAlpha() / 1.7f));
                DrawUtil.roundedGradientV(DrawUtil.scaledBoxX(cx, barX, scale), DrawUtil.scaledBoxY(cy, barY, scale),
                        barMaxW * scale, 6.0 * scale, 3.0 * scale, barBgTop, bg);
                DrawUtil.roundedGradientH(DrawUtil.scaledBoxX(cx, barX, scale), DrawUtil.scaledBoxY(cy, barY, scale),
                        barCurW * scale, 6.0 * scale, 3.0 * scale, c2, c1);

                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                RiseFont.F18.drawShadow(name, barX, y + 8.0, c1.getRGB());
                RiseFont.VANILLA.drawShadow("\u00a74\u2764", x + 43.0, y + 17.5, -1);
                double heartW = RiseFont.VANILLA.width("\u2764");
                RiseFont.F18.drawShadow(String.valueOf(Math.round(rawHp)), x + 43.0 + heartW + 3.0, y + 18.0, -1);
                gl.pop();
            }
        });
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                double hx = x + 4.0 + hurtOffset * 0.5;
                double hy = y + 4.0 + hurtOffset * 0.5;
                double hs = 32.0 - hurtOffset;
                double r2 = getInterfaceRounding() * 2.0;
                DrawUtil.dropShadow(3, hx, hy, hs, hs, 20.0, r2);
                DrawUtil.drawRoundedSkinHead(target, inWorld, rawHp, hx, hy, hs, r2, hurtTint, true, r2);
                gl.pop();
            }
        });
    }

    private static void renderCreidaModern(final RiseTargetInfoModule mod, final Entity target, final String name,
                                           final float rawHp, final float maxHp, final double displayHp,
                                           final double hurtOffset, final Color hurtTint,
                                           boolean expired, float partialTicks) {
        openAnim.setDuration(expired ? 400L : 850L);
        openAnim.setEasing(expired ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        openAnim.run(expired ? 0.0 : 1.0);
        final double scale = openAnim.value;
        if (scale <= 0.005) return;

        final String hpStr = String.valueOf(displayHp);
        double nameW = RiseFont.F22.width(name);
        double hpW = RiseFont.F22.width(hpStr);
        final double barMaxW = Math.max(nameW + 35.0 - hpW, 75.0);

        healthAnim.setEasing(Easing.EASE_OUT_QUINT);
        healthAnim.setDuration(250L);
        healthAnim.run((displayHp / (double) maxHp) * barMaxW);
        final double barCurW = clampDouble(healthAnim.value, 0.0, barMaxW);

        creidaHurtAnim.setEasing(Easing.EASE_IN_OUT_CUBIC);
        creidaHurtAnim.setDuration(300L);
        creidaHurtAnim.run(hurtOffset * 0.5);
        final double hAnim = creidaHurtAnim.value;

        final double boxW = 10.0 + 32.0 + 10.0 + barMaxW + 4.0 + hpW + 10.0;
        final double boxH = 42.0;
        mod.dragBox.updateSize(boxW + 2.0, boxH + 5.0);

        Vec2d pos = new Vec2d(0.0, 0.0);
        if (!resolveTargetPosition(mod, boxH + 5.0, pos, partialTicks)) return;
        final double x = pos.x;
        final double y = pos.y;
        final double cx = x + boxW * 0.5;
        final double cy = y + boxH * 0.5;
        final boolean inWorld = targetInWorld;
        final String bgMode = mod.getBgModeName();
        final String label = Loc.tr("ui.targethud.name");

        ShaderPipeline.addBlur(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x + 3.0, scale), DrawUtil.scaledBoxY(cy, y + 5.0, scale),
                        (boxW - 1.0) * scale, boxH * scale, 11.0 * scale, Color.BLACK);
            }
        });
        ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                DrawUtil.roundedRect(DrawUtil.scaledBoxX(cx, x + 3.5, scale), DrawUtil.scaledBoxY(cy, y + 5.5, scale),
                        (boxW - 2.0) * scale, (boxH - 1.0) * scale, 12.0 * scale, activeTheme.dropShadowColor());
                DrawUtil.roundedGradientH(DrawUtil.scaledBoxX(cx, x + 48.0, scale), DrawUtil.scaledBoxY(cy, y + 31.0, scale),
                        barCurW * scale, 6.0 * scale, 3.0 * scale,
                        Palette.withAlpha(activeTheme.secondColor(), 255),
                        Palette.withAlpha(activeTheme.firstColor(), 255));
            }
        });
        ShaderPipeline.addNormal(0, new Runnable() {
            public void run() {
                Color bg1 = RiseTheme.backgroundShade;
                Color bg2 = RiseTheme.backgroundShade;
                Color c1 = activeTheme.firstColor();
                Color c2 = activeTheme.secondColor();
                if ("Tint".equals(bgMode)) {
                    Color t1 = activeTheme.accentAt(x, y);
                    Color t2 = activeTheme.accentAt(x, y + boxH);
                    bg1 = new Color(t1.getRed() / 5, t1.getGreen() / 5, t1.getBlue() / 5, 128);
                    bg2 = new Color(t2.getRed() / 5, t2.getGreen() / 5, t2.getBlue() / 5, 128);
                } else if ("Solid".equals(bgMode)) {
                    Color f1 = activeTheme.firstColor();
                    Color f2 = activeTheme.secondColor();
                    bg1 = new Color(f1.getRed(), f1.getGreen(), f1.getBlue(), 128);
                    bg2 = new Color(f2.getRed(), f2.getGreen(), f2.getBlue(), 128);
                    c1 = new Color(255, 255, 255);
                    c2 = new Color(164, 164, 164);
                }

                DrawUtil.roundedGradientV(DrawUtil.scaledBoxX(cx, x + 3.0, scale), DrawUtil.scaledBoxY(cy, y + 5.0, scale),
                        (boxW - 1.0) * scale, boxH * scale, 11.0 * scale, bg1, bg2);

                double barX = x + 48.0;
                double barY = y + 31.0;
                Color barBgTop = Palette.withAlpha(RiseTheme.backgroundShade, (int) (RiseTheme.backgroundShade.getAlpha() / 1.7f));
                DrawUtil.roundedGradientV(DrawUtil.scaledBoxX(cx, barX, scale), DrawUtil.scaledBoxY(cy, barY, scale),
                        barMaxW * scale, 6.5 * scale, 3.5 * scale, barBgTop, RiseTheme.backgroundShade);
                DrawUtil.roundedGradientH(DrawUtil.scaledBoxX(cx, barX, scale), DrawUtil.scaledBoxY(cy, barY, scale),
                        barCurW * scale, 6.5 * scale, 3.5 * scale, c2, c1);

                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                RiseFont.F22.drawShadow(label, barX, y + 16.0, Color.WHITE.getRGB());
                RiseFont.F22.drawShadow(name, barX + RiseFont.F22.width(label) + 3.0, y + 16.5, c1.getRGB());
                RiseFont.F22.drawShadow(hpStr, barX + barMaxW + 4.0, y + 30.0, c1.getRGB());
                gl.pop();
            }
        });
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                gl.push();
                gl.translate(cx * (1.0 - scale), cy * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                double r2 = getInterfaceRounding() * 2.0;
                DrawUtil.dropShadow(3, x + 10.0 + hAnim, y + 10.0 + hAnim, 32.0 - hurtOffset, 32.0 - hurtOffset, 20.0, r2);
                double hOff = (hAnim == 0.0) ? 1.0 : hAnim;
                DrawUtil.drawRoundedSkinHead(target, inWorld, rawHp, x + 10.0 + hOff * 0.5, y + 10.0 + hOff * 0.5, 32.0 - hOff * 0.5, 7.0, hurtTint, true, r2);
                gl.pop();
            }
        });
    }

    private static Color getExhibitionHealthColor(float hp, float maxHp) {
        Color c = new Color(0, 165, 0);
        if (hp < maxHp / 1.5f) {
            c = new Color(200, 200, 0);
        }
        if (hp < maxHp / 2.5f) {
            c = new Color(200, 155, 0);
        }
        if (hp < maxHp / 4.0f) {
            c = new Color(120, 0, 0);
        }
        return c;
    }

    private static void renderExhibition(final RiseTargetInfoModule mod, final Entity target, final String name,
                                         final float rawHp, final float maxHp, final double displayHp,
                                         boolean expired, float partialTicks) {
        if (expired) return;
        final double boxW = 140.0;
        final double boxH = 50.0;
        mod.dragBox.updateSize(boxW, boxH);

        Vec2d pos = new Vec2d(0.0, 0.0);
        if (!resolveTargetPosition(mod, boxH, pos, partialTicks)) return;
        final double x = pos.x;
        final double y = pos.y;

        Entity me = client.getPlayer();
        long distVal = 0L;
        if (me != null && me.getPosition() != null) {
            distVal = Math.round(target.distanceTo(me.getPosition()));
        }
        final String subLine = applyFontPrefix("HP: " + Math.round(rawHp) + " | Dist: " + distVal);

        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                DrawUtil.rect(x, y, 140.0, 50.0, new Color(0, 0, 0));
                DrawUtil.rect(x + 0.5, y + 0.5, 139.0, 49.0, new Color(60, 60, 60));
                DrawUtil.rect(x + 1.5, y + 1.5, 137.0, 47.0, new Color(0, 0, 0));
                DrawUtil.rect(x + 2.0, y + 2.0, 136.0, 46.0, new Color(25, 25, 24));

                RiseFont.VANILLA.draw(name, x + 40.0, y + 6.0, Color.WHITE.getRGB());
                ravens4.utility.RenderUtils.setAlphaLimit(0.0f);
                render.text2d(subLine, (float) (x + 40.0), (float) (y + 17.0), 0.7f, Color.WHITE.getRGB(), false);

                Color barCol = getExhibitionHealthColor(rawHp, maxHp);
                double bx = x + 40.0;
                DrawUtil.rect(bx, y + 25.0, 91.0, 5.0, Palette.withAlpha(barCol, 50));
                DrawUtil.rect(bx, y + 25.0, 91.0 * clampDouble(displayHp / (double) maxHp, 0.0, 1.0), 6.0, barCol);
                DrawUtil.rect(bx, y + 25.0, 91.0, 1.0, Color.BLACK);
                DrawUtil.rect(bx, y + 30.0, 91.0, 1.0, Color.BLACK);
                for (int i = 0; i < 10; i++) {
                    DrawUtil.rect(bx + 10.0 * i, y + 25.0, 1.0, 6.0, Color.BLACK);
                }

                ItemStack held = target.getHeldItem();
                if (held != null) {
                    render.item(held, (float) bx, (float) (y + 31.0), 1.0f);
                }
                for (int slot = 0; slot < 4; slot++) {
                    ItemStack arm = target.getArmorInSlot(3 - slot);
                    if (arm != null) {
                        render.item(arm, (float) (bx + 15.0 * (slot + 1)), (float) (y + 31.0), 1.0f);
                    }
                }

                gl.resetColor();
                render.entityGui(target, (int) Math.round(x + 20.0), (int) Math.round(y + 44.0),
                        -25.0f, -clampFloat(target.getPitch() * 0.5f, -30.0f, 30.0f), 20);
                DrawUtil.resetGlState();
            }
        });
    }

    private static void renderWurst(final RiseTargetInfoModule mod, final Entity target, final String name,
                                    final float maxHp, final double displayHp,
                                    boolean expired, float partialTicks) {
        if (expired) return;
        final double boxW = 185.0;
        final double boxH = 34.0;
        mod.dragBox.updateSize(boxW, boxH);

        Vec2d pos = new Vec2d(0.0, 0.0);
        if (!resolveTargetPosition(mod, boxH, pos, partialTicks)) return;
        final double x = pos.x;
        final double y = pos.y;

        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                DrawUtil.rect(x, y, 185.0, 34.0, new Color(255, 255, 255, 100));
                RiseFont.VANILLA.draw("Name: " + name, x + 4.0, y + 4.0, Color.BLACK.getRGB());
                DrawUtil.rect(x + 4.0, y + 16.0, 177.0 * clampDouble(displayHp / (double) maxHp, 0.0, 1.0), 10.0, Color.ORANGE);
            }
        });
    }

    private static float getWinAdvantage(Entity target) {
        long now = client.time();
        if (now - lastWinCalcMs > 200L) {
            cachedWinAdvantage = simulateCombatAdvantage(target);
            lastWinCalcMs = now;
        }
        return cachedWinAdvantage;
    }

    private static float simulateCombatAdvantage(Entity target) {
        Entity me = client.getPlayer();
        if (me == null || target == null || !target.isLiving) {
            return 0.0f;
        }
        float myHp = Math.max(0.1f, me.getHealth());
        float theirHp = Math.max(0.1f, target.getHealth());
        float myDmg = estimateHitDamage(me, target);
        float theirDmg = estimateHitDamage(target, me);
        if (myDmg <= 0.01f && theirDmg <= 0.01f) {
            return 0.0f;
        }
        int steps = 0;
        while (myHp > 0.0f && theirHp > 0.0f && steps < 256) {
            theirHp -= myDmg;
            myHp -= theirDmg;
            steps++;
        }
        if (steps >= 256) {
            return myDmg - theirDmg;
        }
        return myHp - theirHp;
    }

    private static float estimateHitDamage(Entity attacker, Entity defender) {
        float dmg = 1.0f;
        ItemStack held = attacker.getHeldItem();
        if (held != null && held.name != null) {
            dmg = baseWeaponDamage(held.name);
            int sharp = getEnchantLevel(held, "sharpness");
            if (sharp > 0) {
                dmg += sharp * 1.25f;
            }
        }
        int strengthAmp = getPotionAmplifierPlusOne(attacker, 5);
        if (strengthAmp > 0) {
            dmg += strengthAmp * 3.0f;
        }
        if (defender.isPlayer) {
            float armorPoints = estimateArmorPoints(defender);
            float armorRed = Math.min(armorPoints * 0.04f, 0.8f);
            dmg *= (1.0f - armorRed);

            int epfTotal = estimateProtectionEpf(defender);
            int cappedEpf = Math.min(20, (int) Math.ceil(Math.min(25, epfTotal) * 0.75f));
            if (cappedEpf > 0) {
                dmg *= (1.0f - cappedEpf * 0.04f);
            }
        }
        int resistAmp = getPotionAmplifierPlusOne(defender, 11);
        if (resistAmp > 0) {
            dmg *= (1.0f - Math.min(resistAmp * 0.2f, 0.8f));
        }
        if (Float.isNaN(dmg) || Float.isInfinite(dmg)) {
            dmg = 1.0f;
        }
        return Math.max(0.01f, dmg);
    }

    private static float baseWeaponDamage(String itemName) {
        String n = itemName.toLowerCase();
        if (n.endsWith("_sword")) {
            if (n.startsWith("diamond")) return 8.0f;
            if (n.startsWith("iron")) return 7.0f;
            if (n.startsWith("stone")) return 6.0f;
            return 5.0f;
        }
        if (n.endsWith("_axe")) {
            if (n.startsWith("diamond")) return 7.0f;
            if (n.startsWith("iron")) return 6.0f;
            if (n.startsWith("stone")) return 5.0f;
            return 4.0f;
        }
        if (n.endsWith("_pickaxe")) {
            if (n.startsWith("diamond")) return 6.0f;
            if (n.startsWith("iron")) return 5.0f;
            if (n.startsWith("stone")) return 4.0f;
            return 3.0f;
        }
        if (n.endsWith("_shovel")) {
            if (n.startsWith("diamond")) return 5.5f;
            if (n.startsWith("iron")) return 4.5f;
            if (n.startsWith("stone")) return 3.5f;
            return 2.5f;
        }
        return 1.0f;
    }

    private static float estimateArmorPoints(Entity player) {
        float pts = 0.0f;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack arm = player.getArmorInSlot(slot);
            if (arm == null || arm.name == null) continue;
            String n = arm.name.toLowerCase();
            if (n.startsWith("diamond_")) {
                pts += (slot == 3 ? 3 : (slot == 2 ? 8 : (slot == 1 ? 6 : 3)));
            } else if (n.startsWith("iron_")) {
                pts += (slot == 3 ? 2 : (slot == 2 ? 6 : (slot == 1 ? 5 : 2)));
            } else if (n.startsWith("chainmail_")) {
                pts += (slot == 3 ? 2 : (slot == 2 ? 5 : (slot == 1 ? 4 : 1)));
            } else if (n.startsWith("golden_")) {
                pts += (slot == 3 ? 2 : (slot == 2 ? 5 : (slot == 1 ? 3 : 1)));
            } else if (n.startsWith("leather_")) {
                pts += (slot == 3 ? 1 : (slot == 2 ? 3 : (slot == 1 ? 2 : 1)));
            }
        }
        return pts;
    }

    private static int estimateProtectionEpf(Entity player) {
        int epf = 0;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack arm = player.getArmorInSlot(slot);
            if (arm == null) continue;
            int prot = getEnchantLevel(arm, "protection");
            if (prot > 0) {
                epf += (int) Math.floor((6 + prot * prot) * 0.75f / 3.0f);
            }
        }
        return epf;
    }

    private static int getEnchantLevel(ItemStack stack, String keyPart) {
        try {
            List<Object[]> ench = stack.getEnchantments();
            if (ench == null) return 0;
            for (int i = 0; i < ench.size(); i++) {
                Object[] pair = ench.get(i);
                if (pair != null && pair.length >= 2 && pair[0] != null && pair[1] instanceof Integer) {
                    String en = String.valueOf(pair[0]).toLowerCase();
                    if (en.equals(keyPart) || (keyPart.equals("protection") && en.endsWith("protection") && !en.contains("fire") && !en.contains("blast") && !en.contains("projectile"))) {
                        return ((Integer) pair[1]).intValue();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int getPotionAmplifierPlusOne(Entity ent, int targetId) {
        try {
            List<Object[]> pots = ent.getPotionEffects();
            if (pots == null) return 0;
            for (int i = 0; i < pots.size(); i++) {
                Object[] pd = pots.get(i);
                if (pd != null && pd.length >= 3 && pd[0] instanceof Integer && pd[2] instanceof Integer) {
                    if (((Integer) pd[0]).intValue() == targetId) {
                        return ((Integer) pd[2]).intValue() + 1;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}

static final class ToastItem {
    final String title;
    final String body;
    final int durationMs;

    ToastItem(String title, String body, int durationMs) {
        this.title = title;
        this.body = body;
        this.durationMs = durationMs;
    }
}

static final class ToggleToastManager {
    private static final List<ToastItem> queue = new ArrayList<ToastItem>();
    private static ToastItem current = null;
    private static final StopWatch timer = new StopWatch();
    private static final Animation anim = new Animation(Easing.EASE_OUT_EXPO, 500L);
    private static double boxW = 140.0;
    private static final double boxH = 30.0;
    private static final double iconSize = 20.0;
    private static final double pad = (boxH - iconSize) / 2.0;
    private static final double posX = 5.0;
    private static final double posY = 126.0;
    private static int tickCounter = 0;

    static void push(String title, String body, int durationMs) {
        if (queue.size() >= 5) {
            queue.remove(0);
        }
        queue.add(new ToastItem(title, body, durationMs));
    }

    static void onTick() {
        tickCounter++;
        if (tickCounter % 5 != 0) {
            return;
        }
        if (!queue.isEmpty() && (current == null || timer.finished(current.durationMs + 200L))) {
            if (current != null) {
                queue.remove(current);
            }
            if (!queue.isEmpty()) {
                current = queue.get(0);
                timer.reset();
                anim.setValue(1.1);
                boxW = Math.max(140.0, (double) RiseFont.F15.width(current.body) + pad * 3.0 + iconSize + 2.0);
            } else {
                current = null;
            }
        } else if (current != null && timer.finished(current.durationMs + 500L)) {
            current = null;
        }
    }

    private static final ToastItem PREVIEW = new ToastItem("Notifications", "Drag me anywhere in the chat", 1000);

    static void onRender2D() {
        if (notificationsModule == null || !notificationsModule.isEnabled()) {
            return;
        }
        final boolean preview = current == null && isChatScreenOpen();
        final ToastItem item = preview ? PREVIEW : current;
        if (item == null) {
            return;
        }
        final double posX = notificationsModule.dragBox.x;
        final double posY = notificationsModule.dragBox.y;
        final double userScale = notificationsModule.getScale();
        final boolean doBlur = notificationsModule.blur.isToggled();
        final boolean doBloom = notificationsModule.bloom.isToggled();
        boolean expired = !preview && timer.finished(item.durationMs);
        anim.setDuration(500L);
        anim.setEasing(Easing.EASE_OUT_EXPO);
        anim.run(expired ? 1.1 : 1.0);
        final double scale = anim.value;
        final double alphaFactor = clampDouble(1.0 - 10.0 * Math.abs(1.0 - scale), 0.0, 1.0);
        if ((anim.finished && expired) || alphaFactor <= 0.02) {
            return;
        }

        final double w = preview ? Math.max(140.0, (double) RiseFont.F15.width(PREVIEW.body) + pad * 3.0 + iconSize + 2.0) : boxW;
        notificationsModule.dragBox.updateSize(w * userScale, boxH * userScale);
        if (doBlur && Math.abs(scale - 1.0) <= 0.045) {
            ShaderPipeline.addBlur(new Runnable() {
                public void run() {
                    gl.push();
                    gl.translate(posX, posY, 0.0);
                    gl.scale((float) userScale, (float) userScale, 1.0f);
                    gl.translate(-posX, -posY, 0.0);
                    gl.translate((posX + w / 2.0) * (1.0 - scale), (posY + boxH / 2.0) * (1.0 - scale), 0.0);
                    gl.scale((float) scale, (float) scale, 1.0f);
                    DrawUtil.roundedRect(posX, posY, w, boxH, 10.0, Palette.withAlpha(Color.BLACK, (int) (255.0 * alphaFactor)));
                    gl.pop();
                }
            });
        }

        if (doBloom) ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                gl.push();
                gl.translate(posX, posY, 0.0);
                gl.scale((float) userScale, (float) userScale, 1.0f);
                gl.translate(-posX, -posY, 0.0);
                gl.translate((posX + w / 2.0) * (1.0 - scale), (posY + boxH / 2.0) * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                Color ds = activeTheme.dropShadowColor();
                DrawUtil.roundedRect(posX + 0.5, posY + 0.5, w - 1.0, boxH - 1.0, 11.0, Palette.withAlpha(ds, (int) (ds.getAlpha() * alphaFactor)));
                gl.pop();
            }
        });

        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                gl.push();
                gl.translate(posX, posY, 0.0);
                gl.scale((float) userScale, (float) userScale, 1.0f);
                gl.translate(-posX, -posY, 0.0);
                gl.translate((posX + w / 2.0) * (1.0 - scale), (posY + boxH / 2.0) * (1.0 - scale), 0.0);
                gl.scale((float) scale, (float) scale, 1.0f);
                Color bg = RiseTheme.backgroundShade;
                DrawUtil.roundedRect(posX, posY, w, boxH, 10.0, Palette.withAlpha(bg, (int) (bg.getAlpha() * alphaFactor)));
                DrawUtil.roundedRect(posX + pad, posY + pad, iconSize, iconSize, 6.0, Palette.withAlpha(Color.WHITE, (int) (255.0 * alphaFactor)));

                int checkCol = Palette.withAlpha(activeTheme.firstColor(), (int) (255.0 * alphaFactor)).getRGB();
                render.line2D(posX + pad + 5.5, posY + pad + 10.5, posX + pad + 8.8, posY + pad + 13.8, 1.8f, checkCol);
                render.line2D(posX + pad + 8.8, posY + pad + 13.8, posX + pad + 14.8, posY + pad + 6.8, 1.8f, checkCol);
                RiseFont.F15.drawShadow(item.title, posX + pad + iconSize + pad, posY + pad + 3.0,
                        Palette.withAlpha(activeTheme.firstColor(), (int) (255.0 * alphaFactor)).getRGB());
                RiseFont.F15.drawShadow(item.body, posX + pad + iconSize + pad, posY + pad + 0.5 + pad * 0.7 + RiseFont.F15.height(),
                        Palette.withAlpha(Color.WHITE, (int) (255.0 * alphaFactor)).getRGB());
                gl.pop();
            }
        });
    }
}

static final class KillParticle {
    final Vec2f pos;
    final Vec2f vel;
    final float size;
    final Color baseColor;
    Color renderColor;
    Color bloomColor;
    final StopWatch stepTimer = new StopWatch();
    final StopWatch lifeTimer = new StopWatch();
    float alpha;

    KillParticle(Vec2f pos, Vec2f vel) {
        this.pos = pos;
        this.vel = vel;
        this.baseColor = Palette.withAlpha(DrawUtil.mixColors(activeTheme.firstColor(), activeTheme.secondColor(), Math.random()), (int) (Math.random() * 255.0));
        this.size = (float) (2.0 + Math.random() * 3.0);
        this.alpha = this.baseColor.getAlpha();
    }

    void update() {
        long dt = Math.min(stepTimer.elapsed(), 100L);
        for (long i = 0; i <= dt; i++) {
            this.pos.x += this.vel.x / 10.0f;
            this.pos.y += this.vel.y / 10.0f;
            this.vel.x *= 0.999f;
            this.vel.y *= 0.999f;
        }
        this.bloomColor = Palette.withAlpha(this.baseColor, clampInt((int) (this.alpha * 3.0f), 0, 255));
        this.renderColor = Palette.withAlpha(this.baseColor, clampInt((int) this.alpha, 0, 255));
        this.alpha = Math.max(this.alpha - (float) dt / 18.0f, 0.0f);
        stepTimer.reset();
    }

    void drawNormal() {
        if (renderColor != null) {
            DrawUtil.roundedRect(pos.x, pos.y, size, size, size / 2.0f, renderColor);
        }
    }

    void drawBloom() {
        if (bloomColor != null) {
            DrawUtil.roundedRect(pos.x, pos.y, size, size, size / 2.0f, bloomColor);
        }
    }
}

static final class KillParticleManager {
    private static final List<KillParticle> particles = new ArrayList<KillParticle>();

    static void spawn(KillParticle p) {
        if (particles.size() < 64) {
            particles.add(p);
        }
    }

    static void renderAndUpdate() {
        if (particles.isEmpty()) return;
        Iterator<KillParticle> it = particles.iterator();
        while (it.hasNext()) {
            KillParticle p = it.next();
            p.update();
            if (p.lifeTimer.finished(3000L) || p.alpha <= 1.0f) {
                it.remove();
            }
        }
        if (particles.isEmpty()) return;
        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                for (int i = 0; i < particles.size(); i++) {
                    particles.get(i).drawNormal();
                }
            }
        });
        ShaderPipeline.addBloom(new Runnable() {
            public void run() {
                for (int i = 0; i < particles.size(); i++) {
                    particles.get(i).drawBloom();
                }
            }
        });
    }
}

static final class ArrayEntry {
    final ravens4.module.Module mod;
    Vec2d pos = new Vec2d(5000.0, 0.0);
    Vec2d target = new Vec2d(5000.0, 0.0);
    float anim = 0.0f;
    String rawName = "";
    String tag = "";
    String displayName = "";
    String displayTag = "";
    float nameWidth = 0.0f;
    float tagWidth = 0.0f;
    boolean showTag = false;
    Color color = Color.WHITE;

    ArrayEntry(ravens4.module.Module mod) {
        this.mod = mod;
        this.rawName = mod.getName();
    }

    boolean shouldShow(RiseInterfaceModule hud) {
        if (mod == clickGuiModule
                || ("Gui".equals(mod.getName()) && mod.moduleCategory() == ravens4.module.Module.category.client)) {
            return false;
        }
        if (!mod.canBeEnabled() || mod.isHidden()) {
            return false;
        }
        String filter = hud.getOption(hud.modulesToShow, RiseInterfaceModule.MODULES_TO_SHOW);
        if ("Exclude render".equals(filter)) {
            return mod.moduleCategory() != ravens4.module.Module.category.render;
        }
        if ("Only bound".equals(filter)) {
            return mod.getKeycode() != 0;
        }
        return true;
    }
}

static final class InterfaceEngine {
    private static final List<ArrayEntry> allEntries = new ArrayList<ArrayEntry>();
    private static final List<ArrayEntry> sortedEntries = new ArrayList<ArrayEntry>();
    private static final StopWatch animTimer = new StopWatch();
    private static final StopWatch refreshTimer = new StopWatch();
    private static float moduleSpacing = 12.0f;
    private static float edgeOffset = 10.0f;
    private static int lastKnownModuleCount = -1;

    static void rebuildEntries() {
        Map<ravens4.module.Module, ArrayEntry> prevMap = new HashMap<ravens4.module.Module, ArrayEntry>();
        for (int i = 0; i < allEntries.size(); i++) {
            ArrayEntry e = allEntries.get(i);
            prevMap.put(e.mod, e);
        }
        allEntries.clear();
        List<ravens4.module.Module> mods = getAllClientModules();
        lastKnownModuleCount = mods.size();
        for (int i = 0; i < mods.size(); i++) {
            ravens4.module.Module m = mods.get(i);
            ArrayEntry existing = prevMap.get(m);
            if (existing != null) {
                existing.rawName = m.getName();
                allEntries.add(existing);
            } else {
                allEntries.add(new ArrayEntry(m));
            }
        }
        resortEntries(interfaceModule);
    }

    private static void resortEntries(RiseInterfaceModule hud) {
        sortedEntries.clear();
        if (hud == null) return;
        for (int i = 0; i < allEntries.size(); i++) {
            ArrayEntry e = allEntries.get(i);
            if (e.shouldShow(hud)) {
                sortedEntries.add(e);
            }
        }
        Collections.sort(sortedEntries, new Comparator<ArrayEntry>() {
            public int compare(ArrayEntry a, ArrayEntry b) {
                return Double.compare(-(b.nameWidth + b.tagWidth), -(a.nameWidth + a.tagWidth));
            }
        });

        Collections.reverse(sortedEntries);
    }

    private static String resolveModuleTag(ravens4.module.Module m, boolean riseInfoType) {
        if (riseInfoType) {
            for (ravens4.module.setting.Setting s : m.getSettings()) {
                if (s instanceof ravens4.module.setting.impl.SliderSetting) {
                    ravens4.module.setting.impl.SliderSetting ss = (ravens4.module.setting.impl.SliderSetting) s;
                    if (ss.isString && ss.getOptions() != null && ss.getOptions().length > 0) {
                        int idx = clampInt((int) Math.round(ss.getInput()), 0, ss.getOptions().length - 1);
                        String opt = ss.getOptions()[idx];
                        if ("Bloxd".equalsIgnoreCase(opt)) {
                            return "Blocks";
                        }
                        return opt;
                    }
                }
            }
            String fallback = m.getInfo();
            return fallback == null ? "" : fallback;
        } else {
            String info = m.getInfo();
            if (info != null && !info.isEmpty()) {
                return info;
            }
            for (ravens4.module.setting.Setting s : m.getSettings()) {
                if (s instanceof ravens4.module.setting.impl.SliderSetting) {
                    ravens4.module.setting.impl.SliderSetting ss = (ravens4.module.setting.impl.SliderSetting) s;
                    if (ss.isString && ss.getOptions() != null && ss.getOptions().length > 0) {
                        int idx = clampInt((int) Math.round(ss.getInput()), 0, ss.getOptions().length - 1);
                        return ss.getOptions()[idx];
                    }
                }
            }
            return "";
        }
    }

    static void onTick(RiseInterfaceModule hud) {
        List<ravens4.module.Module> currentMods = getAllClientModules();
        if (allEntries.isEmpty() || currentMods.size() != lastKnownModuleCount || refreshTimer.finished(15000L)) {
            rebuildEntries();
            refreshTimer.reset();
        }

        String mode = hud.getModeName();
        boolean riseInfo = "Rise".equals(hud.getOption(hud.informationType, RiseInterfaceModule.INFO_TYPES));
        boolean lower = hud.lowercase.isToggled();
        boolean noSpaces = hud.removeSpaces.isToggled();
        boolean suffixOn = hud.suffix.isToggled();

        RiseFont activeListFont = getArrayListFont(hud, mode);

        for (int i = 0; i < allEntries.size(); i++) {
            ArrayEntry e = allEntries.get(i);
            e.rawName = e.mod.getName();
            e.tag = resolveModuleTag(e.mod, riseInfo);
            e.showTag = !e.tag.isEmpty() && suffixOn;

            String n = lower ? e.rawName.toLowerCase() : e.rawName;
            String t = lower ? e.tag.toLowerCase() : e.tag;
            if (noSpaces) {
                n = n.replace(" ", "");
                t = t.replace(" ", "");
            }
            e.displayName = n;
            e.displayTag = t;
            e.nameWidth = activeListFont.width(n);
            e.tagWidth = e.showTag ? (activeListFont.width(t) + 3.0f) : 0.0f;

            if ("Modern".equals(mode)) {
                String cm = hud.getOption(hud.modernColorMode, RiseInterfaceModule.MODERN_COLOR_MODES);
                Color col = activeTheme.firstColor();
                if ("Breathe".equals(cm)) {
                    double bf = activeTheme.blendFactor(0.0, 0.0);
                    col = DrawUtil.mixColors(col, activeTheme.secondColor(), bf);
                } else if ("Fade".equals(cm)) {
                    col = activeTheme.accentAt(0.0, e.pos.y);
                }
                e.color = col;
            }
        }
        resortEntries(hud);
    }

    private static RiseFont getArrayListFont(RiseInterfaceModule hud, String mode) {
        if ("Modern".equals(mode)) {
            String f = hud.getOption(hud.modernFont, RiseInterfaceModule.MODERN_FONTS);
            if ("Minecraft".equals(f)) return RiseFont.VANILLA;
            return RiseFont.F18;
        }
        if (activeLang == RiseLang.ZH_ZH) {
            return RiseFont.F18;
        }
        return RiseFont.VANILLA;
    }

    static void onRender2D(RiseInterfaceModule hud, float partialTicks) {
        if (client.getPlayer() == null) return;
        if (!toggleOn(showHudSetting)) return;
        if (allEntries.isEmpty()) {
            rebuildEntries();
            onTick(hud);
        }

        int[] disp = client.getDisplaySize();
        float sw = disp[0];
        float sh = disp[1];
        String mode = hud.getModeName();

        RiseFont listFont = getArrayListFont(hud, mode);
        if ("Modern".equals(mode)) {
            boolean isMc = (listFont == RiseFont.VANILLA);
            moduleSpacing = listFont.height() + (isMc ? 2.0f : 3.0f);
            edgeOffset = 10.0f;
        }

        long dt = Math.min(animTimer.elapsed(), 100L);
        animTimer.reset();
        float speedDiv = 100.0f;

        for (int i = 0; i < allEntries.size(); i++) {
            ArrayEntry e = allEntries.get(i);
            if (e.mod.isEnabled()) {
                e.anim = Math.min(e.anim + (float) dt / speedDiv, 10.0f);
            } else {
                e.anim = Math.max(e.anim - (float) dt / speedDiv, 0.0f);
            }
        }

        float currentY = 0.0f;
        double lerpStep = 1.0 - Math.pow(1.0 - 0.015, Math.max(1L, dt));
        for (int i = 0; i < sortedEntries.size(); i++) {
            ArrayEntry e = sortedEntries.get(i);
            if (e.anim == 0.0f) continue;

            if (!e.mod.isEnabled() && e.anim < 10.0f) {
                e.target = new Vec2d(sw + e.nameWidth + e.tagWidth - edgeOffset, currentY + edgeOffset);
            } else {
                e.target = new Vec2d(sw - e.nameWidth - e.tagWidth - edgeOffset, currentY + edgeOffset);
                currentY += moduleSpacing;
            }

            if (e.pos.x > sw + 500.0) {
                e.pos.x = sw + e.nameWidth + e.tagWidth + 10.0;
                e.pos.y = e.target.y;
            }

            if (Math.abs(e.pos.x - e.target.x) > 0.5 || Math.abs(e.pos.y - e.target.y) > 0.5 || (e.anim != 0.0f && e.anim != 10.0f)) {
                e.pos.x = lerpDouble(e.pos.x, e.target.x, lerpStep);
                e.pos.y = lerpDouble(e.pos.y, e.target.y, lerpStep);
            } else {
                e.pos.x = e.target.x;
                e.pos.y = e.target.y;
            }
        }

        if ("Modern".equals(mode)) {
            renderModernHud(hud, sw, sh, listFont);
        }
    }

    private static void renderModernHud(final RiseInterfaceModule hud, final float sw, final float sh, final RiseFont listFont) {
        final boolean isMc = (listFont == RiseFont.VANILLA);
        final double padX = isMc ? 3.5 : 2.0;
        final String shaderEffect = hud.getOption(hud.modernShaderEffect, RiseInterfaceModule.MODERN_SHADER_EFFECTS);
        final boolean isGlow = "Glow".equals(shaderEffect);
        final boolean isShadow = "Shadow".equals(shaderEffect);
        final boolean drawBg = "Normal".equals(hud.getOption(hud.modernBackground, RiseInterfaceModule.MODERN_BACKGROUNDS));
        final boolean drawSidebar = hud.modernSidebar.isToggled();
        final boolean dropShadow = hud.modernDropShadow.isToggled();
        final String customTag = hud.modernClientName.getText();
        final boolean hideBottomInfo = isChatScreenOpen();

        Entity player = client.getPlayer();
        Vec3 pPos = player != null ? player.getPosition() : new Vec3(0, 0, 0);
        final String coordsText = (int) Math.floor(pPos.x) + ", " + (int) Math.floor(pPos.y) + ", " + (int) Math.floor(pPos.z);
        final String userName = client.getUser() != null ? client.getUser() : "Player";
        final float bottomY = sh - listFont.height() - 2.0f;
        final float xyzLabelW = RiseFont.F18.width("XYZ:") + 2.0f;

        final String verLabel = "Version: ";
        final String userLabel = "User: ";
        final float verLabelW = RiseFont.F18.width(verLabel);
        final float verValW = RiseFont.F18.width(CLIENT_VERSION);
        final float userLabelW = RiseFont.F18.width(userLabel);
        final float userValW = RiseFont.F18.width(userName);
        final float userStartX = sw - 5.0f - (userLabelW + userValW);
        final float verStartX = userStartX - 5.0f - (verLabelW + verValW);

        final List<Object[]> activePotions = new ArrayList<Object[]>();
        if (player != null) {
            List<Object[]> rawPots = player.getPotionEffects();
            for (int i = 0; i < rawPots.size(); i++) {
                Object[] pd = rawPots.get(i);
                int id = ((Integer) pd[0]).intValue();
                String rawKey = String.valueOf(pd[1]);
                int amp = ((Integer) pd[2]).intValue();
                int dur = ((Integer) pd[3]).intValue();
                if (dur > 24000) continue;
                String pName = formatPotionName(id, rawKey) + " " + toRoman(amp + 1);
                String pDur = formatDurationTicks(dur);
                activePotions.add(new Object[]{Integer.valueOf(id), pName, pDur});
            }
            Collections.sort(activePotions, new Comparator<Object[]>() {
                public int compare(Object[] a, Object[] b) {
                    float wa = RiseFont.F16.width((String) a[1]) + RiseFont.F16.width(" " + (String) a[2]);
                    float wb = RiseFont.F16.width((String) b[1]) + RiseFont.F16.width(" " + (String) b[2]);
                    return Float.compare(wb, wa);
                }
            });
        }

        String cm = hud.getOption(hud.modernColorMode, RiseInterfaceModule.MODERN_COLOR_MODES);
        for (int i = 0; i < sortedEntries.size(); i++) {
            ArrayEntry e = sortedEntries.get(i);
            if (e.anim == 0.0f) continue;
            if ("Fade".equals(cm)) {
                e.color = activeTheme.accentAt(0.0, e.pos.y);
            } else if ("Breathe".equals(cm)) {
                e.color = DrawUtil.mixColors(activeTheme.firstColor(), activeTheme.secondColor(), activeTheme.blendFactor(0.0, 0.0));
            } else {
                e.color = activeTheme.firstColor();
            }
        }

        if (drawBg) {
            ShaderPipeline.addBlur(new Runnable() {
                public void run() {
                    for (int i = 0; i < sortedEntries.size(); i++) {
                        ArrayEntry e = sortedEntries.get(i);
                        if (e.anim == 0.0f) continue;
                        double bx = e.pos.x - padX + (isMc ? 0.5 : 0.0);
                        DrawUtil.rect(bx, e.pos.y - 3.0, e.nameWidth + e.tagWidth + 3.0f + padX, moduleSpacing, Color.BLACK);
                    }
                }
            });
        }

        if (isGlow || isShadow) {
            ShaderPipeline.addBloom(new Runnable() {
                public void run() {
                    for (int i = 0; i < sortedEntries.size(); i++) {
                        ArrayEntry e = sortedEntries.get(i);
                        if (e.anim == 0.0f) continue;
                        double x = e.pos.x;
                        double y = e.pos.y;
                        Color c = e.color;
                        if (drawBg) {
                            double bx = x - padX + (isMc ? 0.5 : 0.0);
                            DrawUtil.rect(bx, y - 3.0, e.nameWidth + e.tagWidth + 3.0f + padX, moduleSpacing,
                                    isGlow ? Palette.withAlpha(c, 255) : activeTheme.dropShadowColor());
                        } else if (isGlow) {
                            drawModernEntryText(listFont, e, x + 0.5, y, c.getRGB(), dropShadow);
                        } else {
                            listFont.draw(e.displayName, x, y, Color.BLACK.getRGB());
                            if (e.showTag) {
                                listFont.draw(e.displayTag, x + e.nameWidth + 3.0, y, Color.BLACK.getRGB());
                            }
                        }
                        if (drawSidebar) {
                            DrawUtil.roundedRect(x + e.nameWidth + e.tagWidth + 2.0, y - 1.5, 2.0, 9.0, 1.0, c);
                        }
                    }

                    if (toggleOn(showWatermarkSetting)) {
                    if (isMc) {
                        RiseFont.of(32).drawShadow(CLIENT_NAME, 6.0, 6.0, activeTheme.firstColor().getRGB());
                    } else {
                        DrawUtil.drawThemeGradientText(RiseFont.F36, CLIENT_NAME, 6.0, 6.0, true);
                    }
                    if (!customTag.isEmpty()) {
                        RiseFont.F18.draw(customTag, 6.0 + RiseFont.F36.width(CLIENT_NAME) + 2.0, 6.0, activeTheme.secondColor().getRGB());
                    }
                    }
                }
            });
        }

        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                for (int i = 0; i < sortedEntries.size(); i++) {
                    ArrayEntry e = sortedEntries.get(i);
                    if (e.anim == 0.0f) continue;
                    double x = e.pos.x;
                    double y = e.pos.y;
                    if (drawBg) {
                        double bx = x - padX + (isMc ? 0.5 : 0.0);
                        DrawUtil.rect(bx, y - 3.0, e.nameWidth + e.tagWidth + 3.0f + padX, moduleSpacing, RiseTheme.backgroundShade);
                    }
                    drawModernEntryText(listFont, e, x, y - 0.5, e.color.getRGB(), dropShadow);
                    if (drawSidebar) {
                        DrawUtil.roundedRect(x + e.nameWidth + e.tagWidth + 2.0, y - 1.5, 2.0, 9.0, 1.0, e.color);
                    }
                }

                if (toggleOn(showWatermarkSetting)) {
                if (isMc) {
                    RiseFont.of(32).drawShadow(CLIENT_NAME, 6.0, 6.0, activeTheme.firstColor().getRGB());
                } else {
                    DrawUtil.drawThemeGradientText(RiseFont.F36, CLIENT_NAME, 6.0, 6.0, true);
                }
                if (!customTag.isEmpty()) {
                    RiseFont.F18.draw(customTag, 6.0 + RiseFont.F36.width(CLIENT_NAME) + 2.0, 6.0, activeTheme.secondColor().getRGB());
                }
                }

                int infoCol = -3355444;
                if (!hideBottomInfo) {
                    RiseFont.F18.drawShadow("XYZ:", 5.0, bottomY, infoCol);
                    RiseFont.F18.drawShadow(coordsText, 5.0 + xyzLabelW, bottomY, infoCol);

                    RiseFont.F18.drawShadow(verLabel, verStartX, bottomY, infoCol);
                    RiseFont.F18.drawShadow(CLIENT_VERSION, verStartX + verLabelW, bottomY, infoCol);
                    RiseFont.F18.drawShadow(userLabel, userStartX, bottomY, infoCol);
                    RiseFont.F18.drawShadow(userName, userStartX + userLabelW, bottomY, infoCol);
                }

                for (int i = 0; i < activePotions.size(); i++) {
                    Object[] pd = activePotions.get(i);
                    int pid = ((Integer) pd[0]).intValue();
                    String pName = (String) pd[1];
                    String pDur = (String) pd[2];
                    float rowX = sw - RiseFont.F16.width(pName) - RiseFont.F16.width(" " + pDur) - 19.0f - 5.0f;
                    float rowY = bottomY - (RiseFont.F18.height() + 5.0f) * (i + 1);
                    DrawUtil.drawPotionIcon(pid, rowX + 4.0f, rowY - 3.7f, 13.0f);
                    RiseFont.F16.drawShadow(pName, rowX + 19.0f, rowY, infoCol);
                    RiseFont.F16.drawShadow(" " + pDur, rowX + 19.0f + RiseFont.F16.width(pName), rowY, infoCol);
                }
            }
        });
    }

    private static void drawModernEntryText(RiseFont font, ArrayEntry e, double x, double y, int color, boolean shadow) {
        if (shadow) {
            font.drawShadow(e.displayName, x, y, color);
            if (e.showTag) {
                font.drawShadow(e.displayTag, x + e.nameWidth + 3.0, y, -3355444);
            }
        } else {
            font.draw(e.displayName, x, y, color);
            if (e.showTag) {
                font.draw(e.displayTag, x + e.nameWidth + 3.0, y, -3355444);
            }
        }
    }

    private static String toRoman(int num) {
        if (num < 1) return String.valueOf(num);
        if (num > 255) num = 255;
        StringBuilder sb = new StringBuilder();
        int[] vals = new int[]{100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] syms = new String[]{"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 0; i < vals.length; i++) {
            while (num >= vals[i]) {
                sb.append(syms[i]);
                num -= vals[i];
            }
        }
        return sb.toString();
    }

    private static String formatDurationTicks(int ticks) {
        int totalSec = Math.max(0, ticks / 20);
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        return mins + ":" + (secs < 10 ? "0" + secs : String.valueOf(secs));
    }

    private static String formatPotionName(int id, String rawKey) {
        switch (id) {
            case 1: return "Speed";
            case 2: return "Slowness";
            case 3: return "Haste";
            case 4: return "Mining Fatigue";
            case 5: return "Strength";
            case 6: return "Instant Health";
            case 7: return "Instant Damage";
            case 8: return "Jump Boost";
            case 9: return "Nausea";
            case 10: return "Regeneration";
            case 11: return "Resistance";
            case 12: return "Fire Resistance";
            case 13: return "Water Breathing";
            case 14: return "Invisibility";
            case 15: return "Blindness";
            case 16: return "Night Vision";
            case 17: return "Hunger";
            case 18: return "Weakness";
            case 19: return "Poison";
            case 20: return "Wither";
            case 21: return "Health Boost";
            case 22: return "Absorption";
            case 23: return "Saturation";
            default: {
                if (rawKey != null && rawKey.startsWith("potion.")) {
                    String s = rawKey.substring(7);
                    return s.isEmpty() ? "Effect" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
                }
                return rawKey == null ? "Effect" : rawKey;
            }
        }
    }

    private static String formatTime12Hour() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(client.time());
        int h = cal.get(java.util.Calendar.HOUR);
        if (h == 0) h = 12;
        int m = cal.get(java.util.Calendar.MINUTE);
        String ampm = cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM ? "AM" : "PM";
        return h + ":" + (m < 10 ? "0" + m : String.valueOf(m)) + " " + ampm;
    }

    private static String formatTime24Hour() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(client.time());
        int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int m = cal.get(java.util.Calendar.MINUTE);
        int s = cal.get(java.util.Calendar.SECOND);
        return (h < 10 ? "0" + h : String.valueOf(h)) + ":"
                + (m < 10 ? "0" + m : String.valueOf(m)) + ":"
                + (s < 10 ? "0" + s : String.valueOf(s));
    }

    private static int getPlayerPingSafe() {
        try {
            Entity p = client.getPlayer();
            if (p != null) {
                NetworkPlayer np = p.getNetworkPlayer();
                if (np != null) {
                    return np.getPing();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}

static enum SidebarCategory {
    SEARCH("category.search", false),
    COMBAT("category.combat", true),
    MOVEMENT("category.movement", true),
    PLAYER("category.player", true),
    RENDER("category.render", true),
    EXPLOIT("category.exploit", true),
    GHOST("category.ghost", true),
    CONFIGS("category.config", false),
    SCRIPTS("category.script", false),
    THEMES("category.themes", false),
    LANGUAGE("category.language", false);

    final String trKey;
    final boolean moduleCategory;

    SidebarCategory(String trKey, boolean moduleCategory) {
        this.trKey = trKey;
        this.moduleCategory = moduleCategory;
    }

    String displayName() {
        return Loc.tr(this.trKey);
    }

    boolean matchesModule(ravens4.module.Module mod) {
        if (mod == null) return false;
        ravens4.module.Module.category c = mod.moduleCategory();
        if (c == null) return false;
        String cn = c.name().toLowerCase();
        switch (this) {
            case COMBAT:
                return "combat".equals(cn);
            case MOVEMENT:
                return "movement".equals(cn);
            case PLAYER:
                return "player".equals(cn) || "world".equals(cn);
            case RENDER:
                return "render".equals(cn);
            case EXPLOIT:
                return "exploit".equals(cn) || "network".equals(cn) || "minigames".equals(cn);
            case GHOST:
                return !"combat".equals(cn)
                        && !"movement".equals(cn)
                        && !"player".equals(cn)
                        && !"world".equals(cn)
                        && !"render".equals(cn)
                        && !"exploit".equals(cn)
                        && !"network".equals(cn)
                        && !"minigames".equals(cn)
                        && !"scripts".equals(cn)
                        && !"theme".equals(cn)
                        && !"profiles".equals(cn);
            case SCRIPTS:
                return "scripts".equals(cn);
            default:
                return false;
        }
    }

    static SidebarCategory fromModule(ravens4.module.Module mod) {
        if (mod == null) return GHOST;
        for (SidebarCategory sc : values()) {
            if (sc != SEARCH && sc.matchesModule(mod)) {
                return sc;
            }
        }
        return GHOST;
    }
}

static final class KeyNameUtil {
    static boolean isCtrlDown() {
        return keybinds.isKeyDown(29) || keybinds.isKeyDown(157)
                || keybinds.isKeyDown(219) || keybinds.isKeyDown(220);
    }

    static boolean isShiftDown() {
        return keybinds.isKeyDown(42) || keybinds.isKeyDown(54);
    }

    static String nameOf(int keyCode) {
        if (keyCode <= 0) return "NONE";
        if (keyCode >= 1000) {
            if (keyCode == 1069) return "MWHEELUP";
            if (keyCode == 1070) return "MWHEELDOWN";
            return "MOUSE" + (keyCode - 1000);
        }
        switch (keyCode) {
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
            case 55: return "MULTIPLY";
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
            case 87: return "F11";
            case 88: return "F12";
            case 157: return "RCTRL";
            case 184: return "RALT";
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
            default: return "KEY_" + keyCode;
        }
    }
}

static final class ScrollHandler {
    double target = 0.0;
    double scroll = 0.0;
    double minScroll = 0.0;
    final StopWatch lerpTimer = new StopWatch();
    final StopWatch wheelTimer = new StopWatch();
    boolean scrollbarVisible = false;
    boolean wheelActive = false;
    boolean scrolling = false;
    boolean reverse = false;

    /** One fixed step per wheel notch (several notches in one frame add up), clamped to the content. */
    void addWheel(int wheelDelta) {
        if (wheelDelta == 0) {
            return;
        }
        double clampedMin = Math.min(0.0, this.minScroll);
        int notches = Math.max(1, Math.abs(wheelDelta) / 120);
        double step = (wheelDelta > 0 ? 1.0 : -1.0) * (this.reverse ? -1.0 : 1.0) * 28.0 * notches;
        this.wheelActive = true;
        this.target = Math.min(Math.max(this.target + step, clampedMin), 0.0);
        this.wheelTimer.reset();
    }

    void update(int wheelDelta, boolean active) {
        double clampedMin = Math.min(0.0, this.minScroll);
        if (active && wheelDelta != 0) {
            addWheel(wheelDelta);
        } else if (this.wheelTimer.finished(50L)) {
            this.wheelActive = false;
            this.target = Math.min(Math.max(this.target, clampedMin), 0.0);
            this.wheelTimer.reset();
        }

        long elapsed = Math.min(this.lerpTimer.elapsed(), 200L);
        for (long i = 0; i < elapsed; i++) {
            this.scroll = lerpDouble(this.scroll, this.target, 0.015);
        }
        this.scrolling = Math.abs(this.scroll - this.target) > 0.5;
        this.lerpTimer.reset();
    }

    void renderScrollBar(double x, double y, double maxScrollbarHeight) {
        if (maxScrollbarHeight <= 4.0 || this.minScroll >= -0.5) {
            this.scrollbarVisible = false;
            return;
        }
        double ratio = (this.reverse ? (this.minScroll - this.scroll) : this.scroll) / this.minScroll;
        ratio = clampDouble(ratio, 0.0, 1.0);
        double barHeight = maxScrollbarHeight - this.minScroll / (this.minScroll - maxScrollbarHeight) * maxScrollbarHeight;
        this.scrollbarVisible = barHeight < maxScrollbarHeight && barHeight > 2.0;
        if (!this.scrollbarVisible) {
            return;
        }
        double barY = y + maxScrollbarHeight * ratio - barHeight * ratio;
        DrawUtil.roundedRect(x - 1.0, barY, 2.0, barHeight, 1.0, new Color(255, 255, 255, 110));
    }

    void setMaxScrollRange(double minScrollVal) {
        this.minScroll = Math.min(0.0, minScrollVal);
        if (!this.wheelActive) {
            this.target = Math.min(Math.max(this.target, this.minScroll), 0.0);
        }
    }

    void reset() {
        this.scroll = 0.0;
        this.target = 0.0;
        this.lerpTimer.reset();
    }
}

static final class RiseTextBox {
    static final int ALIGN_LEFT = 0;
    static final int ALIGN_CENTER = 1;

    String text = "";
    boolean focused = false;
    int cursor = 0;
    Vec2d pos;
    float width;
    RiseFont font;
    Color color;
    int align;
    String placeholder;
    boolean passwordMask;
    int maxLength = 256;
    String allowedChars = null;

    private int scrollIndex = 0;
    private double renderPosX = 0.0;
    private double caretSmoothX = 0.0;
    private long lastRenderMs = 0L;

    RiseTextBox(Vec2d pos, RiseFont font, Color color, int align, String placeholder, float width) {
        this(pos, font, color, align, placeholder, width, null, false);
    }

    RiseTextBox(Vec2d pos, RiseFont font, Color color, int align, String placeholder, float width, String allowedChars) {
        this(pos, font, color, align, placeholder, width, allowedChars, false);
    }

    RiseTextBox(Vec2d pos, RiseFont font, Color color, int align, String placeholder, float width, String allowedChars, boolean passwordMask) {
        this.pos = pos;
        this.font = font;
        this.color = color;
        this.align = align;
        this.placeholder = placeholder == null ? "" : placeholder;
        this.width = width;
        this.allowedChars = allowedChars;
        this.passwordMask = passwordMask;
        this.renderPosX = pos != null ? pos.x : 0.0;
    }

    void setPos(double x, double y) {
        if (this.pos == null) {
            this.pos = new Vec2d(x, y);
            this.renderPosX = x;
        } else {
            this.pos.x = x;
            this.pos.y = y;
        }
    }

    void setText(String t) {
        String safe = t == null ? "" : t;
        if (safe.length() > this.maxLength) {
            safe = safe.substring(0, this.maxLength);
        }
        this.text = safe;
        this.cursor = clampInt(this.cursor, 0, this.text.length());
    }

    boolean isEmpty() {
        return this.text == null || this.text.isEmpty();
    }

    void draw() {
        if (this.pos == null) return;
        this.cursor = clampInt(this.cursor, 0, this.text.length());
        boolean empty = isEmpty();
        String rawDisplay;
        if (empty) {
            rawDisplay = this.placeholder;
        } else if (this.passwordMask) {
            StringBuilder sb = new StringBuilder(this.text.length());
            for (int i = 0; i < this.text.length(); i++) {
                sb.append('*');
            }
            rawDisplay = sb.toString();
        } else {
            rawDisplay = this.text;
        }

        String visible;
        if (empty) {
            this.scrollIndex = 0;
            visible = rawDisplay;
        } else {
            this.scrollIndex = computeScrollOffset(rawDisplay);
            visible = rawDisplay.substring(this.scrollIndex);
            while (this.font.width(visible) > this.width && !visible.isEmpty()) {
                visible = visible.substring(0, visible.length() - 1);
            }
        }

        float visibleWidth = this.font.width(visible);
        long now = client.time();
        long dt = this.lastRenderMs == 0L ? 16L : Math.min(Math.abs(now - this.lastRenderMs), 500L);
        this.lastRenderMs = now;

        if (this.align == ALIGN_CENTER) {
            double targetX = this.pos.x - visibleWidth / 2.0;
            if (Math.abs(this.renderPosX - targetX) > 200.0) {
                this.renderPosX = targetX;
            } else {
                for (long i = 0; i < dt; i++) {
                    this.renderPosX = (this.renderPosX * 19.0 + targetX) / 20.0;
                }
            }
        } else {
            this.renderPosX = this.pos.x;
        }

        if (empty) {
            int phAlpha = clampInt((int) (this.color.getAlpha() * (this.focused ? 0.3f : 0.2f)), 0, 255);
            Color phCol = new Color(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), phAlpha);
            this.font.draw(visible, this.renderPosX, this.pos.y, phCol.getRGB());
        } else {
            this.font.draw(visible, this.renderPosX, this.pos.y, this.color.getRGB());
        }

        if (this.focused) {
            String beforeCursor = safeSub( empty ? "" : rawDisplay, this.scrollIndex, this.cursor);
            float subW = this.font.width(beforeCursor);
            for (long i = 0; i < dt; i++) {
                this.caretSmoothX = (this.caretSmoothX * 19.0 + (subW - 2.0)) / 20.0;
            }
            int caretAlpha = this.color.getAlpha() == 0
                    ? 0
                    : clampInt((int) (((Math.sin((double) now / 150.0) + 1.0) / 2.0) * (this.color.getAlpha())), 0, 255);
            Color caretCol = new Color(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), caretAlpha);
            this.font.draw("|", this.renderPosX + this.caretSmoothX + 1.0, this.pos.y - 1.0, caretCol.getRGB());
        }
    }

    private int computeScrollOffset(String s) {
        if (this.font.width(s) <= this.width) {
            return 0;
        }
        int idx = this.cursor;
        while (idx > 0 && this.font.width(s.substring(idx - 1, this.cursor)) < this.width) {
            idx--;
        }
        return idx;
    }

    private String safeSub(String s, int from, int to) {
        int a = clampInt(from, 0, s.length());
        int b = clampInt(to, a, s.length());
        return s.substring(a, b);
    }

    void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.pos == null) return;
        double hitX = this.pos.x + (this.align == ALIGN_CENTER ? -this.width / 2.0 : 0.0);
        boolean hit = DrawUtil.hovered(hitX, this.pos.y - 2.0, this.width, this.font.height() + 4.0, mouseX, mouseY);
        this.focused = (button == 0 && hit);
        if (this.focused) {
            this.cursor = this.text.length();
        }
    }

    void keyTyped(char typedChar, int keyCode) {
        if (!this.focused || keyCode == 28) {
            return;
        }
        this.cursor = clampInt(this.cursor, 0, this.text.length());
        boolean ctrl = KeyNameUtil.isCtrlDown();

        if (keyCode == 200 || keyCode == 208) {
            return;
        }
        if (ctrl && keyCode == 30) {

            this.cursor = this.text.length();
            return;
        }
        if (keyCode == 199) {
            this.cursor = 0;
            return;
        }
        if (keyCode == 207) {
            this.cursor = this.text.length();
            return;
        }
        if (keyCode == 211 && !this.text.isEmpty()) {
            if (this.cursor < this.text.length()) {
                this.text = this.text.substring(0, this.cursor) + this.text.substring(this.cursor + 1);
            }
            return;
        }
        if (keyCode == 14 && !this.text.isEmpty()) {
            if (ctrl) {
                this.text = this.text.substring(this.cursor);
                this.cursor = 0;
            } else if (this.cursor > 0) {
                this.text = this.text.substring(0, this.cursor - 1) + this.text.substring(this.cursor);
                this.cursor--;
            }
            return;
        }
        if (keyCode == 205) {
            this.cursor = ctrl ? this.text.length() : Math.min(this.text.length(), this.cursor + 1);
            return;
        }
        if (keyCode == 203) {
            this.cursor = ctrl ? 0 : Math.max(0, this.cursor - 1);
            return;
        }
        if (isCharAllowed(typedChar) && this.text.length() < this.maxLength) {
            this.text = this.text.substring(0, this.cursor) + typedChar + this.text.substring(this.cursor);
            this.cursor++;
        }
        this.cursor = clampInt(this.cursor, 0, this.text.length());
    }

    private boolean isCharAllowed(char c) {
        if (c < 32 || c == 127) {
            return false;
        }
        if (this.allowedChars == null || this.allowedChars.isEmpty()) {
            return true;
        }
        return this.allowedChars.indexOf(c) >= 0 || this.allowedChars.indexOf(Character.toLowerCase(c)) >= 0;
    }
}

static abstract class ValueComp {
    final ravens4.module.Module ownerModule;
    final ravens4.module.setting.Setting setting;
    final ravens4.module.setting.impl.GroupSetting parentGroup;
    final boolean conditionalChild;
    double height = 14.0;
    Vec2d position;
    int alpha = 255;

    ValueComp(ravens4.module.Module ownerModule,
              ravens4.module.setting.Setting setting,
              ravens4.module.setting.impl.GroupSetting parentGroup,
              boolean conditionalChild) {
        this.ownerModule = ownerModule;
        this.setting = setting;
        this.parentGroup = parentGroup;
        this.conditionalChild = conditionalChild;
    }

    boolean isVisibleNow() {
        if (this.setting == null || !this.setting.visible) {
            return false;
        }
        if (this.parentGroup != null && (!this.parentGroup.visible || !this.parentGroup.isOpened())) {
            return false;
        }
        return true;
    }

    boolean isIndented() {
        return this.parentGroup != null || this.conditionalChild;
    }

    abstract void render(Vec2d pos, int mouseX, int mouseY, float partialTicks);

    abstract boolean mouseClicked(int mouseX, int mouseY, int button);

    void mouseReleased() {
    }

    void renderBloom() {
    }

    void keyTyped(char typedChar, int keyCode) {
    }

    boolean hasFocusedText() {
        return false;
    }

    boolean unfocusText() {
        return false;
    }
}

static final class BoolComp extends ValueComp {
    private final ravens4.module.setting.impl.ButtonSetting buttonSetting;
    private final StopWatch animWatch = new StopWatch();
    private double dotSize = 0.0;

    BoolComp(ravens4.module.Module mod,
             ravens4.module.setting.impl.ButtonSetting bs,
             ravens4.module.setting.impl.GroupSetting group,
             boolean conditional) {
        super(mod, bs, group, conditional);
        this.buttonSetting = bs;
        this.dotSize = (!bs.isMethodButton && bs.isToggled()) ? 5.0 : 0.0;
    }

    @Override
    void render(Vec2d pos, int mouseX, int mouseY, float partialTicks) {
        this.position = pos;
        String label = this.buttonSetting.getName();
        RiseFont.F16.draw(label, pos.x, pos.y, Palette.rgbWithAlpha(Palette.TEXT_SUB, this.alpha));

        if (this.buttonSetting.isMethodButton) {
            double bx = pos.x + RiseFont.F16.width(label) + 6.0;
            DrawUtil.roundedRect(bx, pos.y - 1.0, 26.0, 9.0, 3.0, Palette.withAlpha(Palette.BACK_MAIN, this.alpha));
            RiseFont.F12.drawCentered("Run", bx + 13.0, pos.y + 0.5, Palette.rgbWithAlpha(activeTheme.firstColor(), this.alpha));
            this.animWatch.reset();
            return;
        }

        double d2 = pos.x + RiseFont.F16.width(label) + 3.0;
        double step = (double) this.animWatch.elapsed() / 20.0;
        this.dotSize = this.buttonSetting.isToggled()
                ? Math.min(5.0, this.dotSize + step)
                : Math.max(0.0, this.dotSize - step);
        DrawUtil.roundedRect(d2 + 2.5, pos.y, 5.0, 5.0, 2.5, Palette.withAlpha(Palette.BACK_MAIN, this.alpha));
        if (this.dotSize > 0.01) {
            DrawUtil.roundedRect(
                    d2 - this.dotSize / 2.0 + 5.0,
                    pos.y - this.dotSize / 2.0 + 2.5,
                    this.dotSize,
                    this.dotSize,
                    this.dotSize / 2.0,
                    Palette.withAlpha(activeTheme.firstColor(), this.alpha)
            );
        }
        this.animWatch.reset();
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.position == null || button != 0) return false;
        if (DrawUtil.hovered(this.position.x, this.position.y - 3.5, 265.0, this.height, mouseX, mouseY)) {
            if (this.buttonSetting.isMethodButton) {
                this.buttonSetting.runMethod();
            } else {
                this.buttonSetting.toggle();
            }
            this.ownerModule.guiUpdate();
            return true;
        }
        return false;
    }

    @Override
    void renderBloom() {
        if (this.position == null || this.buttonSetting.isMethodButton || this.dotSize <= 0.05) return;
        String label = this.buttonSetting.getName();
        double d2 = this.position.x + RiseFont.F16.width(label) + 2.0;
        DrawUtil.roundedRect(
                d2 - this.dotSize / 2.0 + 4.0,
                this.position.y - this.dotSize / 2.0 + 2.5,
                this.dotSize,
                this.dotSize,
                this.dotSize / 2.0,
                Palette.withAlpha(activeTheme.firstColor(), this.alpha)
        );
    }
}

static final class ModeComp extends ValueComp {
    private final ravens4.module.setting.impl.SliderSetting modeSetting;

    ModeComp(ravens4.module.Module mod,
             ravens4.module.setting.impl.SliderSetting ss,
             ravens4.module.setting.impl.GroupSetting group,
             boolean conditional) {
        super(mod, ss, group, conditional);
        this.modeSetting = ss;
    }

    @Override
    void render(Vec2d pos, int mouseX, int mouseY, float partialTicks) {
        this.position = pos;
        String prefix = this.modeSetting.getName() + ":";
        int textAlpha = Math.min(this.alpha, Palette.TEXT_SUB.getAlpha());
        int col = Palette.rgbWithAlpha(Palette.TEXT_SUB, textAlpha);
        RiseFont.F16.draw(prefix, pos.x, pos.y, col);

        String[] opts = this.modeSetting.getOptions();
        String current = "";
        if (opts != null && opts.length > 0) {
            int idx = clampInt((int) Math.round(this.modeSetting.getInput()), 0, opts.length - 1);
            current = opts[idx];
        }
        RiseFont.F16.draw(current, pos.x + RiseFont.F16.width(prefix) + 2.0, pos.y, col);
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.position == null || (button != 0 && button != 1)) return false;
        String[] opts = this.modeSetting.getOptions();
        if (opts == null || opts.length == 0) return false;
        if (DrawUtil.hovered(this.position.x, this.position.y - 3.5, 265.0, this.height, mouseX, mouseY)) {
            int cur = clampInt((int) Math.round(this.modeSetting.getInput()), 0, opts.length - 1);
            int next;
            if (button == 0) {
                next = (cur + 1 >= opts.length) ? 0 : (cur + 1);
            } else {
                next = (cur - 1 < 0) ? (opts.length - 1) : (cur - 1);
            }
            this.modeSetting.setValueWithEvent(next);
            this.ownerModule.guiUpdate();
            return true;
        }
        return false;
    }
}

static final class SliderComp extends ValueComp {
    private final ravens4.module.setting.impl.SliderSetting sliderSetting;
    private final StopWatch smoothWatch = new StopWatch();
    private final RiseTextBox valueBox;
    boolean dragging = false;
    private double targetPercent = 0.0;
    private double smoothPercent = 0.0;
    private double barStartX = 0.0;
    private boolean trackHovered = false;

    SliderComp(ravens4.module.Module mod,
               ravens4.module.setting.impl.SliderSetting ss,
               ravens4.module.setting.impl.GroupSetting group,
               boolean conditional) {
        super(mod, ss, group, conditional);
        this.sliderSetting = ss;
        String initStr = formatSliderValue(ss.getInput());
        this.valueBox = new RiseTextBox(
                new Vec2d(0.0, 0.0),
                RiseFont.F16,
                Palette.TEXT_SUB,
                RiseTextBox.ALIGN_LEFT,
                initStr,
                32.0f,
                "-1234567890."
        );
        syncFromSetting();
        this.smoothPercent = this.targetPercent;
    }

    void syncFromSetting() {
        double min = this.sliderSetting.getMin();
        double max = this.sliderSetting.getMax();
        if (max <= min) {
            this.targetPercent = 0.0;
        } else {
            this.targetPercent = clampDouble((this.sliderSetting.getInput() - min) / (max - min), 0.0, 1.0);
        }
    }

    private static String formatSliderValue(double v) {
        String s = String.valueOf(Math.round(v * 1000.0) / 1000.0);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }

    @Override
    void render(Vec2d pos, int mouseX, int mouseY, float partialTicks) {
        this.position = pos;
        String name = this.sliderSetting.getName();
        String valStr = formatSliderValue(this.sliderSetting.getInput());
        float labelOffset = RiseFont.F16.width(name) + 7.0f;

        this.trackHovered = DrawUtil.hovered(pos.x + labelOffset - 5.0, pos.y - 3.5, 110.0, this.height, mouseX, mouseY);
        RiseFont.F16.draw(name, pos.x, pos.y, Palette.rgbWithAlpha(Palette.TEXT_SUB, this.alpha));

        this.valueBox.setPos(pos.x + labelOffset + 105.0, pos.y);
        if (!this.valueBox.focused) {
            this.valueBox.setText(valStr);
            syncFromSetting();
        }
        this.valueBox.width = 34.0f;
        this.valueBox.color = Palette.withAlpha(Palette.TEXT_SUB, this.alpha);
        this.valueBox.draw();

        this.barStartX = pos.x + labelOffset;
        DrawUtil.roundedRect(this.barStartX, pos.y + 1.5, 100.0, 2.0, 1.0, Palette.withAlpha(Palette.BACK_MAIN, this.alpha));

        if (modernGui != null && modernGui.scaleValue < 0.8) {
            this.dragging = false;
        }
        if (this.dragging) {
            this.targetPercent = clampDouble((mouseX - this.barStartX) / 100.0, 0.0, 1.0);
            double min = this.sliderSetting.getMin();
            double max = this.sliderSetting.getMax();
            double nextVal = min + (max - min) * this.targetPercent;
            this.sliderSetting.setValueWithEvent(nextVal);
            this.ownerModule.guiUpdate();
        }

        long elapsed = Math.min(this.smoothWatch.elapsed(), 200L);
        for (long i = 0; i <= elapsed; i++) {
            this.smoothPercent = (this.smoothPercent * 29.0 + this.targetPercent) / 30.0;
        }
        double knobX = this.barStartX + this.smoothPercent * 100.0;
        DrawUtil.roundedRect(knobX - 2.5, pos.y, 5.0, 5.0, 2.5, Palette.withAlpha(activeTheme.firstColor(), this.alpha));
        this.smoothWatch.reset();
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.position == null) return false;
        if (button == 0 && this.trackHovered) {
            this.dragging = true;
            this.valueBox.focused = false;
            return true;
        }
        this.valueBox.mouseClicked(mouseX, mouseY, button);
        return this.valueBox.focused;
    }

    @Override
    void mouseReleased() {
        this.dragging = false;
    }

    @Override
    void renderBloom() {
        if (this.position == null) return;
        double knobX = this.barStartX + this.smoothPercent * 100.0;
        DrawUtil.roundedRect(knobX - 2.5, this.position.y, 5.0, 5.0, 2.5, Palette.withAlpha(activeTheme.firstColor(), this.alpha));
    }

    @Override
    void keyTyped(char typedChar, int keyCode) {
        if (!this.valueBox.focused) return;
        if (keyCode == 28) {
            if (!this.valueBox.text.trim().isEmpty()) {
                try {
                    double parsed = Double.parseDouble(this.valueBox.text.trim());
                    this.sliderSetting.setValueWithEvent(parsed);
                    this.ownerModule.guiUpdate();
                } catch (Exception ignored) {
                }
            }
            this.valueBox.focused = false;
            syncFromSetting();
            return;
        }
        this.valueBox.keyTyped(typedChar, keyCode);
    }

    @Override
    boolean hasFocusedText() {
        return this.valueBox.focused;
    }

    @Override
    boolean unfocusText() {
        if (this.valueBox.focused) {
            this.valueBox.focused = false;
            return true;
        }
        return false;
    }
}

static final class ColorComp extends ValueComp {
    private final ravens4.module.setting.impl.ColorSetting colorSetting;
    private boolean expanded = false;
    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private float hueCursorX = 0.0f;
    private final Vec2f svCursor = new Vec2f(104.0f, 0.0f);
    private Color pureHueColor = Color.RED;
    private boolean initializedHsb = false;

    ColorComp(ravens4.module.Module mod,
              ravens4.module.setting.impl.ColorSetting cs,
              ravens4.module.setting.impl.GroupSetting group,
              boolean conditional) {
        super(mod, cs, group, conditional);
        this.colorSetting = cs;
    }

    private void ensureHsbSynced(double pickerW, double svHeight, double hueBarWidth) {
        if (this.initializedHsb) return;
        float[] hsb = Color.RGBtoHSB(this.colorSetting.getRed(), this.colorSetting.getGreen(), this.colorSetting.getBlue(), null);
        this.hueCursorX = (float) (hsb[0] * hueBarWidth);
        this.pureHueColor = Color.getHSBColor(hsb[0], 1.0f, 1.0f);
        this.svCursor.x = (float) (hsb[1] * pickerW);
        this.svCursor.y = (float) ((1.0f - hsb[2]) * svHeight);
        this.initializedHsb = true;
    }

    @Override
    void render(Vec2d pos, int mouseX, int mouseY, float partialTicks) {
        this.position = pos;
        double pickerW = 105.0;
        double pickerH = 120.0;
        double svHeight = pickerH * 0.55;
        double margin = 8.5;
        double hueBarWidth = pickerW - margin * 2.0;
        ensureHsbSynced(pickerW, svHeight, hueBarWidth);

        String name = this.colorSetting.getName();
        float labelOffset = RiseFont.F16.width(name) + 4.0f;
        RiseFont.F16.draw(name, pos.x, pos.y, Palette.rgbWithAlpha(Palette.TEXT_SUB, this.alpha));

        Color currentCol = new Color(this.colorSetting.getRed(), this.colorSetting.getGreen(), this.colorSetting.getBlue(), 255);
        DrawUtil.roundedRect(pos.x + labelOffset, pos.y, 15.0, 7.0, 2.5, Palette.withAlpha(currentCol, this.alpha));

        this.height = this.expanded ? 110.0 : 15.0;
        if (!this.expanded) {
            return;
        }

        int guiRound = 12;
        double px = pos.x + 18.5 + labelOffset;
        double py = pos.y + 0.5;

        DrawUtil.dropShadow(10, px, py, pickerW, pickerH - 15.0, 40.0, guiRound * 2.0);
        DrawUtil.roundedRect(px - 0.5, pos.y, pickerW, pickerH - 15.0, guiRound - 3.0, Palette.BACK_SIDEBAR);
        DrawUtil.roundedRect(px, py, pickerW - 1.0, pickerH - 16.0, guiRound - 3.0, Palette.BACK_MAIN);

        DrawUtil.roundedGradientH(px, py, pickerW - 1.0, svHeight, 7.0, Color.WHITE, this.pureHueColor);
        DrawUtil.roundedGradientV(px - 0.5, py, pickerW, svHeight + 0.5, 0.5, new Color(0, 0, 0, 0), Color.BLACK);

        double hueX = px + margin;
        double hueY = py + margin + svHeight - 5.0;
        DrawUtil.rainbowRectangle(hueX, hueY + 2.5, hueBarWidth, guiRound - 5.0);

        double previewY = py + svHeight + margin + margin + guiRound - 11.0;
        DrawUtil.dropShadow(16, px + margin, previewY, 15.0, 15.5, 40.0, guiRound / 2.0);

        if (this.draggingSV) {
            this.svCursor.x = clampFloat((float) (mouseX - px), 0.0f, (float) pickerW);
            this.svCursor.y = clampFloat((float) (mouseY - py), 0.0f, (float) svHeight);
            float sat = clampFloat((float) (this.svCursor.x / pickerW), 0.0f, 1.0f);
            float bri = clampFloat(1.0f - (float) (this.svCursor.y / svHeight), 0.0f, 1.0f);
            float hueDeg = clampFloat((float) ((this.hueCursorX / hueBarWidth) * 360.0), 0.0f, 360.0f);
            this.colorSetting.setFromHSB(hueDeg, sat, bri);
            currentCol = new Color(this.colorSetting.getRed(), this.colorSetting.getGreen(), this.colorSetting.getBlue(), 255);
        } else if (this.draggingHue) {
            this.hueCursorX = clampFloat((float) (mouseX - hueX), 0.0f, (float) hueBarWidth);
            float hueNorm = clampFloat((float) (this.hueCursorX / hueBarWidth), 0.0f, 1.0f);
            this.pureHueColor = Color.getHSBColor(hueNorm, 1.0f, 1.0f);
            float sat = clampFloat((float) (this.svCursor.x / pickerW), 0.0f, 1.0f);
            float bri = clampFloat(1.0f - (float) (this.svCursor.y / svHeight), 0.0f, 1.0f);
            this.colorSetting.setFromHSB(hueNorm * 360.0f, sat, bri);
            currentCol = new Color(this.colorSetting.getRed(), this.colorSetting.getGreen(), this.colorSetting.getBlue(), 255);
        }

        DrawUtil.roundedRect(px + margin, previewY, 15.0, 15.5, 3.5, currentCol);

        double hkSize = guiRound - 1.0;
        double hkX = hueX + this.hueCursorX - guiRound / 2.0 + 0.5;
        double hkY = hueY + 0.5;
        DrawUtil.roundedRect(hkX, hkY, hkSize, hkSize, guiRound / 3.0 + 1.0, this.pureHueColor);
        DrawUtil.roundedOutline(hkX, hkY, hkSize, hkSize, guiRound / 5.0 + 1.0, 1.0, Color.BLACK);

        DrawUtil.roundedRect(px - 1.0 + this.svCursor.x - 2.5, py - 1.0 + this.svCursor.y - 2.5, 7.0, 7.0, 3.5, Color.WHITE);
        DrawUtil.roundedRect(px - 0.5 + this.svCursor.x - 2.5, py - 0.5 + this.svCursor.y - 2.5, 6.0, 6.0, 3.0, Color.BLACK);
        DrawUtil.roundedRect(px + this.svCursor.x - 2.5, py + this.svCursor.y - 2.5, 5.0, 5.0, 2.5, currentCol);

        double textBaseX = px + margin * 2.0 + 15.0;
        RiseFont.F17.drawCentered(String.valueOf(currentCol.getRed()), textBaseX + margin, previewY, Palette.TEXT_SUB.getRGB());
        RiseFont.F17.drawCentered(String.valueOf(currentCol.getGreen()), textBaseX + 30.0, previewY, Palette.TEXT_SUB.getRGB());
        RiseFont.F17.drawCentered(String.valueOf(currentCol.getBlue()), textBaseX + margin * 6.0, previewY, Palette.TEXT_SUB.getRGB());
        String hex = toHexColor(currentCol.getRed(), currentCol.getGreen(), currentCol.getBlue());
        RiseFont.F13.draw(hex, textBaseX, previewY + 13.0, new Color(55, 59, 61).getRGB());
    }

    private static String toHexColor(int r, int g, int b) {
        return "#" + byteHex(r) + byteHex(g) + byteHex(b);
    }

    private static String byteHex(int v) {
        String h = Integer.toHexString(clampInt(v, 0, 255)).toUpperCase();
        return h.length() == 1 ? ("0" + h) : h;
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.position == null || button != 0) return false;
        float labelOffset = RiseFont.F16.width(this.colorSetting.getName()) + 4.0f;
        double px = this.position.x + 18.5 + labelOffset;
        double pickerW = 105.0;
        double pickerH = 120.0;
        double svHeight = pickerH * 0.55;

        if (this.expanded) {
            if (DrawUtil.hovered(px, this.position.y, pickerW, svHeight, mouseX, mouseY)) {
                this.draggingSV = true;
                return true;
            }
            if (DrawUtil.hovered(px, this.position.y + svHeight, pickerW, 20.0, mouseX, mouseY)) {
                this.draggingHue = true;
                return true;
            }
            if (DrawUtil.hovered(px, this.position.y, pickerW, pickerH - 15.0, mouseX, mouseY)) {
                return true;
            }
        }
        if (DrawUtil.hovered(this.position.x, this.position.y - 3.5, 220.0, 15.0, mouseX, mouseY)) {
            this.expanded = !this.expanded;
            return true;
        }
        return false;
    }

    @Override
    void mouseReleased() {
        this.draggingSV = false;
        this.draggingHue = false;
    }
}

static final class TextComp extends ValueComp {
    private final ravens4.module.setting.impl.TextSetting textSetting;
    final RiseTextBox textBox;

    TextComp(ravens4.module.Module mod,
             ravens4.module.setting.impl.TextSetting ts,
             ravens4.module.setting.impl.GroupSetting group,
             boolean conditional) {
        super(mod, ts, group, conditional);
        this.textSetting = ts;
        this.height = 28.0;
        this.textBox = new RiseTextBox(
                new Vec2d(0.0, 0.0),
                RiseFont.F16,
                Color.WHITE,
                RiseTextBox.ALIGN_LEFT,
                ts.getPlaceholder() == null || ts.getPlaceholder().isEmpty() ? "Type here..." : ts.getPlaceholder(),
                230.5f
        );
        this.textBox.maxLength = Math.max(1, ts.getMaxLength());
        this.textBox.setText(ts.getText());
    }

    @Override
    void render(Vec2d pos, int mouseX, int mouseY, float partialTicks) {
        this.position = pos;
        this.height = 28.0;
        String label = this.textSetting.getName();
        RiseFont.F16.draw(label, pos.x, pos.y, Palette.rgbWithAlpha(Palette.TEXT_SUB, this.alpha));

        if (!this.textBox.focused && !this.textBox.text.equals(this.textSetting.getText())) {
            this.textBox.setText(this.textSetting.getText());
        }
        this.textBox.color = Palette.withAlpha(Color.WHITE, this.alpha);
        this.textBox.setPos(pos.x, pos.y + 14.0);
        this.textBox.width = 230.5f;
        this.textBox.draw();
        if (this.textBox.focused) {
            this.textSetting.setText(this.textBox.text);
        }
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.position == null) return false;
        this.textBox.mouseClicked(mouseX, mouseY, button);
        return this.textBox.focused;
    }

    @Override
    void keyTyped(char typedChar, int keyCode) {
        if (!this.textBox.focused) return;
        if (keyCode == 28) {
            this.textSetting.setText(this.textBox.text);
            this.textSetting.submit();
            this.textBox.focused = false;
            return;
        }
        this.textBox.keyTyped(typedChar, keyCode);
        this.textSetting.setText(this.textBox.text);
    }

    @Override
    boolean hasFocusedText() {
        return this.textBox.focused;
    }

    @Override
    boolean unfocusText() {
        if (this.textBox.focused) {
            this.textBox.focused = false;
            return true;
        }
        return false;
    }
}

static final class KeyComp extends ValueComp {
    private final ravens4.module.setting.impl.KeySetting keySetting;
    boolean listening = false;

    KeyComp(ravens4.module.Module mod,
            ravens4.module.setting.impl.KeySetting ks,
            ravens4.module.setting.impl.GroupSetting group,
            boolean conditional) {
        super(mod, ks, group, conditional);
        this.keySetting = ks;
    }

    @Override
    void render(Vec2d pos, int mouseX, int mouseY, float partialTicks) {
        this.position = pos;
        String prefix = this.keySetting.getName() + ":";
        int col = Palette.rgbWithAlpha(Palette.TEXT_SUB, Math.min(this.alpha, Palette.TEXT_SUB.getAlpha()));
        RiseFont.F16.draw(prefix, pos.x, pos.y, col);
        String keyStr = this.listening ? "[Press a key...]" : KeyNameUtil.nameOf(this.keySetting.getKey());
        int valCol = this.listening
                ? Palette.rgbWithAlpha(activeTheme.firstColor(), this.alpha)
                : col;
        RiseFont.F16.draw(keyStr, pos.x + RiseFont.F16.width(prefix) + 3.0, pos.y, valCol);
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.position == null) return false;
        if (this.listening) {
            if (button >= 2) {
                this.keySetting.setKey(1000 + button);
                this.listening = false;
                keyToggleCooldown.reset();
                syncClickGuiKeybind();
                return true;
            }
            if (button == 1) {
                this.keySetting.setKey(0);
                this.listening = false;
                syncClickGuiKeybind();
                return true;
            }
        }
        if (button == 0 && DrawUtil.hovered(this.position.x, this.position.y - 3.5, 265.0, this.height, mouseX, mouseY)) {
            this.listening = !this.listening;
            return true;
        }
        return false;
    }

    @Override
    void keyTyped(char typedChar, int keyCode) {
        if (!this.listening) return;
        if (keyCode == 1 || keyCode == 14 || keyCode == 211) {
            this.keySetting.setKey(0);
        } else if (keyCode > 0) {
            this.keySetting.setKey(keyCode);
        }
        this.listening = false;
        keyToggleCooldown.reset();
        syncClickGuiKeybind();
    }

    @Override
    boolean hasFocusedText() {
        return this.listening;
    }

    @Override
    boolean unfocusText() {
        if (this.listening) {
            this.keySetting.setKey(0);
            this.listening = false;
            syncClickGuiKeybind();
            return true;
        }
        return false;
    }
}

static final class ModBindComp extends ValueComp {
    boolean listening = false;

    ModBindComp(ravens4.module.Module mod) {
        super(mod, null, null, false);
    }

    @Override
    boolean isVisibleNow() {
        return this.ownerModule != null && !(this.ownerModule instanceof RiseClickGuiModule);
    }

    @Override
    void render(Vec2d pos, int mouseX, int mouseY, float partialTicks) {
        this.position = pos;
        String prefix = "Keybind:";
        int col = Palette.rgbWithAlpha(Palette.TEXT_SUB, Math.min(this.alpha, Palette.TEXT_SUB.getAlpha()));
        RiseFont.F16.draw(prefix, pos.x, pos.y, col);
        String keyStr = this.listening ? "[Press a key...]" : KeyNameUtil.nameOf(this.ownerModule.getKeycode());
        int valCol = this.listening
                ? Palette.rgbWithAlpha(activeTheme.firstColor(), this.alpha)
                : col;
        RiseFont.F16.draw(keyStr, pos.x + RiseFont.F16.width(prefix) + 3.0, pos.y, valCol);
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.position == null) return false;
        if (this.listening) {
            if (button >= 2) {
                this.ownerModule.setBind(1000 + button);
                this.listening = false;
                keyToggleCooldown.reset();
                return true;
            }
            if (button == 1) {
                this.ownerModule.setBind(0);
                this.listening = false;
                return true;
            }
        }
        if (button == 0 && DrawUtil.hovered(this.position.x, this.position.y - 3.5, 265.0, this.height, mouseX, mouseY)) {
            this.listening = !this.listening;
            return true;
        }
        return false;
    }

    @Override
    void keyTyped(char typedChar, int keyCode) {
        if (!this.listening) return;
        if (keyCode == 1 || keyCode == 14 || keyCode == 211) {
            this.ownerModule.setBind(0);
        } else if (keyCode > 0) {
            this.ownerModule.setBind(keyCode);
        }
        this.listening = false;
        keyToggleCooldown.reset();
    }

    @Override
    boolean hasFocusedText() {
        return this.listening;
    }

    @Override
    boolean unfocusText() {
        if (this.listening) {
            this.ownerModule.setBind(0);
            this.listening = false;
            return true;
        }
        return false;
    }
}

static final class GroupComp extends ValueComp {
    private final ravens4.module.setting.impl.GroupSetting groupSetting;

    GroupComp(ravens4.module.Module mod, ravens4.module.setting.impl.GroupSetting gs) {
        super(mod, gs, null, false);
        this.groupSetting = gs;
    }

    @Override
    void render(Vec2d pos, int mouseX, int mouseY, float partialTicks) {
        this.position = pos;
        String label = this.groupSetting.getName();
        String arrow = this.groupSetting.isOpened() ? "\u25be" : "\u25b8";
        Color textCol = this.groupSetting.isOpened() ? activeTheme.firstColor() : Palette.TEXT_SUB;
        RiseFont.F16.draw(label + " " + arrow, pos.x, pos.y, Palette.rgbWithAlpha(textCol, this.alpha));
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.position == null || (button != 0 && button != 1)) return false;
        if (DrawUtil.hovered(this.position.x, this.position.y - 3.5, 265.0, this.height, mouseX, mouseY)) {
            this.groupSetting.setOpened(!this.groupSetting.isOpened());
            return true;
        }
        return false;
    }
}

static final class ModCard {
    final ravens4.module.Module mod;
    final Vec2f size = new Vec2f(283.0f, 38.0f);
    boolean expanded = false;
    final List<ValueComp> valueComps = new ArrayList<ValueComp>();
    Vec2d pos;
    final Animation hoverAnim = new Animation(Easing.LINEAR, 50L);
    final Animation expandAnim = new Animation(Easing.LINEAR, 200L);
    final Animation valueAlphaAnim = new Animation(Easing.LINEAR, 5000L);
    boolean holdingPress = false;

    ModCard(ravens4.module.Module mod) {
        this.mod = mod;
        this.expandAnim.setValue(38.0);
        this.valueAlphaAnim.setValue(0.0);
        rebuildValueComps();
    }

    void rebuildValueComps() {
        this.valueComps.clear();
        if (this.mod == null || this.mod.getSettings() == null) return;
        for (ravens4.module.setting.Setting s : this.mod.getSettings()) {
            if (s == null || s instanceof ravens4.module.setting.impl.DescriptionSetting) {
                continue;
            }
            boolean cond = isConditionalRiseSetting(this.mod, s);
            if (s instanceof ravens4.module.setting.impl.GroupSetting) {
                this.valueComps.add(new GroupComp(this.mod, (ravens4.module.setting.impl.GroupSetting) s));
            } else if (s instanceof ravens4.module.setting.impl.ButtonSetting) {
                ravens4.module.setting.impl.ButtonSetting bs = (ravens4.module.setting.impl.ButtonSetting) s;
                this.valueComps.add(new BoolComp(this.mod, bs, bs.group, cond));
            } else if (s instanceof ravens4.module.setting.impl.SliderSetting) {
                ravens4.module.setting.impl.SliderSetting ss = (ravens4.module.setting.impl.SliderSetting) s;
                if (ss.isString) {
                    this.valueComps.add(new ModeComp(this.mod, ss, ss.groupSetting, cond));
                } else {
                    this.valueComps.add(new SliderComp(this.mod, ss, ss.groupSetting, cond));
                }
            } else if (s instanceof ravens4.module.setting.impl.ColorSetting) {
                ravens4.module.setting.impl.ColorSetting cs = (ravens4.module.setting.impl.ColorSetting) s;
                this.valueComps.add(new ColorComp(this.mod, cs, cs.groupSetting, cond));
            } else if (s instanceof ravens4.module.setting.impl.TextSetting) {
                ravens4.module.setting.impl.TextSetting ts = (ravens4.module.setting.impl.TextSetting) s;
                this.valueComps.add(new TextComp(this.mod, ts, ts.group, cond));
            } else if (s instanceof ravens4.module.setting.impl.KeySetting) {
                ravens4.module.setting.impl.KeySetting ks = (ravens4.module.setting.impl.KeySetting) s;
                this.valueComps.add(new KeyComp(this.mod, ks, ks.group, cond));
            }
        }
        if (!(this.mod instanceof RiseClickGuiModule)) {
            this.valueComps.add(new ModBindComp(this.mod));
        }
    }

    private static boolean isConditionalRiseSetting(ravens4.module.Module m, ravens4.module.setting.Setting s) {
        if (!(m instanceof RiseInterfaceModule)) return false;
        RiseInterfaceModule im = (RiseInterfaceModule) m;
        return s == im.modernColorMode || s == im.modernFont || s == im.modernCustomFont
                || s == im.modernShaderEffect || s == im.modernDropShadow || s == im.modernSidebar
                || s == im.modernParticlesOnKill || s == im.modernBackground || s == im.modernClientName
                || s == im.blurRadius || s == im.blurCompression
                || s == im.bloomRadius || s == im.bloomCompression || s == im.backgroundAlpha;
    }

    void syncSliders() {
        for (int i = 0; i < this.valueComps.size(); i++) {
            ValueComp vc = this.valueComps.get(i);
            if (vc instanceof SliderComp) {
                ((SliderComp) vc).syncFromSetting();
            }
        }
    }

    void render(Vec2d cardPos, int mouseX, int mouseY, float partialTicks, boolean showCategorySuffix) {
        this.pos = cardPos;
        float totalH = 38.0f;
        boolean onScreen = cardPos != null
                && modernGui != null
                && !(cardPos.y + this.size.y < modernGui.position.y)
                && !(cardPos.y > modernGui.position.y + modernGui.size.y);

        if (this.expanded || !this.expandAnim.finished) {
            this.mod.guiUpdate();
        }

        if (onScreen) {
            DrawUtil.roundedRect(cardPos.x, cardPos.y, this.size.x, this.size.y, 6.0, Palette.CARD_BG);
            Color baseText = Palette.withAlpha(Palette.TEXT_BRIGHT, this.mod.isEnabled() ? 255 : 200);
            boolean hov = DrawUtil.hovered(cardPos.x, cardPos.y, this.size.x, this.size.y, mouseX, mouseY);
            this.hoverAnim.run(hov ? (this.holdingPress ? 35.0 : 20.0) : 0.0);
            int hovAlpha = clampInt((int) this.hoverAnim.value, 0, 255);
            if (hovAlpha > 0) {
                DrawUtil.roundedRect(cardPos.x, cardPos.y, this.size.x, this.size.y, 6.0, new Color(0, 0, 0, hovAlpha));
            }

            String modName = this.mod.getName();
            if (showCategorySuffix) {
                String catLabel = "(" + SidebarCategory.fromModule(this.mod).displayName() + ")";
                RiseFont.F15.draw(
                        catLabel,
                        cardPos.x + RiseFont.F20.width(modName) + 10.0,
                        cardPos.y + 10.0,
                        Palette.rgbWithAlpha(baseText, 64)
                );
            }

            if (bindingTargetCard == this) {
                RiseFont.F13.drawRight("[Press key...]", cardPos.x + this.size.x - 7.0, cardPos.y + 10.0, activeTheme.firstColor().getRGB());
            } else if (this.mod.getKeycode() > 0) {
                String bindLabel = "[" + KeyNameUtil.nameOf(this.mod.getKeycode()) + "]";
                RiseFont.F13.drawRight(bindLabel, cardPos.x + this.size.x - 7.0, cardPos.y + 10.0, Palette.rgbWithAlpha(baseText, 55));
            }

            int titleCol = this.mod.isEnabled()
                    ? activeTheme.accentAt(0.0, cardPos.y / 5.0).getRGB()
                    : baseText.getRGB();
            RiseFont.F20.draw(modName, cardPos.x + 6.0, cardPos.y + 8.0, titleCol);
            RiseFont.F15.draw(Loc.moduleDescription(this.mod), cardPos.x + 6.0, cardPos.y + 25.0, Palette.rgbWithAlpha(baseText, 70));
            this.size.x = 283.0f;
        }

        if (!this.expandAnim.finished || this.expanded) {
            for (int i = 0; i < this.valueComps.size(); i++) {
                ValueComp vc = this.valueComps.get(i);
                if (!vc.isVisibleNow()) continue;
                int baseAlpha = (vc.position == null)
                        ? 0
                        : (vc.position.y < cardPos.y + this.expandAnim.value + 15.0 ? (int) this.valueAlphaAnim.value : 0);
                vc.alpha = vc.isIndented() ? Math.max(baseAlpha - 40, 0) : clampInt(baseAlpha, 0, 255);
                if (onScreen) {
                    double indent = vc.isIndented() ? 10.0 : 0.0;
                    vc.render(new Vec2d(cardPos.x + 6.0 + indent, cardPos.y + totalH + 1.0), mouseX, mouseY, partialTicks);
                }
                totalH += (float) vc.height;
            }
            totalH -= 1.0f;
        }

        this.expandAnim.setDuration(Math.max(1L, Math.min((long) totalH * 3L, 450L)));
        this.expandAnim.setEasing(Easing.EASE_OUT_EXPO);
        this.expandAnim.run(this.expanded ? (double) totalH : 38.0);
        this.size.y = (float) this.expandAnim.value;

        this.valueAlphaAnim.setDuration(Math.max(1L, this.expanded ? this.expandAnim.duration / 2L : this.expandAnim.duration / 3L));
        this.valueAlphaAnim.run(this.expanded ? 255.0 : 0.0);
    }

    void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.pos == null || modernGui == null) return;
        if (this.pos.y + this.size.y < modernGui.position.y || this.pos.y > modernGui.position.y + modernGui.size.y) {
            return;
        }
        boolean inGui = DrawUtil.hovered(modernGui.position.x, modernGui.position.y, modernGui.size.x, modernGui.size.y, mouseX, mouseY);
        boolean inHeader = DrawUtil.hovered(this.pos.x, this.pos.y, this.size.x, 35.0, mouseX, mouseY);

        if (inHeader) {
            this.holdingPress = true;
            if (button == 0 && inGui) {
                this.mod.toggle();
                return;
            } else if (button == 1) {
                this.mod.guiUpdate();
                boolean hasAnyVisible = false;
                for (int i = 0; i < this.valueComps.size(); i++) {
                    if (this.valueComps.get(i).isVisibleNow()) {
                        hasAnyVisible = true;
                        break;
                    }
                }
                if (hasAnyVisible) {
                    this.expanded = !this.expanded;
                    for (int i = 0; i < this.valueComps.size(); i++) {
                        ValueComp vc = this.valueComps.get(i);
                        if (vc instanceof SliderComp) {
                            ((SliderComp) vc).dragging = false;
                        }
                    }
                }
                return;
            } else if (button == 2 && inGui) {
                bindingTargetCard = (bindingTargetCard == this) ? null : this;
                return;
            }
        }

        if (this.expanded) {
            for (int i = 0; i < this.valueComps.size(); i++) {
                ValueComp vc = this.valueComps.get(i);
                if (!vc.isVisibleNow()) continue;
                if (vc.mouseClicked(mouseX, mouseY, button)) {
                    break;
                }
            }
        }
    }

    void mouseReleased() {
        this.holdingPress = false;
        if (this.expanded) {
            for (int i = 0; i < this.valueComps.size(); i++) {
                this.valueComps.get(i).mouseReleased();
            }
        }
    }

    void renderBloom() {
        if (this.pos == null || modernGui == null) return;
        if (this.pos.y + this.size.y < modernGui.position.y || this.pos.y > modernGui.position.y + modernGui.size.y) {
            return;
        }
        if (!this.expandAnim.finished || this.expanded) {
            for (int i = 0; i < this.valueComps.size(); i++) {
                ValueComp vc = this.valueComps.get(i);
                if (!vc.isVisibleNow()) continue;
                vc.renderBloom();
            }
        }
    }

    void keyTyped(char typedChar, int keyCode) {
        if (this.pos == null || modernGui == null) return;
        if (!this.expanded) return;
        for (int i = 0; i < this.valueComps.size(); i++) {
            ValueComp vc = this.valueComps.get(i);
            if (!vc.isVisibleNow()) continue;
            vc.keyTyped(typedChar, keyCode);
        }
    }

    boolean hasFocusedText() {
        if (!this.expanded) return false;
        for (int i = 0; i < this.valueComps.size(); i++) {
            ValueComp vc = this.valueComps.get(i);
            if (vc.isVisibleNow() && vc.hasFocusedText()) return true;
        }
        return false;
    }

    boolean unfocusText() {
        boolean any = false;
        for (int i = 0; i < this.valueComps.size(); i++) {
            if (this.valueComps.get(i).unfocusText()) {
                any = true;
            }
        }
        return any;
    }
}

static final class SidebarItem {
    final SidebarCategory category;
    private final Animation selectAnim = new Animation(Easing.LINEAR, 200L);
    private float drawX = 0.0f;
    private float drawY = 0.0f;

    SidebarItem(SidebarCategory category) {
        this.category = category;
    }

    void render(double yOffset, double sidebarAnimatedWidth, int sidebarAlpha, SidebarCategory activeCat) {
        if (modernGui == null) return;
        this.drawX = (float) ((double) modernGui.position.x - (69.0 - sidebarAnimatedWidth) - 21.0);
        this.drawY = (float) ((double) modernGui.position.y + yOffset) + 16.0f;

        boolean selected = (activeCat == this.category);
        this.selectAnim.setDuration(200L);
        this.selectAnim.run(selected ? 255.0 : 0.0);

        String catName = this.category.displayName();
        double iconW = 9.0;
        double pillW = RiseFont.F16.width(catName) + 8.0 + iconW;

        int pillAlpha = clampInt((int) Math.min(this.selectAnim.value, (double) sidebarAlpha), 0, 255);
        if (pillAlpha > 0) {
            Color accentDark = activeTheme.accentAt(0.0, this.drawY / 5.0).darker();
            DrawUtil.roundedRect(this.drawX, this.drawY - 5.5, pillW + 8.0, 15.0, 5.0, Palette.withAlpha(accentDark, pillAlpha));
        }

        int contentAlpha = clampInt(Math.min(selected ? 255 : 200, sidebarAlpha), 0, 255);
        int textColor = new Color(255, 255, 255, contentAlpha).getRGB();
        float slideOffset = (float) (this.selectAnim.value / 80.0);

        DrawUtil.drawCategoryIcon(this.category, this.drawX + slideOffset + 3.0f, this.drawY - 0.5f, textColor);
        RiseFont.F16.draw(catName, this.drawX + slideOffset + 7.0f + (float) iconW, this.drawY, textColor);
    }

    void renderBloom(double sidebarAlpha) {
        int pillAlpha = clampInt((int) Math.min(this.selectAnim.value, sidebarAlpha), 0, 255);
        if (pillAlpha <= 4) return;
        String catName = this.category.displayName();
        double iconW = 9.0;
        double pillW = RiseFont.F16.width(catName) + 8.0 + iconW;
        Color accentDark = activeTheme.accentAt(0.0, this.drawY / 5.0).darker();
        DrawUtil.roundedRect(this.drawX, this.drawY - 5.0, pillW + 8.0, 14.0, 5.0, Palette.withAlpha(accentDark, pillAlpha));
    }

    void mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0 || modernGui == null) return;
        if (DrawUtil.hovered(this.drawX - 11.0f, this.drawY - 5.0f, 82.0, 19.0, mouseX, mouseY)) {
            modernGui.switchCategory(this.category);
        }
    }
}

static final class Sidebar {
    final List<SidebarItem> items = new ArrayList<SidebarItem>();
    final double width = 100.0;
    private double alpha = 255.0;
    private boolean expanded = true;
    private long lastUpdateMs = 0L;
    private final Animation slideAnim = new Animation(Easing.EASE_OUT_EXPO, 300L);
    private final Animation shadowAnim = new Animation(Easing.LINEAR, 300L);

    Sidebar() {
        for (SidebarCategory sc : SidebarCategory.values()) {
            this.items.add(new SidebarItem(sc));
        }
    }

    void renderBackground(SidebarCategory activeCat, boolean pageCollapsible) {
        if (modernGui == null) return;
        Color bgCol = Palette.withAlpha(Palette.BACK_SIDEBAR, clampInt((int) this.alpha, 0, 255));
        this.slideAnim.setDuration(this.expanded ? 700L : 2000L);
        this.slideAnim.run(this.expanded ? 0.0 : -this.width / 1.5);

        double curWidth = this.width + this.slideAnim.value;
        DrawUtil.roundedRectEdges(
                modernGui.position.x,
                modernGui.position.y,
                curWidth,
                modernGui.size.y,
                modernGui.rounding,
                bgCol,
                true, false, false, true
        );

        this.shadowAnim.setDuration(1000L);
        this.shadowAnim.run(pageCollapsible ? 255.0 : 0.0);
        int shAlpha = clampInt((int) Math.min(this.shadowAnim.value, this.alpha / 7.0), 0, 255);
        if (shAlpha > 0) {
            DrawUtil.gradientRectH(
                    modernGui.position.x + curWidth,
                    modernGui.position.y,
                    30.0,
                    modernGui.size.y,
                    new Color(0, 0, 0, shAlpha),
                    new Color(0, 0, 0, 0)
            );
        }
    }

    void renderItems(int mouseX, int mouseY, SidebarCategory activeCat, boolean pageCollapsible) {
        if (modernGui == null) return;
        long now = client.time();
        if (this.lastUpdateMs == 0L) {
            this.lastUpdateMs = now;
        }
        long dt = Math.min(Math.max(0L, now - this.lastUpdateMs), 100L);
        this.lastUpdateMs = now;

        this.expanded = ((!keybinds.isMouseDown(0) || this.expanded)
                && DrawUtil.hovered(modernGui.position.x - 200.0f, modernGui.position.y, this.expanded ? 310.0 : 210.0, modernGui.size.y, mouseX, mouseY))
                || !pageCollapsible;

        if (this.expanded) {
            this.alpha = Math.min(255.0, this.alpha + dt * 2.0);
        } else {
            this.alpha = Math.max(0.0, this.alpha - dt * 1.5);
        }

        double curWidth = this.width + this.slideAnim.value;
        double yOff = 10.0;
        for (int i = 0; i < this.items.size(); i++) {
            yOff += 19.5;
            this.items.get(i).render(yOff, curWidth, (int) this.alpha, activeCat);
        }

        float logoX = (float) (modernGui.position.x + 9.0 + this.slideAnim.value);
        float logoY = modernGui.position.y + (24.75f - RiseFont.F42.height() / 2.0f);
        int mainAlpha = clampInt((int) this.alpha, 0, 255);
        int verAlpha = clampInt((int) Math.min(this.alpha, 200.0), 0, 255);
        RiseFont.F32.draw(CLIENT_NAME, logoX + 5.0f, logoY + 2.0f, new Color(255, 255, 255, mainAlpha).getRGB());
        RiseFont.F16.draw(
                CLIENT_VERSION,
                logoX + 5.0f + RiseFont.F32.width(CLIENT_NAME) + 1.0f,
                logoY,
                Palette.rgbWithAlpha(activeTheme.firstColor(), verAlpha)
        );
    }

    void renderBloom() {
        for (int i = 0; i < this.items.size(); i++) {
            this.items.get(i).renderBloom(this.alpha);
        }
    }

    void mouseClicked(float mouseX, float mouseY, int button) {
        if (this.alpha <= 0.0) return;
        for (int i = 0; i < this.items.size(); i++) {
            this.items.get(i).mouseClicked(mouseX, mouseY, button);
        }
    }
}

static abstract class GuiPage {
    abstract void render(int mouseX, int mouseY, float partialTicks, int wheel);

    /** The page's scroll state, so the GUI can feed it the mouse wheel; null if the page does not scroll. */
    ScrollHandler scrollHandler() {
        return null;
    }

    void keyTyped(char typedChar, int keyCode) {
    }

    void mouseClicked(int mouseX, int mouseY, int button) {
    }

    void mouseReleased() {
    }

    void renderBloom() {
    }

    void onOpen() {
    }

    boolean allowQuickSearchRedirect() {
        return true;
    }

    boolean collapsibleSidebar() {
        return false;
    }

    boolean hasFocusedText() {
        return false;
    }

    boolean unfocusText() {
        return false;
    }
}

static final class SearchPage extends GuiPage {
    final RiseTextBox searchBar = new RiseTextBox(
            new Vec2d(200.0, 200.0),
            RiseFont.F20,
            Color.WHITE,
            RiseTextBox.ALIGN_CENTER,
            "Type to search...",
            150.0f
    );
    private final StopWatch fadeWatch = new StopWatch();
    final ScrollHandler scroll = new ScrollHandler();

    @Override
    ScrollHandler scrollHandler() {
        return this.scroll;
    }
    List<ModCard> filteredCards = new ArrayList<ModCard>();
    private double headerAlpha = 255.0;
    private boolean typedSinceOpen = false;

    @Override
    void onOpen() {
        this.searchBar.placeholder = Loc.tr("ui.search.text");
        this.filteredCards = filterModules(this.searchBar.text);
        this.typedSinceOpen = false;
        this.fadeWatch.reset();
    }

    @Override
    void render(int mouseX, int mouseY, float partialTicks, int wheel) {
        if (modernGui == null) return;
        this.searchBar.placeholder = Loc.tr("ui.search.text");

        long elapsed = Math.min(this.fadeWatch.elapsed(), 100L);
        if (this.scroll.target < 0.0) {
            this.headerAlpha -= elapsed * 4.0;
        } else {
            this.headerAlpha += elapsed * 4.0;
        }
        this.headerAlpha = clampDouble(this.headerAlpha, 0.0, 255.0);
        this.fadeWatch.reset();

        this.searchBar.color = Palette.withAlpha(Palette.TEXT_BRIGHT, (int) this.headerAlpha);
        double centerContentX = modernGui.position.x + modernGui.sidebar.width + (modernGui.size.x - modernGui.sidebar.width) / 2.0;
        this.searchBar.setPos(centerContentX, modernGui.position.y + 17.0 + this.scroll.scroll);
        this.searchBar.draw();

        this.scroll.update(wheel, true);
        double y = modernGui.position.y + 35.0 + this.scroll.scroll;
        double contentH = 0.0;
        double cardX = modernGui.position.x + modernGui.sidebar.width + 8.0;

        for (int i = 0; i < this.filteredCards.size(); i++) {
            ModCard card = this.filteredCards.get(i);
            card.render(new Vec2d(cardX, y), mouseX, mouseY, partialTicks, true);
            y += card.size.y + 7.0;
            contentH += card.size.y + 7.0;
        }

        this.scroll.setMaxScrollRange(-contentH + modernGui.size.y - 37.0);
        double sbX = modernGui.position.x + modernGui.size.x - 4.0;
        double sbY = modernGui.position.y + 35.0;
        this.scroll.renderScrollBar(sbX, sbY, modernGui.size.y - 42.0);
    }

    @Override
    void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 200 || keyCode == 208) {
            return;
        }
        boolean valueEditing = hasFocusedValueText();
        if (!this.typedSinceOpen && !valueEditing && typedChar >= 32 && typedChar != 127) {
            this.typedSinceOpen = true;
            this.searchBar.setText("");
        }
        if (!valueEditing) {
            this.searchBar.focused = true;
            this.searchBar.keyTyped(typedChar, keyCode);
            this.scroll.target = 0.0;
            this.filteredCards = filterModules(this.searchBar.text);
        }
        for (int i = 0; i < this.filteredCards.size(); i++) {
            this.filteredCards.get(i).keyTyped(typedChar, keyCode);
        }
    }

    @Override
    void mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = 0; i < this.filteredCards.size(); i++) {
            this.filteredCards.get(i).mouseClicked(mouseX, mouseY, button);
        }
        this.searchBar.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    void mouseReleased() {
        for (int i = 0; i < this.filteredCards.size(); i++) {
            this.filteredCards.get(i).mouseReleased();
        }
    }

    @Override
    void renderBloom() {
        for (int i = 0; i < this.filteredCards.size(); i++) {
            this.filteredCards.get(i).renderBloom();
        }
    }

    private boolean hasFocusedValueText() {
        for (int i = 0; i < this.filteredCards.size(); i++) {
            if (this.filteredCards.get(i).hasFocusedText()) return true;
        }
        return false;
    }

    @Override
    boolean hasFocusedText() {
        return this.searchBar.focused || hasFocusedValueText();
    }

    @Override
    boolean unfocusText() {
        boolean u = false;
        if (this.searchBar.focused) {
            this.searchBar.focused = false;
            u = true;
        }
        for (int i = 0; i < this.filteredCards.size(); i++) {
            if (this.filteredCards.get(i).unfocusText()) {
                u = true;
            }
        }
        return u;
    }

    private List<ModCard> filterModules(String rawQuery) {
        List<ModCard> out = new ArrayList<ModCard>();
        if (modernGui == null) return out;
        String q = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        if (q.isEmpty()) {
            out.addAll(modernGui.allCards);
            return out;
        }
        String[] parts = q.split(" ");
        List<String> tokens = new ArrayList<String>(Arrays.asList(parts));
        String compact = q.replace(" ", "");
        if (!tokens.contains(compact)) {
            tokens.add(compact);
        }
        for (int t = 0; t < tokens.size(); t++) {
            String tok = tokens.get(t);
            if (tok.isEmpty()) continue;
            for (int i = 0; i < modernGui.allCards.size(); i++) {
                ModCard c = modernGui.allCards.get(i);
                String nameNorm = c.mod.getName().toLowerCase().replace(" ", "");
                if (nameNorm.contains(tok) && !out.contains(c)) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}

static final class CategoryPage extends GuiPage {
    final SidebarCategory category;
    final ScrollHandler scroll = new ScrollHandler();

    @Override
    ScrollHandler scrollHandler() {
        return this.scroll;
    }
    final List<ModCard> cards = new ArrayList<ModCard>();

    CategoryPage(SidebarCategory category) {
        this.category = category;
    }

    @Override
    void onOpen() {
        this.cards.clear();
        if (modernGui == null) return;
        for (int i = 0; i < modernGui.allCards.size(); i++) {
            ModCard c = modernGui.allCards.get(i);
            if (this.category.matchesModule(c.mod)) {
                this.cards.add(c);
            }
        }
    }

    @Override
    void render(int mouseX, int mouseY, float partialTicks, int wheel) {
        if (modernGui == null) return;
        this.scroll.update(wheel, true);
        double y = modernGui.position.y + 7.0 + this.scroll.scroll;
        double contentH = 0.0;
        double cardX = modernGui.position.x + modernGui.sidebar.width + 8.0;

        for (int i = 0; i < this.cards.size(); i++) {
            ModCard card = this.cards.get(i);
            card.render(new Vec2d(cardX, y), mouseX, mouseY, partialTicks, false);
            y += card.size.y + 7.0;
            contentH += card.size.y + 7.0;
        }

        double pad = 7.0;
        double sbX = modernGui.position.x + modernGui.size.x - 4.0;
        double sbY = modernGui.position.y + pad;
        this.scroll.renderScrollBar(sbX, sbY, modernGui.size.y - pad * 2.0);
        this.scroll.setMaxScrollRange(-contentH + modernGui.size.y - 7.0);
    }

    @Override
    void keyTyped(char typedChar, int keyCode) {
        for (int i = 0; i < this.cards.size(); i++) {
            this.cards.get(i).keyTyped(typedChar, keyCode);
        }
    }

    @Override
    void mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = 0; i < this.cards.size(); i++) {
            this.cards.get(i).mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    void mouseReleased() {
        for (int i = 0; i < this.cards.size(); i++) {
            this.cards.get(i).mouseReleased();
        }
    }

    @Override
    void renderBloom() {
        for (int i = 0; i < this.cards.size(); i++) {
            this.cards.get(i).renderBloom();
        }
    }

    @Override
    boolean hasFocusedText() {
        for (int i = 0; i < this.cards.size(); i++) {
            if (this.cards.get(i).hasFocusedText()) return true;
        }
        return false;
    }

    @Override
    boolean unfocusText() {
        boolean u = false;
        for (int i = 0; i < this.cards.size(); i++) {
            if (this.cards.get(i).unfocusText()) u = true;
        }
        return u;
    }
}

static final class ColorFilterSelector {
    final KeyColor keyColor;
    double lastX, lastY, lastW;
    final Animation activeDimAnim = new Animation(Easing.EASE_OUT_QUINT, 500L);
    final Animation bloomAnim = new Animation(Easing.EASE_OUT_QUINT, 500L);

    ColorFilterSelector(KeyColor keyColor) {
        this.keyColor = keyColor;
        this.activeDimAnim.setValue(1.0);
        this.bloomAnim.setValue(0.0);
    }

    void render(double x, double y, double width) {
        DrawUtil.roundedRect(x, y, width, 17.0, 5.0, new Color(18, 21, 30));
        DrawUtil.roundedRect(x + 0.5, y + 0.5, width - 1.0, 16.0, 4.0, this.keyColor.color);
        int dimAlpha = clampInt((int) ((1.0 - this.activeDimAnim.value) * 128.0), 0, 255);
        if (dimAlpha > 0) {
            DrawUtil.roundedRect(x, y, width, 17.0, 5.0, new Color(25, 25, 25, dimAlpha));
        }
        this.lastX = x;
        this.lastY = y;
        this.lastW = width;
    }

    void renderBloom() {
        int bAlpha = clampInt((int) (this.bloomAnim.value * 255.0), 0, 255);
        if (bAlpha <= 4) return;
        DrawUtil.roundedRect(this.lastX, this.lastY, this.lastW, 17.0, 5.0, new Color(18, 21, 30, bAlpha));
        DrawUtil.roundedRect(this.lastX + 0.5, this.lastY + 0.5, this.lastW - 1.0, 16.0, 4.0, Palette.withAlpha(this.keyColor.color, bAlpha));
    }
}

static final class ThemeCard {
    final RiseTheme theme;
    double hitX, hitY, hitW;
    final Animation animX = new Animation(Easing.EASE_OUT_QUINT, 500L);
    final Animation animY = new Animation(Easing.EASE_OUT_QUINT, 500L);
    final Animation alphaAnim = new Animation(Easing.EASE_OUT_QUINT, 500L);
    final Animation selectGlowAnim = new Animation(Easing.EASE_OUT_QUINT, 500L);

    ThemeCard(RiseTheme theme) {
        this.theme = theme;
        this.alphaAnim.setValue(255.0);
    }

    void render(double scrollOffset, double width) {
        int alpha = clampInt((int) this.alphaAnim.value, 0, 255);
        if (alpha <= 2) return;
        boolean selected = (this.theme == activeTheme);
        Color cardCol = selected ? new Color(15, 19, 26, alpha) : new Color(18, 21, 30, alpha);

        double x = this.animX.value;
        double y = this.animY.value + scrollOffset;
        this.hitX = x;
        this.hitY = y;
        this.hitW = width;

        if (modernGui != null && (y + 55.0 < modernGui.position.y || y > modernGui.position.y + modernGui.size.y)) {
            this.selectGlowAnim.run(selected ? 255.0 : 0.0);
            return;
        }

        DrawUtil.roundedRect(x, y, width, 50.0, 10.0, cardCol);

        Color c1 = Palette.withAlpha(this.theme.firstColor(), alpha);
        Color c2 = Palette.withAlpha(this.theme.secondColor(), alpha);
        if (this.theme.triColor) {
            Color c3 = Palette.withAlpha(this.theme.thirdColor(), alpha);
            double halfW = width / 2.0;
            DrawUtil.roundedGradientH(x, y, halfW + 8.0, 38.0, 9.0, c1, c2);
            DrawUtil.roundedGradientH(x + halfW - 8.0, y, halfW + 8.0, 38.0, 9.0, c2, c3);
            DrawUtil.gradientRectH(x + halfW - 9.0, y, 18.0, 30.0, c2, c2);
        } else {
            DrawUtil.roundedGradientH(x, y, width, 38.0, 9.0, c1, c2);
        }

        DrawUtil.rect(x, y + 30.0, width, 10.0, cardCol);

        int nameColor = selected
                ? Palette.rgbWithAlpha(activeTheme.firstColor(), alpha)
                : new Color(255, 255, 255, alpha).getRGB();
        RiseFont.F16.drawCentered(this.theme.themeName, x + width / 2.0, y + 37.0, nameColor);

        this.selectGlowAnim.run(selected ? 255.0 : 0.0);
    }

    void renderBloom(double scrollOffset, double width) {
        int alpha = clampInt((int) this.alphaAnim.value, 0, 255);
        int glow = clampInt((int) Math.min(this.selectGlowAnim.value, (double) alpha), 0, 255);
        if (glow <= 4 || modernGui == null || modernGui.scaleValue <= 0.8) return;

        double x = this.animX.value;
        double y = this.animY.value + scrollOffset;
        if (y + 55.0 < modernGui.position.y || y > modernGui.position.y + modernGui.size.y) {
            return;
        }

        Color c1 = Palette.withAlpha(this.theme.firstColor(), glow);
        Color c2 = Palette.withAlpha(this.theme.secondColor(), glow);
        if (this.theme.triColor) {
            Color c3 = Palette.withAlpha(this.theme.thirdColor(), glow);
            double halfW = width / 2.0;
            DrawUtil.roundedGradientH(x, y, halfW + 6.0, 30.0, 9.0, c1, c2);
            DrawUtil.roundedGradientH(x + halfW - 6.0, y, halfW + 6.0, 30.0, 9.0, c2, c3);
        } else {
            DrawUtil.roundedGradientH(x + 1.0, y, width - 2.0, 30.0, 9.0, c1, c2);
        }
        RiseFont.F16.drawCentered(this.theme.themeName, x + width / 2.0, y + 37.0, c1.getRGB());
    }
}

static final class ThemesPage extends GuiPage {
    private final List<ThemeCard> allThemes = new ArrayList<ThemeCard>();
    private List<ThemeCard> visibleThemes = new ArrayList<ThemeCard>();
    private final List<ColorFilterSelector> selectors = new ArrayList<ColorFilterSelector>();
    private final ScrollHandler scroll = new ScrollHandler();

    @Override
    ScrollHandler scrollHandler() {
        return this.scroll;
    }
    private ColorFilterSelector activeSelector = null;

    ThemesPage() {
        for (RiseTheme t : RiseTheme.values()) {
            this.allThemes.add(new ThemeCard(t));
        }
        for (KeyColor kc : KeyColor.values()) {
            this.selectors.add(new ColorFilterSelector(kc));
        }
        this.visibleThemes = new ArrayList<ThemeCard>(this.allThemes);
    }

    @Override
    void onOpen() {
        for (int i = 0; i < this.allThemes.size(); i++) {
            this.allThemes.get(i).alphaAnim.setValue(255.0);
        }
        this.visibleThemes = new ArrayList<ThemeCard>(this.allThemes);
        this.activeSelector = null;
        this.scroll.reset();
        snapPositionsImmediately();
    }

    void snapPositionsImmediately() {
        if (modernGui == null) return;
        double sideW = modernGui.sidebar.width;
        double cardW = (modernGui.size.x - sideW - 29.0) / 3.0;
        for (int i = 0; i < this.visibleThemes.size(); i++) {
            ThemeCard tc = this.visibleThemes.get(i);
            tc.animX.setValue(modernGui.position.x + sideW + 7.0 + (7.0 + cardW) * (i % 3));
            tc.animY.setValue(modernGui.position.y + 44.0 + Math.floor(i / 3.0) * 57.0 + 60.0);
        }
    }

    @Override
    void render(int mouseX, int mouseY, float partialTicks, int wheel) {
        if (modernGui == null) return;
        this.scroll.update(wheel, true);
        double rows = Math.ceil((double) this.visibleThemes.size() / 3.0);
        this.scroll.setMaxScrollRange(-57.0 * Math.max(0.0, rows - 3.0));

        double pad = 7.0;
        double sbX = modernGui.position.x + modernGui.size.x - 4.0;
        double sbY = modernGui.position.y + pad;
        this.scroll.renderScrollBar(sbX, sbY, modernGui.size.y - pad * 2.0);

        double sideW = modernGui.sidebar.width;
        double filterStartY = modernGui.position.y + 44.0 + this.scroll.scroll;
        double cardW = (modernGui.size.x - sideW - 29.0) / 3.0;
        double selW = (modernGui.size.x - sideW - 43.0) / 5.0;

        RiseFont.F16.drawRight(
                Loc.tr("ui.themes.text"),
                modernGui.position.x + modernGui.size.x - 20.0,
                modernGui.position.y + 20.0 + this.scroll.scroll,
                new Color(255, 255, 255, 128).getRGB()
        );

        for (int i = 0; i < this.selectors.size(); i++) {
            ColorFilterSelector sel = this.selectors.get(i);
            double sx = modernGui.position.x + sideW + 7.0 + (7.0 + selW) * (i % 5);
            double sy = filterStartY + Math.floor(i / 5.0) * 24.0;
            sel.render(sx, sy, selW);
            sel.activeDimAnim.run(this.activeSelector == null || this.activeSelector == sel ? 1.0 : 0.0);
            sel.bloomAnim.run(this.activeSelector == sel ? 1.0 : 0.0);
        }

        for (int i = 0; i < this.visibleThemes.size(); i++) {
            ThemeCard tc = this.visibleThemes.get(i);
            tc.animX.run(modernGui.position.x + sideW + 7.0 + (7.0 + cardW) * (i % 3));
            tc.animY.run(modernGui.position.y + 44.0 + Math.floor(i / 3.0) * 57.0 + 60.0);
        }

        for (int i = 0; i < this.allThemes.size(); i++) {
            ThemeCard tc = this.allThemes.get(i);
            if (tc.alphaAnim.value > 0.5) {
                tc.render(this.scroll.scroll, cardW);
            }
            tc.alphaAnim.run(this.visibleThemes.contains(tc) ? 255.0 : 0.0);
        }
    }

    @Override
    void renderBloom() {
        if (modernGui == null) return;
        double sideW = modernGui.sidebar.width;
        double cardW = (modernGui.size.x - sideW - 29.0) / 3.0;
        for (int i = 0; i < this.selectors.size(); i++) {
            this.selectors.get(i).renderBloom();
        }
        for (int i = 0; i < this.allThemes.size(); i++) {
            ThemeCard tc = this.allThemes.get(i);
            if (tc.alphaAnim.value > 0.5) {
                tc.renderBloom(this.scroll.scroll, cardW);
            }
        }
    }

    @Override
    void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        for (int i = 0; i < this.visibleThemes.size(); i++) {
            ThemeCard tc = this.visibleThemes.get(i);
            if (DrawUtil.hovered(tc.hitX, tc.hitY, tc.hitW, 50.0, mouseX, mouseY)) {
                activeTheme = tc.theme;
                savePersistedState();
                ravens4.utility.ClientTheme.applyToRenderModules();
                return;
            }
        }
        for (int i = 0; i < this.selectors.size(); i++) {
            ColorFilterSelector sel = this.selectors.get(i);
            if (DrawUtil.hovered(sel.lastX, sel.lastY, sel.lastW, 17.0, mouseX, mouseY)) {
                this.activeSelector = (this.activeSelector == sel) ? null : sel;
                rebuildFilteredThemes();
                return;
            }
        }
    }

    private void rebuildFilteredThemes() {
        this.visibleThemes.clear();
        if (this.activeSelector == null) {
            this.visibleThemes.addAll(this.allThemes);
            for (int i = 0; i < this.visibleThemes.size(); i++) {
                this.visibleThemes.get(i).alphaAnim.run(255.0);
            }
            return;
        }
        for (int i = 0; i < this.allThemes.size(); i++) {
            ThemeCard tc = this.allThemes.get(i);
            if (tc.theme.keyColors.contains(this.activeSelector.keyColor)) {
                this.visibleThemes.add(tc);
                tc.alphaAnim.run(255.0);
            }
        }
        this.scroll.target = 0.0;
    }
}

static final class ConfigTile {
    final String profileName;
    final String actionText;
    final Vec2f size = new Vec2f(86.45f, 86.45f);
    final Animation hoverAnim = new Animation(Easing.LINEAR, 200L);
    Vec2f lastPos;

    ConfigTile(String profileName, String actionText) {
        this.profileName = profileName;
        this.actionText = actionText;
    }

    void render(float x, float y, int mouseX, int mouseY) {
        this.lastPos = new Vec2f(x, y);
        if (modernGui == null) return;
        if (x + this.size.x < modernGui.position.x + modernGui.sidebar.width || x > modernGui.position.x + modernGui.size.x) {
            return;
        }
        boolean hov = DrawUtil.hovered(x, y, this.size.x, this.size.y, mouseX, mouseY);
        this.hoverAnim.run(hov ? 75.0 : 0.0);

        DrawUtil.roundedRect(x, y, this.size.x, this.size.y, 8.0, Palette.CARD_BG);
        int extraAlpha = clampInt((int) this.hoverAnim.value, 0, 255);
        if (extraAlpha > 0) {
            DrawUtil.roundedRect(x, y, this.size.x, this.size.y, 8.0, new Color(0, 0, 0, extraAlpha));
        }

        boolean activeProf = (ravens4.Raven.currentProfile != null
                && this.profileName.equalsIgnoreCase(ravens4.Raven.currentProfile.getName()));
        String trimmedTitle = trimTextToWidth(RiseFont.F20, capitalizeFirst(this.profileName), this.size.x - 16.0f);
        float titleY = y + this.size.y / 2.0f - RiseFont.F20.height() / 2.0f - 1.5f;
        int titleCol = activeProf ? activeTheme.firstColor().getRGB() : Palette.TEXT_SUB.getRGB();
        RiseFont.F20.drawCentered(trimmedTitle, x + this.size.x / 2.0f, titleY, titleCol);
        RiseFont.F15.drawCentered(
                activeProf ? "Active Profile" : this.actionText,
                x + this.size.x / 2.0f,
                titleY + RiseFont.F20.height() + 5.0f,
                Palette.TEXT_MUTED.getRGB()
        );
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || this.lastPos == null) return false;
        if (DrawUtil.hovered(this.lastPos.x, this.lastPos.y, this.size.x, this.size.y, mouseX, mouseY)) {
            if (ravens4.Raven.profileManager != null) {
                ravens4.Raven.profileManager.loadProfile(this.profileName);
                ToggleToastManager.push("Profile", "Loaded profile " + this.profileName, 1200);
            }
            return true;
        }
        return false;
    }

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String trimTextToWidth(RiseFont f, String text, float maxW) {
        if (f.width(text) <= maxW) return text;
        String cur = text;
        while (!cur.isEmpty() && f.width(cur + "..") > maxW) {
            cur = cur.substring(0, cur.length() - 1);
        }
        return cur + "..";
    }
}

static final class ConfigsPage extends GuiPage {
    private static final int GAP = 10;
    private final ScrollHandler pageScroll = new ScrollHandler();

    @Override
    ScrollHandler scrollHandler() {
        return this.pageScroll;
    }
    private final List<ConfigTile> configTiles = new ArrayList<ConfigTile>();

    @Override
    void onOpen() {
        this.configTiles.clear();
        if (ravens4.Raven.profileManager != null && ravens4.Raven.profileManager.profiles != null) {
            for (ravens4.utility.profile.Profile p : ravens4.Raven.profileManager.profiles) {
                if (p != null && p.getName() != null) {
                    this.configTiles.add(new ConfigTile(p.getName(), "Click to load"));
                }
            }
        }
    }

    @Override
    void render(int mouseX, int mouseY, float partialTicks, int wheel) {
        if (modernGui == null) return;

        double contentLeft = modernGui.position.x + modernGui.sidebar.width;
        double contentWidth = modernGui.size.x - modernGui.sidebar.width;

        this.pageScroll.update(wheel, true);

        float curX = (float) (contentLeft + GAP);
        float curY = (float) (modernGui.position.y + this.pageScroll.scroll + GAP);
        float bannerW = (float) (contentWidth - GAP * 2);
        float bannerH = 90.0f;

        DrawUtil.roundedRect(curX, curY, bannerW, bannerH, 10.0, new Color(0, 0, 0, 100));
        DrawUtil.roundedGradientH(
                curX, curY, bannerW, bannerH, 10.0,
                Palette.withAlpha(activeTheme.firstColor(), 38),
                Palette.withAlpha(activeTheme.secondColor(), 38)
        );
        RiseFont.F24.draw("CaS", curX + 12.0, curY + 18.0, Color.WHITE.getRGB());
        RiseFont.F15.draw("Load and manage your Raven bS profiles seamlessly.", curX + 12.0, curY + 40.0, Palette.TEXT_SUB.getRGB());

        curY += bannerH + GAP * 2;

        RiseFont.F18.draw("Your Configs", curX, curY, Color.WHITE.getRGB());
        RiseFont.F18.draw(
                String.valueOf(this.configTiles.size()),
                curX + RiseFont.F18.width("Your Configs") + GAP / 2.0f,
                curY,
                activeTheme.firstColor().getRGB()
        );
        curY += GAP + RiseFont.F18.height();

        if (this.configTiles.isEmpty()) {
            RiseFont.F15.draw("No configs found.", curX, curY, Palette.TEXT_MUTED.getRGB());
            curY += RiseFont.F15.height() + GAP;
        } else {
            float tileSize = 86.45f;
            float step = tileSize + GAP;
            int cols = Math.max(1, (int) Math.floor((bannerW + GAP) / step));
            for (int i = 0; i < this.configTiles.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                this.configTiles.get(i).render(curX + col * step, curY + row * step, mouseX, mouseY);
            }
            int rows = (this.configTiles.size() + cols - 1) / cols;
            curY += rows * step;
        }

        double totalHeight = curY - this.pageScroll.scroll - modernGui.position.y;
        double pad = 7.0;
        double sbX = modernGui.position.x + modernGui.size.x - 4.0;
        double sbY = modernGui.position.y + pad;
        this.pageScroll.renderScrollBar(sbX, sbY, modernGui.size.y - pad * 2.0);
        this.pageScroll.setMaxScrollRange(-totalHeight + modernGui.size.y - 7.0);
    }

    @Override
    void mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = 0; i < this.configTiles.size(); i++) {
            if (this.configTiles.get(i).mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
    }
}

static final class ScriptsPage extends GuiPage {
    private static final int GAP = 10;
    private final ScrollHandler pageScroll = new ScrollHandler();

    @Override
    ScrollHandler scrollHandler() {
        return this.pageScroll;
    }
    private final List<ModCard> scriptCards = new ArrayList<ModCard>();

    private static boolean isPinnedManager(ravens4.module.Module mod) {
        if (mod == null || mod.getName() == null) return false;
        return "Manager".equalsIgnoreCase(mod.getName().trim());
    }

    @Override
    void onOpen() {
        this.scriptCards.clear();
        if (modernGui != null) {
            ModCard pinned = null;
            for (int i = 0; i < modernGui.allCards.size(); i++) {
                ModCard c = modernGui.allCards.get(i);
                if (SidebarCategory.SCRIPTS.matchesModule(c.mod)) {
                    if (pinned == null && isPinnedManager(c.mod)) {
                        pinned = c;
                    } else {
                        this.scriptCards.add(c);
                    }
                }
            }
            // Manager は常にリストの一番上に固定
            if (pinned != null) {
                this.scriptCards.add(0, pinned);
            }
        }
    }

    @Override
    void render(int mouseX, int mouseY, float partialTicks, int wheel) {
        if (modernGui == null) return;

        double contentLeft = modernGui.position.x + modernGui.sidebar.width;
        double contentWidth = modernGui.size.x - modernGui.sidebar.width;

        this.pageScroll.update(wheel, true);

        float curX = (float) (contentLeft + GAP);
        float curY = (float) (modernGui.position.y + this.pageScroll.scroll + GAP);
        float bannerW = (float) (contentWidth - GAP * 2);
        float bannerH = 90.0f;

        DrawUtil.roundedRect(curX, curY, bannerW, bannerH, 10.0, new Color(0, 0, 0, 100));
        DrawUtil.roundedGradientH(
                curX, curY, bannerW, bannerH, 10.0,
                Palette.withAlpha(activeTheme.firstColor(), 38),
                Palette.withAlpha(activeTheme.secondColor(), 38)
        );
        RiseFont.F24.draw("Local Hub", curX + 12.0, curY + 18.0, Color.WHITE.getRGB());
        RiseFont.F15.draw("Manage your custom scripts seamlessly.", curX + 12.0, curY + 40.0, Palette.TEXT_SUB.getRGB());
        String fpStatus = "Font Prefix ({g}): " + (isFontPrefixEnabled() ? "ON" : "OFF");
        RiseFont.F15.draw(fpStatus, curX + 12.0, curY + 60.0, activeTheme.firstColor().getRGB());

        curY += bannerH + GAP * 2;

        RiseFont.F18.draw("Your Scripts", curX, curY, Color.WHITE.getRGB());
        RiseFont.F18.draw(
                String.valueOf(this.scriptCards.size()),
                curX + RiseFont.F18.width("Your Scripts") + GAP / 2.0f,
                curY,
                activeTheme.firstColor().getRGB()
        );
        curY += GAP + RiseFont.F18.height();

        double cardX = contentLeft + 8.0;
        for (int i = 0; i < this.scriptCards.size(); i++) {
            ModCard sc = this.scriptCards.get(i);
            sc.render(new Vec2d(cardX, curY), mouseX, mouseY, partialTicks, false);
            curY += sc.size.y + 7.0f;
        }

        double totalHeight = curY - this.pageScroll.scroll - modernGui.position.y;
        double pad = 7.0;
        double sbX = modernGui.position.x + modernGui.size.x - 4.0;
        double sbY = modernGui.position.y + pad;
        this.pageScroll.renderScrollBar(sbX, sbY, modernGui.size.y - pad * 2.0);
        this.pageScroll.setMaxScrollRange(-totalHeight + modernGui.size.y - 7.0);
    }

    @Override
    void keyTyped(char typedChar, int keyCode) {
        for (int i = 0; i < this.scriptCards.size(); i++) {
            this.scriptCards.get(i).keyTyped(typedChar, keyCode);
        }
    }

    @Override
    void mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = 0; i < this.scriptCards.size(); i++) {
            this.scriptCards.get(i).mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    void mouseReleased() {
        for (int i = 0; i < this.scriptCards.size(); i++) {
            this.scriptCards.get(i).mouseReleased();
        }
    }

    @Override
    void renderBloom() {
        for (int i = 0; i < this.scriptCards.size(); i++) {
            this.scriptCards.get(i).renderBloom();
        }
    }

    @Override
    boolean hasFocusedText() {
        for (int i = 0; i < this.scriptCards.size(); i++) {
            if (this.scriptCards.get(i).hasFocusedText()) return true;
        }
        return false;
    }

    @Override
    boolean unfocusText() {
        boolean u = false;
        for (int i = 0; i < this.scriptCards.size(); i++) {
            if (this.scriptCards.get(i).unfocusText()) u = true;
        }
        return u;
    }
}

static final class LanguageCard {
    final RiseLang lang;
    double lastOffsetY = 0.0;

    LanguageCard(RiseLang lang) {
        this.lang = lang;
    }

    void render(double yOffset) {
        if (modernGui == null) return;
        this.lastOffsetY = yOffset;
        double x = modernGui.position.x + modernGui.sidebar.width + 8.0;
        double y = modernGui.position.y + yOffset;
        DrawUtil.roundedRect(x, y, 285.0, 38.0, 6.0, Palette.CARD_BG);

        boolean selected = (activeLang == this.lang);
        int nameCol = selected
                ? activeTheme.accentAt(0.0, modernGui.position.y / 5.0).getRGB()
                : Palette.TEXT_BRIGHT.getRGB();
        RiseFont.F20.draw(this.lang.englishName, x + 10.0, y + 9.0, nameCol);
        RiseFont.F17.draw(this.lang.localName, x + 10.0, y + 24.0, Palette.rgbWithAlpha(Palette.TEXT_BRIGHT, 100));

        double fx = x + 17.0 + RiseFont.F20.width(this.lang.englishName);
        double fy = y + 7.5;
        DrawUtil.roundedRect(fx - 0.5, fy - 0.5, 16.0, 12.0, 2.5, new Color(0, 0, 0, 120));
        DrawUtil.rect(fx + 0.5, fy + 0.5, 4.6, 10.0, this.lang.flag1);
        DrawUtil.rect(fx + 5.1, fy + 0.5, 4.8, 10.0, this.lang.flag2);
        DrawUtil.rect(fx + 9.9, fy + 0.5, 4.6, 10.0, this.lang.flag3);
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (modernGui == null) return false;
        double x = modernGui.position.x + modernGui.sidebar.width + 8.0;
        double y = modernGui.position.y + this.lastOffsetY;
        if (DrawUtil.hovered(x, y, 285.0, 38.0, mouseX, mouseY)) {
            activeLang = this.lang;
            savePersistedState();
            return true;
        }
        return false;
    }
}

static final class LanguagePage extends GuiPage {
    private final List<LanguageCard> items = new ArrayList<LanguageCard>();
    private final ScrollHandler scroll = new ScrollHandler();

    @Override
    ScrollHandler scrollHandler() {
        return this.scroll;
    }

    LanguagePage() {
        for (RiseLang l : RiseLang.values()) {
            this.items.add(new LanguageCard(l));
        }
    }

    @Override
    void onOpen() {
        this.scroll.reset();
    }

    @Override
    void render(int mouseX, int mouseY, float partialTicks, int wheel) {
        if (modernGui == null) return;
        this.scroll.update(wheel, true);
        double sideW = modernGui.sidebar.width;

        for (int i = 0; i < this.items.size(); i++) {
            this.items.get(i).render((i + 1) * 46.0 + this.scroll.scroll);
        }

        DrawUtil.roundedRectEdges(
                modernGui.position.x + sideW,
                modernGui.position.y,
                modernGui.size.x - sideW,
                40.0,
                modernGui.rounding,
                Palette.BACK_MAIN,
                true, true, false, false
        );
        RiseFont.F16.drawRight(
                Loc.tr("ui.language.text"),
                modernGui.position.x + modernGui.size.x - 20.0,
                modernGui.position.y + 20.0,
                new Color(255, 255, 255, 128).getRGB()
        );

        double totalH = (this.items.size() + 1) * 46.0;
        this.scroll.setMaxScrollRange(-totalH + modernGui.size.y - 10.0);
        double pad = 7.0;
        this.scroll.renderScrollBar(
                modernGui.position.x + modernGui.size.x - 4.0,
                modernGui.position.y + 42.0,
                modernGui.size.y - 42.0 - pad
        );
    }

    @Override
    void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || modernGui == null) return;
        if (mouseY < modernGui.position.y + 40.0) return;
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i).mouseClicked(mouseX, mouseY)) {
                return;
            }
        }
    }
}

static final class ModernGui {
    final Vec2f position = new Vec2f(-1.0f, -1.0f);
    final Vec2f size = new Vec2f(400.0f, 300.0f);
    final Sidebar sidebar = new Sidebar();
    final Map<SidebarCategory, GuiPage> pages = new HashMap<SidebarCategory, GuiPage>();
    final List<ModCard> allCards = new ArrayList<ModCard>();

    SidebarCategory currentCategory = SidebarCategory.SEARCH;
    GuiPage currentPage;
    GuiPage previousPage;
    GuiPage renderedPage;

    float dragOffsetX = 0.0f;
    float dragOffsetY = 0.0f;
    boolean dragging = false;

    double scaleValue = 0.0;
    /** User chosen size of the window (ClickGUI "Size" setting). */
    double userScale = 1.0;
    /** scaleValue (open/close animation) * userScale: what is actually drawn. */
    double drawScale = 0.0;
    double opacityValue = 0.0;
    int rounding = 12;
    Vec2d scaleOffset = new Vec2d(0.0, 0.0);
    boolean closingRenderActive = false;

    final Animation scaleAnim = new Animation(Easing.EASE_IN_EXPO, 300L);
    final Animation opacityAnim = new Animation(Easing.EASE_IN_EXPO, 300L);
    long pageSwitchTimeMs = 0L;

    ModernGui() {
        SearchPage sp = new SearchPage();
        this.pages.put(SidebarCategory.SEARCH, sp);
        this.pages.put(SidebarCategory.COMBAT, new CategoryPage(SidebarCategory.COMBAT));
        this.pages.put(SidebarCategory.MOVEMENT, new CategoryPage(SidebarCategory.MOVEMENT));
        this.pages.put(SidebarCategory.PLAYER, new CategoryPage(SidebarCategory.PLAYER));
        this.pages.put(SidebarCategory.RENDER, new CategoryPage(SidebarCategory.RENDER));
        this.pages.put(SidebarCategory.EXPLOIT, new CategoryPage(SidebarCategory.EXPLOIT));
        this.pages.put(SidebarCategory.GHOST, new CategoryPage(SidebarCategory.GHOST));
        this.pages.put(SidebarCategory.CONFIGS, new ConfigsPage());
        this.pages.put(SidebarCategory.SCRIPTS, new ScriptsPage());
        this.pages.put(SidebarCategory.THEMES, new ThemesPage());
        this.pages.put(SidebarCategory.LANGUAGE, new LanguagePage());

        this.currentPage = sp;
        this.previousPage = sp;
        this.renderedPage = sp;
    }

    void rebuildModuleComponents() {
        this.allCards.clear();
        List<ravens4.module.Module> mods = getAllClientModules();
        Collections.sort(mods, new Comparator<ravens4.module.Module>() {
            public int compare(ravens4.module.Module a, ravens4.module.Module b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (int i = 0; i < mods.size(); i++) {
            this.allCards.add(new ModCard(mods.get(i)));
        }
        if (this.currentPage != null) {
            this.currentPage.onOpen();
        }
    }

    void initGui() {
        if (this.allCards.isEmpty()) {
            rebuildModuleComponents();
        }
        this.rounding = 12;
        this.closingRenderActive = false;
        this.scaleAnim.reset();
        this.scaleAnim.setValue(0.0);
        this.opacityAnim.reset();
        this.opacityAnim.setValue(0.0);

        int[] disp = client.getDisplaySize();
        float sw = disp[0];
        float sh = disp[1];

        if (this.position.x < 0.0f && this.position.y < 0.0f) {
            try {
                String sx = config.get("rise_gui_x");
                String sy = config.get("rise_gui_y");
                if (sx != null && sy != null) {
                    this.position.x = Float.parseFloat(sx.trim());
                    this.position.y = Float.parseFloat(sy.trim());
                }
            } catch (Exception ignored) {
            }
        }
        if (this.position.x < 0.0f || this.position.y < 0.0f
                || this.position.x + this.size.x > sw
                || this.position.y + this.size.y > sh) {
            this.position.x = sw / 2.0f - this.size.x / 2.0f;
            this.position.y = sh / 2.0f - this.size.y / 2.0f;
        }

        this.previousPage = this.currentPage;
        this.pageSwitchTimeMs = client.time() - 1000L;
        this.currentPage.onOpen();

        for (int i = 0; i < this.allCards.size(); i++) {
            this.allCards.get(i).syncSliders();
        }
    }

    void onGuiClosed() {
        this.dragging = false;
        for (int i = 0; i < this.allCards.size(); i++) {
            this.allCards.get(i).mouseReleased();
        }
    }

    void switchCategory(SidebarCategory nextCat) {
        GuiPage target = this.pages.get(nextCat);
        if (target == null || target == this.currentPage) {
            return;
        }
        unfocusAnyText();
        this.previousPage = this.currentPage;
        this.currentCategory = nextCat;
        this.currentPage = target;
        this.currentPage.onOpen();
        this.pageSwitchTimeMs = client.time();
    }

    void drawGui(int mouseX, int mouseY, float partialTicks, int wheel, boolean shadersOn) {
        renderInternal(mouseX, mouseY, partialTicks, wheel, true, shadersOn);
    }

    void renderClosingOverlay(int mouseX, int mouseY, float partialTicks) {
        boolean shadersOn = interfaceModule != null && interfaceModule.shaders.isToggled();
        renderInternal(mouseX, mouseY, partialTicks, 0, false, shadersOn);
    }

    private void renderInternal(final int rawMouseX, final int rawMouseY, final float partialTicks, final int wheel,
                                final boolean openingOrOpen, final boolean shadersOn) {
        this.size.set(400.0f, 300.0f);
        this.userScale = clickGuiModule != null ? clickGuiModule.getModernScale() : 1.0;

        if (this.dragging && openingOrOpen) {
            if (this.currentPage instanceof ThemesPage) {
                ((ThemesPage) this.currentPage).snapPositionsImmediately();
            }
            // drag by the window centre: it does not move when the window is scaled around it
            this.position.x = rawMouseX + this.dragOffsetX - this.size.x / 2.0f;
            this.position.y = rawMouseY + this.dragOffsetY - this.size.y / 2.0f;
        }

        final int mouseX = toLogicalX(rawMouseX);
        final int mouseY = toLogicalY(rawMouseY);

        this.opacityAnim.setEasing(openingOrOpen ? Easing.EASE_OUT_EXPO : Easing.LINEAR);
        this.opacityAnim.setDuration(openingOrOpen ? 300L : 100L);
        this.opacityAnim.run(openingOrOpen ? 1.0 : 0.0);
        this.opacityValue = this.opacityAnim.value;

        this.scaleAnim.setEasing(openingOrOpen ? Easing.EASE_OUT_EXPO : Easing.LINEAR);
        this.scaleAnim.setDuration(openingOrOpen ? 300L : 100L);
        this.scaleAnim.run(openingOrOpen ? 1.0 : 0.0);
        this.scaleValue = this.scaleAnim.value;

        if (openingOrOpen && this.scaleValue == 0.0) {
            this.scaleValue = 0.01;
        }
        if (!openingOrOpen && this.scaleValue <= 0.005) {
            this.closingRenderActive = false;
            if (clickGuiModule != null && clickGuiModule.isEnabled()) {
                clickGuiModule.setEnabled(false);
            }
            return;
        }

        this.drawScale = this.scaleValue * this.userScale;
        this.scaleOffset = new Vec2d(
                (double) (this.position.x + this.size.x / 2.0f) * (1.0 - this.drawScale),
                (double) (this.position.y + this.size.y / 2.0f) * (1.0 - this.drawScale)
        );

        final long switchDuration = 200L;
        final long switchElapsed = Math.max(0L, client.time() - this.pageSwitchTimeMs);
        this.renderedPage = (switchElapsed >= switchDuration) ? this.currentPage : this.previousPage;

        if (shadersOn) {
            ShaderPipeline.addBlur(new Runnable() {
                public void run() {
                    double bx = position.x * drawScale + scaleOffset.x;
                    double by = position.y * drawScale + scaleOffset.y;
                    double bw = size.x * drawScale;
                    double bh = size.y * drawScale;
                    DrawUtil.roundedRect(bx, by, bw, bh, rounding * drawScale, Color.WHITE);
                }
            });

            ShaderPipeline.addBloom(new Runnable() {
                public void run() {
                    gl.push();
                    if (drawScale != 1.0) {
                        gl.translate(scaleOffset.x, scaleOffset.y, 0.0);
                        gl.scale((float) drawScale, (float) drawScale, 1.0f);
                    }
                    double sx = position.x * drawScale + scaleOffset.x;
                    double sy = position.y * drawScale + scaleOffset.y;
                    double sw = size.x * drawScale;
                    double sh = (size.y - 4.0f) * drawScale;
                    DrawUtil.enableScissor(sx, sy, sw, sh);
                    if (renderedPage != null) {
                        renderedPage.renderBloom();
                    }
                    sidebar.renderBloom();
                    DrawUtil.disableScissor();
                    gl.pop();
                }
            });
        }

        if (openingOrOpen && wheel != 0 && this.currentPage != null
                && DrawUtil.hovered(this.position.x, this.position.y, this.size.x, this.size.y, mouseX, mouseY)) {
            ScrollHandler pageScrollHandler = this.currentPage.scrollHandler();
            if (pageScrollHandler != null) {
                pageScrollHandler.addWheel(wheel);
            }
        }

        ShaderPipeline.addNormal(1, new Runnable() {
            public void run() {
                gl.push();
                if (drawScale != 1.0) {
                    gl.translate(scaleOffset.x, scaleOffset.y, 0.0);
                    gl.scale((float) drawScale, (float) drawScale, 1.0f);
                }

                if (scaleValue > 0.993) {
                    DrawUtil.dropShadow(18, position.x, position.y, size.x, size.y, 30.0, rounding * 1.3);
                }

                DrawUtil.roundedRect(position.x, position.y, size.x, size.y, rounding, Palette.BACK_MAIN);

                reapplyGuiScissor();

                if (renderedPage != null) {
                    renderedPage.render(mouseX, mouseY, partialTicks, 0);
                }

                if (switchElapsed <= switchDuration * 2L) {
                    float fadeProgress = switchElapsed < switchDuration
                            ? 255.0f - (float) switchElapsed * (255.0f / (float) switchDuration)
                            : (float) (switchElapsed - switchDuration) * (255.0f / (float) switchDuration);
                    int overlayAlpha = 255 - clampInt((int) fadeProgress, 0, 255);
                    if (overlayAlpha > 0) {
                        DrawUtil.roundedRect(position.x, position.y, size.x, size.y, rounding, Palette.withAlpha(Palette.BACK_MAIN, overlayAlpha));
                    }
                }

                boolean collapsible = currentPage != null && currentPage.collapsibleSidebar();
                sidebar.renderBackground(currentCategory, collapsible);
                Color ambient = Palette.withAlpha(activeTheme.firstColor(), 1);
                for (int i = 1; i <= 8; i++) {
                    double diameter = i * 50.0;
                    DrawUtil.circle(
                            position.x + sidebar.width,
                            position.y + size.y / 2.0f,
                            diameter / 2.0,
                            ambient
                    );
                }
                sidebar.renderItems(mouseX, mouseY, currentCategory, collapsible);

                DrawUtil.disableScissor();
                gl.pop();
            }
        });
    }

    void reapplyGuiScissor() {
        double sx = this.position.x * this.drawScale + this.scaleOffset.x + 1.0;
        double sy = this.position.y * this.drawScale + this.scaleOffset.y + 1.0;
        double sw = this.size.x * this.drawScale - 2.0;
        double sh = this.size.y * this.drawScale - 2.0;
        DrawUtil.enableScissor(sx, sy, sw, sh);
    }

    /** Mouse position on the 400x300 layout of the window, whatever size the window is drawn at. */
    int toLogicalX(int rawX) {
        double centre = this.position.x + this.size.x / 2.0;
        return (int) Math.round(centre + (rawX - centre) / Math.max(0.01, this.userScale));
    }

    int toLogicalY(int rawY) {
        double centre = this.position.y + this.size.y / 2.0;
        return (int) Math.round(centre + (rawY - centre) / Math.max(0.01, this.userScale));
    }

    void mouseClicked(int rawMouseX, int rawMouseY, int button) {
        int mouseX = toLogicalX(rawMouseX);
        int mouseY = toLogicalY(rawMouseY);
        if (button == 0 && DrawUtil.hovered(this.position.x, this.position.y, this.size.x, 15.0, mouseX, mouseY)) {
            this.dragOffsetX = this.position.x + this.size.x / 2.0f - rawMouseX;
            this.dragOffsetY = this.position.y + this.size.y / 2.0f - rawMouseY;
            this.dragging = true;
            return;
        }
        if (DrawUtil.hovered(this.position.x, this.position.y, this.size.x, this.size.y, mouseX, mouseY)) {
            this.sidebar.mouseClicked(mouseX, mouseY, button);
            if (this.currentPage != null) {
                this.currentPage.mouseClicked(mouseX, mouseY, button);
            }
        } else {
            unfocusAnyText();
        }
    }

    void mouseReleased(int mouseX, int mouseY, int button) {
        this.dragging = false;
        if (this.currentPage != null) {
            this.currentPage.mouseReleased();
        }
    }

    void keyTyped(char typedChar, int keyCode) {
        String lower = String.valueOf(typedChar).toLowerCase();
        if ("abcdefghijklmnopqrstuvwxyz1234567890 ".contains(lower)
                && this.currentPage != null
                && this.currentPage.allowQuickSearchRedirect()
                && !hasActiveTextInput()
                && this.currentCategory != SidebarCategory.SEARCH) {
            switchCategory(SidebarCategory.SEARCH);
        }
        if (this.currentPage != null) {
            this.currentPage.keyTyped(typedChar, keyCode);
        }
    }

    boolean hasActiveTextInput() {
        return this.currentPage != null && this.currentPage.hasFocusedText();
    }

    boolean unfocusAnyText() {
        return this.currentPage != null && this.currentPage.unfocusText();
    }
}

}
