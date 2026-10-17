package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorModuleSettingsTest {
    @Test void disabledAndChargeThresholdAlwaysApply(){
        assertFalse(new ArmorModuleSettings(false,ArmorModuleSettings.Trigger.AUTO,0,true).permits(100,~0));
        var settings=new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.UNDERWATER,25,false);
        int water=1<<ArmorModuleSettings.Trigger.UNDERWATER.ordinal();
        assertFalse(settings.permits(24,water));assertFalse(settings.permits(100,0));assertTrue(settings.permits(25,water));
    }
    @Test void manualNeedsExplicitArmingRatherThanAnotherTriggerBit(){
        assertFalse(new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.MANUAL,0,false).permits(100,~0));
        assertTrue(new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.MANUAL,0,true).permits(100,0));
        assertFalse(ArmorModuleSettings.invalid().permits(100,~0));
    }
    @Test void malformedSettingsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new ArmorModuleSettings(true,null,0,false));
        for(int value:new int[]{-5,1,99,100,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.AUTO,value,false));
    }
    @Test void telemetryCountsPaidEventsAndExpiresWithoutExtrapolating(){
        var meter=new ArmorEnergyHistory();meter.record(10,2000,1);meter.record(10,5,2);meter.record(11,80,3);
        assertEquals(2085,meter.perSecond(11));assertEquals(14,meter.active(11));
        assertEquals(80,meter.perSecond(30));assertEquals(0,meter.perSecond(31));
        meter.record(50,8,2);assertEquals(0,meter.perSecond(1));
    }
}
