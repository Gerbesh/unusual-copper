package io.github.gerbesh.unusualcopper.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.machine.SideMode;
import java.util.*;

/** Owner-only DTO: no inventories, NBT, entity IDs or arbitrary client scan coordinates. */
public record ArmorSenseSnapshot(String dimension,int profile,int visor,int scanner,int conditions,
                                 ScanState scan,int checked,int total,List<BlockPos> ores,Target target,DiagnosticState diagnostic){
    public static final int MAX_ORES=48,LEASE_TICKS=15;
    public enum ScanState { OFF,SEARCHING,READY,DISABLED,PARTIAL }
    public enum DiagnosticState { OFF,AIM,READY,PROTECTED,UNLOADED,BUDGET,UNSUPPORTED }
    public record Target(BlockPos pos,String block,int energy,int capacity,int status,int ports){
        public Target{
            pos=Objects.requireNonNull(pos).immutable();
            if(!position(pos)||block.length()>128||ResourceLocation.tryParse(block)==null
                    ||energy<0||capacity<energy||capacity>250_000_000||status< -1||status>=MachineStatus.values().length
                    ||ports<0||ports>>>18!=0||status==-1&&(energy!=0||capacity!=0||ports!=0))throw new IllegalArgumentException("Diagnostic target");
            for(int i=0;i<6;i++)if((ports>>>(i*3)&7)>=SideMode.values().length)throw new IllegalArgumentException("Port mode");
        }
    }
    public ArmorSenseSnapshot {
        Objects.requireNonNull(dimension);Objects.requireNonNull(scan);Objects.requireNonNull(diagnostic);ores=ores.stream().map(BlockPos::immutable).toList();
        if(dimension.length()>128||ResourceLocation.tryParse(dimension)==null||profile<0||profile>3
                ||visor<0||visor>2||scanner<0||scanner>2||conditions<0||conditions>255||checked<0||checked>total||total<0||total>2200
                ||ores.size()>MAX_ORES||new HashSet<>(ores).size()!=ores.size()||ores.stream().anyMatch(p->!position(p))
                ||visor<2&&(!ores.isEmpty()||scan!=ScanState.OFF||checked!=0||total!=0)
                ||scanner==0&&(target!=null||diagnostic!=DiagnosticState.OFF)||scanner>0&&diagnostic==DiagnosticState.OFF
                ||(diagnostic==DiagnosticState.READY)!=(target!=null)||scanner<2&&target!=null&&target.ports()!=0)
            throw new IllegalArgumentException("Armor senses");
    }
    public ArmorSenseSnapshot(String dimension,int profile,int visor,int scanner,int conditions,ScanState scan,int checked,int total,List<BlockPos> ores,Target target){
        this(dimension,profile,visor,scanner,conditions,scan,checked,total,ores,target,scanner==0?DiagnosticState.OFF:target==null?DiagnosticState.AIM:DiagnosticState.READY);
    }
    private static boolean position(BlockPos p){return Math.abs((long)p.getX())<=30_000_000&&Math.abs((long)p.getZ())<=30_000_000&&p.getY()>=-2048&&p.getY()<=2047;}
    public static final class Codec {
        public static void encode(ArmorSenseSnapshot v,FriendlyByteBuf b){
            b.writeUtf(v.dimension,128);b.writeVarInt(v.profile);b.writeVarInt(v.visor);b.writeVarInt(v.scanner);b.writeVarInt(v.conditions);
            b.writeEnum(v.scan);b.writeEnum(v.diagnostic);b.writeVarInt(v.checked);b.writeVarInt(v.total);b.writeVarInt(v.ores.size());
            for(var pos:v.ores)b.writeBlockPos(pos);
            b.writeBoolean(v.target!=null);if(v.target!=null){var t=v.target;b.writeBlockPos(t.pos);b.writeUtf(t.block,128);b.writeVarInt(t.energy);b.writeVarInt(t.capacity);b.writeVarInt(t.status);b.writeVarInt(t.ports);}
        }
        public static ArmorSenseSnapshot decode(FriendlyByteBuf b){
            String dim=b.readUtf(128);int profile=b.readVarInt(),visor=b.readVarInt(),scanner=b.readVarInt(),conditions=b.readVarInt();
            var scan=b.readEnum(ScanState.class);var diagnostic=b.readEnum(DiagnosticState.class);int checked=b.readVarInt(),total=b.readVarInt(),n=b.readVarInt();
            if(n<0||n>MAX_ORES)throw new IllegalArgumentException("Ore list bound");
            var ores=new ArrayList<BlockPos>(n);for(int i=0;i<n;i++)ores.add(b.readBlockPos());
            Target target=b.readBoolean()?new Target(b.readBlockPos(),b.readUtf(128),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()):null;
            return new ArmorSenseSnapshot(dim,profile,visor,scanner,conditions,scan,checked,total,ores,target,diagnostic);
        }
        private Codec(){}
    }
}
