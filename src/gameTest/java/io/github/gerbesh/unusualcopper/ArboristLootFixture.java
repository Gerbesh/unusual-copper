package io.github.gerbesh.unusualcopper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
import java.util.*;

@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ArboristLootFixture {
    public static final Map<BlockPos,Probe> PROBES=new HashMap<>();
    public static final class Probe { public int calls; public boolean silk=true,removed=true; public int stone; }
    @SubscribeEvent public static void register(RegisterEvent event) { event.register(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,new ResourceLocation(UnusualCopper.MOD_ID,"fixture_arborist_modifier"),()->Modifier.CODEC); }
    public static final class Modifier extends LootModifier {
        static final Codec<Modifier> CODEC=RecordCodecBuilder.create(instance->codecStart(instance).apply(instance,Modifier::new));
        Modifier(LootItemCondition[] conditions) { super(conditions); }
        @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
        @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context) {
            var origin=context.getParamOrNull(LootContextParams.ORIGIN); var probe=origin==null?null:PROBES.get(BlockPos.containing(origin));
            if(probe!=null && context.hasParam(LootContextParams.BLOCK_STATE)) {
                probe.calls++; var tool=context.getParam(LootContextParams.TOOL); probe.silk &= tool.getEnchantmentLevel(Enchantments.SILK_TOUCH)==1;
                probe.removed &= context.getLevel().getBlockState(BlockPos.containing(origin)).isAir();
                var bonus=new ItemStack(Items.EMERALD,3); bonus.setHoverName(net.minecraft.network.chat.Component.literal("Actual forestry GLM")); loot.add(bonus);
                if(probe.stone>0) loot.add(new ItemStack(Items.STONE,probe.stone));
            }
            return loot;
        }
    }
}
