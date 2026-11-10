package ravens4.module.impl.player;

import ravens4.event.PrePlayerInteractEvent;
import ravens4.event.PreSlotScrollEvent;
import ravens4.event.SlotUpdateEvent;
import ravens4.mixin.impl.accessor.IAccessorPlayerControllerMP;
import ravens4.mixin.interfaces.IMixinItemRenderer;
import ravens4.module.Module;
import ravens4.module.setting.impl.BlockListSetting;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.GroupSetting;
import ravens4.module.setting.impl.ItemListSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.BlockUtils;
import ravens4.utility.ItemSortScoring;
import ravens4.utility.RotationUtils;
import ravens4.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

public class AutoTool extends Module {
    private final GroupSetting timingGroup;
    private final SliderSetting activationTime;
    private final SliderSetting hoverDelay;

    private final ButtonSetting ignoredHeldItemsToggle;
    private final ItemListSetting ignoredHeldItems;

    private final GroupSetting conditionsGroup;
    private final ButtonSetting onlyWhileCrouching;
    private final ButtonSetting requireLeftMouse;

    private final GroupSetting swapGroup;
    private final ButtonSetting switchBackWhenDone;
    private final ButtonSetting overrideSwapBack;
    public final ButtonSetting spoofItem;

    private final ButtonSetting autoSword;

    private final ButtonSetting blockWhitelistToggle;
    private final BlockListSetting blockWhitelist;
    private final ButtonSetting blockBlacklistToggle;
    private final BlockListSetting blockBlacklist;

    private boolean hasSwapped;
    public int previousSlot = -1;
    private int tickCounter;
    private int leftMouseDownSinceTick = -1;
    private int hoverStartTick = -1;

    public AutoTool() {
        super("Auto Tool", category.player);

        this.registerSetting(timingGroup = new GroupSetting("Timing"));
        this.registerSetting(activationTime = new SliderSetting(timingGroup, "Activation time", "ms", 0.0, 0.0, 1000.0, 25.0));
        this.registerSetting(hoverDelay = new SliderSetting(timingGroup, "Hover delay", "ms", 0.0, 0.0, 1000.0, 25.0));

        this.registerSetting(conditionsGroup = new GroupSetting("Conditions"));
        this.registerSetting(onlyWhileCrouching = new ButtonSetting(conditionsGroup, "Only while crouching", false));
        this.registerSetting(requireLeftMouse = new ButtonSetting(conditionsGroup, "Require Left mouse", true, "Require mouse down"));

        this.registerSetting(swapGroup = new GroupSetting("Swap"));
        this.registerSetting(switchBackWhenDone = new ButtonSetting(swapGroup, "Switch back when done", true, "Swap to previous slot"));
        this.registerSetting(overrideSwapBack = new ButtonSetting(swapGroup, "Override swap back", true));
        this.registerSetting(spoofItem = new ButtonSetting(swapGroup, "Spoof item", false));

        this.registerSetting(autoSword = new ButtonSetting("Auto sword", false));
        this.registerSetting(ignoredHeldItemsToggle = new ButtonSetting("Held item blacklist", false, "Ignore held items", "Restrict held items", "Allow while holding"));
        this.registerSetting(ignoredHeldItems = new ItemListSetting("Held items", "Items"));
        this.registerSetting(blockWhitelistToggle = new ButtonSetting("Block whitelist", false, "Restrict allowed blocks", "Blocks.Block whitelist"));
        this.registerSetting(blockWhitelist = new BlockListSetting("Whitelisted blocks", "Blocks", "Blocks.Whitelisted blocks"));
        this.registerSetting(blockBlacklistToggle = new ButtonSetting("Block blacklist", false, "Blocks.Block blacklist"));
        this.registerSetting(blockBlacklist = new BlockListSetting("Blacklisted blocks", "Block blacklist", "Blocks.Block blacklist", "Blocks.Blacklisted blocks"));
        this.closetModule = true;
    }

    @Override
    public void guiUpdate() {
        activationTime.setVisible(requireLeftMouse.isToggled(), this);
        ignoredHeldItems.setVisible(ignoredHeldItemsToggle.isToggled(), this);
        blockWhitelist.setVisible(blockWhitelistToggle.isToggled(), this);
        blockBlacklist.setVisible(blockBlacklistToggle.isToggled(), this);
    }

