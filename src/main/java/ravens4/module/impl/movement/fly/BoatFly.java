package ravens4.module.impl.movement.fly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ravens4.script.ScriptDefaults;
import ravens4.script.model.*;
import ravens4.script.packet.clientbound.*;
import ravens4.script.packet.serverbound.*;

/**
 * Boat fly: places a boat, mounts it, breaks it while holding transaction packets, then flies.
 * Runs as the "Boat" mode of {@link ravens4.module.impl.movement.Fly}.
 */
public class BoatFly extends ScriptDefaults {
    public static final ScriptDefaults.modules modules = new ScriptDefaults.modules("Fly");
    public static final String scriptName = "Fly";

final int READY = 0;
final int PLACING = 1;
final int MOUNTING = 2;
final int BREAKING = 3;
final int WAITING = 4;
final int FLYING = 5;

final Object transactionLock = new Object();
final List<CPacket> transactions = new ArrayList<>();
final Set<Integer> existingBoats = new HashSet<>();
final Set<Integer> attemptedSlots = new HashSet<>();

volatile int phase = READY;
volatile boolean holdingTransactions;
volatile boolean correctionReceived;
volatile boolean worldChanged;
volatile boolean queueFull;

int boatId = -1;
int originalSlot = -1;
int selectedSlot = -1;
int playerId = -1;
int lastPlayerTick;
int placementAttempts;
int placementLimit;
int groundTicks;
boolean startWasDown;
boolean placementPending;
boolean leftGround;
float placementYaw;
float placementPitch;
long phaseStarted;
long holdStarted;
long flightStarted;
long flightDuration;
long nextAction;
Vec3 placementPosition;
String dimension = "";

public void onLoad() {
    modules.registerKey("Start / stop", 34);
    modules.registerSlider("Maximum boats", 9, 1, 9, 1);
    modules.registerSlider("Horizontal speed", " blocks/tick", 0.5, 0.1, 3, 0.05);
    modules.registerSlider("Vertical speed", " blocks/tick", 0.3, 0.1, 1, 0.05);
    modules.registerSlider("Flight time", " seconds", 15, 1, 15, 1);
}

public void onEnable() {
    reset(false);
    startWasDown = modules.getKeyPressed(scriptName, "Start / stop");
    message("Ready. Press your Start / stop key (default G).");
}

public void onDisable() {
    reset(true);
}

public void onDisconnect() {
    reset(false);
}

public void onWorldJoin(Entity entity) {
    if (entity != null && entity.isUser) {
        discardTransactions();
        worldChanged = true;
    }
}

public void onPreUpdate() {
    Entity player = client.getPlayer();
    if (player == null) {
        reset(false);
        return;
    }

    boolean startDown = modules.getKeyPressed(scriptName, "Start / stop");
    boolean startPressed = startDown && !startWasDown;
    startWasDown = startDown;

    if (phase != READY && (worldChanged || player.entityId != playerId ||
            player.getTicksExisted() < lastPlayerTick || !dimension.equals(world.getDimension()))) {
        reset(false);
        message("Stopped: world changed.");
        return;
    }
    lastPlayerTick = player.getTicksExisted();
    worldChanged = false;

    if (phase != READY && player.isDead()) {
        stop("Stopped: player died.");
        return;
    }
    if (phase != READY && correctionReceived) {
        stop("Stopped: server corrected your position.");
        return;
    }
    if (phase != READY && queueFull) {
        stop("Stopped: transaction queue limit reached.");
        return;
    }
    if (holdingTransactions && client.time() - holdStarted >= 19000) {
        stop("Stopped: transaction hold expired.");
        return;
    }
    if (startPressed && client.getScreen().isEmpty()) {
        if (phase == READY) start(player);
        else stop("Stopped.");
        return;
    }
    if (phase == READY) return;

    long now = client.time();
    if (phase <= BREAKING && now - phaseStarted > 5000) {
        if (phase == PLACING) stop("No boat appeared. Check placement space and boat permissions.");
        else if (phase == MOUNTING) stop("The server did not confirm mounting the boat.");
        else stop("The boat did not break.");
        return;
    }

    if (phase == PLACING) {
        Entity boat = findPlacedBoat(player);
        if (boat != null) {
            boatId = boat.entityId;
            phase = MOUNTING;
            phaseStarted = now;
            nextAction = now;
            placementPending = false;
            message("Boat placed, mounting it.");
        } else if (!placementPending && now >= nextAction && placementAttempts < placementLimit) {
            preparePlacement(player);
        }
    }

    if (phase == MOUNTING) {
        Entity riding = player.getRidingEntity();
        if (riding != null && riding.entityId == boatId) {
            selectSlot(findAttackSlot());
            phase = BREAKING;
            phaseStarted = now;
            nextAction = now + 100;
            message("Mounted. Breaking the boat and holding transactions.");
        } else {
            Entity boat = world.getEntityById(boatId);
            if (boat == null || boat.isDead()) {
                stop("The boat disappeared before mounting was confirmed.");
                return;
            }
            if (riding != null) {
                stop("Stopped: mounted a different entity.");
                return;
            }
            if (boat.distanceToSq(player.getPosition()) > 16) {
                stop("The boat moved out of reach.");
                return;
            }
            if (now >= nextAction) {
                client.sendPacket(new C02(boat, "INTERACT", new Vec3(0, 0, 0)));
                nextAction = now + 300;
            }
        }
    }

    if (phase == BREAKING) {
        Entity boat = world.getEntityById(boatId);
        Entity riding = player.getRidingEntity();
        if (boat == null || boat.isDead()) {
            if (riding != null) return;
            if (!holdingTransactions) {
                stop("The boat vanished before it was hit.");
                return;
            }
            phase = WAITING;
            phaseStarted = now;
            restoreSlot();
            client.setMotion(0, 0, 0);
            message("Boat gone. Flight starts in 1 second; " + heldCount() + " transactions held.");
        } else if (riding == null || riding.entityId != boatId) {
            stop("Dismounted before the boat broke. Restart to try again.");
            return;
        } else if (now >= nextAction) {
            if (!holdingTransactions) {
                holdStarted = now;
                holdingTransactions = true;
            }
            client.swing();
            client.sendPacket(new C02(boat, "ATTACK", new Vec3(0, 0, 0)));
            nextAction = now + 150;
        }
    }

    if (phase == WAITING) {
        client.setMotion(0, 0, 0);
        if (player.getRidingEntity() != null) {
            stop("Stopped: mounted another entity.");
            return;
        }
        if (now - phaseStarted >= 1000) {
            if (heldCount() == 0) {
                stop("No transaction acknowledgements were captured; flight cancelled.");
                return;
            }
            phase = FLYING;
            flightStarted = now;
            groundTicks = 0;
            leftGround = false;
            message("Flight active for " + (flightDuration / 1000) + " seconds. Space up, sneak down.");
        }
    }

    if (phase == FLYING) {
        if (now - flightStarted >= flightDuration) {
            stop("Flight finished. Transactions released.");
            return;
        }
        if (player.getRidingEntity() != null) {
            stop("Stopped: mounted another entity.");
            return;
        }
        if (!player.onGround()) leftGround = true;
        groundTicks = player.onGround() && leftGround ? groundTicks + 1 : 0;
        if (groundTicks >= 3) {
            stop("Landed. Transactions released.");
            return;
        }
        double vertical = 0;
        if (client.getScreen().isEmpty()) {
            if (keybinds.isPressed("jump")) vertical += modules.getSlider(scriptName, "Vertical speed");
            if (keybinds.isPressed("sneak")) vertical -= modules.getSlider(scriptName, "Vertical speed");
        }
        client.setMotion(0, vertical, 0);
        if (client.getScreen().isEmpty()) client.setSpeed(modules.getSlider(scriptName, "Horizontal speed"));
    }
}

public void onPrePlayerInput(MovementInput input) {
    if (phase > READY && phase < FLYING) {
        input.forward = 0;
        input.strafe = 0;
        input.jump = false;
        input.sneak = false;
    } else if (phase == FLYING && input.sneak) {
        input.forward /= 0.3f;
        input.strafe /= 0.3f;
        input.sneak = false;
    }
}

public void onPreMotion(PlayerState state) {
    if (phase == PLACING && placementPending) {
        state.yaw = placementYaw;
        state.pitch = placementPitch;
    }
}

public void onPostMotion() {
    if (phase != PLACING || !placementPending || client.getPlayer() == null) return;
    ItemStack stack = inventory.getStackInSlot(selectedSlot);
    if (!isBoat(stack)) {
        placementPending = false;
        return;
    }
    selectSlot(selectedSlot);
    client.sendPacket(new C08(stack, new Vec3(-1, -1, -1), 255, new Vec3(0, 0, 0)));
    placementAttempts++;
    attemptedSlots.add(selectedSlot);
    placementPending = false;
    nextAction = client.time() + 700;
}

public boolean onPacketSent(CPacket packet) {
    if (!(packet instanceof C0F)) return true;
    synchronized (transactionLock) {
        if (!holdingTransactions) return true;
        if (transactions.size() >= 512) {
            queueFull = true;
            releaseTransactions();
            return true;
        }
        transactions.add(packet);
        return false;
    }
}

public boolean onPacketReceived(SPacket packet) {
    if (packet == null) return true;
    if ("S01PacketJoinGame".equals(packet.name) || "S07PacketRespawn".equals(packet.name) ||
            "S40PacketDisconnect".equals(packet.name)) {
        discardTransactions();
        worldChanged = true;
    } else if (holdingTransactions && "S08PacketPlayerPosLook".equals(packet.name)) {
        correctionReceived = true;
    }
    return true;
}

public void onRenderTick(float partialTicks) {
    if (phase == READY || client.getPlayer() == null || !client.getScreen().isEmpty()) return;
    String text = phase == PLACING ? "Placing boat" : phase == MOUNTING ? "Mounting boat" :
        phase == BREAKING ? "Breaking boat" : phase == WAITING ? "Preparing flight" :
        "Flying: " + (Math.max(0, flightDuration - client.time() + flightStarted) / 100 / 10.0) + "s";
    text += " | " + heldCount() + " transactions";
    int[] display = client.getDisplaySize();
    render.text2d(text, (display[0] - render.getFontWidth(text)) / 2f, display[1] / 2f + 18, 1, -1, true);
}

void start(Entity player) {
    if (!player.onGround() || player.getRidingEntity() != null || player.isDead()) {
        message("Stand on the ground outside a vehicle before starting.");
        return;
    }
    reset(true);
    int boats = 0;
    for (int slot = 0; slot < 9; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (isBoat(stack)) boats++;
    }
    if (boats == 0) {
        message("Put at least one boat in your hotbar.");
        return;
    }
    originalSlot = inventory.getSlot();
    playerId = player.entityId;
    lastPlayerTick = player.getTicksExisted();
    dimension = world.getDimension();
    flightDuration = (long) (modules.getSlider(scriptName, "Flight time") * 1000);
    placementLimit = Math.min(boats, (int) modules.getSlider(scriptName, "Maximum boats"));
    for (Entity entity : world.getEntities()) {
        if (isBoatEntity(entity)) existingBoats.add(entity.entityId);
    }
    phase = PLACING;
    phaseStarted = client.time();
    nextAction = phaseStarted;
    message("Placing up to " + placementLimit + " boats.");
    preparePlacement(player);
}

void preparePlacement(Entity player) {
    int slot = -1;
    for (int i = 0; i < 9; i++) {
        if (!attemptedSlots.contains(i) && isBoat(inventory.getStackInSlot(i))) {
            slot = i;
            break;
        }
    }
    if (slot == -1) return;
    float[] offsets = new float[]{0, 45, -45, 90, -90, 180};
    for (int i = 0; i < offsets.length; i++) {
        float yaw = player.getYaw() + offsets[(i + placementAttempts) % offsets.length];
        Object[] ray = client.raycastBlock(4.5, yaw, 60);
        if (ray == null || !"UP".equals(ray[2])) continue;
        Vec3 block = (Vec3) ray[0];
        Vec3 position = new Vec3(block.x + 0.5, block.y + 1, block.z + 0.5);
        Vec3 playerPosition = player.getPosition();
        double dx = position.x - playerPosition.x;
        double dz = position.z - playerPosition.z;
        if (dx * dx + dz * dz < 0.81 || position.distanceToSq(playerPosition) > 12.25) continue;
        if (!"air".equals(world.getBlockAt((int) block.x, (int) block.y + 1, (int) block.z).name)) continue;
        placementPosition = position;
        placementYaw = yaw;
        placementPitch = 60;
        selectSlot(slot);
        placementPending = true;
        return;
    }
    stop("No clear boat placement found nearby. Stand beside an open, flat area.");
}

Entity findPlacedBoat(Entity player) {
    if (placementAttempts == 0 || placementPosition == null) return null;
    Entity nearest = null;
    double distance = 16;
    for (Entity entity : world.getEntities()) {
        if (!isBoatEntity(entity) || existingBoats.contains(entity.entityId) || entity.isDead()) continue;
        if (entity.getPosition().distanceToSq(placementPosition) > 4) continue;
        Entity rider = entity.getRiddenByEntity();
        if (rider != null && rider.entityId != player.entityId) continue;
        double candidate = entity.distanceToSq(player.getPosition());
        if (candidate < distance) {
            distance = candidate;
            nearest = entity;
        }
    }
    return nearest;
}

int findAttackSlot() {
    for (int slot = 0; slot < 9; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack != null && "ItemSword".equals(stack.type)) return slot;
    }
    return originalSlot;
}

