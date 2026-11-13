package com.shumsic.autodrinkominousbottles;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * The boss bars on screen. The boss overlay keeps them in a private map, and its name differs between versions and
 * loaders, so it's found once by type instead of by name.
 */
public final class BossBars {

    private static Field field;
    private static boolean searched;

    private BossBars() {}

    public static Collection<?> events(Object overlay) {
        if (overlay == null) return Collections.emptyList();
        try {
            if (!searched) {
                searched = true;
                for (Field f : overlay.getClass().getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        field = f;
                        break;
                    }
                }
            }
            if (field == null) return Collections.emptyList();
            Object map = field.get(overlay);
            return map instanceof Map<?, ?> m ? m.values() : Collections.emptyList();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Collections.emptyList();
        }
    }
}
