package ravens4.bus;

import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public abstract class GuiScreenEvent extends Event {
    public final GuiScreen gui;

    public GuiScreenEvent(GuiScreen gui) {
        this.gui = gui;
    }

    public static class InitGuiEvent extends GuiScreenEvent {
        public List<GuiButton> buttonList;

        public InitGuiEvent(GuiScreen gui, List<GuiButton> buttonList) {
            super(gui);
            this.buttonList = buttonList;
        }

        @Cancelable
        public static class Pre extends InitGuiEvent {
            public Pre(GuiScreen gui, List<GuiButton> buttonList) {
                super(gui, buttonList);
            }
        }

        public static class Post extends InitGuiEvent {
            public Post(GuiScreen gui, List<GuiButton> buttonList) {
                super(gui, buttonList);
            }
        }
    }

    public static class DrawScreenEvent extends GuiScreenEvent {
        public final int mouseX;
        public final int mouseY;
        public final float renderPartialTicks;

        public DrawScreenEvent(GuiScreen gui, int mouseX, int mouseY, float renderPartialTicks) {
            super(gui);
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.renderPartialTicks = renderPartialTicks;
        }

        @Cancelable
        public static class Pre extends DrawScreenEvent {
            public Pre(GuiScreen gui, int mouseX, int mouseY, float renderPartialTicks) {
                super(gui, mouseX, mouseY, renderPartialTicks);
            }
        }

        public static class Post extends DrawScreenEvent {
            public Post(GuiScreen gui, int mouseX, int mouseY, float renderPartialTicks) {
                super(gui, mouseX, mouseY, renderPartialTicks);
            }
        }
    }
}
