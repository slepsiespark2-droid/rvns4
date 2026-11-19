package ravens4.bus;

import net.minecraft.entity.EntityLivingBase;

public class LivingEvent extends EntityEvent {
    public final EntityLivingBase entityLiving;

    public LivingEvent(EntityLivingBase entity) {
        super(entity);
        this.entityLiving = entity;
    }

    @Cancelable
    public static class LivingUpdateEvent extends LivingEvent {
        public LivingUpdateEvent(EntityLivingBase entity) {
            super(entity);
        }
    }

    public static class LivingJumpEvent extends LivingEvent {
        public LivingJumpEvent(EntityLivingBase entity) {
            super(entity);
        }
    }
}
