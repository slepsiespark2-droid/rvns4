package ravens4.lag.fdp;

import ravens4.utility.IMinecraftInstance;
import ravens4.utility.Utils;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.util.Vec3;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class S4BacktrackPacketHandler implements IMinecraftInstance {
    private final ConcurrentLinkedQueue<QueueData> packetQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PositionData> positions = new ConcurrentLinkedQueue<>();

    public boolean isPacketQueueEmpty() {
        return packetQueue.isEmpty();
    }

    public boolean isScheduledQueueEmpty() {
        return true;
    }

    public void queue(Packet<?> packet) {
        packetQueue.add(new QueueData(packet, System.currentTimeMillis()));
    }

    public void addPosition(Vec3 position) {
        positions.add(new PositionData(position, System.currentTimeMillis()));
    }

    public Iterable<PositionData> positions() {
        return positions;
    }

    public void processOlderThan(long ageMs) {
        long cutoff = System.currentTimeMillis() - ageMs;
        processUntil(cutoff);
        removePositionsOlderThan(cutoff);
    }

    public void processUntil(long timestamp) {
        Iterator<QueueData> iterator = packetQueue.iterator();
        while (iterator.hasNext()) {
            QueueData data = iterator.next();
            if (data.time <= timestamp) {
                iterator.remove();
                processPacket(data.packet);
            }
        }
    }

    public void removePositionsOlderThan(long timestamp) {
        Iterator<PositionData> iterator = positions.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().time < timestamp) {
                iterator.remove();
            }
        }
    }

    public void clear(boolean handlePackets) {
        QueueData data;
        while ((data = packetQueue.poll()) != null) {
            if (handlePackets) {
                processPacket(data.packet);
            }
        }
        positions.clear();
    }

    @SuppressWarnings("unchecked")
    private void processPacket(Packet<?> packet) {
        if (packet == null || mc.getNetHandler() == null) {
            return;
        }

        try {
            ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        } catch (Exception e) {
            if (packet instanceof net.minecraft.network.play.server.S0CPacketSpawnPlayer) {
                return;
            }
            Utils.sendDebugMessage("error while handling S4 Backtrack packet: " + packet.getClass().getSimpleName());
        }
    }

    public static final class QueueData {
        public final Packet<?> packet;
        public final long time;

        private QueueData(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }
    }

    public static final class PositionData {
        public final Vec3 position;
        public final long time;

        private PositionData(Vec3 position, long time) {
            this.position = position;
            this.time = time;
        }
    }
}
