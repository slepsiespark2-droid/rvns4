package ravens4.module.impl.player;

// Converted from the "BedDefender" script into a native module. Loads its defense presets
// from a bundled resource (assets/ravens4/bed_defenses/bed_defenses.json - the same file the
// original script would otherwise fetch from GitHub on first run) rather than porting the
// HTTP-fetch-on-first-run path, since the data is identical either way and this removes the
// runtime network dependency entirely.

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ravens4.event.PostPlayerInputEvent;
import ravens4.event.PreUpdateEvent;
import ravens4.helper.RotationHelper;
import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.BlockUtils;
import ravens4.utility.RenderUtils;
import ravens4.utility.Utils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BedDefender extends Module {

    private final ButtonSetting onlyTopBeds;
    private final SliderSetting delayAfterSwap;
    private final SliderSetting delayAfterAim;
    private final SliderSetting sneakHoldTicksSetting;
    private final SliderSetting fovSetting;
    private final SliderSetting defenseSlider;

    static class DefenseBlock {
        final String block;
        final int x, y, z;
        DefenseBlock(String block, int x, int y, int z) {
            this.block = block;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private String[] defenseNames = new String[0];
    private List<List<DefenseBlock>> defenses = new ArrayList<>();

    public BedDefender() {
        super("BedDefender", category.player);
        loadDefensesFromResource();

        this.registerSetting(onlyTopBeds = new ButtonSetting("Only Top Beds", true));
        this.registerSetting(delayAfterSwap = new SliderSetting("Delay After Swap", " ticks", 0, 0, 10, 1));
        this.registerSetting(delayAfterAim = new SliderSetting("Delay After Aiming", " ticks", 0, 0, 10, 1));
        this.registerSetting(sneakHoldTicksSetting = new SliderSetting("Sneak Hold Ticks", " ticks", 5, 0, 20, 1));
        this.registerSetting(fovSetting = new SliderSetting("FOV", "", 180, 0, 180, 1));
        this.registerSetting(defenseSlider = defenseNames.length == 0
                ? new SliderSetting("Defense", "", 0, new String[]{"No defenses found"})
                : new SliderSetting("Defense", "", 0, defenseNames));
    }

    private void loadDefensesFromResource() {
        try (InputStream stream = BedDefender.class.getResourceAsStream("/assets/ravens4/bed_defenses/bed_defenses.json")) {
            if (stream == null) return;
            JsonObject root = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> names = new ArrayList<>();
            List<List<DefenseBlock>> loaded = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonArray()) continue;
                JsonArray arr = entry.getValue().getAsJsonArray();
                List<DefenseBlock> list = new ArrayList<>();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    list.add(new DefenseBlock(obj.get("block").getAsString(), obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt()));
                }
                names.add(entry.getKey());
                loaded.add(list);
            }
            defenseNames = names.toArray(new String[0]);
            defenses = loaded;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    int i;
    boolean started;
    boolean sneaked;
    boolean onlyTopBedsCached;
    boolean pendingPlace;
    String lockedDirection = "";
    String face;
    int[] origin = null;
    int[] hitPos;
    Vec3 placePos;
    int[] renderTarget;
    float serverYaw;
    float serverPitch;
    int delayAfterSwapCached;
    int delayAfterAimCached;
    int swapTicks;
    int aimTicks;
    int sneakHoldTicksCached;
    int sneakHoldTicksRemaining;
    Map<String, Integer> inventoryCache = new HashMap<>();
    List<DefenseBlock> defense;

    @Override
    public void onEnable() {
        int index = defenses.isEmpty() ? -1 : (int) defenseSlider.getInput();
        defense = (index >= 0 && index < defenses.size()) ? defenses.get(index) : new ArrayList<>();
        i = 0;
        renderTarget = null;
        started = false;
        sneaked = false;
        lockedDirection = "";
        aimTicks = (int) delayAfterAim.getInput();
        EntityPlayerSP player = mc.thePlayer;
        if (player != null) {
            serverYaw = player.rotationYaw;
            serverPitch = player.rotationPitch;
        }
        inventoryCache.clear();
    }

    @Override
    public void onDisable() {
        if (sneaked) {
            setKeyPressed(mc.gameSettings.keyBindSneak, false);
        }
    }

    void setKeyPressed(KeyBinding binding, boolean pressed) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), pressed);
    }

    // ============================== rotation resolution ==============================

    float[] getRotations() {
        onlyTopBedsCached = onlyTopBeds.isToggled();
        delayAfterSwapCached = (int) delayAfterSwap.getInput();
        delayAfterAimCached = (int) delayAfterAim.getInput();
        sneakHoldTicksCached = (int) sneakHoldTicksSetting.getInput();

        float fov = (float) fovSetting.getInput();
        float maxYaw = fov;
        float maxPitch = Math.min(fov, 90f);

        EntityPlayerSP player = mc.thePlayer;
        if (player == null || defense == null || defense.isEmpty()) return null;

        if (!started) {
            int[] bh = findBed(8, player);
            if (bh == null) return null;
            origin = bh;
            int variant = getBlockVariant(bh[0], bh[1], bh[2]);
            switch (variant) {
                case 10: case 0: lockedDirection = "north"; break;
                case 8: case 2: lockedDirection = "south"; break;
                case 9: case 3: lockedDirection = "west"; break;
                case 11: case 1: lockedDirection = "east"; break;
                default: lockedDirection = ""; break;
            }
            started = true;
        }

        while (i < defense.size()) {
            int[] off = rotateOffset(defense.get(i), lockedDirection);
            int[] tgt = {origin[0] + off[0], origin[1] + off[1], origin[2] + off[2]};
            if (blockNameAt(tgt[0], tgt[1], tgt[2]).equals("air")) break;
            i++;
        }
        if (i >= defense.size()) {
            this.disable();
            return null;
        }

        int[] off = rotateOffset(defense.get(i), lockedDirection);
        int[] target = {origin[0] + off[0], origin[1] + off[1], origin[2] + off[2]};
        renderTarget = target;

        float baseYaw = serverYaw;
        float basePitch = serverPitch;

        float[] res0 = attemptPlace(baseYaw, basePitch, target);
        if (res0 != null) {
            if (res0[1] == -999f) return new float[]{baseYaw, basePitch};
            return res0;
        }

        String[] opp = {"DOWN", "UP", "SOUTH", "NORTH", "WEST", "EAST"};
        int[] dx = {0, 0, 0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0, 0, 0};
        int[] dz = {0, 0, -1, 1, 0, 0};

        Vec3 eye = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        float curYawW = normYaw(serverYaw);
        float curPit = serverPitch;
        float cliYawW = normYaw(player.rotationYaw);
        float cliPit = player.rotationPitch;

        double INSET = 0.05, STEP = 0.2, JIT = 0.2, insetTop = 1 - INSET - 1e-3, insetBot = INSET + 1e-3;
        int GRID = (int) Math.round(1 / STEP);

        List<Object[]> cands = new ArrayList<>((GRID + 1) * (GRID + 1) * 6);

        for (int fi = 0; fi < 6; fi++) {
            String faceName = opp[fi];
            int[] support = {target[0] + dx[fi], target[1] + dy[fi], target[2] + dz[fi]};

            String supportName = blockNameAt(support[0], support[1], support[2]);
            if (supportName.equals("air")) continue;
            if (onlyTopBedsCached && supportName.contains("bed") && !"UP".equals(faceName)) continue;

            for (int rr = 0; rr <= GRID; rr++) {
                boolean ltr = (rr & 1) == 0;
                double v = clamp01(rr * STEP + randomJitter(STEP * JIT));

                for (int cc = 0; cc <= GRID; cc++) {
                    double cu = clamp01(cc * STEP + randomJitter(STEP * JIT));
                    double u = ltr ? cu : 1 - cu;

                    double px, py, pz;
                    if (fi < 2) {
                        px = support[0] + u; pz = support[2] + v;
                        py = support[1] + (fi == 1 ? insetTop : insetBot);
                    } else if (fi < 4) {
                        px = support[0] + u; py = support[1] + v;
                        pz = support[2] + (fi == 2 ? insetTop : insetBot);
                    } else {
                        pz = support[2] + u; py = support[1] + v;
                        px = support[0] + (fi == 5 ? insetTop : insetBot);
                    }

                    float[] rotW = getRotationsWrapped(eye, px, py, pz);
                    float yawW = rotW[0], pit = rotW[1];

                    if (Math.abs(wrapYawDelta(cliYawW, yawW)) > maxYaw) continue;
                    if (Math.abs(pit - cliPit) > maxPitch) continue;
                    if (Math.abs(pit) > 90f) continue;

                    double cost = Math.abs((double) wrapYawDelta(curYawW, yawW)) + Math.abs((double) (pit - curPit)) + ("UP".equals(faceName) ? -0.25 : 0);
                    cands.add(new Object[]{cost, yawW, pit});
                }
            }
        }

        if (cands.isEmpty()) {
            if (i >= defense.size()) this.disable();
            return null;
        }

        cands.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));

        for (Object[] cand : cands) {
            float yawW = (Float) cand[1];
            float pit = (Float) cand[2];
            float yawUnwrapped = unwrapYaw(yawW, serverYaw);

            float[] res = attemptPlace(yawUnwrapped, pit, target);
            if (res != null) return (res[1] == -999f) ? new float[]{yawUnwrapped, pit} : res;
        }

        if (i >= defense.size()) this.disable();
        return null;
    }

    double randomJitter(double bound) {
        return (Math.random() * 2 - 1) * bound;
    }

    double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    float[] attemptPlace(float yaw, float pitch, int[] target) {
        if (i >= defense.size()) return null;

        Object[] ray = rayCastForFace(yaw, pitch);
        if (ray == null) return null;

        int[] hit = (int[]) ray[0];
        String faceStr = (String) ray[2];
        int[] place = offsetByFace(hit, faceStr);
        if (!(place[0] == target[0] && place[1] == target[1] && place[2] == target[2])) return null;

        String targetBlock = blockNameAt(hit[0], hit[1], hit[2]);
        if (onlyTopBedsCached && !faceStr.equals("UP") && targetBlock.contains("bed")) return null;

        DefenseBlock cur = defense.get(i);
        String block = cur.block;

        EntityPlayerSP player = mc.thePlayer;
        int curSlot = player.inventory.currentItem;
        int wantSlot = getMatchingInventorySlot(block);
        if (wantSlot == -1) {
            this.disable();
            return null;
        }

        if (curSlot != wantSlot) {
            player.inventory.currentItem = wantSlot;
            swapTicks = delayAfterSwapCached;
        }

        ItemStack held = player.getHeldItem();
        Vec3 hitAbs = (Vec3) ray[3];
        if (canPlaceBlockOnSide(held, hit, faceStr)) {
            RotationHelper.get().forceMovementFix = true;

            if (swapTicks-- > 0) return new float[]{-999f, -999f};

            if (!Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) && targetBlock.contains("bed")) {
                setKeyPressed(mc.gameSettings.keyBindSneak, true);
                sneaked = true;
                sneakHoldTicksRemaining = sneakHoldTicksCached;
                return new float[]{-999f, -999f};
            }

            if (aimTicks-- > 0 || Math.abs(yaw - serverYaw) > 25 || Math.abs(pitch - serverPitch) > 25)
                return new float[]{yaw, pitch};

            aimTicks = delayAfterAimCached;

            hitPos = hit;
            face = faceStr;
            placePos = hitAbs;
            pendingPlace = true;

            return new float[]{yaw, pitch};
        }
        return null;
    }

    boolean canPlaceBlockOnSide(ItemStack stack, int[] pos, String faceName) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        return ((ItemBlock) stack.getItem()).canPlaceBlockOnSide(mc.theWorld, new BlockPos(pos[0], pos[1], pos[2]), EnumFacing.valueOf(faceName), mc.thePlayer, stack);
    }

    // ============================== angle helpers ==============================

    float normYaw(float yaw) {
        yaw = ((yaw % 360f) + 360f) % 360f;
        return (yaw > 180f) ? (yaw - 360f) : yaw;
    }

    float wrapYawDelta(float base, float target) {
        float d = target - base;
        while (d <= -180f) d += 360f;
        while (d > 180f) d -= 360f;
        return d;
    }

    float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
    }

    float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord, dy = ty - eye.yCoord, dz = tz - eye.zCoord;
        double hd = Math.sqrt(dx * dx + dz * dz);
        float yawWrapped = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        yawWrapped = normYaw(yawWrapped);
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
        return new float[]{yawWrapped, pitch};
    }

    // ============================== event hooks ==============================

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!this.isEnabled()) return;
        if (sneaked) {
            if (sneakHoldTicksRemaining > 0) {
                sneakHoldTicksRemaining--;
            } else {
                setKeyPressed(mc.gameSettings.keyBindSneak, false);
                sneaked = false;
            }
        }

        if (!pendingPlace) return;
        pendingPlace = false;
        if (placeBlockReal(hitPos, face, placePos)) {
            mc.thePlayer.swingItem();
            i++;
        }
    }

    boolean rotationActive = false;
    long rotationStartedAt = 0L;
    long rotationDuration = 50L;
    float rotationStartYaw = 0f;
    float rotationStartPitch = 0f;
    float rotationTargetYaw = 0f;
    float rotationTargetPitch = 0f;

    void setRotationTarget(float targetYaw, float targetPitch, long duration) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        applySmoothedRotation();
        rotationStartYaw = player.rotationYaw;
        rotationStartPitch = player.rotationPitch;
        rotationTargetYaw = rotationStartYaw + wrapAngle(targetYaw - rotationStartYaw);
        rotationTargetPitch = targetPitch;
        rotationStartedAt = System.currentTimeMillis();
        rotationDuration = Math.max(1L, duration);
        rotationActive = true;
    }

    void applySmoothedRotation() {
        if (!rotationActive) return;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;

        double progress = (double) (System.currentTimeMillis() - rotationStartedAt) / (double) rotationDuration;
        if (progress < 0.0) progress = 0.0;
        if (progress > 1.0) progress = 1.0;

        player.rotationYaw = rotationStartYaw + (rotationTargetYaw - rotationStartYaw) * (float) progress;
        player.rotationPitch = rotationStartPitch + (rotationTargetPitch - rotationStartPitch) * (float) progress;
        if (progress >= 1.0) rotationActive = false;
    }

    float wrapAngle(float angle) {
        angle = angle % 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    @SubscribeEvent
    public void onRenderTick(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent e) {
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        if (!this.isEnabled()) return;
        applySmoothedRotation();
    }

    @SubscribeEvent
    public void onPostPlayerInput(PostPlayerInputEvent e) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        float[] rotations = getRotations();
        if (rotations == null) return;
        setRotationTarget(rotations[0], rotations[1], 50L);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!this.isEnabled() || renderTarget == null) return;
        RenderUtils.renderBlock(new BlockPos(renderTarget[0], renderTarget[1], renderTarget[2]), 0x00FF00, true, true);
    }

    // Tracks the last server-acked rotation from outgoing look/pos-look packets - matches the
    // original script's C05/C06 checks (the two C03PacketPlayer subtypes that carry rotation).
    // getYaw()/getPitch() are declared on the base C03PacketPlayer class itself (confirmed
    // against this codebase's own C03.java wrapper), so no subtype cast is needed here.
    @SubscribeEvent
    public void onSendPacket(ravens4.event.SendPacketEvent e) {
        if (!this.isEnabled()) return;
        net.minecraft.network.Packet<?> packet = e.getPacket();
        if (packet instanceof C03PacketPlayer.C05PacketPlayerLook || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            C03PacketPlayer posPacket = (C03PacketPlayer) packet;
            serverYaw = posPacket.getYaw();
            serverPitch = posPacket.getPitch();
        }
    }

    // ============================== block / inventory helpers ==============================

    String blockNameAt(int x, int y, int z) {
        IBlockState state = BlockUtils.getBlockState(new BlockPos(x, y, z));
        if (state == null) return "air";
        String name = state.getBlock().getRegistryName();
        return name == null ? "air" : name.replace("minecraft:", "").toLowerCase();
    }

    int getBlockVariant(int x, int y, int z) {
        IBlockState state = BlockUtils.getBlockState(new BlockPos(x, y, z));
        if (state == null) return -1;
        return state.getBlock().getMetaFromState(state);
    }

    boolean placeBlockReal(int[] targetPos, String faceName, Vec3 hitVecAbs) {
        return mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(),
                new BlockPos(targetPos[0], targetPos[1], targetPos[2]), EnumFacing.valueOf(faceName), hitVecAbs);
    }

    int getMatchingInventorySlot(String blockName) {
        String key = blockName.toLowerCase();
        Integer slot = inventoryCache.get(key);
        if (slot != null) {
            ItemStack cachedItem = mc.thePlayer.inventory.getStackInSlot(slot);
            if (cachedItem != null && itemName(cachedItem).equalsIgnoreCase(blockName)) {
                return slot;
            }
        }
        for (int s = 0; s < 9; s++) {
            ItemStack item = mc.thePlayer.inventory.getStackInSlot(s);
            if (item != null && itemName(item).equalsIgnoreCase(blockName)) {
                inventoryCache.put(key, s);
                return s;
            }
        }
        return -1;
    }

    String itemName(ItemStack stack) {
        if (stack == null || stack.getItem().getRegistryName() == null) return "";
        return stack.getItem().getRegistryName().replace("minecraft:", "");
    }

    // ============================== geometry helpers ==============================

    int[] rotateOffset(DefenseBlock offset, String direction) {
        int x = offset.x, y = offset.y, z = offset.z;
        switch (direction) {
            case "north": return new int[]{x, y, z};
            case "south": return new int[]{-x, y, -z};
            case "east": return new int[]{-z, y, x};
            case "west": return new int[]{z, y, -x};
            default: return new int[]{x, y, z};
        }
    }

    int[] offsetByFace(int[] pos, String faceName) {
        switch (faceName) {
            case "UP": return new int[]{pos[0], pos[1] + 1, pos[2]};
            case "DOWN": return new int[]{pos[0], pos[1] - 1, pos[2]};
            case "NORTH": return new int[]{pos[0], pos[1], pos[2] - 1};
            case "SOUTH": return new int[]{pos[0], pos[1], pos[2] + 1};
            case "EAST": return new int[]{pos[0] + 1, pos[1], pos[2]};
            case "WEST": return new int[]{pos[0] - 1, pos[1], pos[2]};
            default: return pos;
        }
    }

    int[] findBed(int range, EntityPlayerSP player) {
        int centerX = (int) Math.floor(player.posX);
        int centerY = (int) Math.floor(player.posY);
        int centerZ = (int) Math.floor(player.posZ);
        int startX = centerX - range, endX = centerX + range;
        int startY = centerY - range, endY = centerY + range;
        int startZ = centerZ - range, endZ = centerZ + range;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (!blockNameAt(x, y, z).contains("bed")) continue;
                    int variant = getBlockVariant(x, y, z);
                    int[] head = null, foot = null;
                    switch (variant) {
                        case 0: foot = new int[]{x, y, z}; head = new int[]{x, y, z + 1}; break;
                        case 1: foot = new int[]{x, y, z}; head = new int[]{x - 1, y, z}; break;
                        case 2: foot = new int[]{x, y, z}; head = new int[]{x, y, z - 1}; break;
                        case 3: foot = new int[]{x, y, z}; head = new int[]{x + 1, y, z}; break;
                        case 8: head = new int[]{x, y, z}; foot = new int[]{x, y, z - 1}; break;
                        case 9: head = new int[]{x, y, z}; foot = new int[]{x + 1, y, z}; break;
                        case 10: head = new int[]{x, y, z}; foot = new int[]{x, y, z + 1}; break;
                        case 11: head = new int[]{x, y, z}; foot = new int[]{x - 1, y, z}; break;
                        default: break;
                    }
                    if (head != null && foot != null) {
                        double headDist = distSq(head, player);
                        double footDist = distSq(foot, player);
                        return headDist > footDist ? head : foot;
                    }
                }
            }
        }
        return null;
    }

    double distSq(int[] pos, EntityPlayerSP player) {
        double dx = pos[0] - player.posX;
        double dy = pos[1] - player.posY;
        double dz = pos[2] - player.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    // ---- arbitrary-yaw/pitch raycast returning {supportBlock, localOffset, faceName, absHitVec} ----
    Object[] rayCastForFace(float yaw, float pitch) {
        Vec3 eyeVec = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = Utils.getLookVec(yaw, pitch);
        double distance = 4.5;
        Vec3 endVec = eyeVec.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyeVec, endVec, false, false, false);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        int[] pos = {mop.getBlockPos().getX(), mop.getBlockPos().getY(), mop.getBlockPos().getZ()};
        Vec3 offset = new Vec3(mop.hitVec.xCoord - pos[0], mop.hitVec.yCoord - pos[1], mop.hitVec.zCoord - pos[2]);
        return new Object[]{pos, offset, mop.sideHit.name(), mop.hitVec};
    }
}
