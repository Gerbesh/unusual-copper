package io.github.gerbesh.unusualcopper.armor;

import java.util.UUID;

/** Bounds and optimistic concurrency for configuration intentions, not item/energy input. */
public final class ArmorControlPolicy {
    public enum Operation { PROFILE,ENABLE,TRIGGER,MINIMUM,ARM,PRIORITY }
    private ArmorControlPolicy(){}
    public static boolean current(UUID expected,long revision,UUID supplied,long expectedRevision){
        return expected!=null&&expected.equals(supplied)&&revision>=0&&revision==expectedRevision;
    }
    public static boolean valid(Operation op,int piece,int module,int value,int moduleCount){
        if(op==null||piece<0||piece>3)return false;
        if(op==Operation.PROFILE)return piece==1&&module==-1&&value>=0&&value<4;
        if(module<0||module>=moduleCount)return false;
        return switch(op){
            case ENABLE,ARM -> value==0||value==1;
            case TRIGGER -> value>=0&&value<ArmorModuleSettings.Trigger.values().length;
            case MINIMUM -> value>=0&&value<=95&&value%5==0;
            case PRIORITY -> value>=2&&value<=6;
            default -> false;
        };
    }
}
