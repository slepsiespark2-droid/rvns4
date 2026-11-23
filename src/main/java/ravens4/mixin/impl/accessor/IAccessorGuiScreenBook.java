package ravens4.mixin.impl.accessor;

import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(GuiScreenBook.class)
public interface IAccessorGuiScreenBook {
    @Accessor(value = "field_175386_A", remap = false)
    List<IChatComponent> getBookContents();
}
