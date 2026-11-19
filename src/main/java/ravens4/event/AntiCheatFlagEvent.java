package ravens4.event;

import net.minecraft.entity.Entity;
import ravens4.bus.Event;

public class AntiCheatFlagEvent extends Event {
    public String flag;
    public Entity entity;

    public AntiCheatFlagEvent(String flag, Entity entity) {
        this.flag = flag;
        this.entity = entity;
    }
}
