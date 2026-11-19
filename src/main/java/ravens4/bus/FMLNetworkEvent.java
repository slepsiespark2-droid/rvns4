package ravens4.bus;

import net.minecraft.network.NetworkManager;

public class FMLNetworkEvent extends Event {
    public static class ClientDisconnectionFromServerEvent extends FMLNetworkEvent {
        public final NetworkManager manager;

        public ClientDisconnectionFromServerEvent(NetworkManager manager) {
            this.manager = manager;
        }
    }
}
