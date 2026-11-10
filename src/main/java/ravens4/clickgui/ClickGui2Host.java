package ravens4.clickgui;

import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ravens4.event.KeyPressEvent;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.client.Gui;
import ravens4.utility.Utils;

import java.util.ArrayList;

/**
 * Forwards the render, key and mouse events to the {@link ClickGui2} menu while the Gui module is in ClickGui2 mode.
 * This does what the script engine used to do for the original script. Because the menu code is a ported script, any
 * exception it throws is caught here: the mode switches back to ClickGui instead of crashing the game.
 */
public class ClickGui2Host {
    private final ClickGui2 menu;
    private boolean active;

    public ClickGui2Host(ClickGui2 menu) {
        this.menu = menu;
    }

    /** Called by the Gui module when its key is pressed in ClickGui2 mode. */
    public void open() {
        if (!Utils.nullCheck()) {
            return;
        }
        active = true;
        try {
            menu.openMenu();
        }
        catch (Throwable throwable) {
            fail(throwable);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        if (!Gui.isClickGui2()) {
            deactivate();
            return;
        }

        active = true;
        try {
            menu.onRenderTick(e.renderTickTime);
        }
        catch (Throwable throwable) {
            fail(throwable);
        }
    }

    /**
     * The old click GUI makes every module refresh which of its settings are visible (Module.guiUpdate) while it is open.
     * ClickGui2 is a different screen, so that has to be done here, otherwise changing a mode would never show or hide
     * the settings that depend on it.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !active || !Gui.isClickGui2() || !Utils.nullCheck() || !menu.isMenuOpen()) {
            return;
        }
        try {
            for (Module module : new ArrayList<Module>(ModuleManager.modules)) {
                module.guiUpdate();
            }
        }
        catch (Throwable ignored) {
            // a module's own visibility logic failed; that is not a reason to switch the menu off
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyTyped(KeyPressEvent e) {
        if (e.isCanceled() || !active || !Gui.isClickGui2() || !Utils.nullCheck()) {
            return;
        }
        try {
            if (!menu.onKeyPress(e.typedChar, e.keyCode)) {
                e.setCanceled(true);
            }
        }
        catch (Throwable throwable) {
            fail(throwable);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!active || !Gui.isClickGui2() || !Utils.nullCheck()) {
            return;
        }
        try {
            if (!menu.onMouse(e.button, e.buttonstate, 0, 0)) {
                e.setCanceled(true);
            }
        }
        catch (Throwable throwable) {
            fail(throwable);
        }
    }

    /** The mode was switched away from ClickGui2: let the menu close itself and drop what it was showing. */
    private void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        try {
            menu.onDisable();
        }
        catch (Throwable ignored) {
        }
    }

    private void fail(Throwable throwable) {
        deactivate();
        Gui.resetToClickGui();
        Utils.sendMessage("&cClickGui2 hit an error (&7" + throwable.getClass().getSimpleName() + "&c) and was switched off. Mode is back to ClickGui.");
    }
}
