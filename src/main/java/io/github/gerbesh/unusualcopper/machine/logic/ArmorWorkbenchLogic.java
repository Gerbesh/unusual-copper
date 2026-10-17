package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.armor.*;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** E6A: physical module/battery insertion, recovery and paid charging on the server thread. */
public final class ArmorWorkbenchLogic implements MachineLogic {
    private static boolean allowed(CopperMachineBlockEntity m) {
        return m.kind==MachineKind.ARMOR_WORKBENCH;
    }

    public static boolean install(CopperMachineBlockEntity m) {
        if(!allowed(m)||!m.energyEnabled())return false;
        ItemStack armor=m.inventory.getStackInSlot(0);
        ItemStack input=m.inventory.getStackInSlot(1);
        if(!(armor.getItem() instanceof ProgrammableArmorItem)||input.isEmpty())return false;
        if(input.getItem() instanceof ArmorModuleItem module) {
            if(input.hasTag() || !ArmorData.canInstall(armor,module.module,module.moduleLevel))return false;
            m.commitLocally(()-> {
                if(ArmorData.install(armor,module.module,module.moduleLevel))input.shrink(1);
                m.setChanged();
            });
            return true;
        }
        if(input.getItem() instanceof CopperBatteryItem && input.getCount()==1) {
            ItemStack copy=input.copyWithCount(1);
            if(!ArmorData.battery(armor).isEmpty())return false;
            if(!(armor.getItem() instanceof ProgrammableArmorItem worn)
                    || worn.getType()!=net.minecraft.world.item.ArmorItem.Type.CHESTPLATE)return false;
            m.commitLocally(()->{
                if(!ArmorData.installBattery(armor,copy))throw new IllegalStateException("Invalid battery install");
                m.inventory.setStackInSlot(1,ItemStack.EMPTY);
                m.setChanged();
            });
            return true;
        }
        return false;
    }

    /**
     * Consume one already-crafted next-tier chassis, preserving the original physical
     * stack's name, enchants, full serialized capabilities, modules and embedded battery.
     * A separate donor cannot be re-used; one old plus one new chassis become one new.
     */
    public static boolean upgrade(CopperMachineBlockEntity m) {
        if(!allowed(m)||!m.energyEnabled()||!(m.getLevel() instanceof ServerLevel))return false;
        ItemStack previous=m.inventory.getStackInSlot(0);
        ItemStack donor=m.inventory.getStackInSlot(1);
        if(!(previous.getItem() instanceof ProgrammableArmorItem from)
                || !(donor.getItem() instanceof ProgrammableArmorItem to)
                || previous.getCount()!=1 || donor.getCount()!=1 || donor.hasTag()
                || donor.save(new net.minecraft.nbt.CompoundTag()).contains("ForgeCaps")
                || from.getType()!=to.getType() || to.tier!=from.tier+1
                || !ArmorData.upgradeSafe(previous))return false;
        int cost=ArmorUpgradePolicy.energyCost(from.tier);
        if(cost<=0||m.energy.stored()<cost)return false;

        // NBT-based identity conversion retains serialized ForgeCaps unlike copying
        // only the vanilla tag. Validate it before consuming either physical item.
        var saved=previous.save(new net.minecraft.nbt.CompoundTag());
        var itemId=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(donor.getItem());
        if(itemId==null)return false;
        saved.putString("id",itemId.toString());
        ItemStack upgraded=ItemStack.of(saved);
        if(!upgraded.is(donor.getItem()) || !ArmorData.upgradeSafe(upgraded))return false;
        if(saved.contains("ForgeCaps") && !upgraded.save(new net.minecraft.nbt.CompoundTag())
                .getCompound("ForgeCaps").equals(saved.getCompound("ForgeCaps")))return false;
        int damage;
        try {
            damage=ArmorUpgradePolicy.damageAfterUpgrade(previous.getDamageValue(),
                    previous.getMaxDamage(),upgraded.getMaxDamage());
        } catch(IllegalArgumentException invalid) {return false;}
        upgraded.setDamageValue(damage);
        m.commitLocally(()->{
            if(!m.energy.spend(cost))throw new IllegalStateException("Upgrade energy changed");
            m.inventory.setStackInSlot(0,upgraded);
            m.inventory.setStackInSlot(1,ItemStack.EMPTY);
            m.setChanged();
        });
        return true;
    }

    public static boolean recoverModule(CopperMachineBlockEntity m) {
        if(!allowed(m)||!m.energyEnabled())return false;
        ItemStack armor=m.inventory.getStackInSlot(0);
        var types=ArmorData.modules(armor);
        if(types.isEmpty()||!ArmorData.upgradeSafe(armor))return false;
        var module=types.get(types.size()-1);
        ItemStack result=new ItemStack(ModContent.armorModuleItem(module,ArmorData.moduleLevel(armor,module)));
        if(!m.canFitOutput(result))return false;
        m.commitLocally(()->{
            ArmorData.removeLast(armor);
            ItemStack rest=m.insertOutput(result);
            if(!rest.isEmpty())throw new IllegalStateException("Validated output was refused");
            m.setChanged();
        });
        return true;
    }

    public static boolean recoverBattery(CopperMachineBlockEntity m) {
        if(!allowed(m)||!m.energyEnabled())return false;
        ItemStack armor=m.inventory.getStackInSlot(0);
        ItemStack stored=ArmorData.battery(armor);
        if(stored.isEmpty()||!m.canFitOutput(stored))return false;
        m.commitLocally(()->{
            ArmorData.takeBattery(armor);
            ItemStack rest=m.insertOutput(stored);
            if(!rest.isEmpty())throw new IllegalStateException("Validated battery return was refused");
            m.setChanged();
        });
        return true;
    }

    public static boolean cycleProfile(CopperMachineBlockEntity m) {
        if(!allowed(m))return false;
        ItemStack armor=m.inventory.getStackInSlot(0);
        if(!(armor.getItem() instanceof ProgrammableArmorItem item)
                || item.getType()!=net.minecraft.world.item.ArmorItem.Type.CHESTPLATE)return false;
        ArmorData.nextProfile(armor);m.setChanged();return true;
    }

    @Override public void tick(CopperMachineBlockEntity m,ServerLevel level){
        ItemStack armor=m.inventory.getStackInSlot(0);
        if(!(armor.getItem() instanceof ProgrammableArmorItem item)
                || item.getType()!=net.minecraft.world.item.ArmorItem.Type.CHESTPLATE){
            m.status(MachineStatus.IDLE);return;
        }
        ItemStack cell=ArmorData.battery(armor);
        if(cell.isEmpty()){m.status(MachineStatus.IDLE);return;}
        int missing=CopperBatteryItem.capacity(cell)-CopperBatteryItem.stored(cell);
        if(missing<=0){m.status(MachineStatus.IDLE);return;}
        int moved=Math.min(Math.min(4096,missing),m.energy.stored());
        if(moved<=0){m.status(MachineStatus.NO_ENERGY);return;}
        if(!m.energy.spend(moved)){m.status(MachineStatus.NO_ENERGY);return;}
        CopperBatteryItem.stored(cell,CopperBatteryItem.stored(cell)+moved);
        ArmorData.setBattery(armor,cell);
        m.setChanged();
        m.status(MachineStatus.CHARGING);
    }
}
