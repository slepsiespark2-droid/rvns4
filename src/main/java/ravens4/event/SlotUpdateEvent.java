package ravens4.event;

import ravens4.bus.Cancelable;
import ravens4.bus.Event;

@Cancelable
public class SlotUpdateEvent extends Event {
    public int slot;

    public SlotUpdateEvent(int slot) {
        this.slot = slot;
    }
}
