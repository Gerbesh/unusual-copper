package io.github.gerbesh.unusualcopper.fluid.tank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Public visual metadata only. This is not a second persisted reservoir. */
public record TankView(int amount,int blocks,int connections,int corners,boolean controller,float fill,String fluid,State state,java.util.UUID identity,long shape,long revision,double surface){
    public enum State { READY,LIMIT,PROTECTED }
    public static final TankView EMPTY=new TankView(0,1,0,15,true,0,"minecraft:empty",State.READY,new java.util.UUID(0,0),0,0,0);
    public TankView {
        if(amount<0||blocks<1||blocks>TankTopology.MAX_BLOCKS||connections<0||connections>63||corners<0||corners>15
                ||!Float.isFinite(fill)||fill<0||fill>1||fluid==null||fluid.length()>128
                ||ResourceLocation.tryParse(fluid)==null||state==null||identity==null||revision<0||!Double.isFinite(surface)||surface< -4096||surface>4096)throw new IllegalArgumentException("Tank view");
    }
    public int nominalCapacity(){return TankVolumes.capacity(blocks);}
    public int capacity(){return Math.max(nominalCapacity(),amount);}
    public boolean legacyOverflow(){return amount>nominalCapacity();}
    public void write(CompoundTag parent){
        var tag=new CompoundTag();tag.putInt("Amount",amount);tag.putInt("Blocks",blocks);tag.putInt("Connections",connections);
        tag.putFloat("Fill",fill);tag.putString("Fluid",fluid);tag.putInt("State",state.ordinal());parent.put("UCFluidTankView",tag);
        tag.putLong("Shape",shape);tag.putLong("Revision",revision);tag.putDouble("Surface",surface);
        tag.putInt("Corners",corners);tag.putBoolean("Controller",controller);
        tag.putUUID("Identity",identity);
    }
    public static TankView read(CompoundTag parent){
        var tag=parent.getCompound("UCFluidTankView");
        if(!parent.contains("UCFluidTankView",net.minecraft.nbt.Tag.TAG_COMPOUND))return EMPTY;
        try{int state=tag.getInt("State");if(state<0||state>=State.values().length||!tag.hasUUID("Identity"))return EMPTY;
            return new TankView(tag.getInt("Amount"),tag.getInt("Blocks"),tag.getInt("Connections"),tag.getInt("Corners"),tag.getBoolean("Controller"),tag.getFloat("Fill"),tag.getString("Fluid"),State.values()[state],tag.getUUID("Identity"),tag.getLong("Shape"),tag.getLong("Revision"),tag.getDouble("Surface"));
        }catch(IllegalArgumentException malformed){return EMPTY;}
    }
}
