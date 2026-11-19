package ravens4.event;

import ravens4.bus.Cancelable;
import ravens4.bus.Event;

@Cancelable
public class PreSlotScrollEvent extends Event {
    public int slot;
    public int previousSlot;

    public PreSlotScrollEvent(int slot, int previousSlot) {
        this.slot = slot;
        this.previousSlot = previousSlot;
    }
}
