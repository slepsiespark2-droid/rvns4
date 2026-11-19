package ravens4.event;

import net.minecraft.network.Packet;
import ravens4.bus.Event;

public class NoEventPacketEvent extends Event {
    private Packet<?> packet;

    public NoEventPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
