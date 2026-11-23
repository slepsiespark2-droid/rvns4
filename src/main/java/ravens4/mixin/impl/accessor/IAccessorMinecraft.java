package ravens4.mixin.impl.accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.Timer;
import ravens4.bus.Side;
import ravens4.bus.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@SideOnly(Side.CLIENT)
@Mixin(Minecraft.class)
public interface IAccessorMinecraft {
    @Accessor(value = "timer", remap = false)
    Timer getTimer();

    @Accessor(value = "myNetworkManager", remap = false)
    NetworkManager getMyNetworkManager();

    @Accessor(value = "rightClickDelayTimer", remap = false)
    int getRightClickDelayTimer();

    @Accessor(value = "rightClickDelayTimer", remap = false)
    void setRightClickDelayTimer(int delay);

    @Accessor(value = "leftClickCounter", remap = false)
    void setLeftClickCounter(int delay);

    @Invoker(value = "rightClickMouse", remap = false)
    void callRightClickMouse();

    @Invoker(value = "clickMouse", remap = false)
    void callClickMouse();

    @Invoker(value = "dispatchKeypresses", remap = false)
    void invokeDispatchKeypresses();
}