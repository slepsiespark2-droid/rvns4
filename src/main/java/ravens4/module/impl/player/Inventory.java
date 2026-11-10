package ravens4.module.impl.player;

import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Inventory helper. Modes:
 * <ul>
 *   <li><b>Auto Hotkey</b> - while you hold (or click) left/right click, jumps to a chosen hotbar
 *   slot (1-9) and, when you let go, optionally returns to the slot you were on. Handy for e.g.
 *   keeping a pickaxe on slot 1 and swapping to a sword/torches/blocks only while clicking.</li>
 * </ul>
 */
public class Inventory extends Module {
    private static final String[] MODES = new String[]{"Auto Hotkey"};

    private final SliderSetting mode;
    private final ButtonSetting rightClick;
    private final ButtonSetting leftClick;
    private final ButtonSetting switchBack;
    private final SliderSetting selectedHotkey;

    // slot we came from, or -1 when we are not currently overriding the slot
    private int previousSlot = -1;

    public Inventory() {
        super("Inventory", category.player);
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(rightClick = new ButtonSetting("Right click", true));
        this.registerSetting(leftClick = new ButtonSetting("Left click", false));
        this.registerSetting(switchBack = new ButtonSetting("Switch back", true));
        this.registerSetting(selectedHotkey = new SliderSetting("Selected hotkey", 2, 1, 9, 1));
    }

    @Override
    public void guiUpdate() {
        boolean autoHotkey = mode.getInput() == 0;
        rightClick.setVisible(autoHotkey, this);
        leftClick.setVisible(autoHotkey, this);
        switchBack.setVisible(autoHotkey, this);
        selectedHotkey.setVisible(autoHotkey, this);
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @Override
    public void onDisable() {
        // leave the player where they started rather than stranding them on the override slot
        restorePreviousSlot();
    }

    // START phase: runs before Minecraft processes this tick's clicks, so the very click that
    // triggered the swap already uses the new slot. Raw input is read because the key binding
    // state is only updated later in the tick.
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            previousSlot = -1; // GUI opened / world left: forget the override, don't yank slots later
            return;
        }
        if (mode.getInput() == 0) {
            autoHotkey();
        }
    }

    private void autoHotkey() {
        int target = (int) selectedHotkey.getInput() - 1;
        boolean held = (rightClick.isToggled() && isDown(mc.gameSettings.keyBindUseItem))
                || (leftClick.isToggled() && isDown(mc.gameSettings.keyBindAttack));
        int current = mc.thePlayer.inventory.currentItem;

        if (held) {
            if (previousSlot == -1 && current != target) {
                previousSlot = current;
                Utils.switchSlot(target, true);
            } else if (previousSlot != -1 && current != target) {
                // player scrolled / pressed a number key while holding: respect it, stop overriding
                previousSlot = -1;
            }
        } else if (previousSlot != -1) {
            if (switchBack.isToggled()) {
                restorePreviousSlot();
            } else {
                previousSlot = -1;
            }
        }
    }

    private void restorePreviousSlot() {
        if (previousSlot != -1 && mc.thePlayer != null && previousSlot != mc.thePlayer.inventory.currentItem) {
            Utils.switchSlot(previousSlot, true);
        }
        previousSlot = -1;
    }

    private static boolean isDown(KeyBinding binding) {
        int code = binding.getKeyCode();
        if (code < 0) {
            return Mouse.isButtonDown(code + 100);
        }
        return code > 0 && Keyboard.isKeyDown(code);
    }
}
