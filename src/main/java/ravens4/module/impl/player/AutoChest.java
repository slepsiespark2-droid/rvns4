package ravens4.module.impl.player;

import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.KeySetting;
import ravens4.module.setting.impl.SliderSetting;

import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ravens4.event.PreUpdateEvent;

import java.util.HashSet;

// Adapted from an uploaded script (loaded from PugrillaDev/Raven-Scripts on GitHub) that
// auto-transfers iron/gold/diamond/emerald between an open chest and the player's inventory.
// It used to only run in an active Hypixel Bed Wars game; it now works on any server. One real simplification from the source: the script manually rebuilt a combined
// chest+inventory slot index (working around its own scripting API's limits) - the real
// ContainerChest's own inventorySlots list already IS that combined index in the exact order
// windowClick expects, so this just uses it directly instead of replicating that remapping.
public class AutoChest extends Module {

    private final ButtonSetting automatic;
    private final ButtonSetting stopWhenFinished;
    private final SliderSetting delaySetting;
    private final ButtonSetting iron;
    private final ButtonSetting gold;
    private final ButtonSetting diamonds;
    private final ButtonSetting emeralds;
    private final KeySetting invToChestKey;
    private final KeySetting chestToInvKey;

    private int delayTicks;
    private int inChestDelay;
    private boolean foundAllItemsInInventory;
    private boolean foundAllItemsInChest;
    private boolean foundItemsInChest;
    private boolean foundItemsInInventory;

    public AutoChest() {
        super("AutoChest", category.player);
        this.registerSetting(automatic = new ButtonSetting("Automatic", false));
        this.registerSetting(stopWhenFinished = new ButtonSetting("Stop When Finished", true));
        this.registerSetting(delaySetting = new SliderSetting("Delay", "ms", 50, 0, 1000, 50));
        this.registerSetting(iron = new ButtonSetting("Iron", true));
        this.registerSetting(gold = new ButtonSetting("Gold", true));
        this.registerSetting(diamonds = new ButtonSetting("Diamonds", true));
        this.registerSetting(emeralds = new ButtonSetting("Emeralds", true));
        this.registerSetting(invToChestKey = new KeySetting("Inventory To Chest", 0));
        this.registerSetting(chestToInvKey = new KeySetting("Chest To Inventory", 0));
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!this.isEnabled()) return;
        if (delayTicks > 0) delayTicks--;

        if (mc.thePlayer == null || !(mc.thePlayer.openContainer instanceof ContainerChest)) {
            resetState();
            return;
        }

        ContainerChest chestContainer = (ContainerChest) mc.thePlayer.openContainer;
        if (!isChestTitle(chestContainer.getLowerChestInventory().getDisplayName())) {
            resetState();
            return;
        }

        if (inChestDelay > 0 && --inChestDelay != 0) return;

        boolean isAutomatic = automatic.isToggled();
        boolean stopWhenDone = stopWhenFinished.isToggled();
        int delay = (int) (delaySetting.getInput() / 50);

        HashSet<String> items = new HashSet<>();
        if (iron.isToggled()) items.add("iron_ingot");
        if (gold.isToggled()) items.add("gold_ingot");
        if (diamonds.isToggled()) items.add("diamond");
        if (emeralds.isToggled()) items.add("emerald");

        int chestSize = chestContainer.getLowerChestInventory().getSizeInventory();
        int totalSlots = chestContainer.inventorySlots.size();
        boolean clicked = false;

        boolean invToChestPressed = invToChestKey.getKey() != 0 && invToChestKey.isPressed();
        boolean chestToInvPressed = chestToInvKey.getKey() != 0 && chestToInvKey.isPressed();

        if ((isAutomatic && !foundItemsInChest && !foundAllItemsInInventory) || (invToChestPressed && delayTicks == 0)) {
            for (int i = chestSize; i < totalSlots; i++) {
                ItemStack item = chestContainer.getSlot(i).getStack();
                if (!matchesTracked(item, items)) continue;
                windowClick(i, 0, 1);
                clicked = true;
                delayTicks = delay;
                foundItemsInInventory = true;
                if (delay > 0) break;
            }
            if (stopWhenDone && !clicked && foundItemsInInventory) {
                foundAllItemsInInventory = true;
            }
        }

        clicked = false;

        if ((isAutomatic && !foundItemsInInventory && !foundAllItemsInChest) || (chestToInvPressed && delayTicks == 0)) {
            for (int i = 0; i < chestSize; i++) {
                ItemStack item = chestContainer.getSlot(i).getStack();
                if (!matchesTracked(item, items)) continue;
                windowClick(i, 1, 1);
                clicked = true;
                delayTicks = delay;
                foundItemsInChest = true;
                if (delay > 0) break;
            }
            if (stopWhenDone && !clicked && foundItemsInChest) {
                foundAllItemsInChest = true;
            }
        }
    }

    private void resetState() {
        inChestDelay = 2;
        foundItemsInInventory = false;
        foundItemsInChest = false;
        foundAllItemsInChest = false;
        foundAllItemsInInventory = false;
    }

    // Registry names in this Forge/MCP version come back as plain "minecraft:iron_ingot"-style
    // strings (not ResourceLocation objects) - matches ravens4.script.model.ItemStack's own
    // "substring(10)" convention exactly, so mirroring that here rather than inventing a new one.
    private boolean matchesTracked(ItemStack item, HashSet<String> tracked) {
        if (item == null || item.getItem() == null) return false;
        String registryName = item.getItem().getRegistryName();
        if (registryName == null || registryName.length() < 10) return false;
        return tracked.contains(registryName.substring(10));
    }

    private void windowClick(int slot, int button, int mode) {
        mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, slot, button, mode, mc.thePlayer);
    }

    /**
     * True for regular chests, large chests and ender chests on any server. Servers send the vanilla window title as a
     * translation key (container.chest / container.chestDouble / container.enderchest), which also works when the game
     * is not in English; custom-named chests are accepted if their name ends with "Chest". Other GUIs (shops, menus)
     * are ignored so nothing gets shift-clicked in them by accident.
     */
    private boolean isChestTitle(IChatComponent title) {
        if (title == null) return false;
        if (title instanceof ChatComponentTranslation) {
            String key = ((ChatComponentTranslation) title).getKey();
            if (key != null && key.startsWith("container.") && key.toLowerCase().contains("chest")) return true;
        }
        return title.getUnformattedText().endsWith("Chest");
    }
}
