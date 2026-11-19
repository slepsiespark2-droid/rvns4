package ravens4.bus;

/** Stand-in for Forge's Side. */
public enum Side {
    CLIENT, SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }
}
