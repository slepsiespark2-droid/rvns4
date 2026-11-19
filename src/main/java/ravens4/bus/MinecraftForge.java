package ravens4.bus;

/** Keeps the familiar MinecraftForge.EVENT_BUS name, but it is the client's own bus. */
public final class MinecraftForge {
    public static final EventBus EVENT_BUS = new EventBus();

    private MinecraftForge() {
    }
}
