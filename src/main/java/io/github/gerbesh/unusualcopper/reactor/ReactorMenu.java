package io.github.gerbesh.unusualcopper.reactor;

import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;
import java.util.UUID;

public final class ReactorMenu extends AbstractContainerMenu {
    public final ReactorBlockEntity reactor;
    public final ContainerData data;
    private final UUID session;
    public ReactorMenu(int id,Inventory inventory,FriendlyByteBuf buffer){this(id,inventory,(ReactorBlockEntity)null);buffer.readBlockPos();}
    public ReactorMenu(int id,Inventory player,ReactorBlockEntity reactor){
        super(ModContent.REACTOR_MENU.get(),id);this.reactor=reactor;session=reactor==null?null:reactor.group;
        data=reactor==null?new SimpleContainerData(16):new ContainerData(){
            public int get(int i){return switch(i){case 0->reactor.engine.stored&65535;case 1->reactor.engine.stored>>>16;case 2->reactor.engine.remaining&65535;case 3->reactor.engine.remaining>>>16;case 4->reactor.engine.heat;case 5->reactor.coolant.getFluidAmount();case 6->reactor.lava.getFluidAmount();case 7->reactor.engine.mode;case 8->reactor.status.ordinal();case 9->reactor.generated;case 10->reactor.delivered;case 11->reactor.paused?1:0;case 12->reactor.copperCredit;case 13->reactor.isController()?1:0;default->0;};}
            public void set(int i,int v){}public int getCount(){return 16;}
        };
        ItemStackHandler handler=reactor==null?new ItemStackHandler(9):reactor.inventory;
        int[] xs={18,62,84,106,128,172,204,244,278};
        for(int i=0;i<9;i++){final int slot=i;addSlot(new SlotItemHandler(handler,i,xs[i],47){
            @Override public boolean mayPlace(ItemStack stack){return slot!=8&&super.mayPlace(stack);}
            @Override public boolean mayPickup(Player p){return slot==0||slot>=7||reactor==null||(reactor.paused||!reactor.isController())&&reactor.engine.heat<1000;}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,79+col*18,150+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,79+col*18,208));addDataSlots(data);
    }
    public int energy(){return data.get(0)&65535|(data.get(1)&65535)<<16;}
    @Override public boolean stillValid(Player p){return reactor==null||!reactor.isRemoved()&&p.level()==reactor.getLevel()&&p.level().getBlockEntity(reactor.getBlockPos())==reactor&&java.util.Objects.equals(session,reactor.group)&&p.distanceToSqr(reactor.getBlockPos().getX()+.5,reactor.getBlockPos().getY()+.5,reactor.getBlockPos().getZ()+.5)<=100&&reactor.canAccess(p);}
    @Override public boolean clickMenuButton(Player p,int button){if(reactor==null||!stillValid(p))return false;if(button==0)reactor.paused=!reactor.paused;else if(button==1)reactor.engine.mode=(reactor.engine.mode+1)%3;else if(button==2){if(!reactor.recoverCopper())return false;}else return false;reactor.sync();return true;}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size()||!stillValid(p))return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
        var stack=slot.getItem();var original=stack.copy();if(index<9){if(!moveItemStackTo(stack,9,slots.size(),true))return ItemStack.EMPTY;}
        else{boolean moved=false;for(int i=0;i<8&&!moved;i++)if(slots.get(i).mayPlace(stack))moved=moveItemStackTo(stack,i,i+1,false);if(!moved)return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return original;
    }
}
