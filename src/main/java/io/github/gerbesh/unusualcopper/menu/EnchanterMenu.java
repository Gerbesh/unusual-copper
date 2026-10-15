package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.network.EnchanterNetwork;
import io.github.gerbesh.unusualcopper.network.EnchanterNetwork.Result;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.items.*;
import java.util.*;

public final class EnchanterMenu extends AbstractContainerMenu {
    public static final int QUOTE_TICKS=600;
    private record Pending(UUID nonce,UUID actor,long issued,long revision,ItemKey item,EnchantmentPolicy.Offer offer,ItemStack preview){}
    public final BlockPos pos;private final CopperMachineBlockEntity machine;private final Player viewer;
    private Pending pending;private long lastReply=Long.MIN_VALUE,lastSelect=Long.MIN_VALUE;private Result result=Result.NONE;
    public EnchanterNetwork.Reply snapshot;public int serial;
    public EnchanterMenu(int id,Inventory inventory,FriendlyByteBuf b){this(id,inventory,null,b.readBlockPos(),new ItemStackHandler(4));}
    public EnchanterMenu(int id,Inventory inventory,CopperMachineBlockEntity machine){this(id,inventory,machine,machine.getBlockPos(),machine.inventory);}
    private EnchanterMenu(int id,Inventory inventory,CopperMachineBlockEntity machine,BlockPos pos,IItemHandler items){
        super(ModContent.ENCHANTER_MENU.get(),id);this.machine=machine;this.pos=pos;viewer=inventory.player;
        addSlot(new SlotItemHandler(items,0,18,30){@Override public int getMaxStackSize(){return 1;}});addSlot(new SlotItemHandler(items,1,18,60));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,79+col*18,126+row*18));for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,79+col*18,184));
    }
    @Override public boolean stillValid(Player player){return machine==null||player==viewer&&!machine.isRemoved()&&machine.kind==MachineKind.ENCHANTMENT_RESONATOR&&player.level()==machine.getLevel()&&player.level().hasChunkAt(pos)&&player.level().getBlockEntity(pos)==machine&&machine.canAccess(player)&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&player.level() instanceof ServerLevel level&&ProtectionPolicy.allowed(level,pos,machine.owner());}
    private boolean context(ServerPlayer player,UUID runtime){return machine!=null&&player.containerMenu==this&&stillValid(player)&&machine.runtimeIdentity.equals(runtime);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();boolean moved=index<2?moveItemStackTo(stack,2,slots.size(),true):stack.is(Items.LAPIS_LAZULI)?moveItemStackTo(stack,1,2,false):moveItemStackTo(stack,0,1,false);if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return original;}
    public void receive(EnchanterNetwork.Reply reply){if(reply.menu()==containerId&&reply.pos().equals(pos)){snapshot=reply;serial++;}}
    public EnchanterNetwork.Quote quote(){if(pending==null)return null;return new EnchanterNetwork.Quote(pending.nonce,pending.offer,pending.preview,(int)Math.max(0,QUOTE_TICKS-(viewer.level().getGameTime()-pending.issued)));}
    public boolean select(UUID runtime,ResourceLocation enchantment,ServerPlayer player){
        if(!context(player,runtime))return false;long now=player.level().getGameTime();if(lastSelect!=Long.MIN_VALUE&&now-lastSelect<2)return false;lastSelect=now;pending=null;
        try{var source=machine.inventory.getStackInSlot(0).copy();var revision=machine.enchantmentRevision;var offer=EnchantmentPolicy.offer(source,enchantment);if(offer==null)return fail(player,Result.INVALID);
            var preview=EnchantmentPolicy.apply(source,offer);if(!context(player,runtime)||revision!=machine.enchantmentRevision||!ItemKey.of(source).matches(machine.inventory.getStackInSlot(0)))return fail(player,Result.STALE);
            pending=new Pending(UUID.randomUUID(),player.getUUID(),now,revision,ItemKey.of(source),offer,preview);reply(player,Result.QUOTED);return true;
        }catch(RuntimeException unsupported){UnusualCopper.LOGGER.error("Cannot prepare enchantment offer",unsupported);return fail(player,Result.PROVIDER_ERROR);}
    }
    private boolean fail(ServerPlayer player,Result why){reply(player,why);return false;}
    private boolean unchanged(Pending q){var target=machine.inventory.getStackInSlot(0);return q.revision==machine.enchantmentRevision&&target.getCount()==1&&q.item.matches(target);}
    public boolean confirm(UUID runtime,UUID nonce,ServerPlayer player){
        if(!context(player,runtime))return false;var q=pending;pending=null;
        if(q==null||!q.nonce.equals(nonce)||!q.actor.equals(player.getUUID())||player.level().getGameTime()-q.issued>=QUOTE_TICKS||!unchanged(q))return fail(player,Result.STALE);
        if(!machine.energyEnabled())return fail(player,Result.PAUSED);var lapis=machine.inventory.getStackInSlot(1);if(!lapis.is(Items.LAPIS_LAZULI)||lapis.getCount()<64)return fail(player,Result.NO_LAPIS);if(machine.energy.stored()<q.offer.cue())return fail(player,Result.NO_ENERGY);
        final XpWallet.State before,after;
        try{before=XpWallet.capture(player);after=XpWallet.spend(before,q.offer.xp());}catch(IllegalArgumentException insufficient){return fail(player,Result.NO_XP);}
        final ItemStack replacement,remainingLapis,paidLapis;
        try{
            var xp=new PlayerXpEvent.XpChange(player,-q.offer.xp());if(MinecraftForge.EVENT_BUS.post(xp))return fail(player,Result.XP_CANCELLED);if(xp.getAmount()!=-q.offer.xp())return fail(player,Result.XP_CHANGED);
            int levels=after.level()-before.level();if(levels!=0){var change=new PlayerXpEvent.LevelChange(player,levels);if(MinecraftForge.EVENT_BUS.post(change))return fail(player,Result.XP_CANCELLED);if(change.getLevels()!=levels)return fail(player,Result.XP_CHANGED);}
            var actual=EnchantmentPolicy.offer(machine.inventory.getStackInSlot(0).copy(),q.offer.enchantment());if(!q.offer.equals(actual))return fail(player,Result.STALE);
            replacement=EnchantmentPolicy.apply(machine.inventory.getStackInSlot(0).copy(),actual);paidLapis=machine.inventory.getStackInSlot(1).copy();remainingLapis=paidLapis.copy();remainingLapis.shrink(64);
        }catch(RuntimeException hook){UnusualCopper.LOGGER.error("Enchantment hook failed before commit",hook);return fail(player,Result.PROVIDER_ERROR);}
        if(!context(player,runtime)||!unchanged(q)||!before.matches(player)||!machine.energyEnabled())return fail(player,Result.STALE);
        lapis=machine.inventory.getStackInSlot(1);if(!ItemStack.matches(lapis,paidLapis)||!lapis.is(Items.LAPIS_LAZULI)||lapis.getCount()<64||machine.energy.stored()<q.offer.cue())return fail(player,Result.STALE);
        // Private state assignments only; comparator/neighbor notifications are deferred until all are complete.
        machine.commitLocally(()->{after.apply(player);machine.energy.spend(q.offer.cue());machine.inventory.setStackInSlot(1,remainingLapis);machine.inventory.setStackInSlot(0,replacement);if(machine.owner()==null)machine.setOwner(player.getUUID());machine.enchantmentUntil=player.level().getGameTime()+20;machine.setChanged();});
        try{player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress,player.totalExperience,player.experienceLevel));player.awardStat(net.minecraft.stats.Stats.ENCHANT_ITEM);net.minecraft.advancements.CriteriaTriggers.ENCHANTED_ITEM.trigger(player,replacement,Math.max(0,before.level()-after.level()));player.level().playSound(null,pos,net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,net.minecraft.sounds.SoundSource.BLOCKS,.8F,1F);}catch(RuntimeException unavailable){UnusualCopper.LOGGER.debug("Skipped post-commit enchanting notification",unavailable);}
        reply(player,Result.APPLIED);return true;
    }
    private void reply(ServerPlayer player,Result reason){
        if(reason!=Result.NONE)result=reason;long points=0;try{points=(long)Math.floor(XpWallet.points(player)+.0001);}catch(IllegalArgumentException ignored){}
        var item=machine.inventory.getStackInSlot(0);if(pending!=null&&(!unchanged(pending)||player.level().getGameTime()-pending.issued>=QUOTE_TICKS)){pending=null;result=Result.STALE;}
        var lapis=machine.inventory.getStackInSlot(1);EnchanterNetwork.reply(player,new EnchanterNetwork.Reply(containerId,pos,machine.runtimeIdentity,EnchantmentPolicy.offers(item),quote(),points,machine.energy.stored(),lapis.is(Items.LAPIS_LAZULI)?lapis.getCount():0,result,machine.redstoneMode,machine.energyEnabled()));lastReply=player.level().getGameTime();
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(machine!=null&&viewer instanceof ServerPlayer player&&player.containerMenu==this&&stillValid(player)&&(lastReply==Long.MIN_VALUE||player.level().getGameTime()-lastReply>=10))reply(player,Result.NONE);}
    @Override public boolean clickMenuButton(Player player,int button){if(button!=0||!(player instanceof ServerPlayer server)||machine==null||!context(server,machine.runtimeIdentity))return false;machine.redstoneMode=(machine.redstoneMode+1)%3;machine.setChanged();reply(server,Result.NONE);return true;}
}
