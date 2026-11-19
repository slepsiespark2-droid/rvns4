package ravens4.event;

import ravens4.bus.Event;

public class PostSetSliderEvent extends Event {
    public double previousVal;
    public double newVal;

    public PostSetSliderEvent(double previousVal, double newVal) {
        this.previousVal = previousVal;
        this.newVal = newVal;
    }
}
