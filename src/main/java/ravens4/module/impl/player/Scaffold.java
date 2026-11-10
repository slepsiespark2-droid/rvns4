package ravens4.module.impl.player;

// Converted from the "Telly" script (by NeverBeBanned, open source to everyone) into a
// native module.
// PASS 1: class shell, fields, settings, event wiring, pure-math helpers.
// PASS 2: the full activation/arming/auto-aim-to-edge state machine.
// PASS 3: the bridge-cycle execution engine (rotation smoothing, movement curves, straight-
// lane placement validation, the core onPostPlayerInput cycle driver, onPreMotion rotation
// override, packet interception). Also fixed a real bug from Pass 2's rewrite here: several
// Pass-1 pure-math helpers (clampFloat, posKey, offsetPos, etc.) had been silently dropped.
// PASS 4 (this pass): rendering - the activation hitbox (RenderWorldLastEvent, real GL state
// management) and the "Activate?" prompt text (drawn straight in onRenderTick, matching how
// this codebase's own HUD module does 2D text - confirmed against HUD.java rather than
// assumed). Both were fully computed by Pass 2's state machine but never actually visible
// until now.
// STILL TODO (PASS 5): the full generic AutoPlace candidate-resolution subsystem (silent-pitch
// placement via ray-cast candidate scoring across arbitrary geometry - not needed for Telly's
// own straight-lane case, which already works, but used for edge cases in the original), and
// manual-input override protection (blocking WASD/drop while the cycle is driving). That last
// one specifically needs more investigation before porting: the real KeyPressEvent/
// ClickMouseEvent classes in this codebase are much thinner than the script's onKey/onMouse
// facade (no press/release state, no button info) - the real mechanism for this is likely a
// mixin intercepting raw keyboard state rather than a Forge event, and I don't want to guess
// at that without checking how e.g. an existing movement-blocking module does it first.

