package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * One server-authoritative wearer scheduler. The E6A wearable power bus requires
 * all four chassis pieces of the same tier, with one physical chest battery.
 * Does not manipulate vanilla/foreign potion effects or flight permissions.
 */
public final class ArmorRuntime {
    private static final UUID STRIDE=UUID.fromString("2a48d4cb-52ee-4473-9dc9-7cae61db6e0c");
    private static final UUID SWIM=UUID.fromString("447795d5-b9dd-48f2-95e4-33c461cd5a8a");
    // Short-lived input conditions belong to this player session, never saved NBT.
    private static final Map<ServerPlayer,Long> COMBAT_UNTIL=new WeakHashMap<>(),MINING_UNTIL=new WeakHashMap<>();
    private ArmorRuntime(){}

    public static boolean completeSet(ServerPlayer player) {
        int tier=0;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            var item=player.getItemBySlot(slot);
            if(!(item.getItem() instanceof ProgrammableArmorItem armor)||armor.getEquipmentSlot()!=slot||item.getCount()!=1||!ArmorData.upgradeSafe(item))return false;
            if(tier==0)tier=armor.tier;
            else if(tier!=armor.tier)return false;
        }
        return true;
    }
    /** Owner-only, server-authored F3 status for the armor HUD. No NBT sent to clients. */
    public record FlightStatus(boolean session,int ticksLeft,int heightLeft) {
        public FlightStatus {
            if(ticksLeft<0||ticksLeft>LimitedFlightPolicy.MAX_SESSION_TICKS
                    ||heightLeft<0||heightLeft>LimitedFlightPolicy.MAX_HEIGHT
                    ||!session&&(ticksLeft!=0||heightLeft!=0))
                throw new IllegalArgumentException("Invalid flight HUD state");
        }
    }
    public static FlightStatus flightStatus(ServerPlayer player) {
        if(player.onGround())return new FlightStatus(false,0,0);
        var data=player.getPersistentData();
        if(!data.contains("UCF3AnchorY",net.minecraft.nbt.Tag.TAG_DOUBLE))
            return new FlightStatus(false,0,0);
        String dimension=player.level().dimension().location().toString();
        boolean same=dimension.equals(data.getString("UCF3Dimension"));
        long start=data.contains("UCF3Start",net.minecraft.nbt.Tag.TAG_LONG)
                ?data.getLong("UCF3Start"):-1L;
        long now=player.level().getGameTime();
        return new FlightStatus(true,
                LimitedFlightPolicy.remainingTicks(now,start,same),
                LimitedFlightPolicy.headroom(data.getDouble("UCF3AnchorY"),player.getY(),same));
    }
    private static ItemStack chest(ServerPlayer player) {
        return player.isAlive()&&!player.isSpectator()&&!player.isCreative()&&completeSet(player)?player.getItemBySlot(EquipmentSlot.CHEST):ItemStack.EMPTY;
    }
    private static int level(ServerPlayer player,ArmorModuleType module){return ArmorData.moduleLevel(player.getItemBySlot(module.slot),module);}
    private static double addition(net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,double requested,double cap){
        if(attribute==null)return 0;
        double multiplier=1+attribute.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE).stream().mapToDouble(AttributeModifier::getAmount).sum();
        for(var mod:attribute.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL))multiplier*=1+mod.getAmount();
        return ArmorEffectLimits.addition(attribute.getValue(),requested,cap,multiplier);
    }
    private static boolean equipped(ServerPlayer player,EquipmentSlot slot,ArmorModuleType module,int profile) {
        ItemStack stack=player.getItemBySlot(slot);
        return ArmorData.modules(stack).contains(module)
                && ArmorData.settings(stack,module,profile).permits(ArmorData.percentage(player.getItemBySlot(EquipmentSlot.CHEST)),conditions(player));
    }
    public static int conditions(ServerPlayer player){
        int flags=0;
        if(player.isUnderWater())flags|=1<<ArmorModuleSettings.Trigger.UNDERWATER.ordinal();
        if(!player.onGround())flags|=1<<ArmorModuleSettings.Trigger.AIRBORNE.ordinal();
        if(player.isOnFire())flags|=1<<ArmorModuleSettings.Trigger.ON_FIRE.ordinal();
        if(COMBAT_UNTIL.getOrDefault(player,Long.MIN_VALUE)>=player.tickCount)flags|=1<<ArmorModuleSettings.Trigger.IN_COMBAT.ordinal();
        if(player.getHealth()<player.getMaxHealth()*.4F)flags|=1<<ArmorModuleSettings.Trigger.LOW_HEALTH.ordinal();
        if(MINING_UNTIL.getOrDefault(player,Long.MIN_VALUE)>=player.tickCount)flags|=1<<ArmorModuleSettings.Trigger.MINING.ordinal();
        return flags;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))return;
        if(!ArmorPayments.beginContinuous(player))return;
        // Ground contact alone resets an F3 session; re-equipping in mid-air,
        // disconnects and dimension transitions cannot cheaply reset the ceiling.
        var flightData=player.getPersistentData();
        if(player.onGround()) {
            flightData.remove("UCF3AnchorY");
            flightData.remove("UCF3Start");
            flightData.remove("UCF3Dimension");
        }
        var movement=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(movement!=null && movement.getModifier(STRIDE)!=null)movement.removeModifier(STRIDE);
        var swim=player.getAttribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get());
        if(swim!=null&&swim.getModifier(SWIM)!=null)swim.removeModifier(SWIM);
        ItemStack chest=chest(player);
        if(chest.isEmpty()){ArmorTelemetry.limited(player,java.util.List.of());return;}
        int profile=ArmorData.profile(chest);
        // Build intentions first. Neither an effect nor its energy debit may run until
        // the server has allocated all eligible tick requests to piece and set limits.
        var actions=new java.util.EnumMap<ArmorModuleType,Runnable>(ArmorModuleType.class);
        if(player.isUnderWater()&&equipped(player,EquipmentSlot.HEAD,ArmorModuleType.AQUA_LUNGS,profile)) {
            boolean needsAir=player.getAirSupply()<player.getMaxAirSupply()&&!player.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING);
            double swimBoost=level(player,ArmorModuleType.AQUA_LUNGS)>=2?addition(swim,.35D,2D):0;
            if(needsAir||swimBoost>0)actions.put(ArmorModuleType.AQUA_LUNGS,()->{
                if(needsAir)player.setAirSupply(Math.min(player.getMaxAirSupply(),player.getAirSupply()+4));
                if(swimBoost>0)swim.addTransientModifier(new AttributeModifier(SWIM,"Copper aqua lungs",swimBoost,AttributeModifier.Operation.ADDITION));
            });
        }
        if(equipped(player,EquipmentSlot.CHEST,ArmorModuleType.SHIELD_FIELD,profile))
            actions.put(ArmorModuleType.SHIELD_FIELD,()->{});
        if(!player.onGround() && equipped(player,EquipmentSlot.FEET,ArmorModuleType.FALL_DAMPER,profile))
            actions.put(ArmorModuleType.FALL_DAMPER,()->{});
        double stride=addition(movement,.05D*level(player,ArmorModuleType.STRIDE_DRIVE),.30D);
        if(stride>0 && equipped(player,EquipmentSlot.LEGS,ArmorModuleType.STRIDE_DRIVE,profile))
            actions.put(ArmorModuleType.STRIDE_DRIVE,
                    ()->movement.addTransientModifier(new AttributeModifier(STRIDE,"Copper stride drive",stride,AttributeModifier.Operation.ADDITION)));

        ItemStack boots=player.getItemBySlot(EquipmentSlot.FEET);
        // F2 uses no mayfly, and only asks for power while crouching in mid-air.
        if(boots.getItem() instanceof ProgrammableArmorItem chassis
                && chassis.tier>=HoverPolicy.REQUIRED_TIER
                && !player.getAbilities().mayfly && !player.isPassenger()
                && !player.onGround() && !player.isInWater() && !player.isInLava()
                && player.isShiftKeyDown()
                && equipped(player,EquipmentSlot.FEET,ArmorModuleType.HOVER_DRIVE,profile)) {
            var data=player.getPersistentData();
            long now=player.level().getGameTime();
            long last=data.getLong("UCHoverLastTick");
            long started=data.getLong("UCHoverStart");
            long restUntil=data.getLong("UCHoverRestUntil");
            if(!data.contains("UCHoverLastTick") || now<=last || now-last>1)started=now;
            if(now>=restUntil && !HoverPolicy.canRun(chassis.tier,now,started,restUntil)) {
                if(now>=started && now-started>=HoverPolicy.MAX_SESSION_TICKS)
                    data.putLong("UCHoverRestUntil",HoverPolicy.restUntil(started));
            } else if(HoverPolicy.canRun(chassis.tier,now,started,restUntil)) {
                final long runStarted=started;
                actions.put(ArmorModuleType.HOVER_DRIVE,()->{
                    data.putLong("UCHoverStart",runStarted);
                    data.putLong("UCHoverLastTick",now);
                    var velocity=player.getDeltaMovement();
                    player.setDeltaMovement(velocity.x*0.65D,0.075D,velocity.z*0.65D);
                    player.fallDistance=0F;
                    player.hurtMarked=true;
                });
            }
        }

        // F3A is paid bounded kinematic thrust, not a vanilla flight grant.
        ItemStack chestPiece=player.getItemBySlot(EquipmentSlot.CHEST);
        if(chestPiece.getItem() instanceof ProgrammableArmorItem chestArmor
                && boots.getItem() instanceof ProgrammableArmorItem bootArmor
                && player.isSprinting() && !player.isShiftKeyDown()
                && !player.onGround() && !player.isPassenger()
                && !player.isInWater() && !player.isInLava()
                && !player.getAbilities().mayfly && !player.getAbilities().flying
                && equipped(player,EquipmentSlot.CHEST,ArmorModuleType.FLIGHT_CORE_II,profile)) {
            long now=player.level().getGameTime();
            var data=player.getPersistentData();
            boolean newSession=!data.contains("UCF3AnchorY",net.minecraft.nbt.Tag.TAG_DOUBLE);
            long started=newSession?now:data.getLong("UCF3Start");
            double anchorY=newSession?player.getY():data.getDouble("UCF3AnchorY");
            String dimension=player.level().dimension().location().toString();
            boolean matchingDimension=newSession||dimension.equals(data.getString("UCF3Dimension"));
            if(LimitedFlightPolicy.canThrust(chestArmor.tier,bootArmor.tier,now,started,
                    matchingDimension,player.level().getWorldBorder().isWithinBounds(player.blockPosition()))) {
                actions.put(ArmorModuleType.FLIGHT_CORE_II,()->{
                    if(newSession) {
                        data.putLong("UCF3Start",now);
                        data.putDouble("UCF3AnchorY",anchorY);
                        data.putString("UCF3Dimension",dimension);
                    }
                    var look=player.getLookAngle();
                    var velocity=player.getDeltaMovement();
                    var horizontal=LimitedFlightPolicy.horizontal(
                            look.x*0.28D+velocity.x*0.30D,
                            look.z*0.28D+velocity.z*0.30D);
                    double vertical=LimitedFlightPolicy.vertical(look.y,
                            anchorY+LimitedFlightPolicy.MAX_HEIGHT-player.getY());
                    player.setDeltaMovement(horizontal[0],vertical,horizontal[1]);
                    player.fallDistance=0F;
                    player.hurtMarked=true;
                });
            }
        }
        // Jump is charged on the actual jump event only, never for standing still.
        // Optic visor and mining servo are not fake idle consumers: their runtime
        // visual/event features have independent activation paths.
        var requests=actions.keySet().stream()
                .map(module->new ArmorPowerScheduler.Demand(module,
                        ArmorData.settings(player.getItemBySlot(module.slot),module,profile).priority(),module.cue(level(player,module)))).toList();
        var plan=ArmorPowerScheduler.plan(requests,
                io.github.gerbesh.unusualcopper.item.CopperBatteryItem.stored(ArmorData.battery(chest)),ArmorPayments.committedPower(player));
        ArmorTelemetry.updateLimited(player,plan.limited(),actions.keySet());
        ArmorPayments.execute(player,chest,plan,actions);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fall(LivingFallEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getDistance()<=3 || event.getDamageMultiplier()<=0)return;
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        if(equipped(player,EquipmentSlot.FEET,ArmorModuleType.FALL_DAMPER,ArmorData.profile(chest))
                && ArmorPayments.event(player,chest,ArmorModuleType.FALL_DAMPER,event,1000,
                    ()->!event.isCanceled()&&event.getDamageMultiplier()==0F)) {
            event.setDamageMultiplier(0F);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void shield(LivingDamageEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !Float.isFinite(event.getAmount())||event.getAmount()<=0)return;
        COMBAT_UNTIL.put(player,(long)player.tickCount+100);
        if(event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        var quote=ArmorEffectLimits.shield(event.getAmount(),level(player,ArmorModuleType.SHIELD_FIELD),
                io.github.gerbesh.unusualcopper.item.CopperBatteryItem.stored(ArmorData.battery(chest)));
        float prevented=quote.prevented();int cue=quote.cue();
        if(cue<=0)return;
        if(equipped(player,EquipmentSlot.CHEST,ArmorModuleType.SHIELD_FIELD,ArmorData.profile(chest))
                && ArmorPayments.event(player,chest,ArmorModuleType.SHIELD_FIELD,event,cue,()->!event.isCanceled())) {
            event.setAmount(Math.max(0F,event.getAmount()-prevented));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void mining(PlayerEvent.BreakSpeed event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !Float.isFinite(event.getNewSpeed())||event.getNewSpeed()<=0)return;
        MINING_UNTIL.put(player,(long)player.tickCount+10);
        ItemStack chest=chest(player);
        if(chest.isEmpty()||!equipped(player,EquipmentSlot.LEGS,ArmorModuleType.MINING_SERVO,ArmorData.profile(chest)))return;
        var pos=event.getPosition();
        if(pos.isEmpty()||!io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(player.serverLevel(),pos.get(),player.getUUID())
                ||event.getState().requiresCorrectToolForDrops()&&!player.getMainHandItem().isCorrectToolForDrops(event.getState()))return;
        float speed=ArmorEffectLimits.mining(event.getNewSpeed(),level(player,ArmorModuleType.MINING_SERVO));
        if(speed<=event.getNewSpeed())return;
        if(ArmorPayments.mining(player,chest,event,event.getNewSpeed())){
            event.setNewSpeed(speed);
        }
    }
    /** F1: server-side boosted jump, real CuE event debit, 40-tick cooldown. */
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        ItemStack chest=chest(player);
        if(chest.isEmpty() || !equipped(player,EquipmentSlot.FEET,ArmorModuleType.JUMP_DRIVE,ArmorData.profile(chest)))return;
        int moduleLevel=level(player,ArmorModuleType.JUMP_DRIVE),cooldown=moduleLevel==1?40:60,cost=moduleLevel==1?2000:5000;
        double boost=moduleLevel==1?.75D:1D;
        if(player.getDeltaMovement().y>=boost)return;
        var data=player.getPersistentData();
        long now=player.level().getGameTime();
        if(data.contains("UnusualCopperLastJump")){
            long last=data.getLong("UnusualCopperLastJump");
            if(now>=last && now-last<cooldown)return;
        }
        if(!ArmorPayments.event(player,chest,ArmorModuleType.JUMP_DRIVE,event,cost,()->true))return;
        data.putLong("UnusualCopperLastJump",now);
        var velocity=player.getDeltaMovement();
        player.setDeltaMovement(velocity.x,boost,velocity.z);
        player.hurtMarked=true;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player){ArmorTelemetry.clear(player);COMBAT_UNTIL.remove(player);MINING_UNTIL.remove(player);}
        var attribute=event.getEntity().getAttribute(Attributes.MOVEMENT_SPEED);
        if(attribute!=null && attribute.getModifier(STRIDE)!=null)attribute.removeModifier(STRIDE);
        var swim=event.getEntity().getAttribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get());
        if(swim!=null&&swim.getModifier(SWIM)!=null)swim.removeModifier(SWIM);
    }
}
