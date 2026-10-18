package io.github.gerbesh.unusualcopper.item;

import io.github.gerbesh.unusualcopper.gameplay.WorkArea;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.network.AreaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;

public final class AreaWandItem extends Item {
    public static final String PREVIEW_SESSION="AreaPreviewSession";
    public AreaWandItem() { super(new Properties().stacksTo(1)); }
    private static void message(Player player, String key) { player.displayClientMessage(Component.translatable("wand.unusualcopper." + key), true); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isCrouching()) { var tag = stack.getOrCreateTag(); tag.putInt("Mode", (Math.floorMod(tag.getInt("Mode"), 3) + 1) % 3); clearSelection(tag); message(player, "mode." + tag.getInt("Mode")); }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    private static void clearSelection(CompoundTag tag) { tag.remove("Machine"); tag.remove("Dimension"); tag.remove("Instance"); tag.remove("First");tag.remove(PREVIEW_SESSION);tag.remove("ReturnToReaper");tag.remove("ReturnToMining"); }
    /** Periodic owner-only recovery also covers reconnects; no world scan or hover packets. */
    @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected) {
        if(!level.isClientSide&&entity instanceof ServerPlayer player&&(selected||player.getOffhandItem()==stack)
                &&player.tickCount%20==0) {
            var main=player.getMainHandItem();var mainTag=main.getTag();
            if(!selected&&main.getItem() instanceof AreaWandItem&&mainTag!=null&&mainTag.getInt("Mode")==0&&mainTag.contains("First"))return;
            syncSelection(player,stack);
        }
    }
    private static void syncSelection(ServerPlayer player,ItemStack stack) {
        var tag=stack.getTag();
        if(tag==null||Math.floorMod(tag.getInt("Mode"),3)!=0||!tag.contains("First"))return;
        var level=player.level();var anchor=BlockPos.of(tag.getLong("Machine"));
        if(!tag.contains("Machine")||!tag.getString("Dimension").equals(level.dimension().location().toString())
                ||!level.hasChunkAt(anchor)||!(level.getBlockEntity(anchor) instanceof CopperMachineBlockEntity machine)
                ||!machine.canAccess(player)||!tag.hasUUID("Instance")||!machine.runtimeIdentity.equals(tag.getUUID("Instance"))
                ||machine.arborist!=null||!(machine.kind.usesArea()||machine.mining!=null)) {
            clearSelection(tag);player.inventoryMenu.broadcastChanges();return;
        }
        // Old saved wands acquire a token on first re-equip without exposing private machine NBT.
        if(!tag.hasUUID(PREVIEW_SESSION)){tag.putUUID(PREVIEW_SESSION,java.util.UUID.randomUUID());player.inventoryMenu.broadcastChanges();}
        AreaNetwork.selection(player,new AreaNetwork.Selection(tag.getUUID(PREVIEW_SESSION),level.dimension().location(),anchor,BlockPos.of(tag.getLong("First")),machine.mining!=null));
    }
    public static boolean beginMiningBinding(ServerPlayer player,CopperMachineBlockEntity machine){if(!beginMiningSelection(player,machine))return false;player.getMainHandItem().getOrCreateTag().putInt("Mode",1);message(player,"storage");return true;}
    public static boolean beginMiningSelection(ServerPlayer player,CopperMachineBlockEntity machine){if(!beginReaperSelection(player,machine))return false;var tag=player.getMainHandItem().getOrCreateTag();tag.remove("ReturnToReaper");tag.putBoolean("ReturnToMining",true);return true;}
    public static boolean beginReaperSelection(ServerPlayer player,CopperMachineBlockEntity machine){
        if(!(player.getMainHandItem().getItem() instanceof AreaWandItem)){
            int slot=-1;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).getItem() instanceof AreaWandItem){slot=i;break;}
            if(slot<0){if(!(player.getOffhandItem().getItem() instanceof AreaWandItem))return false;var old=player.getMainHandItem();player.setItemInHand(InteractionHand.MAIN_HAND,player.getOffhandItem());player.setItemInHand(InteractionHand.OFF_HAND,old);}
            else if(slot<9)player.getInventory().selected=slot;
            else{var old=player.getMainHandItem();player.getInventory().setItem(player.getInventory().selected,player.getInventory().getItem(slot));player.getInventory().setItem(slot,old);}
        }
        ItemStack wand=player.getMainHandItem();
        var tag=wand.getOrCreateTag();clearSelection(tag);tag.putInt("Mode",0);tag.putLong("Machine",machine.getBlockPos().asLong());tag.putString("Dimension",player.level().dimension().location().toString());tag.putUUID("Instance",machine.runtimeIdentity);tag.putBoolean("ReturnToReaper",true);
        player.closeContainer();player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(player.getInventory().selected));player.inventoryMenu.broadcastChanges();message(player,"first");return true;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel(); var player = context.getPlayer(); var pos = context.getClickedPos();
        if (player == null || !level.hasChunkAt(pos)) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.FAIL;
        var tag = context.getItemInHand().getOrCreateTag(); int mode = Math.floorMod(tag.getInt("Mode"), 3);
        var clicked = level.getBlockEntity(pos);
        if (mode == 2) {
            if (clicked instanceof CopperMachineBlockEntity machine && machine.canAccess(player)) { NetworkHooks.openScreen(server, machine, pos); return InteractionResult.SUCCESS; }
            message(player, "denied"); return InteractionResult.FAIL;
        }
        if (clicked instanceof CopperMachineBlockEntity machine && (mode == 0 ? machine.kind.usesArea() || machine.arborist!=null || machine.mining!=null : machine.kind.hasInventory())) {
            if (!machine.canAccess(player)) { message(player, "denied"); return InteractionResult.FAIL; }
            clearSelection(tag);tag.putLong("Machine", pos.asLong()); tag.putString("Dimension", level.dimension().location().toString()); tag.putUUID("Instance", machine.runtimeIdentity);
            message(player, mode == 0 ? machine.arborist!=null?"plantation":"first" : "storage"); return InteractionResult.SUCCESS;
        }
        if (!tag.contains("Machine") || !tag.getString("Dimension").equals(level.dimension().location().toString())) { clearSelection(tag); message(player, "select_machine"); return InteractionResult.FAIL; }
        var anchor = BlockPos.of(tag.getLong("Machine"));
        if (!level.hasChunkAt(anchor) || !(level.getBlockEntity(anchor) instanceof CopperMachineBlockEntity machine) || !machine.canAccess(player) || !tag.hasUUID("Instance") || !machine.runtimeIdentity.equals(tag.getUUID("Instance"))) { clearSelection(tag); message(player, "stale"); return InteractionResult.FAIL; }
        boolean miningSelection=mode==0&&machine.mining!=null;
        if (miningSelection?!io.github.gerbesh.unusualcopper.gameplay.MiningFootprint.reachable(anchor.getX(),anchor.getZ(),pos.getX(),pos.getZ()):anchor.distSqr(pos)>32*32) { message(player, miningSelection?"mining_too_large":"too_far"); return InteractionResult.FAIL; }
        if (mode == 1) {
            if (!machine.bind(pos, context.getClickedFace(), player)) { message(player, "denied"); return InteractionResult.FAIL; }
            boolean reopen=tag.getBoolean("ReturnToMining");message(player, "bound"); clearSelection(tag);if(reopen&&machine.mining!=null&&player.distanceToSqr(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5)<=64)NetworkHooks.openScreen(server,machine,anchor);return InteractionResult.SUCCESS;
        }
        if(machine.arborist!=null) {
            int before=machine.arborist.points.size();
            if(!machine.togglePlantation(pos,player)) { message(player,"denied"); return InteractionResult.FAIL; }
            AreaNetwork.preview(server,pos,pos,true); message(player,machine.arborist.points.size()>before?"plantation_added":"plantation_removed"); return InteractionResult.SUCCESS;
        }
        if (!tag.contains("First")) { tag.putLong("First", pos.asLong());tag.putUUID(PREVIEW_SESSION,java.util.UUID.randomUUID());server.inventoryMenu.broadcastChanges();syncSelection(server,context.getItemInHand()); AreaNetwork.preview(server, pos, pos, true); message(player, "second"); return InteractionResult.SUCCESS; }
        var first = BlockPos.of(tag.getLong("First"));
        try {
            if(machine.mining!=null){
                var miningArea=new io.github.gerbesh.unusualcopper.gameplay.MiningFootprint(Math.min(first.getX(),pos.getX()),Math.min(first.getZ(),pos.getZ()),Math.max(first.getX(),pos.getX()),Math.max(first.getZ(),pos.getZ()));
                if(!machine.setMiningArea(miningArea,player)){message(player,"denied");return InteractionResult.FAIL;}
                AreaNetwork.preview(server,new BlockPos(miningArea.minX(),anchor.getY(),miningArea.minZ()),new BlockPos(miningArea.maxX(),anchor.getY(),miningArea.maxZ()),true);boolean reopen=tag.getBoolean("ReturnToMining");clearSelection(tag);message(player,"selected");
                if(reopen&&player.distanceToSqr(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5)<=64)NetworkHooks.openScreen(server,machine,anchor);return InteractionResult.SUCCESS;
            }
            var area = WorkArea.between(first, pos);
            if (!machine.setWorkArea(area, player)) { AreaNetwork.preview(server, area.min(), area.max(), false); message(player, "denied"); return InteractionResult.FAIL; }
            AreaNetwork.preview(server, area.min(), area.max(), true); tag.remove("First");tag.remove(PREVIEW_SESSION); message(player, "selected");
            if(tag.getBoolean("ReturnToReaper")&&machine.reaper!=null){
                machine.reaper.settings(new io.github.gerbesh.unusualcopper.gameplay.ReaperRule(true,machine.reaper.rule.adultsOnly(),java.util.Set.of(),java.util.Set.of(),java.util.Set.of()),machine.reaper.trigger,machine.reaper.keep);
                io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.cancel(machine,(net.minecraft.server.level.ServerLevel)level);machine.setChanged();clearSelection(tag);
                if(player.distanceToSqr(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5)<=64)io.github.gerbesh.unusualcopper.menu.ReaperMenu.open(server,machine);
                else player.displayClientMessage(Component.translatable("wand.unusualcopper.return_reaper"),false);
            }return InteractionResult.SUCCESS;
        } catch (IllegalArgumentException invalid) { AreaNetwork.preview(server, first, pos, false); message(player, machine.mining!=null?"mining_too_large":"too_large"); return InteractionResult.FAIL; }
    }
    @Override public CompoundTag getShareTag(ItemStack stack) {
        var tag=new CompoundTag();var source=stack.getTag();tag.putInt("Mode",source==null?0:Math.floorMod(source.getInt("Mode"),3));
        if(source!=null&&tag.getInt("Mode")==0&&source.hasUUID(PREVIEW_SESSION))tag.putUUID(PREVIEW_SESSION,source.getUUID(PREVIEW_SESSION));
        return tag;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("wand.unusualcopper.mode." + Math.floorMod(stack.getOrCreateTag().getInt("Mode"), 3)));
        tooltip.add(Component.translatable("wand.unusualcopper.tooltip"));
    }
}
