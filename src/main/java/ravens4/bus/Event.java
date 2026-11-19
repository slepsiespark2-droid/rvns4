package ravens4.bus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Base of every event on the client's own bus. */
public class Event {
    public enum Result {
        DENY, DEFAULT, ALLOW
    }

    private static final Map<Class<?>, Boolean> CANCELABLE = new ConcurrentHashMap<Class<?>, Boolean>();

    private boolean canceled;
    private Result result = Result.DEFAULT;
    private final boolean cancelable;

    public Event() {
        cancelable = isCancelableClass(getClass());
    }

    private static boolean isCancelableClass(Class<?> type) {
        Boolean known = CANCELABLE.get(type);
        if (known != null) {
            return known;
        }
        boolean found = false;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(Cancelable.class)) {
                found = true;
                break;
            }
        }
        CANCELABLE.put(type, found);
        return found;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean cancel) {
        if (!cancelable) {
            throw new IllegalArgumentException("Attempted to cancel a non-cancelable event: " + getClass().getName());
        }
        canceled = cancel;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result value) {
        result = value;
    }
}
