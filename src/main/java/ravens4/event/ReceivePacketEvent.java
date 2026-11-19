package ravens4.event;

import net.minecraft.network.Packet;
import ravens4.bus.Cancelable;
import ravens4.bus.Event;

@Cancelable
public class ReceivePacketEvent extends Event {
    private Packet<?> packet;

    public ReceivePacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
