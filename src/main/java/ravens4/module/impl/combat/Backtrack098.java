package ravens4.module.impl.combat;

import ravens4.event.AttackEvent;
import ravens4.event.GameTickEvent;
import ravens4.event.ReceivePacketEvent;
import ravens4.lag.fdp.S4BacktrackPacketHandler;
import ravens4.mixin.impl.accessor.IAccessorS14PacketEntity;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.player.AntiBot;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.ColorSetting;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.RenderUtils;
import ravens4.utility.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.server.*;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class Backtrack098 extends Module {
    private static final String[] NON_DELAYED_SOUND_SUBSTRINGS = {"game.player.hurt", "game.player.die"};

    private final SliderSetting nextLagReachDelay;
    private final SliderSetting maxDelay;
    private final SliderSetting minDelay;
    private final SliderSetting distanceMin;
    private final SliderSetting distanceMax;
    private final ButtonSetting smart;
    private final ButtonSetting esp;
    private final ButtonSetting showInFirstPerson;
    private final ColorSetting espColor;
    private final SliderSetting espLineWidth;
    private final ButtonSetting espFilled;
    private final ButtonSetting onFocus;

    private final S4BacktrackPacketHandler fdpPackets = new S4BacktrackPacketHandler();
    private final Map<Integer, Vec3> truePositions = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public EntityLivingBase target;
    private boolean shouldRender = true;
    private boolean ignoreWholeTick;
    private long delayForNextLagReach;
    private int modernDelay;
    private boolean modernDelayChanged;
    private int lastMinDelay = -1;
    private int lastMaxDelay = -1;
    private int rangeMissTicks;

    private static final int RANGE_MISS_GRACE_TICKS = 3;

    public Backtrack098() {
        super("Backtrack098", category.combat);
        this.registerSetting(nextLagReachDelay = new SliderSetting("NextLagReachDelay", "ms", 0, 0, 1000, 10));
        this.registerSetting(maxDelay = new SliderSetting("MaxDelay", "ms", 80, 0, 1000, 10));
        this.registerSetting(minDelay = new SliderSetting("MinDelay", "ms", 80, 0, 1000, 10));
        this.registerSetting(distanceMin = new SliderSetting("DistanceMin", 0.0, 0.0, 6.0, 0.1));
        this.registerSetting(distanceMax = new SliderSetting("DistanceMax", 3.0, 0.0, 6.0, 0.1));
        this.registerSetting(smart = new ButtonSetting("Smart", true));
        this.registerSetting(onFocus = new ButtonSetting("On focus", true));
        this.registerSetting(new DescriptionSetting("ESP"));
        this.registerSetting(esp = new ButtonSetting("ESP", true));
        this.registerSetting(showInFirstPerson = new ButtonSetting("Show in first person", false));
        this.registerSetting(espColor = new ColorSetting("ESP color", 255, 0, 0, 100));
        this.registerSetting(espLineWidth = new SliderSetting("ESP line width", 2.0, 1.0, 5.0, 0.5));
        this.registerSetting(espFilled = new ButtonSetting("ESP filled", false));
        this.modernDelay = randomDelay();
        this.lastMinDelay = (int) minDelay.getInput();
        this.lastMaxDelay = (int) maxDelay.getInput();
    }

    @Override
    public void onEnable() {
        modernDelay = randomDelay();
        modernDelayChanged = false;
        lastMinDelay = (int) minDelay.getInput();
        lastMaxDelay = (int) maxDelay.getInput();
        reset();
    }

    @Override
    public void onDisable() {
        clearPackets(true, true);
        truePositions.clear();
    }

    @Override
    public String getInfo() {
        return String.valueOf(supposedDelay());
    }

    @Override
    public void guiUpdate() {
        if (minDelay.getInput() > maxDelay.getInput()) {
            minDelay.setValue(maxDelay.getInput());
        }
        int currentMinDelay = (int) minDelay.getInput();
        int currentMaxDelay = (int) maxDelay.getInput();
        if (currentMinDelay != lastMinDelay || currentMaxDelay != lastMaxDelay) {
            modernDelay = randomDelay();
            modernDelayChanged = false;
            lastMinDelay = currentMinDelay;
            lastMaxDelay = currentMaxDelay;
        }
        if (distanceMin.getInput() > distanceMax.getInput()) {
            distanceMin.setValue(distanceMax.getInput());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (isBlinkingReceive() || event.isCanceled()) {
            return;
        }

        updateTruePosition(packet);

        if (mc.isSingleplayer() || mc.getCurrentServerData() == null) {
            clearPackets(true, true);
            return;
        }

        if (fdpPackets.isPacketQueueEmpty() && fdpPackets.isScheduledQueueEmpty() && !shouldLagReach()) {
            return;
        }

        if (packet instanceof C00Handshake || packet instanceof C00PacketServerQuery || packet instanceof S02PacketChat || packet instanceof S01PacketPong) {
            return;
        }

        if (packet instanceof S29PacketSoundEffect) {
            String soundName = ((S29PacketSoundEffect) packet).getSoundName();
            for (String substring : NON_DELAYED_SOUND_SUBSTRINGS) {
                if (soundName != null && soundName.contains(substring)) {
                    return;
                }
            }
        }

        if (packet instanceof S06PacketUpdateHealth && ((S06PacketUpdateHealth) packet).getHealth() <= 0) {
            clearPackets(true, true);
            return;
        }

        if (packet instanceof S13PacketDestroyEntities && target != null) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (id == target.getEntityId()) {
                    clearPackets(true, true);
                    reset();
                    return;
                }
            }
        }

        if (packet instanceof S1CPacketEntityMetadata && target != null) {
            S1CPacketEntityMetadata metadataPacket = (S1CPacketEntityMetadata) packet;
            if (metadataPacket.getEntityId() == target.getEntityId()) {
                List<DataWatcher.WatchableObject> metadata = metadataPacket.func_149376_c();
                if (metadata != null) {
                    for (DataWatcher.WatchableObject watchableObject : metadata) {
                        if (watchableObject.getDataValueId() == 6) {
                            Object object = watchableObject.getObject();
                            Double health = parseDouble(object);
                            if (health != null && !health.isNaN() && health <= 0.0D) {
                                clearPackets(true, true);
                                reset();
                                return;
                            }
                        }
                    }
                }
                return;
            }
        }

        if (packet instanceof S19PacketEntityStatus && target != null) {
            Entity entity = ((S19PacketEntityStatus) packet).getEntity(mc.theWorld);
            if (entity != null && entity.getEntityId() == target.getEntityId()) {
                return;
            }
        }

        if (target != null) {
            int packetEntityId = packetEntityId(packet);
            if (packetEntityId == target.getEntityId() && (packet instanceof S14PacketEntity || packet instanceof S18PacketEntityTeleport)) {
                fdpPackets.addPosition(truePosition(target));
            }
        }

        event.setCanceled(true);
        fdpPackets.queue(packet);
    }

    @SubscribeEvent
    public void onGameLoop(GameTickEvent event) {
        if (Utils.nullCheck()) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity instanceof EntityLivingBase) {
                    truePositions.putIfAbsent(entity.getEntityId(), new Vec3(entity.posX, entity.posY, entity.posZ));
                }
            }
        }

        if (onFocus.isToggled() && !mc.inGameHasFocus) {
            ignoreWholeTick = false;
            return;
        }

        EntityLivingBase currentTarget = target;

        if (shouldLagReach() && currentTarget != null) {
            if (!isBlinkingReceive()) {
                double trueDist = mc.thePlayer.getDistance(trueX(currentTarget), trueY(currentTarget), trueZ(currentTarget));
                double dist = mc.thePlayer.getDistance(currentTarget.posX, currentTarget.posY, currentTarget.posZ);

                if (trueDist <= 10.0F && (!smart.isToggled() || trueDist >= dist)) {
                    shouldRender = true;

                    if (inDistanceRange(distanceToEntityBox(mc.thePlayer, currentTarget.getEntityBoundingBox()))) {
                        handlePackets();
                    } else {
                        handlePacketsRange();
                    }
                } else {
                    clear();
                }
            }
        } else {
            clear();
        }

        boolean shouldChangeDelay = fdpPackets.isPacketQueueEmpty() && fdpPackets.isScheduledQueueEmpty();
        if (!shouldChangeDelay) {
            modernDelayChanged = false;
        }

        if (shouldChangeDelay && !modernDelayChanged && !shouldLagReach()) {
            delayForNextLagReach = System.currentTimeMillis() + (long) nextLagReachDelay.getInput();
            modernDelay = randomDelay();
            modernDelayChanged = true;
        }

        ignoreWholeTick = false;
    }

    @SubscribeEvent
    public void onAttack(AttackEvent event) {
        if (!(event.target instanceof EntityLivingBase) || !isSelected((EntityLivingBase) event.target)) {
            return;
        }

        if (target != event.target) {
            clearPackets(true, true);
            reset();
        }

        target = (EntityLivingBase) event.target;
    }

    @SubscribeEvent
    public void onRender3D(RenderWorldLastEvent event) {
        if (!Utils.nullCheck() || !shouldLagReach() || !shouldRender || target == null || !esp.isToggled()) {
            return;
        }
        if (mc.gameSettings.thirdPersonView == 0 && !showInFirstPerson.isToggled()) {
            return;
        }

        renderModern(event.partialTicks);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        clearPackets(false, true);
        target = null;
        truePositions.clear();
    }

    private void handlePackets() {
        fdpPackets.processOlderThan(supposedDelay());
    }

    private void handlePacketsRange() {
        long time = getRangeTime();
        if (time == -1L) {
            rangeMissTicks++;
            if (rangeMissTicks >= RANGE_MISS_GRACE_TICKS) {
                clearPackets(true, true);
                rangeMissTicks = 0;
            }
            return;
        }

        rangeMissTicks = 0;
        fdpPackets.processUntil(time);
        fdpPackets.removePositionsOlderThan(time);
    }

    private long getRangeTime() {
        EntityLivingBase currentTarget = target;
        if (currentTarget == null) {
            return 0L;
        }

        long time = 0L;
        boolean found = false;

        for (S4BacktrackPacketHandler.PositionData data : fdpPackets.positions()) {
            time = data.time;
            Vec3 targetPos = new Vec3(currentTarget.posX, currentTarget.posY, currentTarget.posZ);
            Vec3 delta = data.position.subtract(targetPos);
            AxisAlignedBB targetBox = currentTarget.getEntityBoundingBox().offset(delta.xCoord, delta.yCoord, delta.zCoord);

            if (inDistanceRange(distanceToEntityBox(mc.thePlayer, targetBox))) {
                found = true;
                break;
            }
        }

        return found ? time : -1L;
    }

    private void clearPackets(boolean handlePackets, boolean stopRendering) {
        fdpPackets.clear(handlePackets);
        if (stopRendering) {
            shouldRender = false;
            ignoreWholeTick = true;
        }
    }

    private void clear() {
        clearPackets(true, true);
    }

    private void reset() {
        target = null;
        rangeMissTicks = 0;
    }

    private void renderModern(float partialTicks) {
        double x = trueX(target);
        double y = trueY(target);
        double z = trueZ(target);

        AxisAlignedBB worldBox = target.getEntityBoundingBox().offset(x - target.posX, y - target.posY, z - target.posZ);
        Vec3 cameraPos = Utils.getCameraPos(partialTicks);
        if (worldBox.isVecInside(cameraPos)) {
            return;
        }

        double viewX = mc.getRenderManager().viewerPosX;
        double viewY = mc.getRenderManager().viewerPosY;
        double viewZ = mc.getRenderManager().viewerPosZ;
        AxisAlignedBB box = worldBox.offset(-viewX, -viewY, -viewZ);

        float r = espColor.getRed() / 255.0f;
        float g = espColor.getGreen() / 255.0f;
        float b = espColor.getBlue() / 255.0f;
        float a = espColor.getAlpha() / 255.0f;

        GL11.glPushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (espFilled.isToggled()) {
            RenderUtils.drawBoundingBox(box, r, g, b, a);
        }

        GL11.glLineWidth((float) espLineWidth.getInput());
        GL11.glColor4f(r, g, b, a);
        RenderGlobal.drawSelectionBoundingBox(box);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glPopMatrix();
    }

    private boolean shouldLagReach() {
        return mc.thePlayer != null
                && mc.theWorld != null
                && target != null
                && mc.thePlayer.getHealth() > 0
                && (target.getHealth() > 0 || Float.isNaN(target.getHealth()))
                && mc.playerController.getCurrentGameType() != WorldSettings.GameType.SPECTATOR
                && System.currentTimeMillis() >= delayForNextLagReach
                && isSelected(target)
                && mc.thePlayer.ticksExisted > 20
                && !ignoreWholeTick;
    }

    private boolean isSelected(EntityLivingBase entity) {
        if (entity == null || entity == mc.thePlayer || entity.isDead || entity.deathTime != 0) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return !Utils.isFriended(player) && !AntiBot.isBot(player) && !Utils.isTeammate(player);
        }
        return false;
    }

    private boolean isBlinkingReceive() {
        return ModuleManager.blink != null && ModuleManager.blink.isEnabled() && ModuleManager.blink.delaysInboundPackets();
    }

    private int supposedDelay() {
        return modernDelay;
    }

    private int randomDelay() {
        int min = (int) Math.min(minDelay.getInput(), maxDelay.getInput());
        int max = (int) Math.max(minDelay.getInput(), maxDelay.getInput());
        return min == max ? min : min + random.nextInt(max - min + 1);
    }

    private boolean inDistanceRange(double distance) {
        double min = Math.min(distanceMin.getInput(), distanceMax.getInput());
        double max = Math.max(distanceMin.getInput(), distanceMax.getInput());
        return distance >= min && distance <= max;
    }

    private static double distanceToEntityBox(Entity from, AxisAlignedBB box) {
        if (from == null || box == null) {
            return Double.MAX_VALUE;
        }
        Vec3 eye = from.getPositionEyes(1.0f);
        double x = clamp(eye.xCoord, box.minX, box.maxX);
        double y = clamp(eye.yCoord, box.minY, box.maxY);
        double z = clamp(eye.zCoord, box.minZ, box.maxZ);
        return eye.distanceTo(new Vec3(x, y, z));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateTruePosition(Packet<?> packet) {
        if (packet instanceof S0CPacketSpawnPlayer) {
            S0CPacketSpawnPlayer spawn = (S0CPacketSpawnPlayer) packet;
            truePositions.put(spawn.getEntityID(), new Vec3(spawn.getX() / 32.0D, spawn.getY() / 32.0D, spawn.getZ() / 32.0D));
            return;
        }

        if (packet instanceof S14PacketEntity) {
            int id = packetEntityId(packet);
            Entity entity = mc.theWorld != null ? mc.theWorld.getEntityByID(id) : null;
            Vec3 current = truePositions.get(id);
            if (current == null && entity != null) {
                current = new Vec3(entity.posX, entity.posY, entity.posZ);
            }
            if (current != null) {
                S14PacketEntity move = (S14PacketEntity) packet;
                truePositions.put(id, new Vec3(
                        current.xCoord + move.func_149062_c() / 32.0D,
                        current.yCoord + move.func_149061_d() / 32.0D,
                        current.zCoord + move.func_149064_e() / 32.0D
                ));
            }
            return;
        }

        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            truePositions.put(teleport.getEntityId(), new Vec3(
                    teleport.getX() / 32.0D,
                    teleport.getY() / 32.0D,
                    teleport.getZ() / 32.0D
            ));
        }
    }

    private int packetEntityId(Packet<?> packet) {
        if (packet instanceof S14PacketEntity) {
            return ((IAccessorS14PacketEntity) packet).getEntityId();
        }
        if (packet instanceof S18PacketEntityTeleport) {
            return ((S18PacketEntityTeleport) packet).getEntityId();
        }
        return -1;
    }

    private Vec3 truePosition(Entity entity) {
        Vec3 tracked = truePositions.get(entity.getEntityId());
        return tracked != null ? tracked : new Vec3(entity.serverPosX / 32.0D, entity.serverPosY / 32.0D, entity.serverPosZ / 32.0D);
    }

    private double trueX(Entity entity) {
        return truePosition(entity).xCoord;
    }

    private double trueY(Entity entity) {
        return truePosition(entity).yCoord;
    }

    private double trueZ(Entity entity) {
        return truePosition(entity).zCoord;
    }

    private Double parseDouble(Object object) {
        if (object instanceof Number) {
            return ((Number) object).doubleValue();
        }
        if (object == null) {
            return null;
        }
        try {
            return Double.parseDouble(object.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
