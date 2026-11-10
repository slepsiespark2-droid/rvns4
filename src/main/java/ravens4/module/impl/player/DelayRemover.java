package ravens4.module.impl.player;

import ravens4.mixin.impl.accessor.IAccessorEntityLivingBase;
import ravens4.mixin.impl.accessor.IAccessorMinecraft;
import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.utility.Utils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class DelayRemover extends Module {
    public ButtonSetting oldReg, removeJumpTicks;

    public DelayRemover() {
        super("Delay Remover", category.player, 0);
        this.registerSetting(oldReg = new ButtonSetting("1.7 hitreg", true));
        this.registerSetting(removeJumpTicks = new ButtonSetting("Remove jump ticks", false));
        this.closetModule = true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !mc.inGameHasFocus || !Utils.nullCheck()) {
            return;
        }
        if (oldReg.isToggled()) {
            ((IAccessorMinecraft) mc).setLeftClickCounter(0);
        }
        if (removeJumpTicks.isToggled()) {
            ((IAccessorEntityLivingBase) mc.thePlayer).setJumpTicks(0);
        }
    }
}
