/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.observable;

public class EventSource<T> {

    private final EventStream<T> stream;

    protected EventSource(EventStream<T> stream) {
        this.stream = stream;
    }

    public Subscription subscribe(T subscriber) {
        this.stream.addSubscriber(subscriber);
        return new Subscription(subscriber);
    }

    public class Subscription {
        protected final T subscriber;

        public Subscription(T subscriber) {
            this.subscriber = subscriber;
        }

        public void cancel() {
            EventSource.this.stream.removeSubscriber(this.subscriber);
        }
    }
}
