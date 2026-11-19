package ravens4.event;

import net.minecraft.client.gui.GuiScreen;
import ravens4.bus.Event;

public class GuiUpdateEvent extends Event {
    public GuiScreen guiScreen;
    public boolean opened;

    public GuiUpdateEvent(GuiScreen guiScreen, boolean opened) {
        this.guiScreen = guiScreen;
        this.opened = opened;
    }
}
