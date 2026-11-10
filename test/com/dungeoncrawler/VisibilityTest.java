package com.dungeoncrawler;

import com.dungeoncrawler.engine.TerminalRenderer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisibilityTest {
    @Test
    void wallVisibleButDirectlyBehindItIsHidden() {
        DungeonMap map = TestMaps.room(7, 7);
        map.getTerrain()[2][2] = TerminalRenderer.WALL_SOLID;
        map.calculateFOV(1, 2, 4);
        assertTrue(map.isVisible(1, 2));
        assertTrue(map.isVisible(2, 2));
        assertFalse(map.isVisible(3, 2));
    }

    @Test
    void exploredTilesRemainRememberedWhenNotVisible() {
        DungeonMap map = TestMaps.room(9, 9);
        map.calculateFOV(1, 1, 3);
        assertTrue(map.isExplored(1, 1));
        map.calculateFOV(7, 7, 2);
        assertFalse(map.isVisible(1, 1));
        assertTrue(map.isExplored(1, 1));
    }

    @Test
    void visibilityRespectsRadius() {
        DungeonMap map = TestMaps.room(9, 9);
        map.calculateFOV(4, 4, 2);
        assertTrue(map.isVisible(6, 4));
        assertFalse(map.isVisible(7, 4));
        assertFalse(map.isVisible(6, 6));
        assertTrue(map.isVisible(4, 4));
    }

    @Test
    void twoPerpendicularWallsBlockDiagonalCorner() {
        DungeonMap map = TestMaps.room(7, 7);
        map.getTerrain()[1][2] = TerminalRenderer.WALL_SOLID;
        map.getTerrain()[2][1] = TerminalRenderer.WALL_SOLID;
        map.calculateFOV(1, 1, 3);
        assertFalse(map.isVisible(2, 2));
    }

    @Test
    void invalidOriginsAndNegativeRadiusDoNotThrow() {
        DungeonMap map = TestMaps.room(7, 7);
        assertDoesNotThrow(() -> map.calculateFOV(-1, -1, 3));
        assertDoesNotThrow(() -> map.calculateFOV(2, 2, -1));
        assertFalse(map.isVisible(2, 2));
        assertFalse(map.isVisible(-1, -1));
        assertFalse(map.isExplored(50, 50));
    }

    @Test
    void monsterTileMustBeCurrentlyVisible() {
        DungeonMap map = TestMaps.room(9, 9);
        Monster monster = new Monster(6, 4, 'B', "", "Bug", 1.0, 1, 1, 20);
        map.getMonsters().add(monster);
        map.calculateFOV(4, 4, 2);
        assertTrue(map.isVisible(monster.getX(), monster.getY()));
        map.calculateFOV(1, 1, 2);
        assertFalse(map.isVisible(monster.getX(), monster.getY()));
        assertTrue(map.isExplored(monster.getX(), monster.getY()));
    }
}