    @Override
    public void onEnable() {
        resetState(true);
    }

    @Override
    public void onDisable() {
        resetState(true);
    }

    @SubscribeEvent
    public void onScrollSlot(PreSlotScrollEvent e) {
        if (!hasSwapped) {
            return;
        }
        if (overrideSwapBack.isToggled()) {
            int slot = Integer.compare(e.slot, 0);
            previousSlot = Math.floorMod(mc.thePlayer.inventory.currentItem - slot, 9);
        }
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onSlotUpdate(SlotUpdateEvent e) {
        if (!hasSwapped) {
            return;
        }
        if (overrideSwapBack.isToggled()) {
            previousSlot = e.slot;
        }
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck()) {
            resetState(true);
            return;
        }

        if (spoofItem.isToggled() && previousSlot != mc.thePlayer.inventory.currentItem && previousSlot != -1) {
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(true);
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(true);
        }

        int currentTick = ++tickCounter;
        boolean leftMouseDown = Mouse.isButtonDown(0);
        updateLeftMouseState(leftMouseDown, currentTick);

        if (!mc.inGameHasFocus || mc.currentScreen != null || mc.thePlayer.isDead) {
            resetState(true);
            return;
        }

        // hitting a player: use the sword; this takes priority over the block tools below
        if (autoSword.isToggled() && updateAutoSword(leftMouseDown)) {
            return;
        }

        if (!mc.thePlayer.capabilities.allowEdit) {
            resetState(true);
            return;
        }

        MovingObjectPosition hoverResult = RotationUtils.rayTraceBlockIfNoEntityInFront(
            mc.playerController.getBlockReachDistance(),
            mc.thePlayer.rotationYaw,
            mc.thePlayer.rotationPitch
        );
        BlockPos hoverPos = hoverResult != null
            && hoverResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            ? hoverResult.getBlockPos()
            : null;
        updateHoverState(hoverPos, currentTick);

        if (hoverPos == null) {
            resetSlot();
            return;
        }

        if (onlyWhileCrouching.isToggled() && !mc.thePlayer.isSneaking()) {
            resetSlot();
            return;
        }

        if (requireLeftMouse.isToggled()) {
            if (!leftMouseDown) {
                resetSlot();
                return;
            }
            if (!hasElapsed(leftMouseDownSinceTick, activationTime.getInput(), currentTick)) {
                resetSlot();
                return;
            }
        }

        if (!hasElapsed(hoverStartTick, hoverDelay.getInput(), currentTick)) {
            resetSlot();
            return;
        }

        if (isUseBlocked()) {
            resetSlot();
            return;
        }

        if (isBlockedBlock(hoverPos)) {
            resetSlot();
            return;
        }

        if (blockWhitelistToggle.isToggled() && !isWhitelistedBlock(hoverPos)) {
            resetSlot();
            return;
        }

        MovingObjectPosition swapResult = mc.objectMouseOver;
        BlockPos swapPos = swapResult != null
            && swapResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            ? swapResult.getBlockPos()
            : null;
        if (swapPos == null) {
            resetSlot();
            return;
        }

        int slot = Utils.getTool(BlockUtils.getBlock(swapPos));
        if (slot == -1) {
            return;
        }

        if (previousSlot == -1 && slot != mc.thePlayer.inventory.currentItem) {
            previousSlot = mc.thePlayer.inventory.currentItem;
        }

        if (!hasSwapped) {
            setSlot(slot);
            return;
        }

        if (slot != mc.thePlayer.inventory.currentItem) {
            setSlot(slot);
        }
    }

    /**
     * Switches to the best sword in the hotbar while the left mouse button is held on a player.
     * Any ItemSword counts (wooden to diamond and modded swords) and swords are ranked by their real attack damage
     * and enchantments, not by material. The usual "Switch back when done" handling restores the previous slot.
     *
     * @return true while a player is being hit (so the block tool logic is skipped)
     */
    private boolean updateAutoSword(boolean leftMouseDown) {
        if (!leftMouseDown) {
            return false;
        }

        MovingObjectPosition target = mc.objectMouseOver;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || !(target.entityHit instanceof EntityPlayer)) {
            return false;
        }

