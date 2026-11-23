package ravens4.mixin.impl.accessor;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface IAccessorEntity {
    @Accessor(value = "fire", remap = false)
    int getFire();

    @Accessor(value = "nextStepDistance", remap = false)
    int getNextStepDistance();

    @Accessor(value = "isInWeb", remap = false)
    boolean getIsInWeb();
}
