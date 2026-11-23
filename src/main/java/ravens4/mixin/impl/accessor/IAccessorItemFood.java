package ravens4.mixin.impl.accessor;

import net.minecraft.item.ItemFood;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin(ItemFood.class)
public interface IAccessorItemFood {
    @Accessor(value = "alwaysEdible", remap = false)
    boolean getAlwaysEdible();
}
