package ravens4.bus;

import net.minecraft.client.gui.GuiScreen;

@Cancelable
public class GuiOpenEvent extends Event {
    public GuiScreen gui;

    public GuiOpenEvent(GuiScreen gui) {
        this.gui = gui;
    }
}
