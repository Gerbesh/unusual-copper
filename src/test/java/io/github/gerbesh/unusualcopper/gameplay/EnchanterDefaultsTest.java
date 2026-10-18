package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnchanterDefaultsTest {
    @Test void freshWorldIncludesAllRegisteredModContentWithoutIdLists(){
        assertTrue(ServerConfig.enchanterAutomatic());assertTrue(ServerConfig.enchanterTreasure());assertTrue(ServerConfig.enchanterCurses());
        assertTrue(ServerConfig.enchanterItems().isEmpty());assertTrue(ServerConfig.enchanterEnchantments().isEmpty());
        assertEquals(32767,ServerConfig.enchanterMaxLevel());
    }
}
