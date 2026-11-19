package ravens4.event;

import ravens4.bus.Event;

public class ClientLookEvent extends Event {
    public float yaw;
    public float pitch;

    public ClientLookEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
