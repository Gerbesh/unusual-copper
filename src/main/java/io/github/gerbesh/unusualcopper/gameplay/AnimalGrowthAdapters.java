package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.monster.Strider;
import java.util.*;
import java.util.function.*;

/** Exact entity type and implementation allowlist. Other age APIs require explicit registration. */
public final class AnimalGrowthAdapters {
    public interface Adapter {
        boolean supports(AgeableMob animal);
        int age(AgeableMob animal);
        void setAge(AgeableMob animal, int age);
    }
    private static final Map<EntityType<?>, Adapter> ADAPTERS = new IdentityHashMap<>();
    static {
        normal(EntityType.COW, Cow.class); normal(EntityType.MOOSHROOM, MushroomCow.class);
        normal(EntityType.PIG, Pig.class); normal(EntityType.SHEEP, Sheep.class); normal(EntityType.CHICKEN, Chicken.class);
        normal(EntityType.RABBIT, Rabbit.class); normal(EntityType.GOAT, Goat.class); normal(EntityType.BEE, Bee.class);
        normal(EntityType.FOX, Fox.class); normal(EntityType.PANDA, Panda.class); normal(EntityType.OCELOT, Ocelot.class);
        normal(EntityType.TURTLE, Turtle.class); normal(EntityType.AXOLOTL, Axolotl.class); normal(EntityType.POLAR_BEAR, PolarBear.class);
        normal(EntityType.WOLF, Wolf.class); normal(EntityType.CAT, Cat.class);
        normal(EntityType.HORSE, Horse.class); normal(EntityType.DONKEY, Donkey.class); normal(EntityType.MULE, Mule.class); normal(EntityType.LLAMA, Llama.class);
        normal(EntityType.CAMEL, net.minecraft.world.entity.animal.camel.Camel.class);
        normal(EntityType.SNIFFER, net.minecraft.world.entity.animal.sniffer.Sniffer.class);
        normal(EntityType.STRIDER, Strider.class); normal(EntityType.HOGLIN, net.minecraft.world.entity.monster.hoglin.Hoglin.class);
    }
    private AnimalGrowthAdapters() {}
    private static <T extends AgeableMob> void normal(EntityType<T> type, Class<T> implementation) { register(type, implementation, AgeableMob::getAge, AgeableMob::setAge); }
    /** Register during common setup on the server thread; adapters must preserve cooldowns and normal adulthood hooks. */
    public static synchronized <T extends AgeableMob> void register(EntityType<T> type, Class<T> implementation, ToIntFunction<T> readAge, ObjIntConsumer<T> writeAge) {
        Objects.requireNonNull(type); Objects.requireNonNull(implementation); Objects.requireNonNull(readAge); Objects.requireNonNull(writeAge);
        if (ADAPTERS.containsKey(type)) throw new IllegalArgumentException("Growth adapter already registered");
        ADAPTERS.put(type, new Adapter() {
            @Override public boolean supports(AgeableMob animal) { return animal.getClass() == implementation; }
            @Override public int age(AgeableMob animal) { return readAge.applyAsInt(implementation.cast(animal)); }
            @Override public void setAge(AgeableMob animal, int age) { writeAge.accept(implementation.cast(animal), age); }
        });
    }
    public static synchronized Adapter adapter(AgeableMob animal) {
        var adapter = ADAPTERS.get(animal.getType()); return adapter != null && adapter.supports(animal) ? adapter : null;
    }
    public static boolean mayAffect(AgeableMob animal, UUID owner, boolean allowOwnTamed) {
        if (animal instanceof TamableAnimal pet && pet.isTame()) return allowOwnTamed && owner != null && owner.equals(pet.getOwnerUUID());
        if (animal instanceof AbstractHorse horse && horse.isTamed()) {
            // Vanilla Camel.isTamed() always returns true, including unowned wild calves.
            if (animal.getClass() == net.minecraft.world.entity.animal.camel.Camel.class && horse.getOwnerUUID() == null) return true;
            return allowOwnTamed && owner != null && owner.equals(horse.getOwnerUUID());
        }
        return true;
    }
}
