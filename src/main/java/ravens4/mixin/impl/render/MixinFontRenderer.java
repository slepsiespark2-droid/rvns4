package ravens4.mixin.impl.render;

import ravens4.module.ModuleManager;
import ravens4.module.impl.other.NameHider;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@SideOnly(Side.CLIENT)
@Mixin(FontRenderer.class)
public class MixinFontRenderer {
    @ModifyVariable(method = "renderString", at = @At("HEAD"), require = 1, ordinal = 0, argsOnly = true)
    private String renderString(String string) {
        if (string == null)
            return null;
        if ((ModuleManager.nameHider != null) && ModuleManager.nameHider.isEnabled()) {
            string = NameHider.getFakeName(string);
        }

        return string;
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), require = 1, ordinal = 0, argsOnly = true)
    private String getStringWidth(String string) {
        if (string == null)
            return null;
        if ((ModuleManager.nameHider != null) && ModuleManager.nameHider.isEnabled()) {
            string = NameHider.getFakeName(string);
        }

        return string;
    }
}
