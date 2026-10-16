package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.network.RepairNetwork;
import io.github.gerbesh.unusualcopper.network.RepairNetwork.Result;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.items.*;
import java.util.*;

/** One owned item stays in its slot; only an explicit, fresh confirmation changes its durability. */
public final class RepairMenu extends AbstractContainerMenu {
    private static final int QUOTE_TICKS=600;
    private record Pending(UUID nonce,long issued,long revision,ItemKey item,RepairPrice price,ItemStack preview){}
    public final BlockPos pos;private final CopperMachineBlockEntity machine;private final Player viewer;
    private Pending pending;private long lastReply=Long.MIN_VALUE,lastSelect=Long.MIN_VALUE;
    private int requested=4096;private Result result=Result.NONE;
    public RepairNetwork.Reply snapshot;
    public RepairMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,null,b.readBlockPos(),new ItemStackHandler(4));}
    public RepairMenu(int id,Inventory inv,CopperMachineBlockEntity m){this(id,inv,m,m.getBlockPos(),m.inventory);}
    private RepairMenu(int id,Inventory inv,CopperMachineBlockEntity m,BlockPos pos,IItemHandler items){
        super(ModContent.REPAIR_MENU.get(),id);machine=m;this.pos=pos;viewer=inv.player;
        addSlot(new SlotItemHandler(items,0,26,41){@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,79+col*18,126+row*18));for(int col=0;col<9;col++)addSlot(new Slot(inv,col,79+col*18,184));
    }
    @Override public boolean stillValid(Player p){return machine==null||p==viewer&&p.level()==machine.getLevel()&&!machine.isRemoved()&&machine.kind==MachineKind.REPAIR_CRYSTAL&&p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos)==machine&&machine.canAccess(p)&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&p.level() instanceof ServerLevel level&&ProtectionPolicy.allowed(level,pos,machine.owner());}
    private boolean context(ServerPlayer p,UUID runtime){return machine!=null&&p.containerMenu==this&&stillValid(p)&&machine.runtimeIdentity.equals(runtime);}
    @Override public ItemStack quickMoveStack(Player p,int index){if(index<0||index>=slots.size()||!stillValid(p))return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var before=stack.copy();if(!(index==0?moveItemStackTo(stack,1,slots.size(),true):moveItemStackTo(stack,0,1,false)))return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return before;}
    public void receive(RepairNetwork.Reply r){if(r.menu()==containerId&&r.pos().equals(pos))snapshot=r;}
    public RepairNetwork.Quote quote(){var q=pending;return q==null?null:new RepairNetwork.Quote(q.nonce,q.price,q.preview,(int)Math.max(0,QUOTE_TICKS-(viewer.level().getGameTime()-q.issued)));}
    private boolean unchanged(Pending q){var item=machine.inventory.getStackInSlot(0);return machine.enchantmentRevision==q.revision&&item.getCount()==1&&q.item.matches(item);}
    public boolean select(UUID runtime,int points,ServerPlayer p){
        if(!context(p,runtime)||points<1||points>4096)return false;long now=p.level().getGameTime();if(lastSelect!=Long.MIN_VALUE&&now-lastSelect<2)return false;lastSelect=now;requested=points;pending=null;prepare();reply(p,pending==null?Result.INVALID:Result.QUOTED);return pending!=null;
    }
    private void prepare(){
        pending=null;var item=machine.inventory.getStackInSlot(0).copy();long revision=machine.enchantmentRevision;
        try{var price=RepairPolicy.quote(item,requested);if(price==null)return;var preview=RepairPolicy.apply(item,price);if(revision!=machine.enchantmentRevision||!ItemKey.of(item).matches(machine.inventory.getStackInSlot(0)))return;pending=new Pending(UUID.randomUUID(),viewer.level().getGameTime(),revision,ItemKey.of(item),price,preview);}catch(RuntimeException unsupported){UnusualCopper.LOGGER.debug("Unsupported repair item",unsupported);}
    }
    private boolean fail(ServerPlayer p,Result why){reply(p,why);return false;}
    public boolean confirm(UUID runtime,UUID nonce,ServerPlayer p){
        if(!context(p,runtime))return false;var q=pending;pending=null;
        if(q==null||!q.nonce.equals(nonce)||p.level().getGameTime()-q.issued>=QUOTE_TICKS||!unchanged(q))return fail(p,Result.STALE);
        if(!machine.energyEnabled())return fail(p,Result.PAUSED);if(machine.energy.stored()<q.price.cue())return fail(p,Result.NO_ENERGY);
        final XpWallet.State before,after;try{before=XpWallet.capture(p);after=XpWallet.spend(before,q.price.xp());}catch(IllegalArgumentException notEnough){return fail(p,Result.NO_XP);}
        final ItemStack replacement;
        try{
            var xp=new PlayerXpEvent.XpChange(p,-q.price.xp());if(MinecraftForge.EVENT_BUS.post(xp))return fail(p,Result.XP_CANCELLED);if(xp.getAmount()!=-q.price.xp())return fail(p,Result.XP_CHANGED);
            int levels=after.level()-before.level();if(levels!=0){var change=new PlayerXpEvent.LevelChange(p,levels);if(MinecraftForge.EVENT_BUS.post(change))return fail(p,Result.XP_CANCELLED);if(change.getLevels()!=levels)return fail(p,Result.XP_CHANGED);}
            var actual=RepairPolicy.quote(machine.inventory.getStackInSlot(0).copy(),requested);if(!q.price.equals(actual))return fail(p,Result.STALE);
            replacement=RepairPolicy.apply(machine.inventory.getStackInSlot(0).copy(),actual);if(!ItemStack.matches(replacement,q.preview))return fail(p,Result.STALE);
        }catch(RuntimeException hook){UnusualCopper.LOGGER.error("Repair provider failed before commit",hook);return fail(p,Result.PROVIDER_ERROR);}
        if(!context(p,runtime)||!unchanged(q)||!before.matches(p)||!machine.energyEnabled()||machine.energy.stored()<q.price.cue())return fail(p,Result.STALE);
        machine.commitLocally(()->{after.apply(p);machine.energy.spend(q.price.cue());machine.inventory.setStackInSlot(0,replacement);if(machine.owner()==null)machine.setOwner(p.getUUID());machine.repairUntil=p.level().getGameTime()+40;machine.setChanged();});
        try{p.connection.send(new ClientboundSetExperiencePacket(p.experienceProgress,p.totalExperience,p.experienceLevel));p.level().playSound(null,pos,net.minecraft.sounds.SoundEvents.ANVIL_USE,net.minecraft.sounds.SoundSource.BLOCKS,.5F,1.3F);}catch(RuntimeException notification){UnusualCopper.LOGGER.debug("Skipped post-commit repair notification",notification);}
        reply(p,Result.APPLIED);return true;
    }
    private void reply(ServerPlayer p,Result why){if(why!=Result.NONE)result=why;long xp=0;try{xp=(long)Math.floor(XpWallet.points(p)+.0001);}catch(IllegalArgumentException ignored){}var q=pending;RepairNetwork.reply(p,new RepairNetwork.Reply(containerId,pos,machine.runtimeIdentity,q==null?null:new RepairNetwork.Quote(q.nonce,q.price,q.preview,(int)Math.max(0,QUOTE_TICKS-(p.level().getGameTime()-q.issued))),xp,machine.energy.stored(),result,machine.energyEnabled()));lastReply=p.level().getGameTime();}
    @Override public void broadcastChanges(){super.broadcastChanges();if(machine!=null&&viewer instanceof ServerPlayer p&&p.containerMenu==this&&stillValid(p)&&(lastReply==Long.MIN_VALUE||p.level().getGameTime()-lastReply>=10)){if(pending==null||!unchanged(pending)||p.level().getGameTime()-pending.issued>=QUOTE_TICKS)prepare();reply(p,Result.NONE);}}
    @Override public boolean clickMenuButton(Player p,int button){if(machine==null||!(p instanceof ServerPlayer server)||!context(server,machine.runtimeIdentity))return false;if(button==0){machine.redstoneMode=(machine.redstoneMode+1)%3;machine.setChanged();reply(server,Result.NONE);return true;}if(button==1){EnergyControlMenu.open(server,machine);return true;}return false;}
}
