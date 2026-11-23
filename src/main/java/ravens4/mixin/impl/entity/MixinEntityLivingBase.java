package ravens4.mixin.impl.entity;

import ravens4.event.JumpEvent;
import ravens4.event.PreMotionEvent;
import ravens4.event.PrePlayerMovementInputEvent;
import ravens4.module.ModuleManager;
import ravens4.module.impl.client.Settings;
import ravens4.utility.RotationUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import ravens4.bus.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {
    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    // NOTE: getActivePotionEffect(Potion) and isPotionActive(Potion) used to
    // be declared here as @Shadow(remap = false) stubs. They are PUBLIC methods on vanilla
    // EntityLivingBase, so @Shadow(remap = false) (meant for members not otherwise
    // reachable) isn't actually needed - it was failing refmap resolution
    // at runtime ("@Shadow(remap = false) method a ... was not located in the target
    // class"). Calling them via a direct cast to EntityLivingBase below
    // sidesteps that refmap lookup entirely, since at weave time `this`
    // genuinely IS an EntityLivingBase instance.

    @Shadow(remap = false)
    public float rotationYawHead;

    @Shadow(remap = false)
    public float renderYawOffset;

    @Shadow(remap = false)
    public float swingProgress;

    @Inject(method = { "updateDistance", "func_110146_f" }, at = @At("HEAD"), cancellable = true)
    protected void injectUpdateDistance(float p_110146_1_, float p_110146_2_, CallbackInfoReturnable<Float> cir) {
        float rotationYaw = this.rotationYaw;
        if (Settings.fullBody != null && Settings.rotateBody != null && !Settings.fullBody.isToggled() && Settings.rotateBody.isToggled() && (EntityLivingBase) (Object) this instanceof EntityPlayerSP && PreMotionEvent.setRenderYaw()) {
            if (this.swingProgress > 0F) {
                p_110146_1_ = RotationUtils.renderYaw;
            }
            rotationYaw = RotationUtils.renderYaw;
            rotationYawHead = RotationUtils.renderYaw;
        }

        float f = MathHelper.wrapAngleTo180_float(p_110146_1_ - this.renderYawOffset);
        this.renderYawOffset += f * 0.3F;
        float f1 = MathHelper.wrapAngleTo180_float(rotationYaw - this.renderYawOffset);
        boolean flag = f1 < 90.0F || f1 >= 90.0F;

        if (f1 < -75.0F) {
            f1 = -75.0F;
        }

        if (f1 >= 75.0F) {
            f1 = 75.0F;
        }

        this.renderYawOffset = rotationYaw - f1;

        if (f1 * f1 > 2500.0F) {
            this.renderYawOffset += f1 * 0.2F;
        }

        if (flag) {
            p_110146_2_ *= -1.0F;
        }

        cir.setReturnValue(p_110146_2_);
    }

    @Shadow(remap = false)
    protected float getJumpUpwardsMotion() {
        return 0.42F;
    }

    @Overwrite
    protected void jump() {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        JumpEvent jumpEvent = new JumpEvent(this.getJumpUpwardsMotion(), this.rotationYaw, this.isSprinting());
        ravens4.bus.MinecraftForge.EVENT_BUS.post(jumpEvent);
        if (jumpEvent.isCanceled()) {
            return;
        }

        this.motionY = jumpEvent.getMotionY();

        if (self.isPotionActive(Potion.jump)) {
            this.motionY += (float) (self.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
        }

        if (jumpEvent.applySprint()) {
            float f = jumpEvent.getYaw() * 0.017453292F;
            this.motionX -= MathHelper.sin(f) * 0.2F;
            this.motionZ += MathHelper.cos(f) * 0.2F;
        }

        this.isAirBorne = true;
        ForgeHooks.onLivingJump(((EntityLivingBase) (Object) this));
    }

    @Inject(method = "isPotionActive(Lnet/minecraft/potion/Potion;)Z", at = @At("HEAD"), cancellable = true)
    private void isPotionActive(Potion p_isPotionActive_1_, final CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (ModuleManager.antiDebuff != null && ModuleManager.antiDebuff.isEnabled() && ((p_isPotionActive_1_ == Potion.confusion && ModuleManager.antiDebuff.removeNausea.isToggled()) || (p_isPotionActive_1_ == Potion.blindness && ModuleManager.antiDebuff.removeBlindness.isToggled()))) {
            if (ModuleManager.antiDebuff.removeSideEffects.isToggled()) {
                callbackInfoReturnable.setReturnValue(false);
            }
        }
    }

    @Redirect(method = "onLivingUpdate", at = @At(value  = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;moveEntityWithHeading(FF)V"))
    private void onMoveEntityWithHeadingRedirect(EntityLivingBase self, float originalStrafing, float originalForward) {
        if (self instanceof EntityPlayerSP) {
            PrePlayerMovementInputEvent event = new PrePlayerMovementInputEvent(originalForward, originalStrafing);

            MinecraftForge.EVENT_BUS.post(event);

            self.moveEntityWithHeading(event.strafe, event.forward);
        }
        else {
            self.moveEntityWithHeading(originalStrafing, originalForward);
        }
    }
}