package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.recipe.BatteryUpgradeRecipe;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.List;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BatteryGameTests {
    private static CopperMachineBlockEntity cell(GameTestHelper h,BlockPos pos,MachineKind kind) {
        h.setBlock(pos,ModContent.MACHINES.get(kind).get());return (CopperMachineBlockEntity)h.getBlockEntity(pos);
    }
    private static List<Item> batteries() { return List.of(ModContent.COPPER_BATTERY.get(),ModContent.REINFORCED_BATTERY.get(),ModContent.RESONANT_BATTERY.get(),ModContent.AETHERIC_BATTERY.get()); }

    @GameTest(template="empty",timeoutTicks=30)
    public static void fourGradesFitEveryCellAndCarryTheirOwnCharge(GameTestHelper h) {
        int expected=10_000;
        for(var item:batteries()) {
            var stack=new ItemStack(item);CopperBatteryItem.stored(stack,Integer.MAX_VALUE);
            var restored=ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(CopperBatteryItem.capacity(stack)==expected && CopperBatteryItem.stored(restored)==expected && stack.getMaxStackSize()==1,"Tier capacity or portable item persistence is wrong");
            for(var kind:List.of(MachineKind.COPPER_CELL,MachineKind.COPPER_CELL_II,MachineKind.COPPER_CELL_III)) {
                var machine=new CopperMachineBlockEntity(BlockPos.ZERO,ModContent.MACHINES.get(kind).get().defaultBlockState());
                h.assertTrue(machine.battery.insertItem(0,restored.copy(),false).isEmpty(),"A battery grade was rejected by a cell grade");
                machine.batteryMode=2;var loaded=new CopperMachineBlockEntity(BlockPos.ZERO,machine.getBlockState());loaded.load(machine.saveWithoutMetadata());
                h.assertTrue(loaded.batteryMode==2 && CopperBatteryItem.stored(loaded.battery.getStackInSlot(0))==expected,"Cell save lost its battery or transfer mode");
            }
            CopperBatteryItem.stored(stack,-1);h.assertTrue(CopperBatteryItem.stored(stack)==0,"Negative battery charge escaped normalization");expected*=10;
        }
        var machine=cell(h,new BlockPos(1,2,1),MachineKind.COPPER_CELL);
        h.assertTrue(!machine.battery.insertItem(0,new ItemStack(Items.DIAMOND),false).isEmpty(),"Dock accepted a non-battery");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=35)
    public static void bidirectionalTransferStopsAtCapacityAndPreservesAccounting(GameTestHelper h) {
        var machine=cell(h,new BlockPos(1,2,1),MachineKind.COPPER_CELL);
        var stack=new ItemStack(ModContent.COPPER_BATTERY.get());CopperBatteryItem.stored(stack,9999);machine.battery.setStackInSlot(0,stack);machine.energy.add(1000,false);
        h.runAtTickTime(3,()->{
            h.assertTrue(machine.energy.stored()==999 && CopperBatteryItem.stored(stack)==10000 && machine.energy.produced()==1000 && machine.energy.consumed()==0,"Charge overflowed or counted transfer as production/consumption");
            machine.energy.restore(machine.energy.capacity()-7);machine.batteryMode=1;
        });
        h.runAtTickTime(6,()->{
            h.assertTrue(machine.energy.stored()==machine.energy.capacity() && CopperBatteryItem.stored(stack)==9993 && machine.energy.produced()==1000 && machine.energy.consumed()==0,"Discharge overflowed the cell or destroyed CuE");
            machine.energy.restore(0);machine.batteryMode=0;
        });
        h.runAtTickTime(9,()->{h.assertTrue(machine.energy.stored()==0 && CopperBatteryItem.stored(stack)==9993,"An empty source invented energy");h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=35)
    public static void ratePauseAndRedstoneApplyToTheDock(GameTestHelper h) {
        var machine=cell(h,new BlockPos(1,2,1),MachineKind.COPPER_CELL);
        var stack=new ItemStack(ModContent.AETHERIC_BATTERY.get());CopperBatteryItem.stored(stack,1_000_000);machine.battery.setStackInSlot(0,stack);machine.batteryMode=2;
        h.runAtTickTime(3,()->{h.assertTrue(machine.energy.stored()==0,"Paused dock transferred CuE");machine.batteryMode=1;machine.redstoneMode=1;});
        h.runAtTickTime(6,()->{h.assertTrue(machine.energy.stored()==0,"Dock ignored redstone pause");machine.redstoneMode=0;});
        h.runAtTickTime(7,()->{
            h.assertTrue(machine.energy.stored()==machine.energyTransferRate() && machine.energy.stored()+CopperBatteryItem.stored(stack)==1_000_000,"Dock ignored the cell rate or lost charge");h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void menuShiftClickMovesOneChargedBatteryAndRejectsStaleControls(GameTestHelper h) {
        var machine=cell(h,new BlockPos(1,2,1),MachineKind.COPPER_CELL_III);var player=h.makeMockPlayer();player.setPos(machine.getBlockPos().getX()+.5,machine.getBlockPos().getY()+.5,machine.getBlockPos().getZ()+.5);
        var stack=new ItemStack(ModContent.AETHERIC_BATTERY.get());CopperBatteryItem.stored(stack,9_876_543);player.getInventory().setItem(9,stack);
        var menu=new CopperMachineMenu(1,player.getInventory(),machine);menu.quickMoveStack(player,4);
        h.assertTrue(player.getInventory().getItem(9).isEmpty() && CopperBatteryItem.stored(machine.battery.getStackInSlot(0))==9_876_543,"Shift-click failed to move the physical battery into the dock");
        h.assertTrue(menu.clickMenuButton(player,11) && machine.batteryMode==1 && menu.data.get(45)==1,"Direction button did not apply to the real cell");
        menu.quickMoveStack(player,40);
        int count=0,total=0;for(int i=0;i<36;i++) { var held=player.getInventory().getItem(i);if(held.getItem() instanceof CopperBatteryItem){count+=held.getCount();total+=CopperBatteryItem.stored(held);} }
        h.assertTrue(machine.battery.getStackInSlot(0).isEmpty() && count==1 && total==9_876_543,"Shift-click extraction duplicated or discharged the item");
        player.setPos(machine.getBlockPos().getX()+20,machine.getBlockPos().getY(),machine.getBlockPos().getZ());
        h.assertTrue(!menu.clickMenuButton(player,11) && machine.batteryMode==1,"Stale out-of-range controls mutated the dock");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void breakingCellDropsTheChargedBatteryExactlyOnce(GameTestHelper h) {
        var pos=new BlockPos(2,2,2);var machine=cell(h,pos,MachineKind.COPPER_CELL_II);var stack=new ItemStack(ModContent.RESONANT_BATTERY.get());CopperBatteryItem.stored(stack,765432);machine.batteryMode=2;machine.battery.setStackInSlot(0,stack);
        h.setBlock(pos,Blocks.AIR);
        h.runAtTickTime(2,()->{
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(1));
            var portable=drops.stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).filter(s->s.getItem() instanceof CopperBatteryItem).toList();
            h.assertTrue(portable.size()==1 && portable.get(0).getCount()==1 && CopperBatteryItem.stored(portable.get(0))==765432,"Block removal lost or duplicated its charged dock item");h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void emptyUpgradeChainMatchesSsnPlannedResultsAndNormalizesLegacyZero(GameTestHelper h) {
        var machine=cell(h,new BlockPos(1,2,1),MachineKind.COPPER_CELL);
        var menu=new CopperMachineMenu(1,h.makeMockPlayer().getInventory(),machine);
        var grid=new TransientCraftingContainer(menu,3,3);
        var stack=new ItemStack(ModContent.COPPER_BATTERY.get());
        var materials=List.of(List.of(Items.COPPER_BLOCK,Items.GLASS,Items.REDSTONE_BLOCK),List.of(Items.AMETHYST_BLOCK,Items.DIAMOND,Items.GOLD_BLOCK),List.of(Items.OBSIDIAN,Items.NETHERITE_INGOT,Items.NETHER_STAR));
        CopperBatteryItem.stored(stack,0);
        h.assertTrue(!stack.hasTag(),"Writing zero charge created NBT on a pristine battery");
        for(int tier=1;tier<4;tier++) {
            var material=materials.get(tier-1);
            for(int i=0;i<9;i++)grid.setItem(i,new ItemStack(material.get(switch(i){case 1,7->1;case 3,5->2;default->0;})));
            grid.setItem(4,stack);
            var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow();
            var planned=recipe.getResultItem(h.getLevel().registryAccess());
            var actual=recipe.assemble(grid,h.getLevel().registryAccess());
            h.assertTrue(actual.is(batteries().get(tier)) && ItemStack.isSameItemSameTags(actual,planned) && actual.getCount()==planned.getCount(),"Empty battery upgrade differs from SSN's planned result");
            stack.getOrCreateTag().putInt(CopperBatteryItem.ENERGY_TAG,0);
            var legacy=recipe.assemble(grid,h.getLevel().registryAccess());
            h.assertTrue(ItemStack.isSameItemSameTags(legacy,planned) && stack.getTag().contains(CopperBatteryItem.ENERGY_TAG),"Legacy CuE:0 was not normalized on the result or crafting mutated its input");
            stack=actual;
        }
        CopperBatteryItem.stored(stack,1234);stack.setHoverName(net.minecraft.network.chat.Component.literal("Keep my name"));
        stack.getOrCreateTag().putString("OtherMetadata","keep");
        CopperBatteryItem.stored(stack,0);
        h.assertTrue(!stack.getTag().contains(CopperBatteryItem.ENERGY_TAG) && stack.getHoverName().getString().equals("Keep my name") && stack.getTag().getString("OtherMetadata").equals("keep"),"Discharging erased unrelated battery metadata");
        var plain=new ItemStack(ModContent.COPPER_BATTERY.get());CopperBatteryItem.stored(plain,100);CopperBatteryItem.stored(plain,0);
        h.assertTrue(!plain.hasTag() && CopperBatteryItem.stored(plain)==0,"Full discharge did not restore the ordinary empty battery identity");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void actualUpgradeRecipesAndNetworkCodecPreserveCharge(GameTestHelper h) {
        var machine=cell(h,new BlockPos(1,2,1),MachineKind.COPPER_CELL);var menu=new CopperMachineMenu(1,h.makeMockPlayer().getInventory(),machine);
        var grid=new TransientCraftingContainer(menu,3,3);var stack=new ItemStack(ModContent.COPPER_BATTERY.get());CopperBatteryItem.stored(stack,9876);stack.setHoverName(net.minecraft.network.chat.Component.literal("Travel power"));
        var materials=List.of(List.of(Items.COPPER_BLOCK,Items.GLASS,Items.REDSTONE_BLOCK),List.of(Items.AMETHYST_BLOCK,Items.DIAMOND,Items.GOLD_BLOCK),List.of(Items.OBSIDIAN,Items.NETHERITE_INGOT,Items.NETHER_STAR));
        for(int tier=1;tier<4;tier++) {
            var material=materials.get(tier-1);for(int i=0;i<9;i++)grid.setItem(i,new ItemStack(material.get(switch(i){case 1,7->1;case 3,5->2;default->0;})));grid.setItem(4,stack);
            var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow();
            h.assertTrue(recipe instanceof BatteryUpgradeRecipe,"Upgrade was not registered as a charge-preserving shaped recipe");
            var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                ModContent.BATTERY_UPGRADE_SERIALIZER.get().toNetwork(buffer,(BatteryUpgradeRecipe)recipe);
                var synced=ModContent.BATTERY_UPGRADE_SERIALIZER.get().fromNetwork(new ResourceLocation("unusualcopper","upgrade_probe"),buffer);
                var result=synced.assemble(grid,h.getLevel().registryAccess());
                h.assertTrue(result.is(batteries().get(tier)) && CopperBatteryItem.stored(result)==9876 && result.getHoverName().getString().equals("Travel power") && CopperBatteryItem.stored(stack)==9876,"Crafting or recipe synchronization lost charge/name or mutated the source");stack=result;
            } finally {buffer.release();}
        }
        h.succeed();
    }
}
