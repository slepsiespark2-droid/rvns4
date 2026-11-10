package ravens4.module.impl.combat;

import ravens4.event.PreUpdateEvent;
import ravens4.event.ReceivePacketEvent;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.player.AntiBot;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.RenderUtils;
import ravens4.utility.RotationUtils;
import ravens4.utility.Utils;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Backtrack: when the target moves out of your reach, the incoming position updates for it are held
 * for a short time so you keep hitting the position it was at a moment ago. Everything is released
 * as soon as holding stops helping (it moves back into reach, you take knockback, you get teleported,
 * the target changes, ...).
 */
public class Backtrack extends Module {
    private static final double HITTABLE_HURT_TIME_FALLBACK = 5.0;

    private final SliderSetting delay;
    private final SliderSetting distanceMin;
    private final SliderSetting distanceMax;
    private final SliderSetting cooldown;
    private final SliderSetting fov;
    private final SliderSetting hittableHurtTime;
    private final ButtonSetting disableAdventure;
    private final ButtonSetting disableWhileChasing;
    private final ButtonSetting flushOnDamage;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting showServerPosition;
    private final ButtonSetting onlyHittable;
    private final ButtonSetting onlyMovement;
    private final ButtonSetting weaponOnly;

    private final TreeMap<Integer, List<Packet<?>>> held = new TreeMap<>();
    private final Object lock = new Object();
    private int tick;
    private volatile int delayTicks;
    private volatile boolean holding;
    private volatile boolean ready;
    public volatile EntityLivingBase target;
    private volatile Vec3 serverPos;
    private volatile EntityLivingBase ghost;
    private volatile long lastFlush;
    private double minRange;
    private double maxRange;
    private double maxRangeSq;
    private double scanRangeSq;

