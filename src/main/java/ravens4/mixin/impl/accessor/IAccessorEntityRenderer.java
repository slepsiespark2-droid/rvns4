package ravens4.mixin.impl.accessor;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@SideOnly(Side.CLIENT)
@Mixin(EntityRenderer.class)
public interface IAccessorEntityRenderer {
    @Invoker(value = "setupCameraTransform", remap = false)
    void callSetupCameraTransform(float partialTicks, int pass);

    @Invoker(value = "loadShader", remap = false)
    void callLoadShader(ResourceLocation resourceLocationIn);

    @Accessor(value = "shaderResourceLocations", remap = false)
    ResourceLocation[] getShaderResourceLocations();

    @Accessor(value = "useShader", remap = false)
    boolean getUseShader();

    @Accessor(value = "useShader", remap = false)
    void setUseShader(boolean useShader);

    @Accessor(value = "shaderIndex", remap = false)
    int getShaderIndex();

    @Accessor(value = "shaderIndex", remap = false)
    void setShaderIndex(int index);

    @Accessor(value = "thirdPersonDistance", remap = false)
    void setThirdPersonDistance(float distance);

    @Accessor(value = "pointedEntity", remap = false)
    Entity getPointedEntity();

    @Accessor(value = "pointedEntity", remap = false)
    void setPointedEntity(Entity entity);
}