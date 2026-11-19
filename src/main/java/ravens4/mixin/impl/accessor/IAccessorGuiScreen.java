package ravens4.mixin.impl.accessor;

import net.minecraft.client.gui.GuiScreen;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@SideOnly(Side.CLIENT)
@Mixin(GuiScreen.class)
public interface IAccessorGuiScreen {
    @Invoker("mouseClicked")
    void callMouseClicked(int mouseX, int mouseY, int mouseButton);
}
