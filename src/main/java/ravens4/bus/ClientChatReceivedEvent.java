package ravens4.bus;

import net.minecraft.util.IChatComponent;

@Cancelable
public class ClientChatReceivedEvent extends Event {
    public final byte type;
    public IChatComponent message;

    public ClientChatReceivedEvent(byte type, IChatComponent message) {
        this.type = type;
        this.message = message;
    }
}
