package io.github.gerbesh.unusualcopper.fluid.tank;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.ArrayList;
import java.util.List;

/** Breaking a cube carries its local quantity once, never the aggregate group amount. */
public final class TankItemStorage {
    private TankItemStorage(){}
    public static List<ItemStack> drops(List<ItemStack> drops,Item item,CopperMachineBlockEntity m){
        if(m.water.isEmpty())return drops;var result=new ArrayList<ItemStack>();boolean saved=false;
        for(var original:drops){
            if(!saved&&original.is(item)&&!original.isEmpty()){
                var filled=original.copyWithCount(1);var data=new CompoundTag();
                data.put("WaterTank",m.water.writeToNBT(new CompoundTag()));
                filled.getOrCreateTag().put("BlockEntityTag",data);result.add(filled);saved=true;
                if(original.getCount()>1){var remainder=original.copyWithCount(original.getCount()-1);withoutFluid(remainder);result.add(remainder);}
            }else if(original.is(item)){var empty=original.copy();withoutFluid(empty);result.add(empty);}
            else result.add(original);
        }
        return result;
    }
    private static void withoutFluid(ItemStack stack){if(stack.getTag()!=null)stack.getTag().getCompound("BlockEntityTag").remove("WaterTank");}
    public static FluidStack contents(ItemStack item){
        if(item.getTag()==null)return FluidStack.EMPTY;
        return FluidStack.loadFluidStackFromNBT(item.getTag().getCompound("BlockEntityTag").getCompound("WaterTank"));
    }
}
