package ravens4.event;

import ravens4.bus.Event;

public class PostProfileLoadEvent extends Event {
    public String profileName;

    public PostProfileLoadEvent(String profileName) {
        this.profileName = profileName;
    }
}
