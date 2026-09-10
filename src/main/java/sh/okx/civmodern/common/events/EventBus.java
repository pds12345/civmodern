package sh.okx.civmodern.common.events;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.SubscriberExceptionHandler;
import org.jetbrains.annotations.NotNull;

public final class EventBus extends com.google.common.eventbus.EventBus {
    public EventBus(
        final @NotNull String identifier
    ) {
        super(identifier);
    }

    /**
     * Guava catches whatever a subscriber throws and hands it to this handler instead of
     * propagating it; the default handler logs through java.util.logging, which never reaches
     * the game log.
     */
    public EventBus(
        final @NotNull SubscriberExceptionHandler exceptionHandler
    ) {
        super(exceptionHandler);
    }

    @Override
    public void post(
        final @NotNull Object event
    ) {
        if (!(event instanceof DeadEvent)) {
            super.post(event);
        }
    }
}
