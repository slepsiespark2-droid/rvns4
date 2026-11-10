package ravens4.script;

import ravens4.script.model.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages client-only entities created through the scripting API.
 * These entities are added only to the local client world and are never sent to the server.
 */
public final class ClientEntityManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(-1000000);
    private static final Map<Integer, net.minecraft.entity.Entity> ENTITIES = new LinkedHashMap<>();

    private ClientEntityManager() {
    }

    public static Entity spawnMob(String name, double x, double y, double z) {
        if (name == null || name.trim().isEmpty() || mc.theWorld == null) {
            return null;
        }

        net.minecraft.entity.Entity entity = EntityList.createEntityByName(name, mc.theWorld);
        if (entity == null) {
            return null;
        }

        int id = NEXT_ID.getAndDecrement();
        entity.setPosition(x, y, z);
        mc.theWorld.addEntityToWorld(id, entity);
        ENTITIES.put(id, entity);
        return Entity.convert(entity);
    }

    public static boolean removeClientEntity(Entity wrapped) {
        if (wrapped == null || wrapped.entity == null || mc.theWorld == null) {
            return false;
        }

        int foundId = Integer.MIN_VALUE;
        for (Map.Entry<Integer, net.minecraft.entity.Entity> entry : ENTITIES.entrySet()) {
            if (entry.getValue() == wrapped.entity) {
                foundId = entry.getKey();
                break;
            }
        }

        if (foundId == Integer.MIN_VALUE) {
            return false;
        }

        mc.theWorld.removeEntityFromWorld(foundId);
        ENTITIES.remove(foundId);
        return true;
    }

    public static void clearClientEntities() {
        if (mc.theWorld != null) {
            for (Integer id : ENTITIES.keySet().toArray(new Integer[0])) {
                mc.theWorld.removeEntityFromWorld(id);
            }
        }
        ENTITIES.clear();
    }
}
