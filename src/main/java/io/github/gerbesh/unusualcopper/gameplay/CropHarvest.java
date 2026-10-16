package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.*;

/** Explicit vanilla single-block harvests; one real planting item is reserved per replanted crop. */
public final class CropHarvest {
    public record Plan(BlockState replacement,Item seed) {}
    private record Context(UUID owner,WorkArea area,BlockPos storage,Direction side) {}
    public static Plan plan(BlockState state) {
        var type=state.getBlock().getClass();
        if(type==CropBlock.class || type==CarrotBlock.class || type==PotatoBlock.class || type==BeetrootBlock.class) {
            var crop=(CropBlock)state.getBlock();if(!crop.isMaxAge(state))return null;
            Item seed=type==CarrotBlock.class?Items.CARROT:type==PotatoBlock.class?Items.POTATO:type==BeetrootBlock.class?Items.BEETROOT_SEEDS:Items.WHEAT_SEEDS;
            return new Plan(crop.getStateForAge(0),seed);
        }
        if(type==NetherWartBlock.class && state.getValue(NetherWartBlock.AGE)==3)return new Plan(state.setValue(NetherWartBlock.AGE,0),Items.NETHER_WART);
        if(type==CocoaBlock.class && state.getValue(CocoaBlock.AGE)==2)return new Plan(state.setValue(CocoaBlock.AGE,0),Items.COCOA_BEANS);
        if(type==SweetBerryBushBlock.class && state.getValue(SweetBerryBushBlock.AGE)==3)return new Plan(state.setValue(SweetBerryBushBlock.AGE,1),null);
        if((type==CaveVinesBlock.class || type==CaveVinesPlantBlock.class) && state.getValue(CaveVines.BERRIES))return new Plan(state.setValue(CaveVines.BERRIES,false),null);
        return null;
    }
    private static boolean valid(CopperMachineBlockEntity m,ServerLevel level,BlockPos pos,BlockState before,Context context) {
        return !m.isRemoved() && level.getBlockEntity(m.getBlockPos())==m && m.growthMode==2 && m.energyEnabled()
                && Objects.equals(m.owner(),context.owner()) && Objects.equals(m.workArea(),context.area())
                && Objects.equals(m.boundInventory(),context.storage()) && m.boundSide()==context.side()
                && m.workArea()!=null && m.workArea().contains(pos) && level.hasChunkAt(pos) && level.hasChunkAt(pos.below())
                && ProtectionPolicy.allowed(level,m.getBlockPos(),m.owner()) && ProtectionPolicy.allowed(level,pos,m.owner())
                && ProtectionPolicy.allowed(level,pos.below(),m.owner()) && level.getBlockState(pos).equals(before);
    }
    public static MachineStatus harvest(CopperMachineBlockEntity m,ServerLevel level,BlockPos pos,BlockState before) {
        var plan=plan(before);if(plan==null)return MachineStatus.UNSUPPORTED;
        var context=new Context(m.owner(),m.workArea(),m.boundInventory(),m.boundSide());
        if(!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))return MachineStatus.NO_GROWTH;
        if(!valid(m,level,pos,before,context) || !plan.replacement().canSurvive(level,pos))return MachineStatus.PROTECTED;
        if(!m.harvest.empty() || m.boundCargoCount()>0)return MachineStatus.OUTPUT_FULL;
        if(m.boundInventory()==null)return MachineStatus.NO_STORAGE;
        var endpoint=m.boundEndpoint(level);if(!endpoint.isAvailable())return MachineStatus.UNLOADED;
        if(endpoint.space()!=io.github.gerbesh.unusualcopper.storage.StorageEndpoint.Space.AVAILABLE)return MachineStatus.OUTPUT_FULL;
        int cost=ServerConfig.cultivatorHarvestCost();if(m.energy.stored()<cost)return MachineStatus.NO_ENERGY;
        var actor=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(m.owner()==null?new UUID(0,3):m.owner(),"[CuECultivator]"));
        var hand=actor.getMainHandItem();var oldPos=actor.position();boolean creative=actor.getAbilities().instabuild;
        actor.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.getAbilities().instabuild=false;
        try {
            if(!level.mayInteract(actor,pos) || !level.mayInteract(actor,pos.below()))return MachineStatus.PROTECTED;
            if(ForgeHooks.onBlockBreakEvent(level,GameType.SURVIVAL,actor,pos)<0)return MachineStatus.BREAK_DENIED;
            if(ForgeEventFactory.onBlockPlace(actor,BlockSnapshot.create(level.dimension(),level,pos),Direction.UP))return MachineStatus.PLANT_DENIED;
            if(!valid(m,level,pos,before,context))return MachineStatus.PROTECTED;
            List<ItemStack> drops;
            if(before.getBlock() instanceof SweetBerryBushBlock)drops=new ArrayList<>(List.of(new ItemStack(Items.SWEET_BERRIES,3+level.random.nextInt(2))));
            else if(before.getBlock() instanceof CaveVines)drops=new ArrayList<>(List.of(new ItemStack(Items.GLOW_BERRIES)));
            else drops=new ArrayList<>(Block.getDrops(before,level,pos,null,actor,ItemStack.EMPTY).stream().map(ItemStack::copy).toList());
            if(drops.size()>64 || drops.stream().anyMatch(s->s.getCount()>64*s.getMaxStackSize()))return MachineStatus.OUTPUT_FULL;
            ItemStack external=ItemStack.EMPTY;
            if(plan.seed()!=null) {
                boolean reserved=false;
                for(var stack:drops)if(stack.is(plan.seed()) && !stack.isEmpty()){stack.shrink(1);reserved=true;break;}
                if(!reserved) {
                    var view=endpoint.snapshot(stack->stack.is(plan.seed()),32);
                    for(var entry:view.entries()) {
                        if(endpoint.simulateExtract(entry.key(),1)<1)continue;
                        var actual=endpoint.extract(entry.key(),1);
                        for(var stack:actual)m.harvest.retain(stack);m.setChanged();
                        if(actual.size()!=1 || actual.get(0).getCount()!=1 || !entry.key().matches(actual.get(0)))return actual.isEmpty()?MachineStatus.NEED_SEEDS:MachineStatus.OUTPUT_FULL;
                        external=actual.get(0).copy();reserved=true;break;
                    }
                }
                if(!reserved)return MachineStatus.NEED_SEEDS;
            }
            if(!valid(m,level,pos,before,context) || !plan.replacement().canSurvive(level,pos))return MachineStatus.PROTECTED;
            if(!m.energy.spend(cost))return MachineStatus.NO_ENERGY;
            if(!level.setBlock(pos,plan.replacement(),3)){m.energy.refundSpent(cost);return MachineStatus.PLANT_DENIED;}
            if(!external.isEmpty())m.harvest.consumeOne(external);
            for(var stack:drops)m.harvest.retain(stack);
            int icon=drops.stream().filter(stack->!stack.isEmpty() && (plan.seed()==null || !stack.is(plan.seed()))).findFirst()
                    .or(()->drops.stream().filter(stack->!stack.isEmpty()).findFirst())
                    .map(stack->net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem())).orElse(-1);
            m.cultivation.changed(pos,icon);
            m.harvested++;m.setChanged();m.harvest.drain(m,8);
            return MachineStatus.WORKING;
        } finally {actor.setItemInHand(InteractionHand.MAIN_HAND,hand);actor.setPos(oldPos);actor.getAbilities().instabuild=creative;}
    }
    private CropHarvest() {}
}
