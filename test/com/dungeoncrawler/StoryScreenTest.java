package com.dungeoncrawler;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class StoryScreenTest {
    private static String capture(GameEngine engine, int width, int height) {
        PrintStream prior = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream sink = new PrintStream(out, true, StandardCharsets.UTF_8)) {
            System.setOut(sink);
            engine.renderFrameAtSize(width, height);
        } finally {
            System.setOut(prior);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void titleAndFullOriginalStoryAvailableInCompactMode() {
        GameEngine engine = new GameEngine(true);
        engine.processTestInput('t');
        assertTrue(engine.isStoryOpenForTest());
        String first = capture(engine, 80, 24);
        assertTrue(first.contains("System Breach Detected"));
        assertTrue(first.contains("Core Network"));
        assertTrue(first.contains("[ENTER / SPACE / N] Continue"));
        assertFalse(first.contains("\033[25;1H"));

        engine.processTestInput('\n');
        assertEquals(1, engine.storyPageForTest());
        String second = capture(engine, 80, 24);
        assertTrue(second.contains("RECOVERED SYSTEM LOGS"));
        assertTrue(second.contains("System Overlord"));
        assertTrue(second.contains("Time to"));
        assertTrue(second.contains("reboot this system."));
        engine.processTestInput('\n');
        assertFalse(engine.isStoryOpenForTest());
        assertTrue(capture(engine, 80, 24).contains("-- SYSTEM STATUS --"));
    }

    @Test
    void storyKeysCannotAccidentallyMoveThePlayer() {
        GameEngine engine = new GameEngine(true);
        int x = engine.getHero().getX();
        int y = engine.getHero().getY();
        engine.processTestInput('t');
        for (char command : new char[]{'w', 'a', 's', 'd', 'e', 'k', 'l'}) engine.processTestInput(command);
        assertEquals(x, engine.getHero().getX());
        assertEquals(y, engine.getHero().getY());
        engine.processTestInput('q');
        assertFalse(engine.isStoryOpenForTest());
        assertTrue(engine.isRunning());
    }

    @Test
    void fullSizeLayoutRemainsTheSameAndStoryCanBeReopened() {
        GameEngine engine = new GameEngine(true);
        assertTrue(capture(engine, 100, 55).contains("System Breach Detected"));
        engine.processTestInput('t');
        assertTrue(capture(engine, 100, 55).contains("System Breach Detected"));
        engine.processTestInput('q');
        assertTrue(capture(engine, 100, 55).contains("-- LEGEND ---"));
    }
}
