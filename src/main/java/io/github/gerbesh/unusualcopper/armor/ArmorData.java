package io.github.gerbesh.unusualcopper.armor;

import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/** All persistent module/battery state belongs to the physical armor stack. */
public final class ArmorData {
    private static final String ROOT="UCProgrammableArmor";
    private ArmorData() {}
    public static int slots(ProgrammableArmorItem item){
        return ArmorProgression.sockets(item.tier,item.getType()==ArmorItem.Type.CHESTPLATE);
    }
    private static CompoundTag data(ItemStack item){
        return item.hasTag() && item.getTag().contains(ROOT,Tag.TAG_COMPOUND)
                ?item.getTag().getCompound(ROOT):new CompoundTag();
    }
    private static CompoundTag mutable(ItemStack item) {
        CompoundTag tag=item.getOrCreateTagElement(ROOT);
        tag.putInt("Version",1);
        return tag;
    }
    public static List<ArmorModuleType> modules(ItemStack item) {
        if(!(item.getItem() instanceof ProgrammableArmorItem armor))return List.of();
        var result=new ArrayList<ArmorModuleType>();
        var list=data(item).getList("Modules",Tag.TAG_STRING);
        for(int i=0;i<Math.min(slots(armor),list.size());i++){
            var kind=ArmorModuleType.byId(list.getString(i));
            if(kind!=null && kind.slot==armor.getEquipmentSlot() && !result.contains(kind))result.add(kind);
        }
        return List.copyOf(result);
    }
    public static boolean canInstall(ItemStack item,ArmorModuleType type) {
        if(!(item.getItem() instanceof ProgrammableArmorItem armor))return false;
        var modules=modules(item);
        return type.slot==armor.getEquipmentSlot() && modules.size()<slots(armor) && !modules.contains(type)
                && (type!=ArmorModuleType.HOVER_DRIVE || armor.tier>=HoverPolicy.REQUIRED_TIER)
                && (type!=ArmorModuleType.FLIGHT_CORE_II || armor.tier>=LimitedFlightPolicy.REQUIRED_TIER);
    }
    public static boolean install(ItemStack item,ArmorModuleType kind) {
        if(!canInstall(item,kind))return false;
        var list=new ListTag();
        for(var existing:modules(item))list.add(StringTag.valueOf(existing.id));
        list.add(StringTag.valueOf(kind.id));
        mutable(item).put("Modules",list);
        return true;
    }
    public static ArmorModuleType removeLast(ItemStack item){
        var types=modules(item);
        if(types.isEmpty())return null;
        var list=new ListTag();
        for(int i=0;i<types.size()-1;i++)list.add(StringTag.valueOf(types.get(i).id));
        mutable(item).put("Modules",list);
        return types.get(types.size()-1);
    }
    public static int profile(ItemStack item) {return Math.floorMod(data(item).getInt("Profile"),4);}
    public static void nextProfile(ItemStack item){mutable(item).putInt("Profile",(profile(item)+1)%4);}
    public static boolean profile(ItemStack item,int profile){
        if(!(item.getItem() instanceof ProgrammableArmorItem armor)||armor.getType()!=ArmorItem.Type.CHESTPLATE||profile<0||profile>3)return false;
        mutable(item).putInt("Profile",profile);return true;
    }
    public static ArmorModuleSettings settings(ItemStack item,ArmorModuleType module,int profile){
        var root=data(item);
        if(profile<0||profile>3)return ArmorModuleSettings.invalid();
        if(!root.contains("Presets"))return new ArmorModuleSettings(enabled(module,profile),ArmorModuleSettings.Trigger.AUTO,0,false,ArmorPowerScheduler.defaultPriority(module));
        if(!root.contains("Presets",Tag.TAG_COMPOUND))return ArmorModuleSettings.invalid();
        var presets=root.getCompound("Presets");String key=Integer.toString(profile);
        if(presets.contains(key)&&!presets.contains(key,Tag.TAG_COMPOUND))return ArmorModuleSettings.invalid();
        var preset=presets.getCompound(key);
        if(!preset.contains(module.id))return new ArmorModuleSettings(enabled(module,profile),ArmorModuleSettings.Trigger.AUTO,0,false,ArmorPowerScheduler.defaultPriority(module));
        if(!preset.contains(module.id,Tag.TAG_COMPOUND))return ArmorModuleSettings.invalid();
        var row=preset.getCompound(module.id);
        if(!row.contains("Enabled",Tag.TAG_BYTE)||!row.contains("Trigger",Tag.TAG_INT)
                ||!row.contains("Minimum",Tag.TAG_INT)||!row.contains("Armed",Tag.TAG_BYTE))return ArmorModuleSettings.invalid();
        int trigger=row.getInt("Trigger"),minimum=row.getInt("Minimum");
        int priority=row.contains("Priority",Tag.TAG_INT)?row.getInt("Priority"):ArmorPowerScheduler.defaultPriority(module);
        if(row.contains("Priority")&&!row.contains("Priority",Tag.TAG_INT)
                ||trigger<0||trigger>=ArmorModuleSettings.Trigger.values().length||minimum<0||minimum>95||minimum%5!=0
                ||priority<2||priority>6)return ArmorModuleSettings.invalid();
        return new ArmorModuleSettings(row.getBoolean("Enabled"),ArmorModuleSettings.Trigger.values()[trigger],minimum,row.getBoolean("Armed"),priority);
    }
    public static boolean settings(ItemStack item,ArmorModuleType module,int profile,ArmorModuleSettings value){
        if(profile<0||profile>3||!modules(item).contains(module)||value==null)return false;
        var root=mutable(item);var presets=root.getCompound("Presets");var preset=presets.getCompound(Integer.toString(profile));
        var row=new CompoundTag();row.putBoolean("Enabled",value.enabled());row.putInt("Trigger",value.trigger().ordinal());
        row.putInt("Minimum",value.minimumPercent());row.putBoolean("Armed",value.armed());row.putInt("Priority",value.priority());
        preset.put(module.id,row);presets.put(Integer.toString(profile),preset);root.put("Presets",presets);return true;
    }
    public static int percentage(ItemStack chest){var cell=battery(chest);int capacity=CopperBatteryItem.capacity(cell);return capacity==0?0:(int)(100L*CopperBatteryItem.stored(cell)/capacity);}
    /** Bounded configuration fingerprint deliberately excludes changing battery charge. */
    public static String configuration(ItemStack item){
        var value=new StringBuilder().append(profile(item)).append('/');
        for(var module:modules(item)){
            value.append(module.ordinal()).append(':');
            for(int p=0;p<4;p++){
                var s=settings(item,module,p);value.append(s.enabled()?'1':'0').append(',').append(s.trigger().ordinal()).append(',')
                        .append(s.minimumPercent()).append(',').append(s.armed()?'1':'0').append(',').append(s.priority()).append(';');
            }
        }
        return value.toString();
    }
    public static boolean enabled(ArmorModuleType module,int profile){
        return switch(profile){
            case 0 -> module==ArmorModuleType.AQUA_LUNGS || module==ArmorModuleType.FALL_DAMPER || module==ArmorModuleType.OPTIC_VISOR;
            case 1 -> module==ArmorModuleType.STRIDE_DRIVE || module==ArmorModuleType.MINING_SERVO || module==ArmorModuleType.AQUA_LUNGS || module==ArmorModuleType.JUMP_DRIVE;
            case 2 -> module==ArmorModuleType.SHIELD_FIELD || module==ArmorModuleType.FALL_DAMPER;
            default -> true;
        };
    }
    public static ItemStack battery(ItemStack chest) {
        if(!(chest.getItem() instanceof ProgrammableArmorItem armor)
                || armor.getType()!=ArmorItem.Type.CHESTPLATE)return ItemStack.EMPTY;
        var saved=data(chest).getCompound("Battery");
        ItemStack result=ItemStack.of(saved);
        return result.getItem() instanceof CopperBatteryItem && result.getCount()==1?result:ItemStack.EMPTY;
    }
    public static boolean installBattery(ItemStack chest,ItemStack battery){
        if(!(chest.getItem() instanceof ProgrammableArmorItem armor)
                || armor.getType()!=ArmorItem.Type.CHESTPLATE || !ArmorData.battery(chest).isEmpty()
                || !(battery.getItem() instanceof CopperBatteryItem) || battery.getCount()!=1)return false;
        mutable(chest).put("Battery",battery.save(new CompoundTag()));
        return true;
    }
    public static ItemStack takeBattery(ItemStack chest) {
        ItemStack old=battery(chest);
        if(!old.isEmpty())mutable(chest).remove("Battery");
        return old;
    }
    public static void setBattery(ItemStack chest,ItemStack battery){
        if(!(chest.getItem() instanceof ProgrammableArmorItem armor)
                || armor.getType()!=ArmorItem.Type.CHESTPLATE
                || !(battery.getItem() instanceof CopperBatteryItem) || battery.getCount()!=1)
            throw new IllegalArgumentException("Invalid battery");
        mutable(chest).put("Battery",battery.save(new CompoundTag()));
    }
    /** Refuse corrupt or future-version armor data rather than silently dropping charged state. */
    public static boolean upgradeSafe(ItemStack item) {
        if(!(item.getItem() instanceof ProgrammableArmorItem armor) || item.getCount()!=1)return false;
        if(!item.hasTag() || !item.getTag().contains(ROOT))return true;
        if(!item.getTag().contains(ROOT,Tag.TAG_COMPOUND))return false;
        CompoundTag root=item.getTag().getCompound(ROOT);
        if(!root.contains("Version",Tag.TAG_INT)||root.getInt("Version")!=1)return false;
        if(root.contains("Modules")) {
            if(!root.contains("Modules",Tag.TAG_LIST))return false;
            ListTag list=root.getList("Modules",Tag.TAG_STRING);
            if(list.size()>0 && list.getElementType()!=Tag.TAG_STRING)return false;
            if(root.get("Modules") instanceof ListTag raw && raw.size()>0 && raw.getElementType()!=Tag.TAG_STRING)return false;
            if(list.size()>slots(armor))return false;
            var seen=new HashSet<ArmorModuleType>();
            for(int i=0;i<list.size();i++) {
                ArmorModuleType module=ArmorModuleType.byId(list.getString(i));
                if(module==null || module.slot!=armor.getEquipmentSlot() || !seen.add(module))return false;
            }
        }
        if(root.contains("Battery") && (!root.contains("Battery",Tag.TAG_COMPOUND)
                || armor.getType()!=ArmorItem.Type.CHESTPLATE || battery(item).isEmpty()))return false;
        if(root.contains("Profile") && (!root.contains("Profile",Tag.TAG_INT)
                || root.getInt("Profile")<0 || root.getInt("Profile")>3))return false;
        if(root.contains("Presets")){
            if(!root.contains("Presets",Tag.TAG_COMPOUND))return false;
            var presets=root.getCompound("Presets");if(presets.size()>4)return false;
            for(String key:presets.getAllKeys()){
                if(!java.util.Set.of("0","1","2","3").contains(key)||!presets.contains(key,Tag.TAG_COMPOUND))return false;
                var preset=presets.getCompound(key);if(preset.size()>ArmorModuleType.values().length)return false;
                for(String id:preset.getAllKeys()){
                    var module=ArmorModuleType.byId(id);
                    if(module==null||module.slot!=armor.getEquipmentSlot()||!preset.contains(id,Tag.TAG_COMPOUND))return false;
                    var row=preset.getCompound(id);int trigger=row.getInt("Trigger"),minimum=row.getInt("Minimum");
                    if(!row.contains("Enabled",Tag.TAG_BYTE)||!row.contains("Armed",Tag.TAG_BYTE)
                            ||!row.contains("Trigger",Tag.TAG_INT)||!row.contains("Minimum",Tag.TAG_INT)
                            ||trigger<0||trigger>=ArmorModuleSettings.Trigger.values().length||minimum<0||minimum>95||minimum%5!=0
                            ||row.contains("Priority")&&(!row.contains("Priority",Tag.TAG_INT)
                                ||row.getInt("Priority")<2||row.getInt("Priority")>6))return false;
                }
            }
        }
        return true;
    }
    public static boolean pay(ItemStack chest,int amount) {
        return ArmorProgression.validPayment(amount) && spend(chest,amount);
    }
    /** Paid trigger costs are separate from the 900 CuE/t scheduler ceiling. */
    public static boolean payEvent(ItemStack chest,int amount) {
        return amount>0 && amount<=10000 && spend(chest,amount);
    }
    private static boolean spend(ItemStack chest,int amount) {
        ItemStack cell=battery(chest);
        int available=CopperBatteryItem.stored(cell);
        if(available<amount)return false;
        CopperBatteryItem.stored(cell,available-amount);
        setBattery(chest,cell);
        return true;
    }
}
