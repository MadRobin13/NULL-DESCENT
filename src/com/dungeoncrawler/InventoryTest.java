package com.dungeoncrawler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {
    @Test
    void selectionSortByNameIgnoresCase() {
        Player player = new Player(1, 1);
        player.addItem(new Item("health syringe", 5));
        player.addItem(new Item("Firewall Patch", 50));
        player.addItem(new Item("Data Fragment", 100));
        player.sortInventory("name");
        assertEquals("Data Fragment", player.getInventory().get(0).getName());
        assertEquals("Firewall Patch", player.getInventory().get(1).getName());
        assertEquals("health syringe", player.getInventory().get(2).getName());
    }

    @Test
    void selectionSortByValueIsAscending() {
        Player player = new Player(1, 1);
        player.addItem(new Item("Fragment", 100));
        player.addItem(new Item("Cache", 25));
        player.addItem(new Item("Syringe", 5));
        player.sortInventory("value");
        assertEquals(5, player.getInventory().get(0).getValue());
        assertEquals(25, player.getInventory().get(1).getValue());
        assertEquals(100, player.getInventory().get(2).getValue());
    }

    @Test
    void itemComparableSortsNamesWithoutCaseSensitivity() {
        assertTrue(new Item("alpha", 1).compareTo(new Item("BETA", 2)) < 0);
        assertEquals(0, new Item("CACHE", 1).compareTo(new Item("cache", 20)));
    }

    @Test
    void addAndRemoveItem() {
        Player player = new Player(1, 1);
        Item patch = new Item("Firewall Patch", 50);
        player.addItem(patch);
        assertEquals(1, player.getInventory().size());
        player.removeItem(patch);
        assertTrue(player.getInventory().isEmpty());
    }

    @Test
    void healingAndDamageRespectHealthBounds() {
        Player player = new Player(1, 1);
        player.takeDamage(30);
        assertEquals(70, player.getHp());
        player.heal(12);
        assertEquals(82, player.getHp());
        player.heal(100);
        assertEquals(100, player.getHp());
        player.takeDamage(500);
        assertEquals(0, player.getHp());
    }

    @Test
    void resetClearsInventoryScoreAndActivePatch() {
        Player player = new Player(1, 1);
        player.takeDamage(50);
        player.addScore(350);
        player.addItem(new Item("Firewall Patch", 50));
        player.setFirewallPatchActive(true);
        player.reset();
        assertEquals(100, player.getHp());
        assertEquals(0, player.getScore());
        assertTrue(player.getInventory().isEmpty());
        assertFalse(player.isFirewallPatchActive());
    }
}
