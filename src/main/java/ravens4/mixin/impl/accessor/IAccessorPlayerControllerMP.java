package ravens4.mixin.impl.accessor;

import net.minecraft.util.BlockPos;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.*;
import net.minecraft.client.multiplayer.*;
import org.spongepowered.asm.mixin.gen.*;

@SideOnly(Side.CLIENT)
@Mixin(PlayerControllerMP.class)
public interface IAccessorPlayerControllerMP {
    @Accessor
    float getCurBlockDamageMP();

    @Accessor
    BlockPos getCurrentBlock();

    @Accessor
    void setCurBlockDamageMP(float damage);

    @Accessor
    int getBlockHitDelay();

    @Accessor
    void setBlockHitDelay(int delay);

    @Invoker(value = "syncCurrentPlayItem", remap = false)
    void callSyncCurrentPlayItem();
}
