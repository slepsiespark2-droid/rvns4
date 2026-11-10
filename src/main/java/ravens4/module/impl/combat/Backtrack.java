package ravens4.module.impl.combat;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.*;
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
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.server.S01PacketPong;
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
import ravens4.event.AttackEvent;
import ravens4.event.GameTickEvent;
import ravens4.event.PreUpdateEvent;
import ravens4.event.ReceivePacketEvent;
import ravens4.lag.fdp.S4BacktrackPacketHandler;
import ravens4.mixin.impl.accessor.IAccessorS14PacketEntity;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.player.AntiBot;
import ravens4.module.setting.Setting;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.ColorSetting;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.IMinecraftInstance;
import ravens4.utility.RenderUtils;
import ravens4.utility.RotationUtils;
import ravens4.utility.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backtrack with two modes. Each mode keeps its own settings, only the ones of the selected mode are shown.
 * <ul>
 *   <li><b>Phantom</b>: picks its own target and only holds packets when that helps you reach it.</li>
 *   <li><b>Mirage</b>: delays packets for the target you attack, with a smooth ESP box.</li>
 * </ul>
 */
public class Backtrack extends Module {
    private static final String[] MODES = new String[]{"Phantom", "Mirage"};

    private final SliderSetting mode;
    private final BacktrackPhantom phantom;
    private final BacktrackMirage mirage;
    private int lastMode = -1;

    public Backtrack() {
        super("Backtrack", category.combat);
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        phantom = new BacktrackPhantom(this);
        mirage = new BacktrackMirage(this);
        for (Setting setting : phantom.settings()) {
            this.registerSetting(setting);
        }
        for (Setting setting : mirage.settings()) {
            this.registerSetting(setting);
        }
        mirage.guiUpdate();
        lastMode = currentMode();
    }

    private int currentMode() {
        return (int) mode.getInput();
    }

    private boolean isMirage() {
        return currentMode() == 1;
    }

    // switching mode: let go of anything the old mode was holding and start the new one clean
    private void syncMode() {
        int m = currentMode();
        if (m == lastMode) {
            return;
        }
        lastMode = m;
        phantom.onDisable();
        mirage.onDisable();
        if (this.isEnabled() && m == 1) {
            mirage.onEnable();
        }
    }

    @Override
    public void onEnable() {
        lastMode = currentMode();
        if (isMirage()) {
            mirage.onEnable();
        }
    }

    @Override
    public void onDisable() {
        phantom.onDisable();
        mirage.onDisable();
    }

    @Override
    public String getInfo() {
        return isMirage() ? mirage.getInfo() : phantom.getInfo();
    }

    @Override
    public void guiUpdate() {
        syncMode();
        boolean m = isMirage();
        for (Setting setting : phantom.settings()) {
            setting.setVisible(!m, this);
        }
        for (Setting setting : mirage.settings()) {
            setting.setVisible(m, this);
        }
        phantom.guiUpdate();
        mirage.guiUpdate();
    }

    @Override
    public void guiButtonToggled(ButtonSetting b) {
        phantom.guiButtonToggled(b);
    }

