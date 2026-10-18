package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineLogic;
import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.machine.OperationMath;
import io.github.gerbesh.unusualcopper.recipe.IndustrialRecipe;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Purely server-authoritative operation, persisted in the existing machine NBT. */
public final class IndustrialProcessingLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity m, ServerLevel level) {
        if(m.kind==io.github.gerbesh.unusualcopper.machine.MachineKind.VORTEX_PURIFIER) {
            ItemStack bucket=m.inventory.getStackInSlot(1);
            if(bucket.is(net.minecraft.world.item.Items.BUCKET)&&m.coolantOutput.getFluidAmount()>=1000&&m.canFitOutput(new ItemStack(io.github.gerbesh.unusualcopper.reactor.ReactorFluids.BUCKET.get()))) {
                m.commitLocally(()->{bucket.shrink(1);m.coolantOutput.drain(1000,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);m.insertOutput(new ItemStack(io.github.gerbesh.unusualcopper.reactor.ReactorFluids.BUCKET.get()));});
            }
            if(bucket.is(net.minecraft.world.item.Items.WATER_BUCKET) && m.water.getFluidAmount()<=3000) {
                m.commitLocally(() -> {
                    m.water.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),
                            net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    m.inventory.setStackInSlot(1,new ItemStack(net.minecraft.world.item.Items.BUCKET));
                });
            }
        }
        if(m.kind==io.github.gerbesh.unusualcopper.machine.MachineKind.CIRCUIT_ETCHER) {
            ItemStack bucket=m.industrialExtra.getStackInSlot(0);
            if(bucket.is(net.minecraft.world.item.Items.WATER_BUCKET) && m.water.getFluidAmount()<=3000) {
                m.commitLocally(() -> {
                    m.water.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),
                            net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    m.industrialExtra.setStackInSlot(0,new ItemStack(net.minecraft.world.item.Items.BUCKET));
                });
            }
        }
        ItemStack input=m.inventory.getStackInSlot(0);
        if (input.isEmpty()) { m.status(m.hasOperation()?MachineStatus.RECIPE_CHANGED:MachineStatus.IDLE); return; }
        var container=new SimpleContainer(input,m.inventory.getStackInSlot(1),m.industrialExtra.getStackInSlot(0));
        IndustrialRecipe recipe=null;
        // Check ID during an in-progress operation; never switch recipes after CuE was paid.
        for (var candidate : level.getRecipeManager().getAllRecipesFor(ModContent.INDUSTRIAL_RECIPE.get())) {
            if (candidate.machine()==m.kind && candidate.matches(container,level)
                    && (!m.hasOperation() || m.recipeId.equals(candidate.getId().toString()))) {
                recipe=candidate; break;
            }
        }
        if (recipe==null) { m.status(m.hasOperation()?MachineStatus.RECIPE_CHANGED:MachineStatus.INVALID_INPUT); return; }
        ItemStack result=recipe.assemble(container,level.registryAccess());
        if (m.hasOperation() && (!m.inputMatches() || !m.industrialInputsMatch() || m.duration!=recipe.ticks()
                || m.operationCost!=recipe.cost() || m.operationWater!=recipe.waterMb()
                || m.operationCoolant!=recipe.fluidOutput()
                || !ItemStack.matches(m.secondaryResult,recipe.secondary())
                || !ItemStack.matches(m.result,result))) {
            m.status(MachineStatus.RECIPE_CHANGED); return;
        }
        if (!m.hasOperation()) {
            if (recipe.fluidOutput()>0?m.coolantOutput.getCapacity()-m.coolantOutput.getFluidAmount()<recipe.fluidOutput():!m.canFitOutputs(result,recipe.secondary())) { m.status(MachineStatus.OUTPUT_FULL); return; }
            m.begin(recipe.getId().toString(),input.copyWithCount(recipe.inputCount()),result,recipe.ticks(),recipe.cost());
            m.secondaryResult=recipe.secondary();
            if(m.hasIndustrialExtra()) {
                m.recipeCatalyst=m.inventory.getStackInSlot(1).copyWithCount(1);
                m.recipeAdditive=m.kind==io.github.gerbesh.unusualcopper.machine.MachineKind.RESONANCE_CRYSTALLIZER
                        ?m.industrialExtra.getStackInSlot(0).copyWithCount(1):ItemStack.EMPTY;
            }
            m.operationWater=recipe.waterMb();
            m.operationCoolant=recipe.fluidOutput();
            m.setChanged();
        }
        if (m.operationCoolant>0?m.coolantOutput.getCapacity()-m.coolantOutput.getFluidAmount()<m.operationCoolant:!m.canFitOutputs(m.result,m.secondaryResult)) { m.status(MachineStatus.OUTPUT_FULL); return; }
        if (m.operationWater>0 && m.water.getFluidAmount()<m.operationWater) { m.status(MachineStatus.NO_WATER); return; }
        if (m.progress>=m.duration) { m.finish(); return; }
        int due=OperationMath.payment(m.operationCost,m.progress+1,m.duration);
        if (!m.energy.spend(due)) { m.status(MachineStatus.NO_ENERGY); return; }
        m.progress++;
        m.paid+=due;
        m.status(MachineStatus.WORKING);
        if (m.progress>=m.duration) m.finish();
        m.setChanged();
    }
}
