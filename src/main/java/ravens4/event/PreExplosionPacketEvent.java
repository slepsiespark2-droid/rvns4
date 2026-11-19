package ravens4.event;

import net.minecraft.network.play.server.S27PacketExplosion;
import ravens4.bus.Cancelable;
import ravens4.bus.Event;

@Cancelable
public class PreExplosionPacketEvent extends Event {
    public S27PacketExplosion packet;

    public PreExplosionPacketEvent(S27PacketExplosion packet) {
        this.packet = packet;
    }

}