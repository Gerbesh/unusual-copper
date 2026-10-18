package io.github.gerbesh.unusualcopper.machine;

import io.github.gerbesh.unusualcopper.energy.*;
import io.github.gerbesh.unusualcopper.machine.logic.*;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.Items;
import java.util.EnumMap;
import java.util.UUID;

public final class CopperMachineBlockEntity extends BlockEntity implements MenuProvider {
    public final MachineKind kind;
    public final CopperEnergyBuffer energy;
    public final ItemStackHandler inventory;
    /** Optional secondary product, so a purifier cannot silently drop its tailings. */
    public ItemStack secondaryResult = ItemStack.EMPTY;
    public int operationWater;
    public int operationCoolant;
    public final FluidTank coolantOutput=new FluidTank(4000,stack->stack.getFluid()==io.github.gerbesh.unusualcopper.reactor.ReactorFluids.STILL.get()){
        @Override protected void onContentsChanged(){setChanged();}
    };
    /** A third physical input for the crystallizer, or a bucket loader for the etcher. */
    public final ItemStackHandler industrialExtra = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.hasTag() && (kind == MachineKind.RESONANCE_CRYSTALLIZER && stack.is(ModContent.ESSENCE.get())
                    || kind == MachineKind.CIRCUIT_ETCHER && stack.is(Items.WATER_BUCKET));
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public ItemStack recipeCatalyst = ItemStack.EMPTY;
    public ItemStack recipeAdditive = ItemStack.EMPTY;
    public boolean hasIndustrialExtra() { return kind == MachineKind.RESONANCE_CRYSTALLIZER || kind == MachineKind.CIRCUIT_ETCHER; }
    public boolean industrialInputsMatch() {
        if (!hasIndustrialExtra()) return true;
        ItemStack catalyst=inventory.getStackInSlot(1);
        if(recipeCatalyst.isEmpty() || !ItemStack.isSameItemSameTags(catalyst,recipeCatalyst)
                || catalyst.getCount()<recipeCatalyst.getCount())return false;
        if(kind==MachineKind.RESONANCE_CRYSTALLIZER){
            ItemStack third=industrialExtra.getStackInSlot(0);
            return !recipeAdditive.isEmpty() && ItemStack.isSameItemSameTags(third,recipeAdditive)
                    && third.getCount()>=recipeAdditive.getCount();
        }
        return true;
    }
    /** Separate 3x3 grid; old four-slot machines keep their original NBT and slot IDs. */
    public final ItemStackHandler assemblyGrid = new ItemStackHandler(9) {
        @Override public boolean isItemValid(int slot,ItemStack stack) {
            return (kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER) && !stack.hasTag()
                    && !(stack.getItem() instanceof io.github.gerbesh.unusualcopper.item.CopperBatteryItem);
        }
        @Override protected void onContentsChanged(int slot){setChanged();}
    };
    /** An operation's immutable input identities, persisted before any CuE is spent. */
    public final ItemStackHandler assemblySnapshot = new ItemStackHandler(9);
    public boolean assemblyInputsMatch() {
        if(kind!=MachineKind.PRECISION_ASSEMBLER)return true;
        for(int slot=0;slot<9;slot++) {
            ItemStack expected=assemblySnapshot.getStackInSlot(slot);
            ItemStack current=assemblyGrid.getStackInSlot(slot);
            if(expected.isEmpty() || !ItemStack.isSameItemSameTags(expected,current) || current.getCount()<expected.getCount())return false;
        }
        return true;
    }
    /** Machine-local E5A queue. No external storage simulate() is treated as a reservation. */
    public int productionQueued, productionCompleted, productionPattern;
    public boolean productionInputsMatch() {
        if (kind != MachineKind.PRODUCTION_CONTROLLER) return true;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack expected=assemblySnapshot.getStackInSlot(slot);
            ItemStack current=assemblyGrid.getStackInSlot(slot);
            if (expected.isEmpty()) { if (!current.isEmpty()) return false; }
            else if (!ItemStack.isSameItemSameTags(expected,current) || current.getCount()<expected.getCount()) return false;
        }
        return true;
    }
    public void queueProduction(int count) {
        if(kind!=MachineKind.PRODUCTION_CONTROLLER || count<=0)return;
        productionQueued=ProductionOrderPolicy.queue(productionQueued,count);
        setChanged();
    }
    public void cancelProduction() {
        if(kind!=MachineKind.PRODUCTION_CONTROLLER)return;
        productionQueued=0;
        cancelOperation();
    }
    public void nextProductionPattern() {
        if(kind!=MachineKind.PRODUCTION_CONTROLLER || hasOperation() || productionQueued>0 || boundCargoCount()>0 || productionSubGridCount()>0 || productionPressTask!=null)return;
        productionPattern=(productionPattern+1)%2;productionCompleted=0;setChanged();
    }
    /** E5B-5: persistent, inaccessible-to-pipes 3x3 intermediate crafting buffer. */
    public final ItemStackHandler productionSubGrid = new ItemStackHandler(9) {
        @Override public boolean isItemValid(int slot,ItemStack stack) {
            return kind==MachineKind.PRODUCTION_CONTROLLER && !stack.hasTag()
                    && stack.is(slot==4?Items.IRON_NUGGET:Items.COPPER_INGOT);
        }
        @Override protected void onContentsChanged(int slot) {setChanged();}
    };
    public int productionSubGridCount() {
        int count=0;
        for(int i=0;i<9;i++)count+=productionSubGrid.getStackInSlot(i).getCount();
        return count;
    }
    /** E5B-4: one physical, persisted E2 press batch under this controller. */
    public ProductionPressTask productionPressTask;
    /** The press-side lease is not an inventory; it prevents concurrent order ownership. */
    public java.util.UUID productionPressLease;
    public BlockPos productionPressController;
    public boolean pressLeased() { return kind==MachineKind.COPPER_PRESS && productionPressLease!=null; }
    public boolean hasPressLease(ProductionPressTask task,BlockPos controller) {
        return pressLeased() && task!=null && productionPressLease.equals(task.token())
                && controller.equals(productionPressController);
    }
    /** Water chemistry or pumped bulk storage, depending on machine kind. */
    public final FluidTank water;
    private final IFluidHandler fluidStorage;
    public IFluidHandler fluidStorage(){return fluidStorage;}
    public io.github.gerbesh.unusualcopper.fluid.tank.TankView tankView=io.github.gerbesh.unusualcopper.fluid.tank.TankView.EMPTY;
    public boolean tankUnloaded;
    /** Public appearance only; no fluid tags, inventory stacks or capabilities are synchronized. */
    public int visualFluidAmount;
    public net.minecraft.world.level.material.Fluid visualFluid=Fluids.EMPTY;
    public BlockPos pumpSource;
    private final EnumMap<Direction,LazyOptional<IFluidHandler>> fluidCaps=new EnumMap<>(Direction.class);
    public boolean canFitOutputs(ItemStack first,ItemStack second) { return outputPlan(first,second)!=null; }
    public final ItemStackHandler battery = new ItemStackHandler(1) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return kind.isCell() && stack.getItem() instanceof io.github.gerbesh.unusualcopper.item.CopperBatteryItem; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    /** 0: cell -> battery; 1: battery -> cell; 2: paused. */
    public int batteryMode;
    private final MachineLogic logic;
    private final EnumMap<Direction, SideMode> sides = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<IItemHandler>> itemCaps = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<CopperEnergyHandler>> energyCaps = new EnumMap<>(Direction.class);
    private UUID owner;
    private long capabilityEpoch;
    private MachineStatus status = MachineStatus.IDLE;
    private float previousVisualEnergy;
    private long visualEnergyChanged;
    public float visualEnergy(float partial) {
        float blend=level==null?1:Math.min(1,(level.getGameTime()-visualEnergyChanged+partial)/8F);
        return previousVisualEnergy+(energy.stored()-previousVisualEnergy)*blend;
    }
    public int redstoneMode;
    public String recipeId = "";
    public ItemStack source = ItemStack.EMPTY;
    public ItemStack result = ItemStack.EMPTY;
    public int progress;
    public int duration;
    public int paid;
    public int operationCost;
    public final UUID runtimeIdentity = UUID.randomUUID();
    private io.github.gerbesh.unusualcopper.gameplay.WorkArea workArea;
    public int scanCursor, growthMode, inspected, grown, harvested;
    public final io.github.gerbesh.unusualcopper.storage.OwnedItems harvest=new io.github.gerbesh.unusualcopper.storage.OwnedItems();
    public final io.github.gerbesh.unusualcopper.gameplay.CultivationSweep cultivation;
    public long nextWaveTick;
    public boolean allowOwnTamed;
    public final ReaperState reaper;
    public final ArboristState arborist;
    public final SentinelState sentinel;
    public final BrewerState brewer;
    public final MiningState mining;
    public long enchantmentRevision;
    public long enchantmentUntil;
    public long repairUntil;
    private boolean localCommit,deferredChange;
    @Override public void setChanged(){if(localCommit){deferredChange=true;return;}super.setChanged();}
    /** No neighbor callbacks interleave the local item/XP/energy assignments. */
    public void commitLocally(Runnable action){
        if(localCommit)throw new IllegalStateException("Nested machine commit");localCommit=true;
        try{action.run();}finally{localCommit=false;if(deferredChange){deferredChange=false;try{super.setChanged();}catch(RuntimeException hook){io.github.gerbesh.unusualcopper.UnusualCopper.LOGGER.error("Neighbor notification failed after a completed local machine commit",hook);}}}
    }
    private net.minecraft.core.BlockPos boundInventory;
    private net.minecraft.core.BlockPos boundIdentity;
    private Direction boundSide = Direction.UP;
    private io.github.gerbesh.unusualcopper.storage.StorageEndpoint boundEndpoint;
    public io.github.gerbesh.unusualcopper.storage.StorageEndpoint boundEndpoint(ServerLevel level) {
        if (boundInventory == null) return null;
        if (boundEndpoint == null) boundEndpoint = io.github.gerbesh.unusualcopper.storage.StorageEndpoints.at(level, boundInventory, boundSide, owner,boundIdentity);
        var identity=boundEndpoint.identity().pos();if(!java.util.Objects.equals(boundIdentity,identity)){boundIdentity=identity;setChanged();}
        return boundEndpoint;
    }
    private final java.util.List<ItemStack> boundCargo = new java.util.ArrayList<>();
    public int boundCargoSlot = -1;
    public boolean boundFaulted;
    public BlockPos boundInventory() { return boundInventory; }
    public Direction boundSide() { return boundSide; }
    public java.util.List<ItemStack> boundCargo() { return boundCargo.stream().map(ItemStack::copy).toList(); }
    public int boundCargoCount() { return (int)Math.min(Integer.MAX_VALUE, boundCargo.stream().mapToLong(ItemStack::getCount).sum()); }
    public void boundCargo(java.util.List<ItemStack> items) {
        boundCargo.clear(); for (var item : items) if (!item.isEmpty()) boundCargo.add(item.copy());
        if (boundCargo.isEmpty()) { boundFaulted = false; boundCargoSlot = -1; } setChanged();
    }
    public boolean bind(BlockPos pos, Direction side, Player player) {
        if(brewer!=null && brewer.job!=null || productionPressTask!=null || productionSubGridCount()>0 && kind==MachineKind.PRODUCTION_CONTROLLER)return false;
        if (!kind.hasInventory() || !canAccess(player) || !boundCargo.isEmpty() || !(level instanceof ServerLevel server) || pos.equals(worldPosition)
                || worldPosition.distSqr(pos) > 32 * 32 || !server.hasChunkAt(pos) || !io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server, worldPosition, player.getUUID())) return false;
        var endpoint = io.github.gerbesh.unusualcopper.storage.StorageEndpoints.at(server, pos, side, owner == null ? player.getUUID() : owner);
        if (!endpoint.isAvailable()) return false;
        if (owner == null) setOwner(player.getUUID()); boundInventory = pos.immutable(); boundIdentity=endpoint.identity().pos(); boundSide = side; boundEndpoint = null; if(brewer!=null)io.github.gerbesh.unusualcopper.gameplay.BrewerScheduler.register(server,this); setChanged(); return true;
    }
    public void clearBinding() { if(brewer!=null && brewer.job!=null || productionPressTask!=null || kind==MachineKind.PRODUCTION_CONTROLLER && productionSubGridCount()>0)return; boundInventory = null; boundIdentity=null; boundEndpoint = null; if(brewer!=null&&level instanceof ServerLevel server)io.github.gerbesh.unusualcopper.gameplay.BrewerScheduler.register(server,this); setChanged(); }
    public void recoverBinding(Player player) {
        if (!canAccess(player)) return;
        var remainder = new java.util.ArrayList<ItemStack>();
        for (var stored : boundCargo) {
            var offered = stored.copyWithCount(Math.min(stored.getCount(), stored.getMaxStackSize())); int before = offered.getCount(); player.getInventory().add(offered);
            var rest = stored.copyWithCount(stored.getCount() - before + offered.getCount()); if (!rest.isEmpty()) remainder.add(rest);
        }
        boundCargo(remainder);
        // The secondary 3x3 buffer belongs to the machine, not an external stock
        // promise. Give the player a way to recover unfinished copper-frame inputs.
        if(kind==MachineKind.PRODUCTION_CONTROLLER) {
            for(int i=0;i<9;i++) {
                ItemStack held=productionSubGrid.getStackInSlot(i);
                if(held.isEmpty())continue;
                ItemStack offered=held.copy();
                player.getInventory().add(offered);
                int moved=held.getCount()-offered.getCount();
                if(moved>0)productionSubGrid.extractItem(i,moved,false);
            }
        }
    }

    public CopperMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MACHINE_ENTITY.get(), pos, state);
        kind = ((CopperMachineBlock)state.getBlock()).kind;
        int fluidCapacity=kind==MachineKind.COPPER_FLUID_PUMP?16000:4000;
        water=kind==MachineKind.COPPER_FLUID_TANK?new io.github.gerbesh.unusualcopper.fluid.tank.LocalTank(this):new FluidTank(fluidCapacity, stack -> kind==MachineKind.COPPER_FLUID_PUMP
                ? !stack.isEmpty() : stack.getFluid()==Fluids.WATER) {
            @Override protected void onContentsChanged(){setChanged();}
        };
        fluidStorage=kind==MachineKind.COPPER_FLUID_TANK?io.github.gerbesh.unusualcopper.fluid.tank.TankNetworks.handler(this):water;
        cultivation = kind == MachineKind.AURA_CULTIVATOR ? new io.github.gerbesh.unusualcopper.gameplay.CultivationSweep() : null;
        reaper = kind == MachineKind.MOB_REAPER ? new ReaperState() : null;
        arborist = kind == MachineKind.ARBORIST ? new ArboristState() : null;
        sentinel = kind == MachineKind.COPPER_SENTINEL ? new SentinelState() : null;
        brewer = kind == MachineKind.AETHERIC_BREWER ? new BrewerState() : null;
        mining=kind==MachineKind.RESONANT_EXTRACTOR?new MiningState():null;
        energy = new CopperEnergyBuffer(kind.capacity, this::energyTransferRate, this::energyTransferRate, this::setChanged);
        inventory = new ItemStackHandler(4) {
            @Override public int getSlotLimit(int slot){return (mining!=null&&slot==0)||(brewer!=null && slot==1)||((kind==MachineKind.ENCHANTMENT_RESONATOR||kind==MachineKind.REPAIR_CRYSTAL)&&slot==0)?1:super.getSlotLimit(slot);}
            @Override protected void onContentsChanged(int slot) { if(slot==0)enchantmentRevision++;setChanged(); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (slot >= 2 || !kind.acceptsInputs()) return false;
                if(kind==MachineKind.ENCHANTMENT_RESONATOR)return slot==0?io.github.gerbesh.unusualcopper.gameplay.EnchantmentPolicy.target(stack):stack.is(net.minecraft.world.item.Items.LAPIS_LAZULI);
                if(mining!=null)return slot==0&&io.github.gerbesh.unusualcopper.gameplay.MiningRules.pickaxe(stack);
                if(kind==MachineKind.REPAIR_CRYSTAL)return slot==0&&io.github.gerbesh.unusualcopper.gameplay.RepairPolicy.target(stack);
                if(brewer!=null)return slot==0?stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE):io.github.gerbesh.unusualcopper.machine.logic.BrewerLogic.ingredient(CopperMachineBlockEntity.this,stack);
                if(arborist!=null) {
                    if(arborist.job!=null) return false;
                    return slot==1?io.github.gerbesh.unusualcopper.gameplay.TreeSpecies.axe(stack):io.github.gerbesh.unusualcopper.gameplay.TreeSpecies.item(stack)!=null;
                }
                if(kind==MachineKind.ARMOR_WORKBENCH)return slot==0?stack.getItem() instanceof io.github.gerbesh.unusualcopper.armor.ProgrammableArmorItem
                        : stack.getItem() instanceof io.github.gerbesh.unusualcopper.armor.ArmorModuleItem || stack.getItem() instanceof io.github.gerbesh.unusualcopper.item.CopperBatteryItem
                        || stack.getItem() instanceof io.github.gerbesh.unusualcopper.armor.ProgrammableArmorItem;
                if(kind==MachineKind.PRECISION_ASSEMBLER) return slot==0 && io.github.gerbesh.unusualcopper.recipe.AssemblyPatternCard.valid(stack);
                if(kind==MachineKind.PRODUCTION_CONTROLLER) return slot==0 && !stack.hasTag() && stack.is(ModContent.PATTERN_CARD.get());
                if(kind==MachineKind.RESONANCE_CRYSTALLIZER) return !stack.hasTag() && (slot==0 ? stack.is(Items.AMETHYST_SHARD) : stack.is(ModContent.RESONANT_COIL.get()));
                if(kind==MachineKind.CIRCUIT_ETCHER) return !stack.hasTag() && (slot==0 ? stack.is(ModContent.CIRCUIT_BLANK.get()) : stack.is(ModContent.ETCHING_REAGENT.get()));
                if(kind==MachineKind.VORTEX_PURIFIER) return slot==0 ? !stack.hasTag()
                        && (stack.is(ModContent.COPPER_GRIT.get()) || stack.is(ModContent.IRON_GRIT.get()) || stack.is(ModContent.GOLD_GRIT.get()) || stack.is(ModContent.COOLANT_CONCENTRATE.get()))
                        : (stack.is(Items.WATER_BUCKET)||stack.is(Items.BUCKET)) && !stack.hasTag();
                if(kind==MachineKind.COPPER_PRESS) return !pressLeased() && !stack.hasTag() && (slot==0
                        ? stack.is(net.minecraft.world.item.Items.COPPER_INGOT) || stack.is(ModContent.COPPER_PLATE.get())
                        : stack.is(ModContent.PLATE_DIE.get()) || stack.is(ModContent.WIRE_DIE.get()));
                if(kind==MachineKind.RESONANCE_CRUSHER) return slot==0 && !stack.hasTag()
                        && (stack.is(net.minecraft.world.item.Items.RAW_COPPER)
                        || stack.is(net.minecraft.world.item.Items.RAW_IRON)
                        || stack.is(net.minecraft.world.item.Items.RAW_GOLD));
                if (slot == 1) return kind == MachineKind.SOLAR_INFUSER && stack.is(ModContent.SOLAR_LENS.get());
                if (kind == MachineKind.ESSENCE_SPLITTER) return stack.is(ModContent.ESSENCE.get());
                if (kind == MachineKind.RESONANCE_SMELTER) return SmelterLogic.accepts(level,stack);
                return true;
            }
        };
        logic = switch (kind) {
            case SOLAR_INFUSER -> new SolarLogic();
            case ESSENCE_SPLITTER -> new SplitterLogic();
            case RESONANCE_SMELTER -> new SmelterLogic();
            case RESONANCE_CRUSHER, COPPER_PRESS, VORTEX_PURIFIER, RESONANCE_CRYSTALLIZER, CIRCUIT_ETCHER -> new IndustrialProcessingLogic();
            case PRECISION_ASSEMBLER -> new PrecisionAssemblyLogic();
            case PRODUCTION_CONTROLLER -> new ProductionOrderLogic();
            case ARMOR_WORKBENCH -> new ArmorWorkbenchLogic();
            case COPPER_FLUID_PUMP -> new FluidPumpLogic();
            case COPPER_FLUID_TANK -> (m,server)->m.status(MachineStatus.IDLE);
            case COPPER_CELL, COPPER_CELL_II, COPPER_CELL_III, ENERGY_DISTRIBUTOR, ENERGY_MONITOR -> (m, level) -> m.status(MachineStatus.IDLE);
            case AURA_CULTIVATOR -> new CultivatorLogic();
            case MAGNETIC_COLLECTOR -> new CollectorLogic();
            case GROWTH_SHEPHERD -> new ShepherdLogic();
            case MOB_REAPER -> new ReaperLogic();
            case ARBORIST -> new ArboristLogic();
            case COPPER_SENTINEL -> new SentinelLogic();
            case AETHERIC_BREWER -> new BrewerLogic();
            case ENCHANTMENT_RESONATOR -> (m,level)->m.status(level.getGameTime()<m.enchantmentUntil?MachineStatus.WORKING:MachineStatus.IDLE);
            case RESONANT_EXTRACTOR -> new MiningLogic();
            case REPAIR_CRYSTAL -> (m,level)->m.status(level.getGameTime()<m.repairUntil?MachineStatus.WORKING:MachineStatus.IDLE);
        };
        for (Direction side : Direction.values()) {
            SideMode mode = kind==MachineKind.COPPER_FLUID_PUMP ? (side==state.getValue(CopperMachineBlock.FACING).getOpposite() ? SideMode.ENERGY_INPUT : SideMode.FLUID_OUTPUT)
                    : kind==MachineKind.COPPER_FLUID_TANK ? (side==Direction.DOWN ? SideMode.FLUID_OUTPUT:SideMode.FLUID_INPUT)
                    : (kind==MachineKind.VORTEX_PURIFIER || kind==MachineKind.CIRCUIT_ETCHER) && side==Direction.NORTH ? SideMode.FLUID_INPUT
                    : kind.usesArea() && !kind.hasInventory() ? SideMode.ENERGY_INPUT
                    : kind == MachineKind.ENERGY_DISTRIBUTOR ? (side == Direction.WEST ? SideMode.ENERGY_INPUT : SideMode.ENERGY_OUTPUT)
                    : kind.isCell() ? (side == Direction.EAST ? SideMode.ENERGY_OUTPUT : SideMode.ENERGY_INPUT)
                    : side == Direction.UP && kind.acceptsInputs() ? SideMode.ITEM_INPUT : side == Direction.DOWN && kind.hasInventory() ? SideMode.ITEM_OUTPUT
                    : kind.produces() ? SideMode.ENERGY_OUTPUT : kind.consumes() ? SideMode.ENERGY_INPUT : SideMode.DISABLED;
            sides.put(side, mode);
        }
        rebuildCaps();
    }

    private int energyPriority = 1;
    private final int[] branchLimits = {4096,4096,4096,4096,4096,4096};
    private final int[] branchPriorities = {1,1,1,1,1,1};
    public int energyPriority() { return energyPriority; }
    public void energyPriority(int value) { energyPriority = Math.max(0,Math.min(2,value)); setChanged(); }
    public int branchLimit(Direction side) { return branchLimits[side.ordinal()]; }
    public int branchPriority(Direction side) { return branchPriorities[side.ordinal()]; }
    public void branchLimit(Direction side,int limit) { if(kind==MachineKind.ENERGY_DISTRIBUTOR){branchLimits[side.ordinal()]=Math.max(0,Math.min(65536,limit));setChanged();} }
    public void branchPriority(Direction side,int priority) { if(kind==MachineKind.ENERGY_DISTRIBUTOR){branchPriorities[side.ordinal()]=Math.max(0,Math.min(2,priority));setChanged();} }
    public int energyTransferRate() { return io.github.gerbesh.unusualcopper.config.ServerConfig.pipeRate(kind.energyTier()); }
    public int energyFaceLimit(Direction side) { return kind==MachineKind.ENERGY_DISTRIBUTOR?Math.min(energyTransferRate(),branchLimit(side)):energyTransferRate(); }
    public int energyOutputPriority(Direction side) { return kind==MachineKind.ENERGY_DISTRIBUTOR?branchPriority(side):1; }
    public void setOwner(UUID id) { owner = id; boundEndpoint = null; setChanged();io.github.gerbesh.unusualcopper.fluid.tank.TankNetworks.changed(this); }
    public boolean togglePlantation(BlockPos pos,Player player) {
        if(arborist==null || !canAccess(player) || !(level instanceof ServerLevel server)) return false;
        for(var point:java.util.List.copyOf(arborist.points)) if(point.pos().equals(pos)||point.pos().equals(pos.above())) {
            arborist.points.remove(point); if(arborist.job!=null && arborist.job.roots.contains(point.pos())) arborist.cancel(); arborist.scan=null; setChanged(); return true;
        }
        if(arborist.points.size()>=16 || worldPosition.distSqr(pos)>32*32 || !server.hasChunkAt(pos) || server.isOutsideBuildHeight(pos) || !server.getWorldBorder().isWithinBounds(pos)) return false;
        if(!io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server,pos,owner==null?player.getUUID():owner) || !io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server,worldPosition,owner==null?player.getUUID():owner)) return false;
        var species=io.github.gerbesh.unusualcopper.gameplay.TreeSpecies.at(server.getBlockState(pos)); if(species==null) return false;
        if(owner==null) setOwner(player.getUUID()); arborist.points.add(new ArboristState.Point(pos,species)); arborist.scan=null; arborist.retryAt=0; setChanged(); return true;
    }
    public UUID owner() { return owner; }
    public boolean installSentinelFocus(Player player,net.minecraft.world.InteractionHand hand) {
        if(sentinel==null||sentinel.focused||!canAccess(player)||!player.getItemInHand(hand).is(ModContent.SENTINEL_FOCUS.get())||!(level instanceof ServerLevel server)||!io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server,worldPosition,owner==null?player.getUUID():owner)) return false;
        if(owner==null) setOwner(player.getUUID()); sentinel.focused=true; if(!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1); setChanged(); server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); return true;
    }
    private void releaseSentinel() { if(sentinel!=null && level instanceof ServerLevel server) { sentinel.active=false; sentinel.target=null; io.github.gerbesh.unusualcopper.gameplay.SentinelScheduler.forget(server,this); } }
    public boolean allowsAutomation(UUID actor) { return owner == null || owner.equals(actor); }
    public boolean canAccess(Player player) { return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2); }
    public io.github.gerbesh.unusualcopper.gameplay.WorkArea workArea() { return workArea; }
    public boolean setWorkArea(io.github.gerbesh.unusualcopper.gameplay.WorkArea area, Player player) {
        if (!kind.usesArea() || !canAccess(player) || !(level instanceof ServerLevel server) || !io.github.gerbesh.unusualcopper.gameplay.AreaAccess.valid(server, worldPosition, area, owner == null ? player.getUUID() : owner)) return false;
        if (owner == null) setOwner(player.getUUID()); workArea = area; scanCursor = 0; inspected = grown = 0; setChanged(); return true;
    }
    public boolean setMiningArea(io.github.gerbesh.unusualcopper.gameplay.MiningFootprint area,Player player){
        if(mining==null||!canAccess(player)||!(level instanceof ServerLevel server)||!io.github.gerbesh.unusualcopper.machine.logic.MiningLogic.geometry(server,worldPosition,area))return false;
        var actor=owner==null?player.getUUID():owner;for(int x=area.minX()>>4;x<=area.maxX()>>4;x++)for(int z=area.minZ()>>4;z<=area.maxZ()>>4;z++)if(!io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server,new BlockPos(x*16,worldPosition.getY(),z*16),actor))return false;
        if(owner==null)setOwner(player.getUUID());mining.area=area;mining.rescan();setChanged();return true;
    }
    public MachineStatus status() { return status; }
    public void status(MachineStatus value) { if (status != value) { status = value; setChanged(); } }
    public SideMode sideMode(Direction side) { return sides.get(side); }
    public boolean energyEnabled() {
        if (isRemoved()) return false;
        boolean signal = level != null && level.hasNeighborSignal(worldPosition);
        return !((redstoneMode == 1 && !signal) || (redstoneMode == 2 && signal));
    }
    public void cycleSide(Direction side) {
        SideMode next = sides.get(side).next();
        while (!supportsSideMode(next)) next = next.next();
        sides.put(side, next);
        invalidateCaps(); rebuildCaps(); setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            EnergyNetworks.dirty(level);
        }
    }
    public boolean supportsSideMode(SideMode mode) {
        return switch (mode) {
            case DISABLED -> true;
            case ITEM_INPUT -> kind.acceptsInputs();
            case ITEM_OUTPUT -> kind.hasInventory();
            case ENERGY_INPUT -> kind.consumes();
            case ENERGY_OUTPUT -> kind.produces();
            case FLUID_INPUT -> kind==MachineKind.VORTEX_PURIFIER || kind==MachineKind.CIRCUIT_ETCHER || kind==MachineKind.COPPER_FLUID_TANK;
            case FLUID_OUTPUT -> kind==MachineKind.COPPER_FLUID_PUMP || kind==MachineKind.COPPER_FLUID_TANK || kind==MachineKind.VORTEX_PURIFIER;
        };
    }

    public boolean hasOperation() { return brewer!=null && brewer.job!=null || arborist!=null && arborist.job!=null || !recipeId.isEmpty() && duration > 0; }
    public boolean inputMatches() {
        ItemStack current = inventory.getStackInSlot(0);
        return ItemStack.isSameItemSameTags(current, source) && current.getCount() >= source.getCount();
    }
    public void begin(String id, ItemStack input, ItemStack output, int ticks) {
        begin(id, input, output, ticks, 0);
    }
    public void begin(String id, ItemStack input, ItemStack output, int ticks, int cost) {
        recipeId = id; source = input.copy(); result = output.copy(); duration = ticks; progress = 0; paid = 0; operationCost = cost; setChanged();
    }
    public void cancelOperation() {
        secondaryResult = ItemStack.EMPTY; operationWater = 0;operationCoolant=0;
        recipeCatalyst=ItemStack.EMPTY; recipeAdditive=ItemStack.EMPTY;
        if(kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER)for(int i=0;i<9;i++)assemblySnapshot.setStackInSlot(i,ItemStack.EMPTY);
        recipeId = ""; source = ItemStack.EMPTY; result = ItemStack.EMPTY; progress = 0; duration = 0; paid = 0; operationCost = 0; setChanged();
    }
    public boolean canFitOutput(ItemStack stack) { return outputPlan(stack) != null; }
    /** Reserve both finished items and container returns in the machine's two physical outputs. */
    public boolean canFitAssemblyOutputs(ItemStack output,ItemStack returns){return outputPlan(output,returns)!=null;}
    public ItemStack insertOutput(ItemStack stack) {
        var rest = stack.copy();
        for (int slot = 2; slot <= 3 && !rest.isEmpty(); slot++) {
            var stored = inventory.getStackInSlot(slot);
            if (!stored.isEmpty() && !io.github.gerbesh.unusualcopper.storage.ItemKey.of(rest).matches(stored)) continue;
            int count = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), inventory.getSlotLimit(slot)) - stored.getCount()); if (count <= 0) continue;
            inventory.setStackInSlot(slot, rest.copyWithCount(stored.getCount() + count)); rest.shrink(count);
        }
        return rest;
    }
    private ItemStack[] outputPlan(ItemStack stack) { return outputPlan(stack,ItemStack.EMPTY); }
    private ItemStack[] outputPlan(ItemStack first, ItemStack second) {
        ItemStack[] planned={inventory.getStackInSlot(2).copy(),inventory.getStackInSlot(3).copy()};
        if (first.isEmpty()) return null;
        for (ItemStack requested : new ItemStack[]{first,second}) {
            ItemStack rest=requested.copy();
            for(int i=0;i<2&&!rest.isEmpty();i++) {
                ItemStack stored=planned[i];
                if(!stored.isEmpty()&&!ItemStack.isSameItemSameTags(stored,rest))continue;
                int capacity=Math.min(inventory.getSlotLimit(i+2),rest.getMaxStackSize())-stored.getCount();
                int accepted=Math.min(capacity,rest.getCount());
                if(accepted<=0)continue;
                if(stored.isEmpty())planned[i]=rest.copyWithCount(accepted);else stored.grow(accepted);
                rest.shrink(accepted);
            }
            if(!rest.isEmpty())return null;
        }
        return planned;
    }
    public boolean finish() {
        ItemStack[] outputs=operationCoolant>0?outputPlan(ItemStack.EMPTY,ItemStack.EMPTY):outputPlan(result,secondaryResult);
        if(operationCoolant>0&&coolantOutput.getCapacity()-coolantOutput.getFluidAmount()<operationCoolant)return false;
        if(outputs==null||!inputMatches()||!industrialInputsMatch()||!assemblyInputsMatch()||!productionInputsMatch()||(operationWater>0 && water.getFluidAmount()<operationWater))return false;
        commitLocally(() -> {
            if(operationWater>0)water.drain(operationWater,IFluidHandler.FluidAction.EXECUTE);
            if(operationCoolant>0)coolantOutput.fill(new FluidStack(io.github.gerbesh.unusualcopper.reactor.ReactorFluids.STILL.get(),operationCoolant),IFluidHandler.FluidAction.EXECUTE);
            if(kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER) {
                for(int slot=0;slot<9;slot++)assemblyGrid.getStackInSlot(slot).shrink(assemblySnapshot.getStackInSlot(slot).getCount());
            } else inventory.getStackInSlot(0).shrink(source.getCount());
            if(hasIndustrialExtra())inventory.getStackInSlot(1).shrink(recipeCatalyst.getCount());
            if(kind==MachineKind.RESONANCE_CRYSTALLIZER)industrialExtra.getStackInSlot(0).shrink(recipeAdditive.getCount());
            inventory.setStackInSlot(2,outputs[0]);inventory.setStackInSlot(3,outputs[1]);
            if(kind==MachineKind.PRODUCTION_CONTROLLER){productionQueued=Math.max(0,productionQueued-1);productionCompleted=Math.min(Integer.MAX_VALUE,productionCompleted+1);}
            cancelOperation();
        });
        return true;
    }

    /** One server-thread commit into owned slots; no external inventory or pickup callback runs here. */
    public boolean collectOutput(net.minecraft.world.entity.item.ItemEntity entity, int cost) {
        if (kind != MachineKind.MAGNETIC_COLLECTOR || !(level instanceof ServerLevel) || entity.level() != level || !entity.isAlive()) return false;
        var outputs = outputPlan(entity.getItem());
        if (outputs == null || !energy.spend(cost)) return false;
        inventory.setStackInSlot(2, outputs[0]); inventory.setStackInSlot(3, outputs[1]);
        entity.setItem(ItemStack.EMPTY); entity.discard(); setChanged(); return true;
    }

    private void transferBattery() {
        var stack=battery.getStackInSlot(0);
        if(!(stack.getItem() instanceof io.github.gerbesh.unusualcopper.item.CopperBatteryItem) || stack.getCount()!=1 || batteryMode==2)return;
        if(batteryMode==0&&io.github.gerbesh.unusualcopper.item.CopperBatteryItem.infinite(stack))return;
        int held=io.github.gerbesh.unusualcopper.item.CopperBatteryItem.stored(stack);
        int moved=batteryMode==0?energy.extract(io.github.gerbesh.unusualcopper.item.CopperBatteryItem.capacity(stack)-held,false):energy.receive(held,false);
        if(moved==0)return;
        io.github.gerbesh.unusualcopper.item.CopperBatteryItem.stored(stack,held+(batteryMode==0?moved:-moved));
        setChanged();status(MachineStatus.CHARGING);
    }

    public static void tick(ServerLevel level, BlockPos pos, BlockState state, CopperMachineBlockEntity machine) {
        if(machine.kind==MachineKind.COPPER_FLUID_TANK){machine.status(MachineStatus.IDLE);if(state.getValue(CopperMachineBlock.ACTIVE))level.setBlock(pos,state.setValue(CopperMachineBlock.ACTIVE,false),2);return;}
        boolean signal = level.hasNeighborSignal(pos);
        if(machine.brewer!=null)io.github.gerbesh.unusualcopper.gameplay.BrewerScheduler.register(level,machine);
        boolean sentinelWasActive=machine.sentinel!=null && machine.sentinel.active;
        if(machine.sentinel!=null) machine.sentinel.active=false;
        if ((machine.redstoneMode == 1 && !signal) || (machine.redstoneMode == 2 && signal)) { if(machine.reaper!=null) io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.cancel(machine,level); machine.status(MachineStatus.REDSTONE_PAUSED); }
        else machine.logic.tick(machine, level);
        if(machine.mining!=null&&machine.status==MachineStatus.REDSTONE_PAUSED)for(var job:machine.mining.jobs)job.advancedAt=Long.MIN_VALUE;
        if(machine.kind.isCell() && machine.energyEnabled())machine.transferBattery();
        if (machine.energyEnabled() && level.getGameTime() % (machine.cultivation!=null||machine.mining!=null?4:20) == 0)
            io.github.gerbesh.unusualcopper.machine.logic.BoundStorageLogic.tick(machine, level);
        if(level.getGameTime()%20==0) {
            if(machine.kind==MachineKind.PRODUCTION_CONTROLLER)
                io.github.gerbesh.unusualcopper.machine.logic.ProductionUpstreamLogic.tick(machine,level);
            else if(machine.pressLeased())
                io.github.gerbesh.unusualcopper.machine.logic.ProductionUpstreamLogic.recoverOrphan(machine,level);
        }
        boolean active = machine.status == MachineStatus.WORKING || machine.status == MachineStatus.CHARGING;
        if (state.getValue(CopperMachineBlock.ACTIVE) != active) level.setBlock(pos, state.setValue(CopperMachineBlock.ACTIVE, active), 2);
        if (level.getGameTime() % (machine.mining!=null?5:10) == 0 || machine.sentinel!=null && sentinelWasActive!=machine.sentinel.active) level.sendBlockUpdated(pos, machine.getBlockState(), machine.getBlockState(), 2);
    }

    private void rebuildCaps() {
        for (Direction side : Direction.values()) {
            fluidCaps.put(side,LazyOptional.of(() -> new IFluidHandler() {
                private final long epoch=capabilityEpoch;
                private boolean live(){return epoch==capabilityEpoch && !isRemoved() && sideMode(side)!=SideMode.DISABLED;}
                private IFluidHandler storage(){return kind==MachineKind.VORTEX_PURIFIER&&sideMode(side)==SideMode.FLUID_OUTPUT?coolantOutput:fluidStorage;}
                @Override public int getTanks(){return 1;}
                @Override public FluidStack getFluidInTank(int tank){return live()&&tank==0?storage().getFluidInTank(0).copy():FluidStack.EMPTY;}
                @Override public int getTankCapacity(int tank){return live()&&tank==0?storage().getTankCapacity(0):0;}
                @Override public boolean isFluidValid(int tank,FluidStack stack){
                    boolean legacyChemistry=(kind==MachineKind.VORTEX_PURIFIER || kind==MachineKind.CIRCUIT_ETCHER)
                            && sideMode(side)!=SideMode.DISABLED;
                    return live()&&tank==0&&sideMode(side)!=SideMode.FLUID_OUTPUT&&(sideMode(side)==SideMode.FLUID_INPUT||legacyChemistry)
                            && fluidStorage.isFluidValid(0,stack);
                }
                @Override public int fill(FluidStack stack,FluidAction action){return isFluidValid(0,stack)?fluidStorage.fill(stack,action):0;}
                @Override public FluidStack drain(FluidStack stack,FluidAction action){return live()&&sideMode(side)==SideMode.FLUID_OUTPUT?storage().drain(stack,action):FluidStack.EMPTY;}
                @Override public FluidStack drain(int amount,FluidAction action){return live()&&sideMode(side)==SideMode.FLUID_OUTPUT?storage().drain(amount,action):FluidStack.EMPTY;}
            }));
            itemCaps.put(side, LazyOptional.of(() -> new IItemHandler() {
                private final long epoch = capabilityEpoch;
                private boolean live() { return epoch == capabilityEpoch && !isRemoved(); }
                @Override public int getSlots() { return (kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER)?13:hasIndustrialExtra()?5:4; }
                @Override public ItemStack getStackInSlot(int slot) { return live() ? ((kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER) && slot>=4 && slot<13?assemblyGrid.getStackInSlot(slot-4):slot==4 && hasIndustrialExtra()?industrialExtra.getStackInSlot(0):slot>=0&&slot<4?inventory.getStackInSlot(slot):ItemStack.EMPTY).copy() : ItemStack.EMPTY; }
                @Override public int getSlotLimit(int slot) { return (kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER) && slot>=4 && slot<13?assemblyGrid.getSlotLimit(slot-4):slot==4 && hasIndustrialExtra()?industrialExtra.getSlotLimit(0):slot>=0&&slot<4?inventory.getSlotLimit(slot):0; }
                @Override public boolean isItemValid(int slot, ItemStack stack) { return live() && !pressLeased() && !(kind==MachineKind.ENCHANTMENT_RESONATOR&&slot==0) && sideMode(side) == SideMode.ITEM_INPUT && ((kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER) && slot>=4 && slot<13?assemblyGrid.isItemValid(slot-4,stack):slot==4 && hasIndustrialExtra()?industrialExtra.isItemValid(0,stack):slot>=0&&slot<4&&inventory.isItemValid(slot,stack)); }
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    return isItemValid(slot,stack) ? (kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER) && slot>=4 && slot<13
                            ? assemblyGrid.insertItem(slot-4,stack,simulate) : slot==4 && hasIndustrialExtra()
                            ? industrialExtra.insertItem(0,stack,simulate) : inventory.insertItem(slot,stack,simulate) : stack;
                }
                @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
                    return live() && !pressLeased() && sideMode(side) == SideMode.ITEM_OUTPUT && slot>=2 && slot<4 ? inventory.extractItem(slot,count,simulate) : ItemStack.EMPTY;
                }
            }));
            energyCaps.put(side, LazyOptional.of(() -> new CopperEnergyHandler() {
                private final long epoch = capabilityEpoch;
                private boolean live() { return epoch == capabilityEpoch && !isRemoved(); }
                @Override public int stored() { return live() ? energy.stored() : 0; }
                @Override public int capacity() { return energy.capacity(); }
                @Override public int receive(int amount, boolean simulate) {
                    return live() && energyEnabled() && kind.consumes() && sideMode(side) == SideMode.ENERGY_INPUT ? energy.receive(Math.min(amount,energyFaceLimit(side)), simulate) : 0;
                }
                @Override public int extract(int amount, boolean simulate) {
                    return live() && energyEnabled() && kind.produces() && sideMode(side) == SideMode.ENERGY_OUTPUT ? energy.extract(Math.min(amount,energyFaceLimit(side)), simulate) : 0;
                }
            }));
        }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (side != null && !isRemoved()) {
            if(cap==ForgeCapabilities.FLUID_HANDLER && (sideMode(side)==SideMode.FLUID_INPUT || sideMode(side)==SideMode.FLUID_OUTPUT
                    || ((kind==MachineKind.VORTEX_PURIFIER || kind==MachineKind.CIRCUIT_ETCHER) && sideMode(side)!=SideMode.DISABLED)))
                return fluidCaps.get(side).cast();
            if (cap == ForgeCapabilities.ITEM_HANDLER && (sideMode(side) == SideMode.ITEM_INPUT || sideMode(side) == SideMode.ITEM_OUTPUT)) return itemCaps.get(side).cast();
            if (cap == CopperCapabilities.ENERGY && energy.capacity() > 0 && (sideMode(side) == SideMode.ENERGY_INPUT || sideMode(side) == SideMode.ENERGY_OUTPUT)) return energyCaps.get(side).cast();
        }
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() {
        capabilityEpoch++;
        super.invalidateCaps(); fluidCaps.values().forEach(LazyOptional::invalidate); itemCaps.values().forEach(LazyOptional::invalidate); energyCaps.values().forEach(LazyOptional::invalidate);
    }
    @Override public void reviveCaps() { super.reviveCaps(); rebuildCaps(); }
    @Override public void onLoad() { super.onLoad();tankUnloaded=false;io.github.gerbesh.unusualcopper.fluid.tank.TankNetworks.register(this);if(mining!=null&&level!=null&&level.isClientSide)io.github.gerbesh.unusualcopper.network.MiningNetwork.trackField(this); if (!itemCaps.get(Direction.UP).isPresent()) rebuildCaps(); EnergyNetworks.dirty(level); if(sentinel!=null && level instanceof ServerLevel server) io.github.gerbesh.unusualcopper.gameplay.SentinelScheduler.register(server,this); if(brewer!=null && level instanceof ServerLevel server)io.github.gerbesh.unusualcopper.gameplay.BrewerScheduler.register(server,this); }
    @Override public void onChunkUnloaded() { tankUnloaded=true;io.github.gerbesh.unusualcopper.fluid.tank.TankNetworks.remove(this); if(brewer!=null && level instanceof ServerLevel brewerLevel)io.github.gerbesh.unusualcopper.gameplay.BrewerScheduler.forget(brewerLevel,this); releaseSentinel(); if(reaper!=null && level instanceof ServerLevel server) io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.cancel(this,server); if(arborist!=null) {arborist.scan=null;arborist.actor=null;} invalidateCaps(); EnergyNetworks.dirty(level); super.onChunkUnloaded(); }
    @Override public void setRemoved() { tankUnloaded=true; io.github.gerbesh.unusualcopper.fluid.tank.TankNetworks.remove(this);if(brewer!=null && level instanceof ServerLevel brewerLevel)io.github.gerbesh.unusualcopper.gameplay.BrewerScheduler.forget(brewerLevel,this); releaseSentinel(); if(reaper!=null && level instanceof ServerLevel server) io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.cancel(this,server); if(arborist!=null) {arborist.scan=null;arborist.actor=null;} super.setRemoved(); EnergyNetworks.dirty(level); }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("DataVersion", 2); tag.put("Inventory", inventory.serializeNBT()); tag.putInt("Energy", energy.stored());
        if(kind.isCell()) { tag.put("Battery",battery.getStackInSlot(0).save(new CompoundTag()));tag.putInt("BatteryMode",batteryMode); }
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Redstone", redstoneMode); saveSides(tag);
        tag.putInt("EnergyPriority",energyPriority);
        if(kind==MachineKind.ENERGY_DISTRIBUTOR){tag.putIntArray("BranchLimits",branchLimits);tag.putIntArray("BranchPriorities",branchPriorities);}
        tag.putString("Recipe", recipeId); tag.put("Source", source.save(new CompoundTag()));
        // ItemStack's vanilla Count is a signed byte: a solar batch can produce 128.
        tag.put("Result", (result.isEmpty() ? ItemStack.EMPTY : result.copyWithCount(1)).save(new CompoundTag()));
        tag.putInt("ResultCount", result.getCount());
        tag.putInt("Progress", progress); tag.putInt("Duration", duration); tag.putInt("Paid", paid);
        tag.putInt("Cost", operationCost);
        if(kind==MachineKind.VORTEX_PURIFIER){tag.put("CoolantOutput",coolantOutput.writeToNBT(new CompoundTag()));tag.putInt("CoolantCost",operationCoolant);}
        if (kind==MachineKind.VORTEX_PURIFIER || kind==MachineKind.CIRCUIT_ETCHER || kind==MachineKind.COPPER_FLUID_PUMP || kind==MachineKind.COPPER_FLUID_TANK) {
            tag.put("WaterTank",water.writeToNBT(new CompoundTag()));
            tag.put("SecondaryResult",secondaryResult.save(new CompoundTag()));
            tag.putInt("WaterCost",operationWater);
        }
        if(kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER) {
            tag.put("AssemblyGrid",assemblyGrid.serializeNBT());
            tag.put("AssemblySnapshot",assemblySnapshot.serializeNBT());
            if(kind==MachineKind.PRECISION_ASSEMBLER)tag.put("AssemblyReturns",secondaryResult.save(new CompoundTag()));
        }
        if(kind==MachineKind.PRODUCTION_CONTROLLER) {
            tag.put("ProductionSubGrid",productionSubGrid.serializeNBT());
            if(productionPressTask!=null)tag.put("ProductionPressTask",productionPressTask.save());
            tag.putInt("ProductionQueued",productionQueued);
            tag.putInt("ProductionCompleted",productionCompleted);
            tag.putInt("ProductionPattern",productionPattern);
        }
        if(hasIndustrialExtra()) {
            tag.put("IndustrialExtra",industrialExtra.serializeNBT());
            tag.put("RecipeCatalyst",recipeCatalyst.save(new CompoundTag()));
            tag.put("RecipeAdditive",recipeAdditive.save(new CompoundTag()));
        }
        if(kind==MachineKind.COPPER_PRESS && productionPressLease!=null) {
            tag.putUUID("ProductionPressLease",productionPressLease);
            if(productionPressController!=null)tag.putLong("ProductionPressController",productionPressController.asLong());
        }
        if(pumpSource!=null&&kind==MachineKind.COPPER_FLUID_PUMP)tag.putLong("PumpSource",pumpSource.asLong());
        if (workArea != null) tag.put("WorkArea", workArea.save());
        tag.putInt("ScanCursor", scanCursor); tag.putInt("GrowthMode", growthMode);
        if(kind==MachineKind.AURA_CULTIVATOR)tag.put("Harvest",harvest.save());
        tag.putLong("NextWaveTick", nextWaveTick); tag.putBoolean("AllowOwnTamed", allowOwnTamed);
        if (reaper != null) tag.put("Reaper", reaper.save());
        if (arborist != null) tag.put("Arborist", arborist.save());
        if (sentinel != null) tag.put("Sentinel",sentinel.save());
        if (brewer != null) tag.put("Brewer",brewer.save());
        if(mining!=null)tag.put("Mining",mining.save());
        if (boundInventory != null) tag.putLong("BoundInventory", boundInventory.asLong()); if(boundIdentity!=null)tag.putLong("BoundIdentity",boundIdentity.asLong());tag.putInt("BoundSide", boundSide.ordinal()); tag.putInt("BoundCargoSlot", boundCargoSlot); tag.putBoolean("BoundFaulted", boundFaulted);
        var cargo = new net.minecraft.nbt.ListTag();
        for (var stack : boundCargo) { var entry = new CompoundTag(); entry.put("Stack", stack.copyWithCount(1).save(new CompoundTag())); entry.putInt("Count", stack.getCount()); cargo.add(entry); } tag.put("BoundCargo", cargo);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        enchantmentRevision++;
        inventory.deserializeNBT(tag.getCompound("Inventory")); energy.restore(tag.getInt("Energy"));
        var portable=ItemStack.of(tag.getCompound("Battery"));
        battery.setStackInSlot(0,battery.isItemValid(0,portable)?portable.copyWithCount(1):ItemStack.EMPTY);
        batteryMode=Math.floorMod(tag.getInt("BatteryMode"),3);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        redstoneMode = Math.floorMod(tag.getInt("Redstone"), 3); loadSides(tag);
        energyPriority=tag.contains("EnergyPriority")?Math.max(0,Math.min(2,tag.getInt("EnergyPriority"))):1;
        var limits=tag.getIntArray("BranchLimits");var priorities=tag.getIntArray("BranchPriorities");
        for(int i=0;i<6;i++){branchLimits[i]=i<limits.length?Math.max(0,Math.min(65536,limits[i])):4096;branchPriorities[i]=i<priorities.length?Math.max(0,Math.min(2,priorities[i])):1;}
        recipeId = tag.getString("Recipe"); source = ItemStack.of(tag.getCompound("Source")); result = ItemStack.of(tag.getCompound("Result"));
        if (!result.isEmpty()) result.setCount(Math.max(1, Math.min(128, tag.getInt("ResultCount"))));
        duration = Math.max(0, tag.getInt("Duration")); progress = Math.max(0, Math.min(duration, tag.getInt("Progress"))); paid = Math.max(0, tag.getInt("Paid"));
        operationCost = tag.contains("Cost") ? Math.max(0, tag.getInt("Cost")) : kind == MachineKind.RESONANCE_SMELTER ? 240 : 0;
        operationCoolant=kind==MachineKind.VORTEX_PURIFIER&&tag.getInt("CoolantCost")==1000?1000:0;
        if(kind==MachineKind.VORTEX_PURIFIER){coolantOutput.readFromNBT(tag.getCompound("CoolantOutput"));if(!coolantOutput.isEmpty()&&coolantOutput.getFluid().getFluid()!=io.github.gerbesh.unusualcopper.reactor.ReactorFluids.STILL.get())coolantOutput.setFluid(FluidStack.EMPTY);}
        if(kind==MachineKind.VORTEX_PURIFIER || kind==MachineKind.CIRCUIT_ETCHER || kind==MachineKind.COPPER_FLUID_PUMP || kind==MachineKind.COPPER_FLUID_TANK) {
            water.readFromNBT(tag.getCompound("WaterTank"));
            secondaryResult=ItemStack.of(tag.getCompound("SecondaryResult"));
            operationWater=Math.max(0,Math.min(4000,tag.getInt("WaterCost")));
        } else { secondaryResult=ItemStack.EMPTY; operationWater=0; }
        if(kind==MachineKind.PRECISION_ASSEMBLER || kind==MachineKind.PRODUCTION_CONTROLLER){
            assemblyGrid.deserializeNBT(tag.getCompound("AssemblyGrid"));
            assemblySnapshot.deserializeNBT(tag.getCompound("AssemblySnapshot"));
            if(kind==MachineKind.PRECISION_ASSEMBLER)secondaryResult=ItemStack.of(tag.getCompound("AssemblyReturns"));
        }
        if(kind==MachineKind.PRODUCTION_CONTROLLER) {
            if(tag.contains("ProductionSubGrid",net.minecraft.nbt.Tag.TAG_COMPOUND)
                    && tag.getCompound("ProductionSubGrid").getInt("Size")==9)
                productionSubGrid.deserializeNBT(tag.getCompound("ProductionSubGrid"));
            productionPressTask=ProductionPressTask.load(tag.getCompound("ProductionPressTask"));
            productionQueued=Math.max(0,Math.min(4096,tag.getInt("ProductionQueued")));
            productionCompleted=Math.max(0,tag.getInt("ProductionCompleted"));
            productionPattern=Math.floorMod(tag.getInt("ProductionPattern"),2);
        }
        if(hasIndustrialExtra()) {
            industrialExtra.deserializeNBT(tag.getCompound("IndustrialExtra"));
            recipeCatalyst=ItemStack.of(tag.getCompound("RecipeCatalyst"));
            recipeAdditive=ItemStack.of(tag.getCompound("RecipeAdditive"));
        } else { recipeCatalyst=ItemStack.EMPTY; recipeAdditive=ItemStack.EMPTY; }
        if (source.isEmpty() || result.isEmpty() || duration == 0) cancelOperation();
        if(kind==MachineKind.COPPER_PRESS) {
            productionPressLease=tag.hasUUID("ProductionPressLease") && tag.contains("ProductionPressController")
                    ?tag.getUUID("ProductionPressLease"):null;
            productionPressController=productionPressLease==null?null:BlockPos.of(tag.getLong("ProductionPressController"));
        }
        pumpSource=kind==MachineKind.COPPER_FLUID_PUMP&&tag.contains("PumpSource")?BlockPos.of(tag.getLong("PumpSource")):null;
        workArea = null;
        if(cultivation!=null)cultivation.reset();
        if (tag.contains("WorkArea")) try { workArea = io.github.gerbesh.unusualcopper.gameplay.WorkArea.load(tag.getCompound("WorkArea")); } catch (IllegalArgumentException ignored) {}
        scanCursor = workArea == null ? 0 : Math.floorMod(tag.getInt("ScanCursor"), workArea.volume()); growthMode = Math.floorMod(tag.getInt("GrowthMode"), 3);
        if(kind==MachineKind.AURA_CULTIVATOR)harvest.load(tag.getList("Harvest",net.minecraft.nbt.Tag.TAG_COMPOUND));
        nextWaveTick = Math.max(0, tag.getLong("NextWaveTick")); allowOwnTamed = tag.getBoolean("AllowOwnTamed");
        if (reaper != null) reaper.load(tag.getCompound("Reaper"));
        if (arborist != null) arborist.load(tag.getCompound("Arborist"));
        if(mining!=null)mining.load(tag.getCompound("Mining"));
        if (sentinel != null) sentinel.load(tag.getCompound("Sentinel"));
        if (brewer != null) {brewer.load(tag.getCompound("Brewer"));if(brewer.job!=null){progress=brewer.job.stage;duration=brewer.job.plan.stages();operationCost=brewer.job.price();}}
        boundInventory = tag.contains("BoundInventory") ? BlockPos.of(tag.getLong("BoundInventory")) : null; boundIdentity=tag.contains("BoundIdentity")?BlockPos.of(tag.getLong("BoundIdentity")):null;boundSide = Direction.values()[Math.floorMod(tag.getInt("BoundSide"), 6)]; boundEndpoint = null;
        int savedCargoSlot=tag.getInt("BoundCargoSlot");
        boundCargoSlot=kind==MachineKind.PRODUCTION_CONTROLLER && (ProductionSupplyPolicy.gridCargoSlot(savedCargoSlot) || savedCargoSlot>=13 && savedCargoSlot<=22)
                ?savedCargoSlot:Math.max(-1,Math.min(1,savedCargoSlot)); boundFaulted = tag.getBoolean("BoundFaulted"); boundCargo.clear();
        for (var raw : tag.getList("BoundCargo", net.minecraft.nbt.Tag.TAG_COMPOUND)) { var entry = (CompoundTag)raw; var stack = ItemStack.of(entry.getCompound("Stack")); stack.setCount(Math.max(0, entry.getInt("Count"))); if (!stack.isEmpty()) boundCargo.add(stack); }
    }
    private void saveSides(CompoundTag tag) { for (Direction side : Direction.values()) tag.putInt("Side" + side.ordinal(), sides.get(side).ordinal()); }
    private void loadSides(CompoundTag tag) {
        for (Direction side : Direction.values()) if (tag.contains("Side" + side.ordinal())) {
            var mode = SideMode.values()[Math.floorMod(tag.getInt("Side" + side.ordinal()), SideMode.values().length)]; sides.put(side, supportsSideMode(mode) ? mode : SideMode.DISABLED);
        }
    }
    @Override public CompoundTag getUpdateTag() {
        // The visible tank contents are deliberately narrower than WaterTank's persisted NBT.
        CompoundTag tag = new CompoundTag(); tag.putInt("Energy", energy.stored()); tag.putInt("Status", status.ordinal());
        if(kind==MachineKind.VORTEX_PURIFIER||kind==MachineKind.CIRCUIT_ETCHER||kind==MachineKind.COPPER_FLUID_PUMP||kind==MachineKind.COPPER_FLUID_TANK){
            tag.putInt("VisualFluidAmount",water.getFluidAmount());
            var fluid=net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(water.getFluid().getFluid());
            if(fluid!=null)tag.putString("VisualFluid",fluid.toString());
        }
        if(kind==MachineKind.COPPER_FLUID_TANK){var view=io.github.gerbesh.unusualcopper.fluid.tank.TankNetworks.view(this);view.write(tag);tag.putString("VisualFluid",view.fluid());tag.putInt("VisualFluidAmount",(int)(view.fill()*16000));}
        if(kind==MachineKind.COPPER_FLUID_PUMP&&pumpSource!=null)tag.putLong("PumpSource",pumpSource.asLong()); if(mining!=null){mining.writeVisual(tag,worldPosition);tag.putUUID("MiningRuntime",runtimeIdentity);} if(sentinel!=null) { tag.putBoolean("SentinelActive",sentinel.active); tag.putBoolean("SentinelFocused",sentinel.focused); } saveSides(tag); return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) {
        if(kind==MachineKind.COPPER_FLUID_TANK)tankView=io.github.gerbesh.unusualcopper.fluid.tank.TankView.read(tag);
        visualFluidAmount=Math.max(0,Math.min(water.getCapacity(),tag.getInt("VisualFluidAmount")));
        var fluidId=net.minecraft.resources.ResourceLocation.tryParse(tag.getString("VisualFluid"));
        visualFluid=fluidId==null?Fluids.EMPTY:net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(fluidId);
        if(visualFluid==null)visualFluid=Fluids.EMPTY;
        if(kind==MachineKind.COPPER_FLUID_TANK&&level!=null&&level.isClientSide)io.github.gerbesh.unusualcopper.fluid.tank.TankNetworks.acceptVisual(this);
        previousVisualEnergy=visualEnergy(0);visualEnergyChanged=level==null?0:level.getGameTime();
        energy.restore(tag.getInt("Energy")); if(kind==MachineKind.COPPER_FLUID_PUMP)pumpSource=tag.contains("PumpSource")?BlockPos.of(tag.getLong("PumpSource")):null; status = MachineStatus.values()[Math.floorMod(tag.getInt("Status"), MachineStatus.values().length)]; loadSides(tag);
        if(mining!=null){mining.visualIdentity=tag.hasUUID("MiningRuntime")?tag.getUUID("MiningRuntime"):null;var a=tag.getIntArray("MiningArea");mining.area=null;if(a.length==4)try{mining.area=new io.github.gerbesh.unusualcopper.gameplay.MiningFootprint(a[0],a[1],a[2],a[3]);}catch(IllegalArgumentException ignored){}mining.enabled=tag.getBoolean("MiningEnabled");mining.visualTick=level==null?0:level.getGameTime();mining.visualRevision++;mining.jobs.clear();for(var raw:tag.getList("MiningBeams",net.minecraft.nbt.Tag.TAG_COMPOUND)){if(mining.jobs.size()>=5)break;var row=(CompoundTag)raw;var job=new MiningState.Job(BlockPos.of(row.getLong("Pos")),net.minecraft.world.level.block.Block.stateById(row.getInt("StateId")),ItemStack.EMPTY,0,0);job.visualId=row.getLong("Flight");job.progress=Math.max(0,Math.min(job.ticks,row.getInt("Progress")));mining.jobs.add(job);}if(level!=null&&level.isClientSide)io.github.gerbesh.unusualcopper.network.MiningNetwork.trackField(this);}
        if(sentinel!=null) { sentinel.active=tag.getBoolean("SentinelActive"); sentinel.focused=tag.getBoolean("SentinelFocused"); }
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { if (packet.getTag() != null) handleUpdateTag(packet.getTag()); }
    @Override public Component getDisplayName() { return Component.translatable("block.unusualcopper." + kind.id); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) { return mining!=null?new io.github.gerbesh.unusualcopper.menu.MiningMenu(id,playerInventory,this):kind==MachineKind.REPAIR_CRYSTAL?new io.github.gerbesh.unusualcopper.menu.RepairMenu(id,playerInventory,this):kind.energyControl()?new io.github.gerbesh.unusualcopper.menu.EnergyControlMenu(id,playerInventory,this):kind==MachineKind.ENCHANTMENT_RESONATOR?new io.github.gerbesh.unusualcopper.menu.EnchanterMenu(id,playerInventory,this):new CopperMachineMenu(id, playerInventory, this); }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override public int getCount() { return CopperMachineMenu.DATA_COUNT; }
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> energy.stored() & 0xffff; case 1 -> energy.stored() >>> 16;
                    case 2 -> energy.capacity() & 0xffff; case 3 -> energy.capacity() >>> 16;
                    case 4 -> progress & 0xffff; case 5 -> duration & 0xffff; case 6 -> status.ordinal();
                    case 7 -> redstoneMode; case 8 -> kind.ordinal(); case 9 -> paid;
                    case 10 -> progress >>> 16; case 11 -> duration >>> 16;
                    case 12 -> operationCost & 0xffff; case 13 -> operationCost >>> 16;
                    case 14 -> growthMode; case 15 -> workArea == null ? 0 : workArea.volume(); case 16 -> scanCursor; case 17 -> inspected; case 18 -> grown;
                    case 19 -> boundInventory == null ? 0 : 1;
                    case 20 -> (int)(Math.min(Integer.MAX_VALUE,(long)boundCargoCount()+productionSubGridCount()) & 0xffffL);
                    case 21 -> (int)(Math.min(Integer.MAX_VALUE,(long)boundCargoCount()+productionSubGridCount()) >>> 16);
                    case 22 -> allowOwnTamed ? 1 : 0; case 23 -> level == null ? 0 : (int)Math.max(0, Math.min(32767, nextWaveTick - level.getGameTime()));
                    case 24 -> io.github.gerbesh.unusualcopper.config.ServerConfig.shepherdCost() & 0xffff;
                    case 25 -> io.github.gerbesh.unusualcopper.config.ServerConfig.shepherdCost() >>> 16;
                    case 26 -> io.github.gerbesh.unusualcopper.config.ServerConfig.shepherdAdvance();
                    case 27 -> reaper == null ? 0 : reaper.population & 0xffff;
                    case 28 -> reaper == null ? 0 : reaper.population >>> 16;
                    case 29 -> reaper == null ? 0 : reaper.lootCount() & 0xffff;
                    case 30 -> reaper == null ? 0 : reaper.lootCount() >>> 16;
                    case 31 -> reaper != null && reaper.active ? 1 : 0;
                    case 32 -> io.github.gerbesh.unusualcopper.config.ServerConfig.reaperCost() & 0xffff;
                    case 33 -> io.github.gerbesh.unusualcopper.config.ServerConfig.reaperCost() >>> 16;
                    case 34 -> arborist==null?0:arborist.points.size(); case 35 -> arborist!=null && arborist.job!=null?1:0;
                    case 36 -> arborist==null||arborist.job==null?0:arborist.job.logs.size()-arborist.job.cut;
                    case 37 -> arborist==null||arborist.job==null?0:arborist.job.roots.size()-arborist.job.planted;
                    case 38 -> arborist==null?0:arborist.recovery.count() & 0xffff; case 39 -> arborist==null?0:arborist.recovery.count()>>>16;
                    case 40 -> sentinel==null?0:io.github.gerbesh.unusualcopper.machine.logic.SentinelLogic.radius(CopperMachineBlockEntity.this);
                    case 41 -> io.github.gerbesh.unusualcopper.config.ServerConfig.sentinelCost() & 0xffff; case 42 -> io.github.gerbesh.unusualcopper.config.ServerConfig.sentinelUpkeep();
                    case 43 -> sentinel!=null && sentinel.active?1:0;
                    case 44 -> io.github.gerbesh.unusualcopper.config.ServerConfig.sentinelCost()>>>16;
                    case 45 -> batteryMode; case 46 -> harvested;
                    case 47 -> kind==MachineKind.VORTEX_PURIFIER || kind==MachineKind.CIRCUIT_ETCHER || kind==MachineKind.COPPER_FLUID_PUMP || kind==MachineKind.COPPER_FLUID_TANK ? water.getFluidAmount() : 0;
                    case 48 -> productionQueued; case 49 -> productionCompleted; case 50 -> productionPattern;
                    case 51 -> io.github.gerbesh.unusualcopper.armor.ArmorData.modules(inventory.getStackInSlot(0)).size();
                    case 52 -> inventory.getStackInSlot(0).getItem() instanceof io.github.gerbesh.unusualcopper.armor.ProgrammableArmorItem armorItem ? io.github.gerbesh.unusualcopper.armor.ArmorData.slots(armorItem):0;
                    case 53 -> io.github.gerbesh.unusualcopper.armor.ArmorData.profile(inventory.getStackInSlot(0));
                    case 54 -> kind==MachineKind.VORTEX_PURIFIER?coolantOutput.getFluidAmount():0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
        };
    }
}
