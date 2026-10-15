package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.item.CargoBundleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Exact identities of genuine loot-table outputs; cancelled world spawns never mint inventory copies. */
public final class ArboristDrops {
    private record Spawn(CopperMachineBlockEntity machine,UUID runtime,ItemEntity item) {}
    private static final Deque<Spawn> ACTIVE=new ArrayDeque<>();
    public static void emit(CopperMachineBlockEntity machine,ServerLevel level,BlockPos pos,List<ItemStack> drops) {
        if(!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS) || level.restoringBlockSnapshots) return;
        for(var stack:drops) {
            if(stack.isEmpty()) continue;
            var item=new ItemEntity(level,pos.getX()+.25+level.random.nextDouble()*.5,pos.getY()+.25,pos.getZ()+.25+level.random.nextDouble()*.5,stack.copy()); item.setDefaultPickUpDelay();
            var action=new Spawn(machine,machine.runtimeIdentity,item); ACTIVE.push(action);
            try { level.addFreshEntity(item); } finally { ACTIVE.remove(action); }
        }
    }
    public static void accepted(Entity entity) {
        if(!(entity instanceof ItemEntity item) || !(entity.level() instanceof ServerLevel level)) return;
        for(var action:ACTIVE) if(action.item()==item) {
            var m=action.machine();
            if(m.isRemoved() || !m.runtimeIdentity.equals(action.runtime()) || !level.hasChunkAt(m.getBlockPos()) || level.getBlockEntity(m.getBlockPos())!=m || !ProtectionPolicy.allowed(level,m.getBlockPos(),m.owner())) {
                if(item.getItem().getCount()>item.getItem().getMaxStackSize()) item.setItem(CargoBundleItem.pack(List.of(item.getItem()))); return;
            }
            var rest=m.arborist.recovery.hold(m.insertOutput(item.getItem()));
            item.setItem(!rest.isEmpty() && rest.getCount()>rest.getMaxStackSize()?CargoBundleItem.pack(List.of(rest)):rest); if(rest.isEmpty()) item.discard(); m.setChanged(); return;
        }
    }
    private ArboristDrops() {}
}