boolean isBoat(ItemStack stack) {
    return stack != null && stack.stackSize > 0 && ("boat".equals(stack.name) || "ItemBoat".equals(stack.type));
}

boolean isBoatEntity(Entity entity) {
    return entity != null && "EntityBoat".equals(entity.type);
}

void selectSlot(int slot) {
    if (slot < 0 || slot > 8) return;
    inventory.setSlot(slot);
    client.sendPacket(new C09(slot));
    selectedSlot = slot;
}

void restoreSlot() {
    if (originalSlot != -1 && client.getPlayer() != null && inventory.getSlot() == selectedSlot) {
        selectSlot(originalSlot);
    }
    originalSlot = -1;
    selectedSlot = -1;
}

int heldCount() {
    synchronized (transactionLock) {
        return transactions.size();
    }
}

void discardTransactions() {
    synchronized (transactionLock) {
        holdingTransactions = false;
        transactions.clear();
    }
}

void releaseTransactions() {
    synchronized (transactionLock) {
        holdingTransactions = false;
        if (client.getPlayer() != null && !worldChanged) {
            for (CPacket packet : transactions) client.sendPacketNoEvent(packet);
        }
        transactions.clear();
    }
}

void stop(String reason) {
    reset(true);
    message(reason);
}

void reset(boolean release) {
    int previousPhase = phase;
    phase = READY;
    if (release) releaseTransactions();
    else discardTransactions();
    if (release) restoreSlot();
    else {
        originalSlot = -1;
        selectedSlot = -1;
    }
    if (previousPhase >= WAITING && client.getPlayer() != null && !worldChanged) client.setMotion(0, 0, 0);
    boatId = -1;
    playerId = -1;
    placementAttempts = 0;
    placementPending = false;
    placementPosition = null;
    correctionReceived = false;
    worldChanged = false;
    queueFull = false;
    groundTicks = 0;
    leftGround = false;
    existingBoats.clear();
    attemptedSlots.clear();
}

void message(String text) {
    if (client.getPlayer() != null) client.print("\u00a7b[BoatFly] \u00a77" + text);
}

}
