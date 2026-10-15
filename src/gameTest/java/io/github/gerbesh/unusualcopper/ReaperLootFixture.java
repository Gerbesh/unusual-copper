package io.github.gerbesh.unusualcopper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
import java.util.*;

@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ReaperLootFixture {
    public static final class Probe { public boolean seen, player; public int looting, stone; }
    public static final Map<UUID, Probe> PROBES = new HashMap<>();
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, new ResourceLocation(UnusualCopper.MOD_ID, "fixture_reaper_modifier"), () -> Modifier.CODEC);
    }
    public static final class Modifier extends LootModifier {
        static final Codec<Modifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, Modifier::new));
        Modifier(LootItemCondition[] conditions) { super(conditions); }
        @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            var entity = context.getParamOrNull(LootContextParams.THIS_ENTITY); var probe = entity == null ? null : PROBES.get(entity.getUUID());
            if (probe != null) {
                probe.seen = true; probe.player = context.hasParam(LootContextParams.LAST_DAMAGE_PLAYER); probe.looting = context.getLootingModifier();
                var bonus = new ItemStack(Items.EMERALD, 5); bonus.setHoverName(net.minecraft.network.chat.Component.literal("Real GLM bonus")); loot.add(bonus);
                if (probe.stone > 0) loot.add(new ItemStack(Items.STONE, probe.stone));
            }
            return loot;
        }
        @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
    }
}
