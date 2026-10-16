package io.github.gerbesh.unusualcopper.storage;

import io.github.gerbesh.unusualcopper.compat.ssn.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Predicate;

/** Selects the loaded target kind on every operation; a temporary unload never fixes the wrong adapter forever. */
final class RoutedStorageEndpoint implements StorageEndpoint {
    private final ServerLevel level;private final BlockPos pos;private final Direction side;private final UUID actor;
    private StorageEndpoint delegate;private StorageIdentity previousIdentity;
    RoutedStorageEndpoint(ServerLevel level,BlockPos pos,Direction side,UUID actor,BlockPos identityHint){this.level=level;this.pos=pos.immutable();this.side=side;this.actor=actor;if(identityHint!=null)previousIdentity=new StorageIdentity(level.dimension(),identityHint,side);}
    private StorageEndpoint resolve(){
        if(!level.hasChunkAt(pos))return null;boolean bridge=level.getBlockEntity(pos) instanceof SsnBridgeBlockEntity;
        if(delegate==null||bridge!=(delegate instanceof SsnStorage))delegate=bridge?new SsnStorage(level,pos,actor):new HandlerStorageEndpoint(level,pos,side,actor);
        return delegate;
    }
    @Override public StorageIdentity identity(){var current=resolve();if(current!=null){var id=current.identity();if(!(current instanceof SsnStorage)||!id.pos().equals(pos)||previousIdentity==null)previousIdentity=id;}return previousIdentity==null?new StorageIdentity(level.dimension(),pos,side):previousIdentity;}
    @Override public boolean isAvailable(){var current=resolve();return current!=null&&current.isAvailable();}
    @Override public View snapshot(Predicate<ItemStack> filter,int limit){var current=resolve();return current==null?new View(List.of(),0,false):current.snapshot(filter,limit);}
    @Override public int simulateExtract(ItemKey key,int amount){var current=resolve();return current==null?0:current.simulateExtract(key,amount);}
    @Override public List<ItemStack> extract(ItemKey key,int amount){var current=resolve();return current==null?List.of():current.extract(key,amount);}
    @Override public ItemStack insert(ItemStack stack,boolean simulate){var current=resolve();return current==null?stack.copy():current.insert(stack,simulate);}
    @Override public Count count(ItemKey key){var current=resolve();return current==null?new Count(0,false):current.count(key);}
    @Override public Space space(){var current=resolve();return current==null?Space.UNAVAILABLE:current.space();}
    @Override public Count countItem(net.minecraft.world.item.Item item,Predicate<ItemStack> accepted){var current=resolve();return current==null?new Count(0,false):current.countItem(item,accepted);}

}
