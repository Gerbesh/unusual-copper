package io.github.gerbesh.unusualcopper.armor;

import io.github.gerbesh.unusualcopper.config.ArmorEffectsConfig;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Paid support effects. No permanent potion effects or attack-damage attributes. */
public final class ArmorSupport {
    private static final UUID KINETIC=UUID.fromString("ca62cad6-4798-4b3b-b206-d5478af299e3");
    private record Attack(AttackEntityEvent event,UUID target,int tick,float scale){}
    private static final Map<ServerPlayer,Attack> ATTACKS=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> HEALS=new WeakHashMap<>();
    private ArmorSupport(){}
    private static boolean enabled(ServerPlayer p,ItemStack chest,ArmorModuleType module){
        return ArmorRuntime.equipped(p,module.slot,module,ArmorData.profile(chest));
    }
    public static void removeModifier(Player p){
        var attribute=p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if(attribute!=null&&attribute.getModifier(KINETIC)!=null)attribute.removeModifier(KINETIC);
    }
    public static void contribute(ServerPlayer p,ItemStack chest,Map<ArmorModuleType,Runnable> actions){
        if((p.isOnFire()||p.isInLava())&&!p.hasEffect(MobEffects.FIRE_RESISTANCE)&&enabled(p,chest,ArmorModuleType.THERMAL_SHELL))
            actions.put(ArmorModuleType.THERMAL_SHELL,()->{});
        if(p.getHealth()<p.getMaxHealth()&&!p.hasEffect(MobEffects.REGENERATION)&&enabled(p,chest,ArmorModuleType.REGEN_CORE))
            actions.put(ArmorModuleType.REGEN_CORE,()->heal(p,chest));
        double boost=ArmorRuntime.addition(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE),
                .25D*ArmorRuntime.level(p,ArmorModuleType.KINETIC_STABILIZER),1D);
        if(boost>0&&enabled(p,chest,ArmorModuleType.KINETIC_STABILIZER))
            actions.put(ArmorModuleType.KINETIC_STABILIZER,()->p.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
                    .addTransientModifier(new AttributeModifier(KINETIC,"Copper kinetic stabilizer",boost,AttributeModifier.Operation.ADDITION)));
    }
    private static void heal(ServerPlayer p,ItemStack chest){
        long now=p.server.getTickCount();long last=HEALS.getOrDefault(p,Long.MIN_VALUE);
        if(last!=Long.MIN_VALUE&&now>=last&&now-last<ArmorEffectsConfig.healInterval())return;
        int level=ArmorRuntime.level(p,ArmorModuleType.REGEN_CORE),unit=ArmorEffectsConfig.healPrice();
        float limit=level;var quote=ArmorHealingMath.quote(p.getHealth(),p.getMaxHealth(),limit,unit,CopperBatteryItem.stored(ArmorData.battery(chest)));
        if(quote.cue()==0)return;
        int[] confirmed={0};Object attempt=new Object();
        if(!ArmorPayments.eventQuoted(p,chest,ArmorModuleType.REGEN_CORE,attempt,quote.cue(),()->confirmed[0],
                ()->ArmorEffectEvents.emit(p,ArmorEffectEvents.Kind.HEAL,level)))return;
        HEALS.put(p,now);
        // Match vanilla heal's Forge hook exactly once. Foreign nested healing is outside our baseline.
        float accepted=ForgeEventFactory.onLivingHeal(p,quote.hp());
        if(!Float.isFinite(accepted)||accepted<=0||!p.isAlive()||ArmorRuntime.chest(p)!=chest
                ||!enabled(p,chest,ArmorModuleType.REGEN_CORE)||level!=ArmorRuntime.level(p,ArmorModuleType.REGEN_CORE))return;
        float before=p.getHealth();
        var actual=ArmorHealingMath.quote(before,p.getMaxHealth(),Math.min(accepted,quote.hp()),unit,quote.cue());
        if(actual.cue()==0)return;
        confirmed[0]=quote.cue(); // Setter exceptions retain the authorization rather than creating free HP.
        p.setHealth(actual.target());
        confirmed[0]=ArmorHealingMath.confirmed(p.getHealth()-before,actual.hp(),unit,quote.cue());
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void thermal(LivingDamageEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!Float.isFinite(event.getAmount())||event.getAmount()<=0
                ||!event.getSource().is(DamageTypeTags.IS_FIRE)||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                ||event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE))return;
        var chest=ArmorRuntime.chest(p);if(chest.isEmpty()||!enabled(p,chest,ArmorModuleType.THERMAL_SHELL))return;
        if(ArmorPayments.tickEvent(p,chest,ArmorModuleType.THERMAL_SHELL,event,()->!event.isCanceled()&&event.getAmount()==0,
                ()->ArmorEffectEvents.emit(p,ArmorEffectEvents.Kind.THERMAL,1)))event.setAmount(0);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void attack(AttackEntityEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        float strength=p.getAttackStrengthScale(.5F);
        if(!Float.isFinite(strength)){ATTACKS.remove(p);return;}
        strength=Math.max(0F,Math.min(1F,strength));
        ATTACKS.put(p,new Attack(event,event.getTarget().getUUID(),p.server.getTickCount(),.2F+.8F*strength*strength));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void strength(LivingHurtEvent event){
        if(!(event.getSource().getDirectEntity() instanceof ServerPlayer p)||event.getSource().getEntity()!=p
                ||event.getEntity().level()!=p.level()
                ||!event.getSource().is(DamageTypes.PLAYER_ATTACK)||!Float.isFinite(event.getAmount())||event.getAmount()<=0
                ||event.getEntity() instanceof Player&&!ArmorEffectsConfig.pvp())return;
        var intent=ATTACKS.remove(p);
        if(intent==null||intent.event.isCanceled()||intent.tick!=p.server.getTickCount()||!intent.target.equals(event.getEntity().getUUID()))return;
        ArmorRuntime.combat(p);
        var chest=ArmorRuntime.chest(p);
        if(chest.isEmpty()||!enabled(p,chest,ArmorModuleType.STRENGTH_SERVO)
                ||!ProtectionPolicy.allowed(p.serverLevel(),event.getEntity().blockPosition(),p.getUUID()))return;
        int level=ArmorRuntime.level(p,ArmorModuleType.STRENGTH_SERVO);float before=event.getAmount();
        float bonus=(float)Math.min(2F*level*intent.scale,Math.max(0F,30F-before));if(bonus<=0)return;
        if(ArmorPayments.tickEvent(p,chest,ArmorModuleType.STRENGTH_SERVO,event,
                ()->!event.isCanceled()&&!intent.event.isCanceled()&&event.getAmount()>before,
                ()->ArmorEffectEvents.emit(p,ArmorEffectEvents.Kind.STRENGTH,level)))event.setAmount(before+bonus);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)ATTACKS.clear();}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        removeModifier(event.getEntity());if(event.getEntity() instanceof ServerPlayer p){ATTACKS.remove(p);HEALS.remove(p);}
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event){ATTACKS.clear();HEALS.clear();}
}
