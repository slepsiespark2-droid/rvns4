package ravens4.module.impl.player.scaffold;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Comparator;
import ravens4.module.Module;
import ravens4.module.setting.Setting;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.script.ScriptDefaults;
import ravens4.script.model.*;
import ravens4.script.packet.clientbound.*;
import ravens4.script.packet.serverbound.*;

/**
 * The "Normal" style of Scaffold's Keep-y mode (the keep_y script): places blocks one below the Y level you started
 * bridging at, with raycast checked rotations, smoothing, speed telly and diagonal bridging.
 * Scaffold owns the lifecycle and forwards the events; the settings are registered on Scaffold.
 */
public class KeepYNormal extends ScriptDefaults {
    public static final String scriptName = "Scaffold";

    /**
     * Stand-in for the script facade's {@code modules}: the script's settings are created directly on Scaffold
     * (with names that cannot clash with Scaffold's own settings) and read back from here.
     */
    static final class modules {
        private static final ScriptDefaults.modules real = new ScriptDefaults.modules("Scaffold");
        static Module host;
        static final Map<String, Setting> settings = new HashMap<String, Setting>();
        static final List<Setting> created = new ArrayList<Setting>();

        static void add(String key, Setting setting) {
            settings.put(key, setting);
            created.add(setting);
            host.registerSetting(setting);
        }

        static void registerButton(String name, boolean defaultValue) {
            add(name, new ButtonSetting(name, defaultValue));
        }

        /** group + name + suffix form, e.g. ("Rotations", "Speed", "%", 70, 10, 100, 1). */
        static void registerSlider(String group, String name, String suffix, double defaultValue, double minimum, double maximum, double interval) {
            String display = group.equals("Rotations") ? "Aim " + name : name;
            add(name, new SliderSetting(display, suffix, defaultValue, minimum, maximum, interval));
        }

        /** ("Safety", "Strict Raycast", 1, 0, 1, 1): a 0/1 slider, shown as an On/Off button. */
        static void registerSlider(String group, String name, double defaultValue, double minimum, double maximum, double interval) {
            add(name, new ButtonSetting(name, defaultValue >= 1.0));
        }

        static double getSlider(String scriptName, String name) {
            Setting setting = settings.get(name);
            if (setting instanceof SliderSetting) {
                return ((SliderSetting) setting).getInput();
            }
            if (setting instanceof ButtonSetting) {
                return ((ButtonSetting) setting).isToggled() ? 1.0 : 0.0;
            }
            return 0.0;
        }

        static boolean getButton(String scriptName, String name) {
            Setting setting = settings.get(name);
            if (setting instanceof ButtonSetting) {
                return ((ButtonSetting) setting).isToggled();
            }
            return setting instanceof SliderSetting && ((SliderSetting) setting).getInput() > 0.0;
        }

        static boolean isEnabled(String moduleName) {
            return real.isEnabled(moduleName);
        }

        static void enable(String moduleName) {
            real.enable(moduleName);
        }

        static void disable(String moduleName) {
            real.disable(moduleName);
        }
    }

