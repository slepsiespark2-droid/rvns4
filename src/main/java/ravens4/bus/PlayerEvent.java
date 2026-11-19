package ravens4.bus;

import net.minecraft.entity.player.EntityPlayer;

public class PlayerEvent extends LivingEvent {
    public final EntityPlayer entityPlayer;

    public PlayerEvent(EntityPlayer player) {
        super(player);
        this.entityPlayer = player;
    }
}
