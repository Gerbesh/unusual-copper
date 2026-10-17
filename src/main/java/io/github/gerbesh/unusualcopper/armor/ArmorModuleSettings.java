package io.github.gerbesh.unusualcopper.armor;

/** Pure persisted preferences. Natural module restrictions and payment still apply. */
public record ArmorModuleSettings(boolean enabled,Trigger trigger,int minimumPercent,boolean armed) {
    public enum Trigger { AUTO,UNDERWATER,AIRBORNE,ON_FIRE,IN_COMBAT,LOW_HEALTH,MINING,MANUAL }
    public ArmorModuleSettings {
        if(trigger==null||minimumPercent<0||minimumPercent>95||minimumPercent%5!=0)throw new IllegalArgumentException("Module settings");
    }
    public boolean permits(int percent,int serverConditions){
        return enabled&&percent>=minimumPercent&&(trigger==Trigger.AUTO
                ||trigger==Trigger.MANUAL&&armed
                ||trigger!=Trigger.MANUAL&&(serverConditions&1<<trigger.ordinal())!=0);
    }
    public static ArmorModuleSettings invalid(){return new ArmorModuleSettings(false,Trigger.AUTO,95,false);}
}
