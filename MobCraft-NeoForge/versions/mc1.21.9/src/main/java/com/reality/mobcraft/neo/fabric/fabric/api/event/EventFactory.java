package com.reality.mobcraft.neo.fabric.fabric.api.event;

import java.lang.reflect.Array;
import java.util.List;
import java.util.function.Function;

public final class EventFactory {
    private EventFactory() {}
    @SuppressWarnings("unchecked")
    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
        return new Event<>(list -> {
            // Rebuilt on each call so listeners added later are seen.
            return invokerFactory.apply(list.toArray((T[]) Array.newInstance(type, 0))) == null ? null : proxy(type, list, invokerFactory);
        });
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<? super T> type, List<T> list, Function<T[], T> invokerFactory) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> {
            if (m.getDeclaringClass() == Object.class) return m.invoke(list, args);
            T real = invokerFactory.apply(list.toArray((T[]) Array.newInstance(type, 0)));
            return m.invoke(real, args);
        });
    }
}
