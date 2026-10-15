package io.github.gerbesh.unusualcopper.compat.ssn;

import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class SsnBridgeBlockEntity extends BlockEntity {
    private UUID owner;
    private SsnStorage.State state;
    private UUID stateOwner;
    private BlockPos statePort;
    private BlockPos knownMain;
    public int redstoneMode;
    public SsnBridgeBlockEntity(BlockPos pos,BlockState state){super(ModContent.SSN_BRIDGE_ENTITY.get(),pos,state);}
    public UUID owner(){return owner;}
    public BlockPos knownMain(){return knownMain;}
    private void connected(BlockPos main){if(!main.equals(knownMain)){knownMain=main.immutable();setChanged();}}
    public void owner(UUID owner){this.owner=owner;state=null;setChanged();}
    public boolean canAccess(Player player){return owner==null||owner.equals(player.getUUID())||player.hasPermissions(2);}
    public boolean allows(UUID actor){return owner==null||owner.equals(actor);}
    public boolean enabled(){if(level==null)return false;boolean signal=level.hasNeighborSignal(worldPosition);return redstoneMode==0||redstoneMode==1&&signal||redstoneMode==2&&!signal;}
    public SsnStorage storage(UUID actor){return new SsnStorage((ServerLevel)level,worldPosition,actor);}
    SsnStorage.State state(UUID actor){
        var port=worldPosition.relative(getBlockState().getValue(SsnBridgeBlock.FACING));
        if(state==null||!Objects.equals(stateOwner,actor)||!port.equals(statePort)){state=new SsnStorage.State((ServerLevel)level,port,actor,this::connected);stateOwner=actor;statePort=port;}
        return state;
    }
    public void invalidateStorage(){state=null;}
    public static void tick(Level level,BlockPos pos,BlockState block,SsnBridgeBlockEntity bridge){
        if(!(level instanceof ServerLevel server))return;UUID actor=bridge.state==null?bridge.owner:bridge.stateOwner;
        boolean allowed=bridge.enabled()&&io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server,pos,actor);
        if(allowed&&bridge.state!=null)bridge.state.advance();
        if(server.getGameTime()%20==0){boolean connected=allowed&&bridge.storage(actor).isAvailable();if(block.getValue(SsnBridgeBlock.ACTIVE)!=connected)server.setBlock(pos,block.setValue(SsnBridgeBlock.ACTIVE,connected),2);}
    }
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(owner!=null)tag.putUUID("Owner",owner);tag.putInt("Redstone",redstoneMode);if(knownMain()!=null)tag.putLong("KnownMain",knownMain().asLong());}
    @Override public void load(CompoundTag tag){super.load(tag);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;redstoneMode=Math.floorMod(tag.getInt("Redstone"),3);knownMain=tag.contains("KnownMain")?BlockPos.of(tag.getLong("KnownMain")):null;state=null;}
    @Override public CompoundTag getUpdateTag(){return new CompoundTag();}
}
