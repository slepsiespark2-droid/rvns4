package ravens4.module.impl.combat;

import ravens4.event.PostMotionEvent;
import ravens4.event.PreUpdateEvent;
import ravens4.module.Module;
import ravens4.module.impl.movement.LongJump;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.GroupSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.RotationUtils;
import ravens4.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class Velocity extends Module {
    private static final String[] MODES = new String[]{"Normal", "Jump Reset"};

    private SliderSetting mode;
    public static SliderSetting horizontal;
    public static SliderSetting vertical;
    private SliderSetting chance;
    private ButtonSetting onlyWhileTargeting;
    private ButtonSetting disableS;

    private GroupSetting jumpResetGroup;
    private SliderSetting jumpResetChance;
    private ButtonSetting requireMouseDown;
    private ButtonSetting requireMovingForward;
    private ButtonSetting requireAim;

    private boolean setJump;
    private boolean ignoreNext;
    private int lastHurtTime;
    private double lastFallDistance;

    public boolean disable;

    public Velocity() {
        super("Velocity", category.combat, 0);
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(horizontal = new SliderSetting("Horizontal", "%", 90.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(vertical = new SliderSetting("Vertical", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(chance = new SliderSetting("Chance", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(onlyWhileTargeting = new ButtonSetting("Only while targeting", false));
        this.registerSetting(disableS = new ButtonSetting("Disable while holding S", false));

        this.registerSetting(jumpResetGroup = new GroupSetting("Jump Reset"));
        this.registerSetting(jumpResetChance = new SliderSetting(jumpResetGroup, "Chance", "%", 80, 0, 100, 1));
        this.registerSetting(requireMouseDown = new ButtonSetting(jumpResetGroup, "Require mouse down", false));
        this.registerSetting(requireMovingForward = new ButtonSetting(jumpResetGroup, "Require moving forward", true));
        this.registerSetting(requireAim = new ButtonSetting(jumpResetGroup, "Require aim", true));

        jumpResetGroup.visible = false;
        jumpResetChance.visible = false;
        requireMouseDown.visible = false;
        requireMovingForward.visible = false;
        requireAim.visible = false;
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        if (isJumpResetMode()) {
            return (int) jumpResetChance.getInput() == 100 ? MODES[1] : MODES[1] + " " + (int) jumpResetChance.getInput() + "%";
        }
        return (int) horizontal.getInput() + "%" + " " + (int) vertical.getInput() + "%";
    }

    @Override
    public void guiUpdate() {
        boolean jumpReset = isJumpResetMode();
        horizontal.setVisible(!jumpReset, this);
        vertical.setVisible(!jumpReset, this);
        chance.setVisible(!jumpReset, this);
        onlyWhileTargeting.setVisible(!jumpReset, this);
        disableS.setVisible(!jumpReset, this);
        jumpResetGroup.setVisible(jumpReset, this);
        jumpResetChance.setVisible(jumpReset, this);
        requireMouseDown.setVisible(jumpReset, this);
        requireMovingForward.setVisible(jumpReset, this);
        requireAim.setVisible(jumpReset, this);
        if (!jumpReset) {
            releaseJump();
        }
    }

    @Override
    public void onDisable() {
        releaseJump();
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent ev) {
        if (!isNormalMode()) {
            return;
        }
        if (Utils.nullCheck() && !LongJump.stopVelocity && !disable) {
            if (ev.entity != mc.thePlayer) {
                return;
            }
            if (mc.thePlayer.maxHurtTime <= 0 || mc.thePlayer.hurtTime != mc.thePlayer.maxHurtTime) {
                return;
            }
            if (onlyWhileTargeting.isToggled() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
                return;
            }
            if (disableS.isToggled() && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
                return;
            }
            if (chance.getInput() == 0) {
                return;
            }
            if (chance.getInput() != 100) {
                double ch = Math.random();
                if (ch >= chance.getInput() / 100.0D) {
                    return;
                }
            }
            if (horizontal.getInput() != 100.0D) {
                mc.thePlayer.motionX *= horizontal.getInput() / 100;
                mc.thePlayer.motionZ *= horizontal.getInput() / 100;
            }
            if (vertical.getInput() != 100.0D) {
                mc.thePlayer.motionY *= vertical.getInput() / 100;
            }
        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!isJumpResetMode()) {
            releaseJump();
            return;
        }

        int hurtTime = mc.thePlayer.hurtTime;
        boolean onGround = mc.thePlayer.onGround;

        if (onGround && lastFallDistance > 3 && !mc.thePlayer.capabilities.allowFlying) {
            ignoreNext = true;
        }

        if (hurtTime > lastHurtTime) {
            boolean mouseDown = mc.gameSettings.keyBindAttack.isKeyDown() || !requireMouseDown.isToggled();
            boolean aimingAt = !requireAim.isToggled() || checkAim();
            boolean forward = mc.gameSettings.keyBindForward.isKeyDown() || !requireMovingForward.isToggled();
            boolean randomization = (int) jumpResetChance.getInput() == 100 || Utils.randomizeDouble(0, 100) < jumpResetChance.getInput();
            boolean fov = Utils.inFov(Utils.getDirection(), 330, RotationUtils.deltaAngle(mc.thePlayer.motionX, mc.thePlayer.motionZ));

            if (!ignoreNext && !mc.thePlayer.isBurning() && onGround && aimingAt && forward && mouseDown && randomization && !hasBadEffect() && fov) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), setJump = true);
            }

            ignoreNext = false;
        }

        lastHurtTime = hurtTime;
        lastFallDistance = mc.thePlayer.fallDistance;
    }

    @SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (!isJumpResetMode()) {
            releaseJump();
            return;
        }
        if (setJump && !Utils.jumpDown()) {
            releaseJump();
        }
    }

    private void releaseJump() {
        if (setJump) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            setJump = false;
        }
    }

    private boolean isNormalMode() {
        return (int) mode.getInput() == 0;
    }

    private boolean isJumpResetMode() {
        return (int) mode.getInput() == 1;
    }

    private boolean hasBadEffect() {
        PotionEffect jump = mc.thePlayer.getActivePotionEffect(Potion.jump);
        PotionEffect poison = mc.thePlayer.getActivePotionEffect(Potion.poison);
        PotionEffect wither = mc.thePlayer.getActivePotionEffect(Potion.wither);
        return jump != null || poison != null || wither != null;
    }

    private boolean checkAim() {
        MovingObjectPosition result = mc.objectMouseOver;
        return result != null && result.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && result.entityHit instanceof EntityPlayer;
    }
}
