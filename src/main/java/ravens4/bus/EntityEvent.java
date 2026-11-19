package ravens4.bus;

import net.minecraft.entity.Entity;

public class EntityEvent extends Event {
    public final Entity entity;

    public EntityEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }
}