import ravens4.event.*;
import ravens4.helper.RotationHelper;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.KeySetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.BlockUtils;
import ravens4.utility.Utils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import ravens4.utility.RotationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Scaffold extends Module {

    /** Scaffold modes. "Legit Telly" is the original Telly module, "Keep-y" is the former Keep-Y module. */
    private static final String[] MODES = new String[]{"Legit Telly", "Keep-y"};
    private static final int MODE_LEGIT_TELLY = 0;
    private static final int MODE_KEEP_Y = 1;

    private final SliderSetting mode;
    private final KeepYMode keepY;
    /** The mode whose logic is currently running (-1 while the module is off); event handlers route on this. */
    private int activeMode = -1;

    private final ButtonSetting autoSwap;
    private final ButtonSetting disableGuard;
    private final ButtonSetting showActivationBounds;
    private final ButtonSetting showActivatePrompt;
    private final ButtonSetting backfillGap;
    private final ButtonSetting flatStart;
    private final ButtonSetting silentAim;
    private final ButtonSetting debug;
    private final KeySetting speedTellyKey;
    private final SliderSetting rotationSpeed;
    private final SliderSetting wasdKeysPressingSpeed;

    public Scaffold() {
        super("Scaffold", category.player);
        this.registerSetting(mode = new SliderSetting("Mode", MODE_LEGIT_TELLY, MODES));
        this.registerSetting(autoSwap = new ButtonSetting("Auto swap", true));
        this.registerSetting(disableGuard = new ButtonSetting("Disable Guard", true));
        this.registerSetting(showActivationBounds = new ButtonSetting("Show activation bounds", false));
        this.registerSetting(showActivatePrompt = new ButtonSetting("Show \"Activate?\" text", true));
        this.registerSetting(backfillGap = new ButtonSetting("Backfill gap", false));
        this.registerSetting(flatStart = new ButtonSetting("Flat start", true));
        this.registerSetting(silentAim = new ButtonSetting("Silent aim", false));
        silentAim.visible = false;
        this.registerSetting(speedTellyKey = new KeySetting("Speed-Telly Key", 0));
        this.registerSetting(rotationSpeed = new SliderSetting("Rotation speed", 240.0, 1.0, 240.0, 1.0));
        this.registerSetting(wasdKeysPressingSpeed = new SliderSetting("wasd-keys pressing speed", 1000.0, 1.0, 1000.0, 1.0));
        this.registerSetting(debug = new ButtonSetting("Debug", false));
        debug.visible = false;
        // hidden from the GUI: they keep their defaults (240 / 1000) and still load from profiles
        rotationSpeed.visible = false;
        wasdKeysPressingSpeed.visible = false;

        this.keepY = new KeepYMode(this);
        this.keepY.hideSettings();
    }

    boolean armed = false;
    boolean running = false;
    long activatePromptAt = 0L;
    long promptBrokeAt = 0L;
    float promptAlpha = 0.0f;
    long promptFadeLastAt = 0L;
    int promptFadeRgb = 0xFF5555;
    int[] boundsLastPos = null;
    int boundsLastFace = -1;
    boolean activationMovementHeld = false;
    boolean antiSwayTapUsed = false;
    HashSet<String> cancelledGhostBlocks = new HashSet<String>();
    boolean tellyAutoPlaceWindow = false;
    boolean autoPlaceDebugActive = false;
    int runTopY = Integer.MIN_VALUE;
    int tellyLaneY = Integer.MIN_VALUE;
    int tellyFloorY = Integer.MIN_VALUE;
    List<int[]> backfillQueue = new ArrayList<>();
    List<Integer> backfillQueueTicks = new ArrayList<>();
    int[] backfillReadyPos = null;
    boolean backfillAttemptActive = false;
    int dbgPlaced = 0;
    int dbgRejStrict = 0;
    int dbgRejTarget = 0;
    int dbgBfTry = 0;
    int dbgBfHit = 0;
    int dbgCycle = 0;
    boolean guardStateCaptured = false;
    boolean guardWasEnabled = false;

    int setupTick = 0;
    int cyclePhase = 19;
    float baseYaw = 0.0f;
    int travelX = 0;
    int travelZ = 0;
    double antiSwayLane = 0.0;
    float antiSwayYawOffset = 0.0f;
    int bridgeLaneBlock = 0;
    int bridgeStartProgress = 0;
    int[] latestStraightPlacedPos = null;
    // Both belong to the full generic AutoPlace candidate system (PASS 4, not wired yet) -
    // stay null here, which makes updateAdaptivePlacementAim() correctly fall through to
    // latestStraightPlacedPos (which IS tracked) as its support reference.
    Object[] cachedCandidate = null;
    int[] lastPlacedPos = null;
    int[] lastSupportPos = null;
    int lastSupportFace = -1;
    int currentClientTick = Integer.MIN_VALUE;
    int placementEvaluationTick = Integer.MIN_VALUE;
    int lastPlacementAttemptTick = Integer.MIN_VALUE;
    int lastSuccessfulPlaceTick = Integer.MIN_VALUE;
    int forceSuppressTick = Integer.MIN_VALUE;
    long totalC08Counter = 0L;
    long c08CounterAtTickBoundary = 0L;
    boolean hasLastSentServerPos = false;
    double lastSentServerPosX, lastSentServerPosY, lastSentServerPosZ;
    int cachedCandidateTick = Integer.MIN_VALUE;
    float cachedCandidateYaw = Float.NaN;
    float cachedCandidatePitch = Float.NaN;
    boolean candidateResolvedThisTick = false;
    int forcedModeCheck = 0;
    boolean useSuppressed = false;
    boolean silentPitchActive = false;
    float silentPitch = 0f;
    boolean placingViaModule = false;
    boolean manualC08InWindow = false;
    HashMap<String, Integer> rejectedTargets = new HashMap<>();
    boolean firstTellyPlacementPending = false;
    boolean adaptiveAimValid = false;
    float adaptiveAimYaw = 0.0f;
    float adaptiveAimPitch = 0.0f;
    long adaptiveAimUpdatedAt = 0L;
    long takeoverDetectionAt = 0L;
    boolean takeoverCameraValid = false;
    float takeoverCameraYaw = 0.0f;
    float takeoverCameraPitch = 0.0f;
    float takeoverAccumulated = 0.0f;
    long takeoverLastFrameAt = 0L;
    long freezeLastTickAt = 0L;
    boolean ignoreForwardUntilRelease = false;
    boolean ignoreBackUntilRelease = false;
    boolean ignoreLeftUntilRelease = false;
    boolean ignoreRightUntilRelease = false;
    boolean ignoreJumpUntilRelease = false;
    boolean ignoreSneakUntilRelease = false;
    boolean ignoreSprintUntilRelease = false;
    double speedTellyPhaseAccumulator = 0.0;

    boolean rotationActive = false;
    long rotationStartedAt = 0L;
    long rotationDuration = 50L;
    float rotationStartYaw = 0.0f;
    float rotationStartPitch = 0.0f;
    float rotationTargetYaw = 0.0f;
    float rotationTargetPitch = 0.0f;
    float scriptedRotationYaw = 0.0f;
    float scriptedRotationPitch = 0.0f;

    final double SENSITIVITY_QUANTUM = 0.03404715;
    final int[] YAW_NUDGE_PATTERN = {0, 1, -1, 2, -2};
    int rotationStepCounter = 0;
    final double ACTIVATION_ACROSS_MIN = 0.38;
    final double ACTIVATION_ACROSS_MAX = 0.65;
    final double ACTIVATION_HEIGHT_MIN = 0.25;
    final double ACTIVATION_HEIGHT_MAX = 0.75;
    final float ACTIVATION_YAW_TOLERANCE = 2.0f;

    // Block-name tables, ported verbatim from the script (plain data, no facade calls).
    final String[] REPLACEABLE_BLOCKS = {"air", "water", "flowing_water", "lava", "flowing_lava", "fire", "tallgrass", "deadbush", "snow_layer", "double_plant", "vine"};
    final String[] EXPERIMENTAL_REPLACEABLE_BLOCKS = {
            "sapling", "yellow_flower", "red_flower", "brown_mushroom", "red_mushroom",
            "wheat", "carrots", "potatoes", "nether_wart", "reeds"
    };
    final String[] UNPLACEABLE_EXACT = {
            "snow_layer", "web", "sapling", "daylight_detector", "beacon", "banner",
            "end_portal_frame", "end_portal", "lever", "stone_button", "wooden_button",
            "skull", "cactus", "double_plant", "waterlily", "carpet", "tripwire_hook",
            "tallgrass", "yellow_flower", "red_flower", "flower_pot", "sign", "ladder",
            "torch", "redstone_torch", "unlit_redstone_torch", "gravel", "clay", "sand",
            "soul_sand", "chest", "trapped_chest", "ender_chest", "furnace", "lit_furnace",
            "jukebox", "enchanting_table", "dropper", "dispenser", "hopper", "anvil",
            "noteblock", "crafting_table", "mob_spawner", "brewing_stand", "bed"
    };
    final String[] UNPLACEABLE_CONTAINS = {
            "stairs", "slab", "fence", "pane", "rail", "door",
            "torch", "pumpkin", "flower", "sapling", "banner", "button",
            "skull", "web", "carpet", "cactus", "sign", "mushroom"
    };
    final String[] INTERACTABLE_TYPES = {
            "BlockTrapDoor", "BlockDoor", "BlockContainer", "BlockJukebox", "BlockFenceGate",
            "BlockChest", "BlockEnderChest", "BlockEnchantmentTable", "BlockBrewingStand",
            "BlockBed", "BlockDropper", "BlockDispenser", "BlockHopper", "BlockAnvil",
            "BlockNote", "BlockWorkbench", "BlockFurnace", "BlockBeacon", "BlockMobSpawner",
            "BlockDaylightDetector", "BlockCommandBlock", "BlockStandingSign", "BlockWallSign", "BlockSkull"
    };

    float[] yawCurve = new float[] {
            91.68f, 98.88f, 78.94f, 37.45f, 1.61f, -21.69f, -33.98f,
            -35.80f, -34.64f, -33.85f, -33.06f, -31.55f, -29.26f, -26.65f,
            -24.19f, -21.07f, -18.84f, -17.06f, -8.87f, 2.61f, 41.94f
    };

    float[] pitchCurve = new float[] {
            64.31f, 59.95f, 60.57f, 61.46f, 60.64f, 58.89f, 56.91f,
            56.63f, 58.65f, 61.63f, 64.20f, 66.74f, 68.69f, 70.64f,
            73.01f, 75.37f, 77.46f, 78.56f, 78.90f, 77.22f, 72.25f
    };

    float[] forwardCurve = new float[] {
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f
    };

    float[] strafeCurve = new float[] {
            -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, -1.0f, -1.0f, -1.0f, -1.0f
    };

    // ============================== lifecycle ==============================

    @Override
    public void onEnable() {
        startMode(selectedMode());
    }

    @Override
    public void onDisable() {
        stopActiveMode();
    }

    private void enableLegitTelly() {
        setKeyPressed(mc.gameSettings.keyBindAttack, false);
        resetControllerState();
        armAutomation();
    }

    private void disableLegitTelly() {
        stopAutomation(false);
        resetControllerState();
        restoreUseToPhysicalState();
        setKeyPressed(mc.gameSettings.keyBindAttack, false);
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (!this.isEnabled() || !isKeepY()) return;
        keepY.onClientRotation(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!this.isEnabled() || !isKeepY()) return;
        keepY.onMouse(e);
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (!this.isEnabled() || !isLegitTelly() || e.entity == null) return;
        if (e.entity == mc.thePlayer) {
            stopAutomation(false);
            resetControllerState();
        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!this.isEnabled()) return;
        syncMode();
        if (isKeepY()) {
            keepY.onPreUpdate();
            return;
        }
        enforceGuardDisabledForRun();

        if (running) {
            setKeyPressed(mc.gameSettings.keyBindAttack, false);
            applySmoothedRotation();
        }

        if (armed && !running) {
            updateActivationPrompt();
        }

        if (!running) return;

        long freezeNow = System.currentTimeMillis();
        if (freezeLastTickAt != 0L && freezeNow - freezeLastTickAt > 300L) {
            stopAutomation(true);
            return;
        }
        freezeLastTickAt = freezeNow;

        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.isDead || player.fallDistance > 7.0f) {
            stopAutomation(true);
            return;
        }
        handleAutoSwap(player);
        if (!(player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemBlock)) {
            stopAutomation(true);
            return;
        }
        if (firstTellyPlacementPending) updateAdaptivePlacementAim(player);

        autoPlaceOnPreUpdate();
        if (firstTellyPlacementPending) updateAdaptivePlacementAim(player);
    }

    void handleAutoSwap(EntityPlayerSP player) {
        if (!autoSwap.isToggled()) return;
        int threshold = 5;
        ItemStack held = player.getHeldItem();
        int heldCount = held != null && isUsableBlockStack(held) ? held.stackSize : 0;
        if (heldCount > threshold) return;

        int bestSlot = -1;
        int bestSize = heldCount;
        for (int slot = 0; slot <= 8; slot++) {
            if (slot == player.inventory.currentItem) continue;
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (!isUsableBlockStack(stack)) continue;
            if (stack.stackSize > bestSize) {
                bestSize = stack.stackSize;
                bestSlot = slot;
            }
        }
        if (bestSlot != -1) player.inventory.currentItem = bestSlot;
    }

    // ============================== activation prompt ==============================

    float activationPitch() {
        return 75.0f;
    }

    boolean activationPromptReady() {
        return activatePromptAt != 0L && System.currentTimeMillis() - activatePromptAt >= 400L;
    }

    boolean activationSuppressUse() {
        return activatePromptAt != 0L && System.currentTimeMillis() - activatePromptAt >= 250L;
    }

    void setKeyPressed(KeyBinding binding, boolean pressed) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
    }

    void updateActivationPrompt() {
        if (mc.thePlayer == null || mc.currentScreen != null) {
            clearActivationPrompt();
            return;
        }
        EntityPlayerSP player = mc.thePlayer;

        setActivationMovementHold(activationPromptReady() && Mouse.isButtonDown(1));

        boolean lookingDown = player.rotationPitch >= activationPitch();
        boolean atEdge = lookingDown && isLookingAtEdge();

        if (mc.thePlayer.movementInput.sneak && atEdge) {
            if (activatePromptAt == 0L) activatePromptAt = System.currentTimeMillis();
            promptBrokeAt = 0L;
            if (activationSuppressUse()) setKeyPressed(mc.gameSettings.keyBindUseItem, false);
            if (activationPromptReady() && Mouse.isButtonDown(1)) {
                disableGuardForRun();
                enforceGuardDisabledForRun();
            } else if (guardStateCaptured) {
                restoreGuardState();
            }
            return;
        }

        if (activatePromptAt == 0L) return;

        if (!activationPromptReady()) {
            clearActivationPrompt();
            return;
        }

        if (promptBrokeAt == 0L) {
            rememberActivationPromptColor();
            promptBrokeAt = System.currentTimeMillis();
        }
        setKeyPressed(mc.gameSettings.keyBindUseItem, false);

        if (!mc.thePlayer.movementInput.sneak && Mouse.isButtonDown(1) && isActivationYawAligned(player.rotationYaw)) {
            rememberActivationPromptColor();
            activatePromptAt = 0L;
            promptBrokeAt = 0L;
            beginAutomation();
            if (!running) setKeyPressed(mc.gameSettings.keyBindUseItem, false);
            return;
        }

        if (System.currentTimeMillis() - promptBrokeAt > 300L) {
            clearActivationPrompt();
        }
    }

    void clearActivationPrompt() {
        rememberActivationPromptColor();
        if (activationSuppressUse()) {
            setKeyPressed(mc.gameSettings.keyBindUseItem, false);
        }
        activatePromptAt = 0L;
        promptBrokeAt = 0L;
        setActivationMovementHold(false);
        if (!running) restoreGuardState();
    }

    void rememberActivationPromptColor() {
        if (activatePromptAt != 0L) {
            promptFadeRgb = activationPromptReady() ? 0x55FF55 : 0xFF5555;
        }
    }

    void setActivationMovementHold(boolean hold) {
        if (hold) {
            activationMovementHeld = true;
            setKeyPressed(mc.gameSettings.keyBindBack, true);
            setKeyPressed(mc.gameSettings.keyBindRight, true);
            return;
        }
        if (!activationMovementHeld) return;
        activationMovementHeld = false;
        setKeyPressed(mc.gameSettings.keyBindBack, org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
        setKeyPressed(mc.gameSettings.keyBindRight, org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()));
    }

    // Returns the first failing activation condition, or null if all pass (prompt should show).
    String activationFailReason() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return null;
        if (mc.currentScreen != null) return "close menus";
        if (!isHoldingBlock(player)) return "hold a block";
        if (!isUsableBlockStack(player.getHeldItem())) return "block not placeable";
        if (player.rotationPitch < activationPitch()) return "look steeper down (pitch " + Utils.round(player.rotationPitch, 0) + " < 75)";
        if (!isActivationYawAligned(player.rotationYaw)) return "face a diagonal (yaw " + Utils.round(player.rotationYaw, 1) + ")";

        MovingObjectPosition hit = raycastBlock(4.5);
        if (hit == null) return "aim at a block face";
        int face = hit.sideHit.getIndex();
        if (face < 2) return "aim at a SIDE face, not top/bottom";
        Vec3 localHit = localHitVec(hit);
        if (!isInActivationFaceCenter(face, localHit)) return "aim lower-center of the side face";

        int[] pos = {hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ()};
        if (!isPlayerOnActivationBlock(pos)) return "stand over the block you're aiming at";

        int[] travel = travelDirectionFromYaw(player.rotationYaw);
        int travelFace = travel[0] > 0 ? 5 : travel[0] < 0 ? 4 : travel[1] > 0 ? 3 : 2;
        if (face != travelFace) return "aim at the face in your travel direction";

        int aheadX = pos[0] + travel[0];
        int aheadZ = pos[2] + travel[1];
        if (!isReplaceableName(blockNameAt(aheadX, pos[1] + 1, aheadZ), false)) return "clear the block ahead";

        if (!mc.thePlayer.movementInput.sneak) return "hold SNEAK";
        return null;
    }

    // ============================== edge / geometry checks ==============================

    int[] travelDirectionFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        double rawX = Math.sin(radians) - Math.cos(radians);
        double rawZ = -Math.cos(radians) - Math.sin(radians);
        if (Math.abs(rawX) >= Math.abs(rawZ)) return new int[]{rawX >= 0.0 ? 1 : -1, 0};
        return new int[]{0, rawZ >= 0.0 ? 1 : -1};
    }

    boolean isLookingAtEdge() {
        EntityPlayerSP player = mc.thePlayer;
        if (!isActivationYawAligned(player.rotationYaw)) return false;
        MovingObjectPosition hit = raycastBlock(4.5);
        if (hit == null) return false;

        int face = hit.sideHit.getIndex();
        if (face < 2) return false;
        if (!isInActivationFaceCenter(face, localHitVec(hit))) return false;

        int[] travel = travelDirectionFromYaw(player.rotationYaw);
        int travelFace = travel[0] > 0 ? 5 : travel[0] < 0 ? 4 : travel[1] > 0 ? 3 : 2;
        if (face != travelFace) return false;

        int[] pos = {hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ()};
        if (!isPlayerOnActivationBlock(pos)) return false;
        int aheadX = pos[0] + travel[0];
        int aheadZ = pos[2] + travel[1];
        if (!isReplaceableName(blockNameAt(aheadX, pos[1] + 1, aheadZ), false)) return false;

        double lipDistance;
        if (face == 5) lipDistance = (pos[0] + 1) - player.posX;
        else if (face == 4) lipDistance = player.posX - pos[0];
        else if (face == 3) lipDistance = (pos[2] + 1) - player.posZ;
        else lipDistance = player.posZ - pos[2];
        return lipDistance <= 0.65;
    }

    boolean isActivationYawAligned(float yaw) {
        float nearestDiagonal = Math.round((yaw - 45.0f) / 90.0f) * 90.0f + 45.0f;
        return Math.abs(wrapAngle(yaw - nearestDiagonal)) <= ACTIVATION_YAW_TOLERANCE;
    }

    boolean isPlayerOnActivationBlock(int[] pos) {
        if (pos == null) return false;
        EntityPlayerSP player = mc.thePlayer;
        if (pos[1] != floor(player.posY - 0.01)) return false;
        double centerX = pos[0] + 0.5;
        double centerZ = pos[2] + 0.5;
        return Math.abs(player.posX - centerX) <= 0.85
                && Math.abs(player.posZ - centerZ) <= 0.85;
    }

    // Limit arming to the lower portion of the outward face and exclude its deep end.
    boolean isInActivationFaceCenter(int face, Vec3 localHit) {
        if (localHit == null) return false;
        double acrossFace = (face == 4 || face == 5) ? localHit.zCoord : localHit.xCoord;
        if (face == 3 || face == 4) acrossFace = 1.0 - acrossFace;
        return acrossFace >= ACTIVATION_ACROSS_MIN && acrossFace <= ACTIVATION_ACROSS_MAX
                && localHit.yCoord >= ACTIVATION_HEIGHT_MIN && localHit.yCoord <= ACTIVATION_HEIGHT_MAX;
    }

    // ============================== raycast / block lookup ==============================

    MovingObjectPosition raycastBlock(double distance) {
        MovingObjectPosition mop = mc.thePlayer.rayTrace(distance, 1.0f);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        return mop;
    }

    // Local (0..1 within the block) hit coordinate, matching the script's `hit[1]` offset.
    Vec3 localHitVec(MovingObjectPosition mop) {
        BlockPos pos = mop.getBlockPos();
        return new Vec3(mop.hitVec.xCoord - pos.getX(), mop.hitVec.yCoord - pos.getY(), mop.hitVec.zCoord - pos.getZ());
    }

    Vec3 getEyes() {
        EntityPlayerSP player = mc.thePlayer;
        return new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    }

    Vec3 getSupportFaceHitVec(int[] supportPos, int placeFace, double primaryOffset, double secondaryOffset) {
        double primary = Math.max(0.001, Math.min(0.999, primaryOffset));
        double secondary = Math.max(0.001, Math.min(0.999, secondaryOffset));
        if (placeFace == 2) return new Vec3(supportPos[0] + primary, supportPos[1] + secondary, supportPos[2] + 0.001);
        if (placeFace == 3) return new Vec3(supportPos[0] + primary, supportPos[1] + secondary, supportPos[2] + 0.999);
        if (placeFace == 5) return new Vec3(supportPos[0] + 0.999, supportPos[1] + primary, supportPos[2] + secondary);
        if (placeFace == 4) return new Vec3(supportPos[0] + 0.001, supportPos[1] + primary, supportPos[2] + secondary);
        if (placeFace == 0) return new Vec3(supportPos[0] + primary, supportPos[1] + 0.001, supportPos[2] + secondary);
        return new Vec3(supportPos[0] + primary, supportPos[1] + 0.999, supportPos[2] + secondary);
    }

    String blockNameAt(int x, int y, int z) {
        IBlockState state = BlockUtils.getBlockState(new BlockPos(x, y, z));
        if (state == null) return "air";
        String name = state.getBlock().getRegistryName();
        return name == null ? "air" : name.replace("minecraft:", "").toLowerCase();
    }

    boolean isInteractableBlock(int x, int y, int z) {
        IBlockState state = BlockUtils.getBlockState(new BlockPos(x, y, z));
        if (state == null) return false;
        net.minecraft.block.Block block = state.getBlock();
        if (BlockUtils.isInteractable(block)) return true;
        String type = block.getClass().getSimpleName();
        for (String interactableType : INTERACTABLE_TYPES) {
            if (type.equals(interactableType)) return true;
        }
        return false;
    }

    boolean isReplaceable(int x, int y, int z) {
        return isReplaceableName(blockNameAt(x, y, z), false);
    }

    boolean isReplaceableName(String name, boolean airOnly) {
        if (airOnly) return name.equals("air");
        for (String replaceable : REPLACEABLE_BLOCKS) {
            if (name.equals(replaceable)) return true;
        }
        for (String replaceable : EXPERIMENTAL_REPLACEABLE_BLOCKS) {
            if (name.equals(replaceable)) return true;
        }
        return false;
    }

    boolean isSupportAvailable(int x, int y, int z) {
        if (isInteractableBlock(x, y, z)) return false;
        return !isReplaceable(x, y, z);
    }

    boolean isHoldingBlock(EntityPlayerSP player) {
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() instanceof ItemBlock;
    }

    boolean isUsableBlockStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock) || stack.stackSize <= 0) return false;
        String registryName = stack.getItem().getRegistryName();
        if (registryName == null) return false;
        String name = registryName.replace("minecraft:", "").toLowerCase();
        for (String bad : UNPLACEABLE_EXACT) {
            if (name.equals(bad)) return false;
        }
        for (String bad : UNPLACEABLE_CONTAINS) {
            if (name.contains(bad)) return false;
        }
        return true;
    }

    int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    // ============================== automation start/stop ==============================

    void armAutomation() {
        armed = true;
        running = false;
        activatePromptAt = 0L;
        promptBrokeAt = 0L;
        setupTick = 0;
        cyclePhase = 19;
        rotationActive = false;
        speedTellyPhaseAccumulator = 0.0;
        activationMovementHeld = false;
        printStatus("&eArmed. Sneak looking down, wait for green, hold rmb and release sneak");
    }

    void beginAutomation() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !isHoldingBlock(player)) {
            printStatus("&cHold blocks before starting");
            return;
        }
        if (!isActivationYawAligned(player.rotationYaw)) return;

        disableGuardForRun();
        baseYaw = player.rotationYaw;
        calculateTravelDirection(baseYaw);
        antiSwayLane = travelX != 0 ? player.posZ : player.posX;
        antiSwayYawOffset = 0.0f;
        antiSwayTapUsed = false;
        cancelledGhostBlocks.clear();
        initializeStraightBridgeLane(player);
        firstTellyPlacementPending = false;
        adaptiveAimValid = false;
        adaptiveAimUpdatedAt = 0L;
        setupTick = 0;
        cyclePhase = 19;
        armed = false;
        running = true;
        freezeLastTickAt = System.currentTimeMillis();
        activationMovementHeld = false;
        tellyAutoPlaceWindow = true;
        scriptedRotationYaw = player.rotationYaw;
        scriptedRotationPitch = player.rotationPitch;
        takeoverDetectionAt = 0L;
        takeoverCameraValid = false;
        clearInitialMovementHolds();
        resetControllerState();
        speedTellyPhaseAccumulator = 0.0;
        setKeyPressed(mc.gameSettings.keyBindAttack, false);
        applyMovement(-1.0f, -1.0f, false, false);
        setRotationTarget(baseYaw, 74.52f, 50L);
        applyUse(true);
        printStatus("&aStarted");
    }

    void stopAutomation(boolean turnOffButton) {
        armed = false;
        running = false;
        setupTick = 0;
        cyclePhase = 19;
        rotationActive = false;
        speedTellyPhaseAccumulator = 0.0;
        activationMovementHeld = false;
        tellyAutoPlaceWindow = false;
        autoPlaceDebugActive = false;
        antiSwayYawOffset = 0.0f;
        antiSwayTapUsed = false;
        firstTellyPlacementPending = false;
        latestStraightPlacedPos = null;
        runTopY = Integer.MIN_VALUE;
        backfillQueue.clear();
        backfillQueueTicks.clear();
        backfillReadyPos = null;
        backfillAttemptActive = false;
        dbgPlaced = 0;
        dbgRejStrict = 0;
        dbgRejTarget = 0;
        dbgBfTry = 0;
        dbgBfHit = 0;
        dbgCycle = 0;
        tellyLaneY = Integer.MIN_VALUE;
        tellyFloorY = Integer.MIN_VALUE;
        adaptiveAimValid = false;
        adaptiveAimUpdatedAt = 0L;
        scriptedRotationYaw = 0.0f;
        scriptedRotationPitch = 0.0f;
        takeoverDetectionAt = 0L;
        takeoverCameraValid = false;
        takeoverCameraYaw = 0.0f;
        takeoverCameraPitch = 0.0f;
        takeoverAccumulated = 0.0f;
        takeoverLastFrameAt = 0L;

        try {
            cancelledGhostBlocks.clear();
            clearInitialMovementHolds();
            resetControllerState();
            if (mc.thePlayer != null) {
                mc.thePlayer.movementInput.moveForward = 0.0f;
                mc.thePlayer.movementInput.moveStrafe = 0.0f;
                mc.thePlayer.movementInput.jump = false;
                mc.thePlayer.setSprinting(false);
            }
            releaseMovementKeys();
            restorePhysicalUse();
            setKeyPressed(mc.gameSettings.keyBindAttack, Mouse.isButtonDown(0));
        } catch (Exception ignored) {}

        restoreGuardState();

        freezeLastTickAt = 0L;
        armed = true;
        activatePromptAt = 0L;
        promptBrokeAt = 0L;
        if (turnOffButton) {
            printStatus("&eStopped. Sneak looking down to arm again");
        }
    }

    void clearInitialMovementHolds() {
        ignoreForwardUntilRelease = false;
        ignoreBackUntilRelease = false;
        ignoreLeftUntilRelease = false;
        ignoreRightUntilRelease = false;
        ignoreJumpUntilRelease = false;
        ignoreSneakUntilRelease = false;
        ignoreSprintUntilRelease = false;
    }

    // ============================== Guard (SafeWalk) interop ==============================

    void disableGuardForRun() {
        if (guardStateCaptured) {
            enforceGuardDisabledForRun();
            return;
        }
        if (!disableGuard.isToggled()) return;

        try {
            SafeWalk guard = (SafeWalk) Module.getModule(SafeWalk.class);
            guardWasEnabled = guard != null && guard.isEnabled();
            guardStateCaptured = true;
            if (guardWasEnabled) guard.disable();
        } catch (Exception ignored) {
            guardStateCaptured = false;
        }
    }

    void enforceGuardDisabledForRun() {
        if (!guardStateCaptured) return;
        try {
            SafeWalk guard = (SafeWalk) Module.getModule(SafeWalk.class);
            if (guard != null && guard.isEnabled()) guard.disable();
        } catch (Exception ignored) {}
    }

    void restoreGuardState() {
        if (!guardStateCaptured) return;

        boolean restoreEnabled = guardWasEnabled;
        guardStateCaptured = false;
        try {
            SafeWalk guard = (SafeWalk) Module.getModule(SafeWalk.class);
            if (guard == null) return;
            boolean currentlyEnabled = guard.isEnabled();
            if (restoreEnabled && !currentlyEnabled) guard.enable();
            if (!restoreEnabled && currentlyEnabled) guard.disable();
        } catch (Exception ignored) {}
    }

    // ============================== misc ==============================

    // NOTE: the original script gated every status message on a "Print" toggle that was
    // never actually registered in onLoad() - modules.getButton() on a missing setting
    // name would have thrown and been swallowed by the try/catch, silently no-op'ing
    // every printStatus() call. Rather than reproduce that (apparent) bug, this always
    // prints - flag if you'd rather it stayed silent by default.
    private int selectedMode() {
        return Math.max(0, Math.min(MODES.length - 1, (int) mode.getInput()));
    }

    /** True while the "Legit Telly" mode (the original Telly behaviour) is the running mode. */
    boolean isLegitTelly() {
        return activeMode == MODE_LEGIT_TELLY;
    }

    /** True while the "Keep-y" mode is the running mode. */
    boolean isKeepY() {
        return activeMode == MODE_KEEP_Y;
    }

    private void startMode(int newMode) {
        activeMode = newMode;
        if (newMode == MODE_KEEP_Y) {
            keepY.onEnable();
        }
        else {
            enableLegitTelly();
        }
    }

    private void stopActiveMode() {
        if (activeMode == MODE_KEEP_Y) {
            keepY.onDisable();
        }
        else if (activeMode == MODE_LEGIT_TELLY) {
            disableLegitTelly();
        }
        activeMode = -1;
    }

    /** Switches over cleanly if the mode was changed while Scaffold is on. */
    private void syncMode() {
        int selected = selectedMode();
        if (selected != activeMode) {
            stopActiveMode();
            startMode(selected);
        }
    }

    @Override
    public String getInfo() {
        return MODES[selectedMode()];
    }

    @Override
    public void guiUpdate() {
        boolean legit = selectedMode() == MODE_LEGIT_TELLY;
        autoSwap.setVisible(legit, this);
        disableGuard.setVisible(legit, this);
        showActivationBounds.setVisible(legit, this);
        showActivatePrompt.setVisible(legit, this);
        backfillGap.setVisible(legit, this);
        flatStart.setVisible(legit, this);
        speedTellyKey.setVisible(legit, this);
        keepY.setSettingsVisible(!legit);
    }

    void printStatus(String message) {
        if (!debug.isToggled()) return;
        Utils.sendMessage("Scaffold | " + message);
    }

    float wrapAngle(float angle) {
        angle = angle % 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    // ============================== silent aim ==============================

    boolean silentAimEnabled() {
        return silentAim.isToggled();
    }

    // Only active once the setup phase has finished (setupTick < 0) - matches the original's
    // intent of not hiding the initial "lock in" turn, only the steady-state bridging cycle.
    boolean silentAimActive() {
        return silentAimEnabled() && running && setupTick < 0;
    }

    // When silent aim is active, these return the script's internal target rotation (what
    // gets sent to the server) rather than the player's real, visible camera rotation - which
    // applySmoothedRotation() deliberately leaves untouched in that case.
    float aimYaw(EntityPlayerSP player) {
        if (silentAimActive()) return scriptedRotationYaw;
        return player == null ? scriptedRotationYaw : player.rotationYaw;
    }

    float aimPitch(EntityPlayerSP player) {
        if (silentAimActive()) return scriptedRotationPitch;
        return player == null ? scriptedRotationPitch : player.rotationPitch;
    }

    // ============================== flat start / backfill toggles ==============================

    boolean flatStartEnabled() {
        return flatStart.isToggled();
    }

    boolean flatStartActive() {
        return flatStartEnabled() && dbgCycle <= 2;
    }

    boolean backfillGapEnabled() {
        return backfillGap.isToggled();
    }

    // These were part of Pass 1 but got dropped when Pass 2 rewrote the whole file instead
    // of editing it in place - re-added here since Pass 3's engine code depends on several
    // of them (clampFloat, posKey, offsetPos especially). Flagging so it's visible: this is
    // a fix for a mistake in an earlier pass, not new Pass 3 content.

    float clampFloat(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    boolean posEquals(int[] a, int[] b) {
        return a != null && b != null && a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    String posKey(int[] pos) {
        return pos[0] + "," + pos[1] + "," + pos[2];
    }

    int[] offsetPos(int[] pos, int face) {
        if (face == 0) return new int[]{pos[0], pos[1] - 1, pos[2]};
        if (face == 1) return new int[]{pos[0], pos[1] + 1, pos[2]};
        if (face == 2) return new int[]{pos[0], pos[1], pos[2] - 1};
        if (face == 3) return new int[]{pos[0], pos[1], pos[2] + 1};
        if (face == 4) return new int[]{pos[0] - 1, pos[1], pos[2]};
        return new int[]{pos[0] + 1, pos[1], pos[2]};
    }

    int opposite(int face) {
        if (face == 0) return 1;
        if (face == 1) return 0;
        if (face == 2) return 3;
        if (face == 3) return 2;
        if (face == 4) return 5;
        return 4;
    }

    int rotateY(int face) {
        if (face == 2) return 5;
        if (face == 5) return 3;
        if (face == 3) return 4;
        if (face == 4) return 2;
        return face;
    }

    int rotateYCCW(int face) {
        if (face == 2) return 4;
        if (face == 4) return 3;
        if (face == 3) return 5;
        if (face == 5) return 2;
        return face;
    }

    int facingFromYaw(float yaw) {
        int index = floor(yaw / 90.0 + 0.5) & 3;
        if (index == 0) return 3;
        if (index == 1) return 4;
        if (index == 2) return 2;
        return 5;
    }

    String faceName(int face) {
        if (face == 0) return "DOWN";
        if (face == 1) return "UP";
        if (face == 2) return "NORTH";
        if (face == 3) return "SOUTH";
        if (face == 4) return "WEST";
        return "EAST";
    }

    int faceFromName(String name) {
        if (name == null) return -1;
        String upper = name.toUpperCase();
        if (upper.equals("DOWN")) return 0;
        if (upper.equals("UP")) return 1;
        if (upper.equals("NORTH")) return 2;
        if (upper.equals("SOUTH")) return 3;
        if (upper.equals("WEST")) return 4;
        if (upper.equals("EAST")) return 5;
        return -1;
    }

    float sanitizePitch(float pitch, float fallbackPitch) {
        float safeFallback = clampFloat(Float.isNaN(fallbackPitch) ? 0.0f : fallbackPitch, -90.0f, 90.0f);
        if (Float.isNaN(pitch) || Float.isInfinite(pitch)) return safeFallback;
        return clampFloat(pitch, -90.0f, 90.0f);
    }

    // ============================== bridge-cycle engine (PASS 3) ==============================

    // resetControllerState() lives further down with the full AutoPlace state reset -
    // this stub (which only touched 2 of the ~20 fields it needs to reset) was superseded.

    boolean isSpeedTellyHeld() {
        return running && mc.currentScreen == null && speedTellyKey.isPressed();
    }

    long getSpeedTellyRotationDuration(long baseDuration) {
        long safeBaseDuration = Math.max(1L, baseDuration);
        if (!isSpeedTellyHeld()) return safeBaseDuration;

        double speed = clampFloat((float) rotationSpeed.getInput(), 1.0f, 240.0f);
        // 20 = vanilla Telly's normal 50ms rotation. Higher values rotate faster while the
        // Speed-Telly key is held, and no Timer module/global timer is used.
        long speedDuration = Math.round(1000.0 / speed);
        return Math.max(1L, speedDuration);
    }

    int getSpeedTellyPhaseAdvance() {
        if (!isSpeedTellyHeld()) {
            speedTellyPhaseAccumulator = 0.0;
            return 1;
        }

        // Treat the slider as phases-per-second: 20.0 exactly matches normal unheld Telly.
        // The slider can be set up to 1000 now, but Minecraft only applies movement input once
        // per game tick (20 times/second). Values above 20 stay on the stable phase-20 WASD
        // timing to avoid skipping parts of the Telly movement curve and falling.
        double speed = Math.max(1.0, Math.min(1000.0, wasdKeysPressingSpeed.getInput()));
        if (speed > 20.0) speed = 20.0;
        speedTellyPhaseAccumulator += speed / 20.0;

        int advance = (int) Math.floor(speedTellyPhaseAccumulator);
        if (advance <= 0) return 0;
        speedTellyPhaseAccumulator -= advance;
        return 1;
    }

    void setRotationTarget(float targetYaw, float targetPitch, long duration) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;

        applySmoothedRotation();
        rotationStartYaw = aimYaw(player);
        rotationStartPitch = aimPitch(player);
        float correctedTargetYaw = targetYaw;
        boolean adaptivePlacementTarget = running
                && tellyAutoPlaceWindow
                && firstTellyPlacementPending
                && adaptiveAimValid
                && System.currentTimeMillis() - adaptiveAimUpdatedAt <= 125L;
        if (adaptivePlacementTarget) {
            correctedTargetYaw = adaptiveAimYaw;
            targetPitch = adaptiveAimPitch;
        } else if (running) {
            correctedTargetYaw += antiSwayYawOffset;
        }

        rotationStepCounter++;
        correctedTargetYaw += (float) (SENSITIVITY_QUANTUM * YAW_NUDGE_PATTERN[rotationStepCounter % 5]);

        rotationTargetYaw = rotationStartYaw + wrapAngle(correctedTargetYaw - rotationStartYaw);
        rotationTargetPitch = clampFloat(targetPitch, -90.0f, 90.0f);
        rotationStartedAt = System.currentTimeMillis();
        rotationDuration = getSpeedTellyRotationDuration(duration);
        rotationActive = true;
    }

    void applySmoothedRotation() {
        if (!rotationActive) return;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;

        long effectiveDuration = getSpeedTellyRotationDuration(rotationDuration);
        double progress = (double) (System.currentTimeMillis() - rotationStartedAt) / (double) effectiveDuration;
        if (progress < 0.0) progress = 0.0;
        if (progress > 1.0) progress = 1.0;

        float desiredYaw = rotationStartYaw + (rotationTargetYaw - rotationStartYaw) * (float) progress;
        float desiredPitch = rotationStartPitch + (rotationTargetPitch - rotationStartPitch) * (float) progress;
        float quantizedYaw = quantizeFrom(rotationStartYaw, desiredYaw);
        float quantizedPitch = quantizeFrom(rotationStartPitch, desiredPitch);

        scriptedRotationYaw = quantizedYaw;
        scriptedRotationPitch = clampFloat(quantizedPitch, -90.0f, 90.0f);
        // Silent aim: leave the player's real, visible camera rotation untouched - only the
        // internal scriptedRotationYaw/Pitch (read by aimYaw/aimPitch and sent to the server
        // via onPreMotion) moves. Without silent aim, the camera itself turns as before.
        if (!silentAimActive()) {
            player.rotationYaw = scriptedRotationYaw;
            player.rotationPitch = scriptedRotationPitch;
        }
        if (progress >= 1.0) rotationActive = false;
    }

    float quantizeFrom(float origin, float value) {
        double steps = Math.round((value - origin) / SENSITIVITY_QUANTUM);
        return (float) (origin + steps * SENSITIVITY_QUANTUM);
    }

    void applyMovement(float forward, float strafe, boolean jumping, boolean sprinting) {
        float controlledForward = forward;
        boolean controlledSprint = sprinting;

        float correctedStrafe = strafe;
        boolean antiSway = running;
        if (antiSway) correctedStrafe = applyAntiSwayCorrection(controlledForward, strafe);
        else antiSwayYawOffset = 0.0f;

        setKeyPressed(mc.gameSettings.keyBindForward, controlledForward > 0.03f);
        setKeyPressed(mc.gameSettings.keyBindBack, controlledForward < -0.03f);
        setKeyPressed(mc.gameSettings.keyBindLeft, correctedStrafe > 0.5f);
        setKeyPressed(mc.gameSettings.keyBindRight, correctedStrafe < -0.5f);
        setKeyPressed(mc.gameSettings.keyBindJump, jumping);
        setKeyPressed(mc.gameSettings.keyBindSprint, controlledSprint);
        mc.thePlayer.movementInput.moveForward = controlledForward;
        mc.thePlayer.movementInput.moveStrafe = correctedStrafe;
        mc.thePlayer.movementInput.jump = jumping;
        mc.thePlayer.movementInput.sneak = false;
        mc.thePlayer.setSprinting(controlledSprint);
    }

    void suppressSneakInput() {
        setKeyPressed(mc.gameSettings.keyBindSneak, false);
        mc.thePlayer.movementInput.sneak = false;
    }

    void calculateTravelDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        double rawX = Math.sin(radians) - Math.cos(radians);
        double rawZ = -Math.cos(radians) - Math.sin(radians);

        if (Math.abs(rawX) >= Math.abs(rawZ)) {
            travelX = rawX >= 0.0 ? 1 : -1;
            travelZ = 0;
        } else {
            travelX = 0;
            travelZ = rawZ >= 0.0 ? 1 : -1;
        }
    }

    void initializeStraightBridgeLane(EntityPlayerSP player) {
        int startX = floor(player.posX);
        int startY = floor(player.posY) - 1;
        int startZ = floor(player.posZ);
        bridgeLaneBlock = travelX != 0 ? startZ : startX;
        bridgeStartProgress = startX * travelX + startZ * travelZ;

        MovingObjectPosition hit = raycastBlock(4.5);
        if (hit != null) {
            int[] hitPos = {hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ()};
            int hitLane = travelX != 0 ? hitPos[2] : hitPos[0];
            int hitProgress = straightProgress(hitPos);
            if (hitLane == bridgeLaneBlock
                    && Math.abs(hitPos[0] - startX) <= 2
                    && Math.abs(hitPos[2] - startZ) <= 2
                    && hitProgress < bridgeStartProgress) {
                bridgeStartProgress = hitProgress;
            }
        }

        latestStraightPlacedPos = new int[]{startX, startY, startZ};
        tellyLaneY = startY;
        tellyFloorY = startY;
    }

    int straightProgress(int[] position) {
        if (position == null) return Integer.MIN_VALUE;
        return position[0] * travelX + position[2] * travelZ;
    }

    boolean isStraightTellyTarget(int[] position) {
        if (!running || position == null) return true;
        int lane = travelX != 0 ? position[2] : position[0];
        if (lane != bridgeLaneBlock) return false;
        return straightProgress(position) >= bridgeStartProgress;
    }

    void updateAdaptivePlacementAim(EntityPlayerSP player) {
        if (!firstTellyPlacementPending) return;
        // cachedCandidate is always null until PASS 4 wires the full candidate system,
        // so this always falls through to the latestStraightPlacedPos-based fallback below.
        int[] support = latestStraightPlacedPos != null ? latestStraightPlacedPos : lastPlacedPos;
        if (support == null || !isStraightTellyTarget(support)) return;
        int face = travelX > 0 ? 5 : travelX < 0 ? 4 : travelZ > 0 ? 3 : 2;
        int[] nextTarget = offsetPos(support, face);
        if (!isStraightTellyTarget(nextTarget) || !isReplaceable(nextTarget[0], nextTarget[1], nextTarget[2])) return;
        Vec3 fallbackHit = getSupportFaceHitVec(support, face, 0.5, 0.5);
        setAdaptiveAimToPoint(player, fallbackHit);
    }

    void setAdaptiveAimToPoint(EntityPlayerSP player, Vec3 point) {
        if (player == null || point == null) return;
        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;
        double dx = point.xCoord - eyeX;
        double dy = point.yCoord - eyeY;
        double dz = point.zCoord - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-5 && Math.abs(dy) < 1.0E-5) return;

        adaptiveAimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        adaptiveAimPitch = clampFloat((float) (-Math.toDegrees(Math.atan2(dy, horizontal))), -89.0f, 89.0f);
        adaptiveAimUpdatedAt = System.currentTimeMillis();
        adaptiveAimValid = true;
    }

    float applyAntiSwayCorrection(float forward, float recordedStrafe) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return recordedStrafe;

        double laneVelocity;
        double lanePosition = travelX != 0 ? player.posZ : player.posX;
        laneVelocity = travelX != 0 ? player.motionZ : player.motionX;
        double error = antiSwayLane - lanePosition;

        if (Math.abs(error) < 0.015 && Math.abs(laneVelocity) < 0.008) {
            antiSwayTapUsed = false;
            antiSwayYawOffset *= 0.65f;
            if (Math.abs(antiSwayYawOffset) < 0.03f) antiSwayYawOffset = 0.0f;
            return recordedStrafe;
        }

        double desiredLaneVelocity = error * 0.42 - laneVelocity * 0.78;
        if (desiredLaneVelocity > 0.16) desiredLaneVelocity = 0.16;
        if (desiredLaneVelocity < -0.16) desiredLaneVelocity = -0.16;
        double velocityCorrection = desiredLaneVelocity - laneVelocity;

        double radians = Math.toRadians(player.rotationYaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double yawLaneDerivative = travelX != 0
                ? -forward * sin + recordedStrafe * cos
                : -forward * cos - recordedStrafe * sin;
        double desiredYawOffset = 0.0;
        if (Math.abs(yawLaneDerivative) >= 0.12) {
            desiredYawOffset = Math.toDegrees(velocityCorrection * 0.55 / yawLaneDerivative);
        }
        if (desiredYawOffset > 2.25) desiredYawOffset = 2.25;
        if (desiredYawOffset < -2.25) desiredYawOffset = -2.25;
        antiSwayYawOffset = antiSwayYawOffset * 0.60f + (float) desiredYawOffset * 0.40f;

        double strafeLaneAxis = travelX != 0 ? sin : cos;
        boolean tapHelps = Math.abs(strafeLaneAxis) >= 0.20 && velocityCorrection * strafeLaneAxis > 0.0;
        if (tapHelps
                && !antiSwayTapUsed
                && Math.abs(velocityCorrection) >= 0.03
                && recordedStrafe < 0.5f) {
            antiSwayTapUsed = true;
            return recordedStrafe + 1.0f;
        }

        return recordedStrafe;
    }

    void applyUse(boolean pressed) {
        if (pressed && !autoPlaceDebugActive) {
            dbgCycle++;
            printStatus("&e--- cycle " + dbgCycle + " placed=" + dbgPlaced
                    + " rejHeight=" + dbgRejStrict + " rejTarget=" + dbgRejTarget
                    + " bfTry=" + dbgBfTry + " bfAim=" + dbgBfHit
                    + " top=" + runTopY + (flatStartActive() ? " FLATSTART" : "") + " ---");
            dbgPlaced = 0;
            dbgRejStrict = 0;
            dbgRejTarget = 0;
            dbgBfTry = 0;
            dbgBfHit = 0;
        }
        autoPlaceDebugActive = pressed;
        tellyAutoPlaceWindow = pressed;
        setKeyPressed(mc.gameSettings.keyBindUseItem, pressed);
    }

    void restorePhysicalUse() {
        tellyAutoPlaceWindow = false;
        autoPlaceDebugActive = false;
        setKeyPressed(mc.gameSettings.keyBindUseItem, Mouse.isButtonDown(1));
    }

    void releaseMovementKeys() {
        restorePhysicalKey(mc.gameSettings.keyBindForward);
        restorePhysicalKey(mc.gameSettings.keyBindBack);
        restorePhysicalKey(mc.gameSettings.keyBindLeft);
        restorePhysicalKey(mc.gameSettings.keyBindRight);
        restorePhysicalKey(mc.gameSettings.keyBindJump);
        restorePhysicalKey(mc.gameSettings.keyBindSneak);
        restorePhysicalKey(mc.gameSettings.keyBindSprint);
    }

    void restorePhysicalKey(KeyBinding binding) {
        setKeyPressed(binding, org.lwjgl.input.Keyboard.isKeyDown(binding.getKeyCode()));
    }

    boolean isActivationInProgress() {
        return armed && !running && activatePromptAt != 0L;
    }

    boolean isDropProtected() {
        return running || isActivationInProgress();
    }

    // Always false in this script version - probeMovementFix() is a real no-op as written
    // (matches the original exactly rather than silently dropping dead-but-harmless code).
    boolean silentRotationsProbe = false;

    void probeMovementFix() {
        if (!silentRotationsProbe) return;
        RotationHelper.get().forceMovementFix = true;
    }

    boolean isForwardKeyHeld() {
        try {
            return org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode());
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean detectManualCameraTakeover() {
        if (!running || setupTick >= 0 || System.currentTimeMillis() < takeoverDetectionAt) return false;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return false;

        long now = System.currentTimeMillis();
        // In silent-aim mode the script never touches the visible camera, so comparing
        // against scriptedRotationYaw/Pitch would be meaningless - any camera movement in
        // that mode is inherently manual. Compare against the last-observed camera position
        // instead (a frame-to-frame delta) rather than the script's internal target.
        float expectedYaw = silentAimActive() ? takeoverCameraYaw : scriptedRotationYaw;
        float expectedPitch = silentAimActive() ? takeoverCameraPitch : scriptedRotationPitch;
        if (!takeoverCameraValid) {
            takeoverCameraValid = true;
            takeoverCameraYaw = player.rotationYaw;
            takeoverCameraPitch = player.rotationPitch;
            takeoverAccumulated = 0.0f;
            takeoverLastFrameAt = now;
            return false;
        }

        double yawInput = Math.abs(wrapAngle(player.rotationYaw - expectedYaw));
        double pitchInput = Math.abs(player.rotationPitch - expectedPitch);
        double noiseFloor = SENSITIVITY_QUANTUM * 0.45;

        long elapsed = Math.max(0L, now - takeoverLastFrameAt);
        takeoverLastFrameAt = now;
        takeoverAccumulated -= (float) (elapsed * 0.045);
        if (takeoverAccumulated < 0.0f) takeoverAccumulated = 0.0f;
        if (yawInput > noiseFloor || pitchInput > noiseFloor) {
            takeoverAccumulated += (float) (yawInput + pitchInput);
        }

        takeoverCameraYaw = player.rotationYaw;
        takeoverCameraPitch = player.rotationPitch;

        if (takeoverAccumulated >= 25.0f) {
            stopAutomation(true);
            return true;
        }
        return false;
    }

    void captureInitialMovementHolds() {
        ignoreForwardUntilRelease = org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode());
        ignoreBackUntilRelease = org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode());
        ignoreLeftUntilRelease = org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode());
        ignoreRightUntilRelease = org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode());
        ignoreJumpUntilRelease = org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode());
        ignoreSneakUntilRelease = org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
        ignoreSprintUntilRelease = org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode());
    }

    // NOTE: the original script's onKey(keyCode, state, ...) fires on raw key-DOWN events.
    // This codebase's real key events (KeyPressEvent/ClickMouseEvent) don't carry press/
    // release state or a key code (checked earlier this conversation), so there's no clean
    // real event to hook here. This polls real Keyboard.isKeyDown() each tick instead - same
    // approach already used for isForwardKeyHeld() - which achieves the same protective intent
    // (stop if the player manually engages a movement key that wasn't already held before the
    // cycle started) without needing an edge-triggered "key just pressed" signal.
    boolean isManualMovementKeyHeldNow() {
        if (org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()) && !ignoreForwardUntilRelease) return true;
        if (org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()) && !ignoreBackUntilRelease) return true;
        if (org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()) && !ignoreLeftUntilRelease) return true;
        if (org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()) && !ignoreRightUntilRelease) return true;
        if (org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) && !ignoreJumpUntilRelease) return true;
        if (org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) && !ignoreSneakUntilRelease) return true;
        if (org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode()) && !ignoreSprintUntilRelease) return true;
        return false;
    }

    void updateInitialMovementHoldReleases() {
        if (ignoreForwardUntilRelease && !org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) ignoreForwardUntilRelease = false;
        if (ignoreBackUntilRelease && !org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) ignoreBackUntilRelease = false;
        if (ignoreLeftUntilRelease && !org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())) ignoreLeftUntilRelease = false;
        if (ignoreRightUntilRelease && !org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode())) ignoreRightUntilRelease = false;
        if (ignoreJumpUntilRelease && !org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) ignoreJumpUntilRelease = false;
        if (ignoreSneakUntilRelease && !org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) ignoreSneakUntilRelease = false;
        if (ignoreSprintUntilRelease && !org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode())) ignoreSprintUntilRelease = false;
    }

    // ============================== the core cycle driver ==============================

    @SubscribeEvent
    public void onPostPlayerInput(PostPlayerInputEvent e) {
        if (!this.isEnabled()) return;
        if (isKeepY()) {
            keepY.onPostPlayerInput(e);
            return;
        }
        if (!isLegitTelly()) return;
        if (!running) return;
        probeMovementFix();
        if (isForwardKeyHeld()) {
            stopAutomation(true);
            printStatus("&eStopped by forward key");
            return;
        }
        suppressSneakInput();
        enforceGuardDisabledForRun();

        if (setupTick >= 0) {
            if (setupTick < 12) {
                boolean setupJump = setupTick >= 6;
                applyMovement(-1.0f, -1.0f, setupJump, false);
                applyUse(true);

                if (setupTick == 11) {
                    setRotationTarget(baseYaw + yawCurve[19], pitchCurve[19], 50L);
                } else {
                    setRotationTarget(baseYaw, 74.52f, 50L);
                }
                setupTick++;
                return;
            }

            setupTick = -1;
            takeoverDetectionAt = System.currentTimeMillis() + 125L;
            EntityPlayerSP takeoverPlayer = mc.thePlayer;
            takeoverCameraValid = takeoverPlayer != null;
            takeoverAccumulated = 0.0f;
            takeoverLastFrameAt = System.currentTimeMillis();
            if (takeoverPlayer != null) {
                takeoverCameraYaw = takeoverPlayer.rotationYaw;
                takeoverCameraPitch = takeoverPlayer.rotationPitch;
            }
            captureInitialMovementHolds();
            cyclePhase = 19;
            speedTellyPhaseAccumulator = 0.0;
            firstTellyPlacementPending = true;
            adaptiveAimValid = false;
            resetControllerState();
            if (mc.thePlayer != null) updateAdaptivePlacementAim(mc.thePlayer);
        }

        updateInitialMovementHoldReleases();
        if (isManualMovementKeyHeldNow()) {
            stopAutomation(true);
            printStatus("&eStopped by manual movement key");
            return;
        }

        int phase = cyclePhase;
        int phaseAdvance = getSpeedTellyPhaseAdvance();

        // If Speed-Telly is above 20, some virtual phases are compressed into one Minecraft
        // tick. Movement can only be physically applied once per tick, so keep the stable
        // phase-20 movement state for this tick, but OR important held actions across every
        // compressed phase. This keeps jump/use/sprint timing from being skipped at >20.
        boolean sprinting = false;
        boolean jumping = false;
        boolean use = false;
        int sampledPhases = Math.max(1, phaseAdvance);
        for (int i = 0; i < sampledPhases; i++) {
            int sampledPhase = (phase + i) % yawCurve.length;
            if (sampledPhase == 0 || sampledPhase == 1) sprinting = true;
            if (sampledPhase >= 1 && sampledPhase <= 19) jumping = true;
            if (sampledPhase >= 7) use = true;
        }

        applyMovement(forwardCurve[phase], strafeCurve[phase], jumping, sprinting);
        applyUse(use);

        int nextPhase = (phase + phaseAdvance) % yawCurve.length;
        setRotationTarget(baseYaw + yawCurve[nextPhase], pitchCurve[nextPhase], 50L);
        cyclePhase = nextPhase;
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (!this.isEnabled() || !isLegitTelly() || !running) return;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;

        float yaw = aimYaw(player);
        float pitch = aimPitch(player);
        // AutoPlace's own pitch nudge for placement accuracy - separate from (and composes
        // with) the "Silent aim" toggle's camera/sent-rotation split above.
        if (silentPitchActive && !manualC08InWindow) {
            pitch = sanitizePitch(silentPitch, pitch);
        }
        e.setRotations(yaw, pitch);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!this.isEnabled() || !isLegitTelly()) return;
        updateActivatePromptFade();
        drawActivatePrompt();
        if (!running) return;
        if (detectManualCameraTakeover()) return;
        applySmoothedRotation();
        autoPlaceOnRenderTick();
    }

    // ============================== rendering (PASS 4) ==============================

    void updateActivatePromptFade() {
        boolean show = armed && !running && activatePromptAt != 0L;
        if (show) rememberActivationPromptColor();

        long now = System.currentTimeMillis();
        long elapsed = promptFadeLastAt == 0L ? 0L : Math.min(100L, now - promptFadeLastAt);
        promptFadeLastAt = now;
        float step = elapsed / 200.0f;
        promptAlpha += show ? step : -step;
        if (promptAlpha < 0.0f) promptAlpha = 0.0f;
        if (promptAlpha > 1.0f) promptAlpha = 1.0f;
    }

    void drawActivatePrompt() {
        if (!showActivatePrompt.isToggled()) return;
        if (promptAlpha < 0.05f) return;

        net.minecraft.client.gui.ScaledResolution scaled = new net.minecraft.client.gui.ScaledResolution(mc);
        int displayWidth = scaled.getScaledWidth();
        int displayHeight = scaled.getScaledHeight();

        String text = "Activate?";
        int alpha = (int) (promptAlpha * 255.0f);
        if (alpha < 16) alpha = 16;
        int color = (alpha << 24) | promptFadeRgb;
        int textWidth = mc.fontRendererObj.getStringWidth(text);
        float x = displayWidth / 2.0f - textWidth / 2.0f;
        float y = displayHeight / 2.0f + 10.0f;
        mc.fontRendererObj.drawString(text, x, y, color, true);
    }

    @SubscribeEvent
    public void onRenderWorld(net.minecraftforge.client.event.RenderWorldLastEvent e) {
        if (!this.isEnabled() || !isLegitTelly()) return;
        if (!showActivationBounds.isToggled()) return;
        if (!armed || running) return;
        if (promptAlpha < 0.05f) return;

        if (activatePromptAt != 0L) {
            MovingObjectPosition hit = raycastBlock(4.5);
            if (hit != null) {
                int face = hit.sideHit.getIndex();
                if (face >= 2) {
                    BlockPos pos = hit.getBlockPos();
                    boundsLastPos = new int[]{pos.getX(), pos.getY(), pos.getZ()};
                    boundsLastFace = face;
                }
            }
        }

        if (boundsLastPos == null || boundsLastFace < 2) return;
        drawActivationFaceRegion(boundsLastPos, boundsLastFace);
    }

    void drawActivationFaceRegion(int[] pos, int face) {
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        double yMin = pos[1] + ACTIVATION_HEIGHT_MIN;
        double yMax = pos[1] + ACTIVATION_HEIGHT_MAX;
        double x1, z1, x2, z2;

        if (face == 5) {
            x1 = pos[0] + 1.005;
            x2 = x1;
            z1 = pos[2] + ACTIVATION_ACROSS_MIN;
            z2 = pos[2] + ACTIVATION_ACROSS_MAX;
        } else if (face == 4) {
            x1 = pos[0] - 0.005;
            x2 = x1;
            z1 = pos[2] + (1.0 - ACTIVATION_ACROSS_MAX);
            z2 = pos[2] + (1.0 - ACTIVATION_ACROSS_MIN);
        } else if (face == 3) {
            z1 = pos[2] + 1.005;
            z2 = z1;
            x1 = pos[0] + (1.0 - ACTIVATION_ACROSS_MAX);
            x2 = pos[0] + (1.0 - ACTIVATION_ACROSS_MIN);
        } else {
            z1 = pos[2] - 0.005;
            z2 = z1;
            x1 = pos[0] + ACTIVATION_ACROSS_MIN;
            x2 = pos[0] + ACTIVATION_ACROSS_MAX;
        }

        int r = (promptFadeRgb >> 16) & 0xFF;
        int g = (promptFadeRgb >> 8) & 0xFF;
        int b = promptFadeRgb & 0xFF;
        int fillAlpha = (int) (60.0f * promptAlpha);
        int lineAlpha = (int) (220.0f * promptAlpha);
        if (fillAlpha < 4) fillAlpha = 4;
        if (lineAlpha < 16) lineAlpha = 16;

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableAlpha();
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.depthMask(false);
        org.lwjgl.opengl.GL11.glTranslated(-camX, -camY, -camZ);

        net.minecraft.client.renderer.GlStateManager.color(r / 255.0f, g / 255.0f, b / 255.0f, fillAlpha / 255.0f);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
        org.lwjgl.opengl.GL11.glVertex3d(x1, yMin, z1);
        org.lwjgl.opengl.GL11.glVertex3d(x2, yMin, z2);
        org.lwjgl.opengl.GL11.glVertex3d(x2, yMax, z2);
        org.lwjgl.opengl.GL11.glVertex3d(x1, yMax, z1);
        org.lwjgl.opengl.GL11.glEnd();

        org.lwjgl.opengl.GL11.glLineWidth(2.0f);
        net.minecraft.client.renderer.GlStateManager.color(r / 255.0f, g / 255.0f, b / 255.0f, lineAlpha / 255.0f);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
        org.lwjgl.opengl.GL11.glVertex3d(x1, yMin, z1);
        org.lwjgl.opengl.GL11.glVertex3d(x2, yMin, z2);
        org.lwjgl.opengl.GL11.glVertex3d(x2, yMax, z2);
        org.lwjgl.opengl.GL11.glVertex3d(x1, yMax, z1);
        org.lwjgl.opengl.GL11.glEnd();
        org.lwjgl.opengl.GL11.glLineWidth(1.0f);

        net.minecraft.client.renderer.GlStateManager.depthMask(true);
        net.minecraft.client.renderer.GlStateManager.enableDepth();
        net.minecraft.client.renderer.GlStateManager.enableCull();
        net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.resetColor();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (!this.isEnabled() || !isLegitTelly() || !running) return;
        autoPlaceOnPostMotion();
    }

    // ============================== packet interception ==============================

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!this.isEnabled() || !isLegitTelly()) return;
        Packet<?> packet = e.getPacket();

        // Tracks the position last confirmed sent to the server - used by the AutoPlace
        // candidate system's player-collision checks. Runs unconditionally (not gated on
        // `running`), matching the original script's always-on packet bookkeeping.
        if (packet instanceof C03PacketPlayer) {
            C03PacketPlayer posPacket = (C03PacketPlayer) packet;
            if (posPacket instanceof C03PacketPlayer.C04PacketPlayerPosition || posPacket instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                hasLastSentServerPos = true;
                lastSentServerPosX = posPacket.getPositionX();
                lastSentServerPosY = posPacket.getPositionY();
                lastSentServerPosZ = posPacket.getPositionZ();
            }
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) packet;
            if (placement.getPlacedBlockDirection() == 255) {
                if (shouldCancelAutoPlaceUseItem()) {
                    suppressUse();
                    e.setCanceled(true);
                    return;
                }
            } else if (mc.thePlayer != null && mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock) {
                totalC08Counter++;
                if (!placingViaModule) manualC08InWindow = true;
            }
        }

        if (isDropProtected() && packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging digging = (C07PacketPlayerDigging) packet;
            String status = digging.getStatus() == null ? "" : digging.getStatus().name();
            if (status.contains("DROP")) {
                e.setCanceled(true);
                return;
            }
        }
        if (!running) return;

        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity interaction = (C02PacketUseEntity) packet;
            if (interaction.getAction() == C02PacketUseEntity.Action.ATTACK) {
                e.setCanceled(true);
                return;
            }
        }
        if (packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging digging = (C07PacketPlayerDigging) packet;
            String status = digging.getStatus() == null ? "" : digging.getStatus().name();
            if (status.contains("DESTROY")) {
                e.setCanceled(true);
                return;
            }
        }
        if (packet instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction action = (C0BPacketEntityAction) packet;
            if (action.getAction() == C0BPacketEntityAction.Action.START_SNEAKING) {
                e.setCanceled(true);
                return;
            }
        }

        int[] placedTarget = null;
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) packet;
            if (placement.getPlacedBlockDirection() != 255 && placement.getPosition() != null) {
                BlockPos pos = placement.getPosition();
                placedTarget = offsetPos(new int[]{pos.getX(), pos.getY(), pos.getZ()}, placement.getPlacedBlockDirection());
                if (!isStraightTellyTarget(placedTarget)) {
                    cancelledGhostBlocks.add(posKey(placedTarget));
                    e.setCanceled(true);
                    return;
                }
            }
        }

        // Straight-lane validation gates Telly's own bridge-cycle placements; AutoPlace's
        // own placements (via attemptPlacement()) are validated separately before sending.
        if (placedTarget != null) {
            cancelledGhostBlocks.remove(posKey(placedTarget));
            latestStraightPlacedPos = new int[]{placedTarget[0], placedTarget[1], placedTarget[2]};
            if (firstTellyPlacementPending && setupTick < 0) {
                firstTellyPlacementPending = false;
                adaptiveAimValid = false;
                adaptiveAimUpdatedAt = 0L;
            }
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!this.isEnabled() || !isLegitTelly()) return;
        Packet<?> packet = e.getPacket();

        if (running && packet instanceof S08PacketPlayerPosLook) {
            stopAutomation(true);
            return;
        }
        if (packet instanceof S23PacketBlockChange && !cancelledGhostBlocks.isEmpty()) {
            S23PacketBlockChange change = (S23PacketBlockChange) packet;
            BlockPos pos = change.getBlockPosition();
            if (pos != null) {
                cancelledGhostBlocks.remove(posKey(new int[]{pos.getX(), pos.getY(), pos.getZ()}));
            }
        }
    }

    // ========================================================================================
    // AutoPlace candidate-resolution engine, ported from the original script.
    //
    // NOTE on scope: several methods present in the original script (findStraightGroundException-
    // Candidate, findPreviousBlockAirborneFallback, findStraightLegacyLaneFallback, findLegacy-
    // BelowPlacement + its whole "Legacy"/diagonal/support family, findBelowPlacementForSupport,
    // getBelowTargets, and everything ONLY reachable through those) were verified - by checking
    // every call site in the original 2991-line file - to be dead code: never called from any
    // reachable path starting at the real entry points (autoPlaceOnPreUpdate/autoPlaceOnRenderTick).
    // They're omitted here. This isn't a shortcut: omitting genuinely unreachable code changes
    // nothing about the script's actual behavior, since the original never executed it either.
    //
    // NOTE on simplification: the original used a `bridge` shared key-value store to cooperate
    // with OTHER scripts (checking "ScaffoldRunning", claiming "PlacementArbiterTick"/"Owner").
    // That mechanism doesn't exist for native modules and nothing else in this codebase needs to
    // cooperate with Telly's placement timing, so claimExperimentalPlacementTick() below always
    // succeeds and the "ScaffoldRunning" check is dropped. If you have another module doing its
    // own block placement, this is the seam to add real coordination back in.
    //
    // NOTE on simplification: `render.getRotations()` (a third-person free-look camera override,
    // used only as a fallback inside getCursorLookVec) has no confirmed real equivalent I could
    // verify against this codebase, so getCursorLookVec always falls through to the player's own
    // rotationYaw/rotationPitch. This only matters if you use Telly while in a free-look camera
    // mod/mode - normal first-person play is unaffected.
    // ========================================================================================

    boolean useExtendedSearch() {
        return true;
    }

    void resetControllerState() {
        currentClientTick = Integer.MIN_VALUE;
        placementEvaluationTick = Integer.MIN_VALUE;
        lastPlacementAttemptTick = Integer.MIN_VALUE;
        lastSuccessfulPlaceTick = Integer.MIN_VALUE;
        forceSuppressTick = Integer.MIN_VALUE;
        totalC08Counter = 0L;
        c08CounterAtTickBoundary = 0L;
        hasLastSentServerPos = false;
        clearCachedCandidate();
        lastPlacedPos = null;
        lastSupportPos = null;
        lastSupportFace = -1;
        rejectedTargets.clear();
        forcedModeCheck = 0;
        useSuppressed = false;
        silentPitchActive = false;
        placingViaModule = false;
        manualC08InWindow = false;
    }

    // ---- entry points ----

    void autoPlaceOnPreUpdate() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        syncPlacementTick(player);
        if (placementEvaluationTick != currentClientTick) {
            placementEvaluationTick = currentClientTick;
            processAutoPlaceTick(player);
        }
    }

    void syncPlacementTick(EntityPlayerSP player) {
        int tick = placementTick(player);
        if (tick == currentClientTick) return;
        currentClientTick = tick;
        candidateResolvedThisTick = false;
        silentPitchActive = false;
        updateStandingFloorY(player);
    }

    void updateStandingFloorY(EntityPlayerSP player) {
        if (player == null || !player.onGround) return;
        tellyFloorY = floor(player.posY) - 1;
    }

    void autoPlaceOnPostMotion() {
        c08CounterAtTickBoundary = totalC08Counter;
        manualC08InWindow = false;
    }

    void autoPlaceOnRenderTick() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !isAutoPlaceActiveWindow(player)) return;
        ItemStack heldStack = player.getHeldItem();
        if (!isUsableBlockStack(heldStack)) return;

        float basePitch = sanitizePitch(player.rotationPitch, player.rotationPitch);
        Object[] candidate = resolveCandidateWithOffCursorSilentPitch(player, player.rotationYaw, basePitch, heldStack);
        if (candidate != null) {
            silentPitch = sanitizePitch(candidatePitch(candidate), basePitch);
            silentPitchActive = true;
            suppressUse();
        }
    }

    void processAutoPlaceTick(EntityPlayerSP player) {
        pruneRejectedTargets();

        if (lastPlacedPos != null && !isSupportAvailable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2])) {
            lastPlacedPos = null;
            lastSupportPos = null;
            lastSupportFace = -1;
        }

        if (!isAutoPlaceActiveWindow(player)) {
            clearCachedCandidate();
            if (useSuppressed) restoreUseToPhysicalState();
            return;
        }

        ItemStack heldStack = player.getHeldItem();
        if (!isUsableBlockStack(heldStack)) {
            clearCachedCandidate();
            if (useSuppressed) restoreUseToPhysicalState();
            return;
        }
        if (!isBlockBelowPlayerReplaceable(player)) {
            clearCachedCandidate();
            if (useSuppressed) restoreUseToPhysicalState();
            return;
        }

        float yaw = player.rotationYaw;
        float basePitch = sanitizePitch(player.rotationPitch, player.rotationPitch);
        Object[] candidate = resolveCandidateWithOffCursorSilentPitch(player, yaw, basePitch, heldStack);
        if (candidate != null) {
            silentPitch = sanitizePitch(candidatePitch(candidate), basePitch);
            silentPitchActive = true;
            suppressUse();
        } else if (useSuppressed && !placedInCurrentWindow() && lastPlacementAttemptTick != currentClientTick) {
            restoreUseToPhysicalState();
        }

        if (placedInCurrentWindow() || lastPlacementAttemptTick == currentClientTick) {
            suppressUse();
            return;
        }
        if (candidate == null) {
            clearCachedCandidate();
            return;
        }
        if (!claimExperimentalPlacementTick()) {
            clearCachedCandidate();
            return;
        }

        lastPlacementAttemptTick = currentClientTick;
        if (attemptPlacement(player, candidate, heldStack)) return;
        if (placedInCurrentWindow()) return;

        float retryYaw = player.rotationYaw;
        float retryPitch = player.rotationPitch;
        clearCachedCandidate();
        Object[] retryCandidate = findBelowPlacement(player, retryYaw, retryPitch, heldStack, System.currentTimeMillis() + (useExtendedSearch() ? 4L : 2L));
        cacheCandidate(retryCandidate, retryYaw, retryPitch);
        if (retryCandidate != null) {
            silentPitch = sanitizePitch(candidatePitch(retryCandidate), retryPitch);
            silentPitchActive = true;
            attemptPlacement(player, retryCandidate, heldStack);
        }
    }

    // Always succeeds - see the class-level NOTE on the bridge-arbitration simplification.
    boolean claimExperimentalPlacementTick() {
        return true;
    }

    boolean attemptPlacement(EntityPlayerSP player, Object[] candidate, ItemStack heldStack) {
        if (candidate == null) return false;
        int[] placedPos = candidatePlacedPos(candidate);
        int[] supportPos = candidateSupportPos(candidate);
        int face = candidateFace(candidate);
        if (placedPos == null || supportPos == null || face <= 0) return false;
        if (!isStraightTellyTarget(placedPos)) return false;
        if (!isBlockBelowPlayerReplaceable(player)) return false;
        if (!isUsableBlockStack(player.getHeldItem())) return false;
        if (placedInCurrentWindow()) return false;

        float placementPitch = sanitizePitch(candidatePitch(candidate), aimPitch(player));
        Object[] prePlaceHit = resolveVerifiedHit(aimYaw(player), placementPitch, supportPos, face, placedPos);
        if (prePlaceHit == null) return false;
        if (cancelledGhostBlocks.contains(posKey(supportPos))) return false;
        if (!isReplaceable(placedPos[0], placedPos[1], placedPos[2])) return false;
        if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return false;
        if (doesPlacementIntersectPlayer(player, placedPos)) return false;

        long counterBefore = totalC08Counter;
        Vec3 hitAbs = (Vec3) prePlaceHit[2];
        net.minecraft.util.BlockPos supportBlockPos = new net.minecraft.util.BlockPos(supportPos[0], supportPos[1], supportPos[2]);
        net.minecraft.util.EnumFacing placeFacing = net.minecraft.util.EnumFacing.getFront(face);
        placingViaModule = true;
        // hitVec must be ABSOLUTE world coordinates here, not block-relative - confirmed
        // against every other real usage of onPlayerRightClick in this codebase (Clutch.java,
        // LadderClutch.java) and ScriptDefaults.placeBlock, all of which pass mop.hitVec
        // straight through with no subtraction. An earlier version of this line incorrectly
        // subtracted supportPos first.
        boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(),
                supportBlockPos, placeFacing, hitAbs);
        placingViaModule = false;
        boolean packetSent = totalC08Counter > counterBefore;

        if (!placed && !packetSent) return false;
        if (!packetSent) {
            markRejectedTarget(placedPos);
            return false;
        }

        lastPlacedPos = placedPos;
        lastSupportPos = supportPos;
        if (runTopY == Integer.MIN_VALUE || placedPos[1] > runTopY) runTopY = placedPos[1];
        dropBackfillEntry(placedPos);
        if (runTopY != Integer.MIN_VALUE && placedPos[1] < runTopY) {
            queueBackfill(new int[]{placedPos[0], runTopY, placedPos[2]});
        }
        recordLanePlacement(player, placedPos);
        lastSupportFace = face;
        lastSuccessfulPlaceTick = currentClientTick;
        forceSuppressTick = currentClientTick;
        mc.thePlayer.swingItem();
        return true;
    }

    Object[] resolveVerifiedHit(float yaw, float pitch, int[] expectedSupport, int expectedFace, int[] expectedPlaced) {
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] tracedSupport = (int[]) traced[0];
        int tracedFace = (Integer) traced[1];
        if (!posEquals(tracedSupport, expectedSupport) || tracedFace != expectedFace) return null;
        int[] tracedPlaced = offsetPos(tracedSupport, tracedFace);
        if (!posEquals(tracedPlaced, expectedPlaced)) return null;
        return traced;
    }

    // ---- candidate cache / resolution ----

    Object[] resolveCandidateWithOffCursorSilentPitch(EntityPlayerSP player, float yaw, float basePitch, ItemStack heldStack) {
        float safeBasePitch = sanitizePitch(basePitch, aimPitch(player));
        Object[] previousCandidate = cachedCandidate;
        Object[] baseCandidate = resolveCandidateForCurrentTick(player, yaw, safeBasePitch, heldStack);
        if (baseCandidate == null) {
            if (previousCandidate != null) {
                float previousBlockPitch = getBlockDerivedSilentPitch(player, previousCandidate, safeBasePitch);
                Object[] recovered = resolveCandidateForCurrentTick(player, yaw, previousBlockPitch, heldStack);
                if (recovered != null) return recovered;
                cacheCandidate(previousCandidate, yaw, safeBasePitch);
                return previousCandidate;
            }
            return null;
        }
        if (isPlacementLookAligned(yaw, safeBasePitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidatePlacedPos(baseCandidate))) {
            return baseCandidate;
        }
        float blockPitch = getBlockDerivedSilentPitch(player, baseCandidate, safeBasePitch);
        if (isPlacementLookAligned(yaw, blockPitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidatePlacedPos(baseCandidate))) {
            return new Object[]{blockPitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidateHitVec(baseCandidate), candidatePlacedPos(baseCandidate)};
        }
        Object[] corrected = resolveCandidateForCurrentTick(player, yaw, blockPitch, heldStack);
        if (corrected != null && posEquals(candidatePlacedPos(baseCandidate), candidatePlacedPos(corrected))) {
            return corrected;
        }
        cacheCandidate(baseCandidate, yaw, safeBasePitch);
        return baseCandidate;
    }

    Object[] resolveCandidateForCurrentTick(EntityPlayerSP player, float yaw, float pitch, ItemStack heldStack) {
        float safePitch = sanitizePitch(pitch, aimPitch(player));
        if (hasCachedCandidateForCurrentTick(yaw, safePitch)) return cachedCandidate;
        Object[] candidate = findBelowPlacement(player, yaw, safePitch, heldStack, System.currentTimeMillis() + (useExtendedSearch() ? 8L : 4L));
        cacheCandidate(candidate, yaw, safePitch);
        return candidate;
    }

    float getBlockDerivedSilentPitch(EntityPlayerSP player, Object[] candidate, float fallbackPitch) {
        if (candidate == null) return sanitizePitch(fallbackPitch, fallbackPitch);
        Vec3 hitVec = candidateHitVec(candidate);
        if (hitVec != null) {
            Float derived = computePitchToHitVec(player, hitVec);
            if (derived != null) return sanitizePitch(derived, fallbackPitch);
        }
        return sanitizePitch(candidatePitch(candidate), fallbackPitch);
    }

    void cacheCandidate(Object[] candidate, float yaw, float pitch) {
        cachedCandidate = candidate;
        cachedCandidateTick = currentClientTick;
        cachedCandidateYaw = yaw;
        cachedCandidatePitch = pitch;
        candidateResolvedThisTick = candidate != null;
    }

    boolean hasCachedCandidateForCurrentTick(float yaw, float pitch) {
        if (cachedCandidateTick != currentClientTick || !candidateResolvedThisTick || cachedCandidate == null) return false;
        if (Float.isNaN(cachedCandidateYaw) || Float.isNaN(cachedCandidatePitch)) return false;
        return Math.abs(wrapAngle(yaw - cachedCandidateYaw)) <= 0.75f && Math.abs(pitch - cachedCandidatePitch) <= 0.75f;
    }

    void clearCachedCandidate() {
        cachedCandidate = null;
        cachedCandidateTick = Integer.MIN_VALUE;
        cachedCandidateYaw = Float.NaN;
        cachedCandidatePitch = Float.NaN;
        candidateResolvedThisTick = false;
    }

    // ---- candidate search (live paths only - see class-level NOTE on dead code) ----

    Object[] findBackfillPlacement(EntityPlayerSP player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (System.currentTimeMillis() >= deadlineMs) return null;
        if (!isBackfillReady(player)) return null;
        dbgBfTry++;
        backfillAttemptActive = true;
        Object[] candidate;
        try {
            candidate = findPitchPlacementForTarget(player, yaw, currentPitch, backfillReadyPos, heldStack, null, -1, deadlineMs, false, isDiagonalMovementContext(player));
        } finally {
            backfillAttemptActive = false;
        }
        if (candidate != null) dbgBfHit++;
        return candidate;
    }

    Object[] findBelowPlacement(EntityPlayerSP player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (System.currentTimeMillis() >= deadlineMs) return null;

        Object[] backfillCandidate = findBackfillPlacement(player, yaw, currentPitch, heldStack, deadlineMs);
        if (backfillCandidate != null) return backfillCandidate;

        Object[] cursorRayCandidate = findDirectCursorRayPlacement(player, yaw, currentPitch, heldStack);
        if (cursorRayCandidate != null) return cursorRayCandidate;

        int currentY = getCurrentBelowTargetY(player);
        int strictY = getStrictBelowTargetY(player);
        int previousY = getPreviousBelowTargetY(player);
        int[] feetPos = getFeetBelowTargetAtY(player, currentY);

        List<int[]> targets = new ArrayList<>();
        addBelowTarget(player, targets, feetPos);
        addBelowTarget(player, targets, offsetPos(feetPos, facingFromYaw(yaw)));
        for (int dy = 0; dy <= 2; dy++) {
            int targetY = dy == 0 ? currentY : dy == 1 ? strictY : previousY;
            if (targetY == Integer.MIN_VALUE || (dy == 1 && targetY == currentY) || (dy == 2 && (targetY == currentY || targetY == strictY))) continue;
            addBelowTarget(player, targets, new int[]{feetPos[0], targetY, feetPos[2]});
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                addBelowTarget(player, targets, new int[]{feetPos[0] + dx, currentY, feetPos[2] + dz});
                if (strictY != currentY) addBelowTarget(player, targets, new int[]{feetPos[0] + dx, strictY, feetPos[2] + dz});
                if (previousY != currentY && previousY != strictY) addBelowTarget(player, targets, new int[]{feetPos[0] + dx, previousY, feetPos[2] + dz});
            }
        }
        if (!player.onGround) {
            addBelowTarget(player, targets, getMotionBelowTargetAtY(player, currentY, 1.0));
            if (previousY != currentY && previousY != strictY) {
                addBelowTarget(player, targets, getMotionBelowTargetAtY(player, previousY, 1.0));
            }
        }

        Object[] bestCandidate = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int[] targetPos : targets) {
            if (System.currentTimeMillis() >= deadlineMs) return null;
            if (!isPlacementTargetAvailable(player, targetPos)) continue;
            Object[] candidate = findPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, null, -1, deadlineMs, false, true);
            if (candidate == null) continue;
            double score = scorePlacementCandidate(player, currentPitch, candidatePitch(candidate), candidateFace(candidate), 0.5, 0.5);
            if (score < bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }
        return bestCandidate;
    }

    Object[] findDirectCursorRayPlacement(EntityPlayerSP player, float yaw, float pitch, ItemStack heldStack) {
        if (!isUsableBlockStack(heldStack)) return null;
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] supportPos = (int[]) traced[0];
        int face = (Integer) traced[1];
        if (face == 0) return null;
        int[] targetPos = offsetPos(supportPos, face);
        if (!isPlacementTargetAvailable(player, targetPos)) return null;
        if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return null;
        if (shouldRejectStraightSideSwitch(player, targetPos, face)) return null;
        float tracedPitch = clampFloat(pitch, -89.0f, 89.0f);
        return new Object[]{tracedPitch, supportPos, face, (Vec3) traced[2], targetPos};
    }

    Object[] findPitchPlacementForTarget(EntityPlayerSP player, float yaw, float currentPitch, int[] targetPos, ItemStack heldStack, int[] preferredSupportPos, int preferredSupportFace, long deadlineMs, boolean requireLookAlignment, boolean allowNonCursorTarget) {
        if (System.currentTimeMillis() >= deadlineMs || targetPos == null) return null;
        boolean effectiveAllowNonCursorTarget = allowNonCursorTarget || shouldAllowPlayerOneNonCursorTarget(player, targetPos);
        if (!effectiveAllowNonCursorTarget && !isCursorOrBelowPlayerTarget(player, targetPos, yaw, currentPitch)) return null;
        if (!isPlacementTargetAvailable(player, targetPos)) return null;

        Object[] bestCandidate = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int placeFace : getAllowedPlaceFacesForContext(player, yaw)) {
            if (System.currentTimeMillis() >= deadlineMs) break;
            if (shouldRejectStraightSideSwitch(player, targetPos, placeFace)) continue;
            int[] supportPos = offsetPos(targetPos, opposite(placeFace));
            if (preferredSupportPos != null && !posEquals(supportPos, preferredSupportPos)) continue;
            if (preferredSupportFace >= 0 && placeFace != preferredSupportFace) continue;
            if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) continue;
            if (!isWithinReach(player, supportPos)) continue;

            double[] hitOffsets = FACE_HIT_OFFSETS_EXT;
            for (double primaryOffset : hitOffsets) {
                for (double secondaryOffset : hitOffsets) {
                    if (System.currentTimeMillis() >= deadlineMs) break;
                    Vec3 hitVec = getSupportFaceHitVec(supportPos, placeFace, primaryOffset, secondaryOffset);
                    Object[] candidate = buildPlacementCandidateForHitVec(player, yaw, targetPos, supportPos, placeFace, hitVec, requireLookAlignment, effectiveAllowNonCursorTarget);
                    if (candidate == null) continue;
                    double candidateScore = scorePlacementCandidate(player, currentPitch, candidatePitch(candidate), placeFace, primaryOffset, secondaryOffset);
                    if (candidateScore < bestScore) {
                        bestScore = candidateScore;
                        bestCandidate = candidate;
                    }
                }
            }
        }
        if (bestCandidate == null && preferredSupportPos != null && preferredSupportFace >= 0) {
            return findRayAlignedPitchCandidate(yaw, currentPitch, targetPos, preferredSupportPos, preferredSupportFace, deadlineMs);
        }
        return bestCandidate;
    }

    Object[] findRayAlignedPitchCandidate(float yaw, float currentPitch, int[] targetPos, int[] supportPos, int placeFace, long deadlineMs) {
        float clampedBasePitch = clampFloat(currentPitch, 40.0f, 89.0f);
        for (int offset = 0; offset <= 49; offset++) {
            if (System.currentTimeMillis() >= deadlineMs) return null;
            float upPitch = clampedBasePitch + offset;
            if (upPitch <= 89.0f) {
                Object[] candidate = tryRayAlignedPitch(yaw, upPitch, targetPos, supportPos, placeFace);
                if (candidate != null) return candidate;
            }
            if (offset == 0) continue;
            float downPitch = clampedBasePitch - offset;
            if (downPitch >= 40.0f) {
                Object[] candidate = tryRayAlignedPitch(yaw, downPitch, targetPos, supportPos, placeFace);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    Object[] tryRayAlignedPitch(float yaw, float pitch, int[] targetPos, int[] supportPos, int placeFace) {
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] tracedSupport = (int[]) traced[0];
        int tracedFace = (Integer) traced[1];
        if (!posEquals(tracedSupport, supportPos) || tracedFace != placeFace) return null;
        int[] tracedPlaced = offsetPos(tracedSupport, tracedFace);
        if (!posEquals(tracedPlaced, targetPos)) return null;
        return new Object[]{pitch, tracedSupport, tracedFace, (Vec3) traced[2], tracedPlaced};
    }

    double scorePlacementCandidate(EntityPlayerSP player, float currentPitch, float candidatePitchValue, int placeFace, double primaryOffset, double secondaryOffset) {
        double pitchPenalty = Math.abs(wrapAngle(candidatePitchValue - currentPitch));
        double centerPenalty = Math.abs(primaryOffset - 0.5) + Math.abs(secondaryOffset - 0.5);
        double facePenalty = placeFace == 1 ? 0.0 : 0.35;
        double straightSidePenalty = getStraightSideSwitchPenalty(placeFace);
        return pitchPenalty + centerPenalty * 2.0 + facePenalty + straightSidePenalty;
    }

    double getStraightSideSwitchPenalty(int placeFace) {
        if (lastSupportFace < 2) return 0.0;
        if (placeFace == lastSupportFace) return 0.0;
        return 0.8;
    }

    boolean shouldRejectStraightSideSwitch(EntityPlayerSP player, int[] targetPos, int placeFace) {
        if (targetPos == null) return false;
        if (placeFace < 2 || lastSupportFace < 2 || placeFace == lastSupportFace) return false;
        if (isNearStraightSupportEdge(player)) return false;
        int[] laneSupportPos = offsetPos(targetPos, opposite(lastSupportFace));
        return isSupportAvailable(laneSupportPos[0], laneSupportPos[1], laneSupportPos[2]) && isWithinReach(player, laneSupportPos);
    }

    Object[] buildPlacementCandidateForHitVec(EntityPlayerSP player, float yaw, int[] targetPos, int[] supportPos, int placeFace, Vec3 hitVec, boolean requireLookAlignment, boolean allowNonCursorTarget) {
        if (hitVec == null) return null;
        int[] offsetTarget = offsetPos(supportPos, placeFace);
        if (!posEquals(offsetTarget, targetPos)) return null;
        if (!isStrictOneBelowPlayer(player, offsetTarget)) return null;
        Float pitch = computePitchToHitVec(player, hitVec);
        if (pitch == null) return null;
        if (!isPlacementLookAligned(yaw, pitch, supportPos, placeFace, targetPos)) return null;
        if (!(allowNonCursorTarget || isDiagonalMovementContext(player) || isSupportFaceVisible(player, supportPos, placeFace, hitVec))) return null;
        return new Object[]{pitch, supportPos, placeFace, hitVec, offsetTarget};
    }

    int[] getAllowedPlaceFacesForContext(EntityPlayerSP player, float yaw) {
        return ALLOWED_PLACE_FACES_EXT;
    }

    boolean isPlacementLookAligned(float yaw, float pitch, int[] supportPos, int placeFace, int[] targetPos) {
        if (supportPos == null || placeFace < 0 || targetPos == null) return false;
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return false;
        if (!posEquals((int[]) traced[0], supportPos) || (Integer) traced[1] != placeFace) return false;
        int[] tracedOffset = offsetPos((int[]) traced[0], (Integer) traced[1]);
        return posEquals(tracedOffset, targetPos);
    }

    boolean isSupportFaceVisible(EntityPlayerSP player, int[] supportPos, int placeFace, Vec3 hitVec) {
        Vec3 eyes = getEyes(player);
        double dx = hitVec.xCoord - eyes.xCoord;
        double dy = hitVec.yCoord - eyes.yCoord;
        double dz = hitVec.zCoord - eyes.zCoord;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-4) return false;
        float traceYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float tracePitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        Object[] traced = rayCast(traceYaw, tracePitch);
        if (traced == null) return false;
        return posEquals((int[]) traced[0], supportPos) && (Integer) traced[1] == placeFace;
    }

    boolean isWithinReach(EntityPlayerSP player, int[] pos) {
        if (pos == null) return false;
        Vec3 eyes = getEyes(player);
        double cx = Math.max(pos[0], Math.min(eyes.xCoord, pos[0] + 1.0));
        double cy = Math.max(pos[1], Math.min(eyes.yCoord, pos[1] + 1.0));
        double cz = Math.max(pos[2], Math.min(eyes.zCoord, pos[2] + 1.0));
        double dx = eyes.xCoord - cx;
        double dy = eyes.yCoord - cy;
        double dz = eyes.zCoord - cz;
        double r = reach();
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    // ---- Y-level / target helpers ----

    int[] getFeetBelowTargetAtY(EntityPlayerSP player, int targetY) {
        return new int[]{floor(player.posX), targetY, floor(player.posZ)};
    }

    int[] getMotionBelowTargetAtY(EntityPlayerSP player, int targetY, double multiplier) {
        return new int[]{floor(player.posX + player.motionX * multiplier), targetY, floor(player.posZ + player.motionZ * multiplier)};
    }

    void addBelowTargetIfUnique(EntityPlayerSP player, List<int[]> targets, int[] candidate) {
        if (candidate == null || !isStrictOneBelowPlayer(player, candidate)) return;
        for (int[] existing : targets) {
            if (posEquals(existing, candidate)) return;
        }
        targets.add(candidate);
    }

    void addBelowTarget(EntityPlayerSP player, List<int[]> targets, int[] candidate) {
        addBelowTargetIfUnique(player, targets, candidate);
    }

    boolean isPlacementTargetAvailable(EntityPlayerSP player, int[] pos) {
        return isBasePlacementTargetAvailable(player, pos) && isStrictOneBelowPlayer(player, pos);
    }

    boolean isBasePlacementTargetAvailable(EntityPlayerSP player, int[] pos) {
        return pos != null
                && isStraightTellyTarget(pos)
                && !isRejectedTarget(pos)
                && !doesPlacementIntersectPlayer(player, pos)
                && isReplaceable(pos[0], pos[1], pos[2]);
    }

    boolean isStrictOneBelowPlayer(EntityPlayerSP player, int[] pos) {
        if (pos == null) return false;
        if (!strictHeightAccepts(player, pos)) {
            dbgRejStrict++;
            return false;
        }
        return true;
    }

    boolean isQueuedBackfill(int[] pos) {
        if (!backfillGapEnabled() || pos == null) return false;
        for (int[] queued : backfillQueue) {
            if (posEquals(queued, pos)) return true;
        }
        return false;
    }

    boolean upwardPhaseWindow() {
        return cyclePhase <= 13;
    }

    boolean strictHeightAccepts(EntityPlayerSP player, int[] pos) {
        if (backfillAttemptActive && isQueuedBackfill(pos)) return true;
        int targetY = pos[1];
        int currentY = getCurrentBelowTargetY(player);
        if (targetY == currentY) return true;
        if (targetY == getStrictBelowTargetY(player)) return true;
        int previousY = getPreviousBelowTargetY(player);
        if (previousY != Integer.MIN_VALUE && targetY == previousY) return true;
        return !flatStartActive() && upwardPhaseWindow() && isStraightAscendingContext(player) && targetY == currentY + 1;
    }

    double getStableBelowReferenceY(EntityPlayerSP player) {
        double referenceY = player.posY;
        if (!player.onGround && player.motionY > -0.12 && player.motionY <= 0.0) {
            referenceY = Math.max(referenceY, player.lastTickPosY);
        }
        return referenceY;
    }

    int getStrictBelowTargetY(EntityPlayerSP player) {
        if (isDiagonalMovementContext(player)) return getCurrentBelowTargetY(player);
        double projectedY = getStableBelowReferenceY(player);
        if (!player.onGround && player.motionY < -0.12) {
            projectedY = player.posY + player.motionY * 0.75;
        }
        return floor(projectedY) - 1;
    }

    int getCurrentBelowTargetY(EntityPlayerSP player) {
        return floor(getStableBelowReferenceY(player)) - 1;
    }

    int getPreviousBelowTargetY(EntityPlayerSP player) {
        return floor(player.lastTickPosY) - 1;
    }

    boolean isStraightAscendingContext(EntityPlayerSP player) {
        if (getConditionModeCheck(player) != 1) return false;
        return player.motionY > 0.0 || player.posY > player.lastTickPosY + 1.0E-4;
    }

    boolean isRejectedTarget(int[] pos) {
        Integer rejectedAtTick = rejectedTargets.get(posKey(pos));
        if (rejectedAtTick == null) return false;
        return currentClientTick - rejectedAtTick <= 4;
    }

    void markRejectedTarget(int[] pos) {
        if (pos == null) return;
        dbgRejTarget++;
        rejectedTargets.put(posKey(pos), currentClientTick);
    }

    void pruneRejectedTargets() {
        if (rejectedTargets.isEmpty()) return;
        Iterator<Map.Entry<String, Integer>> iterator = rejectedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            if (currentClientTick - entry.getValue() > 4) {
                iterator.remove();
                continue;
            }
            String[] parts = entry.getKey().split(",");
            if (!isReplaceable(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))) {
                iterator.remove();
            }
        }
    }

    // ---- backfill queue: after moving forward on the lane, remembers a gap dropped behind/
    // below (runTopY vs the just-placed Y) and tries to fill it in on a later tick ----

    String backfillBlockReason(EntityPlayerSP player, int[] pos) {
        if (!backfillGapEnabled()) return "off";
        if (player == null || pos == null) return "nil";
        if (isBackfillOutOfRange(player, pos)) return "far";
        if (!isReplaceable(pos[0], pos[1], pos[2])) return "occ";
        if (!isStraightTellyTarget(pos)) return "lane";
        if (isRejectedTarget(pos)) return "rej";
        if (doesPlacementIntersectPlayer(player, pos)) return "self";
        return "ok";
    }

    String describeBackfillQueue(EntityPlayerSP player) {
        if (backfillQueue.isEmpty()) return "empty";
        String text = "";
        for (int i = 0; i < backfillQueue.size(); i++) {
            int[] queued = backfillQueue.get(i);
            if (i > 0) text = text + ",";
            text = text + queued[0] + "/" + queued[1] + "/" + queued[2] + ":" + backfillBlockReason(player, queued);
        }
        return backfillQueue.size() + ":" + text + (backfillReadyPos == null ? " ready=no" : " ready=yes");
    }

    boolean isBackfillOutOfRange(EntityPlayerSP player, int[] pos) {
        if (player == null || pos == null) return false;
        double dx = (pos[0] + 0.5) - player.posX;
        double dz = (pos[2] + 0.5) - player.posZ;
        return dx * dx + dz * dz > 25.0;
    }

    void queueBackfill(int[] pos) {
        if (pos == null) return;
        for (int[] queued : backfillQueue) {
            if (posEquals(queued, pos)) return;
        }
        backfillQueue.add(pos);
        backfillQueueTicks.add(currentClientTick);
        while (backfillQueue.size() > 6) {
            backfillQueue.remove(0);
            backfillQueueTicks.remove(0);
        }
    }

    void dropBackfillEntry(int[] pos) {
        if (pos == null) return;
        for (int i = backfillQueue.size() - 1; i >= 0; i--) {
            if (posEquals(backfillQueue.get(i), pos)) {
                backfillQueue.remove(i);
                backfillQueueTicks.remove(i);
            }
        }
    }

    boolean isBackfillReady(EntityPlayerSP player) {
        backfillReadyPos = null;
        if (!backfillGapEnabled() || player == null) return false;

        for (int i = backfillQueue.size() - 1; i >= 0; i--) {
            int[] queued = backfillQueue.get(i);
            boolean stale = currentClientTick - backfillQueueTicks.get(i) > 60;
            if (stale || isBackfillOutOfRange(player, queued) || !isReplaceable(queued[0], queued[1], queued[2])) {
                backfillQueue.remove(i);
                backfillQueueTicks.remove(i);
            }
        }

        for (int i = 0; i < backfillQueue.size(); i++) {
            int[] queued = backfillQueue.get(i);
            if (!isBasePlacementTargetAvailable(player, queued)) continue;
            backfillReadyPos = queued;
            return true;
        }
        return false;
    }

    void recordLanePlacement(EntityPlayerSP player, int[] placedPos) {
        if (placedPos == null) return;
        int previousLaneY = tellyLaneY;
        tellyLaneY = placedPos[1];
        if (player == null) return;
        int motionYHundredths = (int) Math.round(player.motionY * 100.0);
        dbgPlaced++;
        printStatus("place x=" + placedPos[0] + " z=" + placedPos[2]
                + " y=" + placedPos[1]
                + " prev=" + previousLaneY
                + " rise=" + (previousLaneY == Integer.MIN_VALUE ? 0 : placedPos[1] - previousLaneY)
                + " top=" + runTopY
                + " feet=" + getCurrentBelowTargetY(player)
                + " phase=" + cyclePhase
                + " sent=" + (silentPitchActive ? ((int) silentPitch) + "" : "cam")
                + " cam=" + ((int) player.rotationPitch)
                + " bfq=" + describeBackfillQueue(player)
                + " ground=" + player.onGround
                + " motionY=" + motionYHundredths + "/100");
    }

    // ---- cursor-ray helpers ----

    boolean isCursorOrBelowPlayerTarget(EntityPlayerSP player, int[] targetPos, float yaw, float pitch) {
        if (targetPos == null) return false;
        if (!isDiagonalMovementContext(player)) {
            int currentY = getCurrentBelowTargetY(player);
            if (posEquals(getCursorStartTargetAtY(player, currentY), targetPos)) return true;
            if (posEquals(getCursorPlacedTargetFromRay(yaw, pitch, currentY), targetPos)) return true;
            int strictY = getStrictBelowTargetY(player);
            if (strictY != currentY) {
                if (posEquals(getCursorStartTargetAtY(player, strictY), targetPos)) return true;
                if (posEquals(getCursorPlacedTargetFromRay(yaw, pitch, strictY), targetPos)) return true;
            }
            if (isCursorInsideTargetAtY(player, targetPos, currentY)) return true;
            return posEquals(getCursorTargetAtY(player, currentY), targetPos);
        }
        int strictY = getStrictBelowTargetY(player);
        if (isBelowPlayerTargetAtY(player, targetPos, strictY, yaw, pitch)) return true;
        return isBelowPlayerTargetAtY(player, targetPos, getCurrentBelowTargetY(player), yaw, pitch);
    }

    boolean isBelowPlayerTargetAtY(EntityPlayerSP player, int[] targetPos, int targetY, float yaw, float pitch) {
        for (int[] candidate : getBelowPlayerFallbackEndpoints(player, yaw, pitch, targetY)) {
            if (posEquals(targetPos, candidate)) return true;
        }
        return false;
    }

    boolean shouldAllowPlayerOneNonCursorTarget(EntityPlayerSP player, int[] targetPos) {
        if (targetPos == null || isDiagonalMovementContext(player) || player.onGround) return false;
        if (!isPlayerHitboxFullyInsideSingleBlockColumn(player)) return false;
        if (lastSupportPos == null || lastSupportFace < 0 || lastSupportFace == 0) return false;
        int[] continuationTarget = offsetPos(lastSupportPos, lastSupportFace);
        if (!posEquals(targetPos, continuationTarget)) return false;
        int targetY = targetPos[1];
        int currentY = getCurrentBelowTargetY(player);
        int strictY = getStrictBelowTargetY(player);
        if (targetY != currentY && targetY != strictY) return false;
        int[] feetBelow = getFeetBelowTargetAtY(player, targetY);
        int horizontalDistance = Math.abs(targetPos[0] - feetBelow[0]) + Math.abs(targetPos[2] - feetBelow[2]);
        return horizontalDistance <= 1;
    }

    boolean isPlayerHitboxFullyInsideSingleBlockColumn(EntityPlayerSP player) {
        double half = player.width / 2.0;
        int minX = floor(player.posX - half + 1.0E-4);
        int maxX = floor(player.posX + half - 1.0E-4);
        if (minX != maxX) return false;
        int minZ = floor(player.posZ - half + 1.0E-4);
        int maxZ = floor(player.posZ + half - 1.0E-4);
        return minZ == maxZ;
    }

    List<int[]> getBelowPlayerFallbackEndpoints(EntityPlayerSP player, float yaw, float pitch, int targetY) {
        List<int[]> endpoints = new ArrayList<>();
        if (!isDiagonalMovementContext(player)) {
            if (!player.onGround) {
                addBelowTargetIfUnique(player, endpoints, getFeetBelowTargetAtY(player, targetY));
                addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.0));
                addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.7));
            }
            addBelowTargetIfUnique(player, endpoints, getCursorStartTargetAtY(player, targetY));
            addBelowTargetIfUnique(player, endpoints, getCursorPlacedTargetFromRay(yaw, pitch, targetY));
            addBelowTargetIfUnique(player, endpoints, getCursorTargetAtY(player, targetY));
            return endpoints;
        }
        addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.0));
        addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.7));
        return endpoints;
    }

    int[] getCursorPlacedTargetFromRay(float yaw, float pitch, int targetY) {
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] offsetTarget = offsetPos((int[]) traced[0], (Integer) traced[1]);
        return offsetTarget[1] != targetY ? null : offsetTarget;
    }

    int[] getCursorStartTargetAtY(EntityPlayerSP player, int targetY) {
        Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
        Vec3 lookVec = getCursorLookVec(player);
        if (cursorPoint == null || lookVec == null) return null;
        double startX = cursorPoint.xCoord - lookVec.xCoord * 0.03;
        double startZ = cursorPoint.zCoord - lookVec.zCoord * 0.03;
        return new int[]{floor(startX), targetY, floor(startZ)};
    }

    int[] getCursorTargetAtY(EntityPlayerSP player, int targetY) {
        Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
        return cursorPoint == null ? null : new int[]{floor(cursorPoint.xCoord), targetY, floor(cursorPoint.zCoord)};
    }

    Vec3 getCursorIntersectionAtY(EntityPlayerSP player, int targetY) {
        Vec3 eyes = getEyes(player);
        Vec3 lookVec = getCursorLookVec(player);
        if (lookVec == null || Math.abs(lookVec.yCoord) < 1.0E-4) return null;
        double t = (targetY - eyes.yCoord) / lookVec.yCoord;
        if (t <= 0.0) return null;
        return new Vec3(eyes.xCoord + lookVec.xCoord * t, targetY + 0.5, eyes.zCoord + lookVec.zCoord * t);
    }

    // See class-level NOTE: render.getRotations() (free-look camera override) has no
    // confirmed real equivalent, so this always uses the player's own rotation.
    Vec3 getCursorLookVec(EntityPlayerSP player) {
        return Utils.getLookVec(player.rotationYaw, player.rotationPitch);
    }

    boolean isCursorInsideTargetAtY(EntityPlayerSP player, int[] targetPos, int targetY) {
        if (targetPos == null || targetPos[1] != targetY) return false;
        Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
        if (cursorPoint == null) return false;
        double x = cursorPoint.xCoord;
        double z = cursorPoint.zCoord;
        return x >= targetPos[0] - 1.0E-6 && x <= targetPos[0] + 1.0 + 1.0E-6 && z >= targetPos[2] - 1.0E-6 && z <= targetPos[2] + 1.0 + 1.0E-6;
    }

    // ---- player-collision helpers ----

    boolean doesPlacementIntersectPlayer(EntityPlayerSP player, int[] placePos) {
        if (placePos == null) return false;
        if (isInsideAnyPlayerPositionCell(player, placePos)) return true;

        double half = player.width / 2.0;
        double height = player.height;
        if (boxIntersectsBlock(player.posX - half, player.posY, player.posZ - half, player.posX + half, player.posY + height, player.posZ + half, placePos)) return true;
        if (isBlockPosInsideBounds(placePos, player.posX - half, player.posY, player.posZ - half, player.posX + half, player.posY + height, player.posZ + half)) return true;

        if (!shouldUseHistoricalPlayerCollisionChecks(player, placePos)) return false;

        if (player.lastTickPosX != player.posX || player.lastTickPosY != player.posY || player.lastTickPosZ != player.posZ) {
            if (boxIntersectsBlock(player.lastTickPosX - half, player.lastTickPosY, player.lastTickPosZ - half, player.lastTickPosX + half, player.lastTickPosY + height, player.lastTickPosZ + half, placePos)) return true;
            if (isBlockPosInsideBounds(placePos, player.lastTickPosX - half, player.lastTickPosY, player.lastTickPosZ - half, player.lastTickPosX + half, player.lastTickPosY + height, player.lastTickPosZ + half)) return true;
        }
        if (hasLastSentServerPos && (lastSentServerPosX != player.posX || lastSentServerPosY != player.posY || lastSentServerPosZ != player.posZ)) {
            double sx = lastSentServerPosX, sy = lastSentServerPosY, sz = lastSentServerPosZ;
            if (boxIntersectsBlock(sx - half, sy, sz - half, sx + half, sy + height, sz + half, placePos)) return true;
            if (isBlockPosInsideBounds(placePos, sx - half, sy, sz - half, sx + half, sy + height, sz + half)) return true;
        }
        return false;
    }

    boolean boxIntersectsBlock(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int[] pos) {
        return maxX > pos[0] && minX < pos[0] + 1.0 && maxY > pos[1] && minY < pos[1] + 1.0 && maxZ > pos[2] && minZ < pos[2] + 1.0;
    }

    boolean isBlockPosInsideBounds(int[] pos, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int bMinX = floor(minX + 1.0E-4), bMaxX = floor(maxX - 1.0E-4);
        if (pos[0] < bMinX || pos[0] > bMaxX) return false;
        int bMinZ = floor(minZ + 1.0E-4), bMaxZ = floor(maxZ - 1.0E-4);
        if (pos[2] < bMinZ || pos[2] > bMaxZ) return false;
        int bMinY = floor(minY + 1.0E-4), bMaxY = floor(maxY - 1.0E-4);
        return pos[1] >= bMinY && pos[1] <= bMaxY;
    }

    boolean isInsideAnyPlayerPositionCell(EntityPlayerSP player, int[] placePos) {
        if (isInsidePlayerPositionCell(placePos, player.posX, player.posY, player.posZ)) return true;
        if (!shouldUseHistoricalPlayerCollisionChecks(player, placePos)) return false;
        if (isInsidePlayerPositionCell(placePos, player.lastTickPosX, player.lastTickPosY, player.lastTickPosZ)) return true;
        return hasLastSentServerPos && isInsidePlayerPositionCell(placePos, lastSentServerPosX, lastSentServerPosY, lastSentServerPosZ);
    }

    boolean shouldUseHistoricalPlayerCollisionChecks(EntityPlayerSP player, int[] placePos) {
        if (!player.onGround) return false;
        if (placePos == null) return true;
        return placePos[1] > getCurrentBelowTargetY(player);
    }

    boolean isInsidePlayerPositionCell(int[] placePos, double x, double y, double z) {
        int playerX = floor(x), playerY = floor(y), playerZ = floor(z);
        return placePos[0] == playerX && placePos[2] == playerZ && (placePos[1] == playerY || placePos[1] == playerY + 1);
    }

    // ---- movement-mode detection ----

    int getDetectedModeCheck(EntityPlayerSP player) {
        float forwardInput = Math.abs(mc.thePlayer.movementInput.moveForward);
        float strafeInput = Math.abs(mc.thePlayer.movementInput.moveStrafe);
        if (forwardInput >= 0.08f || strafeInput >= 0.08f) {
            return (forwardInput >= 0.08f && strafeInput >= 0.08f) ? 1 : 2;
        }
        double[] direction = getMotionDirectionComponents(player);
        if (direction == null) return 1;
        double angleDeg = Math.toDegrees(Math.atan2(direction[1], direction[0]));
        double norm90 = (angleDeg % 90.0 + 90.0) % 90.0;
        return Math.abs(norm90 - 45.0) <= 18.0 ? 2 : 1;
    }

    double[] getMotionDirectionComponents(EntityPlayerSP player) {
        double dirX = player.posX - player.lastTickPosX;
        double dirZ = player.posZ - player.lastTickPosZ;
        double speedSq = dirX * dirX + dirZ * dirZ;
        if (speedSq < 1.0E-4) {
            dirX = player.motionX;
            dirZ = player.motionZ;
            speedSq = dirX * dirX + dirZ * dirZ;
        }
        return speedSq < 1.0E-4 ? null : new double[]{dirX, dirZ};
    }

    double[] getInputDirectionComponents(float referenceYaw) {
        float forwardInput = mc.thePlayer.movementInput.moveForward;
        float strafeInput = mc.thePlayer.movementInput.moveStrafe;
        if (Math.abs(forwardInput) < 0.08f && Math.abs(strafeInput) < 0.08f) return null;
        double yawRadians = Math.toRadians(referenceYaw);
        double sinYaw = Math.sin(yawRadians), cosYaw = Math.cos(yawRadians);
        double dirX = forwardInput * -sinYaw + strafeInput * cosYaw;
        double dirZ = forwardInput * cosYaw - strafeInput * sinYaw;
        return dirX * dirX + dirZ * dirZ < 1.0E-4 ? null : new double[]{dirX, dirZ};
    }

    int getStraightForwardFacing(EntityPlayerSP player, float fallbackYaw) {
        double[] direction = getInputDirectionComponents(fallbackYaw);
        if (direction == null) direction = getMotionDirectionComponents(player);
        if (direction == null) return facingFromYaw(fallbackYaw);
        float directionYaw = (float) (Math.toDegrees(Math.atan2(direction[1], direction[0])) - 90.0);
        return facingFromYaw(directionYaw);
    }

    int getConditionModeCheck(EntityPlayerSP player) {
        return forcedModeCheck != 0 ? forcedModeCheck : getDetectedModeCheck(player);
    }

    boolean isDiagonalMovementContext(EntityPlayerSP player) {
        return getConditionModeCheck(player) == 2;
    }

    boolean isNearStraightSupportEdge(EntityPlayerSP player) {
        if (lastSupportPos == null || lastSupportFace < 2) return false;
        double localX = player.posX - lastSupportPos[0];
        double localZ = player.posZ - lastSupportPos[2];
        if (isPastStraightSupportEdgeThreshold(lastSupportFace, localX, localZ)) return true;
        if (player.motionX * player.motionX + player.motionZ * player.motionZ < 1.0E-4) return false;
        if (!isMovingTowardStraightSupportEdge(lastSupportFace, player.motionX, player.motionZ)) return false;
        return isPastStraightSupportEdgeThreshold(lastSupportFace, localX + player.motionX * 1.45, localZ + player.motionZ * 1.45);
    }

    boolean isPastStraightSupportEdgeThreshold(int supportFace, double localX, double localZ) {
        if (supportFace == 5) return localX >= 0.52;
        if (supportFace == 4) return localX <= 0.48;
        if (supportFace == 3) return localZ >= 0.52;
        if (supportFace == 2) return localZ <= 0.48;
        return false;
    }

    boolean isMovingTowardStraightSupportEdge(int supportFace, double motionX, double motionZ) {
        if (supportFace == 5) return motionX > 0.0;
        if (supportFace == 4) return motionX < 0.0;
        if (supportFace == 3) return motionZ > 0.0;
        if (supportFace == 2) return motionZ < 0.0;
        return false;
    }

    // ---- placement plumbing / primitives ----

    boolean isBlockBelowPlayerReplaceable(EntityPlayerSP player) {
        return isReplaceable(floor(player.posX), floor(player.posY) - 1, floor(player.posZ));
    }

    boolean placedInCurrentWindow() {
        return totalC08Counter > c08CounterAtTickBoundary;
    }

    boolean areAutoPlaceConditionsMet(EntityPlayerSP player) {
        return tellyAutoPlaceWindow && isUsableBlockStack(player.getHeldItem());
    }

    boolean isAutoPlaceActiveWindow(EntityPlayerSP player) {
        return isInGameContext() && areAutoPlaceConditionsMet(player) && isUsableBlockStack(player.getHeldItem());
    }

    boolean isInGameContext() {
        return mc.thePlayer != null && mc.currentScreen == null;
    }

    boolean shouldSuppressManualClicksThisTick() {
        return isInGameContext() && (lastSuccessfulPlaceTick == currentClientTick || forceSuppressTick == currentClientTick);
    }

    boolean shouldCancelAutoPlaceUseItem() {
        if (!isInGameContext()) return false;
        if (shouldSuppressManualClicksThisTick()) return true;
        return useSuppressed && silentPitchActive;
    }

    void suppressUse() {
        setKeyPressed(mc.gameSettings.keyBindUseItem, false);
        useSuppressed = true;
    }

    void restoreUseToPhysicalState() {
        setKeyPressed(mc.gameSettings.keyBindUseItem, running ? tellyAutoPlaceWindow : Mouse.isButtonDown(1));
        useSuppressed = false;
    }

    Object[] rayCast(float yaw, float pitch) {
        Vec3 eyeVec = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = Utils.getLookVec(yaw, pitch);
        double distance = reach();
        Vec3 endVec = eyeVec.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyeVec, endVec, false, false, false);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        int face = mop.sideHit.getIndex();
        if (face <= 0) return null;
        int[] supportPos = {mop.getBlockPos().getX(), mop.getBlockPos().getY(), mop.getBlockPos().getZ()};
        return new Object[]{supportPos, face, mop.hitVec};
    }

    Vec3 getEyes(EntityPlayerSP player) {
        return new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    }

    Float computePitchToHitVec(EntityPlayerSP player, Vec3 hitVec) {
        Vec3 eyes = getEyes(player);
        double dx = hitVec.xCoord - eyes.xCoord;
        double dz = hitVec.zCoord - eyes.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = hitVec.yCoord - eyes.yCoord;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return Math.max(-89.0f, Math.min(89.0f, pitch));
    }

    double reach() {
        return mc.thePlayer.capabilities.isCreativeMode ? 5.0 : 4.5;
    }

    int placementTick(EntityPlayerSP player) {
        try {
            if (ModuleManager.timer != null && ModuleManager.timer.isEnabled()) {
                return (int) (System.currentTimeMillis() / 50L);
            }
        } catch (Exception ignored) {}
        return player.ticksExisted;
    }

    float candidatePitch(Object[] candidate) { return clampFloat((Float) candidate[0], -90.0f, 90.0f); }
    int[] candidateSupportPos(Object[] candidate) { return (int[]) candidate[1]; }
    int candidateFace(Object[] candidate) { return (Integer) candidate[2]; }
    Vec3 candidateHitVec(Object[] candidate) { return (Vec3) candidate[3]; }
    int[] candidatePlacedPos(Object[] candidate) { return (int[]) candidate[4]; }

    final double[] FACE_HIT_OFFSETS_EXT = {0.5, 0.25, 0.75, 0.15, 0.85, 0.35, 0.65, 0.05, 0.95};
    final int[] ALLOWED_PLACE_FACES_EXT = {2, 3, 4, 5, 1};

    /**
     * The "Keep-y" mode of Scaffold. Keeps your Y level while bridging: you jump off the edge on your own (AutoJump behaviour) and, while you are in the
     * air or while the select key is held, blocks are placed under you (Clutch behaviour). Everything lives in this module, so it does not depend on the
     * Clutch module being enabled, configured or even present. Scaffold owns the settings and forwards its events here.
     */
    private static final class KeepYMode {
        private static final Minecraft mc = Minecraft.getMinecraft();

        private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
        private static final double HALF_WIDTH = 0.3;
        private static final double[][] CORNERS = {{-HALF_WIDTH, -HALF_WIDTH}, {HALF_WIDTH, -HALF_WIDTH}, {-HALF_WIDTH, HALF_WIDTH}, {HALF_WIDTH, HALF_WIDTH}};

        static {
            BLOCK_SCORE.put("obsidian", 0);
            BLOCK_SCORE.put("end_stone", 1);
            BLOCK_SCORE.put("planks", 2);
            BLOCK_SCORE.put("log", 2);
            BLOCK_SCORE.put("log2", 2);
            BLOCK_SCORE.put("glass", 3);
            BLOCK_SCORE.put("stained_glass", 3);
            BLOCK_SCORE.put("hardened_clay", 4);
            BLOCK_SCORE.put("stained_hardened_clay", 4);
            BLOCK_SCORE.put("stone", 5);
            BLOCK_SCORE.put("wool", 5);
        }

        /** Values used automatically while moving: index 0 = W held, 1 = W + Space held, 2 = Space held (without W). */
        private static final double[] PRESET_DELAY = {0, 0, 0};
        private static final double[] PRESET_REACH = {4.5, 4.5, 4.5};
        private static final double[] PRESET_SPEED = {40, 100, 40};
        private static final double[] PRESET_SNAPBACK_SPEED = {100, 100, 100};
        private static final double[] PRESET_MAX_DISTANCE = {0, 0, 0};
        private static final double[] PRESET_ROTATION_TOLERANCE = {20, 20, 20};

        private final Scaffold owner;
        private final SliderSetting delay;
        private final ButtonSetting autoPresets;
        private final ButtonSetting tower;
        private final ButtonSetting cancelWhenShifting;
        private final SliderSetting reach;
        private final SliderSetting speed;
        private final SliderSetting snapbackSpeed;
        private final SliderSetting maxDistance;
        private final SliderSetting rotationTolerance;
        private final ButtonSetting simulateFuturePosition;
        private final KeySetting selectKeybind;

        // air / edge jump state
        private long airStartTime;
        private boolean wasInAir;
        /** True while this module is holding the jump key down for the edge jump. */
        private boolean jumping;
        /** True while in the air and the Delay has passed, i.e. blocks should be placed. */
        private boolean clutchActive;

        // block placing state
        private BlockPos placeAtBlock;
        private EnumFacing hitSide;
        private Vec3 hitVec;
        private boolean placeQueued;
        private boolean placing;
        private boolean slotWasSwapped;
        private int prevSlot = -1;
        private int plannedSlot = -1;
        private float aimYaw;
        private float aimPitch;
        private BlockPos targetHitPos;
        private EnumFacing targetSide;
        private boolean hasAim;
        private boolean resetting;
        private BlockPos lastPlaced;
        private int clutchBlocksPlaced;

        KeepYMode(Scaffold owner) {
            this.owner = owner;
            owner.registerSetting(autoPresets = new ButtonSetting("Auto presets", true));
            owner.registerSetting(delay = new SliderSetting("Delay", "ms", 0, 0, 1000, 50));
            owner.registerSetting(tower = new ButtonSetting("Tower", false));
            owner.registerSetting(cancelWhenShifting = new ButtonSetting("Cancel when shifting", true));
            owner.registerSetting(reach = new SliderSetting("Reach", " blocks", 4.5, 0.5, 4.5, 0.1));
            owner.registerSetting(speed = new SliderSetting("Speed", 8, 0, 100, 1));
            owner.registerSetting(snapbackSpeed = new SliderSetting("Snapback Speed", 12, 0, 100, 1));
            owner.registerSetting(maxDistance = new SliderSetting("Max distance", " blocks", 10, 0, 20, 1));
            owner.registerSetting(rotationTolerance = new SliderSetting("Rotation Tolerance", "\u00B0", 25, 20, 100, 1));
            owner.registerSetting(simulateFuturePosition = new ButtonSetting("Simulate future position", true));
            owner.registerSetting(selectKeybind = new KeySetting("Select Keybind", 0));

            // hidden from the GUI: they keep their defaults (and still load from profiles) but can't be edited
            autoPresets.visible = false;
            tower.visible = false;
            reach.visible = false;
            speed.visible = false;
            snapbackSpeed.visible = false;
            maxDistance.visible = false;
            rotationTolerance.visible = false;
            simulateFuturePosition.visible = false;
            selectKeybind.visible = false;
        }

        /** Applies the mode's visibility before the GUI exists (no GUI refresh). */
        void hideSettings() {
            delay.visible = false;
            cancelWhenShifting.visible = false;
        }

        /** Shows or hides the Keep-y settings that are editable; the permanently hidden ones are never touched. */
        void setSettingsVisible(boolean visible) {
            delay.setVisible(visible, owner);
            cancelWhenShifting.setVisible(visible, owner);
        }

        void onEnable() {
            airStartTime = 0L;
            wasInAir = false;
            jumping = false;
            clutchActive = false;
            hasAim = false;
            resetting = false;
            clutchBlocksPlaced = 0;
        }

        void onDisable() {
            clutchActive = false;
            clearAim(false);
            disablePlacing(true);
            placeQueued = false;
            releaseJump();
            wasInAir = false;
        }

        // ============================== rotations + placing (Clutch behaviour) ==============================

        void onClientRotation(ClientRotationEvent e) {
            if (!Utils.nullCheck()) {
                return;
            }
            if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
                return;
            }

            updateAirState();
            runPrePlayerInteract();

            if (mc.currentScreen != null) {
                disablePlacing(false);
            }

            float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
            float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];

            if (resetting) {
                aimYaw = mc.thePlayer.rotationYaw;
                aimPitch = mc.thePlayer.rotationPitch;
                float[] smoothed = getRotationsSmoothed(baseYaw, basePitch, aimYaw, aimPitch, true);
                if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - aimYaw)) < 0.5f && Math.abs(smoothed[1] - aimPitch) < 0.5f) {
                    resetting = false;
                    restoreInputsAndAutoClicker();
                    return;
                }
                RotationHelper.get().forceMovementFix = true;
                e.setYaw(smoothed[0]);
                e.setPitch(smoothed[1]);
                return;
            }

            if (!hasAim) {
                return;
            }

            float[] smoothed = getRotationsSmoothed(baseYaw, basePitch, aimYaw, aimPitch, false);

            if (placing && targetHitPos != null) {
                MovingObjectPosition mop = RotationUtils.rayCastBlock(getReach(), smoothed[0], smoothed[1]);
                if (mop != null && targetHitPos.equals(mop.getBlockPos()) && targetSide == mop.sideHit) {
                    int maxBlocks = (int) getMaxDistance();
                    if (maxBlocks == 0 || clutchBlocksPlaced < maxBlocks) {
                        double tolerance = getRotationTolerance();
                        if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - RotationUtils.serverRotations[0])) <= tolerance
                                && Math.abs(smoothed[1] - RotationUtils.serverRotations[1]) <= tolerance) {
                            placeAtBlock = mop.getBlockPos();
                            hitSide = mop.sideHit;
                            hitVec = mop.hitVec;
                            placeQueued = true;
                        }
                    }
                }
            }

            RotationHelper.get().forceMovementFix = true;
            e.setYaw(smoothed[0]);
            e.setPitch(smoothed[1]);
        }

        void onPreUpdate() {
            if (!Utils.nullCheck()) {
                return;
            }

            placeQueuedBlock();

            // Tower: while space is held the edge jump is paused
            if (isTowering()) {
                if (jumping) {
                    setJump(false);
                }
            }
            else {
                runEdgeJump();
            }
        }

        void onMouse(MouseEvent e) {
            if ((placing || resetting || hasAim) && e.button > -1) {
                e.setCanceled(true);
            }
        }

        /** With Tower on, holding space only jumps at an edge (instead of hopping on the spot). */
        void onPostPlayerInput(PostPlayerInputEvent e) {
            if (!Utils.nullCheck()) {
                return;
            }
            if (tower.isToggled() && isJumpKeyHeld() && !Utils.onEdge()) {
                mc.thePlayer.movementInput.jump = false;
            }
        }

        /** Tracks how long we have been airborne; blocks are placed once the Delay has passed. */
        private void updateAirState() {
            if (!mc.thePlayer.onGround) {
                long now = System.currentTimeMillis();
                if (!wasInAir) {
                    airStartTime = now;
                    wasInAir = true;
                }
                double requiredDelay = isTowering() ? 0.0 : getDelay();
                clutchActive = now - airStartTime >= requiredDelay;
            }
            else {
                wasInAir = false;
                clutchActive = false;
            }
        }

        private void placeQueuedBlock() {
            if (!placeQueued) {
                return;
            }

            placeQueued = false;
            if (placeAtBlock != null && hitSide != null && hitVec != null
                    && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), placeAtBlock, hitSide, hitVec)) {
                if (hitSide != EnumFacing.UP) {
                    clutchBlocksPlaced++;
                }
                lastPlaced = placeAtBlock;
                mc.thePlayer.swingItem();
            }
        }

        private void runPrePlayerInteract() {
            if (mc.thePlayer.onGround) {
                clutchBlocksPlaced = 0;
            }

            // blocks are placed while airborne (after the Delay) or whenever the select key is held
            boolean active = clutchActive || selectKeybind.isPressed();
            if (mc.currentScreen != null || !active) {
                clearAim(true);
                disablePlacing(false);
                return;
            }

            BlockPos below = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY) - 1,
                    MathHelper.floor_double(mc.thePlayer.posZ)
            );
            if (!canPlaceThrough(below)) {
                clearAim(true);
                disablePlacing(false);
                return;
            }

            int blockSlot = pickBlockSlot();
            if (blockSlot == -1) {
                clearAim(true);
                disablePlacing(false);
                return;
            }

            plannedSlot = blockSlot;
            AimResult target = clutchAim();
            if (target != null) {
                targetHitPos = target.ray.getBlockPos();
                targetSide = target.ray.sideHit;
                aimYaw = target.yaw;
                aimPitch = target.pitch;
                hasAim = true;
                resetting = false;
            }
            else {
                clearAim(true);
                disablePlacing(false);
                return;
            }

            if (hasAim && !placing) {
                enablePlacing();
            }

            if (placing || resetting || hasAim) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                equipPlannedSlot();
            }
        }

        private void enablePlacing() {
            if (placing) {
                return;
            }
            placing = true;
            if (!slotWasSwapped) {
                prevSlot = mc.thePlayer.inventory.currentItem;
            }
            if (ModuleManager.autoClicker != null) {
                ModuleManager.autoClicker.setSuppressed(this, true);
            }
        }

        private void disablePlacing(boolean forceRestore) {
            if (!placing && !forceRestore) {
                return;
            }

            placing = false;
            plannedSlot = -1;
            placeQueued = false;

            if ((forceRestore || !hasAim) && slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
                mc.thePlayer.inventory.currentItem = prevSlot;
                slotWasSwapped = false;
            }
            if (forceRestore) {
                prevSlot = -1;
                restoreInputsAndAutoClicker();
            }
        }

        private void clearAim(boolean allowSnapback) {
            if (slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
                mc.thePlayer.inventory.currentItem = prevSlot;
                slotWasSwapped = false;
            }
            targetHitPos = null;
            targetSide = null;
            placeAtBlock = null;
            hitSide = null;
            hitVec = null;
            placeQueued = false;
            lastPlaced = null;
            clutchBlocksPlaced = 0;
            if (allowSnapback && hasAim) {
                resetting = true;
            }
            hasAim = false;
            prevSlot = -1;
        }

        private void restoreInputsAndAutoClicker() {
            if (mc.currentScreen == null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), Mouse.isButtonDown(0));
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(1));
            }
            if (ModuleManager.autoClicker != null) {
                ModuleManager.autoClicker.setSuppressed(this, false);
            }
        }

        private AimResult clutchAim() {
            Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

            Vec3 futurePos = playerPos;
            if (simulateFuturePosition.isToggled()) {
                PredictionState prediction = PredictionState.fromPlayer();
                for (int t = 0; t < 20; t++) {
                    prediction.tick(false);
                    if (prediction.posY < playerPos.yCoord - 2 || prediction.onGround) {
                        break;
                    }
                }
                futurePos = prediction.getPos();
            }

            int feetX = MathHelper.floor_double(playerPos.xCoord);
            int feetZ = MathHelper.floor_double(playerPos.zCoord);
            int feetY = MathHelper.floor_double(playerPos.yCoord);
            int minX = feetX - 5;
            int maxX = feetX + 4;
            int minZ = feetZ - 5;
            int maxZ = feetZ + 4;
            int maxY = feetY - 1;
            int minY = feetY - 4;

            ArrayList<BlockCandidate> candidates = new ArrayList<>();
            for (int y = maxY; y >= minY; y--) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (canPlaceThrough(pos)) {
                            continue;
                        }

                        double currentDist = BlockUtils.dist2PointAABB(playerPos, pos);
                        double futureDist = BlockUtils.dist2PointAABB(futurePos, pos);
                        double score = simulateFuturePosition.isToggled() ? (currentDist * 0.3 + futureDist * 0.7) : currentDist;
                        if (pos.equals(lastPlaced)) {
                            score *= 0.95;
                        }
                        candidates.add(new BlockCandidate(score, pos));
                    }
                }
            }

            candidates.sort((a, b) -> Double.compare(a.score, b.score));

            ItemStack held = plannedSlot >= 0 && plannedSlot <= 8 ? mc.thePlayer.inventory.mainInventory[plannedSlot] : null;
            for (BlockCandidate candidate : candidates) {
                boolean underPlayer = isBlockUnderPlayer(candidate.pos, playerPos);
                AimResult result = getBestRotationsToBlock(held, candidate.pos, eye, getReach(), underPlayer);
                if (result != null) {
                    return result;
                }
            }

            return null;
        }

        private boolean isBlockUnderPlayer(BlockPos blockPos, Vec3 pos) {
            if (blockPos.getY() >= MathHelper.floor_double(pos.yCoord)) {
                return false;
            }
            for (double[] corner : CORNERS) {
                int cx = MathHelper.floor_double(pos.xCoord + corner[0]);
                int cz = MathHelper.floor_double(pos.zCoord + corner[1]);
                if (blockPos.getX() == cx && blockPos.getZ() == cz) {
                    return true;
                }
            }
            return false;
        }

        private AimResult getBestRotationsToBlock(ItemStack held, BlockPos targetCell, Vec3 eye, double reachVal, boolean underPlayer) {
            double inset = 0.05;
            double step = 0.2;
            double jitter = step * 0.1;
            boolean faceSouth = Math.abs(eye.zCoord - (targetCell.getZ() + 1)) < Math.abs(eye.zCoord - targetCell.getZ());
            boolean faceEast = Math.abs(eye.xCoord - (targetCell.getX() + 1)) < Math.abs(eye.xCoord - targetCell.getX());
            float baseYaw = normYaw(RotationUtils.serverRotations[0]);
            float basePitch = RotationUtils.serverRotations[1];
            int n = (int) Math.round(1 / step);

            ArrayList<RotationCandidate> candidates = new ArrayList<>();
            candidates.add(new RotationCandidate(0, baseYaw, basePitch));

            for (int row = 0; row <= n; row++) {
                double v = clamp01(row * step + randomRange(-jitter, jitter));
                for (int col = 0; col <= n; col++) {
                    double u = clamp01(col * step + randomRange(-jitter, jitter));

                    if (underPlayer) {
                        float[] rV = getRotationsWrapped(eye, targetCell.getX() + u, targetCell.getY() + 1 - inset, targetCell.getZ() + v);
                        double costV = Math.abs(wrapYawDelta(baseYaw, rV[0])) + Math.abs(rV[1] - basePitch);
                        candidates.add(new RotationCandidate(costV, rV[0], rV[1]));
                    }

                    float[] rZ = getRotationsWrapped(eye, targetCell.getX() + u, targetCell.getY() + v, faceSouth ? targetCell.getZ() + 1 - inset : targetCell.getZ() + inset);
                    double costZ = Math.abs(wrapYawDelta(baseYaw, rZ[0])) + Math.abs(rZ[1] - basePitch);
                    candidates.add(new RotationCandidate(costZ, rZ[0], rZ[1]));

                    float[] rX = getRotationsWrapped(eye, faceEast ? targetCell.getX() + 1 - inset : targetCell.getX() + inset, targetCell.getY() + v, targetCell.getZ() + u);
                    double costX = Math.abs(wrapYawDelta(baseYaw, rX[0])) + Math.abs(rX[1] - basePitch);
                    candidates.add(new RotationCandidate(costX, rX[0], rX[1]));
                }
            }

            candidates.sort((a, b) -> Double.compare(a.cost, b.cost));

            for (RotationCandidate candidate : candidates) {
                float yaw = unwrapYaw(candidate.yaw, RotationUtils.serverRotations[0]);
                MovingObjectPosition ray = RotationUtils.rayCastBlock(reachVal, yaw, candidate.pitch);
                if (ray == null) {
                    continue;
                }

                EnumFacing face = ray.sideHit;
                if (face == EnumFacing.DOWN) {
                    continue;
                }
                if (face == EnumFacing.UP && !underPlayer) {
                    continue;
                }
                if (!targetCell.equals(ray.getBlockPos())) {
                    continue;
                }
                if (!BlockUtils.canPlaceBlockOnSide(held, ray.getBlockPos(), face)) {
                    continue;
                }

                return new AimResult(ray, yaw, candidate.pitch);
            }

            return null;
        }

        private int pickBlockSlot() {
            boolean playingBedwars = Utils.getBedwarsStatus() == 2;
            if (!playingBedwars) {
                int current = mc.thePlayer.inventory.currentItem;
                if (isBlockSlot(current)) {
                    return current;
                }

                for (int slot = 8; slot >= 0; --slot) {
                    if (isBlockSlot(slot)) {
                        return slot;
                    }
                }
                return -1;
            }

            int best = -1;
            int bestScore = Integer.MIN_VALUE;

            for (int slot = 8; slot >= 0; --slot) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
                if (stack == null || stack.stackSize == 0 || !(stack.getItem() instanceof ItemBlock)) {
                    continue;
                }

                Block block = ((ItemBlock) stack.getItem()).getBlock();
                ResourceLocation id = Block.blockRegistry.getNameForObject(block);
                if (id == null) {
                    continue;
                }

                Integer score = BLOCK_SCORE.get(id.getResourcePath());
                if (score == null) {
                    continue;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = slot;
                }
            }
            return best;
        }

        private boolean isBlockSlot(int slot) {
            if (slot < 0 || slot > 8) {
                return false;
            }
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock;
        }

        private void equipPlannedSlot() {
            int current = mc.thePlayer.inventory.currentItem;
            if (plannedSlot != -1 && plannedSlot != current) {
                mc.thePlayer.inventory.currentItem = plannedSlot;
                slotWasSwapped = true;
            }
        }

        private float[] getRotationsSmoothed(float currentYaw, float currentPitch, float targetYaw, float targetPitch, boolean snapback) {
            float curYaw = currentYaw;
            float curPitch = currentPitch;
            float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - curYaw);
            float deltaPitch = targetPitch - curPitch;

            if (Math.abs(deltaYaw) < 0.1f) {
                curYaw = targetYaw;
            }
            if (Math.abs(deltaPitch) < 0.1f) {
                curPitch = targetPitch;
            }
            if (curYaw == targetYaw && curPitch == targetPitch) {
                return new float[]{curYaw, RotationUtils.clampPitch(curPitch)};
            }

            float maxStep = (float) (snapback ? getSnapbackSpeed() : getSpeed());
            float factor = 1f - (float) randomRange(0, 0.2);
            maxStep *= factor;

            float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
            if (totalDelta <= maxStep) {
                curYaw = targetYaw;
                curPitch = targetPitch;
            }
            else if (maxStep > 0) {
                float scale = maxStep / totalDelta;
                curYaw += deltaYaw * scale;
                curPitch += deltaPitch * scale;
            }

            return new float[]{curYaw, RotationUtils.clampPitch(curPitch)};
        }

        private boolean canPlaceThrough(BlockPos pos) {
            Block block = BlockUtils.getBlock(pos);
            Material material = block.getMaterial();
            return material == Material.air || material == Material.water || material == Material.lava || block == Blocks.fire;
        }

        private static double clamp01(double v) {
            return v < 0 ? 0 : v > 1 ? 1 : v;
        }

        private static double randomRange(double min, double max) {
            return min + Math.random() * (max - min);
        }

        private static float normYaw(float yaw) {
            yaw = ((yaw % 360f) + 360f) % 360f;
            return yaw > 180f ? yaw - 360f : yaw;
        }

        private static float wrapYawDelta(float base, float target) {
            return MathHelper.wrapAngleTo180_float(target - base);
        }

        private static float unwrapYaw(float yaw, float prevYaw) {
            return prevYaw + MathHelper.wrapAngleTo180_float(yaw - prevYaw);
        }

        private static float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
            double dx = tx - eye.xCoord;
            double dy = ty - eye.yCoord;
            double dz = tz - eye.zCoord;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDistance));
            return new float[]{normYaw(yaw), RotationUtils.clampPitch(pitch)};
        }

        // ============================== automatic presets ==============================

        /**
         * -1 = use the sliders, 0 = W held, 1 = W + Space held, 2 = Space held (without W). The sliders themselves are
         * never changed, so your own values are used again as soon as you let go of the keys (or turn Auto presets off).
         */
        private int getPreset() {
            if (!autoPresets.isToggled() || mc.gameSettings == null || mc.currentScreen != null) {
                return -1;
            }
            boolean forward = mc.gameSettings.keyBindForward.isKeyDown();
            boolean space = isJumpKeyHeld();
            if (forward) {
                return space ? 1 : 0;
            }
            return space ? 2 : -1;
        }

        private double getDelay() {
            int preset = getPreset();
            return preset >= 0 ? PRESET_DELAY[preset] : delay.getInput();
        }

        private double getReach() {
            int preset = getPreset();
            return preset >= 0 ? PRESET_REACH[preset] : reach.getInput();
        }

        private double getSpeed() {
            int preset = getPreset();
            return preset >= 0 ? PRESET_SPEED[preset] : speed.getInput();
        }

        private double getSnapbackSpeed() {
            int preset = getPreset();
            return preset >= 0 ? PRESET_SNAPBACK_SPEED[preset] : snapbackSpeed.getInput();
        }

        private double getMaxDistance() {
            int preset = getPreset();
            return preset >= 0 ? PRESET_MAX_DISTANCE[preset] : maxDistance.getInput();
        }

        private double getRotationTolerance() {
            int preset = getPreset();
            return preset >= 0 ? PRESET_ROTATION_TOLERANCE[preset] : rotationTolerance.getInput();
        }

        // ============================== edge jump (AutoJump template) ==============================

        private void runEdgeJump() {
            boolean canJump = mc.thePlayer.onGround && (!cancelWhenShifting.isToggled() || !mc.thePlayer.isSneaking());
            if (canJump && Utils.onEdge()) {
                setJump(true);
            }
            else if (jumping) {
                setJump(false);
            }
        }

        private void setJump(boolean pressed) {
            jumping = pressed;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), pressed);
        }

        /** Lets go of the jump key, keeping it down only if the player is really holding it. */
        private void releaseJump() {
            jumping = false;
            if (mc.gameSettings == null) {
                return;
            }
            int code = mc.gameSettings.keyBindJump.getKeyCode();
            KeyBinding.setKeyBindState(code, isJumpKeyHeld());
        }

        private boolean isTowering() {
            return tower.isToggled() && isJumpKeyHeld();
        }

        /** Physical state of the jump key, so our own simulated presses are not mistaken for the player's. */
        private boolean isJumpKeyHeld() {
            if (mc.gameSettings == null || mc.currentScreen != null) {
                return false;
            }
            int code = mc.gameSettings.keyBindJump.getKeyCode();
            return code > 0 && Keyboard.isKeyDown(code);
        }

        // ============================== helper types ==============================

        private static class BlockCandidate {
            final double score;
            final BlockPos pos;

            BlockCandidate(double score, BlockPos pos) {
                this.score = score;
                this.pos = pos;
            }
        }

        private static class RotationCandidate {
            final double cost;
            final float yaw;
            final float pitch;

            RotationCandidate(double cost, float yaw, float pitch) {
                this.cost = cost;
                this.yaw = yaw;
                this.pitch = pitch;
            }
        }

        private static class AimResult {
            final MovingObjectPosition ray;
            final float yaw;
            final float pitch;

            AimResult(MovingObjectPosition ray, float yaw, float pitch) {
                this.ray = ray;
                this.yaw = yaw;
                this.pitch = pitch;
            }
        }

        /** Small physics simulation used to predict where the player will land. */
        private static class PredictionState {
            private AxisAlignedBB box;
            private double motionX;
            private double motionY;
            private double motionZ;
            private double posY;
            private boolean onGround;

            static PredictionState fromPlayer() {
                PredictionState state = new PredictionState();
                state.box = mc.thePlayer.getEntityBoundingBox();
                state.motionX = mc.thePlayer.motionX;
                state.motionY = mc.thePlayer.motionY;
                state.motionZ = mc.thePlayer.motionZ;
                state.posY = mc.thePlayer.posY;
                state.onGround = mc.thePlayer.onGround;
                return state;
            }

            Vec3 getPos() {
                return new Vec3((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
            }

            void tick(boolean stopHorizontal) {
                if (stopHorizontal) {
                    motionX = 0.0;
                    motionZ = 0.0;
                }

                motionY -= 0.08;
                move(motionX, motionY, motionZ);
                motionY *= 0.9800000190734863;
                motionX *= 0.91;
                motionZ *= 0.91;
            }

            private void move(double x, double y, double z) {
                double originalX = x;
                double originalY = y;
                double originalZ = z;

                List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.addCoord(x, y, z));
                for (AxisAlignedBB collision : collisions) {
                    y = collision.calculateYOffset(box, y);
                }
                box = box.offset(0.0, y, 0.0);

                for (AxisAlignedBB collision : collisions) {
                    x = collision.calculateXOffset(box, x);
                }
                box = box.offset(x, 0.0, 0.0);

                for (AxisAlignedBB collision : collisions) {
                    z = collision.calculateZOffset(box, z);
                }
                box = box.offset(0.0, 0.0, z);

                onGround = originalY != y && originalY < 0.0;
                posY = box.minY;

                if (originalX != x) {
                    motionX = 0.0;
                }
                if (originalY != y) {
                    motionY = 0.0;
                }
                if (originalZ != z) {
                    motionZ = 0.0;
                }
            }
        }
    }
}
