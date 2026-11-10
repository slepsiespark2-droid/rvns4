package ravens4.module;

import ravens4.module.impl.client.ChatCommands;
import ravens4.module.impl.client.CommandLine;
import ravens4.module.impl.client.Gui;
import ravens4.module.impl.client.Relationships;
import ravens4.module.impl.client.Settings;
import ravens4.module.impl.combat.*;
import ravens4.module.impl.movement.*;
import ravens4.module.impl.movement.MovementFix;
import ravens4.module.impl.other.*;
import ravens4.module.impl.player.*;
import ravens4.module.impl.render.*;
import ravens4.utility.font.RavenFontRenderer;
import ravens4.utility.profile.Manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    public static List<Module> modules = new ArrayList<>();
    public static List<Module> organizedModules = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Module> modulesByName = new HashMap<>();
    private static final Map<String, Module> modulesByNormalizedName = new HashMap<>();
    private static final Map<Class<?>, Module> modulesByClass = new HashMap<>();

    public static NameHider nameHider;
    public static FastPlace fastPlace;
    public static InvMove invmove;
    public static SkyWars skyWars;
    public static AntiFireball antiFireball;
    public static BedAura bedAura;
    public static FastMine fastMine;
    public static MovementFix movementFix;
    public static CommandLine commandLine;
    public static LongJump longJump;
    public static AntiBot antiBot;
    public static NoSlow noSlow;
    public static KillAura killAura;
    public static AutoClicker autoClicker;
    public static BlockHit blockHit;
    public static HitSelect hitSelect;
    public static Backtrack098 backtrack;
    public static Backtrack backtrackModule;
    public static KnockbackDelay knockbackDelay;
    public static Reach reach;
    public static NoRotate noRotate;
    public static BedESP bedESP;
    public static Blink blink;
    public static Chams chams;
    public static HUD hud;
    public static PotionHUD potionHUD;
    public static Timer timer;
    public static WTap wTap;
    public static Velocity velocity;
    public static AntiDebuff antiDebuff;
    public static NoFall noFall;
    public static PlayerESP playerESP;
    public static SafeWalk safeWalk;
    public static KeepSprint keepSprint;
    public static Piercing piercing;
    public static GhostHand ghostHand;
    public static Freelook freelook;
    public static NoCameraClip noCameraClip;
    public static Lazyfy bedwars;
    public static BHop bHop;
    public static NoHurtCam noHurtCam;
    public static AutoTool autoTool;
    public static AutoSwap autoSwap;
    public static Sprint sprint;
    public static Weather weather;
    public static ChatCommands chatCommands;
    public static BlockIn blockIn;
    public static Relationships relationships;
    public static Displace displace;
    public static Clutch clutch;

    public void register() {
        this.addModule(chatCommands = new ChatCommands());
        this.addModule(commandLine = new CommandLine());
        this.addModule(new Gui());
        this.addModule(new Settings());
        this.addModule(relationships = new Relationships());
        if (ravens4.Raven.playerRelationsManager == null || ravens4.Raven.playerRelationsManager.isActive()) {
            relationships.enable();
        }

        this.addModule(new AimAssist());
        this.addModule(autoClicker = new AutoClicker());
        this.addModule(blockHit = new BlockHit());
        this.addModule(new Autoblock());
        this.addModule(blockIn = new BlockIn());
        this.addModule(new ClickAssist());
        this.addModule(displace = new Displace());
        this.addModule(hitSelect = new HitSelect());
        this.addModule(backtrack = new Backtrack098());
        this.addModule(backtrackModule = new Backtrack());
        this.addModule(killAura = new KillAura());
        this.addModule(knockbackDelay = new KnockbackDelay());
        this.addModule(new LagRange());
        this.addModule(piercing = new Piercing());
        this.addModule(ghostHand = new GhostHand());
        this.addModule(new RawInput());
        this.addModule(reach = new Reach());
        this.addModule(new RodAimbot());
        this.addModule(velocity = new Velocity());
        this.addModule(wTap = new WTap());

        this.addModule(bedwars = new Lazyfy());
        this.addModule(skyWars = new SkyWars());

        this.addModule(bHop = new BHop());
        this.addModule(movementFix = new MovementFix());
        this.addModule(invmove = new InvMove());
        this.addModule(keepSprint = new KeepSprint());
        this.addModule(longJump = new LongJump());
        this.addModule(noSlow = new NoSlow());
        this.addModule(new NullMove());
        this.addModule(sprint = new Sprint());
        this.addModule(new Stasis());
        this.addModule(new StopMotion());
        this.addModule(timer = new Timer());

        this.addModule(new Anticheat());
        this.addModule(new LatencyAlerts());
        this.addModule(nameHider = new NameHider());
        this.addModule(new ViewPackets());

        this.addModule(antiFireball = new AntiFireball());
        this.addModule(autoSwap = new AutoSwap());
        this.addModule(new AutoChest());
        this.addModule(new BridgeAssist());
        this.addModule(new Scaffold());
        this.addModule(new BedDefender());
        this.addModule(clutch = new Clutch());
        this.addModule(autoTool = new AutoTool());
        this.addModule(new Inventory());
        this.addModule(new AutoLadder());
        this.addModule(bedAura = new BedAura());
        this.addModule(blink = new Blink());
        this.addModule(new DelayRemover());
        this.addModule(fastMine = new FastMine());
        this.addModule(fastPlace = new FastPlace());
        this.addModule(new FakeLag());
        this.addModule(new Freecam());
        this.addModule(noFall = new NoFall());
        this.addModule(noRotate = new NoRotate());
        this.addModule(safeWalk = new SafeWalk());

        this.addModule(new Manager());

        this.addModule(antiDebuff = new AntiDebuff());
        this.addModule(new Arrows());
        this.addModule(bedESP = new BedESP());
        this.addModule(new BlockOverlay());
        this.addModule(new BreakProgress());
        this.addModule(chams = new Chams());
        this.addModule(new DamageTags());
        this.addModule(new ChestESP());
        this.addModule(freelook = new Freelook());
        this.addModule(hud = new HUD());
        this.addModule(new Indicators());
        this.addModule(new ItemESP());
        this.addModule(new Nametags());
        this.addModule(noCameraClip = new NoCameraClip());
        this.addModule(noHurtCam = new NoHurtCam());
        this.addModule(new Visuals());
        this.addModule(new FireballPath());
        this.addModule(playerESP = new PlayerESP());
        this.addModule(potionHUD = new PotionHUD());
        this.addModule(new Saturation());
        this.addModule(new TargetHUD());
        this.addModule(new Trajectories());

        this.addModule(antiBot = new AntiBot());
        this.addModule(weather = new Weather());

        this.addModule(new ravens4.script.Manager());

        movementFix.enable();
        antiBot.enable();
        modules.sort(Comparator.comparing(Module::getName));
    }

    public void addModule(Module module) {
        modules.add(module);
        modulesByName.put(module.getName(), module);
        modulesByNormalizedName.put(normalizeModuleName(module.getName()), module);
        modulesByClass.put(module.getClass(), module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> inCategory(Module.category category) {
        ArrayList<Module> categoryModules = new ArrayList<>();

        for (Module module : this.getModules()) {
            if (module.moduleCategory().equals(category)) {
                categoryModules.add(module);
            }
        }

        return categoryModules;
    }

    public static Module getModule(String moduleName) {
        Module module = modulesByName.get(moduleName);
        if (module != null) {
            return module;
        }
        return modulesByNormalizedName.get(normalizeModuleName(moduleName));
    }

    public static Module getModule(Class<?> clazz) {
        return modulesByClass.get(clazz);
    }

    public static void sort() {
        if (HUD.alphabeticalSort.isToggled()) {
            organizedModules.sort(Comparator.comparing(Module::getNameInHud));
            return;
        }

        final RavenFontRenderer hudFont = HUD.getHudFontRenderer();
        organizedModules.sort((o1, o2) -> hudFont.getStringWidth(HUD.getHudRenderText(o2)) - hudFont.getStringWidth(HUD.getHudRenderText(o1)));
    }

    private static String normalizeModuleName(String moduleName) {
        if (moduleName == null) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(moduleName.length());
        for (int i = 0; i < moduleName.length(); i++) {
            char character = moduleName.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }
        return normalized.toString();
    }
}
