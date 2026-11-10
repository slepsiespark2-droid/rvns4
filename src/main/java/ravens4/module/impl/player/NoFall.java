package ravens4.module.impl.player;

import ravens4.Raven;
import ravens4.event.ClientRotationEvent;
import ravens4.event.PreMotionEvent;
import ravens4.event.PreUpdateEvent;
import ravens4.helper.RotationHelper;
import ravens4.mixin.impl.accessor.IAccessorMinecraft;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.GroupSetting;
import ravens4.module.setting.impl.KeySetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.BlockUtils;
import ravens4.utility.RenderUtils;
import ravens4.utility.RotationUtils;
import ravens4.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.Color;
import java.util.Random;

public class NoFall extends Module {
    private static final int MODE_SPOOF = 0;
    private static final int MODE_NOGROUND = 1;
    private static final int MODE_PACKET_A = 2;
    private static final int MODE_PACKET_B = 3;
    private static final int MODE_WATER_BUCKET = 4;
    private static final int MODE_LADDER_CLUTCH = 5;

    private static final String[] MODES = new String[]{"Spoof", "NoGround", "Packet A", "Packet B", "Water Bucket", "Ladder Clutch"};
    private static final String[] LADDER_STAGE_NAMES = new String[]{"Searching", "Base", "Ladder", "Done"};

    // Vanilla only deals fall damage past 3 blocks of fall distance.
    private static final double NO_DAMAGE_FALL_BLOCKS = 3.0;
    private static final double MIN_DETECTION_RANGE = 6.0;
    private static final double MAX_DETECTION_RANGE = 64.0;
    private static final double REACTION_TICKS = 12.0;

    private static final long WATER_PLACE_DELAY = 500L;
    private static final long WATER_PICKUP_WAIT = 150L;

    public SliderSetting mode;
    private SliderSetting minFallDistance;
    private ButtonSetting disableAdventure;
    private ButtonSetting ignoreVoid;

    private GroupSetting waterBucketGroup;
    private ButtonSetting waterPickupWater;
    private ButtonSetting waterSilentAim;
    private ButtonSetting waterSwitchToItem;

    private GroupSetting ladderClutchGroup;
    private ButtonSetting ladderDisableAdventure;
    private ButtonSetting ladderIgnoreVoid;
    private ButtonSetting ladderWorkInCreative;
    private SliderSetting ladderCpsMin;
    private SliderSetting ladderCpsMax;
    private SliderSetting ladderRotationSpeed;
    private SliderSetting ladderRotationRandomness;
    private ButtonSetting ladderHighlights;
    private KeySetting ladderSelectKeybind;

    private double initialY;
    private double dynamic;
    private boolean isFalling;

    private long waterLastPlace = 0L;
    private boolean waterShouldPickup = false;
    private int waterLastSlot = -1;

    // 0 = idle/searching, 1 = placing anchor block, 2 = placing ladder, 3 = done for this fall
    private int ladderStage;
    private BlockPos ladderSupportPos;
    private BlockPos ladderAnchorPos;
    private EnumFacing ladderAnchorDir;

    private boolean ladderPlaceQueued;
    private BlockPos ladderQueuedPos;
    private EnumFacing ladderQueuedFace;
    private Vec3 ladderQueuedHit;

    private long ladderNextClickTime;
    private int ladderPrevSlot = -1;
    private boolean ladderSlotSwapped;
    private boolean ladderHeldByKeybind;
    private final Random ladderRandom = new Random();

