package com.dungeoncrawler;

import com.dungeoncrawler.engine.EventManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MechanicsTest {
    @Test
    void wallsAndBoundsBlockMovement() {
        DungeonMap map = TestMaps.room(7, 7);
        Player player = new Player(1, 1);
        assertFalse(player.move(-1, 0, map, new EventManager()));
        assertFalse(player.move(0, -1, map, new EventManager()));
        assertEquals(1, player.getX());
        assertEquals(1, player.getY());
        assertFalse(map.isWalkable(-1, 2));
        assertFalse(map.isWalkable(7, 2));
    }

    @Test
    void attackingMonsterDoesNotMovePlayerIntoItsTile() {
        DungeonMap map = TestMaps.room(7, 7);
        Monster monster = new Monster(2, 1, 'B', "", "Logic Bug", 1.0, 5, 8, 100);
        map.getMonsters().add(monster);
        Player player = new Player(1, 1);
        assertTrue(player.move(1, 0, map, new EventManager()));
        assertEquals(1, player.getX());
        assertEquals(1, player.getY());
        assertTrue(monster.getHp() <= 85 && monster.getHp() >= 76);
    }

    @Test
    void defeatingMonsterAwardsScoreOnlyOnce() {
        DungeonMap map = TestMaps.room(7, 7);
        Monster monster = new Monster(2, 1, 'B', "", "Logic Bug", 1.0, 5, 8, 1);
        map.getMonsters().add(monster);
        Player player = new Player(1, 1);
        EventManager events = new EventManager();
        assertTrue(player.move(1, 0, map, events));
        assertEquals(50, player.getScore());
        assertFalse(map.hasMonsterAt(2, 1));
        assertTrue(player.move(1, 0, map, events));
        assertEquals(50, player.getScore());
        assertEquals(2, player.getX());
    }

    @Test
    void dataFragmentAwardsScoreExactlyOnce() {
        DungeonMap map = TestMaps.room(7, 7);
        map.getInteractables()[1][2] = '$';
        Player player = new Player(1, 1);
        EventManager events = new EventManager();
        assertTrue(player.move(1, 0, map, events));
        assertEquals(100, player.getScore());
        assertEquals(1, player.getInventory().size());
        assertEquals(' ', map.getInteractableAt(2, 1));
        assertTrue(player.move(-1, 0, map, events));
        assertTrue(player.move(1, 0, map, events));
        assertEquals(100, player.getScore());
        assertEquals(1, player.getInventory().size());
    }

    @Test
    void activeFirewallPatchBlocksTrapAndIsConsumed() {
        DungeonMap map = TestMaps.room(7, 7);
        map.getInteractables()[1][2] = 'T';
        Player player = new Player(1, 1);
        player.setFirewallPatchActive(true);
        assertTrue(player.move(1, 0, map, new EventManager()));
        assertEquals(100, player.getHp());
        assertFalse(player.isFirewallPatchActive());
        assertEquals(' ', map.getInteractableAt(2, 1));
    }

    @Test
    void unprotectedFirewallTrapDamagesOnce() {
        DungeonMap map = TestMaps.room(7, 7);
        map.getInteractables()[1][2] = 'T';
        Player player = new Player(1, 1);
        EventManager events = new EventManager();
        assertTrue(player.move(1, 0, map, events));
        assertEquals(65, player.getHp());
        assertTrue(player.move(-1, 0, map, events));
        assertTrue(player.move(1, 0, map, events));
        assertEquals(65, player.getHp());
    }

    @Test
    void monsterCannotMoveThroughBoundaryWall() {
        DungeonMap map = TestMaps.room(7, 7);
        Monster monster = new Monster(1, 1, 'B', "", "Bug", 1.0, 1, 1, 20);
        map.getMonsters().add(monster);
        monster.moveTowards(-1, 1, map);
        assertEquals(1, monster.getX());
        assertEquals(1, monster.getY());
    }
}
