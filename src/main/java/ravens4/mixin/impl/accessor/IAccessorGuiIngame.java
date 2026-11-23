package ravens4.mixin.impl.accessor;

import net.minecraft.client.gui.GuiIngame;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin(GuiIngame.class)
public interface IAccessorGuiIngame {
    @Accessor(value = "recordPlaying", remap = false)
    String getRecordPlaying();

    @Accessor(value = "displayedTitle", remap = false)
    String getDisplayedTitle();

    @Accessor(value = "displayedSubTitle", remap = false)
    String getDisplayedSubTitle();
}
