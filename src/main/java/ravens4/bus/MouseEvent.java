package ravens4.bus;

import org.lwjgl.input.Mouse;

/** A mouse button, move or scroll. The fields are final on purpose: ReflectionUtils sets button/buttonstate by reflection. */
@Cancelable
public class MouseEvent extends Event {
    public final int x;
    public final int y;
    public final int dx;
    public final int dy;
    public final int dwheel;
    public final int button;
    public final boolean buttonstate;
    public final long nanoseconds;

    /** Reads the mouse event the game is handling right now. */
    public MouseEvent() {
        this.x = Mouse.getEventX();
        this.y = Mouse.getEventY();
        this.dx = Mouse.getEventDX();
        this.dy = Mouse.getEventDY();
        this.dwheel = Mouse.getEventDWheel();
        this.button = Mouse.getEventButton();
        this.buttonstate = Mouse.getEventButtonState();
        this.nanoseconds = Mouse.getEventNanoseconds();
    }

    public MouseEvent(int x, int y, int dx, int dy, int dwheel, int button, boolean buttonstate, long nanoseconds) {
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.dwheel = dwheel;
        this.button = button;
        this.buttonstate = buttonstate;
        this.nanoseconds = nanoseconds;
    }
}
