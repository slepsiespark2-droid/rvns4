package ravens4.mixin.impl.accessor;

import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@SideOnly(Side.CLIENT)
@Mixin(ShaderGroup.class)
public interface IAccessorShaderGroup {
    @Accessor
    List<Shader> getListShaders();
}
