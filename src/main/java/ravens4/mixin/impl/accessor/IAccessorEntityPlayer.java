package ravens4.mixin.impl.accessor;

import net.minecraft.entity.player.EntityPlayer;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin(EntityPlayer.class)
public interface IAccessorEntityPlayer {
    @Accessor(value = "itemInUseCount", remap = false)
    void setItemInUseCount(int count);
}
