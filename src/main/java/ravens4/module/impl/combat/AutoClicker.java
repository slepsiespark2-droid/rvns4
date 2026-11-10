package ravens4.module.impl.combat;

import ravens4.event.PrePlayerInteractEvent;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.mixin.impl.accessor.IAccessorMinecraft;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.utility.ReflectionUtils;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.Utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.input.Mouse;

import net.minecraft.inventory.Slot;

import java.lang.reflect.Field;
import java.util.Random;

public class AutoClicker extends Module {
    public SliderSetting minCPS;
    public SliderSetting maxCPS;
    public ButtonSetting notUsingItem;
    public ButtonSetting breakBlocks;
    public ButtonSetting weaponOnly;
    public ButtonSetting disableCreative;
    public ButtonSetting inventory;
    public SliderSetting inventoryStartDelay;

    /** Modules (Clutch, Scaffold's Keep-y mode) that are placing blocks and need the clicker paused. */
    private final java.util.Set<Object> suppressors = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());

    private long nextClickTime;
    private long inventoryNextClickTime;
    private boolean isHoldingBlockBreak;

    private Random rand;
    private static Field hoveredSlotField;

    public AutoClicker() {
        super("Auto Clicker", category.combat, 0);
        this.registerSetting(new DescriptionSetting("Removes miss delay while clicking."));
        this.registerSetting(minCPS = new SliderSetting("Min CPS", 8.0, 1.0, 20.0, 0.5));
        this.registerSetting(maxCPS = new SliderSetting("Max CPS", 12.0, 1.0, 20.0, 0.5));
        this.registerSetting(notUsingItem = new ButtonSetting("Not using item", false));
        this.registerSetting(breakBlocks = new ButtonSetting("Break blocks", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(disableCreative = new ButtonSetting("Disable in creative", false));
        this.registerSetting(inventory = new ButtonSetting("Inventory", false));
        this.registerSetting(inventoryStartDelay = new SliderSetting("Start delay", "ms", 100.0, 0.0, 250.0, 10.0));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        double min = Math.min(minCPS.getInput(), maxCPS.getInput());
        double max = Math.max(minCPS.getInput(), maxCPS.getInput());
        return formatCps(min) + "-" + formatCps(max);
    }

    /**
     * Pauses or resumes clicking without turning the module off, so the module state (and anything that listens to it,
     * like the Notifs "Enabled/Disabled" notifications) never changes.
     */
    public void setSuppressed(Object owner, boolean suppressed) {
        if (suppressed) {
            if (suppressors.add(owner) && suppressors.size() == 1) {
                this.nextClickTime = 0L;
                this.inventoryNextClickTime = 0L;
                this.isHoldingBlockBreak = false;
            }
        }
        else {
            suppressors.remove(owner);
        }
    }

    public boolean isSuppressed() {
        return !suppressors.isEmpty();
    }

    @Override
    public void onEnable() {
        this.rand = new Random();
        this.nextClickTime = 0L;
        this.inventoryNextClickTime = 0L;
        this.isHoldingBlockBreak = false;
        ensureHoveredSlotField();
    }

    @Override
    public void onDisable() {
        this.nextClickTime = 0L;
        this.inventoryNextClickTime = 0L;
        this.isHoldingBlockBreak = false;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        if (isSuppressed()) {
            return;
        }
        if (!inventory.isToggled()) {
            return;
        }
        if (!Utils.nullCheck()) {
            return;
        }
        if (!(mc.currentScreen instanceof GuiContainer)) {
            inventoryNextClickTime = 0L;
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            inventoryNextClickTime = 0L;
            return;
        }

        ensureHoveredSlotField();
        if (hoveredSlotField == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (inventoryNextClickTime == 0L) {
            inventoryNextClickTime = now + (long) inventoryStartDelay.getInput();
        }

        int clicks = 0;
        while (inventoryNextClickTime <= now) {
            clicks++;
            inventoryNextClickTime += nextDelay();
        }
        if (clicks <= 0) {
            return;
        }

        GuiContainer gui = (GuiContainer) mc.currentScreen;
        Slot slot = getHoveredSlot(gui);
        if (slot == null || slot.slotNumber < 0) {
            return;
        }

        int windowId = gui.inventorySlots.windowId;
        int slotId = slot.slotNumber;
        int mode = net.minecraft.client.gui.GuiScreen.isShiftKeyDown() ? 1 : 0;

        if (mc.playerController == null || mc.thePlayer == null) {
            return;
        }

        for (int i = 0; i < clicks; i++) {
            mc.playerController.windowClick(windowId, slotId, 0, mode, mc.thePlayer);
        }
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck()) return;
        if (isSuppressed()) return;
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) return;

        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        if (Mouse.isButtonDown(0)) {
            long now = System.currentTimeMillis();
            if (nextClickTime == 0) {
                nextClickTime = now + nextDelay();
            }

            int clicks = 0;
            while (nextClickTime <= now) {
                clicks++;
                nextClickTime += nextDelay();
            }

            if (notUsingItem.isToggled() && mc.thePlayer.isUsingItem()) return;
            if (disableCreative.isToggled() && mc.thePlayer.capabilities.isCreativeMode) return;
            if (mc.currentScreen != null || !mc.inGameHasFocus) return;
            if (weaponOnly.isToggled() && !Utils.holdingWeapon()) return;

            if (breakBlocks.isToggled()) {
                if (!mc.thePlayer.capabilities.allowEdit) {
                    if (this.isHoldingBlockBreak) {
                        KeyBinding.setKeyBindState(key, false);
                        ReflectionUtils.setButton(0, false);
                        this.isHoldingBlockBreak = false;
                    }
                }
                else if (mc.objectMouseOver != null) {
                BlockPos pos = mc.objectMouseOver.getBlockPos();
                if (pos != null) {
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block != Blocks.air && !(block instanceof BlockLiquid)) {
                        if (!this.isHoldingBlockBreak) {
                            KeyBinding.setKeyBindState(key, true);
                            ReflectionUtils.setButton(0, true);
                            this.isHoldingBlockBreak = true;
                        }
                        return;
                    }
                    if (this.isHoldingBlockBreak) {
                        KeyBinding.setKeyBindState(key, false);
                        ReflectionUtils.setButton(0, false);
                        this.isHoldingBlockBreak = false;
                        return;
                    }
                } else {
                    this.isHoldingBlockBreak = false;
                }
                }
            }

            for (int i = 0; i < clicks; i++) {
                ((IAccessorMinecraft) mc).setLeftClickCounter(0);
                KeyBinding.onTick(key);
                ReflectionUtils.setButton(0, true);
            }
        } else {
            this.nextClickTime = 0L;
            this.isHoldingBlockBreak = false;
            KeyBinding.setKeyBindState(key, false);
            ReflectionUtils.setButton(0, false);
        }
    }

    private long nextDelay() {
        double min = Math.min(minCPS.getInput(), maxCPS.getInput());
        double max = Math.max(minCPS.getInput(), maxCPS.getInput());
        double cps = max <= min ? min : min + (rand.nextDouble() * (max - min));
        return Math.max(1L, Math.round(1000.0D / Math.max(1.0D, cps)));
    }

    private String formatCps(double cps) {
        return cps == Math.rint(cps) ? Integer.toString((int) cps) : Double.toString(Utils.round(cps, 1));
    }

    private static void ensureHoveredSlotField() {
        if (hoveredSlotField != null) {
            return;
        }
        try {
            hoveredSlotField = GuiContainer.class.getDeclaredField("theSlot");
            hoveredSlotField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                hoveredSlotField = GuiContainer.class.getDeclaredField("field_147006_u");
                hoveredSlotField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
                hoveredSlotField = null;
            }
        }
    }

    private static Slot getHoveredSlot(GuiContainer gui) {
        if (hoveredSlotField == null || gui == null) {
            return null;
        }
        try {
            Object value = hoveredSlotField.get(gui);
            if (value instanceof Slot) {
                return (Slot) value;
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }
}
