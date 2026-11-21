package com.reality.mobcraft.neo.fabric.fabric.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A list of listeners, like Fabric's Event. The invoker calls them all in order. */
public final class Event<T> {
    private final List<T> listeners = new ArrayList<>();
    private final Function<List<T>, T> combine;
    private T invoker;
    public Event(Function<List<T>, T> combine) {
        this.combine = combine;
        this.invoker = combine.apply(listeners);
    }
    public synchronized void register(T listener) {
        listeners.add(listener);
    }
    public T invoker() { return invoker; }
    public List<T> listeners() { return listeners; }
}
