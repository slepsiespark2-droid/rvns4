package ravens4.module.impl.combat;

import ravens4.Raven;
import ravens4.event.PrePlayerInteractEvent;
import ravens4.event.RightClickMouseEvent;
import ravens4.lag.api.EnumLagDirection;
import ravens4.lag.api.LagRequest;
import ravens4.lag.timeout.ModuleBackedTimeout;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.event.SendPacketEvent;
import ravens4.event.UseItemEvent;
import ravens4.utility.CombatTargeting;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.KeySetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.ReflectionUtils;
import ravens4.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

public class Autoblock extends Module {
    public SliderSetting mode;
    public String[] modes = new String[]{"Ordinary", "AutoBlock2"};

    private final DescriptionSetting lagHeader;
    private final DescriptionSetting conditionsHeader;

    private final SliderSetting range;
    private final SliderSetting maxHurtTimeMs;
    private final SliderSetting maxHoldMs;

    private final ButtonSetting requireLmb;
    private final ButtonSetting requireRmb;
    private final ButtonSetting onlyWhenDamaged;
    private final ButtonSetting ignoreTeammates;

    private final SliderSetting lagChance;
    private final SliderSetting lagMaxDuration;
    private final ButtonSetting preventDelayAttacks;
    private final ButtonSetting blockAgainImmediately;
    private final ButtonSetting forceBlockAnimation;

    private final DescriptionSetting autoBlock2Header;
    private final SliderSetting ab2DistanceMin;
    private final SliderSetting ab2DistanceMax;
    private final SliderSetting ab2DurationMin;
    private final SliderSetting ab2DurationMax;
    private final SliderSetting ab2Chance;
    private final KeySetting holdKey;

    private boolean ab2Engaged;
    private long ab2EngagedStart;
    private long ab2HoldDuration;
    private boolean holdKeyActive;

    private boolean isBlocking;
    private boolean manualBlock;
    private int blockStartTick = -1;
    private EntityPlayer currentTarget;
    private int lastSelfHurtTime;

    private boolean isLagging;
    private int lagStartTick = -1;
    private LagRequest outboundLag;

    private int tickCounter;

