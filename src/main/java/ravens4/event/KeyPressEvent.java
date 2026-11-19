package ravens4.event;

import ravens4.bus.Cancelable;
import ravens4.bus.Event;

@Cancelable
public class KeyPressEvent extends Event {
    public char typedChar;
    public int keyCode;

    public KeyPressEvent(char typedChar, int keyCode) {
        this.typedChar = typedChar;
        this.keyCode = keyCode;
    }
}
