package ravens4.event;

import ravens4.bus.Event;

public class PrePlayerMovementInputEvent extends Event {
    public float forward;
    public float strafe;

    public PrePlayerMovementInputEvent(float forward, float strafe) {
        this.forward = forward;
        this.strafe = strafe;
    }
}
