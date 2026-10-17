package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.recipe.IndustrialRecipe;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/**
 * E5B-4 vertical slice: actually manufacture missing copper plates / wire spools using
 * already placed, independently CuE-powered adjacent E2 presses. Only a single physical
 * batch is owned at once; all input/output is extracted into persisted BoundCargo.
 *
 * No force-loaded chunks, world-global reservations, fake recipes, or SSN order ownership.
 * A press needs the appropriate reusable die in its physical mold slot.
 */
public final class ProductionUpstreamLogic {
    private ProductionUpstreamLogic() {}

    private static Item input(int stage) {return stage==ProductionPressTask.PLATE?Items.COPPER_INGOT:ModContent.COPPER_PLATE.get();}
    private static Item output(int stage) {return stage==ProductionPressTask.PLATE?ModContent.COPPER_PLATE.get():ModContent.COPPER_WIRE.get();}
    private static Item die(int stage) {return stage==ProductionPressTask.PLATE?ModContent.PLATE_DIE.get():ModContent.WIRE_DIE.get();}
    private static int inputCount(int stage) {return stage==ProductionPressTask.PLATE?1:2;}
    private static int outputCount(int stage) {return stage==ProductionPressTask.PLATE?2:4;}
    private static String recipeId(int stage) {return stage==ProductionPressTask.PLATE?"press_copper_plate":"press_copper_wire";}

    private static IndustrialRecipe recipe(ServerLevel level,int stage) {
        var loaded=level.getRecipeManager().byKey(new ResourceLocation("unusualcopper","industrial/"+recipeId(stage)));
        if(loaded.isEmpty()||!(loaded.get() instanceof IndustrialRecipe recipe)
                || recipe.machine()!=MachineKind.COPPER_PRESS || recipe.inputCount()!=inputCount(stage)
                || recipe.getResultItem(level.registryAccess()).getCount()!=outputCount(stage)
                || !recipe.getResultItem(level.registryAccess()).is(output(stage))
                || recipe.getResultItem(level.registryAccess()).hasTag()
                || !recipe.secondary().isEmpty() || recipe.waterMb()!=0)return null;
        var parts=recipe.getIngredients();
        if(parts.size()!=2 || !onlyItem(parts.get(0),input(stage)) || !onlyItem(parts.get(1),die(stage)))return null;
        return recipe;
    }

    private static boolean onlyItem(net.minecraft.world.item.crafting.Ingredient ingredient,Item expected) {
        var variants=ingredient.getItems();
        return variants.length==1 && variants[0].is(expected) && variants[0].getCount()==1 && !variants[0].hasTag();
    }

    private static boolean controls(CopperMachineBlockEntity controller,CopperMachineBlockEntity press,ServerLevel level) {
        var owner=controller.owner();
        return owner!=null && owner.equals(press.owner())
                && controller.getBlockPos().distManhattan(press.getBlockPos())==1
                // Both persistent block entities must be saved in the same chunk.
                && (controller.getBlockPos().getX()>>4)==(press.getBlockPos().getX()>>4)
                && (controller.getBlockPos().getZ()>>4)==(press.getBlockPos().getZ()>>4)
                && level.hasChunkAt(press.getBlockPos())
                && ProtectionPolicy.allowed(level,controller.getBlockPos(),owner)
                && ProtectionPolicy.allowed(level,press.getBlockPos(),owner);
    }

    private static CopperMachineBlockEntity leasedPress(CopperMachineBlockEntity controller,ServerLevel level) {
        var task=controller.productionPressTask;
        if(task==null || !level.hasChunkAt(task.press()))return null;
        var entity=level.getBlockEntity(task.press());
        if(!(entity instanceof CopperMachineBlockEntity press) || press.kind!=MachineKind.COPPER_PRESS
                || !press.hasPressLease(task,controller.getBlockPos()) || !controls(controller,press,level))return null;
        return press;
    }

