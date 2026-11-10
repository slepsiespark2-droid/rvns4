package ravens4.utility;

import ravens4.module.impl.player.AntiBot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

public final class CombatTargeting implements IMinecraftInstance {
    private CombatTargeting() {
    }

    public static EntityPlayer findTarget(double maxDistanceSq) {
        return findTarget(maxDistanceSq, true);
    }

    public static EntityPlayer findTarget(double maxDistanceSq, boolean ignoreTeammates) {
        EntityPlayer mouseOverTarget = getMouseOverTarget(maxDistanceSq, ignoreTeammates);
        if (mouseOverTarget != null) {
            return mouseOverTarget;
        }

        return findClosestTarget(maxDistanceSq, ignoreTeammates);
    }

    public static EntityPlayer findClosestTarget(double maxDistanceSq) {
        return findClosestTarget(maxDistanceSq, true);
    }

    public static EntityPlayer findClosestTarget(double maxDistanceSq, boolean ignoreTeammates) {
        if (mc == null || mc.theWorld == null) {
            return null;
        }

        EntityPlayer closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!isValidPlayer(player, maxDistanceSq, ignoreTeammates)) {
                continue;
            }

            double distanceSq = RotationUtils.distanceSqFromEyeToClosestOnAABB(player);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = player;
            }
        }

        return closest;
    }

    /**
     * Any hostile mob (everything the game counts as a monster: zombies, skeletons, creepers, spiders, slimes, ghasts,
     * blazes, endermen...). Mobs with their AI switched off, which servers use for NPCs, are ignored.
     */
    public static boolean isHostileMob(Entity entity) {
        if (!(entity instanceof IMob) || !(entity instanceof EntityLiving) || entity.isDead) {
            return false;
        }
        EntityLiving mob = (EntityLiving) entity;
        return mob.deathTime == 0 && !mob.isAIDisabled();
    }

    public static EntityLivingBase findClosestHostile(double maxDistanceSq) {
        if (mc == null || mc.theWorld == null) {
            return null;
        }

        EntityLivingBase closest = null;
        double closestDistanceSq = Double.MAX_VALUE;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!isHostileMob(entity)) {
                continue;
            }
            double distanceSq = RotationUtils.distanceSqFromEyeToClosestOnAABB(entity);
            if (distanceSq <= maxDistanceSq && distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = (EntityLivingBase) entity;
            }
        }
        return closest;
    }

    public static EntityPlayer getMouseOverTarget(double maxDistanceSq) {
        return getMouseOverTarget(maxDistanceSq, true);
    }

    public static EntityPlayer getMouseOverTarget(double maxDistanceSq, boolean ignoreTeammates) {
        if (mc == null || mc.objectMouseOver == null) {
            return null;
        }

        MovingObjectPosition objectMouseOver = mc.objectMouseOver;
        return asValidPlayer(objectMouseOver.entityHit, maxDistanceSq, ignoreTeammates);
    }

    public static EntityPlayer asValidPlayer(Entity entity, double maxDistanceSq) {
        return asValidPlayer(entity, maxDistanceSq, true);
    }

    public static EntityPlayer asValidPlayer(Entity entity, double maxDistanceSq, boolean ignoreTeammates) {
        if (!(entity instanceof EntityPlayer)) {
            return null;
        }

        EntityPlayer player = (EntityPlayer) entity;
        return isValidPlayer(player, maxDistanceSq, ignoreTeammates) ? player : null;
    }

    public static boolean isValidPlayer(EntityPlayer player, double maxDistanceSq) {
        return isValidPlayer(player, maxDistanceSq, true);
    }

    public static boolean isValidPlayer(EntityPlayer player, double maxDistanceSq, boolean ignoreTeammates) {
        return isTrackablePlayer(player, ignoreTeammates) && isWithinRange(player, maxDistanceSq);
    }

    public static boolean isTrackablePlayer(EntityPlayer player) {
        return isTrackablePlayer(player, true);
    }

    public static boolean isTrackablePlayer(EntityPlayer player, boolean ignoreTeammates) {
        if (!Utils.nullCheck() || player == null || player == mc.thePlayer || player.isDead || player.deathTime != 0) {
            return false;
        }

        if (Utils.isFriended(player) || AntiBot.isBot(player)) {
            return false;
        }

        if (ignoreTeammates && Utils.isTeammate(player)) {
            return false;
        }

        return true;
    }

    public static boolean isWithinRange(EntityPlayer player, double maxDistanceSq) {
        if (player == null) {
            return false;
        }

        return RotationUtils.distanceSqFromEyeToClosestOnAABB(player) <= maxDistanceSq;
    }
}
