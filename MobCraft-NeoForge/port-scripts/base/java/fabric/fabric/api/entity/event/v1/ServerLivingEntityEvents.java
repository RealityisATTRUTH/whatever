package com.reality.mobcraft.neo.fabric.fabric.api.entity.event.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class ServerLivingEntityEvents {
    public static final Event<AllowDamage> ALLOW_DAMAGE = new Event<>(l -> (e, s, a) -> { for (AllowDamage c : l) if (!c.allowDamage(e, s, a)) return false; return true; });
    public static final Event<AfterDamage> AFTER_DAMAGE = new Event<>(l -> (e, s, b, d, bl) -> { for (AfterDamage c : l) c.afterDamage(e, s, b, d, bl); });
    public static final Event<AllowDeath> ALLOW_DEATH = new Event<>(l -> (e, s, a) -> { for (AllowDeath c : l) if (!c.allowDeath(e, s, a)) return false; return true; });
    public static final Event<AfterDeath> AFTER_DEATH = new Event<>(l -> (e, s) -> { for (AfterDeath c : l) c.afterDeath(e, s); });
    public static final Event<MobConversion> MOB_CONVERSION = new Event<>(l -> (a, b, p) -> { for (MobConversion c : l) c.onConversion(a, b, p); });
    public interface AllowDamage { boolean allowDamage(LivingEntity entity, DamageSource source, float amount); }
    public interface AfterDamage { void afterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked); }
    public interface AllowDeath { boolean allowDeath(LivingEntity entity, DamageSource source, float amount); }
    public interface AfterDeath { void afterDeath(LivingEntity entity, DamageSource source); }
    public interface MobConversion { void onConversion(Mob previous, Mob converted, Object conversionContext); }
}
