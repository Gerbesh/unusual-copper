package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArmorModuleLevelsTest {
    private static CompoundTag data(String id,int level){var root=new CompoundTag();var levels=new CompoundTag();levels.putInt(id,level);root.put("ModuleLevels",levels);return root;}
    @Test void legacyArmorKeepsLevelOneWithoutRewritingItsData(){
        var root=new CompoundTag();var before=root.copy();
        assertTrue(ArmorModuleLevels.valid(root,1,List.of(ArmorModuleType.MINING_SERVO)));
        assertEquals(1,ArmorModuleLevels.read(root,ArmorModuleType.MINING_SERVO));assertEquals(before,root);
    }
    @Test void futureAndMalformedLevelsRemainPreservedAndCannotRun(){
        for(int level:new int[]{-1,0,4,Integer.MAX_VALUE}){
            var root=data("mining_servo",level);var before=root.copy();
            assertFalse(ArmorModuleLevels.valid(root,3,List.of(ArmorModuleType.MINING_SERVO)));
            assertEquals(0,ArmorModuleLevels.read(root,ArmorModuleType.MINING_SERVO));assertEquals(before,root);
        }
        var root=new CompoundTag();root.putString("ModuleLevels","corrupt");
        assertFalse(ArmorModuleLevels.valid(root,3,List.of()));assertEquals(0,ArmorModuleLevels.read(root,ArmorModuleType.SHIELD_FIELD));
        root=data("mining_servo",2);root.getCompound("ModuleLevels").putString("mining_servo","2");
        assertFalse(ArmorModuleLevels.valid(root,3,List.of(ArmorModuleType.MINING_SERVO)));
    }
    @Test void forgedLevelsCannotOutrunTheChassisOrAddUninstalledModules(){
        var root=data("mining_servo",3);
        assertFalse(ArmorModuleLevels.valid(root,2,List.of(ArmorModuleType.MINING_SERVO)));
        assertTrue(ArmorModuleLevels.valid(root,3,List.of(ArmorModuleType.MINING_SERVO)));
        assertFalse(ArmorModuleLevels.valid(root,3,List.of(ArmorModuleType.STRIDE_DRIVE)));
        assertFalse(ArmorModuleLevels.valid(data("foreign_module",1),3,List.of(ArmorModuleType.MINING_SERVO)));
        assertFalse(ArmorModuleLevels.valid(data("fall_damper",2),3,List.of(ArmorModuleType.FALL_DAMPER)));
    }
}
