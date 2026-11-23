package ravens4.mixin.impl.accessor;

import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.*;
import net.minecraft.client.entity.*;
import org.spongepowered.asm.mixin.gen.*;

@SideOnly(Side.CLIENT)
@Mixin(EntityPlayerSP.class)
public interface IAccessorEntityPlayerSP {
    @Accessor(value = "lastReportedPosX", remap = false)
    double getLastReportedPosX();

    @Accessor(value = "lastReportedPosY", remap = false)
    double getLastReportedPosY();

    @Accessor(value = "lastReportedPosZ", remap = false)
    double getLastReportedPosZ();

    @Accessor(value = "lastReportedYaw", remap = false)
    float getLastReportedYaw();
}