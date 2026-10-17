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

/**
 * One server-authoritative wearer scheduler. The E6A wearable power bus requires
 * all four chassis pieces of the same tier, with one physical chest battery.
 * Does not manipulate vanilla/foreign potion effects or flight permissions.
 */
public final class ArmorRuntime {
    private static final UUID STRIDE=UUID.fromString("2a48d4cb-52ee-4473-9dc9-7cae61db6e0c");
    private ArmorRuntime(){}

    private static boolean completeSet(ServerPlayer player) {
        int tier=0;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            if(!(player.getItemBySlot(slot).getItem() instanceof ProgrammableArmorItem armor))return false;
            if(tier==0)tier=armor.tier;
            else if(tier!=armor.tier)return false;
        }
        return true;
    }
    private static ItemStack chest(ServerPlayer player) {
        return completeSet(player)?player.getItemBySlot(EquipmentSlot.CHEST):ItemStack.EMPTY;
    }
    private static boolean equipped(ServerPlayer player,EquipmentSlot slot,ArmorModuleType module,int profile) {
        ItemStack stack=player.getItemBySlot(slot);
        return ArmorData.enabled(module,profile) && ArmorData.modules(stack).contains(module);
    }
    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))return;
        var movement=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(movement!=null && movement.getModifier(STRIDE)!=null)movement.removeModifier(STRIDE);
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        int profile=ArmorData.profile(chest);
        int used=0;
        boolean paid=false;
        if(player.isUnderWater() && player.getAirSupply()<player.getMaxAirSupply()
                && equipped(player,EquipmentSlot.HEAD,ArmorModuleType.AQUA_LUNGS,profile)
                && ArmorData.pay(chest,ArmorModuleType.AQUA_LUNGS.cue)) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(),player.getAirSupply()+4));
            used+=ArmorModuleType.AQUA_LUNGS.cue;paid=true;
        }
        if(movement!=null && equipped(player,EquipmentSlot.LEGS,ArmorModuleType.STRIDE_DRIVE,profile)
                && used+ArmorModuleType.STRIDE_DRIVE.cue<=ArmorProgression.totalBudget()
                && ArmorData.pay(chest,ArmorModuleType.STRIDE_DRIVE.cue)) {
            movement.addTransientModifier(new AttributeModifier(STRIDE,"Copper stride drive",0.05D,AttributeModifier.Operation.ADDITION));
            used+=ArmorModuleType.STRIDE_DRIVE.cue;paid=true;
        }
        // The shield's 6 CuE/t idle charge is separate from its 300 CuE per absorbed HP.
        if(equipped(player,EquipmentSlot.CHEST,ArmorModuleType.SHIELD_FIELD,profile)
                && used+ArmorModuleType.SHIELD_FIELD.cue<=ArmorProgression.totalBudget()
                && ArmorData.pay(chest,ArmorModuleType.SHIELD_FIELD.cue)){
            used+=ArmorModuleType.SHIELD_FIELD.cue;paid=true;
        }
        if(!player.onGround() && equipped(player,EquipmentSlot.FEET,ArmorModuleType.FALL_DAMPER,profile)
                && used+ArmorModuleType.FALL_DAMPER.cue<=ArmorProgression.totalBudget()
                && ArmorData.pay(chest,ArmorModuleType.FALL_DAMPER.cue)){
            used+=ArmorModuleType.FALL_DAMPER.cue;paid=true;
        }
        if(equipped(player,EquipmentSlot.FEET,ArmorModuleType.JUMP_DRIVE,profile)
                && used+ArmorModuleType.JUMP_DRIVE.cue<=ArmorProgression.totalBudget()
                && ArmorData.pay(chest,ArmorModuleType.JUMP_DRIVE.cue)){
            used+=ArmorModuleType.JUMP_DRIVE.cue;paid=true;
        }
        // F2: crouch in mid-air to stabilize vertically. This NEVER touches mayfly,
        // flying or another mod's permissions and stops immediately when unpowered.
        ItemStack boots=player.getItemBySlot(EquipmentSlot.FEET);
        if(boots.getItem() instanceof ProgrammableArmorItem chassis
                && chassis.tier>=HoverPolicy.REQUIRED_TIER
                && !player.getAbilities().mayfly && !player.isPassenger()
                && !player.onGround() && !player.isInWater() && !player.isInLava()
                && player.isShiftKeyDown()
                && equipped(player,EquipmentSlot.FEET,ArmorModuleType.HOVER_DRIVE,profile)
                && used+HoverPolicy.CUE_PER_TICK<=ArmorProgression.totalBudget()) {
            var data=player.getPersistentData();
            long now=player.level().getGameTime();
            long last=data.getLong("UCHoverLastTick");
            long started=data.getLong("UCHoverStart");
            long restUntil=data.getLong("UCHoverRestUntil");
            if(!data.contains("UCHoverLastTick") || now<=last || now-last>1) {
                // Releasing crouch, losing equipment or changing dimension ends the session.
                started=now;
            }
            if(now>=restUntil && !HoverPolicy.canRun(chassis.tier,now,started,restUntil)) {
                // Duration cap: maintain a separate, persistent cooldown on the wearer.
                if(now>=started && now-started>=HoverPolicy.MAX_SESSION_TICKS)
                    data.putLong("UCHoverRestUntil",HoverPolicy.restUntil(started));
            } else if(HoverPolicy.canRun(chassis.tier,now,started,restUntil)
                    && ArmorData.pay(chest,HoverPolicy.CUE_PER_TICK)) {
                data.putLong("UCHoverStart",started);
                data.putLong("UCHoverLastTick",now);
                var velocity=player.getDeltaMovement();
                // Vertical thrust roughly compensates vanilla gravity. Collisions and world
                // border remain vanilla-server-controlled; no noclip or free-flying grant.
                player.setDeltaMovement(velocity.x*0.65D,0.075D,velocity.z*0.65D);
                player.fallDistance=0F;
                player.hurtMarked=true;
                used+=HoverPolicy.CUE_PER_TICK;
                paid=true;
            }
        }
        if(paid)sync(player);
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getDistance()<=3 || event.getDamageMultiplier()<=0)return;
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        if(equipped(player,EquipmentSlot.FEET,ArmorModuleType.FALL_DAMPER,ArmorData.profile(chest))
                && ArmorData.payEvent(chest,1000)) {
            event.setDamageMultiplier(0F);
            sync(player);
        }
    }
    @SubscribeEvent public static void shield(LivingDamageEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getAmount()<=0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        float prevented=Math.min(event.getAmount(),2F);
        int cue=(int)Math.ceil(prevented*300F);
        if(equipped(player,EquipmentSlot.CHEST,ArmorModuleType.SHIELD_FIELD,ArmorData.profile(chest))
                && ArmorData.payEvent(chest,cue)) {
            event.setAmount(Math.max(0F,event.getAmount()-prevented));
            sync(player);
        }
    }
    @SubscribeEvent public static void mining(PlayerEvent.BreakSpeed event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getNewSpeed()<=0)return;
        ItemStack chest=chest(player);
        if(chest.isEmpty())return;
        if(equipped(player,EquipmentSlot.LEGS,ArmorModuleType.MINING_SERVO,ArmorData.profile(chest))
                && ArmorData.pay(chest,ArmorModuleType.MINING_SERVO.cue)){
            event.setNewSpeed(event.getNewSpeed()*1.4F);
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
        data.putLong("UnusualCopperLastJump",now);
        var velocity=player.getDeltaMovement();
        player.setDeltaMovement(velocity.x,Math.max(velocity.y,0.75D),velocity.z);
        player.hurtMarked=true;
        sync(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var attribute=event.getEntity().getAttribute(Attributes.MOVEMENT_SPEED);
        if(attribute!=null && attribute.getModifier(STRIDE)!=null)attribute.removeModifier(STRIDE);
    }
}
