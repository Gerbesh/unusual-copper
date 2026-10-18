package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.world.entity.EquipmentSlot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * Pure, deterministic per-tick arbitration of the physical chest battery.
 * Earlier reactive allocations occupy the same tick's bus without being billed twice.
 * Additional event prices (jump, shield damage, healing) share the durable payment batch.
 * No effects may be applied until the complete accepted tick cost is reserved.
 */
public final class ArmorPowerScheduler {
    private ArmorPowerScheduler() {}

    public record Demand(ArmorModuleType module,int priority,int cue) {
        public Demand {
            Objects.requireNonNull(module);
            if(priority<2 || priority>6 || cue<=0 || cue>ArmorProgression.totalBudget())
                throw new IllegalArgumentException("Invalid armor power request");
        }
    }
    public record Plan(List<ArmorModuleType> accepted,List<ArmorModuleType> limited,int cost,Map<ArmorModuleType,Integer> charges) {
        public Plan {
            accepted=List.copyOf(accepted);
            limited=List.copyOf(limited);
            charges=Map.copyOf(charges);
            var expected=EnumSet.noneOf(ArmorModuleType.class);expected.addAll(accepted);
            if(cost<0||cost>ArmorProgression.totalBudget()||expected.size()!=accepted.size()||!charges.keySet().equals(expected)||charges.values().stream().anyMatch(n->n<=0)
                    ||charges.values().stream().mapToLong(Integer::longValue).sum()!=cost)
                throw new IllegalArgumentException("Incomplete power quote");
        }
    }
    public static int defaultPriority(ArmorModuleType module) {
        return switch(module) {
            case FLIGHT_CORE_II,HOVER_DRIVE -> 2;
            case AQUA_LUNGS,SHIELD_FIELD,FALL_DAMPER,THERMAL_SHELL,REGEN_CORE -> 3;
            case STRIDE_DRIVE,JUMP_DRIVE,KINETIC_STABILIZER -> 4;
            case MINING_SERVO,STRENGTH_SERVO -> 5;
            case OPTIC_VISOR,FIELD_SCANNER -> 6;
        };
    }
    public static Plan plan(Collection<Demand> requests,int available) {
        return plan(requests,available,List.of());
    }
    /** Earlier on-demand continuous work (mining) still occupies this tick's power bus. */
    public static Plan plan(Collection<Demand> requests,int available,Collection<Demand> committed) {
        if(available<0)throw new IllegalArgumentException("Negative battery energy");
        var sorted=new ArrayList<>(requests);
        sorted.sort(Comparator.comparingInt(Demand::priority)
                .thenComparingInt(d->d.module()==ArmorModuleType.FLIGHT_CORE_II?-1:d.module().ordinal()));
        var seen=EnumSet.noneOf(ArmorModuleType.class);
        var consumed=new EnumMap<EquipmentSlot,Integer>(EquipmentSlot.class);
        var accepted=new ArrayList<ArmorModuleType>();
        var limited=new ArrayList<ArmorModuleType>();
        var charges=new EnumMap<ArmorModuleType,Integer>(ArmorModuleType.class);
        int total=0,alreadyPaid=0;
        for(var request:committed){
            if(!seen.add(request.module()))throw new IllegalArgumentException("Duplicate committed module");
            var slot=request.module().slot;int cap=ArmorProgression.pieceBudget(slot==EquipmentSlot.CHEST,slot==EquipmentSlot.FEET,slot==EquipmentSlot.HEAD);
            int occupied=consumed.getOrDefault(slot,0);
            if(request.cue()>cap-occupied||request.cue()>ArmorProgression.totalBudget()-total)
                throw new IllegalArgumentException("Invalid committed power");
            total+=request.cue();consumed.put(slot,occupied+request.cue());
        }
        alreadyPaid=total;
        for(var request:sorted) {
            if(!seen.add(request.module()))throw new IllegalArgumentException("Duplicate module power request");
            EquipmentSlot slot=request.module().slot;
            int cap=ArmorProgression.pieceBudget(slot==EquipmentSlot.CHEST,slot==EquipmentSlot.FEET,slot==EquipmentSlot.HEAD);
            int occupied=consumed.getOrDefault(slot,0);
            int cost=request.cue();
            if(cost>cap-occupied || cost>ArmorProgression.totalBudget()-total || cost>available-(total-alreadyPaid)) {
                limited.add(request.module());
                continue;
            }
            total+=cost;
            consumed.put(slot,occupied+cost);
            accepted.add(request.module());
            charges.put(request.module(),cost);
        }
        return new Plan(accepted,limited,total-alreadyPaid,charges);
    }
}
