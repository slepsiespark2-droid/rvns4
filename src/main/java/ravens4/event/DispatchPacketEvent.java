package ravens4.event;

import net.minecraft.network.Packet;
import ravens4.bus.Event;

public class DispatchPacketEvent extends Event {
    private Packet<?> packet;

    public DispatchPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}