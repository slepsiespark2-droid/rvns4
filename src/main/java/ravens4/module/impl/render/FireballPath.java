package ravens4.module.impl.render;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import ravens4.module.Module;
import ravens4.module.impl.player.AntiBot;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.ColorSetting;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.RenderUtils;
import ravens4.utility.Utils;

import java.util.List;

/**
 * Draws a line from every other player who is holding a fireball to the spot they are looking at, so you can see where
 * a fireball would go before it is even shot. This is its own module and does not depend on Indicators.
 */
public class FireballPath extends Module {
    /** Fireballs are about one block wide, so entities are hit a little before their own hitbox. */
    private static final double ENTITY_HIT_EXPANSION = 0.5;
    /** The line starts a bit in front of the player's eyes so it doesn't begin inside their head. */
    private static final double LINE_START_OFFSET = 0.5;
    private static final double MARKER_HALF_SIZE = 0.3;

    private final SliderSetting pathLength;
    private final SliderSetting lineThickness;
    private final ColorSetting lineColor;
    private final ColorSetting markerColor;
    private final ColorSetting entityColor;
    private final ButtonSetting showMarker;
    private final ButtonSetting ignoreBots;
    private final ButtonSetting ignoreTeammates;

    public FireballPath() {
        super("Fireball path", category.render);
        this.registerSetting(new DescriptionSetting("Shows where other players aim their fireballs."));
        this.registerSetting(pathLength = new SliderSetting("Path length", " blocks", 100, 20, 150, 5));
        this.registerSetting(lineThickness = new SliderSetting("Line thickness", 2.0, 1.0, 5.0, 0.1));
        this.registerSetting(lineColor = new ColorSetting("Line", 255, 255, 255));
        this.registerSetting(markerColor = new ColorSetting("Marker", 85, 255, 255));
        this.registerSetting(entityColor = new ColorSetting("Entity hit", 255, 50, 50));
        this.registerSetting(showMarker = new ButtonSetting("Show marker", true));
        this.registerSetting(ignoreBots = new ButtonSetting("Ignore bots", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", false));
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || mc.theWorld == null) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null) {
            return;
        }

        boolean setUp = false;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!isTargetPlayer(player)) {
                continue;
            }

