package ravens4.mixin.impl.accessor;

import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderManager.class)
public interface IAccessorRenderManager {
    @Accessor(value = "renderPosX", remap = false)
    double getRenderPosX();

    @Accessor(value = "renderPosY", remap = false)
    double getRenderPosY();

    @Accessor(value = "renderPosZ", remap = false)
    double getRenderPosZ();
}
