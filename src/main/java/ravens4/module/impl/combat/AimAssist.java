package ravens4.module.impl.combat;

import ravens4.event.ClientRotationEvent;
import ravens4.helper.RotationHelper;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.player.AntiBot;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.RotationUtils;
import ravens4.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AimAssist extends Module {

    private SliderSetting mode;
    private SliderSetting speed;
    private SliderSetting legitV2Speed1;
    private SliderSetting legitV2Speed2;
    private SliderSetting multipointHorizontal;
    private SliderSetting multipointVertical;
    private SliderSetting randomization;
    private SliderSetting fov;
    private SliderSetting range;
    private SliderSetting sortMode;

    private ButtonSetting aimInvis;
    private ButtonSetting clickAim;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting ignoreBehindWalls;
    private ButtonSetting ignoreBehindEntities;
    private ButtonSetting stopWhenBreaking;
    private ButtonSetting keepMoveDirection;
    private SliderSetting hoverDelay;
    private ButtonSetting weaponOnly;
    private ButtonSetting legitV2BreakBlocks;
    private ButtonSetting legitV2Blatant;

    private long miningStartTime = -1;
    private static Entity currentTarget;

    private final String[] AIM_MODES = new String[]{"Normal", "Silent", "LegitV2"};
    private final String[] SORT_MODES = new String[]{"Health", "Angle", "Hurt time", "Distance"};

    public AimAssist() {
        super("Aim Assist", category.combat);
        this.registerSetting(mode = new SliderSetting("Mode", 0, AIM_MODES));
        this.registerSetting(speed = new SliderSetting("Speed", 10, 1, 30, 1));
        this.registerSetting(legitV2Speed1 = new SliderSetting("LegitV2 speed 1", 45, 5, 100, 1));
        this.registerSetting(legitV2Speed2 = new SliderSetting("LegitV2 speed 2", 15, 2, 97, 1));
        this.registerSetting(multipointHorizontal = new SliderSetting("Multipoint horizontal", "%", 0, 0, 100, 1));
        this.registerSetting(multipointVertical = new SliderSetting("Multipoint vertical", "%", 0, 0, 100, 1));
        this.registerSetting(randomization = new SliderSetting("Randomization", "%", 50, 0, 100, 1));
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 15.0D, 360.0D, 1.0D));
        this.registerSetting(range = new SliderSetting("Range", 4.5D, 0.0D, 6.0D, 0.1D));
        this.registerSetting(sortMode = new SliderSetting("Sort", 1, SORT_MODES));
        this.registerSetting(ignoreBehindWalls = new ButtonSetting("Ignore behind walls", false));
        this.registerSetting(ignoreBehindEntities = new ButtonSetting("Ignore behind entities", false));
        this.registerSetting(aimInvis = new ButtonSetting("Aim invis", false));
        this.registerSetting(clickAim = new ButtonSetting("Require mouse", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(stopWhenBreaking = new ButtonSetting("Stop when breaking", false));
        this.registerSetting(keepMoveDirection = new ButtonSetting("Keep move direction", true));
        this.registerSetting(hoverDelay = new SliderSetting("Hover delay", " ms", 100, 0, 500, 10));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(legitV2BreakBlocks = new ButtonSetting("LegitV2 break blocks", true));
        this.registerSetting(legitV2Blatant = new ButtonSetting("LegitV2 blatant", false));
    }

    @Override
    public String getInfo() {
        return AIM_MODES[(int) mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean legitV2 = mode.getInput() == 2;
        speed.setVisible(!legitV2, this);
        legitV2Speed1.setVisible(legitV2, this);
        legitV2Speed2.setVisible(legitV2, this);
        multipointHorizontal.setVisible(!legitV2, this);
        multipointVertical.setVisible(!legitV2, this);
        randomization.setVisible(!legitV2, this);
        ignoreBehindWalls.setVisible(!legitV2, this);
        ignoreBehindEntities.setVisible(!legitV2, this);
        keepMoveDirection.setVisible(mode.getInput() == 1, this);
        hoverDelay.setVisible(stopWhenBreaking.isToggled(), this);
        legitV2BreakBlocks.setVisible(legitV2, this);
        legitV2Blatant.setVisible(legitV2, this);
    }

    @Override
    public void onDisable() {
        miningStartTime = -1;
        currentTarget = null;
    }

    public static Entity getCurrentTarget() {
        return currentTarget;
    }

    public static boolean hasCurrentTarget() {
        return currentTarget != null && !currentTarget.isDead;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onClientRotation(ClientRotationEvent e) {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            currentTarget = null;
            return;
        }
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) {
            currentTarget = null;
            return;
        }
        if (mode.getInput() != 1) {
            return;
        }
        if (!conditionsMet()) {
            currentTarget = null;
            return;
        }
        Entity en = getEnemy(true);
        currentTarget = en;
        if (en == null) {
            return;
        }

        int speedVal = (int) speed.getInput();
        double multipointH = multipointHorizontal.getInput();
        double multipointV = multipointVertical.getInput();
        float randomizationPercent = (float) randomization.getInput();
        boolean useBackup = ignoreBehindWalls.isToggled() || ignoreBehindEntities.isToggled();
        float[] rot = RotationHelper.get().getRotationsToTarget(en, e, speedVal, multipointH, multipointV, randomizationPercent, useBackup, range.getInput(), !ignoreBehindWalls.isToggled(), !ignoreBehindEntities.isToggled());
        if (rot == null) return;
        RotationHelper.get().forceMovementFix = true;
        RotationHelper.get().setServerRelativeMovementInputs(!keepMoveDirection.isToggled());
        e.yaw = rot[0];
        e.pitch = rot[1];
    }

    @Override
    public void onUpdate() {
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) {
            currentTarget = null;
            return;
        }
        if (mode.getInput() == 1) {
            return;
        }
        if (!conditionsMet()) {
            currentTarget = null;
            return;
        }

        if (mode.getInput() == 2) {
            updateLegitV2();
            return;
        }

        Entity en = getEnemy(false);
        currentTarget = en;
        if (en == null) {
            return;
        }

        int speedVal = (int) speed.getInput();
        double multipointH = multipointHorizontal.getInput();
        double multipointV = multipointVertical.getInput();
        float randomizationPercent = (float) randomization.getInput();
        boolean useBackup = ignoreBehindWalls.isToggled() || ignoreBehindEntities.isToggled();
        float[] rot = RotationHelper.get().getRotationsToTarget(en, speedVal, multipointH, multipointV, randomizationPercent, useBackup, range.getInput(), !ignoreBehindWalls.isToggled(), !ignoreBehindEntities.isToggled());
        if (rot == null) return;
        mc.thePlayer.rotationYaw = rot[0];
        mc.thePlayer.rotationPitch = rot[1];
    }

    private void updateLegitV2() {
        Entity en = getEnemy(false);
        currentTarget = en;
        if (en == null) {
            return;
        }

        if (legitV2Blatant.isToggled()) {
            Utils.aim(en, 0.0F, false);
            return;
        }

        double yawDelta = Utils.aimDifference(en, false);
        if (yawDelta <= 1.0D && yawDelta >= -1.0D) {
            return;
        }

        double speed1 = legitV2Speed1.getInput();
        double speed2 = legitV2Speed2.getInput();
        double complimentSpeed = yawDelta * (randomBetween(speed2 - 1.47328D, speed2 + 2.48293D) / 100.0D);
        double denominator = 101.0D - randomBetween(speed1 - 4.723847D, speed1);
        if (denominator < 1.0D) {
            denominator = 1.0D;
        }
        float value = (float) -(complimentSpeed + yawDelta / denominator);
        mc.thePlayer.rotationYaw += value;
    }

    private static double randomBetween(double min, double max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private Entity getEnemy(boolean silentMode) {
        final int fovVal = (int) this.fov.getInput();
        float viewYaw = mc.thePlayer.rotationYaw;
        if (silentMode) {
            Float serverYaw = RotationHelper.get().getServerYaw();
            if (serverYaw != null) {
                viewYaw = serverYaw;
            }
        }

        List<EntityPlayer> candidates = new ArrayList<>();
        for (EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
            if (entityPlayer == mc.thePlayer || entityPlayer.deathTime != 0 || entityPlayer.isDead) {
                continue;
            }
            if (Utils.isFriended(entityPlayer)) {
                continue;
            }
            if (ignoreTeammates.isToggled() && Utils.isTeammate(entityPlayer)) {
                continue;
            }
            if (!aimInvis.isToggled() && entityPlayer.isInvisible()) {
                continue;
            }
            if (RotationUtils.distanceSqFromEyeToClosestOnAABB(entityPlayer) > range.getInput() * range.getInput()) {
                continue;
            }
            if (AntiBot.isBot(entityPlayer)) {
                continue;
            }
            if (fovVal != 360) {
                float angleToEntity = RotationUtils.angle(entityPlayer.posX, entityPlayer.posZ);
                if (!Utils.inFov(viewYaw, (float) fovVal, angleToEntity)) {
                    continue;
                }
            }
            candidates.add(entityPlayer);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Comparator<EntityPlayer> primary;
        switch ((int) sortMode.getInput()) {
            case 0: // Health (lower first)
                primary = Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount());
                break;
            case 1: // Angle (smaller first)
                primary = Comparator.comparingDouble(p -> {
                    double yawDelta = Math.abs(Utils.aimDifference(p, false));
                    double pitchDelta = Math.abs(Utils.pitchDifference(p, false));
                    return yawDelta + pitchDelta;
                });
                break;
            case 2: // Hurt time (lower first)
                primary = Comparator.<EntityPlayer>comparingInt(p -> p.hurtTime);
                break;
            case 3: // Distance (closer first)
                primary = Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
                break;
            default:
                primary = Comparator.comparingDouble(p -> {
                    double yawDelta = Math.abs(Utils.aimDifference(p, false));
                    double pitchDelta = Math.abs(Utils.pitchDifference(p, false));
                    return yawDelta + pitchDelta;
                });
        }
        candidates.sort(primary.thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));

        if (mode.getInput() != 2 && (ignoreBehindWalls.isToggled() || ignoreBehindEntities.isToggled())) {
            double multipointH = multipointHorizontal.getInput();
            double multipointV = multipointVertical.getInput();
            double rangeVal = range.getInput();
            boolean allowThroughBlocks = !ignoreBehindWalls.isToggled();
            boolean allowThroughEntities = !ignoreBehindEntities.isToggled();
            for (EntityPlayer candidate : candidates) {
                if (RotationUtils.hasValidAimPoint(candidate, multipointH, multipointV, rangeVal, allowThroughBlocks, allowThroughEntities)) {
                    return candidate;
                }
            }
            return null;
        }

        return candidates.get(0);
    }

    private boolean conditionsMet() {
        if (!Utils.nullCheck() || mc.currentScreen != null || !mc.inGameHasFocus) {
            return false;
        }
        if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        }
        if (clickAim.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        }
        if (mode.getInput() == 2 && legitV2BreakBlocks.isToggled() && isLookingAtSolidBlock()) {
            return false;
        }
        if (stopWhenBreaking.isToggled() && Utils.isMining()) {
            if (miningStartTime == -1) {
                miningStartTime = System.currentTimeMillis();
            }
            long elapsed = System.currentTimeMillis() - miningStartTime;
            if (elapsed >= hoverDelay.getInput()) {
                return false;
            }
        } else {
            miningStartTime = -1;
        }
        return true;
    }

    private boolean isLookingAtSolidBlock() {
        if (mc.objectMouseOver == null || mc.theWorld == null) {
            return false;
        }
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        if (pos == null) {
            return false;
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block != Blocks.air && !(block instanceof BlockLiquid);
    }

}
