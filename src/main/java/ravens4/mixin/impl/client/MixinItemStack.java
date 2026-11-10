package ravens4.mixin.impl.client;

import ravens4.module.impl.render.PlayerESP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void suppressGlintDuringOutlinePass(CallbackInfoReturnable<Boolean> cir) {
        if (PlayerESP.renderingOutlinePass) {
            cir.setReturnValue(false);
        }
    }
}
