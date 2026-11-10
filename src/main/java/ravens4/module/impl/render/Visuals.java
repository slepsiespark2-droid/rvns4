package ravens4.module.impl.render;

import net.minecraft.block.BlockBed;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import ravens4.event.PreUpdateEvent;
import ravens4.event.SendPacketEvent;
import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.Utils;

import java.util.Random;

/**
 * Cute visuals: a trail of hearts and dots behind you while you move, and a burst of hearts / stars / diamonds with a
 * rainbow plus a sound when a bed breaks.
 * Native port of the "Visuals" script (dev. frail &amp; chef).
 */
public class Visuals extends Module {
    private static final int MAX_HEARTS = 50;
    private static final int MAX_DOTS = 100;
    private static final int MAX_BED_PARTICLES = 200;
    private static final int MAX_RAINBOWS = 5;

    private static final int TRAIL_HEART_SEGMENTS = 30;
    private static final int BED_HEART_SEGMENTS = 20;
    private static final int DOT_SEGMENTS = 8;
    private static final int DOT_FILL_SEGMENTS = 6;
    private static final int RAINBOW_SEGMENTS = 30;

    private static final double TWO_PI = Math.PI * 2.0;

    private static final double HEART_RADIUS = 1.5;
    private static final double HEART_SIZE = 0.15;
    private static final double HEART_FLOAT_SPEED = 1.0;
    private static final float HEART_LINE_WIDTH = 1.5f;

    private static final double DOT_SIZE = 0.04;
    private static final double DOT_SPREAD = 0.9;
    private static final double DOT_DRIFT_SPEED = 0.3;
    private static final float DOT_LINE_WIDTH = 2.0f;

    private static final double RAINBOW_SIZE = 3.0;
    private static final double RAINBOW_BAND_WIDTH = 0.15;
    private static final boolean RAINBOW_GLOW = true;

    private static final double[] RAINBOW_RED = {0.85, 0.60, 0.50, 0.50, 1.00, 1.00, 1.00};
    private static final double[] RAINBOW_GREEN = {0.50, 0.50, 0.75, 1.00, 0.90, 0.60, 0.40};
    private static final double[] RAINBOW_BLUE = {1.00, 1.00, 1.00, 0.65, 0.50, 0.40, 0.50};

    private static final double[] BED_PARTICLE_RED = {1.00, 1.00, 1.00, 0.50, 0.50, 0.60, 0.85};
    private static final double[] BED_PARTICLE_GREEN = {0.40, 0.60, 0.90, 1.00, 0.75, 0.50, 0.50};
    private static final double[] BED_PARTICLE_BLUE = {0.50, 0.40, 0.50, 0.65, 1.00, 1.00, 1.00};

    // precomputed geometry
    private static final double[] TRAIL_HEART_X = new double[TRAIL_HEART_SEGMENTS + 1];
    private static final double[] TRAIL_HEART_Y = new double[TRAIL_HEART_SEGMENTS + 1];
    private static final double[] BED_HEART_X = new double[BED_HEART_SEGMENTS + 1];
    private static final double[] BED_HEART_Y = new double[BED_HEART_SEGMENTS + 1];
    private static final double[] DOT_CIRCLE_X = new double[DOT_SEGMENTS + 1];
    private static final double[] DOT_CIRCLE_Y = new double[DOT_SEGMENTS + 1];
    private static final double[] DOT_FILL_X = new double[DOT_FILL_SEGMENTS + 1];
    private static final double[] DOT_FILL_Y = new double[DOT_FILL_SEGMENTS + 1];
    private static final double[] STAR_X = new double[9];
    private static final double[] STAR_Y = new double[9];