    public Backtrack() {
        super("Backtrack", category.combat);
        this.registerSetting(delay = new SliderSetting("Delay", "ms", 100, 0, 500, 10));
        this.registerSetting(distanceMin = new SliderSetting("Distance (min)", 3.0, 0.0, 6.0, 0.1));
        this.registerSetting(distanceMax = new SliderSetting("Distance (max)", 4.5, 0.0, 6.0, 0.1));
        this.registerSetting(cooldown = new SliderSetting("Cooldown", "s", 0, 0, 10, 0.5));
        this.registerSetting(fov = new SliderSetting("FOV", 180, 30, 360, 10));
        this.registerSetting(hittableHurtTime = new SliderSetting("Hittable hurt time", HITTABLE_HURT_TIME_FALLBACK, 0, 10, 1));
        this.registerSetting(disableAdventure = new ButtonSetting("Disable adventure", false));
        this.registerSetting(disableWhileChasing = new ButtonSetting("Disable while chasing", true));
        this.registerSetting(flushOnDamage = new ButtonSetting("Flush on damage", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(showServerPosition = new ButtonSetting("Show server position", true));
        this.registerSetting(onlyHittable = new ButtonSetting("Only hittable", false));
        this.registerSetting(onlyMovement = new ButtonSetting("Only movement", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
        guiUpdate();
    }

    @Override
    public String getInfo() {
        return String.valueOf((int) delay.getInput());
    }

    @Override
    public void onDisable() {
        flush();
    }

    @Override
    public void guiUpdate() {
        if (distanceMin.getInput() > distanceMax.getInput()) {
            distanceMin.setValue(distanceMax.getInput());
        }
        minRange = distanceMin.getInput();
        maxRange = distanceMax.getInput();
        double d = maxRange + 1.2;
        double d2 = maxRange + 2.5;
        maxRangeSq = d * d;
        scanRangeSq = d2 * d2;
    }

    @Override
    public void guiButtonToggled(ButtonSetting b) {
        if (b == onlyMovement) {
            flush();
        } else if (b == showServerPosition) {
            if (!b.isToggled()) {
                ghost = null;
            } else if (holding && target != null) {
                makeGhost(target);
            }
        }
    }

    // ------------------------------------------------------------------ packets

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPacket(ReceivePacketEvent event) {
        if (!this.isEnabled() || event.isCanceled()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        try {
            if (!Utils.nullCheck() || otherLagActive()) {
                if (holding) {
                    synchronized (lock) {
                        held.clear();
                        resetState();
                    }
                }
                return;
            }
            if (holding) {
                boolean flush = packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect;
                if (!flush && flushOnDamage.isToggled() && packet instanceof S12PacketEntityVelocity
                        && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
                    flush = true;
                }
                if (flush) {
                    flush();
                    return;
                }
            } else if (!ready) {
                return;
            }

            Vec3 pos = serverPos;
            EntityLivingBase t = target;
            if (pos == null || t == null) {
                return;
            }
            double x = pos.xCoord;
            double y = pos.yCoord;
            double z = pos.zCoord;
            boolean moves = false;
            boolean teleport = false;
            if (packet instanceof S14PacketEntity.S15PacketEntityRelMove || packet instanceof S14PacketEntity.S17PacketEntityLookMove) {
                S14PacketEntity p = (S14PacketEntity) packet;
                Entity entity = p.getEntity(mc.theWorld);
                if (entity != null && entity.getEntityId() == t.getEntityId()) {
                    moves = true;
                    x += p.func_149062_c() / 32.0;
                    y += p.func_149061_d() / 32.0;
                    z += p.func_149064_e() / 32.0;
                }
            } else if (packet instanceof S18PacketEntityTeleport) {
                S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
                if (p.getEntityId() == t.getEntityId()) {
                    teleport = true;
                    moves = true;
                    x = p.getX() / 32.0;
                    y = p.getY() / 32.0;
                    z = p.getZ() / 32.0;
                }
            }

            if (moves) {
                boolean flush = true;
                double distSq = mc.thePlayer.getDistanceSq(x, y, z);
                AxisAlignedBB box = t.getEntityBoundingBox();
                AxisAlignedBB newBox = box.offset(x - t.posX, y - t.posY, z - t.posZ);
                float[] rot = rotationsToBox(newBox);
                // the new position is out of the short reach but still inside the long one
                if (distSq <= maxRangeSq && !rayHits(newBox, rot[0], rot[1], minRange)) {
                    if (rayHits(newBox, rot[0], rot[1], maxRange)) {
                        AxisAlignedBB oldBox = box.offset(pos.xCoord - t.posX, pos.yCoord - t.posY, pos.zCoord - t.posZ);
                        float[] oldRot = rotationsToBox(oldBox);
                        double newDist = rayDistanceSq(newBox, rot[0], rot[1], maxRange);
                        double oldDist = rayDistanceSq(oldBox, oldRot[0], oldRot[1], maxRange + 2.0);
                        // holding only helps if the old position is the closer one
                        if (oldDist >= 0.0 && newDist > oldDist && (!teleport || newDist - oldDist < 64.0)) {
                            if (!holding && canStart()) {
                                start();
                            }
                            serverPos = new Vec3(x, y, z);
                            EntityLivingBase g = ghost;
                            if (g instanceof EntityOtherPlayerMP) {
                                g.setPositionAndRotation2(x, y, z, 0.0f, 0.0f, 3, teleport);
                            }
                            flush = false;
                        }
                    }
                }
                if (holding && flush) {
                    flush();
                    return;
                }
            }

            if (holding && (onlyMovement.isToggled() ? moves : !passesThrough(packet))) {
                hold(packet);
                event.setCanceled(true);
            }
        } catch (Throwable throwable) {
            Utils.sendDebugMessage("backtrack error: " + throwable.getClass().getSimpleName());
        }
    }

    private static boolean passesThrough(Packet<?> packet) {
        return packet instanceof S02PacketChat || packet instanceof S06PacketUpdateHealth || packet instanceof S14PacketEntity.S16PacketEntityLook
                || packet instanceof S19PacketEntityStatus || packet instanceof S22PacketMultiBlockChange || packet instanceof S23PacketBlockChange
                || packet instanceof S29PacketSoundEffect || packet instanceof S2APacketParticles || packet instanceof S3BPacketScoreboardObjective
                || packet instanceof S3CPacketUpdateScore || packet instanceof S3DPacketDisplayScoreboard || packet instanceof S3EPacketTeams;
    }

    private void hold(Packet<?> packet) {
        synchronized (lock) {
            int due = tick + delayTicks;
            List<Packet<?>> list = held.get(due);
            if (list == null) {
                list = new ArrayList<>();
                held.put(due, list);
            }
            list.add(packet);
        }
    }

    // releases everything that is being held, in order, and stops holding
    public void flush() {
        ArrayList<Packet<?>> list = null;
        synchronized (lock) {
            if (!held.isEmpty()) {
                list = new ArrayList<>();
                for (List<Packet<?>> packets : held.values()) {
                    list.addAll(packets);
                    packets.clear();
                }
                held.clear();
            }
            resetState();
        }
        if (list != null) {
            process(list);
        }
    }

    private void releaseDue() {
        ArrayList<Packet<?>> list = null;
        synchronized (lock) {
            if (!held.isEmpty()) {
                Iterator<Map.Entry<Integer, List<Packet<?>>>> it = held.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, List<Packet<?>>> entry = it.next();
                    if (tick < entry.getKey()) {
                        break;
                    }
                    if (!entry.getValue().isEmpty()) {
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        list.addAll(entry.getValue());
                    }
                    it.remove();
                }
            }
            tick++;
        }
        if (list != null) {
            process(list);
        }
    }

    @SuppressWarnings("unchecked")
    private void process(List<Packet<?>> list) {
        for (Packet<?> packet : list) {
            if (packet == null || mc.getNetHandler() == null) {
                continue;
            }
            try {
                ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
            } catch (ThreadQuickExitException ignored) {
                // processed later on the main thread
            } catch (Throwable throwable) {
                Utils.sendDebugMessage("backtrack error: " + packet.getClass().getSimpleName());
            }
        }
    }

    private void resetState() {
        ready = false;
        holding = false;
        ghost = null;
        target = null;
        tick = 0;
        lastFlush = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ logic

    private boolean canStart() {
        double cd = cooldown.getInput();
        return cd <= 0.0 || Math.abs(System.currentTimeMillis() - lastFlush) > cd * 1000.0 - 20.0;
    }

    private void start() {
        delayTicks = Math.max(1, (int) delay.getInput() / 50);
        updateServerPos();
        holding = true;
        if (showServerPosition.isToggled() && target != null) {
            makeGhost(target);
        }
    }

    // other modules that already hold incoming packets: do not fight them
    private boolean otherLagActive() {
        return (ModuleManager.backtrack != null && ModuleManager.backtrack.isEnabled())
                || (ModuleManager.blink != null && ModuleManager.blink.isEnabled() && ModuleManager.blink.delaysInboundPackets());
    }

    private boolean valid(EntityLivingBase e) {
        if (onlyHittable.isToggled() && e.hurtTime > hittableHurtTime.getInput()) {
            return false;
        }
        return !disableWhileChasing.isToggled() || !movingAway(e);
    }

    // the target is running away from you
    private boolean movingAway(EntityLivingBase e) {
        double dx = e.posX - mc.thePlayer.posX;
        double dz = e.posZ - mc.thePlayer.posZ;
        double mx = e.posX - e.lastTickPosX;
        double mz = e.posZ - e.lastTickPosZ;
        return dx * mx + dz * mz > 0.05;
    }

    private boolean targetValid() {
        EntityLivingBase t = target;
        if (!Utils.nullCheck() || mc.isSingleplayer() || t == null) {
            return false;
        }
        if (mc.theWorld.getEntityByID(t.getEntityId()) == null || t.deathTime != 0) {
            return false;
        }
        if (mc.currentScreen != null) {
            return false;
        }
        if (mc.thePlayer.capabilities.allowFlying
                || (disableAdventure.isToggled() && mc.playerController.getCurrentGameType() == WorldSettings.GameType.ADVENTURE)) {
            return false;
        }
        return !weaponOnly.isToggled() || Utils.holdingWeapon();
    }

    private void updateServerPos() {
        EntityLivingBase t = target;
        if (t != null) {
            serverPos = new Vec3(t.serverPosX / 32.0, t.serverPosY / 32.0, t.serverPosZ / 32.0);
        }
    }

    private boolean selectable(EntityPlayer p) {
        if (p == mc.thePlayer || p.deathTime != 0 || p.isDead) {
            return false;
        }
        if (Utils.isFriended(p) || AntiBot.isBot(p)) {
            return false;
        }
        return !(ignoreTeammates.isToggled() && Utils.isTeammate(p));
    }

    private EntityLivingBase findTarget() {
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target instanceof EntityPlayer) {
            EntityPlayer ka = (EntityPlayer) KillAura.target;
            if (selectable(ka) && valid(ka)) {
                return ka;
            }
        }
        double half = fov.getInput() / 2.0;
        double best = 999.0;
        EntityPlayer found = null;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (!selectable(p) || !valid(p)) {
                continue;
            }
            if (mc.thePlayer.getDistanceSqToEntity(p) > scanRangeSq) {
                continue;
            }
            float[] r = RotationUtils.getRotationsFromEye(eye, p.posX, p.posY + p.getEyeHeight() / 2.0, p.posZ);
            double yaw = Math.abs(MathHelper.wrapAngleTo180_float(r[0] - mc.thePlayer.rotationYaw));
            if (yaw > half) {
                continue;
            }
            if (best != 999.0 && !(yaw < best)) {
                continue;
            }
            best = yaw;
            found = p;
        }
        return found;
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (!this.isEnabled() || !Utils.nullCheck()) {
            return;
        }
        if (otherLagActive()) {
            return;
        }
        EntityLivingBase e = findTarget();
        if (holding) {
            if (e != target || !targetValid()) {
                flush();
                return;
            }
            EntityLivingBase g = ghost;
            if (g instanceof EntityOtherPlayerMP) {
                g.prevPosX = g.lastTickPosX = g.posX;
                g.prevPosY = g.lastTickPosY = g.posY;
                g.prevPosZ = g.lastTickPosZ = g.posZ;
                g.onLivingUpdate();
            }
            releaseDue();
        } else {
            if (e != null) {
                target = e;
                updateServerPos();
            }
            ready = target != null && targetValid();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        synchronized (lock) {
            held.clear();
            resetState();
        }
    }

    // ------------------------------------------------------------------ ray helpers

    // yaw/pitch that look at the closest point of the box
    private float[] rotationsToBox(AxisAlignedBB box) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double px = clamp(eye.xCoord, box.minX, box.maxX);
        double py = clamp(eye.yCoord, box.minY, box.maxY);
        double pz = clamp(eye.zCoord, box.minZ, box.maxZ);
        if (box.isVecInside(eye)) {
            px = (box.minX + box.maxX) / 2.0;
            py = (box.minY + box.maxY) / 2.0;
            pz = (box.minZ + box.maxZ) / 2.0;
        }
        return RotationUtils.getRotationsFromEye(eye, px, py, pz);
    }

    private boolean rayHits(AxisAlignedBB box, float yaw, float pitch, double range) {
        return rayDistanceSq(box, yaw, pitch, range) >= 0.0;
    }

    // squared distance from the eyes to where the look ray enters the box, -1 if it misses
    private double rayDistanceSq(AxisAlignedBB box, float yaw, float pitch, double range) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        if (box.isVecInside(eye)) {
            return 0.0;
        }
        Vec3 look = Utils.getLookVec(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition hit = box.calculateIntercept(eye, end);
        return hit == null ? -1.0 : eye.squareDistanceTo(hit.hitVec);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------ ghost box

    private void makeGhost(EntityLivingBase e) {
        Vec3 pos = serverPos;
        if (pos == null || !Utils.nullCheck()) {
            return;
        }
        EntityOtherPlayerMP copy = new EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile());
        copy.copyLocationAndAnglesFrom(e);
        copy.setPosition(pos.xCoord, pos.yCoord, pos.zCoord);
        copy.setPositionAndRotation2(pos.xCoord, pos.yCoord, pos.zCoord, e.rotationYaw, e.rotationPitch, 0, false);
        ghost = copy;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        EntityLivingBase g = ghost;
        if (g == null) {
            return;
        }
        RenderManager rm = mc.getRenderManager();
        float pt = event.partialTicks;
        double x = g.lastTickPosX + (g.posX - g.lastTickPosX) * pt - rm.viewerPosX;
        double y = g.lastTickPosY + (g.posY - g.lastTickPosY) * pt - rm.viewerPosY;
        double z = g.lastTickPosZ + (g.posZ - g.lastTickPosZ) * pt - rm.viewerPosZ;
        AxisAlignedBB bb = g.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
        AxisAlignedBB box = new AxisAlignedBB(bb.minX - g.posX + x, bb.minY - g.posY + y, bb.minZ - g.posZ + z,
                bb.maxX - g.posX + x, bb.maxY - g.posY + y, bb.maxZ - g.posZ + z);

        GL11.glPushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderUtils.drawBoundingBox(box, 1.0f, 0.2f, 0.2f, 0.37f);
        GL11.glLineWidth(2.0f);
        GL11.glColor4f(1.0f, 0.2f, 0.2f, 1.0f);
        RenderGlobal.drawSelectionBoundingBox(box);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glPopMatrix();
    }
}
