package ravens4.event;

import net.minecraft.util.MovingObjectPosition;
import ravens4.bus.Cancelable;
import ravens4.bus.Event;

/**
 * Fired at the start of Minecraft.clickMouse() before swingItem() or attackEntity().
 * Cancel to prevent both the swing packet (C0A) and attack packet (C02) from being sent
 * for normal left-click attacks.
 * <p>
 * For direct calls to attackEntity() (e.g. KillAura, scripts), use {@link AttackEvent} instead,
 * which still fires in PlayerControllerMP.attackEntity and can cancel the attack packet only.
 */
@Cancelable
public class PreAttackEvent extends Event {

    /** The current mouse-over from this tick's getMouseOver; may be null or a block/entity hit. */
    public final MovingObjectPosition objectMouseOver;

    public PreAttackEvent(MovingObjectPosition objectMouseOver) {
        this.objectMouseOver = objectMouseOver;
    }
}
