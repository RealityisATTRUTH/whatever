package com.reality.mobcraft.neo;

import java.lang.reflect.Method;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** The damage a hit actually did. NeoForge 26.1.2 calls it getInflictedDamage; the 26.1 and 26.1.1 betas call it getNewDamage. */
final class DamageCompat {
    private static final Method DEALT;
    static {
        Method m;
        try { m = LivingDamageEvent.Post.class.getMethod("getInflictedDamage"); }
        catch (NoSuchMethodException e) {
            try { m = LivingDamageEvent.Post.class.getMethod("getNewDamage"); }
            catch (NoSuchMethodException e2) { throw new IllegalStateException("No damage getter on LivingDamageEvent.Post", e2); }
        }
        DEALT = m;
    }
    private DamageCompat() {}
    static float dealt(LivingDamageEvent.Post e) {
        try { return (float) DEALT.invoke(e); }
        catch (ReflectiveOperationException ex) { return e.getOriginalDamage(); }
    }
}
