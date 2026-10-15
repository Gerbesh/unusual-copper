package io.github.gerbesh.unusualcopper.ssnprobe;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.*;
import net.minecraftforge.registries.*;

/** Test-only large, sided physical inventory; never included in the main mod. */
public final class ProbeInventory extends BlockEntity {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"unusualcopper_ssn_probe");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"unusualcopper_ssn_probe");
    public static final RegistryObject<Block> BLOCK=BLOCKS.register("sided_storage",()->new BaseEntityBlock(BlockBehaviour.Properties.of().strength(1)){
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ProbeInventory(pos,state);}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    });
    private static final RegistryObject<BlockEntityType<ProbeInventory>> TYPE=TYPES.register("sided_storage",()->BlockEntityType.Builder.of(ProbeInventory::new,BLOCK.get()).build(null));
    public int reads;
    public final ItemStackHandler inventory=new ItemStackHandler(512){
        @Override protected void onContentsChanged(int slot){((BlockEntity)ProbeInventory.this).setChanged();}
        @Override public ItemStack getStackInSlot(int slot){reads++;return super.getStackInSlot(slot);}
    };
    private final LazyOptional<IItemHandler> items=LazyOptional.of(()->inventory);
    public ProbeInventory(BlockPos pos,BlockState state){super(TYPE.get(),pos,state);}
    public static void register(IEventBus bus){BLOCKS.register(bus);TYPES.register(bus);}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){return cap==ForgeCapabilities.ITEM_HANDLER&&side==Direction.NORTH&&!isRemoved()?items.cast():super.getCapability(cap,side);}
    @Override public void invalidateCaps(){super.invalidateCaps();items.invalidate();}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("Inventory",inventory.serializeNBT());}
    @Override public void load(CompoundTag tag){super.load(tag);inventory.deserializeNBT(tag.getCompound("Inventory"));}
}
