package ravens4.module.impl.combat;

import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.player.AntiBot;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.ReflectionUtils;
import ravens4.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.util.concurrent.ThreadLocalRandom;

public class BlockHit extends Module {
    private final String[] EVENT_TYPES = new String[]{"PRE", "POST"};

    private SliderSetting range;
    private SliderSetting eventType;
    private SliderSetting chance;
    private SliderSetting waitMsMin;
    private SliderSetting waitMsMax;
    private SliderSetting hitPerMin;
    private SliderSetting hitPerMax;
    private SliderSetting postDelayMin;
    private SliderSetting postDelayMax;
    private ButtonSetting onlyPlayers;
    private ButtonSetting onRightMBHold;
    private ButtonSetting weaponOnly;

    private boolean executingAction;
    private boolean hitCoolDown;
    private boolean alreadyHit;
    private boolean safeGuard;
    private boolean waitingForPostDelay;
    private boolean holdingUseItem;
    private int hitTimeout;
    private int hitsWaited;
    private long actionEndMs;
    private long postDelayEndMs;
    private long actionDurationMs;

    public BlockHit() {
        super("BlockHit", category.combat);
        this.registerSetting(new DescriptionSetting("Auto right-click block-hits after attacks."));
        this.registerSetting(onlyPlayers = new ButtonSetting("Only players", true));
        this.registerSetting(onRightMBHold = new ButtonSetting("Require RMB combo", true));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", true));
        this.registerSetting(waitMsMin = new SliderSetting("Action min", " ms", 110, 1, 500, 1));
        this.registerSetting(waitMsMax = new SliderSetting("Action max", " ms", 150, 1, 500, 1));
        this.registerSetting(hitPerMin = new SliderSetting("Hits min", 1, 1, 10, 1));
        this.registerSetting(hitPerMax = new SliderSetting("Hits max", 1, 1, 10, 1));
        this.registerSetting(postDelayMin = new SliderSetting("Post delay min", " ms", 10, 0, 500, 1));
        this.registerSetting(postDelayMax = new SliderSetting("Post delay max", " ms", 40, 0, 500, 1));
        this.registerSetting(chance = new SliderSetting("Chance", "%", 100, 0, 100, 1));
        this.registerSetting(range = new SliderSetting("Range", 3.0D, 1.0D, 6.0D, 0.05D));
        this.registerSetting(eventType = new SliderSetting("Timing", 1, EVENT_TYPES));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return EVENT_TYPES[(int) eventType.getInput()];
    }

    @Override
    public void onDisable() {
        finishCombo();
        resetState();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        if (!Utils.nullCheck() || mc.currentScreen != null || !mc.inGameHasFocus) {
            resetState();
            finishCombo();
            return;
        }
        if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            resetState();
            finishCombo();
            return;
        }

        long now = System.currentTimeMillis();
        if (onRightMBHold.isToggled() && !tryingToCombo()) {
            if (holdingUseItem || executingAction || waitingForPostDelay) {
                safeFinish();
            }
            executingAction = false;
            waitingForPostDelay = false;
            alreadyHit = false;
            safeGuard = false;
            return;
        }

        if (waitingForPostDelay) {
            if (now >= postDelayEndMs) {
                waitingForPostDelay = false;
                executingAction = true;
                startCombo();
                safeGuard = false;
                actionEndMs = now + actionDurationMs;
            }
            return;
        }

        if (executingAction) {
            if (now >= actionEndMs) {
                executingAction = false;
                finishCombo();
            }
            return;
        }

        if (!Mouse.isButtonDown(0)) {
            alreadyHit = false;
            return;
        }

        Entity target = findTarget();
        if (target == null) {
            if (onRightMBHold.isToggled() && tryingToCombo()) {
                safeFinish();
            }
            return;
        }

        if (target.isDead || mc.thePlayer.getDistanceToEntity(target) > range.getInput()) {
            safeFinish();
            return;
        }
        if (onlyPlayers.isToggled() && !(target instanceof EntityPlayer)) {
            return;
        }
        if (target instanceof EntityPlayer && AntiBot.isBot(target)) {
            return;
        }

        boolean post = (int) eventType.getInput() == 1;
        boolean timingOk = post ? target.hurtResistantTime >= 10 : target.hurtResistantTime <= 10;
        if (!timingOk) {
            alreadyHit = false;
            safeGuard = false;
            return;
        }

        if (hitCoolDown && !alreadyHit) {
            hitsWaited++;
            if (hitsWaited < hitTimeout) {
                alreadyHit = true;
                return;
            }
            hitCoolDown = false;
            hitsWaited = 0;
        }

        if (chance.getInput() != 100.0D && Math.random() > chance.getInput() / 100.0D) {
            alreadyHit = true;
            return;
        }

        if (!alreadyHit) {
            scheduleAction(now);
            alreadyHit = true;
        }
    }

    private Entity findTarget() {
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
            return mc.objectMouseOver.entityHit;
        }

        Module aimAssist = ModuleManager.getModule(AimAssist.class);
        Entity aimTarget = AimAssist.getCurrentTarget();
        if (aimAssist != null && aimAssist.isEnabled() && aimTarget != null && !aimTarget.isDead) {
            return aimTarget;
        }
        return null;
    }

    private void scheduleAction(long now) {
        int minHits = (int) Math.min(hitPerMin.getInput(), hitPerMax.getInput());
        int maxHits = (int) Math.max(hitPerMin.getInput(), hitPerMax.getInput());
        hitTimeout = minHits == maxHits ? minHits : ThreadLocalRandom.current().nextInt(minHits, maxHits + 1);
        hitCoolDown = true;
        hitsWaited = 0;

        actionDurationMs = randomLong(waitMsMin.getInput(), waitMsMax.getInput());
        long postDelay = randomLong(postDelayMin.getInput(), postDelayMax.getInput());
        if (postDelay > 0L) {
            postDelayEndMs = now + postDelay;
            waitingForPostDelay = true;
        } else {
            executingAction = true;
            startCombo();
            safeGuard = false;
            actionEndMs = now + actionDurationMs;
        }
    }

    private long randomLong(double a, double b) {
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        if (max <= min) {
            return (long) min;
        }
        return (long) ThreadLocalRandom.current().nextDouble(min, max + 0.01D);
    }

    private boolean tryingToCombo() {
        return Mouse.isButtonDown(0) && Mouse.isButtonDown(1);
    }

    private void safeFinish() {
        if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
            safeGuard = true;
            finishCombo();
        }
    }

    private void resetState() {
        executingAction = false;
        hitCoolDown = false;
        alreadyHit = false;
        safeGuard = false;
        waitingForPostDelay = false;
        hitTimeout = 0;
        hitsWaited = 0;
        actionEndMs = 0L;
        postDelayEndMs = 0L;
        actionDurationMs = 0L;
    }

    private void finishCombo() {
        if (!holdingUseItem) {
            return;
        }
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, false);
        ReflectionUtils.setButton(1, false);
        holdingUseItem = false;
    }

    private void startCombo() {
        if (mc.gameSettings.keyBindForward.isKeyDown()) {
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
            ReflectionUtils.setButton(1, true);
            holdingUseItem = true;
        }
    }
}
