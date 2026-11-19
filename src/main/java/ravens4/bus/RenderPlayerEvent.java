package ravens4.bus;

import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;

public abstract class RenderPlayerEvent extends Event {
    public final EntityPlayer entityPlayer;
    public final RenderPlayer renderer;
    public final float partialRenderTick;

    protected RenderPlayerEvent(EntityPlayer entityPlayer, RenderPlayer renderer, float partialRenderTick) {
        this.entityPlayer = entityPlayer;
        this.renderer = renderer;
        this.partialRenderTick = partialRenderTick;
    }

    @Cancelable
    public static class Pre extends RenderPlayerEvent {
        public Pre(EntityPlayer entityPlayer, RenderPlayer renderer, float partialRenderTick) {
            super(entityPlayer, renderer, partialRenderTick);
        }
    }

    public static class Post extends RenderPlayerEvent {
        public Post(EntityPlayer entityPlayer, RenderPlayer renderer, float partialRenderTick) {
            super(entityPlayer, renderer, partialRenderTick);
        }
    }
}
