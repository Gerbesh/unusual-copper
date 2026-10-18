package io.github.gerbesh.unusualcopper.reactor;

import io.github.gerbesh.unusualcopper.energy.*;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.*;
import java.util.*;

/** Only the controller owns resources. Member capabilities resolve a live, validated controller. */
public final class ReactorBlockEntity extends BlockEntity implements MenuProvider {
    public enum Status { INCOMPLETE, UNLOADED, PROTECTED, PAUSED, NO_COOLANT, NO_LENSES, NO_FILTER, NO_INSULATION, NO_COPPER, NO_LAVA, FULL, HOT, WORKING, IDLE }
    public BlockPos anchor,controller;
    public UUID group,owner;
    public final ReactorEngine engine=new ReactorEngine();
    public boolean paused;
    public int copperCredit,disabledFaces,generated,delivered;
    public int visualLenses;
    public Status status=Status.INCOMPLETE;
    public String buildIssue="reactor.build";
    private long checked=Long.MIN_VALUE;
    private boolean valid;
    private Boolean lastActivity;
    private final ReactorPortBudget outputBudget=new ReactorPortBudget();
    private long portEpoch;
    private record CapKey(Capability<?> capability,Direction side){}
    private final Map<CapKey,LazyOptional<?>> ports=new HashMap<>();
    private void invalidatePorts(){portEpoch++;var previous=new ArrayList<>(ports.values());ports.clear();previous.forEach(LazyOptional::invalidate);}
    @Override public void invalidateCaps(){invalidatePorts();super.invalidateCaps();}
    public void togglePort(Direction side){disabledFaces^=1<<side.ordinal();invalidatePorts();sync();}
    public float previousHeat,previousPower;
    public long visualTime;
    public final ItemStackHandler inventory=new ItemStackHandler(9){
        @Override public int getSlotLimit(int slot){return slot==0||slot==7||slot==8?64:1;}
        @Override public boolean isItemValid(int slot,ItemStack stack){
            if(slot==0)return stack.is(Items.COPPER_INGOT)||stack.is(Items.COPPER_BLOCK);
            if(slot>=1&&slot<=4)return stack.is(ModContent.REACTOR_LENS.get())&&stack.getCount()==1;
            if(slot==5)return stack.is(ModContent.REACTOR_FILTER.get())&&stack.getCount()==1;
            if(slot==6)return stack.is(ModContent.REACTOR_INSULATION.get())&&stack.getCount()==1;
            return slot==7&&(stack.is(Items.LAVA_BUCKET)||stack.is(ReactorFluids.BUCKET.get()));
        }
        @Override protected void onContentsChanged(int slot){setChanged();}
    };
    public final FluidTank coolant=tank(ReactorEngine.COOLANT,()->ReactorFluids.STILL.get());
    public final FluidTank lava=tank(16000,()->Fluids.LAVA);
    private FluidTank tank(int size,java.util.function.Supplier<net.minecraft.world.level.material.Fluid> fluid){return new FluidTank(size,s->s.getFluid()==fluid.get()){@Override protected void onContentsChanged(){setChanged();}};}
    public ReactorBlockEntity(BlockPos pos,BlockState state){super(ModContent.REACTOR_ENTITY.get(),pos,state);}
    public static ReactorBlockEntity at(net.minecraft.world.level.Level level,BlockPos pos){return level!=null&&level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof ReactorBlockEntity r&&!r.isRemoved()?r:null;}
    public ReactorBlockEntity core(){
        if(controller==null||anchor==null||group==null)return null;
        var core=at(level,controller);
        return core!=null&&controller.equals(core.controller)&&group.equals(core.group)&&anchor.equals(core.anchor)?core:null;
    }
    public boolean isController(){return worldPosition.equals(controller);}
    public boolean canAccess(Player player){return level instanceof ServerLevel server&&(owner==null||owner.equals(player.getUUID())||player.hasPermissions(2))&&ProtectionPolicy.allowed(server,worldPosition,player.getUUID());}
    public boolean automationAllowed(UUID actor){var c=core();return c!=null&&(c.owner==null||c.owner.equals(actor))&&c.ready();}
    public boolean payload(){if(engine.stored>0||engine.remaining>0||engine.heat>0||copperCredit>0||!coolant.isEmpty()||!lava.isEmpty())return true;for(int i=0;i<9;i++)if(!inventory.getStackInSlot(i).isEmpty())return true;return false;}
    public void sync(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    public void unform(){
        var c=core();if(c==null){anchor=null;controller=null;group=null;formed(false);return;}
        var origin=c.anchor;var identity=c.group;
        var members=new ArrayList<ReactorBlockEntity>();
        for(var pos:ReactorGeometry.positions(origin)){var member=at(level,pos);if(member!=null&&identity.equals(member.group)){members.add(member);member.anchor=null;member.controller=null;member.group=null;member.checked=Long.MIN_VALUE;member.valid=false;member.lastActivity=null;member.status=Status.INCOMPLETE;}}
        for(var member:members){member.invalidatePorts();member.formed(false);}
        EnergyNetworks.dirty(level);
    }
    private void formed(boolean value){if(level==null)return;var state=level.getBlockState(worldPosition);if(!state.is(ModContent.REACTOR_BLOCK.get()))return;if(state.getValue(ReactorBlock.FORMED)!=value||!value&&state.getValue(ReactorBlock.ACTIVE))level.setBlock(worldPosition,state.setValue(ReactorBlock.FORMED,value).setValue(ReactorBlock.ACTIVE,false),3);sync();}
    private void activity(boolean active){
        if(anchor==null||lastActivity!=null&&lastActivity==active)return;
        for(var pos:ReactorGeometry.positions(anchor)){var member=at(level,pos);if(member!=null&&Objects.equals(group,member.group)){var state=level.getBlockState(pos);if(state.is(ModContent.REACTOR_BLOCK.get())&&state.getValue(ReactorBlock.ACTIVE)!=active)level.setBlock(pos,state.setValue(ReactorBlock.ACTIVE,active),2);}}
        lastActivity=valid?active:null;
    }
    public void tryForm(){
        if(!(level instanceof ServerLevel server))return;
        if(core()!=null)return;
        if(controller!=null&&!level.hasChunkAt(controller))return;
        if(controller!=null){anchor=null;controller=null;group=null;formed(false);}
        buildIssue="reactor.build";
        for(int dy=0;dy<4;dy++)for(int dz=0;dz<3;dz++)for(int dx=0;dx<3;dx++){
            var origin=worldPosition.offset(-dx,-dy,-dz);var positions=ReactorGeometry.positions(origin);
            var members=new ArrayList<ReactorBlockEntity>(36);ReactorBlockEntity holder=null;boolean ok=true;
            for(var pos:positions){var m=at(level,pos);if(m==null||m.controller!=null){ok=false;break;}members.add(m);}
            if(!ok)continue;
            var checkedChunks=new HashSet<Long>();
            for(var m:members){
                if(!Objects.equals(owner,m.owner)){buildIssue="reactor.build_owner";ok=false;break;}
                long chunk=net.minecraft.world.level.ChunkPos.asLong(m.worldPosition.getX()>>4,m.worldPosition.getZ()>>4);
                if(checkedChunks.add(chunk)&&!ProtectionPolicy.allowed(server,m.worldPosition,owner)){buildIssue="reactor.build_protected";ok=false;break;}
                if(m.payload()){if(holder!=null){buildIssue="reactor.build_multiple";ok=false;break;}holder=m;}
            }
            if(!ok)continue;
            var c=holder==null?at(level,origin):holder;UUID identity=UUID.randomUUID();
            for(var m:members){m.anchor=origin.immutable();m.controller=c.worldPosition.immutable();m.group=identity;m.lastActivity=null;m.checked=Long.MIN_VALUE;}
            for(var m:members){m.invalidatePorts();m.formed(true);}
            c.status=Status.IDLE;EnergyNetworks.dirty(level);return;
        }
    }
    public boolean ready(){
        if(!(level instanceof ServerLevel server)||!isController()||anchor==null||group==null)return false;
        long now=level.getGameTime();if(checked==now)return valid;checked=now;valid=false;
        var chunks=new HashSet<Long>();
        for(var pos:ReactorGeometry.positions(anchor)){
            if(!level.hasChunkAt(pos)){status=Status.UNLOADED;return false;}
            var m=at(level,pos);if(m==null||!group.equals(m.group)||!worldPosition.equals(m.controller)){unform();return false;}
            long chunk=net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);
            if(chunks.add(chunk)&&!ProtectionPolicy.allowed(server,pos,owner)){status=Status.PROTECTED;return false;}
        }
        valid=true;return true;
    }
    public boolean energyPort(Direction side){return anchor!=null&&(disabledFaces&(1<<side.ordinal()))==0&&ReactorGeometry.outlet(anchor,worldPosition,side)>=0;}
    private boolean external(Direction side){return side!=null&&anchor!=null&&(disabledFaces&(1<<side.ordinal()))==0&&ReactorGeometry.outside(anchor,worldPosition,side);}
    public int extract(Direction side,int amount,boolean simulate){
        var c=core();if(c==null||!energyPort(side)||!c.ready())return 0;
        long tick=level.getGameTime();
        int socket=ReactorGeometry.outlet(anchor,worldPosition,side);
        int request=c.outputBudget.transfer(tick,socket,amount,true);
        int taken=c.engine.extract(request,simulate);
        if(!simulate&&taken>0){c.outputBudget.transfer(tick,socket,taken,false);c.setChanged();}return taken;
    }
    public void refund(Direction side,int amount){var c=core();if(c==null||amount<=0)return;int socket=ReactorGeometry.outlet(anchor,worldPosition,side);int actual=c.outputBudget.refund(level.getGameTime(),socket,Math.min(amount,ReactorEngine.CAPACITY-c.engine.stored));c.engine.stored+=actual;c.setChanged();}
    private static boolean component(ItemStack item,Item expected,int wear){return item.is(expected)&&item.getCount()==1&&item.getDamageValue()>=0&&item.getDamageValue()+wear<=item.getMaxDamage();}
    private void wear(int slot,int amount){var stack=inventory.getStackInSlot(slot);int next=stack.getDamageValue()+amount;if(next>=stack.getMaxDamage())inventory.setStackInSlot(slot,ItemStack.EMPTY);else stack.setDamageValue(next);}
    private boolean beginBatch(){
        for(int slot=1;slot<=4;slot++)if(!component(inventory.getStackInSlot(slot),ModContent.REACTOR_LENS.get(),8)){status=Status.NO_LENSES;return false;}
        if(!component(inventory.getStackInSlot(5),ModContent.REACTOR_FILTER.get(),8)){status=Status.NO_FILTER;return false;}
        if(!component(inventory.getStackInSlot(6),ModContent.REACTOR_INSULATION.get(),1)){status=Status.NO_INSULATION;return false;}
        var fuel=inventory.getStackInSlot(0);int unit=fuel.is(Items.COPPER_BLOCK)?9:fuel.is(Items.COPPER_INGOT)?1:0;
        if(copperCredit+(long)fuel.getCount()*unit<8){status=Status.NO_COPPER;return false;}
        if(lava.getFluidAmount()<250){status=Status.NO_LAVA;return false;}
        // No external capability callbacks inside this commit.
        int used=unit==0?0:Math.max(0,(8-copperCredit+unit-1)/unit);fuel.shrink(used);copperCredit+=used*unit-8;
        lava.drain(250,IFluidHandler.FluidAction.EXECUTE);for(int i=1;i<=5;i++)wear(i,8);
        engine.begin();setChanged();return true;
    }
    private void bucket(){
        var input=inventory.getStackInSlot(7);var out=inventory.getStackInSlot(8);
        FluidTank target=input.is(Items.LAVA_BUCKET)?lava:input.is(ReactorFluids.BUCKET.get())?coolant:null;
        if(target==null||target.getCapacity()-target.getFluidAmount()<1000||!out.isEmpty()&&(!out.is(Items.BUCKET)||out.getCount()>=64))return;
        target.fill(new FluidStack(target==lava?Fluids.LAVA:ReactorFluids.STILL.get(),1000),IFluidHandler.FluidAction.EXECUTE);
        input.shrink(1);inventory.setStackInSlot(8,new ItemStack(Items.BUCKET,out.getCount()+1));setChanged();
    }
    public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,ReactorBlockEntity r){
        if(level.isClientSide)return;
        if(!r.isController()){if(r.engine.heat>0){r.engine.tick(r.coolant.getFluidAmount(),false);r.setChanged();if(level.getGameTime()%10==0)r.sync();}return;}
        r.generated=0;r.delivered=r.outputBudget.delivered(level.getGameTime()-1);
        boolean complete=r.ready();if(complete)r.bucket();
        boolean enabled=complete&&!r.paused;
        if(enabled){
            if(r.coolant.getFluidAmount()<1000){r.status=Status.NO_COOLANT;enabled=false;}
            else if(r.engine.heat>=9000||r.engine.cooling){r.status=Status.HOT;enabled=false;}
            else if(!component(r.inventory.getStackInSlot(6),ModContent.REACTOR_INSULATION.get(),1)){r.status=Status.NO_INSULATION;enabled=false;}
            else if(r.engine.remaining==0){if(!r.engine.canBegin()){r.status=Status.FULL;enabled=false;}else enabled=r.beginBatch();}
        }else if(complete)r.status=Status.PAUSED;
        r.generated=r.engine.tick(r.coolant.getFluidAmount(),enabled);
        r.activity(complete&&r.generated>0);
        if(r.generated>0)r.status=Status.WORKING;else if(enabled)r.status=r.engine.stored==ReactorEngine.CAPACITY?Status.FULL:Status.IDLE;
        if(r.engine.heat>=8000&&level.getGameTime()%20==0&&!r.inventory.getStackInSlot(6).isEmpty())r.wear(6,1);
        r.setChanged();if(level.getGameTime()%10==0)r.sync();
    }
    public CompoundTag memory(){
        var tag=new CompoundTag();tag.put("Items",inventory.serializeNBT());tag.put("Coolant",coolant.writeToNBT(new CompoundTag()));tag.put("Lava",lava.writeToNBT(new CompoundTag()));
        tag.putInt("Energy",engine.stored);tag.putInt("Remaining",engine.remaining);tag.putInt("Heat",engine.heat);tag.putInt("HeatRemainder",engine.heatRemainder);tag.putBoolean("Cooling",engine.cooling);tag.putInt("Mode",engine.mode);tag.putLong("Produced",engine.produced);tag.putInt("CopperCredit",copperCredit);tag.putBoolean("Paused",paused);if(owner!=null)tag.putUUID("Owner",owner);return tag;
    }
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.merge(memory());tag.putInt("Disabled",disabledFaces);if(anchor!=null&&controller!=null&&group!=null){tag.putLong("Anchor",anchor.asLong());tag.putLong("Controller",controller.asLong());tag.putUUID("Group",group);}}
    @Override public void load(CompoundTag tag){super.load(tag);inventory.deserializeNBT(tag.getCompound("Items"));coolant.readFromNBT(tag.getCompound("Coolant"));lava.readFromNBT(tag.getCompound("Lava"));
        if(!coolant.isEmpty()&&coolant.getFluid().getFluid()!=ReactorFluids.STILL.get())coolant.setFluid(FluidStack.EMPTY);
        if(!lava.isEmpty()&&lava.getFluid().getFluid()!=Fluids.LAVA)lava.setFluid(FluidStack.EMPTY);
        engine.restore(tag.getInt("Energy"),tag.getInt("Remaining"),tag.getInt("Heat"),tag.getInt("HeatRemainder"),tag.getInt("Mode"),tag.getLong("Produced"));
        engine.cooling=engine.heat>=9000||tag.getBoolean("Cooling")&&engine.heat>7000;
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;paused=tag.getBoolean("Paused");copperCredit=Math.max(0,Math.min(8,tag.getInt("CopperCredit")));disabledFaces=tag.getInt("Disabled")&63;
        if(tag.hasUUID("Group")&&tag.contains("Anchor")&&tag.contains("Controller")){anchor=BlockPos.of(tag.getLong("Anchor"));controller=BlockPos.of(tag.getLong("Controller"));group=tag.getUUID("Group");}else{anchor=controller=null;group=null;}checked=Long.MIN_VALUE;lastActivity=null;
    }
    @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();if(anchor!=null&&controller!=null&&group!=null){t.putLong("Anchor",anchor.asLong());t.putLong("Controller",controller.asLong());t.putUUID("Group",group);}int lenses=engine.remaining>0?15:0;for(int i=1;i<=4;i++)if(inventory.getStackInSlot(i).is(ModContent.REACTOR_LENS.get()))lenses|=1<<(i-1);t.putInt("Lenses",lenses);t.putInt("Heat",engine.heat);t.putInt("Power",generated);t.putInt("Coolant",coolant.getFluidAmount());t.putInt("Lava",lava.getFluidAmount());t.putInt("Energy",engine.stored);t.putInt("Status",status.ordinal());t.putInt("Disabled",disabledFaces);return t;}
    @Override public void handleUpdateTag(CompoundTag t){previousHeat=visibleHeat(0);previousPower=visiblePower(0);visualTime=level==null?0:level.getGameTime();anchor=t.contains("Anchor")?BlockPos.of(t.getLong("Anchor")):null;controller=t.contains("Controller")?BlockPos.of(t.getLong("Controller")):null;group=t.hasUUID("Group")?t.getUUID("Group"):null;engine.heat=t.getInt("Heat");engine.stored=t.getInt("Energy");generated=t.getInt("Power");visualLenses=t.getInt("Lenses")&15;disabledFaces=t.getInt("Disabled")&63;coolant.setFluid(new FluidStack(ReactorFluids.STILL.get(),Math.max(0,Math.min(16000,t.getInt("Coolant")))));lava.setFluid(new FluidStack(Fluids.LAVA,Math.max(0,Math.min(16000,t.getInt("Lava")))));status=Status.values()[Math.floorMod(t.getInt("Status"),Status.values().length)];}
    private float blend(float partial){return level==null?1:Math.min(1,(level.getGameTime()-visualTime+partial)/10F);}
    public float visibleHeat(float partial){return previousHeat+(engine.heat-previousHeat)*blend(partial);}
    public float visiblePower(float partial){return previousPower+(generated-previousPower)*blend(partial);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onDataPacket(Connection c,ClientboundBlockEntityDataPacket p){if(p.getTag()!=null)handleUpdateTag(p.getTag());}
    @Override public AABB getRenderBoundingBox(){return anchor==null?new AABB(worldPosition):new AABB(anchor,anchor.offset(3,4,3)).inflate(.25);}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide){level.scheduleTick(worldPosition,getBlockState().getBlock(),1);EnergyNetworks.dirty(level);}}
    @Override public void onChunkUnloaded(){EnergyNetworks.dirty(level);super.onChunkUnloaded();}
    public boolean recoverCopper(){
        if(!paused||engine.heat>=1000||copperCredit<=0)return false;
        var held=inventory.getStackInSlot(0);if(!held.isEmpty()&&(!held.is(Items.COPPER_INGOT)||held.hasTag()||held.getCount()+copperCredit>64))return false;
        inventory.setStackInSlot(0,new ItemStack(Items.COPPER_INGOT,held.getCount()+copperCredit));copperCredit=0;setChanged();return true;
    }
    @Override public Component getDisplayName(){return Component.translatable("reactor.title");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory player,Player p){return new ReactorMenu(id,player,this);}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){
        if(side!=null&&(cap==CopperCapabilities.ENERGY||cap==ForgeCapabilities.ITEM_HANDLER||cap==ForgeCapabilities.FLUID_HANDLER)&&external(side)){
            var key=new CapKey(cap,side);var cached=ports.get(key);if(cached!=null)return cached.cast();
            var created=createPort(cap,side);if(created.isPresent())ports.put(key,created);return created;
        }
        return super.getCapability(cap,side);
    }
    private <T> LazyOptional<T> createPort(Capability<T> cap,Direction side){
        final long epoch=portEpoch;final UUID identity=group;
        java.util.function.BooleanSupplier bound=()->epoch==portEpoch&&Objects.equals(identity,group)&&!isRemoved()&&external(side);
        if(!isRemoved()&&external(side)){
            if(cap==CopperCapabilities.ENERGY&&energyPort(side))return LazyOptional.of(()->new CopperEnergyHandler(){public int stored(){var c=core();return bound.getAsBoolean()&&c!=null&&c.ready()?c.engine.stored:0;}public int capacity(){return bound.getAsBoolean()?ReactorEngine.CAPACITY:0;}public int receive(int a,boolean s){return 0;}public int extract(int a,boolean s){return bound.getAsBoolean()?ReactorBlockEntity.this.extract(side,a,s):0;}}).cast();
            if(!energyPort(side)&&cap==ForgeCapabilities.ITEM_HANDLER)return LazyOptional.of(()->new IItemHandler(){
                private ReactorBlockEntity live(){var c=core();return bound.getAsBoolean()&&c!=null&&c.ready()?c:null;}
                public int getSlots(){return 9;}public ItemStack getStackInSlot(int slot){var c=live();return c==null||slot<0||slot>=9?ItemStack.EMPTY:c.inventory.getStackInSlot(slot).copy();}
                public int getSlotLimit(int slot){return slot==0||slot>=7?64:1;}public boolean isItemValid(int slot,ItemStack stack){var c=live();return c!=null&&slot>=0&&slot<8&&c.inventory.isItemValid(slot,stack);}
                public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){var c=live();return c!=null&&isItemValid(slot,stack)?c.inventory.insertItem(slot,stack,simulate):stack;}
                public ItemStack extractItem(int slot,int count,boolean simulate){var c=live();return c!=null&&slot>=0&&slot<9&&(slot==8||c.paused&&c.engine.heat<1000)?c.inventory.extractItem(slot,count,simulate):ItemStack.EMPTY;}
            }).cast();
            if(!energyPort(side)&&cap==ForgeCapabilities.FLUID_HANDLER)return LazyOptional.of(()->new IFluidHandler(){
                private ReactorBlockEntity live(){var c=core();return bound.getAsBoolean()&&c!=null&&c.ready()?c:null;}
                public int getTanks(){return 2;}public FluidStack getFluidInTank(int tank){var c=live();return c==null?FluidStack.EMPTY:(tank==0?c.coolant:c.lava).getFluid().copy();}
                public int getTankCapacity(int t){return 16000;}public boolean isFluidValid(int t,FluidStack f){return t==0?f.getFluid()==ReactorFluids.STILL.get():t==1&&f.getFluid()==Fluids.LAVA;}
                public int fill(FluidStack f,FluidAction a){var c=live();if(c==null)return 0;return f.getFluid()==ReactorFluids.STILL.get()?c.coolant.fill(f,a):f.getFluid()==Fluids.LAVA?c.lava.fill(f,a):0;}
                public FluidStack drain(FluidStack f,FluidAction a){var c=live();return c==null||!c.paused||c.engine.heat>=1000?FluidStack.EMPTY:f.getFluid()==ReactorFluids.STILL.get()?c.coolant.drain(f,a):c.lava.drain(f,a);}
                public FluidStack drain(int n,FluidAction a){var c=live();return c==null||!c.paused||c.engine.heat>=1000?FluidStack.EMPTY:!c.lava.isEmpty()?c.lava.drain(n,a):c.coolant.drain(n,a);}
            }).cast();
        }
        return super.getCapability(cap,side);
    }
}
