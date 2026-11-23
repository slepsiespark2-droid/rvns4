package ravens4.mixin.impl.accessor;

import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.util.IChatComponent;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin(GuiPlayerTabOverlay.class)
public interface IAccessorGuiPlayerTabOverlay {
    @Accessor(value = "header", remap = false)
    IChatComponent getHeader();

    @Accessor(value = "footer", remap = false)
    IChatComponent getFooter();
}
