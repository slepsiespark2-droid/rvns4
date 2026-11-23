package ravens4.mixin.impl.accessor;

import net.minecraft.util.MouseHelper;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin(MouseHelper.class)
public interface IAccessorMouseHelper {
    @Accessor(value = "deltaX", remap = false)
    int getDeltaX();

    @Accessor(value = "deltaY", remap = false)
    int getDeltaY();
}
