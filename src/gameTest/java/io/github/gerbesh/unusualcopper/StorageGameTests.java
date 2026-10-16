package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.itempipe.*;
import io.github.gerbesh.unusualcopper.menu.ItemPipeMenu;
import io.github.gerbesh.unusualcopper.network.PipeNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.*;
import net.minecraftforge.registries.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StorageGameTests {
    private static final ResourceLocation FIXTURE = new ResourceLocation(UnusualCopper.MOD_ID, "fixture_storage");
    private static final RegistryObject<Block> BLOCK = RegistryObject.create(FIXTURE, ForgeRegistries.BLOCKS);
    private static final RegistryObject<BlockEntityType<?>> TYPE = RegistryObject.create(FIXTURE, ForgeRegistries.BLOCK_ENTITY_TYPES);
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, FIXTURE, () -> new BaseEntityBlock(BlockBehaviourProperties()) {
            @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Fixture(pos, state); }
            @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, FIXTURE, () -> BlockEntityType.Builder.of(Fixture::new, BLOCK.get()).build(null));
    }
    private static net.minecraft.world.level.block.state.BlockBehaviour.Properties BlockBehaviourProperties() { return net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(1); }
    public static final class Fixture extends BlockEntity {
        public boolean rejectActual;
        public int reads;
        public final ItemStackHandler inventory = new ItemStackHandler(512) {
            @Override public ItemStack getStackInSlot(int slot){reads++;return super.getStackInSlot(slot);}
            @Override protected void onContentsChanged(int slot) { setChanged(); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return rejectActual && !simulate ? stack : super.insertItem(slot, stack, simulate); }
        };
        private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> inventory);
        Fixture(BlockPos pos, BlockState state) { super(TYPE.get(), pos, state); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) { return cap == ForgeCapabilities.ITEM_HANDLER && !isRemoved() ? items.cast() : super.getCapability(cap, side); }
        @Override public void invalidateCaps() { super.invalidateCaps(); items.invalidate(); }
        @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.put("Inventory", inventory.serializeNBT()); tag.putBoolean("Reject", rejectActual); }
        @Override public void load(CompoundTag tag) { super.load(tag); inventory.deserializeNBT(tag.getCompound("Inventory")); rejectActual = tag.getBoolean("Reject"); }
    }
    private static BarrelBlockEntity barrel(GameTestHelper h, BlockPos pos) { h.setBlock(pos, Blocks.BARREL); return (BarrelBlockEntity)h.getBlockEntity(pos); }
    private static ItemPipeBlockEntity pipe(GameTestHelper h, BlockPos pos) { h.setBlock(pos, ModContent.ITEM_PIPE.get()); return (ItemPipeBlockEntity)h.getBlockEntity(pos); }
    private static ItemStack named(int count, String name) { var stack = new ItemStack(Items.COPPER_INGOT, count); stack.setHoverName(Component.literal(name)); return stack; }
    private static int count(BarrelBlockEntity barrel) { int count = 0; for (int i = 0; i < barrel.getContainerSize(); i++) count += barrel.getItem(i).getCount(); return count; }

    @GameTest(template="empty",timeoutTicks=60)
    public static void ingredientCountIncludesNamedVariantsAndHonoursFilter(GameTestHelper h){
        var p=new BlockPos(1,2,1);h.setBlock(p,BLOCK.get());var fixture=(Fixture)h.getBlockEntity(p);
        fixture.inventory.setStackInSlot(0,new ItemStack(Items.COPPER_INGOT,12));fixture.inventory.setStackInSlot(511,named(7,"Named copper"));
        var endpoint=StorageEndpoints.at(h.getLevel(),h.absolutePos(p),Direction.UP,null);
        h.startSequence().thenExecute(()->h.assertTrue(!endpoint.countItem(Items.COPPER_INGOT,stack->true).complete(),"Partial census is never a full ingredient total"))
          .thenExecuteAfter(1,()->{var total=endpoint.countItem(Items.COPPER_INGOT,stack->true);h.assertTrue(total.complete()&&total.amount()==19,"Plain and named ingredients are counted together");var namedOnly=endpoint.countItem(Items.COPPER_INGOT,ItemStack::hasCustomHoverName);h.assertTrue(namedOnly.complete()&&namedOnly.amount()==7,"Caller filter applies to exact variants");})
          .thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void largeInventoryCensusIsBoundedSharedAndInvalidatedByRealDirtyHook(GameTestHelper h){
        var p=new BlockPos(1,2,1);h.setBlock(p,BLOCK.get());var fixture=(Fixture)h.getBlockEntity(p);var target=named(64,"Census exact");var key=ItemKey.of(target);var other=ItemKey.of(named(1,"Other NBT"));
        fixture.inventory.setStackInSlot(0,target);fixture.inventory.setStackInSlot(511,named(13,"Census exact"));fixture.inventory.setStackInSlot(400,named(9,"Other NBT"));fixture.reads=0;
        var endpoint=StorageEndpoints.at(h.getLevel(),h.absolutePos(p),Direction.UP,null);var before=InventoryChanges.watch(h.getLevel(),fixture);long revision=before.revision();
        h.startSequence().thenExecute(()->{
            var first=endpoint.count(key);h.assertTrue(!first.complete()&&first.amount()==64&&fixture.reads==256,"First census page is bounded to 256 slots");
            for(int i=0;i<20;i++)endpoint.count(i%2==0?key:other);
            h.assertTrue(fixture.reads==256,"Different callers and item keys share one page budget in the same tick");
        }).thenExecuteAfter(1,()->{
            var total=endpoint.count(key);h.assertTrue(total.complete()&&total.amount()==77&&fixture.reads==512,"Second page completes the exact physical count");var alternate=endpoint.count(other);h.assertTrue(alternate.complete()&&alternate.amount()==9&&fixture.reads==512,"Cached count preserves exact item NBT and avoids another scan");
            fixture.inventory.setStackInSlot(511,named(7,"Census exact"));h.assertTrue(before.revision()>revision,"Real BlockEntity dirty notification reached the watched token");
            h.assertTrue(!endpoint.count(key).complete()&&fixture.reads==512,"Mutation immediately invalidates completion without exceeding this tick's budget");
        }).thenExecuteAfter(1,()->h.assertTrue(!endpoint.count(key).complete()&&fixture.reads==768,"Changed census restarts from its first page"))
          .thenExecuteAfter(1,()->{var total=endpoint.count(key);h.assertTrue(total.complete()&&total.amount()==71&&fixture.reads==1024,"Only fresh pages produce the new exact count");})
          .thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void largeCensusNeverSurvivesInventoryReplacement(GameTestHelper h){
        var p=new BlockPos(1,2,1);h.setBlock(p,BLOCK.get());var fixture=(Fixture)h.getBlockEntity(p);fixture.inventory.setStackInSlot(511,new ItemStack(Items.DIAMOND,12));var key=ItemKey.of(new ItemStack(Items.DIAMOND));var endpoint=StorageEndpoints.at(h.getLevel(),h.absolutePos(p),Direction.UP,null);
        h.startSequence().thenExecute(()->h.assertTrue(!endpoint.count(key).complete(),"Initial census needs two pages"))
          .thenExecuteAfter(1,()->{h.assertTrue(endpoint.count(key).complete()&&endpoint.count(key).amount()==12,"Original inventory counted");h.setBlock(p,Blocks.AIR);h.setBlock(p,BLOCK.get());var replacement=(Fixture)h.getBlockEntity(p);replacement.inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND,3));h.assertTrue(!endpoint.count(key).complete(),"Replacement cannot reuse a completed count");})
          .thenExecuteAfter(1,()->{endpoint.count(key);})
          .thenExecuteAfter(1,()->{var result=endpoint.count(key);h.assertTrue(result.complete()&&result.amount()==3,"Replacement count contains no previous physical stock");})
          .thenSucceed();
    }

    @GameTest(template="empty")
    public static void sameTickSnapshotObservesAnotherEndpointMutation(GameTestHelper h){
        var p=new BlockPos(1,2,1);var inventory=barrel(h,p);inventory.setItem(0,new ItemStack(Items.DIAMOND,8));var pos=h.absolutePos(p);var key=ItemKey.of(new ItemStack(Items.DIAMOND));
        var reader=StorageEndpoints.at(h.getLevel(),pos,Direction.UP,null);var writer=StorageEndpoints.at(h.getLevel(),pos,Direction.UP,null);
        h.assertTrue(reader.snapshot(stack->true,32).entries().get(0).count()==8,"Initial snapshot cached eight physical items");
        h.assertTrue(writer.extract(key,2).stream().mapToInt(ItemStack::getCount).sum()==2,"Second endpoint withdraws two actual items");
        var refreshed=reader.snapshot(stack->true,32);h.assertTrue(refreshed.complete()&&refreshed.entries().size()==1&&refreshed.entries().get(0).count()==6,"Dirty notification invalidates the other endpoint's snapshot in the same tick");h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void foreignPipeOwnerCannotDrainMachineOutputs(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 2, 1), ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.SOLAR_INFUSER).get());
        var machine = (io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)h.getBlockEntity(new BlockPos(1, 2, 1));
        machine.setOwner(UUID.randomUUID()); machine.inventory.setStackInSlot(2, new ItemStack(ModContent.ESSENCE.get(), 8));
        var actor = UUID.randomUUID(); var origin = pipe(h, new BlockPos(1, 1, 1)); origin.owner(actor); pipe(h, new BlockPos(2, 1, 1)).owner(actor);
        var target = barrel(h, new BlockPos(3, 1, 1));
        h.runAtTickTime(35, () -> { h.assertTrue(machine.inventory.getStackInSlot(2).getCount() == 8 && origin.cargoCount() == 0 && count(target) == 0, "Different pipe ownership cannot drain a machine even in an unclaimed area"); h.succeed(); });
    }

    @GameTest(template = "empty")
    public static void breakingBufferedPipePreservesRecoveryBundleContents(GameTestHelper h) {
        var pos = new BlockPos(1, 1, 1); var pipe = pipe(h, pos);
        pipe.hold(List.of(named(80, "A"), named(48, "B")), h.absolutePos(pos.west()), Direction.WEST);
        h.setBlock(pos, Blocks.AIR);
        var parcels = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(2))
                .stream().filter(entity -> entity.getItem().is(ModContent.CARGO_BUNDLE.get())).toList();
        h.assertTrue(parcels.size() == 1, "Removing a pipe produces one recovery parcel instead of deleting cargo");
        var parcel = parcels.get(0); var stack = parcel.getItem().copy(); parcel.discard();
        var player = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CargoTest")); player.getInventory().setItem(0, stack); player.getInventory().selected = 0;
        ModContent.CARGO_BUNDLE.get().use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        ModContent.CARGO_BUNDLE.get().use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        int a = 0, b = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var item = player.getInventory().getItem(i); if (!item.is(Items.COPPER_INGOT)) continue;
            if (item.getHoverName().getString().equals("A")) a += item.getCount(); else if (item.getHoverName().getString().equals("B")) b += item.getCount();
        }
        h.assertTrue(a == 80 && b == 48 && player.getInventory().getItem(0).isEmpty(), "Recovery retains 128 items with both exact NBT variants and consumes only the empty parcel"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void endpointKeepsExactNbtAndRevalidatesPhysicalInventory(GameTestHelper h) {
        var source = barrel(h, new BlockPos(1, 1, 1)); source.setItem(0, named(17, "A")); source.setItem(1, named(19, "B"));
        var endpoint = new HandlerStorageEndpoint(h.getLevel(), source.getBlockPos(), Direction.UP, null);
        var original = named(1, "A"); var key = ItemKey.of(original); original.setHoverName(Component.literal("changed"));
        h.assertTrue(key.matches(named(1, "A")) && !key.matches(original), "Item key copies NBT instead of retaining mutable source tags");
        h.assertTrue(endpoint.simulateExtract(key, 8) == 8 && source.getItem(0).getCount() == 17, "Simulation does not remove items");
        source.getItem(0).shrink(3); var actual = endpoint.extract(key, 8);
        h.assertTrue(actual.stream().mapToInt(ItemStack::getCount).sum() == 8 && source.getItem(0).getCount() == 6 && source.getItem(1).getCount() == 19, "Commit uses current stock and keeps another NBT variant separate");
        var target = barrel(h, new BlockPos(3, 1, 1)); for (int i = 0; i < 27; i++) target.setItem(i, new ItemStack(Items.STONE, 64)); target.setItem(0, named(63, "A"));
        var sink = new HandlerStorageEndpoint(h.getLevel(), target.getBlockPos(), Direction.UP, null);
        h.assertTrue(sink.insert(named(8, "A"), true).getCount() == 7 && target.getItem(0).getCount() == 63, "Insertion simulation leaves stock unchanged");
        target.setItem(0, named(64, "A")); h.assertTrue(sink.insert(named(8, "A"), false).getCount() == 8, "Actual refusal returns all unaccepted items");
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        h.assertTrue(!endpoint.isAvailable() && endpoint.extract(key, 8).isEmpty(), "Endpoint does not retain a removed inventory's handler");
        h.setBlock(new BlockPos(4, 1, 3), BLOCK.get()); var large = (Fixture)h.getBlockEntity(new BlockPos(4, 1, 3)); large.inventory.setStackInSlot(400, named(8, "A"));
        var paged = new HandlerStorageEndpoint(h.getLevel(), large.getBlockPos(), Direction.UP, null);
        h.assertTrue(paged.simulateExtract(key, 8) == 0 && paged.simulateExtract(key, 8) == 8, "Bounded extraction scans eventually find a key on the second page");
        h.assertTrue(paged.extract(key, 8).stream().mapToInt(ItemStack::getCount).sum() == 8 && large.inventory.getStackInSlot(400).isEmpty(), "Real extraction retains the simulated slot hint instead of alternating forever between pages"); h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void pipeFilteringPreservesVariantsAndSupportsTags(GameTestHelper h) {
        var source = barrel(h, new BlockPos(0, 1, 1)); source.setItem(0, named(8, "A")); source.setItem(1, named(8, "B")); source.setItem(2, new ItemStack(Items.RAW_IRON, 8));
        var origin = pipe(h, new BlockPos(1, 1, 1)); pipe(h, new BlockPos(2, 1, 1)); var target = barrel(h, new BlockPos(3, 1, 1));
        var rule = origin.rule(Direction.WEST); rule.mode = PipeMode.EXTRACT; rule.filter(0, named(1, "A")); rule.exactNbt = true; origin.changed();
        h.runAtTickTime(25, () -> {
            h.assertTrue(count(target) == 8 && target.getItem(0).getHoverName().getString().equals("A") && source.getItem(1).getCount() == 8, "Exact NBT whitelist accepts A and leaves B");
            rule.exactNbt = false; origin.changed();
        });
        h.runAtTickTime(50, () -> { h.assertTrue(count(target) == 16 && target.getItem(1).getHoverName().getString().equals("B"), "Type filter transports B without changing its name"); rule.blacklist = true; origin.changed(); });
        h.runAtTickTime(75, () -> {
            h.assertTrue(count(target) == 24 && target.getItem(2).is(Items.RAW_IRON), "Blacklist excludes copper and permits iron");
            rule.blacklist = false; rule.filter(0, ItemStack.EMPTY); rule.tag(new ResourceLocation("forge", "raw_materials"), false); source.setItem(0, new ItemStack(Items.RAW_COPPER, 8)); origin.changed();
        });
        h.runAtTickTime(105, () -> { h.assertTrue(count(target) == 32 && count(source) == 0, "Tag route transfers the last real input; all 32 items remain accounted for"); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void rejectedActualInsertionKeepsSerializedCargoAndResumes(GameTestHelper h) {
        var source = barrel(h, new BlockPos(0, 1, 1)); source.setItem(0, named(8, "cargo")); var origin = pipe(h, new BlockPos(1, 1, 1)); pipe(h, new BlockPos(2, 1, 1));
        h.setBlock(new BlockPos(3, 1, 1), BLOCK.get()); var target = (Fixture)h.getBlockEntity(new BlockPos(3, 1, 1)); target.rejectActual = true;
        origin.rule(Direction.WEST).mode = PipeMode.EXTRACT; origin.changed();
        h.runAtTickTime(30, () -> {
            h.assertTrue(count(source) == 0 && origin.cargoCount() == 8 && target.inventory.getStackInSlot(0).isEmpty(), "Refusal after successful simulation retains the extracted batch in the pipe");
            var tag = origin.saveWithFullMetadata(); var restored = (ItemPipeBlockEntity)BlockEntity.loadStatic(origin.getBlockPos(), origin.getBlockState(), tag);
            h.assertTrue(restored != null && restored.cargoCount() == 8 && restored.cargo().get(0).getHoverName().getString().equals("cargo"), "Cargo count and NBT survive serialization");
            h.assertTrue(!origin.getUpdateTag().contains("Cargo") && !origin.getUpdateTag().contains("Rules") && !origin.getUpdateTag().contains("Owner"), "Chunk observers do not receive private cargo/filter data");
            origin.setRemoved(); restored.setLevel(h.getLevel()); h.getLevel().setBlockEntity(restored); restored.onLoad(); target.rejectActual = false;
        });
        h.runAtTickTime(90, () -> { var restored = (ItemPipeBlockEntity)h.getBlockEntity(new BlockPos(1, 1, 1)); h.assertTrue(restored.cargoCount() == 0 && target.inventory.getStackInSlot(0).getCount() == 8 && count(source) == 0, "Resumed transfer issues exactly one batch"); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void priorityReserveAndStockHysteresis(GameTestHelper h) {
        var source = barrel(h, new BlockPos(0, 1, 1)); source.setItem(0, new ItemStack(Items.COPPER_INGOT, 16)); var origin = pipe(h, new BlockPos(1, 1, 1)); var junction = pipe(h, new BlockPos(2, 1, 1));
        var first = barrel(h, new BlockPos(3, 1, 1)); var secondPipe = pipe(h, new BlockPos(2, 1, 2)); var second = barrel(h, new BlockPos(2, 1, 3));
        origin.rule(Direction.WEST).mode = PipeMode.EXTRACT; origin.rule(Direction.WEST).reserve = 4;
        junction.rule(Direction.EAST).settings(20, 4, 8, 0); secondPipe.rule(Direction.SOUTH).settings(0, 0, 4, 0); origin.changed(); junction.changed(); secondPipe.changed();
        h.runAtTickTime(30, () -> {
            h.assertTrue(count(first) == 8 && count(second) == 4 && count(source) == 4, "Priority routes fill their limits and extraction leaves the exact source reserve");
            first.getItem(0).shrink(1); source.setItem(0, new ItemStack(Items.COPPER_INGOT, 24));
        });
        h.runAtTickTime(55, () -> { h.assertTrue(count(first) == 7 && count(source) == 24, "Above the lower threshold replenishment remains stopped"); first.getItem(0).shrink(4); });
        h.runAtTickTime(95, () -> { h.assertTrue(count(first) == 8 && count(second) == 4 && count(source) == 19, "Below the minimum a new replenishment wave stops at the maximum"); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void boundedInsertionReachesTheTailOfLargeHandler(GameTestHelper h) {
        var source = barrel(h, new BlockPos(0, 1, 1)); source.setItem(0, new ItemStack(Items.COPPER_INGOT, 64)); var origin = pipe(h, new BlockPos(1, 1, 1)); pipe(h, new BlockPos(2, 1, 1));
        h.setBlock(new BlockPos(3, 1, 1), BLOCK.get()); var target = (Fixture)h.getBlockEntity(new BlockPos(3, 1, 1));
        for (int i = 0; i < 511; i++) target.inventory.setStackInSlot(i, new ItemStack(Items.STONE, 64));
        origin.rule(Direction.WEST).mode = PipeMode.EXTRACT; origin.changed();
        h.runAtTickTime(105, () -> { h.assertTrue(target.inventory.getStackInSlot(511).getCount() == 64 && target.inventory.getStackInSlot(511).is(Items.COPPER_INGOT) && count(source) == 0, "Bounded cursors eventually reach available slots beyond the first page"); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void eastChestMenuRemembersFaceAndFeedsSolarInput(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos.east(), Blocks.CHEST);
        var chest = (ChestBlockEntity)h.getBlockEntity(pos.east());
        chest.setItem(0, new ItemStack(Items.COPPER_INGOT, 8));
        var origin = pipe(h, pos); pipe(h, pos.west());
        var targetPos = pos.west(2);
        h.setBlock(targetPos, ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.SOLAR_INFUSER).get());
        var target = (io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)h.getBlockEntity(targetPos);
        while(target.sideMode(Direction.EAST) != io.github.gerbesh.unusualcopper.machine.SideMode.ITEM_INPUT) target.cycleSide(Direction.EAST);
        var player = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "EastPipe"));
        player.setPos(origin.getBlockPos().getX()+.5, origin.getBlockPos().getY()+.5, origin.getBlockPos().getZ()+.5);
        var menu = new ItemPipeMenu(78, player.getInventory(), origin);
        h.assertTrue(menu.selected()==Direction.EAST,"Fresh menu must select the attached chest, not world north");
        h.assertTrue(menu.clickMenuButton(player, Direction.EAST.ordinal()) && menu.clickMenuButton(player,10),"Real menu buttons must configure the chest-facing side");
        var reopened = new ItemPipeMenu(79, player.getInventory(), origin);
        h.assertTrue(reopened.selected()==Direction.EAST && reopened.data.get(1)==PipeMode.EXTRACT.ordinal() && origin.rule(Direction.NORTH).mode==PipeMode.AUTO,"Reopening must preserve the selected face and its separate extraction rule");
        h.runAtTickTime(55,()->{
            h.assertTrue(chest.isEmpty() && target.inventory.getStackInSlot(0).is(Items.COPPER_INGOT) && target.inventory.getStackInSlot(0).getCount()==8 && origin.cargoCount()==0,"Empty filters and the chest-facing menu rule must deliver all eight real items to the solar input");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void pipeMenuFocusPersistsWithoutCopyingOtherFaceRules(GameTestHelper h) {
        var origin = pipe(h,new BlockPos(2,2,2));
        origin.rule(Direction.EAST).mode=PipeMode.EXTRACT;
        origin.rule(Direction.NORTH).mode=PipeMode.DISABLED;
        origin.configuredFace(Direction.WEST);
        var saved=origin.saveWithoutMetadata();
        var restored=new ItemPipeBlockEntity(origin.getBlockPos(),origin.getBlockState()); restored.load(saved);
        h.assertTrue(restored.configuredFace()==Direction.WEST && restored.rule(Direction.EAST).mode==PipeMode.EXTRACT && restored.rule(Direction.NORTH).mode==PipeMode.DISABLED,"Disk data must retain focus independently of every face's routing rule");
        saved.remove("ConfiguredFace"); restored.load(saved);
        h.assertTrue(restored.configuredFace()==Direction.NORTH,"Old saves without UI focus must remain readable");
        saved.putInt("ConfiguredFace",99); restored.load(saved);
        h.assertTrue(restored.configuredFace()==Direction.NORTH,"Invalid saved focus must fall back safely");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void ghostFiltersCannotMintItemsAndSettingsAreValidated(GameTestHelper h) {
        var pipe = pipe(h, new BlockPos(1, 1, 1)); var player = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PipeTest"));
        player.setPos(pipe.getBlockPos().getX() + .5, pipe.getBlockPos().getY() + .5, pipe.getBlockPos().getZ() + .5); pipe.owner(player.getUUID());
        var menu = new ItemPipeMenu(77, player.getInventory(), pipe); menu.setCarried(new ItemStack(ModContent.ESSENCE.get(), 32));
        menu.clicked(0, 0, ClickType.PICKUP, player); menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().getCount() == 32 && pipe.rule(Direction.NORTH).filter(0).getCount() == 1 && menu.quickMoveStack(player, 0).isEmpty(), "Ghost templates never consume or release physical items");
        menu.configure(new PipeNetwork.Settings(77, Direction.NORTH.ordinal(), 20, 8, 16, 4, "forge:ingots/copper", false), player);
        h.assertTrue(pipe.rule(Direction.NORTH).maximum == 16 && pipe.rule(Direction.NORTH).tags().size() == 1, "Validated settings are applied to the selected face");
        menu.configure(new PipeNetwork.Settings(77, Direction.SOUTH.ordinal(), 0, 0, 0, 0, "", false), player);
        h.runAtTickTime(3, () -> {
            menu.configure(new PipeNetwork.Settings(77, Direction.NORTH.ordinal(), 101, 20, 8, 0, "", false), player);
            h.assertTrue(pipe.rule(Direction.NORTH).priority == 20 && pipe.rule(Direction.NORTH).maximum == 16, "Stale faces and invalid values cannot mutate routing settings"); h.succeed();
        });
    }
}