    public NoFall() {
        super("NoFall", category.player);
        this.registerSetting(mode = new SliderSetting("Mode", MODE_PACKET_A, MODES));
        this.registerSetting(minFallDistance = new SliderSetting("Minimum fall distance", 3, 0, 10, 0.1));
        this.registerSetting(disableAdventure = new ButtonSetting("Disable adventure", false));
        this.registerSetting(ignoreVoid = new ButtonSetting("Ignore void", true));

        this.registerSetting(waterBucketGroup = new GroupSetting("Water Bucket"));
        this.registerSetting(waterPickupWater = new ButtonSetting(waterBucketGroup, "Pickup water", true));
        this.registerSetting(waterSilentAim = new ButtonSetting(waterBucketGroup, "Silent aim", true));
        this.registerSetting(waterSwitchToItem = new ButtonSetting(waterBucketGroup, "Switch to item", true));

        this.registerSetting(ladderClutchGroup = new GroupSetting("Ladder Clutch"));
        this.registerSetting(ladderDisableAdventure = new ButtonSetting(ladderClutchGroup, "Disable adventure", false));
        this.registerSetting(ladderIgnoreVoid = new ButtonSetting(ladderClutchGroup, "Ignore void", true));
        this.registerSetting(ladderWorkInCreative = new ButtonSetting(ladderClutchGroup, "Work in creative", true));
        this.registerSetting(ladderCpsMin = new SliderSetting(ladderClutchGroup, "CPS min", 14, 1, 50, 1));
        this.registerSetting(ladderCpsMax = new SliderSetting(ladderClutchGroup, "CPS max", 22, 1, 50, 1));
        this.registerSetting(ladderRotationSpeed = new SliderSetting(ladderClutchGroup, "Rotation speed", 10, 1, 30, 1));
        this.registerSetting(ladderRotationRandomness = new SliderSetting(ladderClutchGroup, "Rotation randomness", "%", 25, 0, 100, 1));
        this.registerSetting(ladderHighlights = new ButtonSetting(ladderClutchGroup, "Highlights", true));
        this.registerSetting(ladderSelectKeybind = new KeySetting(ladderClutchGroup, "Select Keybind", 0));

        updateSettingVisibility(false);
    }

