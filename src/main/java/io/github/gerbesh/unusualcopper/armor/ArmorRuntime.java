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
        return completeSet(player)?player.getItemBySlot(EquipmentSlot.CHEST):ItemStack.EMPTY;
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
    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))return;
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
        ItemStack chest=chest(player);
        if(chest.isEmpty()){ArmorTelemetry.limited(player,java.util.List.of());return;}
        int profile=ArmorData.profile(chest);
        // Build intentions first. Neither an effect nor its energy debit may run until
        // the server has allocated all eligible tick requests to piece and set limits.
        var actions=new java.util.EnumMap<ArmorModuleType,Runnable>(ArmorModuleType.class);
        if(player.isUnderWater() && player.getAirSupply()<player.getMaxAirSupply()
                && equipped(player,EquipmentSlot.HEAD,ArmorModuleType.AQUA_LUNGS,profile)) {
            actions.put(ArmorModuleType.AQUA_LUNGS,
                    ()->player.setAirSupply(Math.min(player.getMaxAirSupply(),player.getAirSupply()+4)));
        }
        if(equipped(player,EquipmentSlot.CHEST,ArmorModuleType.SHIELD_FIELD,profile))
            actions.put(ArmorModuleType.SHIELD_FIELD,()->{});
        if(!player.onGround() && equipped(player,EquipmentSlot.FEET,ArmorModuleType.FALL_DAMPER,profile))
            actions.put(ArmorModuleType.FALL_DAMPER,()->{});
        if(movement!=null && equipped(player,EquipmentSlot.LEGS,ArmorModuleType.STRIDE_DRIVE,profile))
            actions.put(ArmorModuleType.STRIDE_DRIVE,
                    ()->movement.addTransientModifier(new AttributeModifier(STRIDE,"Copper stride drive",0.05D,AttributeModifier.Operation.ADDITION)));

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
                        ArmorData.settings(player.getItemBySlot(module.slot),module,profile).priority(),module.cue)).toList();
        var plan=ArmorPowerScheduler.plan(requests,
                io.github.gerbesh.unusualcopper.item.CopperBatteryItem.stored(ArmorData.battery(chest)));
        ArmorTelemetry.limited(player,plan.limited());
        if(plan.cost()>0 && ArmorData.pay(chest,plan.cost())) {
            for(var module:plan.accepted()) {
                actions.get(module).run();
                ArmorTelemetry.record(player,module,module.cue);
            }
            sync(player);
        }
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getDistance()<=3 || event.getDamageMultiplier()<=0)return;
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        if(equipped(player,EquipmentSlot.FEET,ArmorModuleType.FALL_DAMPER,ArmorData.profile(chest))
                && ArmorData.payEvent(chest,1000)) {
            event.setDamageMultiplier(0F);
            ArmorTelemetry.record(player,ArmorModuleType.FALL_DAMPER,1000);
            sync(player);
        }
    }
    @SubscribeEvent public static void shield(LivingDamageEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getAmount()<=0)return;
        COMBAT_UNTIL.put(player,(long)player.tickCount+100);
        if(event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        float prevented=Math.min(event.getAmount(),2F);
        int cue=(int)Math.ceil(prevented*300F);
        if(equipped(player,EquipmentSlot.CHEST,ArmorModuleType.SHIELD_FIELD,ArmorData.profile(chest))
                && ArmorData.payEvent(chest,cue)) {
            event.setAmount(Math.max(0F,event.getAmount()-prevented));
            ArmorTelemetry.record(player,ArmorModuleType.SHIELD_FIELD,cue);
            sync(player);
        }
    }
    @SubscribeEvent public static void mining(PlayerEvent.BreakSpeed event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getNewSpeed()<=0)return;
        MINING_UNTIL.put(player,(long)player.tickCount+10);
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        if(equipped(player,EquipmentSlot.LEGS,ArmorModuleType.MINING_SERVO,ArmorData.profile(chest))
                && ArmorData.pay(chest,ArmorModuleType.MINING_SERVO.cue)){
            event.setNewSpeed(event.getNewSpeed()*1.4F);
            ArmorTelemetry.record(player,ArmorModuleType.MINING_SERVO,ArmorModuleType.MINING_SERVO.cue);
            sync(player);
        }
    }
    /** F1: server-side boosted jump, real CuE event debit, 40-tick cooldown. */
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        ItemStack chest=chest(player);
        if(chest.isEmpty() || !equipped(player,EquipmentSlot.FEET,ArmorModuleType.JUMP_DRIVE,ArmorData.profile(chest)))return;
        var data=player.getPersistentData();
        long now=player.level().getGameTime();
        if(data.contains("UnusualCopperLastJump")){
            long last=data.getLong("UnusualCopperLastJump");
            if(now>=last && now-last<40)return;
        }
        if(!ArmorData.payEvent(chest,2000))return;
        ArmorTelemetry.record(player,ArmorModuleType.JUMP_DRIVE,2000);
        data.putLong("UnusualCopperLastJump",now);
        var velocity=player.getDeltaMovement();
        player.setDeltaMovement(velocity.x,Math.max(velocity.y,0.75D),velocity.z);
        player.hurtMarked=true;
        sync(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player){ArmorTelemetry.clear(player);COMBAT_UNTIL.remove(player);MINING_UNTIL.remove(player);}
        var attribute=event.getEntity().getAttribute(Attributes.MOVEMENT_SPEED);
        if(attribute!=null && attribute.getModifier(STRIDE)!=null)attribute.removeModifier(STRIDE);
    }
}
