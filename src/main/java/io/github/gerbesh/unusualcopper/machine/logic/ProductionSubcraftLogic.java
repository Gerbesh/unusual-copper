package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.List;

/**
 * E5B-5: craft one real vanilla copper frame using nine machine-owned physical
 * ingredients. No imaginary intermediate output, no access to another crafting table
 * and no external inventory reservation. All nine inputs and completed frame belong
 * to the same saved controller BE, with BoundCargo as the output escrow.
 */
public final class ProductionSubcraftLogic {
    private ProductionSubcraftLogic() {}

    private static Item expected(int slot) {return slot==4?Items.IRON_NUGGET:Items.COPPER_INGOT;}

    private static ShapedRecipe recipe(ServerLevel level) {
        var loaded=level.getRecipeManager().byKey(new ResourceLocation("unusualcopper","copper_frame"));
        if(loaded.isEmpty() || !(loaded.get() instanceof ShapedRecipe recipe)
                || recipe.getWidth()!=3 || recipe.getHeight()!=3 || recipe.getIngredients().size()!=9)return null;
        var output=recipe.getResultItem(level.registryAccess());
        if(!output.is(ModContent.FRAME.get()) || output.hasTag() || output.getCount()!=1)return null;
        for(int i=0;i<9;i++) {
            var choices=recipe.getIngredients().get(i).getItems();
            if(choices.length!=1 || !choices[0].is(expected(i)) || choices[0].hasTag()
                    || choices[0].getCount()!=1)return null;
        }
        return recipe;
    }

    private static CraftingContainer snapshot(CopperMachineBlockEntity m) {
        AbstractContainerMenu dummy=new AbstractContainerMenu(null,-1) {
            @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
            @Override public boolean stillValid(Player player){return false;}
        };
        CraftingContainer grid=new TransientCraftingContainer(dummy,3,3);
        for(int i=0;i<9;i++)grid.setItem(i,m.productionSubGrid.getStackInSlot(i).copy());
        return grid;
    }

    /**
     * True if controller is currently trying to make a missing copper frame.
     * No upstream press is commissioned in the same tick, so bounded one-job-at-a-time
     * E5B-4 semantics are preserved.
     */
    public static boolean tick(CopperMachineBlockEntity m,ServerLevel level) {
        if(m.kind!=MachineKind.PRODUCTION_CONTROLLER || m.productionPattern!=1
                || !m.energyEnabled() || m.productionQueued<=0 || m.owner()==null || m.boundFaulted)
            return false;
        boolean ownsInputs=m.productionSubGridCount()>0 ||
                m.boundCargoCount()>0 && m.boundCargoSlot>=14 && m.boundCargoSlot<=22;
        if(!ownsInputs && !m.assemblyGrid.getStackInSlot(4).isEmpty())return false;
        // If the frame already exists, use the ordinary E5B-1 storage grid refill.
        var endpoint=ProductionUpstreamLogic.warehouse(m,level);
        if(endpoint==null)return ownsInputs;
        if(!ownsInputs) {
            var stock=endpoint.countItem(ModContent.FRAME.get(),stack->
                    !stack.isEmpty() && !stack.hasTag() && stack.is(ModContent.FRAME.get()));
            if(!stock.complete() || stock.amount()>0)return false;
        }
        var recipe=recipe(level);
        if(recipe==null)return ownsInputs;
        if(m.boundCargoCount()!=0)return true;
        for(int i=0;i<9;i++) {
            var stored=m.productionSubGrid.getStackInSlot(i);
            if(!stored.isEmpty() && (!stored.is(expected(i)) || stored.hasTag()))return true;
            if(!stored.isEmpty())continue;
            int slot=i;
            Item required=expected(i);
            var entries=endpoint.snapshot(stack->!stack.isEmpty() && !stack.hasTag()
                    && stack.is(required),32);
            for(var entry:entries.entries()) {
                if(entry.count()<1 || endpoint.simulateExtract(entry.key(),1)!=1)continue;
                var actual=endpoint.extract(entry.key(),1);
                if(actual.isEmpty())return true;
                // Escrow before either insertion into the physical subgrid or another extraction.
                m.boundCargo(actual);
                m.boundCargoSlot=14+slot;
                m.boundFaulted=actual.stream().anyMatch(s->!entry.key().matches(s))
                        || actual.stream().mapToLong(ItemStack::getCount).sum()>1;
                m.setChanged();
                return true;
            }
            return true; // material missing: hold all already-extracted pieces in physical slots
        }
        var grid=snapshot(m);
        if(!recipe.matches(grid,level))return true;
        NonNullList<ItemStack> remainders=recipe.getRemainingItems(grid);
        for(var remainder:remainders)if(!remainder.isEmpty())return true;
        var result=recipe.assemble(grid,level.registryAccess());
        if(!result.is(ModContent.FRAME.get())||result.hasTag()||result.getCount()!=1)return true;
        m.commitLocally(()->{
            for(int i=0;i<9;i++)m.productionSubGrid.extractItem(i,1,false);
            m.boundCargo(List.of(result.copy()));
            m.boundCargoSlot=-1;
        });
        return true;
    }
}
