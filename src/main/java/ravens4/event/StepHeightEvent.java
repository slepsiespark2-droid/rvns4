package ravens4.event;

import net.minecraft.entity.Entity;
import ravens4.bus.Event;

public class StepHeightEvent extends Event {
    public Entity entity;
    public float stepHeight;

    public StepHeightEvent(Entity entity, float stepHeight) {
        this.entity = entity;
        this.stepHeight = stepHeight;
    }
}
