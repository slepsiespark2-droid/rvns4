package ravens4.bus;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's own event bus. Registering an object adds every public method that carries @SubscribeEvent and takes
 * one Event parameter; posting an event calls the handlers whose parameter type fits, highest priority first.
 */
public final class EventBus {
    private static final class Handler {
        final Object target;
        final Method method;
        final Class<?> type;
        final EventPriority priority;
        final boolean receiveCanceled;

        Handler(Object target, Method method, Class<?> type, EventPriority priority, boolean receiveCanceled) {
            this.target = target;
            this.method = method;
            this.type = type;
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
        }
    }

    private static final Handler[] NONE = new Handler[0];

    private final Object lock = new Object();
    private final List<Handler> handlers = new ArrayList<Handler>();
    private final Map<Object, Boolean> registered = new IdentityHashMap<Object, Boolean>();
    private final Map<Class<?>, Handler[]> cache = new ConcurrentHashMap<Class<?>, Handler[]>();

    public void register(Object target) {
        if (target == null) {
            return;
        }
        synchronized (lock) {
            if (registered.containsKey(target)) {
                return;
            }
            boolean isClass = target instanceof Class;
            Class<?> type = isClass ? (Class<?>) target : target.getClass();
            List<Handler> found = new ArrayList<Handler>();
            for (Method method : type.getMethods()) {
                SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
                if (annotation == null) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 1 || !Event.class.isAssignableFrom(parameters[0])) {
                    continue;
                }
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                if (isClass != isStatic) {
                    continue; // a class registers its static handlers, an object its instance handlers
                }
                method.setAccessible(true);
                found.add(new Handler(isClass ? null : target, method, parameters[0], annotation.priority(), annotation.receiveCanceled()));
            }
            registered.put(target, Boolean.TRUE);
            handlers.addAll(found);
            sortHandlers();
            cache.clear();
        }
    }

    public void unregister(Object target) {
        if (target == null) {
            return;
        }
        synchronized (lock) {
            if (registered.remove(target) == null) {
                return;
            }
            boolean isClass = target instanceof Class;
            List<Handler> keep = new ArrayList<Handler>();
            for (Handler handler : handlers) {
                boolean mine = isClass ? handler.target == null && handler.method.getDeclaringClass().isAssignableFrom((Class<?>) target)
                        : handler.target == target;
                if (!mine) {
                    keep.add(handler);
                }
            }
            handlers.clear();
            handlers.addAll(keep);
            cache.clear();
        }
    }

    private void sortHandlers() {
        // stable: handlers of the same priority keep the order they were registered in
        Collections.sort(handlers, new Comparator<Handler>() {
            @Override
            public int compare(Handler a, Handler b) {
                return a.priority.ordinal() - b.priority.ordinal();
            }
        });
    }

    private Handler[] handlersFor(Class<?> eventType) {
        Handler[] list = cache.get(eventType);
        if (list != null) {
            return list;
        }
        synchronized (lock) {
            List<Handler> matching = new ArrayList<Handler>();
            for (Handler handler : handlers) {
                if (handler.type.isAssignableFrom(eventType)) {
                    matching.add(handler);
                }
            }
            list = matching.isEmpty() ? NONE : matching.toArray(new Handler[0]);
            cache.put(eventType, list);
        }
        return list;
    }

    /** True if anything listens to this kind of event, so the caller can skip building it. */
    public boolean hasListeners(Class<? extends Event> eventType) {
        return handlersFor(eventType).length > 0;
    }

    /** Calls the handlers. Returns true if the event was cancelled. */
    public boolean post(Event event) {
        Handler[] list = handlersFor(event.getClass());
        for (Handler handler : list) {
            if (event.isCancelable() && event.isCanceled() && !handler.receiveCanceled) {
                continue;
            }
            try {
                handler.method.invoke(handler.target, event);
            } catch (Throwable throwable) {
                Throwable cause = throwable instanceof java.lang.reflect.InvocationTargetException && throwable.getCause() != null
                        ? throwable.getCause() : throwable;
                System.err.println("[rvns4] error in event handler " + handler.method.getDeclaringClass().getName() + "."
                        + handler.method.getName() + " for " + event.getClass().getSimpleName());
                cause.printStackTrace();
            }
        }
        return event.isCancelable() && event.isCanceled();
    }
}
