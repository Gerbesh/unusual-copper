package io.github.gerbesh.unusualcopper.reactor;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.*;
import net.minecraftforge.registries.*;

public final class ReactorFluids {
    private ReactorFluids(){}
    private static final DeferredRegister<FluidType> TYPES=DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,UnusualCopper.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(ForgeRegistries.FLUIDS,UnusualCopper.MOD_ID);
    public static final RegistryObject<FluidType> TYPE=TYPES.register("reactor_coolant",()->new FluidType(FluidType.Properties.create().density(1200).viscosity(1500)){
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
                @Override public ResourceLocation getStillTexture(){return new ResourceLocation("minecraft","block/water_still");}
                @Override public ResourceLocation getFlowingTexture(){return new ResourceLocation("minecraft","block/water_flow");}
                @Override public int getTintColor(){return 0xff54d6e6;}
            });
        }
    });
    public static final RegistryObject<FlowingFluid> STILL=FLUIDS.register("reactor_coolant",()->new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> FLOWING=FLUIDS.register("flowing_reactor_coolant",()->new ForgeFlowingFluid.Flowing(properties()));
    public static final RegistryObject<LiquidBlock> BLOCK=ModContent.BLOCKS.register("reactor_coolant",()->new LiquidBlock(STILL,Block.Properties.of().noCollission().strength(100).noLootTable().liquid()));
    public static final RegistryObject<Item> BUCKET=ModContent.ITEMS.register("reactor_coolant_bucket",()->new BucketItem(STILL,new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    private static ForgeFlowingFluid.Properties properties(){return new ForgeFlowingFluid.Properties(TYPE,STILL,FLOWING).bucket(BUCKET).block(BLOCK).tickRate(10);}
    public static void register(IEventBus bus){TYPES.register(bus);FLUIDS.register(bus);}
}