    public Autoblock() {
        super("Auto Block", category.combat);

        this.registerSetting(mode = new SliderSetting("Mode", 0, modes));

        this.registerSetting(range = new SliderSetting("Range", 4.0, 2.0, 6.0, 0.1));
        this.registerSetting(maxHurtTimeMs = new SliderSetting("Maximum Hurt Time", "ms", 200, 50, 500, 50));
        this.registerSetting(maxHoldMs = new SliderSetting("Maximum Hold Time", "ms", 150, 50, 500, 50));

        this.registerSetting(lagHeader = new DescriptionSetting("Lag"));
        this.registerSetting(lagChance = new SliderSetting("Lag Chance", "%", 100, 0, 100, 5));
        this.registerSetting(lagMaxDuration = new SliderSetting("Lag Max Duration", "ms", 200, 50, 500, 50));
        this.registerSetting(preventDelayAttacks = new ButtonSetting("Prevent delaying attacks", true));
        this.registerSetting(blockAgainImmediately = new ButtonSetting("Block again immediately", true));

        this.registerSetting(conditionsHeader = new DescriptionSetting("Conditions"));
        this.registerSetting(requireLmb = new ButtonSetting("Require Left mouse", true));
        this.registerSetting(requireRmb = new ButtonSetting("Require right mouse", false));
        this.registerSetting(onlyWhenDamaged = new ButtonSetting("Damaged", false));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));

        this.registerSetting(autoBlock2Header = new DescriptionSetting("AutoBlock2"));
        this.registerSetting(ab2DistanceMin = new SliderSetting("Min Distance", 0.0, 0.0, 6.0, 0.1));
        this.registerSetting(ab2DistanceMax = new SliderSetting("Max Distance", 3.0, 0.0, 6.0, 0.1));
        this.registerSetting(ab2DurationMin = new SliderSetting("Min Duration", "ms", 20, 1, 500, 1));
        this.registerSetting(ab2DurationMax = new SliderSetting("Max Duration", "ms", 100, 1, 500, 1));
        this.registerSetting(ab2Chance = new SliderSetting("Chance", "%", 100, 0, 100, 1));
        this.registerSetting(holdKey = new KeySetting("Hold Key", 0));

        this.registerSetting(forceBlockAnimation = new ButtonSetting("Force block animation", true));

        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return modes[(int) mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean ordinary = (int) mode.getInput() == 0;
        boolean autoBlock2 = (int) mode.getInput() == 1;

        range.setVisible(ordinary, this);
        maxHurtTimeMs.setVisible(ordinary, this);
        maxHoldMs.setVisible(ordinary, this);
        lagHeader.setVisible(ordinary, this);
        lagChance.setVisible(ordinary, this);
        lagMaxDuration.setVisible(ordinary, this);
        preventDelayAttacks.setVisible(ordinary, this);
        blockAgainImmediately.setVisible(ordinary, this);
        conditionsHeader.setVisible(ordinary, this);
        requireLmb.setVisible(ordinary, this);
        requireRmb.setVisible(ordinary, this);
        onlyWhenDamaged.setVisible(ordinary, this);
        ignoreTeammates.setVisible(ordinary, this);

        autoBlock2Header.setVisible(autoBlock2, this);
        ab2DistanceMin.setVisible(autoBlock2, this);
        ab2DistanceMax.setVisible(autoBlock2, this);
        ab2DurationMin.setVisible(autoBlock2, this);
        ab2DurationMax.setVisible(autoBlock2, this);
        ab2Chance.setVisible(autoBlock2, this);
        holdKey.setVisible(autoBlock2, this);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        ab2Engaged = false;
        holdKeyActive = false;
        resetState(false);
    }

    private static int msToTicks(double ms) {
        if (ms <= 0.0) return 0;
        return (int) Math.ceil(ms / 50.0);
    }

    @Override
    public void onDisable() {
        ab2Engaged = false;
        holdKeyActive = false;
        resetState(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!this.isEnabled() || !Utils.nullCheck() || !Utils.holdingSword()) return;
        if (!(isBlocking || isLagging || ab2Engaged || holdKeyActive)) return;
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) return;
        if (e.button == 1) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickMouse(RightClickMouseEvent e) {
        if (shouldBlockVanillaUse()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseItem(UseItemEvent e) {
        if (shouldBlockVanillaUse()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if (!Utils.nullCheck()) return;
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) return;
        if (mc.currentScreen != null && (isBlocking || isLagging)) {
            resetState(true);
            return;
        }
        if (!forceBlockAnimation.isToggled() || !Utils.holdingSword()) return;
        ReflectionUtils.setItemInUse(isBlocking || isLagging);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            releaseLag();
            return;
        }
        if (!isLagging || !preventDelayAttacks.isToggled()) return;
        if (!(e.getPacket() instanceof C02PacketUseEntity)) return;
        if (((C02PacketUseEntity) e.getPacket()).getAction() != C02PacketUseEntity.Action.ATTACK) return;

        releaseLag();
        if (blockAgainImmediately.isToggled() && Utils.holdingSword()) {
            startBlocking(tickCounter);
        }
    }

    @SubscribeEvent
    public void onAutoBlock2(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if ((int) mode.getInput() != 1) return;

        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.currentScreen != null || !Utils.holdingSword()) {
            if (ab2Engaged) {
                ab2Engaged = false;
                stopBlocking(true);
            }
            if (holdKeyActive) {
                holdKeyActive = false;
                stopBlocking(true);
            }
            return;
        }

        boolean holdKeyBound = holdKey.getKey() != 0;
        boolean holdKeyDown = holdKeyBound && holdKey.isPressed();

        if (holdKeyBound) {
            if (holdKeyDown && !holdKeyActive) {
                holdKeyActive = true;
                ab2Engaged = false;
                startBlocking(++tickCounter);
            } else if (!holdKeyDown && holdKeyActive) {
                holdKeyActive = false;
                stopBlocking(true);
            }
            if (holdKeyActive) {
                reinforceBlocking();
                return;
            }
        }

        long now = System.currentTimeMillis();

        if (ab2Engaged) {
            reinforceBlocking();
            boolean minTimeMet = now - ab2EngagedStart >= (long) ab2DurationMin.getInput();
            boolean shouldRelease = now - ab2EngagedStart >= ab2HoldDuration || !Mouse.isButtonDown(0);
            if (shouldRelease && minTimeMet) {
                ab2Engaged = false;
                stopBlocking(true);
            }
            return;
        }

        if (!Mouse.isButtonDown(0)) return;
        if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) return;
        if (!(mc.objectMouseOver.entityHit instanceof EntityPlayer)) return;
        if (ignoreTeammates.isToggled() && Utils.isTeammate((EntityPlayer) mc.objectMouseOver.entityHit)) return;

        double dist = mc.thePlayer.getDistanceToEntity(mc.objectMouseOver.entityHit);
        if (dist < ab2DistanceMin.getInput() || dist > ab2DistanceMax.getInput()) return;

        double chance = ab2Chance.getInput();
        if (chance < 100.0 && Math.random() * 100.0 > chance) return;

        ab2HoldDuration = (long) ab2DurationMax.getInput();
        ab2EngagedStart = now;
        ab2Engaged = true;
        startBlocking(++tickCounter);
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if ((int) mode.getInput() != 0) return;
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.currentScreen != null) {
            resetState(true);
            return;
        }

        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            resetState(true);
            return;
        }

        int selfHurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = selfHurtTime > lastSelfHurtTime;
        lastSelfHurtTime = selfHurtTime;

        if (!Utils.holdingSword()) {
            resetState(false);
            return;
        }

        tickCounter++;
        int currentTick = tickCounter;

        currentTarget = CombatTargeting.findTarget(range.getInput() * range.getInput(), ignoreTeammates.isToggled());
        boolean killAuraAttacking = ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && !ModuleManager.killAura.isRequireMouseDown() && currentTarget != null;
        boolean rmbDown = Mouse.isButtonDown(1);
        boolean lmbDown = Mouse.isButtonDown(0) || killAuraAttacking;

        if (!rmbDown) {
            resetState(true);
            return;
        }

        if (!lmbDown) {
            if (isLagging) releaseLag();
            if (!isBlocking) {
                startBlocking(currentTick);
                manualBlock = true;
            }
            return;
        }

        if (manualBlock) {
            stopBlocking(true);
            manualBlock = false;
        }

        boolean hasTarget = currentTarget != null;
        boolean conditionsMet = hasTarget && checkConditions(lmbDown, rmbDown);

        if (isLagging) {
            int lagMaxTicks = msToTicks(lagMaxDuration.getInput());
            boolean lagExpired = lagMaxTicks > 0 && lagStartTick >= 0 && currentTick - lagStartTick >= lagMaxTicks;

            if (lagExpired || !conditionsMet) {
                releaseLag();
                if (lagExpired && blockAgainImmediately.isToggled() && conditionsMet) {
                    startBlocking(currentTick);
                }
            }
        }

        if (!conditionsMet) {
            stopBlocking(true);
            return;
        }

        if (!isBlocking && !isLagging) {
            boolean shouldStart;
            if (onlyWhenDamaged.isToggled()) {
                shouldStart = shouldPredictiveBlock();
            } else {
                shouldStart = true;
            }
            if (shouldStart) {
                startBlocking(currentTick);
            }
        }

        if (isBlocking) {
            int maxHoldTicks = msToTicks(maxHoldMs.getInput());
            boolean timeExpired = maxHoldTicks > 0 && blockStartTick >= 0 && currentTick - blockStartTick >= maxHoldTicks;
            boolean shouldStop = timeExpired;
            if (onlyWhenDamaged.isToggled() && hurtAgain) {
                shouldStop = true;
            }
            if (shouldStop) {
                if (shouldStartLag()) {
                    startLag(currentTick);
                }
                stopBlocking(true);
            }
        }
    }

    private boolean checkConditions(boolean lmbDown, boolean rmbDown) {
        if (requireLmb.isToggled() && !lmbDown) return false;
        if (requireRmb.isToggled() && !rmbDown) return false;
        return true;
    }

    private boolean shouldPredictiveBlock() {
        int ourHurtTime = mc.thePlayer.hurtTime;
        int triggerTick = (int) Math.round(maxHurtTimeMs.getInput() / 50.0);
        triggerTick = Math.max(1, Math.min(10, triggerTick));
        return ourHurtTime == triggerTick;
    }

    private boolean shouldBlockVanillaUse() {
        return isEnabled() && isLagging && Utils.nullCheck() && Utils.holdingSword() && mc.currentScreen == null;
    }

    private void startBlocking(int currentTick) {
        if (!Utils.holdingSword()) return;
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, true);
        KeyBinding.onTick(keyCode);
        isBlocking = true;
        blockStartTick = currentTick;
    }

    private void reinforceBlocking() {
        if (!isBlocking) return;
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, true);
    }

    private void stopBlocking(boolean forceRelease) {
        if (!isBlocking && !forceRelease) return;
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, false);
        isBlocking = false;
        blockStartTick = -1;
    }

    private boolean shouldStartLag() {
        double chance = lagChance.getInput();
        if (chance <= 0) return false;
        if (chance >= 100) return true;
        return Math.random() * 100 < chance;
    }

    private void startLag(int currentTick) {
        if (isLagging) return;
        int lagReferenceTick = blockStartTick >= 0 ? blockStartTick : currentTick;
        int lagMaxTicks = msToTicks(lagMaxDuration.getInput());
        if (lagMaxTicks > 0 && currentTick - lagReferenceTick >= lagMaxTicks) {
            return;
        }
        outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
        Raven.lagHandler.requestLag(outboundLag);
        isLagging = true;
        lagStartTick = lagReferenceTick;
    }

    private void releaseLag() {
        if (!isLagging) return;
        if (outboundLag != null) {
            outboundLag.getTimeout().forceTimeOut();
            outboundLag = null;
        }
        isLagging = false;
        lagStartTick = -1;
    }

    public boolean isActive() {
        return isEnabled() && (isBlocking || isLagging);
    }

    private void resetState(boolean releaseUseKey) {
        boolean wasActive = isBlocking || isLagging;
        releaseLag();
        stopBlocking(releaseUseKey);
        manualBlock = false;
        if (forceBlockAnimation.isToggled() && wasActive) {
            ReflectionUtils.setItemInUse(false);
        }
        if (Mouse.isButtonDown(1) && mc.currentScreen == null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
        currentTarget = null;
        lastSelfHurtTime = 0;
    }
}
