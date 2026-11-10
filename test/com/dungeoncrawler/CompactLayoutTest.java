package com.dungeoncrawler;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CompactLayoutTest {
    private static String capture(GameEngine engine, int width, int height) {
        PrintStream previous = System.out;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (PrintStream sink = new PrintStream(data, true, StandardCharsets.UTF_8)) {
            System.setOut(sink);
            engine.renderFrameAtSize(width, height);
        } finally {
            System.setOut(previous);
        }
        return data.toString(StandardCharsets.UTF_8);
    }

    @Test
    void compactFrameDrawsTheCompleteMapAndStatsWithin80By24() {
        GameEngine engine = new GameEngine(true);
        String output = capture(engine, 80, 24);
        assertTrue(output.contains("NULL//DESCENT"));
        assertTrue(output.contains("-- SYSTEM STATUS --"));
        assertTrue(output.contains("-- MAP LEGEND --"));
        assertTrue(output.contains("WASD Move"));
        assertTrue(output.contains("\033[24;1H"));
        assertFalse(output.contains("\033[25;1H"));
        assertFalse(output.contains("\033[55;1H"));
        assertFalse(output.contains("\n"));
        assertEquals(24, output.split("\\033\\[[0-9]+;1H", -1).length - 1);
    }

    @Test
    void helpAndInventoryRemainFullyAvailableInCompactLayout() {
        GameEngine engine = new GameEngine(true);
        engine.processTestInput('h');
        String help = capture(engine, 80, 24);
        assertTrue(help.contains("HELP / CONTROLS"));
        assertTrue(help.contains("System Overlord controls the corrupted network"));
        assertTrue(help.contains("H / Q CLOSE"));
        engine.processTestInput('h');
        engine.processTestInput('i');
        String inventory = capture(engine, 80, 24);
        assertTrue(inventory.contains("INVENTORY"));
        assertTrue(inventory.contains("Page 1/1"));
        assertTrue(inventory.contains("Inventory is empty"));
    }

    @Test
    void deathAndVictoryMenusRenderOnSmallTerminals() {
        GameEngine engine = new GameEngine(true);
        engine.getHero().takeDamage(9999);
        engine.processTestInput('w');
        String death = capture(engine, 80, 24);
        assertTrue(death.contains("CRITICAL FAILURE. CONNECTION LOST."));
        assertTrue(death.contains("[R] Restart"));
        engine.processTestInput('r');
        DungeonMap bossMap = new DungeonMap(58, 19, true, 9, 1223L);
        engine.setCurrentMapForTest(bossMap);
        bossMap.getMonsters().removeIf(m -> m.getSymbol() == 'O');
        engine.getHero().setPosition(bossMap.getEndX(), bossMap.getEndY());
        engine.processTestInput('e');
        String victory = capture(engine, 80, 24);
        assertTrue(victory.contains("MISSION ACCOMPLISHED!"));
        assertTrue(victory.contains("Final Score: 5000"));
    }

    @Test
    void resizingSwitchesLayoutsWithoutBreakingEither() {
        GameEngine engine = new GameEngine(true);
        assertTrue(capture(engine, 80, 24).contains("-- SYSTEM STATUS --"));
        String full = capture(engine, 100, 55);
        assertTrue(full.contains("System Breach Detected"));
        assertTrue(full.contains("\033[55;1H"));
        assertTrue(capture(engine, 120, 30).contains("-- SYSTEM STATUS --"));
        assertTrue(capture(engine, 79, 24).contains("TERMINAL TOO SMALL"));
        assertTrue(capture(engine, 80, 24).contains("-- SYSTEM STATUS --"));
    }
}