    static {
        for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
            double t = (double) i / TRAIL_HEART_SEGMENTS * TWO_PI;
            TRAIL_HEART_X[i] = heartX(t);
            TRAIL_HEART_Y[i] = heartY(t);
        }
        for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
            double t = (double) i / BED_HEART_SEGMENTS * TWO_PI;
            BED_HEART_X[i] = heartX(t);
            BED_HEART_Y[i] = heartY(t);
        }
        for (int i = 0; i <= DOT_SEGMENTS; i++) {
            double angle = (double) i / DOT_SEGMENTS * TWO_PI;
            DOT_CIRCLE_X[i] = Math.cos(angle);
            DOT_CIRCLE_Y[i] = Math.sin(angle);
        }
        for (int i = 0; i <= DOT_FILL_SEGMENTS; i++) {
            double angle = (double) i / DOT_FILL_SEGMENTS * TWO_PI;
            DOT_FILL_X[i] = Math.cos(angle);
            DOT_FILL_Y[i] = Math.sin(angle);
        }
        for (int i = 0; i <= 8; i++) {
            double angle = i * Math.PI / 4.0 - Math.PI / 2.0;
            double radius = (i % 2 == 0) ? 12.0 : 5.0;
            STAR_X[i] = Math.cos(angle) * radius;
            STAR_Y[i] = Math.sin(angle) * radius;
        }
    }

    private static double heartX(double t) {
        double sinT = Math.sin(t);
        return 16.0 * sinT * sinT * sinT;
    }

    private static double heartY(double t) {
        return 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t) - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t);
    }

    // settings
    private final ButtonSetting bedSound;
    private final ButtonSetting bedBurst;
    private final SliderSetting bedBurstCount;
    private final SliderSetting bedBurstSize;
    private final SliderSetting bedBurstSpeed;
    private final SliderSetting bedBurstLifetime;
    private final ButtonSetting rainbow;
    private final SliderSetting rainbowLineWidth;
    private final SliderSetting rainbowDuration;
    private final ButtonSetting onlyWhileMoving;
    private final SliderSetting opacity;
    private final ButtonSetting hearts;
    private final SliderSetting heartsSpawnRate;
    private final SliderSetting heartsLifetime;
    private final ButtonSetting dots;
    private final SliderSetting dotsSpawnRate;
    private final SliderSetting dotsLifetime;
    private final ButtonSetting pulse;

    private final Random random = new Random();

    // heart trail
    private final double[] heartPosX = new double[MAX_HEARTS];
    private final double[] heartPosY = new double[MAX_HEARTS];
    private final double[] heartPosZ = new double[MAX_HEARTS];
    private final long[] heartTime = new long[MAX_HEARTS];
    private final float[] heartRotY = new float[MAX_HEARTS];
    private final float[] heartRotZ = new float[MAX_HEARTS];
    private final float[] heartScale = new float[MAX_HEARTS];
    private final int[] heartType = new int[MAX_HEARTS];
    private final boolean[] heartActive = new boolean[MAX_HEARTS];
    private int activeHeartCount;
    private long lastHeartSpawn;

    // dot trail
    private final double[] dotPosX = new double[MAX_DOTS];
    private final double[] dotPosY = new double[MAX_DOTS];
    private final double[] dotPosZ = new double[MAX_DOTS];
    private final double[] dotDriftX = new double[MAX_DOTS];
    private final double[] dotDriftY = new double[MAX_DOTS];
    private final double[] dotDriftZ = new double[MAX_DOTS];
    private final long[] dotTime = new long[MAX_DOTS];
    private final float[] dotScale = new float[MAX_DOTS];
    private final int[] dotType = new int[MAX_DOTS];
    private final boolean[] dotActive = new boolean[MAX_DOTS];
    private int activeDotCount;
    private long lastDotSpawn;
    private double lastDotPosX;
    private double lastDotPosY;
    private double lastDotPosZ;
    private boolean hasLastDotPos;

    // bed burst
    private final double[] bedParticleX = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleY = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleZ = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVX = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVY = new double[MAX_BED_PARTICLES];
    private final double[] bedParticleVZ = new double[MAX_BED_PARTICLES];
    private final float[] bedParticleScale = new float[MAX_BED_PARTICLES];
    private final int[] bedParticleType = new int[MAX_BED_PARTICLES];
    private final long[] bedParticleTime = new long[MAX_BED_PARTICLES];
    private final boolean[] bedParticleActive = new boolean[MAX_BED_PARTICLES];
    private int activeBedParticleCount;

    // rainbow
    private final double[] rainbowX = new double[MAX_RAINBOWS];
    private final double[] rainbowY = new double[MAX_RAINBOWS];
    private final double[] rainbowZ = new double[MAX_RAINBOWS];
    private final float[] rainbowYaw = new float[MAX_RAINBOWS];
    private final long[] rainbowTime = new long[MAX_RAINBOWS];
    private final boolean[] rainbowActive = new boolean[MAX_RAINBOWS];
    private int activeRainbowCount;
    private final double[] rainbowArcCos = new double[RAINBOW_SEGMENTS + 1];
    private final double[] rainbowArcSin = new double[RAINBOW_SEGMENTS + 1];

    // bed break detection
    private boolean diggingBed;
    private double bedX;
    private double bedY;
    private double bedZ;

    public Visuals() {
        super("Visuals", category.render);
        this.registerSetting(new DescriptionSetting("dev. frail & chef"));

        this.registerSetting(bedSound = new ButtonSetting("Bed Sound", true));

        this.registerSetting(new DescriptionSetting("Bed break"));
        this.registerSetting(bedBurst = new ButtonSetting("Bed Burst", true));
        this.registerSetting(bedBurstCount = new SliderSetting("Bed Burst Count", "", 20, 5, 40, 1));
        this.registerSetting(bedBurstSize = new SliderSetting("Bed Burst Size", "", 0.20, 0.05, 0.6, 0.01));
        this.registerSetting(bedBurstSpeed = new SliderSetting("Bed Burst Speed", "", 2.5, 0.5, 7.0, 0.1));
        this.registerSetting(bedBurstLifetime = new SliderSetting("Bed Burst Lifetime", "ms", 1500, 500, 3000, 100));
        this.registerSetting(rainbow = new ButtonSetting("Rainbow", true));
        this.registerSetting(rainbowLineWidth = new SliderSetting("Rainbow Line Width", "", 5.0, 1.0, 12.0, 0.5));
        this.registerSetting(rainbowDuration = new SliderSetting("Rainbow Duration", "ms", 3000, 1000, 6000, 200));

        this.registerSetting(new DescriptionSetting("Trail"));
        this.registerSetting(onlyWhileMoving = new ButtonSetting("Only While Moving", true));
        this.registerSetting(opacity = new SliderSetting("Opacity", "%", 85, 20, 100, 5));
        this.registerSetting(hearts = new ButtonSetting("Hearts", true));
        this.registerSetting(heartsSpawnRate = new SliderSetting("Hearts Spawn Rate", "ms", 200, 50, 500, 10));
        this.registerSetting(heartsLifetime = new SliderSetting("Hearts Lifetime", "ms", 1500, 500, 4000, 100));
        this.registerSetting(dots = new ButtonSetting("Dots", true));
        this.registerSetting(dotsSpawnRate = new SliderSetting("Dots Spawn Rate", "ms", 100, 20, 200, 10));
        this.registerSetting(dotsLifetime = new SliderSetting("Dots Lifetime", "ms", 1500, 500, 5000, 100));
        this.registerSetting(pulse = new ButtonSetting("Pulse", false));
    }

    @Override
    public void onEnable() {
        clearAll();
        lastHeartSpawn = 0;
        lastDotSpawn = 0;
        hasLastDotPos = false;
        diggingBed = false;
    }

    @Override
    public void onDisable() {
        clearAll();
        diggingBed = false;
    }

    private void clearAll() {
        clearHearts();
        clearDots();
        for (int i = 0; i < MAX_BED_PARTICLES; i++) bedParticleActive[i] = false;
        activeBedParticleCount = 0;
        for (int i = 0; i < MAX_RAINBOWS; i++) rainbowActive[i] = false;
        activeRainbowCount = 0;
    }

    private void clearHearts() {
        for (int i = 0; i < MAX_HEARTS; i++) heartActive[i] = false;
        activeHeartCount = 0;
    }

    private void clearDots() {
        for (int i = 0; i < MAX_DOTS; i++) dotActive[i] = false;
        activeDotCount = 0;
    }

    // ============================== slots ==============================

    /** First free slot, or the oldest one when all are taken. */
    private static int findFreeSlot(boolean[] active, long[] time) {
        for (int i = 0; i < active.length; i++) {
            if (!active[i]) return i;
        }
        long oldest = Long.MAX_VALUE;
        int oldestIndex = 0;
        for (int i = 0; i < time.length; i++) {
            if (time[i] < oldest) {
                oldest = time[i];
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    // ============================== trail updates ==============================

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }

        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;

        boolean heartsEnabled = hearts.isToggled();
        boolean dotsEnabled = dots.isToggled();

        if (!heartsEnabled && activeHeartCount > 0) clearHearts();
        if (!dotsEnabled && activeDotCount > 0) clearDots();

        boolean canSpawnDots = hasLastDotPos;
        if (!hasLastDotPos) {
            lastDotPosX = px;
            lastDotPosY = py;
            lastDotPosZ = pz;
            hasLastDotPos = true;
        }

        if (!heartsEnabled && !dotsEnabled) {
            lastDotPosX = px;
            lastDotPosY = py;
            lastDotPosZ = pz;
            return;
        }

        if (onlyWhileMoving.isToggled() && !Utils.isMoving()) {
            return;
        }

        long now = System.currentTimeMillis();

        if (heartsEnabled) {
            long heartRate = (long) heartsSpawnRate.getInput();
            if (now - lastHeartSpawn >= heartRate) {
                lastHeartSpawn = now;
                spawnHeart(px, py + 0.5, pz, now);
            }
        }

        if (dotsEnabled && canSpawnDots) {
            long dotRate = (long) dotsSpawnRate.getInput();
            if (now - lastDotSpawn >= dotRate) {
                lastDotSpawn = now;
                spawnDot(py, now);
            }
        }

        lastDotPosX = px;
        lastDotPosY = py;
        lastDotPosZ = pz;
    }

    private void spawnHeart(double x, double y, double z, long now) {
        int slot = findFreeSlot(heartActive, heartTime);
        boolean wasActive = heartActive[slot];

        double angle = random.nextDouble() * TWO_PI;
        double distance = random.nextDouble() * HEART_RADIUS;

        heartPosX[slot] = x + Math.cos(angle) * distance;
        heartPosZ[slot] = z + Math.sin(angle) * distance;
        heartPosY[slot] = y + random.nextDouble() * 0.5;
        heartTime[slot] = now;
        heartRotY[slot] = (float) (random.nextDouble() * 360.0);
        heartRotZ[slot] = (float) (random.nextDouble() * 30.0 - 15.0);
        heartScale[slot] = (float) (HEART_SIZE * (0.6 + random.nextDouble() * 0.8));
        heartType[slot] = random.nextInt(3);
        heartActive[slot] = true;

        if (!wasActive) activeHeartCount++;
    }

    private void spawnDot(double playerY, long now) {
        int slot = findFreeSlot(dotActive, dotTime);
        boolean wasActive = dotActive[slot];

        dotPosX[slot] = lastDotPosX + (random.nextDouble() - 0.5) * DOT_SPREAD;
        dotPosY[slot] = playerY + 0.3 + random.nextDouble() * 1.2;
        dotPosZ[slot] = lastDotPosZ + (random.nextDouble() - 0.5) * DOT_SPREAD;

        dotDriftX[slot] = (random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;
        dotDriftY[slot] = (0.3 + random.nextDouble() * 0.7) * DOT_DRIFT_SPEED;
        dotDriftZ[slot] = (random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;

        dotTime[slot] = now;
        dotScale[slot] = (float) (DOT_SIZE * (0.5 + random.nextDouble()));
        dotType[slot] = random.nextInt(4);
        dotActive[slot] = true;

        if (!wasActive) activeDotCount++;
    }

    // ============================== bed break detection ==============================

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!(e.getPacket() instanceof C07PacketPlayerDigging) || !Utils.nullCheck()) {
            return;
        }

        C07PacketPlayerDigging packet = (C07PacketPlayerDigging) e.getPacket();
        C07PacketPlayerDigging.Action status = packet.getStatus();
        BlockPos position = packet.getPosition();
        if (status == null || position == null) {
            return;
        }

        switch (status) {
            case START_DESTROY_BLOCK:
                boolean isBed = mc.theWorld.getBlockState(position).getBlock() instanceof BlockBed;
                if (!isBed) {
                    diggingBed = false;
                    return;
                }

                double x = position.getX() + 0.5;
                double y = position.getY() + 0.5;
                double z = position.getZ() + 0.5;

                if (mc.thePlayer.capabilities.isCreativeMode) {
                    // creative mode only sends START_DESTROY_BLOCK for instant breaks
                    diggingBed = false;
                    spawnBedBreak(x, y, z);
                }
                else {
                    diggingBed = true;
                    bedX = x;
                    bedY = y;
                    bedZ = z;
                }
                break;
            case STOP_DESTROY_BLOCK:
                if (diggingBed) {
                    spawnBedBreak(bedX, bedY, bedZ);
                    diggingBed = false;
                }
                break;
            case ABORT_DESTROY_BLOCK:
                diggingBed = false;
                break;
            default:
                break;
        }
    }

    private void spawnBedBreak(double x, double y, double z) {
        long now = System.currentTimeMillis();

        if (rainbow.isToggled()) {
            int slot = findFreeSlot(rainbowActive, rainbowTime);
            boolean wasActive = rainbowActive[slot];

            rainbowX[slot] = x;
            rainbowY[slot] = y;
            rainbowZ[slot] = z;
            rainbowTime[slot] = now;
            rainbowActive[slot] = true;
            rainbowYaw[slot] = (float) Math.toDegrees(Math.atan2(mc.thePlayer.posX - x, mc.thePlayer.posZ - z));

            if (!wasActive) activeRainbowCount++;
        }

        if (bedBurst.isToggled()) {
            double speed = bedBurstSpeed.getInput();
            double baseSize = bedBurstSize.getInput();
            int count = (int) bedBurstCount.getInput();

            for (int i = 0; i < count; i++) {
                int slot = findFreeSlot(bedParticleActive, bedParticleTime);
                boolean wasActive = bedParticleActive[slot];

                bedParticleX[slot] = x;
                bedParticleY[slot] = y;
                bedParticleZ[slot] = z;

                double theta = random.nextDouble() * TWO_PI;
                double phi = random.nextDouble() * Math.PI * 0.67 - Math.PI / 6.0;
                double particleSpeed = (0.8 + random.nextDouble() * 1.2) * speed;
                double cosPhi = Math.cos(phi);

                bedParticleVX[slot] = cosPhi * Math.cos(theta) * particleSpeed;
                bedParticleVY[slot] = Math.sin(phi) * particleSpeed + 1.0;
                bedParticleVZ[slot] = cosPhi * Math.sin(theta) * particleSpeed;

                int typeRoll = random.nextInt(5);
                bedParticleType[slot] = typeRoll < 2 ? 0 : typeRoll - 1;
                bedParticleScale[slot] = (float) (baseSize * (0.6 + random.nextDouble() * 0.8));
                bedParticleTime[slot] = now;
                bedParticleActive[slot] = true;

                if (!wasActive) activeBedParticleCount++;
            }
        }

        if (bedSound.isToggled()) {
            mc.thePlayer.playSound("random.orb", 1.0f, 1.5f);
            mc.thePlayer.playSound("random.levelup", 0.5f, 2.0f);
        }
    }

    // ============================== world rendering ==============================

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (activeHeartCount <= 0 && activeDotCount <= 0 && activeBedParticleCount <= 0 && activeRainbowCount <= 0) {
            return;
        }

        // world positions are drawn relative to the render manager's (interpolated) viewer position, exactly like the
        // other in-world render modules do; the script API's eye-height camera position would draw everything too low
        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null) {
            return;
        }
        Vec3 camera = new Vec3(renderManager.viewerPosX, renderManager.viewerPosY, renderManager.viewerPosZ);

        long now = System.currentTimeMillis();
        renderTrail(camera, now);
        renderBedVisuals(camera, now);
    }

    private void renderTrail(Vec3 camera, long now) {
        boolean renderHearts = activeHeartCount > 0 && hearts.isToggled();
        boolean renderDots = activeDotCount > 0 && dots.isToggled();
        if (!renderHearts && !renderDots) return;

        double alphaScale = opacity.getInput() / 100.0;

        GL11.glPushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableCull();

        if (renderHearts) renderHearts(camera, now, alphaScale);
        if (renderDots) renderDots(camera, now, alphaScale);

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    private void renderHearts(Vec3 camera, long now, double alphaScale) {
        long lifetime = (long) heartsLifetime.getInput();
        float effectiveLineWidth = Math.max(HEART_LINE_WIDTH * 0.6f, 1.0f);
        GL11.glLineWidth(effectiveLineWidth);

        for (int i = 0; i < MAX_HEARTS; i++) {
            if (!heartActive[i]) continue;

            long age = now - heartTime[i];
            if (age > lifetime) {
                heartActive[i] = false;
                activeHeartCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;
            double floatY = HEART_FLOAT_SPEED * progress * 1.5;

            double alpha;
            if (progress < 0.1) alpha = progress / 0.1;
            else if (progress > 0.6) alpha = (1.0 - progress) / 0.4;
            else alpha = 1.0;
            alpha *= alphaScale;

            double scaleAnimation;
            if (progress < 0.1) scaleAnimation = progress / 0.1;
            else if (progress > 0.8) scaleAnimation = (1.0 - progress) / 0.2;
            else scaleAnimation = 1.0;

            double drawX = heartPosX[i] + Math.sin(age * 0.002 + i * 1.7) * 0.1 - camera.xCoord;
            double drawY = heartPosY[i] + floatY - camera.yCoord;
            double drawZ = heartPosZ[i] + Math.cos(age * 0.0015 + i * 2.3) * 0.1 - camera.zCoord;

            double red;
            double green;
            double blue;
            int type = heartType[i];
            if (type == 0) {
                red = 1.0; green = 0.5; blue = 0.8;
            }
            else if (type == 1) {
                red = 1.0; green = 0.3; blue = 0.6;
            }
            else {
                red = 0.9; green = 0.4; blue = 0.9;
            }

            double size = heartScale[i] * scaleAnimation;
            double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));
            double spinAngle = (age * 0.1 + heartRotY[i]) % 360.0;

            GL11.glPushMatrix();
            GL11.glTranslated(drawX, drawY, drawZ);
            GL11.glRotated(billboardYaw, 0, 1, 0);
            GL11.glRotated(spinAngle, 0, 1, 0);
            GL11.glRotated(heartRotZ[i], 0, 0, 1);
            GL11.glLineWidth(effectiveLineWidth + 3.0f);
            drawTrailHeart(size, alpha, red, green, blue);
            GL11.glLineWidth(effectiveLineWidth);
            GL11.glPopMatrix();
        }
    }

    private void drawTrailHeart(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;

        for (int layer = 3; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.08);
            double layerAlpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.3 / layer);

            color(red, green, blue, layerAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
                GL11.glVertex3d(TRAIL_HEART_X[i] * glowScale, TRAIL_HEART_Y[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void renderDots(Vec3 camera, long now, double alphaScale) {
        long lifetime = (long) dotsLifetime.getInput();
        boolean pulsing = pulse.isToggled();
        GL11.glLineWidth(DOT_LINE_WIDTH);

        for (int i = 0; i < MAX_DOTS; i++) {
            if (!dotActive[i]) continue;

            long age = now - dotTime[i];
            if (age > lifetime) {
                dotActive[i] = false;
                activeDotCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;

            double fade;
            if (progress < 0.1) fade = progress / 0.1;
            else if (progress > 0.5) fade = (1.0 - progress) / 0.5;
            else fade = 1.0;

            double pulseFactor = 1.0;
            if (pulsing) {
                double flickerSpeed = 3.0 + dotType[i] * 1.5;
                pulseFactor = 0.5 + 0.5 * Math.sin(age * 0.01 * flickerSpeed + i * 2.7);
            }

            double ageSeconds = age / 1000.0;
            double drawX = dotPosX[i] + dotDriftX[i] * ageSeconds + Math.sin(ageSeconds * 1.5 + i * 1.3) * 0.15 - camera.xCoord;
            double drawY = dotPosY[i] + dotDriftY[i] * ageSeconds - camera.yCoord;
            double drawZ = dotPosZ[i] + dotDriftZ[i] * ageSeconds + Math.cos(ageSeconds * 1.2 + i * 2.1) * 0.15 - camera.zCoord;

            double red;
            double green;
            double blue;
            int type = dotType[i];
            if (type == 0) {
                red = 1.0; green = 0.45; blue = 0.7;
            }
            else if (type == 1) {
                red = 1.0; green = 0.6; blue = 0.85;
            }
            else if (type == 2) {
                red = 1.0; green = 0.3; blue = 0.55;
            }
            else {
                red = 1.0; green = 0.75; blue = 0.95;
            }

            double alpha = fade * pulseFactor * alphaScale;
            if (alpha < 0.02) continue;

            double size = dotScale[i];
            double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));

            GL11.glPushMatrix();
            GL11.glTranslated(drawX, drawY, drawZ);
            GL11.glRotated(billboardYaw, 0, 1, 0);
            color(red, green, blue, alpha);

            // a filled core plus an outline
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3d(0, 0, 0);
            for (int j = 0; j <= DOT_FILL_SEGMENTS; j++) {
                GL11.glVertex3d(DOT_FILL_X[j] * size, DOT_FILL_Y[j] * size, 0);
            }
            GL11.glEnd();

            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int j = 0; j <= DOT_SEGMENTS; j++) {
                GL11.glVertex3d(DOT_CIRCLE_X[j] * size, DOT_CIRCLE_Y[j] * size, 0);
            }
            GL11.glEnd();

            GL11.glPopMatrix();
        }
    }

    // ============================== bed rendering ==============================

    private void renderBedVisuals(Vec3 camera, long now) {
        if (activeBedParticleCount <= 0 && activeRainbowCount <= 0) return;

        long bedLifetime = activeBedParticleCount > 0 ? (long) bedBurstLifetime.getInput() : 0;
        long rainbowLength = activeRainbowCount > 0 ? (long) rainbowDuration.getInput() : 0;
        float lineWidth = (float) rainbowLineWidth.getInput();

        GL11.glPushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GlStateManager.disableCull();

        if (activeRainbowCount > 0) {
            renderRainbows(camera, now, rainbowLength, lineWidth);
        }
        if (activeBedParticleCount > 0) {
            renderBedParticles(camera, now, bedLifetime, lineWidth);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    private void renderRainbows(Vec3 camera, long now, long duration, float lineWidth) {
        for (int index = 0; index < MAX_RAINBOWS; index++) {
            if (!rainbowActive[index]) continue;

            long age = now - rainbowTime[index];
            if (age > duration) {
                rainbowActive[index] = false;
                activeRainbowCount--;
                continue;
            }

            double progress = (double) age / (double) duration;

            double alpha;
            if (progress < 0.15) alpha = progress / 0.15;
            else if (progress > 0.6) alpha = (1.0 - progress) / 0.4;
            else alpha = 1.0;

            double arcProgress = progress < 0.2 ? Math.pow(progress / 0.2, 2.0) : 1.0;

            for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
                double angle = (double) segment / RAINBOW_SEGMENTS * Math.PI * arcProgress;
                rainbowArcCos[segment] = Math.cos(angle);
                rainbowArcSin[segment] = Math.sin(angle);
            }

            double drawX = rainbowX[index] - camera.xCoord;
            double drawY = rainbowY[index] - camera.yCoord;
            double drawZ = rainbowZ[index] - camera.zCoord;

            GL11.glPushMatrix();
            GL11.glTranslated(drawX, drawY, drawZ);
            GL11.glRotated(rainbowYaw[index], 0, 1, 0);

            for (int band = 0; band < 7; band++) {
                double radius = RAINBOW_SIZE + (band - 3) * RAINBOW_BAND_WIDTH;
                if (radius < 0.1) continue;

                double red = RAINBOW_RED[band];
                double green = RAINBOW_GREEN[band];
                double blue = RAINBOW_BLUE[band];

                if (RAINBOW_GLOW) {
                    drawRainbowArc(radius, red, green, blue, alpha * 0.15, lineWidth + 3.0f);
                    drawRainbowArc(radius, red, green, blue, alpha * 0.30, lineWidth + 1.5f);
                }
                drawRainbowArc(radius, red, green, blue, alpha * 0.85, lineWidth);
            }

            if (arcProgress > 0.5) {
                drawRainbowSparkles(now, arcProgress, alpha);
            }

            GL11.glPopMatrix();
        }
    }

    private void drawRainbowArc(double radius, double red, double green, double blue, double alpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        color(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
            GL11.glVertex3d(rainbowArcCos[segment] * radius, rainbowArcSin[segment] * radius, 0);
        }
        GL11.glEnd();
    }

    private void drawRainbowSparkles(long now, double arcProgress, double alpha) {
        double sparkleAlpha = alpha * 0.7;
        double sparkleSize = 0.15;
        double rotation = now * 0.003;
        double cosRotation = Math.cos(rotation);
        double sinRotation = Math.sin(rotation);

        color(1.0, 1.0, 0.8, sparkleAlpha);

        for (int end = 0; end < 2; end++) {
            double endAngle = end == 0 ? 0.0 : Math.PI * arcProgress;
            double endX = Math.cos(endAngle) * RAINBOW_SIZE;
            double endY = Math.sin(endAngle) * RAINBOW_SIZE;

            drawSparkleRay(endX, endY, cosRotation, sinRotation, sparkleSize);
            drawSparkleRay(endX, endY, -sinRotation, cosRotation, sparkleSize);
            drawSparkleRay(endX, endY, -cosRotation, -sinRotation, sparkleSize);
            drawSparkleRay(endX, endY, sinRotation, -cosRotation, sparkleSize);
        }
    }

    private void drawSparkleRay(double x, double y, double directionX, double directionY, double size) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(x, y, 0);
        GL11.glVertex3d(x + directionX * size, y + directionY * size, 0);
        GL11.glEnd();
    }

    private void renderBedParticles(Vec3 camera, long now, long lifetime, float lineWidth) {
        GL11.glLineWidth(lineWidth);

        for (int i = 0; i < MAX_BED_PARTICLES; i++) {
            if (!bedParticleActive[i]) continue;

            long age = now - bedParticleTime[i];
            if (age > lifetime) {
                bedParticleActive[i] = false;
                activeBedParticleCount--;
                continue;
            }

            double progress = (double) age / (double) lifetime;
            double ageSeconds = age / 1000.0;

            double particleX = bedParticleX[i] + bedParticleVX[i] * ageSeconds;
            double particleY = bedParticleY[i] + bedParticleVY[i] * ageSeconds - 1.5 * ageSeconds * ageSeconds;
            double particleZ = bedParticleZ[i] + bedParticleVZ[i] * ageSeconds;

            particleX += Math.sin(age * 0.002 + i * 1.7) * 0.05;
            particleZ += Math.cos(age * 0.0015 + i * 2.3) * 0.05;

            double alpha;
            if (progress < 0.1) alpha = progress / 0.1;
            else if (progress > 0.7) alpha = (1.0 - progress) / 0.3;
            else alpha = 1.0;

            double scaleAnimation;
            if (progress < 0.1) scaleAnimation = progress / 0.1;
            else if (progress > 0.7) scaleAnimation = (1.0 - progress) / 0.3;
            else scaleAnimation = 1.0;

            double drawX = particleX - camera.xCoord;
            double drawY = particleY - camera.yCoord;
            double drawZ = particleZ - camera.zCoord;

            int colorIndex = i % 7;
            double red = BED_PARTICLE_RED[colorIndex];
            double green = BED_PARTICLE_GREEN[colorIndex];
            double blue = BED_PARTICLE_BLUE[colorIndex];

            double size = bedParticleScale[i] * scaleAnimation;
            double billboardYaw = Math.toDegrees(Math.atan2(drawX, drawZ));

            GL11.glPushMatrix();
            GL11.glTranslated(drawX, drawY, drawZ);
            GL11.glRotated(billboardYaw, 0, 1, 0);
            GL11.glRotated(ageSeconds * 40.0 + i * 60.0, 0, 0, 1);

            int type = bedParticleType[i];
            if (type == 0) {
                drawBedHeart(size, alpha, red, green, blue);
            }
            else if (type == 1) {
                drawBedStar(size, alpha, red, green, blue);
            }
            else if (type == 2) {
                drawBedDot(size, alpha, red, green, blue);
            }
            else {
                drawBedDiamond(size, alpha, red, green, blue);
            }

            GL11.glPopMatrix();
        }
    }

    private void drawBedHeart(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            color(red, green, blue, layerAlpha(baseAlpha, layer));
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
                GL11.glVertex3d(BED_HEART_X[i] * glowScale, BED_HEART_Y[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void drawBedStar(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            color(red, green, blue, layerAlpha(baseAlpha, layer));
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= 8; i++) {
                GL11.glVertex3d(STAR_X[i] * glowScale, STAR_Y[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void drawBedDot(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 2.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.15);
            color(red, green, blue, layerAlpha(baseAlpha, layer));
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3d(0, 0, 0);
            for (int i = 0; i <= DOT_SEGMENTS; i++) {
                GL11.glVertex3d(DOT_CIRCLE_X[i] * glowScale, DOT_CIRCLE_Y[i] * glowScale, 0);
            }
            GL11.glEnd();
        }
    }

    private void drawBedDiamond(double size, double baseAlpha, double red, double green, double blue) {
        double scale = size / 16.0;
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            color(red, green, blue, layerAlpha(baseAlpha, layer));
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex3d(0, 14.0 * glowScale, 0);
            GL11.glVertex3d(8.0 * glowScale, 0, 0);
            GL11.glVertex3d(0, -14.0 * glowScale, 0);
            GL11.glVertex3d(-8.0 * glowScale, 0, 0);
            GL11.glVertex3d(0, 14.0 * glowScale, 0);
            GL11.glEnd();
        }
    }

    /** The crisp front layer (0) is the most opaque, the glow layers behind it get fainter. */
    private static double layerAlpha(double baseAlpha, int layer) {
        return layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.25 / layer);
    }

    private static void color(double r, double g, double b, double a) {
        GlStateManager.color((float) r, (float) g, (float) b, (float) a);
    }
}
