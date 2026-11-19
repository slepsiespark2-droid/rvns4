package ravens4.event;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import ravens4.bus.Cancelable;
import ravens4.bus.Event;

@Cancelable
public class PreEntityVelocityEvent extends Event {
    public S12PacketEntityVelocity packet;

    public PreEntityVelocityEvent(S12PacketEntityVelocity packet) {
        this.packet = packet;
    }
}