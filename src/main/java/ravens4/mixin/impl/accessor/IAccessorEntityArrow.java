package ravens4.mixin.impl.accessor;

import net.minecraft.entity.projectile.EntityArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityArrow.class)
public interface IAccessorEntityArrow {
    @Accessor(value = "inGround", remap = false)
    boolean getInGround();
}