    /** Registers the settings on the given module; returns the ones that were created. */
    public List<Setting> load(Module host) {
        modules.host = host;
        modules.created.clear();
        onLoad();
        return new ArrayList<Setting>(modules.created);
    }

static final Map<String, Integer> BLOCK_SCORE = new HashMap<String, Integer>();
static final double HALF_WIDTH = 0.3;
static final double[][] CORNERS = {{-HALF_WIDTH, -HALF_WIDTH}, {HALF_WIDTH, -HALF_WIDTH}, {-HALF_WIDTH, HALF_WIDTH}, {HALF_WIDTH, HALF_WIDTH}};

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

Vec3 placeAtBlock;
String hitSide;
Vec3 hitVec;
boolean placeQueued;
boolean placing;
boolean autoClickerWasOn;
int plannedSlot = -1;
float aimYaw;
float aimPitch;
Vec3 targetHitPos;
String targetSide;
boolean hasAim;
boolean resetting;
Vec3 lastPlaced;
int clutchBlocksPlaced;
boolean movementFixOwned;
float appliedYaw;
float appliedPitch;
boolean hasAppliedRotation;
boolean tellyEngaged;
int tellyStartY = Integer.MIN_VALUE;
float smoothYaw;
float smoothPitch;
boolean smoothInit;
long lastPlaceTime;
long jumpDelay = 50;
long lastJumpTime;
boolean jumpStarted = false;
List<Vec3> detectedGaps = new ArrayList<Vec3>();
long lastPlaceAttempt = 0;
int placeAttempts = 0;
int successfulPlaces = 0;
int airborneTicks;
boolean speedTellyActive;
boolean diagonalWPressActive = false;
long diagonalWPressStartTime = 0;
boolean wasMovingDiagonally = false;
boolean wasOnGround = true;
boolean jumpDetected = false;
long jumpAirTime = 0;
Vec3 lastDiagonalDirection = null;
boolean isDiagonalBridging = false;
Vec3 bridgeDirection = null;
long lastSpeedTellyPlace = 0;
int speedTellyCounter = 0;
boolean strictRaycastEnhanced = false;
Vec3 lockedBlock = null;
float targetRandFactor = 1f;

public void onLoad() {
    modules.registerButton("Speed Telly on RMB", false);
    modules.registerSlider("Rotations", "Speed", "%", 70, 10, 100, 1);
    modules.registerSlider("Rotations", "Smoothness", "%", 60, 10, 100, 1);
    modules.registerSlider("Rotations", "Randomness", "%", 10, 0, 50, 1);
    modules.registerSlider("Rotations", "Tolerance", "°", 30, 5, 45, 1);
    modules.registerSlider("Timing", "Jump Delay", "ms", 40, 0, 200, 5);
    modules.registerSlider("Diagonal", "W Press Duration", "ms", 500, 100, 1000, 50);
    modules.registerSlider("Safety", "Strict Raycast", 1, 0, 1, 1);
}

public void onEnable() {
    hasAim = false;
    resetting = false;
    clutchBlocksPlaced = 0;
    movementFixOwned = false;
    hasAppliedRotation = false;
    tellyEngaged = false;
    tellyStartY = Integer.MIN_VALUE;
    smoothInit = false;
    lastPlaceTime = 0;
    lastJumpTime = 0;
    jumpStarted = false;
    detectedGaps.clear();
    lastPlaceAttempt = 0;
    placeAttempts = 0;
    successfulPlaces = 0;
    airborneTicks = 0;
    diagonalWPressActive = false;
    wasMovingDiagonally = false;
    wasOnGround = true;
    jumpDetected = false;
    jumpAirTime = 0;
    lastDiagonalDirection = null;
    isDiagonalBridging = false;
    bridgeDirection = null;
    lastSpeedTellyPlace = 0;
    speedTellyCounter = 0;
    strictRaycastEnhanced = modules.getSlider(scriptName, "Strict Raycast") == 1;
    lockedBlock = null;
    targetRandFactor = 1f;
}

public void onDisable() {
    clearAim(false);
    disablePlacing(true);
    placeQueued = false;
    tellyEngaged = false;
    tellyStartY = Integer.MIN_VALUE;
    detectedGaps.clear();
    airborneTicks = 0;
    diagonalWPressActive = false;
    wasMovingDiagonally = false;
    jumpDetected = false;
    jumpAirTime = 0;
    lastDiagonalDirection = null;
    isDiagonalBridging = false;
    bridgeDirection = null;
    lastSpeedTellyPlace = 0;
    speedTellyCounter = 0;
    lockedBlock = null;
    targetRandFactor = 1f;
}

public void onPrePlayerInput(MovementInput input) {
    if (!isScreenClosed()) return;
    Entity player = client.getPlayer();
    if (player == null || client.isFlying()) return;

    long currentTime = client.time();
    jumpDelay = (long) modules.getSlider(scriptName, "Jump Delay");
    if (currentTime - lastPlaceTime < jumpDelay && !player.onGround()) {
        input.jump = false;
    }

    handleDiagonalWPress(input, player, currentTime);
    updateBridgeDirection(player);

    if (player.onGround() && !jumpStarted) {
        jumpStarted = true;
        lastJumpTime = currentTime;
    } else if (player.onGround()) {
        jumpStarted = false;
    }

    boolean speedTellyHeld = modules.getButton(scriptName, "Speed Telly on RMB") && keybinds.isMouseDown(1);
    if (speedTellyHeld && player.onGround() && currentTime - lastPlaceTime >= jumpDelay) {
        input.jump = true;
    }

    if (!player.onGround()) return;
    try {
        Simulation sim = Simulation.create();
        sim.setForward(input.forward);
        sim.setStrafe(input.strafe);
        sim.setJump(input.jump);
        sim.setSneak(input.sneak);
        sim.tick();
        if (!sim.onGround() && !input.jump) {
            input.jump = true;
        }
    } catch (Exception ignored) {
    }
}

void updateBridgeDirection(Entity player) {
    Vec3 motion = player.getMotion();
    if (motion == null) return;

    boolean movingX = Math.abs(motion.x) > 0.05;
    boolean movingZ = Math.abs(motion.z) > 0.05;

    if (!movingX && !movingZ) return;

    isDiagonalBridging = movingX && movingZ;
    bridgeDirection = new Vec3(Math.signum(motion.x), 0, Math.signum(motion.z));
}

void handleDiagonalWPress(MovementInput input, Entity player, long currentTime) {
    boolean isDiagonal = isMovingDiagonal();
    long wPressDuration = (long) modules.getSlider(scriptName, "W Press Duration");

    if (isDiagonal && player.onGround()) {
        Vec3 motion = player.getMotion();
        if (motion != null) {
            lastDiagonalDirection = new Vec3(Math.signum(motion.x), 0, Math.signum(motion.z));
        }
    } else if (player.onGround()) {
        lastDiagonalDirection = null;
    }

    if (player.onGround() && !wasOnGround) {
        jumpDetected = false;
        jumpAirTime = 0;
    }

    if (!player.onGround() && wasOnGround && isDiagonal) {
        jumpDetected = true;
        diagonalWPressActive = true;
        diagonalWPressStartTime = currentTime;
        jumpAirTime = currentTime;
    }

    wasOnGround = player.onGround();
    wasMovingDiagonally = isDiagonal;

    if (diagonalWPressActive && jumpDetected && !player.onGround()) {
        long timeInAir = currentTime - jumpAirTime;

        if (timeInAir < wPressDuration) {
            input.forward = 1.0f;
        } else {
            diagonalWPressActive = false;
            jumpDetected = false;
        }
    } else if (player.onGround()) {
        diagonalWPressActive = false;
        jumpDetected = false;
    }
}

Float[] getRotations() {
    Entity player = client.getPlayer();
    if (player == null || !world.exists()) return null;

    if (!smoothInit) {
        smoothYaw = player.getYaw();
        smoothPitch = player.getPitch();
        smoothInit = true;
    }

    if (!hasAppliedRotation) {
        appliedYaw = player.getYaw();
        appliedPitch = player.getPitch();
        hasAppliedRotation = true;
    }

    runPrePlayerInteract(player);

    float baseYaw = appliedYaw;
    float basePitch = appliedPitch;

    if (resetting) {
        aimYaw = player.getYaw();
        aimPitch = player.getPitch();
        float[] smoothed = getRotationsSmoothed(smoothYaw, smoothPitch, aimYaw, aimPitch, true);
        appliedYaw = smoothed[0];
        appliedPitch = smoothed[1];
        if (Math.abs(wrapAngleTo180(smoothed[0] - aimYaw)) < 0.5f && Math.abs(smoothed[1] - aimPitch) < 0.5f) {
            resetting = false;
            hasAppliedRotation = false;
            smoothInit = false;
            restoreInputsAndAutoClicker();
            setMovementFix(false);
            return null;
        }
        setMovementFix(true);
        smoothYaw = smoothed[0];
        smoothPitch = smoothed[1];
        return new Float[]{Float.valueOf(smoothed[0]), Float.valueOf(smoothed[1])};
    }

    if (!hasAim) {
        hasAppliedRotation = false;
        float[] resetSmooth = getRotationsSmoothed(smoothYaw, smoothPitch, player.getYaw(), player.getPitch(), true);
        smoothYaw = resetSmooth[0];
        smoothPitch = resetSmooth[1];
        return new Float[]{Float.valueOf(resetSmooth[0]), Float.valueOf(resetSmooth[1])};
    }

    float[] smoothed = getRotationsSmoothed(smoothYaw, smoothPitch, aimYaw, aimPitch, false);
    appliedYaw = smoothed[0];
    appliedPitch = smoothed[1];
    smoothYaw = smoothed[0];
    smoothPitch = smoothed[1];

    if (placing && targetHitPos != null) {
        Object[] ray = client.raycastBlock(4.5, smoothed[0], smoothed[1]);
        if (ray != null && sameBlock((Vec3) ray[0], targetHitPos) && sideEquals((String) ray[2], targetSide)) {
            double tolerance = modules.getSlider(scriptName, "Tolerance");
            if (Math.abs(wrapAngleTo180(smoothed[0] - baseYaw)) <= tolerance
                    && Math.abs(smoothed[1] - basePitch) <= tolerance) {
                placeAtBlock = (Vec3) ray[0];
                hitSide = ((String) ray[2]).toUpperCase();
                hitVec = ((Vec3) ray[0]).offset((Vec3) ray[1]);
                placeQueued = true;
            }
        }
    }

    setMovementFix(true);
    return new Float[]{Float.valueOf(smoothed[0]), Float.valueOf(smoothed[1])};
}

float[] getRotationsSmoothed(float currentYaw, float currentPitch, float targetYaw, float targetPitch, boolean snapback) {
    float curYaw = currentYaw;
    float curPitch = currentPitch;
    float deltaYaw = wrapAngleTo180(targetYaw - curYaw);
    float deltaPitch = targetPitch - curPitch;

    if (Math.abs(deltaYaw) < 0.1f) curYaw = targetYaw;
    if (Math.abs(deltaPitch) < 0.1f) curPitch = targetPitch;
    if (curYaw == targetYaw && curPitch == targetPitch) {
        return new float[]{curYaw, clampPitch(curPitch)};
    }

    double speedFactor = snapback ? 1.0 : modules.getSlider(scriptName, "Speed") / 100.0;
    double smoothFactor = modules.getSlider(scriptName, "Smoothness") / 100.0;

    float maxStep = snapback ? 100f : (speedTellyActive ? 96f : (isDiagonalBridging ? 82f : 48f));
    maxStep *= targetRandFactor;

    float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
    if (totalDelta <= maxStep * speedFactor) {
        curYaw = targetYaw;
        curPitch = targetPitch;
    } else if (maxStep > 0) {
        double ease = 1.0 - smoothFactor * 0.72;
        float scale = (float) (maxStep * speedFactor * ease / totalDelta);
        curYaw += deltaYaw * scale;
        curPitch += deltaPitch * scale;
    }

    return new float[]{curYaw, clampPitch(curPitch)};
}

public void onPreUpdate() {
    if (!world.exists() || client.getPlayer() == null || !placeQueued) return;

    placeQueued = false;
    if (placeAtBlock != null && hitSide != null && hitVec != null) {
        long minPlaceDelay = speedTellyActive ? 25 : 40;
        long currentTime = client.time();
        if (currentTime - lastPlaceAttempt < minPlaceDelay) {
            return;
        }

        lastPlaceAttempt = currentTime;
        placeAttempts++;

        Object[] freshRay = client.raycastBlock(4.5, appliedYaw, appliedPitch);
        if (freshRay == null || !sameBlock((Vec3) freshRay[0], placeAtBlock)
                || !sideEquals((String) freshRay[2], hitSide)) {
            return;
        }
        placeAtBlock = (Vec3) freshRay[0];
        hitSide = ((String) freshRay[2]).toUpperCase();
        hitVec = placeAtBlock.offset((Vec3) freshRay[1]);

        boolean placed = false;
        try {
            placed = client.placeBlock(placeAtBlock, hitSide, hitVec);
        } catch (Exception ignored) {
        }
        if (!placed) {
            try {
                placed = client.placeBlock(placeAtBlock, hitSide.toLowerCase(), hitVec);
            } catch (Exception ignored) {
            }
        }
        if (!placed) {
            placed = sendPlacePacket();
        }
        if (placed) {
            successfulPlaces++;
            if (!hitSide.equalsIgnoreCase("UP")) clutchBlocksPlaced++;
            lastPlaced = placeAtBlock;
            lockedBlock = offsetBySide(placeAtBlock, hitSide);
            client.swing();
            lastPlaceTime = client.time();

            if (speedTellyActive) {
                lastSpeedTellyPlace = client.time();
                speedTellyCounter++;
            }

            Iterator<Vec3> iterator = detectedGaps.iterator();
            while (iterator.hasNext()) {
                Vec3 gap = iterator.next();
                if (sameBlock(gap, placeAtBlock)) {
                    iterator.remove();
                }
            }
        }
    }
}

boolean sendPlacePacket() {
    try {
        Entity player = client.getPlayer();
        if (player == null) return false;
        ItemStack held = player.getHeldItem();
        if (held == null) return false;
        Vec3 offset = hitVec.offset(placeAtBlock.inverse());
        client.sendPacket(new C08(held, placeAtBlock, sideOrdinal(hitSide), offset));
        return true;
    } catch (Exception ignored) {
    }
    return false;
}

public boolean onMouse(int button, boolean state, int x, int y) {
    if ((placing || resetting || hasAim) && button > -1) {
        return false;
    }
    return true;
}

void runPrePlayerInteract(Entity player) {
    if (player.onGround()) {
        clutchBlocksPlaced = 0;
    }

    if (!isScreenClosed()) {
        clearAim(true);
        disablePlacing(false);
        detectedGaps.clear();
        airborneTicks = 0;
        return;
    }

    if (keybinds.isMouseDown(1)) {
        keybinds.setPressed("use", false);
    }

    boolean speedTelly = modules.getButton(scriptName, "Speed Telly on RMB");
    speedTellyActive = speedTelly && keybinds.isMouseDown(1);
    if (player.onGround()) {
        airborneTicks = 0;
        tellyEngaged = false;
        tellyStartY = Integer.MIN_VALUE;
        clearAim(true);
        disablePlacing(true);
        detectedGaps.clear();
        speedTellyCounter = 0;
        return;
    }
    airborneTicks++;
    if (!tellyEngaged) {
        if (speedTellyActive) {
            tellyEngaged = true;
        } else if (keybinds.isPressed("jump")) {
            tellyStartY = Integer.MIN_VALUE;
            tellyEngaged = true;
        } else if (airborneTicks == 1) {
            tellyStartY = (int) Math.floor(player.getPosition().y) - 1;
            tellyEngaged = true;
        } else {
            clearAim(true);
            disablePlacing(false);
            detectedGaps.clear();
            return;
        }
    }

    checkForGaps(player);

    int placeY = (tellyEngaged && tellyStartY != Integer.MIN_VALUE) ? tellyStartY : (int) Math.floor(player.getPosition().y) - 1;
    Vec3 below = new Vec3(Math.floor(player.getPosition().x), placeY, Math.floor(player.getPosition().z));
    if (!canPlaceThrough(below)) {
        disablePlacing(false);
        return;
    }

    int weakSlot = pickBlockSlot();
    if (weakSlot == -1) {
        disablePlacing(false);
        return;
    }

    plannedSlot = weakSlot;
    AimResult target = clutchAim(player);
    if (target != null) {
        targetHitPos = target.rayPos;
        targetSide = target.side;
        aimYaw = target.yaw;
        aimPitch = target.pitch;
        hasAim = true;
        resetting = false;
    }

    if (hasAim && !placing) enablePlacing();

    if (placing || resetting || hasAim) {
        releaseClickKeys();
        equipPlannedSlot();
    }
}

void checkForGaps(Entity player) {
    Vec3 playerPos = player.getPosition();
    int playerFeetX = (int) Math.floor(playerPos.x);
    int playerFeetZ = (int) Math.floor(playerPos.z);
    int placeY = (tellyEngaged && tellyStartY != Integer.MIN_VALUE) ? tellyStartY : (int) Math.floor(playerPos.y) - 1;

    detectedGaps.clear();

    for (int x = playerFeetX - 5; x <= playerFeetX + 5; x++) {
        for (int z = playerFeetZ - 5; z <= playerFeetZ + 5; z++) {
            Vec3 blockPos = new Vec3(x, placeY, z);

            if (canPlaceThrough(blockPos)) continue;

            boolean hasPlayerPlacedNeighbor = false;
            for (int nx = -1; nx <= 1; nx++) {
                for (int nz = -1; nz <= 1; nz++) {
                    if (nx == 0 && nz == 0) continue;
                    Vec3 neighborPos = new Vec3(x + nx, placeY, z + nz);
                    if (sameBlock(neighborPos, lastPlaced)) {
                        hasPlayerPlacedNeighbor = true;
                        break;
                    }
                }
                if (hasPlayerPlacedNeighbor) break;
            }

            if (!hasPlayerPlacedNeighbor) {
                for (int nx = -1; nx <= 1; nx++) {
                    for (int nz = -1; nz <= 1; nz++) {
                        if (nx == 0 && nz == 0) continue;
                        Vec3 adjacentPos = new Vec3(x + nx, placeY, z + nz);
                        if (canPlaceThrough(adjacentPos)) {
                            double distX = Math.abs(adjacentPos.x - playerPos.x);
                            double distZ = Math.abs(adjacentPos.z - playerPos.z);
                            if ((distX >= 3 && distX <= 5) || (distZ >= 3 && distZ <= 5)) {
                                Vec3 placeOnPos = new Vec3(x, placeY, z);
                                if (!canPlaceThrough(placeOnPos)) {
                                    detectedGaps.add(adjacentPos);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

void enablePlacing() {
    if (placing) return;
    placing = true;
    if (!autoClickerWasOn) {
        try {
            autoClickerWasOn = modules.isEnabled("AutoClicker");
        } catch (Exception ignored) {
        }
    }
    if (autoClickerWasOn) {
        try {
            modules.disable("AutoClicker");
        } catch (Exception ignored) {
        }
    }
}

void disablePlacing(boolean forceRestore) {
    if (!placing && !forceRestore) return;

    placing = false;
    plannedSlot = -1;

    if (forceRestore) {
        restoreInputsAndAutoClicker();
    }
}

void clearAim(boolean allowSnapback) {
    targetHitPos = null;
    targetSide = null;
    lastPlaced = null;
    clutchBlocksPlaced = 0;
    lockedBlock = null;
    if (allowSnapback && hasAim) {
        resetting = true;
    } else {
        setMovementFix(false);
    }
    hasAim = false;
    smoothInit = false;
    diagonalWPressActive = false;
    jumpDetected = false;
    isDiagonalBridging = false;
    lastDiagonalDirection = null;
    bridgeDirection = null;
}

void restoreInputsAndAutoClicker() {
    try {
        if (isScreenClosed()) {
            keybinds.setPressed("attack", keybinds.isMouseDown(0));
            keybinds.setPressed("use", keybinds.isMouseDown(1));
        }
    } catch (Exception ignored) {
    }
    if (autoClickerWasOn) {
        try {
            modules.enable("AutoClicker");
        } catch (Exception ignored) {
        }
        autoClickerWasOn = false;
    }
}

void releaseClickKeys() {
    try {
        keybinds.setPressed("attack", false);
        keybinds.setPressed("use", false);
    } catch (Exception ignored) {
    }
}

AimResult clutchAim(Entity player) {
    Vec3 playerPos = player.getPosition();
    Vec3 eye = new Vec3(playerPos.x, playerPos.y + player.getEyeHeight(), playerPos.z);

    int feetX = (int) Math.floor(playerPos.x);
    int feetZ = (int) Math.floor(playerPos.z);
    int feetY = (int) Math.floor(playerPos.y);
    int minX = feetX - 5;
    int maxX = feetX + 4;
    int minZ = feetZ - 5;
    int maxZ = feetZ + 4;
    int maxY;
    int minY;
    if (tellyEngaged && tellyStartY != Integer.MIN_VALUE) {
        maxY = tellyStartY;
        minY = tellyStartY;
    } else {
        maxY = feetY - 1;
        minY = feetY - 4;
    }

    boolean keepY = tellyEngaged && tellyStartY != Integer.MIN_VALUE;

    ItemStack held = plannedSlot >= 0 && plannedSlot <= 8 ? inventory.getStackInSlot(plannedSlot) : null;
    double reachVal = 4.5;

    if (keepY) {
        AimResult tellyAim = getBestRotationsToTelly(held, eye, reachVal);
        if (tellyAim != null) return tellyAim;
    }

    if (lockedBlock != null) {
        boolean lockValid = !canPlaceThrough(lockedBlock)
                && lockedBlock.y >= minY && lockedBlock.y <= maxY
                && distPointToAABB(playerPos, lockedBlock) <= reachVal;
        if (lockValid) {
            boolean underPlayer = isBlockUnderPlayer(lockedBlock, playerPos);
            AimResult lockedAim = getBestRotationsToBlock(held, lockedBlock, eye, reachVal, underPlayer, keepY);
            if (lockedAim != null) return lockedAim;
        }
        lockedBlock = null;
    }

    List<BlockCandidate> candidates = new ArrayList<BlockCandidate>();
    for (int y = maxY; y >= minY; y--) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Vec3 pos = new Vec3(x, y, z);
                if (canPlaceThrough(pos)) continue;

                double score = distPointToAABB(playerPos, pos);
                if (sameBlock(pos, lastPlaced)) score *= 0.8;
                if (bridgeDirection != null && !isBlockInBridgePath(pos, feetX, feetZ)) score *= 1.6;
                candidates.add(new BlockCandidate(score, pos));
            }
        }
    }

    for (Vec3 gapPos : detectedGaps) {
        double score = distPointToAABB(playerPos, gapPos) * 0.8;
        candidates.add(new BlockCandidate(score, gapPos));
    }

    candidates.sort(new Comparator<BlockCandidate>() {
        public int compare(BlockCandidate a, BlockCandidate b) {
            return Double.compare(a.score, b.score);
        }
    });

    for (BlockCandidate candidate : candidates) {
        boolean underPlayer = isBlockUnderPlayer(candidate.pos, playerPos);
        AimResult result = getBestRotationsToBlock(held, candidate.pos, eye, reachVal, underPlayer, keepY);
        if (result != null) {
            if (lockedBlock == null) {
                targetRandFactor = 1f - (float) randomRange(0, modules.getSlider(scriptName, "Randomness") / 100.0);
            }
            lockedBlock = candidate.pos;
            return result;
        }
    }

    return null;
}

boolean isBlockInBridgePath(Vec3 blockPos, int feetX, int feetZ) {
    if (bridgeDirection == null) return true;
    double relX = blockPos.x - feetX;
    double relZ = blockPos.z - feetZ;
    if (isDiagonalBridging) {
        double cross = Math.abs(relX * bridgeDirection.z - relZ * bridgeDirection.x);
        double dot = relX * bridgeDirection.x + relZ * bridgeDirection.z;
        return dot >= -1.5 && cross <= 1.5;
    }
    if (bridgeDirection.x != 0) {
        double dot = relX * bridgeDirection.x;
        return dot >= -1.5 && Math.abs(relZ) <= 1.0;
    }
    double dot = relZ * bridgeDirection.z;
    return dot >= -1.5 && Math.abs(relX) <= 1.0;
}

boolean isBlockUnderPlayer(Vec3 blockPos, Vec3 pos) {
    if (blockPos.y >= Math.floor(pos.y)) return false;
    for (double[] corner : CORNERS) {
        int cx = (int) Math.floor(pos.x + corner[0]);
        int cz = (int) Math.floor(pos.z + corner[1]);
        if (blockPos.x == cx && blockPos.z == cz) return true;
    }
    return false;
}

AimResult getBestRotationsToBlock(ItemStack held, Vec3 targetCell, Vec3 eye, double reachVal, boolean underPlayer, boolean sideFacesOnly) {
    if (held == null) return null;
    double inset = 0.05;
    double step = 0.25;
    boolean faceSouth = Math.abs(eye.z - (targetCell.z + 1)) < Math.abs(eye.z - targetCell.z);
    boolean faceEast = Math.abs(eye.x - (targetCell.x + 1)) < Math.abs(eye.x - targetCell.x);
    float baseYaw = normYaw(anchorYaw());
    float basePitch = anchorPitch();
    int n = (int) Math.round(1 / step);

    List<RotationCandidate> candidates = new ArrayList<RotationCandidate>();
    candidates.add(new RotationCandidate(0, baseYaw, basePitch));

    for (int row = 0; row <= n; row++) {
        double v = clamp01(row * step);
        for (int col = 0; col <= n; col++) {
            double u = clamp01(col * step);

            if (underPlayer && !sideFacesOnly) {
                float[] rV = getRotationsWrapped(eye, targetCell.x + u, targetCell.y + 1 - inset, targetCell.z + v);
                double costV = Math.abs(wrapYawDelta(baseYaw, rV[0])) + Math.abs(rV[1] - basePitch);
                candidates.add(new RotationCandidate(costV, rV[0], rV[1]));
            }

            float[] rZ = getRotationsWrapped(eye, targetCell.x + u, targetCell.y + v, faceSouth ? targetCell.z + 1 - inset : targetCell.z + inset);
            double costZ = Math.abs(wrapYawDelta(baseYaw, rZ[0])) + Math.abs(rZ[1] - basePitch);
            candidates.add(new RotationCandidate(costZ, rZ[0], rZ[1]));

            float[] rX = getRotationsWrapped(eye, faceEast ? targetCell.x + 1 - inset : targetCell.x + inset, targetCell.y + v, targetCell.z + u);
            double costX = Math.abs(wrapYawDelta(baseYaw, rX[0])) + Math.abs(rX[1] - basePitch);
            candidates.add(new RotationCandidate(costX, rX[0], rX[1]));
        }
    }

    candidates.sort(new Comparator<RotationCandidate>() {
        public int compare(RotationCandidate a, RotationCandidate b) {
            return Double.compare(a.cost, b.cost);
        }
    });

    for (RotationCandidate candidate : candidates) {
        float yaw = unwrapYaw(candidate.yaw, anchorYaw());
        Object[] ray = strictRaycastBlock(reachVal, yaw, candidate.pitch, targetCell);
        if (ray == null) continue;

        String face = ((String) ray[2]).toUpperCase();
        if (face.equals("DOWN")) continue;
        if (face.equals("UP") && (sideFacesOnly || !underPlayer)) continue;
        if (!sameBlock((Vec3) ray[0], targetCell)) continue;
        if (!canPlaceOn(held, (Vec3) ray[0], face)) continue;

        return new AimResult((Vec3) ray[0], face, yaw, candidate.pitch);
    }

    return null;
}

Object[] strictRaycastBlock(double distance, float yaw, float pitch, Vec3 targetBlock) {
    if (!strictRaycastEnhanced) {
        return client.raycastBlock(distance, yaw, pitch);
    }

    Object[] primaryRay = client.raycastBlock(distance, yaw, pitch);
    if (primaryRay != null && sameBlock((Vec3) primaryRay[0], targetBlock)) {
        return primaryRay;
    }

    Vec3 eye = getEyePosition();
    if (eye == null) return primaryRay;

    for (int i = 0; i < 3; i++) {
        float adjustedYaw = yaw + (i - 1) * 0.5f;
        float adjustedPitch = pitch + (i - 1) * 0.5f;

        Object[] ray = client.raycastBlock(distance, adjustedYaw, adjustedPitch);
        if (ray != null && sameBlock((Vec3) ray[0], targetBlock)) {
            String raySide = ((String) ray[2]).toUpperCase();
            if (isValidFaceForPlacement(raySide, targetBlock, eye)) {
                return ray;
            }
        }
    }

    return null;
}

boolean isValidFaceForPlacement(String side, Vec3 blockPos, Vec3 eyePos) {
    if (side.equals("DOWN")) return false;

    double dx = eyePos.x - (blockPos.x + 0.5);
    double dy = eyePos.y - (blockPos.y + 0.5);
    double dz = eyePos.z - (blockPos.z + 0.5);

    if (side.equals("UP") && dy < -0.8) return false;
    if (side.equals("NORTH") && dz > 0.3) return false;
    if (side.equals("SOUTH") && dz < -0.3) return false;
    if (side.equals("WEST") && dx > 0.3) return false;
    if (side.equals("EAST") && dx < -0.3) return false;

    return true;
}

Vec3 getEyePosition() {
    Entity player = client.getPlayer();
    if (player == null) return null;
    Vec3 pos = player.getPosition();
    return new Vec3(pos.x, pos.y + player.getEyeHeight(), pos.z);
}

AimResult getBestRotationsToTelly(ItemStack held, Vec3 eye, double reachVal) {
    if (held == null) return null;
    Entity player = client.getPlayer();
    if (player == null) return null;

    Vec3 playerPos = player.getPosition();
    Vec3 motion = player.getMotion();
    Vec3 targetCell = new Vec3(Math.floor(playerPos.x), tellyStartY, Math.floor(playerPos.z));
    if (!canPlaceThrough(targetCell)) return null;
    return getBestRotationsToFillCell(held, eye, reachVal, targetCell);
}

void addTellyCell(List<Vec3> cells, double x, double z) {
    Vec3 cell = new Vec3(Math.floor(x), tellyStartY, Math.floor(z));
    for (Vec3 existing : cells) {
        if (sameBlock(existing, cell)) return;
    }
    cells.add(cell);
}

AimResult getBestRotationsToFillCell(ItemStack held, Vec3 eye, double reachVal, Vec3 fillCell) {
    if (held == null) return null;
    double inset = 0.05;
    double cx = Math.floor(fillCell.x);
    double cy = Math.floor(fillCell.y);
    double cz = Math.floor(fillCell.z);

    List<double[]> aimPoints = new ArrayList<double[]>();
    aimPoints.add(new double[]{cx + 0.5, cy + 0.5, cz + 1 + inset});
    aimPoints.add(new double[]{cx + 0.5, cy + 0.5, cz - inset});
    aimPoints.add(new double[]{cx + 1 + inset, cy + 0.5, cz + 0.5});
    aimPoints.add(new double[]{cx - inset, cy + 0.5, cz + 0.5});

    for (double[] p : aimPoints) {
        float[] r = getRotationsWrapped(eye, p[0], p[1], p[2]);
        float yaw = unwrapYaw(r[0], anchorYaw());
        Object[] ray = client.raycastBlock(reachVal, yaw, r[1]);
        if (ray == null) continue;

        String face = ((String) ray[2]).toUpperCase();
        if (face.equals("DOWN")) continue;
        Vec3 hit = (Vec3) ray[0];
        if (!isPlacementAt(hit, face, cx, cy, cz)) continue;
        if (!canPlaceOn(held, hit, face)) continue;

        return new AimResult(hit, face, yaw, r[1]);
    }

    return null;
}

boolean isPlacementAt(Vec3 hit, String face, double cx, double cy, double cz) {
    int pcx = (int) Math.floor(hit.x);
    int pcy = (int) Math.floor(hit.y);
    int pcz = (int) Math.floor(hit.z);
    if (face.equals("UP")) pcy += 1;
    else if (face.equals("DOWN")) pcy -= 1;
    else if (face.equals("NORTH")) pcz -= 1;
    else if (face.equals("SOUTH")) pcz += 1;
    else if (face.equals("WEST")) pcx -= 1;
    else if (face.equals("EAST")) pcx += 1;
    return pcx == (int) cx && pcy == (int) cy && pcz == (int) cz;
}

int pickBlockSlot() {
    boolean playingBedwars = isPlayingBedwars();
    if (!playingBedwars) {
        int current = inventory.getSlot();
        if (isBlockSlot(current)) {
            if (inventory.getStackInSlot(current).stackSize > 5) return current;
            int swap = findSwapSlot(current);
            if (swap != -1) return swap;
            return current;
        }

        for (int slot = 8; slot >= 0; --slot) {
            if (isBlockSlot(slot) && inventory.getStackInSlot(slot).stackSize > 5) return slot;
        }

        for (int slot = 8; slot >= 0; --slot) {
            if (isBlockSlot(slot)) return slot;
        }
        return -1;
    }

    int best = -1;
    int bestScore = Integer.MIN_VALUE;

    for (int slot = 8; slot >= 0; --slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack == null || stack.stackSize <= 5 || !stack.isBlock) continue;

        Integer score = BLOCK_SCORE.get(normalizeName(stack.name));
        if (score == null) continue;

        if (score > bestScore) {
            bestScore = score;
            best = slot;
        }
    }

    if (best == -1) {
        for (int slot = 8; slot >= 0; --slot) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize == 0 || !stack.isBlock) continue;

            Integer score = BLOCK_SCORE.get(normalizeName(stack.name));
            if (score == null) continue;

            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
    }

    if (best != -1 && inventory.getStackInSlot(best).stackSize == 5) {
        int swap = findSwapSlot(best);
        if (swap != -1) return swap;
    }
    return best;
}

int findSwapSlot(int excludeSlot) {
    String excludeName = null;
    ItemStack currentStack = inventory.getStackInSlot(excludeSlot);
    if (currentStack != null) excludeName = normalizeName(currentStack.name);

    for (int slot = 8; slot >= 0; --slot) {
        if (slot == excludeSlot) continue;
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack == null || stack.stackSize <= 5 || !stack.isBlock) continue;
        if (excludeName != null && normalizeName(stack.name).equals(excludeName)) return slot;
    }

    for (int slot = 8; slot >= 0; --slot) {
        if (slot == excludeSlot) continue;
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack == null || stack.stackSize <= 5 || !stack.isBlock) continue;
        return slot;
    }

    return -1;
}

boolean isBlockSlot(int slot) {
    if (slot < 0 || slot > 8) return false;
    ItemStack stack = inventory.getStackInSlot(slot);
    return stack != null && stack.stackSize > 0 && stack.isBlock;
}

boolean isPlayingBedwars() {
    try {
        String ip = client.getServerIP();
        if (ip == null || !ip.toLowerCase().contains("hypixel")) return false;
        List<String> scoreboard = world.getScoreboard();
        if (scoreboard == null) return false;
        for (String line : scoreboard) {
            if (line == null) continue;
            String s = util.strip(line).toLowerCase();
            if (s.contains("bedwars") || s.contains("bed wars")) return true;
        }
    } catch (Exception ignored) {
    }
    return false;
}

void equipPlannedSlot() {
    int current = inventory.getSlot();
    if (plannedSlot != -1 && plannedSlot != current) {
        inventory.setSlot(plannedSlot);
    }
}

boolean canPlaceThrough(Vec3 pos) {
    Block block = world.getBlockAt(pos);
    if (block == null) return false;
    String name = normalizeName(block.name);
    return "air".equals(name) || "water".equals(name) || "lava".equals(name) || "fire".equals(name);
}

float anchorYaw() {
    if (hasAppliedRotation) return appliedYaw;
    Entity player = client.getPlayer();
    return player != null ? player.getYaw() : 0f;
}

float anchorPitch() {
    if (hasAppliedRotation) return appliedPitch;
    Entity player = client.getPlayer();
    return player != null ? player.getPitch() : 0f;
}

boolean canPlaceOn(ItemStack stack, Vec3 pos, String side) {
    try {
        if (client.canPlaceBlock(stack, pos, side)) return true;
    } catch (Exception ignored) {
    }
    try {
        return client.canPlaceBlock(stack, pos, side.toLowerCase());
    } catch (Exception ignored) {
    }
    return false;
}

void setMovementFix(boolean on) {
    if (on && !movementFixOwned) {
        client.enableMovementFix();
        movementFixOwned = true;
    } else if (!on && movementFixOwned) {
        client.disableMovementFix();
        movementFixOwned = false;
    }
}

boolean isScreenClosed() {
    String screen = client.getScreen();
    return screen == null || screen.isEmpty() || "null".equals(screen);
}

boolean isMovingDiagonal() {
    Entity player = client.getPlayer();
    if (player == null) return false;
    Vec3 motion = player.getMotion();
    if (motion == null) return false;
    return Math.abs(motion.x) > 0.05 && Math.abs(motion.z) > 0.05;
}

static Vec3 offsetBySide(Vec3 pos, String side) {
    String s = side.toUpperCase();
    if (s.equals("UP")) return pos.offset(0, 1, 0);
    if (s.equals("DOWN")) return pos.offset(0, -1, 0);
    if (s.equals("NORTH")) return pos.offset(0, 0, -1);
    if (s.equals("SOUTH")) return pos.offset(0, 0, 1);
    if (s.equals("WEST")) return pos.offset(-1, 0, 0);
    if (s.equals("EAST")) return pos.offset(1, 0, 0);
    return pos;
}

static double distPointToAABB(Vec3 point, Vec3 blockPos) {
    double minX = blockPos.x;
    double minY = blockPos.y;
    double minZ = blockPos.z;
    double maxX = blockPos.x + 1;
    double maxY = blockPos.y + 1;
    double maxZ = blockPos.z + 1;
    double dx = Math.max(minX - point.x, Math.max(0, point.x - maxX));
    double dy = Math.max(minY - point.y, Math.max(0, point.y - maxY));
    double dz = Math.max(minZ - point.z, Math.max(0, point.z - maxZ));
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

static boolean sameBlock(Vec3 a, Vec3 b) {
    if (a == null || b == null) return false;
    return Math.floor(a.x) == Math.floor(b.x)
            && Math.floor(a.y) == Math.floor(b.y)
            && Math.floor(a.z) == Math.floor(b.z);
}

static boolean sideEquals(String a, String b) {
    if (a == null || b == null) return false;
    return a.equalsIgnoreCase(b);
}

static String normalizeName(String name) {
    if (name == null) return "";
    if (name.startsWith("minecraft:")) name = name.substring(10);
    return name;
}

static double clamp01(double v) {
    return v < 0 ? 0 : v > 1 ? 1 : v;
}

static int sideOrdinal(String side) {
    String s = side.toUpperCase();
    if (s.equals("DOWN")) return 0;
    if (s.equals("UP")) return 1;
    if (s.equals("NORTH")) return 2;
    if (s.equals("SOUTH")) return 3;
    if (s.equals("WEST")) return 4;
    if (s.equals("EAST")) return 5;
    return 1;
}

static double randomRange(double min, double max) {
    return min + Math.random() * (max - min);
}

static float normYaw(float yaw) {
    yaw = ((yaw % 360f) + 360f) % 360f;
    return yaw > 180f ? yaw - 360f : yaw;
}

static float wrapYawDelta(float base, float target) {
    return wrapAngleTo180(target - base);
}

static float unwrapYaw(float yaw, float prevYaw) {
    return prevYaw + wrapAngleTo180(yaw - prevYaw);
}

static float wrapAngleTo180(float value) {
    value = value % 360.0f;
    if (value >= 180.0f) value -= 360.0f;
    if (value < -180.0f) value += 360.0f;
    return value;
}

static float clampPitch(float pitch) {
    return Math.max(-90f, Math.min(90f, pitch));
}

static float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
    double dx = tx - eye.x;
    double dy = ty - eye.y;
    double dz = tz - eye.z;
    double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDistance));
    return new float[]{normYaw(yaw), clampPitch(pitch)};
}

static class BlockCandidate {
    final double score;
    final Vec3 pos;

    BlockCandidate(double score, Vec3 pos) {
        this.score = score;
        this.pos = pos;
    }
}

static class RotationCandidate {
    final double cost;
    final float yaw;
    final float pitch;

    RotationCandidate(double cost, float yaw, float pitch) {
        this.cost = cost;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}

static class AimResult {
    final Vec3 rayPos;
    final String side;
    final float yaw;
    final float pitch;

    AimResult(Vec3 rayPos, String side, float yaw, float pitch) {
        this.rayPos = rayPos;
        this.side = side;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}

}
