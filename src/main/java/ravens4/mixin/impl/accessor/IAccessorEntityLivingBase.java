package ravens4.mixin.impl.accessor;

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityLivingBase.class)
public interface IAccessorEntityLivingBase {
    @Accessor(value = "jumpTicks", remap = false)
    void setJumpTicks(int ticks);

    @Accessor(value = "jumpTicks", remap = false)
    int getJumpTicks();
}