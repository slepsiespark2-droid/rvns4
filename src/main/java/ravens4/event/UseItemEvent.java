package ravens4.event;

import net.minecraft.item.ItemStack;
import ravens4.bus.Cancelable;
import ravens4.bus.Event;

@Cancelable
public class UseItemEvent extends Event {
    public ItemStack usedItemStack;

    public UseItemEvent(ItemStack usedItemStack) {
        this.usedItemStack = usedItemStack;
    }
}