    // used by scripts
    public Entity getBacktrackTarget() {
        return isMirage() ? mirage.target : phantom.target;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPacket(ReceivePacketEvent event) {
        if (isMirage()) {
            mirage.onPacket(event);
        } else {
            phantom.onPacket(event);
        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        syncMode();
        if (!isMirage()) {
            phantom.onPreUpdate(event);
        }
    }

    @SubscribeEvent
    public void onGameLoop(GameTickEvent event) {
        if (isMirage()) {
            mirage.onGameLoop(event);
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEvent event) {
        if (isMirage()) {
            mirage.onAttack(event);
        }
    }

    @SubscribeEvent
    public void onRender3D(RenderWorldLastEvent event) {
        if (isMirage()) {
            mirage.onRender3D(event);
        } else {
            phantom.onRenderWorld(event);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        phantom.onWorldUnload(event);
        mirage.onWorldUnload(event);
    }

    /**
     * Backtrack: when the target moves out of your reach, the incoming position updates for it are held
     * for a short time so you keep hitting the position it was at a moment ago. Everything is released
     * as soon as holding stops helping (it moves back into reach, you take knockback, you get teleported,
     * the target changes, ...).
     */
    static final class BacktrackPhantom implements IMinecraftInstance {
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


        private final Module owner;
        private final List<Setting> settings = new ArrayList<>();

        List<Setting> settings() {
            return settings;
        }

        private <T extends Setting> T reg(T setting) {
            settings.add(setting);
            return setting;
        }

        BacktrackPhantom(Module owner) {
            this.owner = owner;
            reg(delay = new SliderSetting("Delay", "ms", 100, 0, 500, 10));
            reg(distanceMin = new SliderSetting("Distance (min)", 3.0, 0.0, 6.0, 0.1));
            reg(distanceMax = new SliderSetting("Distance (max)", 4.5, 0.0, 6.0, 0.1));
            reg(cooldown = new SliderSetting("Cooldown", "s", 0, 0, 10, 0.5));
            reg(fov = new SliderSetting("FOV", 180, 30, 360, 10));
            reg(hittableHurtTime = new SliderSetting("Hittable hurt time", HITTABLE_HURT_TIME_FALLBACK, 0, 10, 1));
            reg(disableAdventure = new ButtonSetting("Disable adventure", false));
            reg(disableWhileChasing = new ButtonSetting("Disable while chasing", true));
            reg(flushOnDamage = new ButtonSetting("Flush on damage", true));
            reg(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
            reg(showServerPosition = new ButtonSetting("Show server position", true));
            reg(onlyHittable = new ButtonSetting("Only hittable", false));
            reg(onlyMovement = new ButtonSetting("Only movement", false));
            reg(weaponOnly = new ButtonSetting("Weapon only", false));
            guiUpdate();
        }

        public String getInfo() {
            return String.valueOf((int) delay.getInput());
        }

        public void onDisable() {
            flush();
        }

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
            if (!owner.isEnabled() || event.isCanceled()) {
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
            return ModuleManager.blink != null && ModuleManager.blink.isEnabled() && ModuleManager.blink.delaysInboundPackets();
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
            if (!owner.isEnabled() || !Utils.nullCheck()) {
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

    static final class BacktrackMirage implements IMinecraftInstance {
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

        // smoothed ESP position (the true position only changes once per received packet, this follows it every frame)
        private double espX;
        private double espY;
        private double espZ;
        private long espLastNanos;
        private EntityLivingBase espTarget;
        private boolean espActive;
        private static final double ESP_SMOOTH_SECONDS = 0.045;

        private static final int RANGE_MISS_GRACE_TICKS = 3;


        private final Module owner;
        private final List<Setting> settings = new ArrayList<>();

        List<Setting> settings() {
            return settings;
        }

        private <T extends Setting> T reg(T setting) {
            settings.add(setting);
            return setting;
        }

        BacktrackMirage(Module owner) {
            this.owner = owner;
            reg(nextLagReachDelay = new SliderSetting("NextLagReachDelay", "ms", 0, 0, 1000, 10));
            reg(maxDelay = new SliderSetting("MaxDelay", "ms", 80, 0, 1000, 10));
            reg(minDelay = new SliderSetting("MinDelay", "ms", 80, 0, 1000, 10));
            reg(distanceMin = new SliderSetting("DistanceMin", 0.0, 0.0, 6.0, 0.1));
            reg(distanceMax = new SliderSetting("DistanceMax", 3.0, 0.0, 6.0, 0.1));
            reg(smart = new ButtonSetting("Smart", true));
            reg(onFocus = new ButtonSetting("On focus", true));
            reg(new DescriptionSetting("ESP"));
            reg(esp = new ButtonSetting("ESP", true));
            reg(showInFirstPerson = new ButtonSetting("Show in first person", false));
            reg(espColor = new ColorSetting("ESP color", 255, 0, 0, 100));
            reg(espLineWidth = new SliderSetting("ESP line width", 2.0, 1.0, 5.0, 0.5));
            reg(espFilled = new ButtonSetting("ESP filled", false));
            this.modernDelay = randomDelay();
            this.lastMinDelay = (int) minDelay.getInput();
            this.lastMaxDelay = (int) maxDelay.getInput();
        }

        public void onEnable() {
            modernDelay = randomDelay();
            modernDelayChanged = false;
            lastMinDelay = (int) minDelay.getInput();
            lastMaxDelay = (int) maxDelay.getInput();
            reset();
        }

        public void onDisable() {
            clearPackets(true, true);
            truePositions.clear();
        }

        public String getInfo() {
            return String.valueOf(supposedDelay());
        }

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
                espActive = false;
                return;
            }
            if (mc.gameSettings.thirdPersonView == 0 && !showInFirstPerson.isToggled()) {
                espActive = false;
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
            double goalX = trueX(target);
            double goalY = trueY(target);
            double goalZ = trueZ(target);

            // glide toward the true position every frame instead of jumping each time a packet arrives
            long now = System.nanoTime();
            double dx = goalX - espX;
            double dy = goalY - espY;
            double dz = goalZ - espZ;
            if (!espActive || espTarget != target || dx * dx + dy * dy + dz * dz > 16.0) {
                espX = goalX;
                espY = goalY;
                espZ = goalZ;
                espTarget = target;
                espActive = true;
            } else {
                double dt = Math.min((now - espLastNanos) / 1.0E9, 0.25);
                double alpha = 1.0 - Math.exp(-dt / ESP_SMOOTH_SECONDS);
                espX += dx * alpha;
                espY += dy * alpha;
                espZ += dz * alpha;
            }
            espLastNanos = now;
            double x = espX;
            double y = espY;
            double z = espZ;

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
}