    public static void tick(CopperMachineBlockEntity controller,ServerLevel level) {
        if(controller.kind!=MachineKind.PRODUCTION_CONTROLLER)return;
        var task=controller.productionPressTask;
        if(task!=null) {
            if(controller.energyEnabled() && !controller.hasOperation() && !controller.boundFaulted)
                controller.status(MachineStatus.WAITING_UPSTREAM);
            if(level.hasChunkAt(task.press())) {
                var target=level.getBlockEntity(task.press());
                if(!(target instanceof CopperMachineBlockEntity press)
                        || press.kind!=MachineKind.COPPER_PRESS
                        || !press.hasPressLease(task,controller.getBlockPos())) {
                    // If its block still exists but BE is waking up, do not drop
                    // a claim just because the BE was temporarily unavailable.
                    if(target==null && level.getBlockState(task.press()).getBlock() instanceof CopperMachineBlock block
                            && block.kind==MachineKind.COPPER_PRESS)return;
                    // Lost/replaced press: its items either live in the real block
                    // or were dropped on removal; escrow remains player-recoverable.
                    controller.productionPressTask=null;
                    controller.setChanged();
                    return;
                }
            }
            var press=leasedPress(controller,level);
            if(press==null)return; // unload/protection/revocation pauses, never drops cargo
            continueBatch(controller,press,level,task);
            return;
        }
        if(ProductionSubcraftLogic.tick(controller,level))return;
        if(!controller.energyEnabled() || controller.productionQueued<=0
                || controller.hasOperation() && !controller.productionInputsMatch()
                || controller.boundCargoCount()!=0 || controller.boundFaulted || controller.boundInventory()==null
                || controller.owner()==null)return;
        var endpoint=warehouse(controller,level);
        if(endpoint==null)return;
        int wanted=desiredStage(controller,endpoint);
        if(wanted==0 || recipe(level,wanted)==null
                || available(endpoint,input(wanted))<inputCount(wanted))return;
        for(Direction dir:Direction.values()) {
            var pos=controller.getBlockPos().relative(dir);
            if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof CopperMachineBlockEntity press)
                    || press.kind!=MachineKind.COPPER_PRESS || press.pressLeased()
                    || press.boundCargoCount()!=0 || press.boundFaulted
                    || !controls(controller,press,level) || !press.energyEnabled()
                    || press.hasOperation() || !press.inventory.getStackInSlot(0).isEmpty()
                    || !press.inventory.getStackInSlot(2).isEmpty() || !press.inventory.getStackInSlot(3).isEmpty()
                    || !press.inventory.getStackInSlot(1).is(die(wanted))
                    || press.inventory.getStackInSlot(1).hasTag())continue;
            var newTask=new ProductionPressTask(UUID.randomUUID(),pos,wanted);
            press.productionPressLease=newTask.token();
            press.productionPressController=controller.getBlockPos().immutable();
            controller.productionPressTask=newTask;
            press.setChanged();controller.setChanged();
            continueBatch(controller,press,level,newTask);
            return;
        }
    }

    /** Never try to commission intermediate jobs while the source census is incomplete. */
    private static int desiredStage(CopperMachineBlockEntity m,StorageEndpoint endpoint) {
        Item wanted=m.productionPattern==0?ModContent.COPPER_WIRE.get():ModContent.COPPER_PLATE.get();
        var order=ProductionOrderPolicy.plan(m.productionPattern);
        var entry=m.getLevel().getRecipeManager().byKey(new ResourceLocation("unusualcopper",order.recipe()));
        if(entry.isEmpty() || !(entry.get() instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped)
                || shaped.getWidth()!=3 || shaped.getHeight()!=3 || shaped.getIngredients().size()!=9)return 0;
        ItemStack output=shaped.getResultItem(m.getLevel().registryAccess());
        if(output.getCount()!=1 || output.hasTag()
                || !output.is(m.productionPattern==0?ModContent.RESONANT_COIL.get():ModContent.REINFORCED_FRAME.get()))return 0;
        boolean[] positions=new boolean[9], matching=new boolean[9], target=new boolean[9];
        int[] counts=new int[9];
        for(int i=0;i<9;i++) {
            var ingredient=shaped.getIngredients().get(i);
            ItemStack held=m.assemblyGrid.getStackInSlot(i);
            positions[i]=!ingredient.isEmpty();
            matching[i]=held.isEmpty() || !held.hasTag() && ingredient.test(held);
            target[i]=positions[i] && ingredient.test(new ItemStack(wanted));
            counts[i]=held.getCount();
        }
        int missingSlots=ProductionInputDemandPolicy.missing(m.productionQueued,positions,matching,target,counts);
        if(missingSlots<=0)return 0;
        long stocked=available(endpoint,wanted);
        if(stocked!=0)return 0;
        long plates=m.productionPattern==0?available(endpoint,ModContent.COPPER_PLATE.get()):0;
        return ProductionPressPolicy.stage(m.productionPattern,missingSlots,stocked,plates);
    }

    private static long available(StorageEndpoint endpoint,Item item) {
        var census=endpoint.countItem(item,stack->!stack.isEmpty() && !stack.hasTag() && stack.is(item));
        return census.complete()?census.amount():-1;
    }

    static StorageEndpoint warehouse(CopperMachineBlockEntity m,ServerLevel level) {
        var bound=m.boundInventory();
        if(bound==null || m.getBlockPos().distSqr(bound)>32*32 || !level.hasChunkAt(bound)
                || !ProtectionPolicy.allowed(level,m.getBlockPos(),m.owner()))return null;
        var endpoint=m.boundEndpoint(level);
        return endpoint!=null&&endpoint.isAvailable()?endpoint:null;
    }

    private static void continueBatch(CopperMachineBlockEntity controller,CopperMachineBlockEntity press,
                                      ServerLevel level,ProductionPressTask task) {
        var recipe=recipe(level,task.stage());
        if(recipe==null || controller.boundFaulted)return;
        var expected=press.inventory.getStackInSlot(2);
        if(!press.inventory.getStackInSlot(1).is(die(task.stage()))
                || press.inventory.getStackInSlot(1).hasTag())return;
        var alternate=press.inventory.getStackInSlot(3);
        // The claimed press started with no output, and ordinary user/item automation
        // cannot take or insert output while the lease is held.
        if(!press.hasOperation() && press.inventory.getStackInSlot(0).isEmpty()
                && ((expected.is(output(task.stage()))&&!expected.hasTag()&&expected.getCount()==outputCount(task.stage())&&alternate.isEmpty())
                    || (alternate.is(output(task.stage()))&&!alternate.hasTag()&&alternate.getCount()==outputCount(task.stage())&&expected.isEmpty()))) {
            if(controller.boundCargoCount()!=0)return;
            int slot=expected.isEmpty()?3:2;
            ItemStack produced=press.inventory.extractItem(slot,outputCount(task.stage()),false);
            if(produced.getCount()!=outputCount(task.stage()))return;
            // The output has been physically moved into the controller's serialized
            // escrow. Returning it to warehouse may pause until the endpoint is writable.
            controller.boundCargo(List.of(produced));
            controller.boundCargoSlot=-1;
            press.productionPressLease=null;
            press.productionPressController=null;
            controller.productionPressTask=null;
            press.setChanged();controller.setChanged();
            return;
        }
        if(!press.inventory.getStackInSlot(2).isEmpty() || !press.inventory.getStackInSlot(3).isEmpty()
                || press.hasOperation() || !controller.energyEnabled() || controller.boundCargoCount()!=0)return;
        var held=press.inventory.getStackInSlot(0);
        if(!held.isEmpty() && (!held.is(input(task.stage())) || held.hasTag()))return;
        int requested=inputCount(task.stage())-held.getCount();
        if(requested<=0)return; // physical press takes over and spends its own CuE
        var endpoint=warehouse(controller,level);
        if(endpoint==null)return;
        var view=endpoint.snapshot(stack->!stack.isEmpty()&&!stack.hasTag()&&stack.is(input(task.stage())),32);
        for(var entry:view.entries()) {
            int count=Math.min(requested,entry.count());
            if(count<=0||endpoint.simulateExtract(entry.key(),count)!=count)continue;
            var actual=endpoint.extract(entry.key(),count);
            if(actual.isEmpty())return;
            controller.boundCargo(actual);
            controller.boundCargoSlot=13; // a dedicated, persisted upstream-press escrow destination
            controller.boundFaulted=actual.stream().anyMatch(s->!entry.key().matches(s))
                    || actual.stream().mapToLong(ItemStack::getCount).sum()>count;
            controller.setChanged();
            return;
        }
    }

    /** Called by the controller's existing persisted cargo delivery path. */
    public static ItemStack deliverInput(CopperMachineBlockEntity controller,ItemStack offered) {
        if(!(controller.getLevel() instanceof ServerLevel level)||controller.productionPressTask==null
                || !controller.energyEnabled() || controller.boundFaulted)return offered.copy();
        var press=leasedPress(controller,level);
        if(press==null || press.hasOperation() || recipe(level,controller.productionPressTask.stage())==null
                || !press.inventory.getStackInSlot(1).is(die(controller.productionPressTask.stage()))
                || press.inventory.getStackInSlot(1).hasTag()
                || !press.inventory.getStackInSlot(2).isEmpty() || !press.inventory.getStackInSlot(3).isEmpty())
            return offered.copy();
        var step=controller.productionPressTask.stage();
        if(offered.isEmpty()||offered.hasTag()||!offered.is(input(step)))return offered.copy();
        var existing=press.inventory.getStackInSlot(0);
        if(!existing.isEmpty()&&(!existing.is(input(step))||existing.hasTag()))return offered.copy();
        int move=Math.min(offered.getCount(),inputCount(step)-existing.getCount());
        if(move<=0)return offered.copy();
        press.commitLocally(()->press.inventory.setStackInSlot(0,
                new ItemStack(input(step),existing.getCount()+move)));
        return offered.copyWithCount(offered.getCount()-move);
    }

    /** A lost controller never voids a press inventory. Only the orphaned lock is removed. */
    public static void recoverOrphan(CopperMachineBlockEntity press,ServerLevel level) {
        if(!press.pressLeased() || press.productionPressController==null)return;
        var pos=press.productionPressController;
        if(!level.hasChunkAt(pos))return;
        var entity=level.getBlockEntity(pos);
        if(entity instanceof CopperMachineBlockEntity controller) {
            if(press.hasPressLease(controller.productionPressTask,pos)
                    && controller.productionPressTask.press().equals(press.getBlockPos()))return;
        } else {
            // An unloaded or temporarily absent BE with its controller block
            // still present must not lose a claim during chunk deserialization.
            if(level.getBlockState(pos).getBlock() instanceof CopperMachineBlock block
                    && block.kind==MachineKind.PRODUCTION_CONTROLLER)return;
        }
        press.productionPressLease=null;
        press.productionPressController=null;
        press.setChanged();
    }
}
