package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.world.entity.EquipmentSlot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Pure, deterministic per-tick arbitration of the physical chest battery.
 * Event payments (jump, impact, shield hit, mining) are separate transactions.
 * No effects may be applied until the complete accepted tick cost is paid.
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
    public record Plan(List<ArmorModuleType> accepted,List<ArmorModuleType> limited,int cost) {
        public Plan {
            accepted=List.copyOf(accepted);
            limited=List.copyOf(limited);
        }
    }
    public static int defaultPriority(ArmorModuleType module) {
        return switch(module) {
            case FLIGHT_CORE_II,HOVER_DRIVE -> 2;
            case AQUA_LUNGS,SHIELD_FIELD,FALL_DAMPER -> 3;
            case STRIDE_DRIVE,JUMP_DRIVE -> 4;
            case MINING_SERVO -> 5;
            case OPTIC_VISOR -> 6;
        };
    }
    public static Plan plan(Collection<Demand> requests,int available) {
        if(available<0)throw new IllegalArgumentException("Negative battery energy");
        var sorted=new ArrayList<>(requests);
        sorted.sort(Comparator.comparingInt(Demand::priority)
                .thenComparingInt(d->d.module()==ArmorModuleType.FLIGHT_CORE_II?-1:d.module().ordinal()));
        var seen=EnumSet.noneOf(ArmorModuleType.class);
        var consumed=new EnumMap<EquipmentSlot,Integer>(EquipmentSlot.class);
        var accepted=new ArrayList<ArmorModuleType>();
        var limited=new ArrayList<ArmorModuleType>();
        int total=0;
        for(var request:sorted) {
            if(!seen.add(request.module()))throw new IllegalArgumentException("Duplicate module power request");
            EquipmentSlot slot=request.module().slot;
            int cap=ArmorProgression.pieceBudget(slot==EquipmentSlot.CHEST,slot==EquipmentSlot.FEET,slot==EquipmentSlot.HEAD);
            int occupied=consumed.getOrDefault(slot,0);
            int cost=request.cue();
            if(cost>cap-occupied || cost>ArmorProgression.totalBudget()-total || cost>available-total) {
                limited.add(request.module());
                continue;
            }
            total+=cost;
            consumed.put(slot,occupied+cost);
            accepted.add(request.module());
        }
        return new Plan(accepted,limited,total);
    }
}