            if (!setUp) {
                setUp = true;
                GL11.glPushMatrix();
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
            }
            drawPath(player, e.partialTicks, renderManager);
        }

        if (setUp) {
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glPopMatrix();
        }
    }

    /** Another, alive player who is holding a fireball right now. */
    private boolean isTargetPlayer(EntityPlayer player) {
        if (player == null || player == mc.thePlayer || player.isDead || player.isSpectator() || player.getHealth() <= 0.0f) {
            return false;
        }
        if (ignoreBots.isToggled() && AntiBot.isBot(player)) {
            return false;
        }
        if (ignoreTeammates.isToggled() && Utils.isTeammate(player)) {
            return false;
        }
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() == Items.fire_charge;
    }

    private void drawPath(EntityPlayer player, float partialTicks, RenderManager rm) {
        double eyeX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double eyeY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks + player.getEyeHeight();
        double eyeZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        Vec3 start = new Vec3(eyeX, eyeY, eyeZ);

        // where they are looking (uses the head rotation, interpolated)
        Vec3 look = player.getLook(partialTicks);
        double length = pathLength.getInput();
        Vec3 end = start.addVector(look.xCoord * length, look.yCoord * length, look.zCoord * length);

        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
        Vec3 lineEnd = blockHit != null ? blockHit.hitVec : end;

        // an entity in the way stops a fireball before the block behind it
        double bestDistSq = start.squareDistanceTo(lineEnd);
        Entity hitEntity = null;
        Vec3 hitEntityVec = null;

        AxisAlignedBB sweep = new AxisAlignedBB(start.xCoord, start.yCoord, start.zCoord, lineEnd.xCoord, lineEnd.yCoord, lineEnd.zCoord)
                .expand(1.0, 1.0, 1.0);
        List<Entity> candidates = mc.theWorld.getEntitiesWithinAABBExcludingEntity(player, sweep);
        for (Entity entity : candidates) {
            if (!(entity instanceof EntityLivingBase) || entity instanceof EntityArmorStand || !entity.canBeCollidedWith()) {
                continue;
            }
            if (((EntityLivingBase) entity).deathTime != 0) {
                continue;
            }
            if (entity instanceof EntityPlayer && AntiBot.isBot(entity)) {
                continue;
            }

            AxisAlignedBB box = entity.getEntityBoundingBox().expand(ENTITY_HIT_EXPANSION, ENTITY_HIT_EXPANSION, ENTITY_HIT_EXPANSION);
            MovingObjectPosition intercept = box.calculateIntercept(start, lineEnd);
            if (intercept == null) {
                continue;
            }
            double distSq = start.squareDistanceTo(intercept.hitVec);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                hitEntity = entity;
                hitEntityVec = intercept.hitVec;
            }
        }

        Vec3 finalEnd = hitEntityVec != null ? hitEntityVec : lineEnd;
        boolean entityHit = hitEntity != null;

        int color = entityHit ? entityColor.getColor() : lineColor.getColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // the line itself
        double startOffset = Math.min(LINE_START_OFFSET, Math.sqrt(start.squareDistanceTo(finalEnd)));
        GL11.glLineWidth((float) lineThickness.getInput());
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(eyeX + look.xCoord * startOffset - rm.viewerPosX, eyeY + look.yCoord * startOffset - rm.viewerPosY, eyeZ + look.zCoord * startOffset - rm.viewerPosZ);
        GL11.glVertex3d(finalEnd.xCoord - rm.viewerPosX, finalEnd.yCoord - rm.viewerPosY, finalEnd.zCoord - rm.viewerPosZ);
        GL11.glEnd();

        if (entityHit) {
            // outline the entity the fireball would hit
            double ex = hitEntity.lastTickPosX + (hitEntity.posX - hitEntity.lastTickPosX) * partialTicks;
            double ey = hitEntity.lastTickPosY + (hitEntity.posY - hitEntity.lastTickPosY) * partialTicks;
            double ez = hitEntity.lastTickPosZ + (hitEntity.posZ - hitEntity.lastTickPosZ) * partialTicks;
            AxisAlignedBB box = hitEntity.getEntityBoundingBox();
            AxisAlignedBB renderBox = new AxisAlignedBB(box.minX - hitEntity.posX + ex, box.minY - hitEntity.posY + ey, box.minZ - hitEntity.posZ + ez,
                    box.maxX - hitEntity.posX + ex, box.maxY - hitEntity.posY + ey, box.maxZ - hitEntity.posZ + ez);
            GL11.glColor4f(r, g, b, 1.0f);
            RenderUtils.drawOutlinedBox(renderBox, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
        }
        else if (blockHit != null && showMarker.isToggled()) {
            int markerRgb = markerColor.getColor();
            drawMarker(blockHit.hitVec.xCoord - rm.viewerPosX, blockHit.hitVec.yCoord - rm.viewerPosY, blockHit.hitVec.zCoord - rm.viewerPosZ,
                    blockHit.sideHit,
                    ((markerRgb >> 16) & 0xFF) / 255.0f, ((markerRgb >> 8) & 0xFF) / 255.0f, (markerRgb & 0xFF) / 255.0f);
        }
    }

    /** A small square lying flat on the face that would be hit (like the marker of the Trajectories module). */
    private void drawMarker(double x, double y, double z, EnumFacing side, float r, float g, float b) {
        double s = MARKER_HALF_SIZE;
        // push it a hair off the surface so it doesn't z-fight
        double ox = side.getFrontOffsetX() * 0.01;
        double oy = side.getFrontOffsetY() * 0.01;
        double oz = side.getFrontOffsetZ() * 0.01;

        double[][] corners;
        if (side.getAxis() == EnumFacing.Axis.Y) {
            corners = new double[][]{{-s, 0, -s}, {s, 0, -s}, {s, 0, s}, {-s, 0, s}};
        }
        else if (side.getAxis() == EnumFacing.Axis.X) {
            corners = new double[][]{{0, -s, -s}, {0, s, -s}, {0, s, s}, {0, -s, s}};
        }
        else {
            corners = new double[][]{{-s, -s, 0}, {s, -s, 0}, {s, s, 0}, {-s, s, 0}};
        }

        GL11.glColor4f(r, g, b, 0.45f);
        GL11.glBegin(GL11.GL_QUADS);
        for (double[] corner : corners) {
            GL11.glVertex3d(x + ox + corner[0], y + oy + corner[1], z + oz + corner[2]);
        }
        GL11.glEnd();

        GL11.glLineWidth(2.0f);
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (double[] corner : corners) {
            GL11.glVertex3d(x + ox + corner[0], y + oy + corner[1], z + oz + corner[2]);
        }
        GL11.glEnd();
    }
}