        if (ignoredHeldItemsToggle.isToggled() && ignoredHeldItems.matches(mc.thePlayer.getHeldItem())) {
            return false;
        }

        int swordSlot = findBestSwordSlot();
        if (swordSlot == -1) {
            return false;
        }

        int currentSlot = mc.thePlayer.inventory.currentItem;
        if (swordSlot != currentSlot) {
            if (previousSlot == -1) {
                previousSlot = currentSlot;
            }
            setSlot(swordSlot);
        }
        return true;
    }

    /** @return the hotbar slot to hit with: the best sword, or the held slot if it already holds one that is as good */
    private int findBestSwordSlot() {
        int bestSlot = -1;
        double bestScore = -Double.MAX_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemSword)) {
                continue;
            }
            double score = ItemSortScoring.getMeleeSelectionScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        if (bestSlot == -1) {
            return -1;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemSword && ItemSortScoring.getMeleeSelectionScore(held) >= bestScore) {
            return mc.thePlayer.inventory.currentItem;
        }
        return bestSlot;
    }

    private void updateLeftMouseState(boolean leftMouseDown, int currentTick) {
        if (leftMouseDown) {
            if (leftMouseDownSinceTick == -1) {
                leftMouseDownSinceTick = currentTick;
            }
        }
        else {
            leftMouseDownSinceTick = -1;
        }
    }

    private void updateHoverState(BlockPos hoverPos, int currentTick) {
        if (hoverPos == null) {
            hoverStartTick = -1;
            return;
        }

        if (hoverStartTick == -1) {
            hoverStartTick = currentTick;
        }
    }

    private boolean isUseBlocked() {
        boolean useActive = Utils.isBindDown(mc.gameSettings.keyBindUseItem) || mc.thePlayer.isUsingItem();
        if (ignoredHeldItemsToggle.isToggled() && ignoredHeldItems.matches(mc.thePlayer.getHeldItem())) {
            return true;
        }
        return useActive;
    }

    private boolean isBlockedBlock(BlockPos blockPos) {
        if (!blockBlacklistToggle.isToggled()) {
            return false;
        }
        return matchesBlockList(blockPos, blockBlacklist);
    }

    private boolean isWhitelistedBlock(BlockPos blockPos) {
        if (blockWhitelist.getBlocks().isEmpty()) {
            return false;
        }
        return matchesBlockList(blockPos, blockWhitelist);
    }

    private boolean matchesBlockList(BlockPos blockPos, BlockListSetting blockList) {
        IBlockState state = BlockUtils.getBlockState(blockPos);
        Block hoveredBlock = state.getBlock();
        if (hoveredBlock == null || Block.blockRegistry.getNameForObject(hoveredBlock) == null) {
            return false;
        }

        String registryId = Block.blockRegistry.getNameForObject(hoveredBlock).toString();
        int meta = hoveredBlock.getMetaFromState(state);
        String storageId = meta != 0 ? registryId + ":" + meta : registryId;
        return blockList.contains(storageId) || blockList.contains(registryId);
    }

    private boolean hasElapsed(int startTick, double requiredMs, int currentTick) {
        int requiredTicks = getRequiredTicks(requiredMs);
        if (requiredTicks <= 0) {
            return true;
        }
        return startTick != -1 && currentTick - startTick >= requiredTicks;
    }

    private int getRequiredTicks(double requiredMs) {
        if (requiredMs <= 0.0) {
            return 0;
        }
        return (int) Math.ceil(requiredMs / 50.0);
    }

    private void resetState(boolean resetTimers) {
        if (resetTimers) {
            tickCounter = 0;
            leftMouseDownSinceTick = -1;
            hoverStartTick = -1;
        }
        resetSlot();
    }

    private void resetSlot() {
        if (previousSlot != -1 && switchBackWhenDone.isToggled()) {
            setSlot(previousSlot);
        }
        previousSlot = -1;
        hasSwapped = false;
    }

    private void setSlot(int currentItem) {
        if (currentItem == -1 || currentItem == mc.thePlayer.inventory.currentItem) {
            return;
        }
        mc.thePlayer.inventory.currentItem = currentItem;
        hasSwapped = true;
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }
}
