package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.storage.ItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Bounded BFS of actual vanilla transitions, never a table of potion IDs. */
public final class PotionPlans {
    public static final int MAX_STEPS=8,MAX_OPTIONS=512;
    public record Step(ItemStack ingredient,ItemStack output) {
        public Step { ingredient=ingredient.copyWithCount(1); output=output.copyWithCount(1); }
        @Override public ItemStack ingredient() { return ingredient.copy(); }
        @Override public ItemStack output() { return output.copy(); }
    }
    public record Plan(ResourceLocation potion,int vessel,ItemStack output,List<Step> steps) {
        public Plan { output=output.copyWithCount(1); steps=List.copyOf(steps); }
        @Override public ItemStack output() { return output.copy(); }
        public int stages() { return Math.max(1,steps.size()); }
    }
    private static volatile List<Plan> cache;
    public static ItemStack water() { return PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER); }
    public static int vessel(ItemStack stack) { return stack.is(Items.POTION)?0:stack.is(Items.SPLASH_POTION)?1:stack.is(Items.LINGERING_POTION)?2:-1; }
    public static synchronized List<Plan> catalogue() {
        if(cache!=null) return cache;
        var ingredients=BuiltInRegistries.ITEM.stream().filter(item->BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")).map(ItemStack::new).filter(PotionBrewing::isIngredient).sorted(Comparator.comparing(s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString())).toList();
        if(ingredients.size()>64) throw new IllegalStateException("Brewing ingredient adapter exceeded its bound");
        var seen=new LinkedHashMap<ItemKey,Plan>(); var queue=new ArrayDeque<Plan>();
        var seed=new Plan(BuiltInRegistries.POTION.getKey(Potions.WATER),0,water(),List.of()); seen.put(ItemKey.of(seed.output()),seed); queue.add(seed);
        while(!queue.isEmpty()) {
            var plan=queue.removeFirst(); if(plan.steps.size()>=MAX_STEPS) continue;
            for(var ingredient:ingredients) {
                var input=plan.output(); if(!PotionBrewing.hasMix(input,ingredient)) continue;
                var output=PotionBrewing.mix(ingredient.copy(),input.copy());
                if(output.isEmpty()||vessel(output)<0||PotionUtils.getPotion(output)==Potions.EMPTY||ItemKey.of(input).matches(output)||seen.containsKey(ItemKey.of(output))) continue;
                var path=new ArrayList<>(plan.steps); path.add(new Step(ingredient,output));
                var next=new Plan(BuiltInRegistries.POTION.getKey(PotionUtils.getPotion(output)),vessel(output),output,path);
                if(seen.size()>=MAX_OPTIONS) throw new IllegalStateException("Brewing graph adapter exceeded its bound"); seen.put(ItemKey.of(output),next); queue.addLast(next);
            }
        }
        cache=seen.values().stream().sorted(Comparator.comparing((Plan p)->p.potion.toString()).thenComparingInt(Plan::vessel)).toList(); return cache;
    }
    public static Plan find(ResourceLocation potion,int vessel) { return catalogue().stream().filter(p->p.potion.equals(potion)&&p.vessel==vessel).findFirst().orElse(null); }
    @SubscribeEvent public static void reload(TagsUpdatedEvent event) { cache=null; }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { cache=null; }
    private PotionPlans() {}
}