    @Override
    public void onKeyBind() {
        super.onKeyBind();

        if (!isLadderClutchMode() || ladderSelectKeybind.getKey() == 0) {
            return;
        }

        boolean pressed = ladderSelectKeybind.isPressed();
        if (pressed && !isEnabled()) {
            ladderHeldByKeybind = true;
            enable();
        }
        else if (!pressed && ladderHeldByKeybind) {
            ladderHeldByKeybind = false;
            disable();
        }
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            initialY = mc.thePlayer.posY;
        }
        isFalling = false;
        resetWaterState();
        resetLadderState(false);
    }

    @Override
    public void onDisable() {
        Utils.resetTimer();
        resetWaterState();
        ladderHeldByKeybind = false;
        resetLadderState(true);
    }

    @Override
    public void guiUpdate() {
        updateSettingVisibility(true);
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        switch (currentMode()) {
            case MODE_WATER_BUCKET:
                Utils.resetTimer();
                onWaterPreUpdate();
                break;
            case MODE_LADDER_CLUTCH:
                Utils.resetTimer();
                onLadderPreUpdate();
                break;
            default:
                onNoFallPreUpdate();
                break;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPreMotion(PreMotionEvent e) {
        switch (currentMode()) {
            case MODE_SPOOF:
                e.setOnGround(true);
                break;
            case MODE_NOGROUND:
                e.setOnGround(false);
                break;
        }
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (isWaterBucketMode()) {
            onWaterClientRotation(e);
        }
        else if (isLadderClutchMode()) {
            onLadderClientRotation(e);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (isWaterBucketMode()) {
            onWaterRenderWorld(e);
        }
        else if (isLadderClutchMode()) {
            onLadderRenderWorld(e);
        }
    }

    @Override
    public String getInfo() {
        if (isLadderClutchMode()) {
            return MODES[MODE_LADDER_CLUTCH] + " " + LADDER_STAGE_NAMES[MathHelper.clamp_int(ladderStage, 0, LADDER_STAGE_NAMES.length - 1)];
        }
        return MODES[currentMode()];
    }

    private void onNoFallPreUpdate() {
        if (nofallReset()) {
            Utils.resetTimer();
            initialY = mc.thePlayer.posY;
            isFalling = false;
            return;
        }
        else if ((double) mc.thePlayer.fallDistance >= minFallDistance.getInput()) {
            isFalling = true;
        }
        double predictedY = mc.thePlayer.posY + mc.thePlayer.motionY;
        double distanceFallen = initialY - predictedY;
        if (mc.thePlayer.motionY >= -1.0) {
            dynamic = 3.0;
        }
        if (mc.thePlayer.motionY < -1.0) {
            dynamic = 4.0;
        }
        if (mc.thePlayer.motionY < -2.0) {
            dynamic = 5.0;
        }
        if (isFalling && currentMode() == MODE_PACKET_A) {
            if (distanceFallen >= dynamic) {
                ((IAccessorMinecraft) mc).getTimer().timerSpeed = (0.7399789F + (float) Utils.randomizeDouble(-0.012, 0.012));
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer(true));
                initialY = mc.thePlayer.posY;
            }
        }
        if (isFalling && currentMode() == MODE_PACKET_B) {
            if (mc.thePlayer.ticksExisted % 2 == 0) {
                ((IAccessorMinecraft) mc).getTimer().timerSpeed = (float) Utils.randomizeDouble(0.5, 0.50201);
            }
            else {
                ((IAccessorMinecraft) mc).getTimer().timerSpeed = (float) 1;
            }
            if (distanceFallen >= 3) {
                mc.getNetHandler().addToSendQueue(new C03PacketPlayer(true));
                initialY = mc.thePlayer.posY;
            }
        }
    }

    private void onWaterRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || mc.isGamePaused() || mc.thePlayer.capabilities.isFlying || mc.thePlayer.capabilities.isCreativeMode) {
            return;
        }
        if (!waterFallCheck()) {
            return;
        }
        MovingObjectPosition mop = Utils.getTarget(mc.playerController.getBlockReachDistance(), mc.thePlayer.rotationYaw, waterSilentAim.isToggled() ? 90.0f : mc.thePlayer.rotationPitch);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mop.sideHit != EnumFacing.UP) {
            return;
        }
        long now = System.currentTimeMillis();
        if (Utils.timeBetween(waterLastPlace, now) < WATER_PLACE_DELAY) {
            return;
        }
        if (!isItem(mc.thePlayer.getHeldItem(), Items.water_bucket) && waterSwitchToItem.isToggled()) {
            attemptWaterSwitch();
        }
        if (!waterSilentAim.isToggled() && mc.thePlayer.rotationPitch < 80.0f) {
            return;
        }
        waterLastPlace = now;
        useCurrentItem();
        if (!(waterShouldPickup = waterPickupWater.isToggled())) {
            waterLastSlot = -1;
        }
        if (Raven.DEBUG) {
            Utils.sendModuleMessage(this, "&7Placed with motionY &d" + Utils.round(mc.thePlayer.motionY, 2) + " &7and fall distance &d" + Utils.round(mc.thePlayer.fallDistance, 2));
        }
    }

    private void onWaterPreUpdate() {
        if (mc.isGamePaused()) {
            return;
        }
        if (waterShouldPickup && Utils.timeBetween(waterLastPlace, System.currentTimeMillis()) > WATER_PICKUP_WAIT && isItem(mc.thePlayer.getHeldItem(), Items.bucket)) {
            waterShouldPickup = false;
            useCurrentItem();
            if (waterLastSlot != -1) {
                Utils.switchSlot(waterLastSlot, true);
                waterLastSlot = -1;
            }
        }
    }

    private void onWaterClientRotation(ClientRotationEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }
        if (waterSilentAim.isToggled() && (waterFallCheck() || Utils.timeBetween(waterLastPlace, System.currentTimeMillis()) < WATER_PLACE_DELAY) && getWaterBucketSlot() != -1) {
            e.setYaw(mc.thePlayer.rotationYaw);
            e.setPitch(90.0f);
        }
    }

    private void attemptWaterSwitch() {
        int slot = getWaterBucketSlot();
        if (slot != -1) {
            waterLastSlot = mc.thePlayer.inventory.currentItem;
            Utils.switchSlot(slot, true);
        }
    }

    private int getWaterBucketSlot() {
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); ++slot) {
            if (isItem(mc.thePlayer.inventory.getStackInSlot(slot), Items.water_bucket)) {
                return slot;
            }
        }
        return -1;
    }

    private void useCurrentItem() {
        mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
    }

    private boolean isItem(ItemStack itemStack, Item item) {
        return itemStack != null && itemStack.getItem() == item;
    }

    private boolean waterFallCheck() {
        return !mc.thePlayer.onGround && mc.thePlayer.fallDistance >= 3.3;
    }

    private void resetWaterState() {
        waterLastPlace = 0L;
        waterShouldPickup = false;
        waterLastSlot = -1;
    }

    private void onLadderPreUpdate() {
        if (ladderReset()) {
            if (ladderStage != 0) {
                resetLadderState(true);
            }
            return;
        }

        if (ladderPlaceQueued) {
            executeQueuedLadderPlacement();
        }

        updateLadderClutch();
    }

    private void onLadderClientRotation(ClientRotationEvent e) {
        if (ladderStage == 0 || ladderStage >= 3) {
            return;
        }
        if (!Utils.nullCheck() || mc.currentScreen != null) {
            return;
        }
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }

        if (ladderSupportPos == null || ladderAnchorDir == null) {
            return;
        }

        BlockPos targetPos = ladderStage == 1 ? ladderSupportPos.offset(ladderAnchorDir) : ladderAnchorPos;
        EnumFacing targetFace = ladderStage == 1 ? EnumFacing.UP : ladderAnchorDir.getOpposite();
        if (targetPos == null || targetFace == null) {
            return;
        }

        equipForLadderStage();

        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];

        float[] target = RotationUtils.getRotationsToBlock(targetPos, targetFace, baseYaw, basePitch);
        float[] smoothed = RotationUtils.smoothRotation(baseYaw, basePitch, target[0], target[1],
                (int) ladderRotationSpeed.getInput(), (float) ladderRotationRandomness.getInput());

        RotationHelper.get().forceMovementFix = true;
        e.setYaw(smoothed[0]);
        e.setPitch(smoothed[1]);

        boolean aligned = Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - target[0])) < 2.0f
                && Math.abs(smoothed[1] - target[1]) < 2.0f;

        long now = System.currentTimeMillis();
        if (aligned && !ladderPlaceQueued && now >= ladderNextClickTime) {
            MovingObjectPosition mop = RotationUtils.rayCastBlock(mc.playerController.getBlockReachDistance(), smoothed[0], smoothed[1]);
            if (mop != null && targetPos.equals(mop.getBlockPos()) && mop.sideHit == targetFace) {
                ladderQueuedPos = mop.getBlockPos();
                ladderQueuedFace = mop.sideHit;
                ladderQueuedHit = mop.hitVec;
                ladderPlaceQueued = true;
            }
        }
    }

    private void onLadderRenderWorld(RenderWorldLastEvent e) {
        if (!ladderHighlights.isToggled() || !Utils.nullCheck() || ladderStage == 0 || ladderStage >= 3) {
            return;
        }

        if (ladderStage == 1 && ladderSupportPos != null && ladderAnchorDir != null) {
            RenderUtils.renderBlock(ladderSupportPos.offset(ladderAnchorDir), Color.yellow.getRGB(), true, false);
            RenderUtils.renderBlock(ladderAnchorPos, Color.orange.getRGB(), true, false);
        } else if (ladderStage == 2 && ladderSupportPos != null && ladderAnchorPos != null) {
            RenderUtils.renderBlock(ladderAnchorPos, Color.orange.getRGB(), true, false);
            RenderUtils.renderBlock(ladderSupportPos.up(), Color.cyan.getRGB(), true, false);
        }
    }

    private void updateLadderClutch() {
        if (mc.currentScreen != null || (!ladderWorkInCreative.isToggled() && mc.thePlayer.capabilities.isCreativeMode)) {
            return;
        }

        switch (ladderStage) {
            case 0: {
                double speed = Math.abs(mc.thePlayer.motionY);
                double dynamicRange = MathHelper.clamp_double(speed * REACTION_TICKS, MIN_DETECTION_RANGE, MAX_DETECTION_RANGE);

                MovingObjectPosition mop = Utils.getTarget(dynamicRange, mc.thePlayer.rotationYaw, 90.0f);
                if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mop.sideHit != EnumFacing.UP) {
                    return;
                }

                BlockPos support = mop.getBlockPos();

                double predictedDrop = mc.thePlayer.posY - (support.getY() + 1);
                if (predictedDrop <= NO_DAMAGE_FALL_BLOCKS) {
                    ladderStage = 3;
                    return;
                }

                if (!BlockUtils.replaceable(support.up())) {
                    return;
                }

                EnumFacing dir = pickLadderAnchorDirection(support);
                if (dir == null) {
                    return;
                }

                ladderSupportPos = support;
                ladderAnchorDir = dir;
                ladderAnchorPos = support.offset(dir).up();
                ladderStage = 1;
                break;
            }
            default:
                break;
        }
    }

    private void executeQueuedLadderPlacement() {
        ladderPlaceQueued = false;
        if (ladderQueuedPos == null || ladderQueuedFace == null || ladderQueuedHit == null) {
            return;
        }

        boolean isBaseStage = ladderStage == 1;
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean holdingCorrectItem = isBaseStage ? isPlaceableBlock(held) : isLadderItem(held);
        if (!holdingCorrectItem) {
            return;
        }

        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, ladderQueuedPos, ladderQueuedFace, ladderQueuedHit)) {
            mc.thePlayer.swingItem();
            ladderNextClickTime = System.currentTimeMillis() + nextLadderClickDelay();
            ladderStage = isBaseStage ? 2 : 3;
            if (!isBaseStage) {
                restoreLadderSlotIfSwapped();
            }
        }
    }

    private void equipForLadderStage() {
        boolean isBaseStage = ladderStage == 1;
        int current = mc.thePlayer.inventory.currentItem;
        ItemStack currentStack = mc.thePlayer.inventory.mainInventory[current];
        if (isBaseStage ? isPlaceableBlock(currentStack) : isLadderItem(currentStack)) {
            return;
        }

        int slot = isBaseStage ? findBlockSlot() : findLadderSlot();
        if (slot == -1) {
            return;
        }
        if (!ladderSlotSwapped) {
            ladderPrevSlot = current;
            ladderSlotSwapped = true;
        }
        mc.thePlayer.inventory.currentItem = slot;
    }

    private void restoreLadderSlotIfSwapped() {
        if (ladderSlotSwapped && ladderPrevSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = ladderPrevSlot;
        }
        ladderSlotSwapped = false;
        ladderPrevSlot = -1;
    }

    private void resetLadderState(boolean restoreSlot) {
        ladderStage = 0;
        ladderSupportPos = null;
        ladderAnchorPos = null;
        ladderAnchorDir = null;
        ladderPlaceQueued = false;
        ladderQueuedPos = null;
        ladderQueuedFace = null;
        ladderQueuedHit = null;
        if (restoreSlot) {
            restoreLadderSlotIfSwapped();
        } else {
            ladderSlotSwapped = false;
            ladderPrevSlot = -1;
        }
    }

    private EnumFacing pickLadderAnchorDirection(BlockPos support) {
        double dx = mc.thePlayer.posX - (support.getX() + 0.5);
        double dz = mc.thePlayer.posZ - (support.getZ() + 0.5);

        EnumFacing[] candidates;
        if (Math.abs(dx) > Math.abs(dz)) {
            candidates = dx > 0
                    ? new EnumFacing[]{EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.NORTH}
                    : new EnumFacing[]{EnumFacing.WEST, EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH};
        } else {
            candidates = dz > 0
                    ? new EnumFacing[]{EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.WEST}
                    : new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
        }

        for (EnumFacing candidate : candidates) {
            if (!BlockUtils.replaceable(support.offset(candidate)) && BlockUtils.replaceable(support.offset(candidate).up())) {
                return candidate;
            }
        }
        return null;
    }

    private long nextLadderClickDelay() {
        int lo = Math.max(1, Math.min(50, (int) Math.min(ladderCpsMin.getInput(), ladderCpsMax.getInput())));
        int hi = Math.max(1, Math.min(50, (int) Math.max(ladderCpsMin.getInput(), ladderCpsMax.getInput())));
        int cps = lo == hi ? lo : lo + ladderRandom.nextInt(hi - lo + 1);

        int baseDelay = 1000 / cps;
        int jitter = ladderRandom.nextInt(baseDelay + 1) - baseDelay / 2;
        int delay = baseDelay + jitter;
        return Math.max(20, Math.min(1000, delay));
    }

    private int findBlockSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (isPlaceableBlock(mc.thePlayer.inventory.mainInventory[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private int findLadderSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (isLadderItem(mc.thePlayer.inventory.mainInventory[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isPlaceableBlock(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return block != Blocks.ladder && block.getMaterial().isSolid();
    }

    private boolean isLadderItem(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() == Item.getItemFromBlock(Blocks.ladder);
    }

    private boolean isVoid() {
        return Utils.overVoid(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    }

    private boolean nofallReset() {
        if (disableAdventure.isToggled() && mc.playerController.getCurrentGameType().isAdventure()) {
            return true;
        }
        if (ignoreVoid.isToggled() && isVoid()) {
            return true;
        }
        if (Utils.isBedwarsPracticeOrReplay()) {
            return true;
        }
        if (Utils.spectatorCheck()) {
            return true;
        }
        if (mc.thePlayer.onGround) {
            return true;
        }
        if (BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ)) != Blocks.air) {
            return true;
        }
        if (mc.thePlayer.motionY > -0.0784) {
            return true;
        }
        if (mc.thePlayer.capabilities.isCreativeMode) {
            return true;
        }
        if (isVoid() && mc.thePlayer.posY <= 41) {
            return true;
        }
        if (mc.thePlayer.capabilities.isFlying) {
            return true;
        }
        return false;
    }

    private boolean ladderReset() {
        if (ladderDisableAdventure.isToggled() && mc.playerController.getCurrentGameType().isAdventure()) {
            return true;
        }
        if (ladderIgnoreVoid.isToggled() && isVoid()) {
            return true;
        }
        if (Utils.isBedwarsPracticeOrReplay()) {
            return true;
        }
        if (Utils.spectatorCheck()) {
            return true;
        }
        if (mc.thePlayer.onGround) {
            return true;
        }
        if (BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ)) != Blocks.air) {
            return true;
        }
        if (mc.thePlayer.motionY > -0.0784) {
            return true;
        }
        if (!ladderWorkInCreative.isToggled() && mc.thePlayer.capabilities.isCreativeMode) {
            return true;
        }
        if (isVoid() && mc.thePlayer.posY <= 41) {
            return true;
        }
        if (mc.thePlayer.capabilities.isFlying) {
            return true;
        }
        return false;
    }

    private int currentMode() {
        return (int) mode.getInput();
    }

    private boolean isWaterBucketMode() {
        return currentMode() == MODE_WATER_BUCKET;
    }

    private boolean isLadderClutchMode() {
        return currentMode() == MODE_LADDER_CLUTCH;
    }

    private void updateSettingVisibility(boolean notifyGui) {
        int currentMode = currentMode();
        boolean regularNoFall = currentMode <= MODE_PACKET_B;
        boolean waterBucket = currentMode == MODE_WATER_BUCKET;
        boolean ladderClutch = currentMode == MODE_LADDER_CLUTCH;

        setVisible(minFallDistance, regularNoFall, notifyGui);
        setVisible(disableAdventure, regularNoFall, notifyGui);
        setVisible(ignoreVoid, regularNoFall, notifyGui);

        setVisible(waterBucketGroup, waterBucket, notifyGui);
        setVisible(waterPickupWater, waterBucket, notifyGui);
        setVisible(waterSilentAim, waterBucket, notifyGui);
        setVisible(waterSwitchToItem, waterBucket, notifyGui);

        setVisible(ladderClutchGroup, ladderClutch, notifyGui);
        setVisible(ladderDisableAdventure, ladderClutch, notifyGui);
        setVisible(ladderIgnoreVoid, ladderClutch, notifyGui);
        setVisible(ladderWorkInCreative, ladderClutch, notifyGui);
        setVisible(ladderCpsMin, ladderClutch, notifyGui);
        setVisible(ladderCpsMax, ladderClutch, notifyGui);
        setVisible(ladderRotationSpeed, ladderClutch, notifyGui);
        setVisible(ladderRotationRandomness, ladderClutch, notifyGui);
        setVisible(ladderHighlights, ladderClutch, notifyGui);
        setVisible(ladderSelectKeybind, ladderClutch, notifyGui);

        if (!waterBucket) {
            resetWaterState();
        }
        if (!ladderClutch) {
            resetLadderState(true);
        }
    }

    private void setVisible(ravens4.module.setting.Setting setting, boolean visible, boolean notifyGui) {
        if (notifyGui) {
            setting.setVisible(visible, this);
        } else {
            setting.visible = visible;
        }
    }
}
