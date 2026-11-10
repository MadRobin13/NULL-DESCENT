package com.dungeoncrawler;

import com.dungeoncrawler.engine.EventManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises engine-level input/state transitions without an interactive terminal or audio. */
public class GameStateTest {
    @TempDir Path tempDir;

    private GameEngine newEngine() {
        return new GameEngine(true, tempDir.resolve("test-save.txt").toString());
    }

    private static String capturedFrame(GameEngine engine) {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream sink = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(sink);
            engine.renderFrame();
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8)
                .replaceAll("\u001b\\[[0-9;?]*[a-zA-Z]", "");
    }

    private static long countDeathMessages(GameEngine engine) {
        return engine.getEventManager().getRecentLogs().stream()
            .filter(log -> log.message.contains("CRITICAL FAILURE"))
            .count();
    }

    @Test
    void mainframeBlocksVictoryUntilBossIsDefeatedAndAwardsScoreOnlyOnce() {
        GameEngine engine = newEngine();
        DungeonMap finalMap = new DungeonMap(58, 19, true, 9, 31337L);
        engine.setCurrentMapForTest(finalMap);
        engine.getHero().setPosition(finalMap.getEndX(), finalMap.getEndY());
        assertEquals('M', finalMap.getInteractableAt(engine.getHero().getX(), engine.getHero().getY()));
        assertEquals(1, finalMap.getMonsters().stream().filter(m -> m.getSymbol() == 'O').count());

        engine.processTestInput('e');
        assertFalse(engine.isVictorious(), "An undefeated boss must block the mainframe");
        assertEquals(0, engine.getHero().getScore());

        finalMap.getMonsters().removeIf(m -> m.getSymbol() == 'O');
        engine.processTestInput('e');
        assertTrue(engine.isVictorious(), "Interacting with an unguarded mainframe should win");
        assertTrue(engine.isRunning(), "Winning must not immediately terminate the game");
        assertEquals(5000, engine.getHero().getScore());

        engine.processTestInput('e');
        engine.processTestInput('i');
        engine.processTestInput('w');
        assertTrue(engine.isVictorious(), "Victorious state should be stable until restart/load/quit");
        assertFalse(engine.isInventoryMenuOpen());
        assertEquals(5000, engine.getHero().getScore(), "Repeated interaction cannot duplicate the win bonus");
    }

    @Test
    void victoryOverlayRemainsVisibleAcrossRepeatedFrameRenders() {
        GameEngine engine = newEngine();
        DungeonMap finalMap = new DungeonMap(58, 19, true, 9, 47L);
        engine.setCurrentMapForTest(finalMap);
        finalMap.getMonsters().removeIf(m -> m.getSymbol() == 'O');
        engine.getHero().setPosition(finalMap.getEndX(), finalMap.getEndY());
        engine.processTestInput('e');
        assertTrue(engine.isVictorious());
        String first = capturedFrame(engine);
        String second = capturedFrame(engine);
        assertTrue(first.contains("MAINFRAME HACKED. MISSION ACCOMPLISHED!"));
        assertTrue(first.contains("Final Score: 5000"));
        assertTrue(second.contains("MAINFRAME HACKED. MISSION ACCOMPLISHED!"));
        assertTrue(second.contains("[R] Restart System"));
        assertTrue(second.contains("[L] Load Saved Run"));
    }

    @Test
    void deathOverlayRemainsVisibleAfterRepeatedUpdates() {
        GameEngine engine = newEngine();
        engine.getHero().takeDamage(9999);
        for (int i = 0; i < 4; i++) engine.processTestInput('w');
        assertTrue(capturedFrame(engine).contains("CRITICAL FAILURE. CONNECTION LOST."));
        assertTrue(capturedFrame(engine).contains("[R] Restart"));
        assertEquals(1, countDeathMessages(engine));
    }

    @Test
    void victoryCanRestartOrQuit() {
        GameEngine engine = newEngine();
        DungeonMap finalMap = new DungeonMap(58, 19, true, 9, 41L);
        engine.setCurrentMapForTest(finalMap);
        engine.getHero().setPosition(finalMap.getEndX(), finalMap.getEndY());
        finalMap.getMonsters().removeIf(m -> m.getSymbol() == 'O');
        engine.processTestInput('e');
        assertTrue(engine.isVictorious());

        engine.processTestInput('r');
        assertFalse(engine.isVictorious());
        assertTrue(engine.isRunning());
        assertEquals(0, engine.getHero().getScore());
        engine.processTestInput('q');
        assertFalse(engine.isRunning());
    }

    @Test
    void deathMessageIsPublishedOnlyOnceAcrossUpdates() {
        GameEngine engine = newEngine();
        engine.getHero().takeDamage(9999);
        for (int i = 0; i < 12; i++) engine.processTestInput('w');
        assertEquals(1, countDeathMessages(engine));
        assertTrue(engine.isDeathMessagePublished());
        assertFalse(engine.wasJumpscareTriggered(), "External damage must not trigger a monster jumpscare");
    }

    @Test
    void restartingClearsOldLogsAndResetsDeathAndInventoryState() {
        GameEngine engine = newEngine();
        engine.getEventManager().publishEvent("STALE MARKER", EventManager.EventType.SYSTEM);
        engine.getHero().addItem(new Item("Data Fragment", 100));
        engine.getHero().takeDamage(9999);
        engine.processTestInput('w');
        engine.processTestInput('r');

        assertTrue(engine.getHero().getHp() > 0);
        assertEquals(0, engine.getHero().getScore());
        assertTrue(engine.getHero().getInventory().isEmpty());
        assertFalse(engine.isDeathMessagePublished());
        assertFalse(engine.wasJumpscareTriggered());
        assertFalse(engine.isInventoryMenuOpen());
        assertFalse(engine.getEventManager().getRecentLogs().stream()
                .anyMatch(log -> log.message.contains("STALE MARKER") || log.message.contains("CRITICAL FAILURE")));
    }

    @Test
    void menuInputsCannotChangeInventoryAfterDeath() {
        GameEngine engine = newEngine();
        engine.getHero().takeDamage(9999);
        engine.processTestInput('w');
        engine.processTestInput('i');
        assertFalse(engine.isInventoryMenuOpen());
        engine.processTestInput('h');
        engine.processTestInput('q'); // Close help, not quit game
        assertTrue(engine.isRunning());
        engine.processTestInput('q');
        assertFalse(engine.isRunning());
    }

    @Test
    void loadingAfterDeathRestoresLivingStateAndClearsDeathLogs() {
        GameEngine engine = newEngine();
        engine.getHero().addScore(175);
        engine.processTestInput('k');
        assertTrue(tempDir.resolve("test-save.txt").toFile().exists());
        engine.getHero().takeDamage(9999);
        engine.processTestInput('w');
        assertTrue(engine.isDeathMessagePublished());
        engine.processTestInput('l');

        assertEquals(175, engine.getHero().getScore());
        assertTrue(engine.getHero().getHp() > 0);
        assertFalse(engine.isDeathMessagePublished());
        assertFalse(engine.wasJumpscareTriggered());
        assertEquals(0, countDeathMessages(engine), "Old death log must not survive a successful load");
        assertTrue(engine.getEventManager().getRecentLogs().stream()
                .anyMatch(log -> log.message.contains("Game loaded successfully")));
    }

    @Test
    void loadingAfterVictoryReturnsToSavedLivingGame() {
        GameEngine engine = newEngine();
        engine.getHero().addScore(80);
        engine.processTestInput('k');

        DungeonMap finalMap = new DungeonMap(58, 19, true, 9, 42L);
        engine.setCurrentMapForTest(finalMap);
        finalMap.getMonsters().removeIf(m -> m.getSymbol() == 'O');
        engine.getHero().setPosition(finalMap.getEndX(), finalMap.getEndY());
        engine.processTestInput('e');
        assertTrue(engine.isVictorious());
        engine.processTestInput('l');

        assertFalse(engine.isVictorious());
        assertTrue(engine.isRunning());
        assertEquals(80, engine.getHero().getScore());
    }

    @Test
    void failedLoadDoesNotReplaceCurrentGameOrEraseEvents() {
        GameEngine engine = newEngine();
        Player originalPlayer = engine.getHero();
        engine.getHero().addScore(10);
        engine.processTestInput('l');
        assertSame(originalPlayer, engine.getHero());
        assertEquals(10, engine.getHero().getScore());
        assertTrue(engine.getEventManager().getRecentLogs().stream()
                .anyMatch(log -> log.message.contains("No valid save")));
    }

    @Test
    void monsterFatalHitTriggersOnceButNotEnvironmentalTrap() {
        GameEngine engine = newEngine();
        DungeonMap map = engine.getCurrentMapForTest();
        Player player = engine.getHero();
        map.getMonsters().clear();
        int[][] adjacent = {{1,0},{-1,0},{0,1},{0,-1}};
        int mx = -1, my = -1;
        for (int[] d : adjacent) {
            int x = player.getX() + d[0], y = player.getY() + d[1];
            if (map.isWalkable(x, y)) { mx=x; my=y; break; }
        }
        assertTrue(mx >= 0, "Start room must have walkable adjacent tile");
        map.getMonsters().add(new Monster(mx, my, 'B', "", "Test Logic Bug", 1.0, 500, 500, 10));
        engine.processMonsterTurns();
        assertEquals(0, player.getHp());
        assertTrue(engine.wasJumpscareTriggered(), "Fatal monster hit must be recorded once");
        engine.processMonsterTurns();
        assertEquals(1, map.getMonsters().size(), "Monster turns after death must not remove/alter enemies");
        engine.processTestInput('w');
        assertEquals(1, countDeathMessages(engine));

        GameEngine trapEngine = newEngine();
        DungeonMap trapMap = trapEngine.getCurrentMapForTest();
        Player trapHero = trapEngine.getHero();
        trapHero.setHp(20);
        trapMap.getInteractables()[trapHero.getY()][trapHero.getX()] = 'T';
        trapMap.triggerTile(trapHero, trapEngine.getEventManager());
        assertEquals(0, trapHero.getHp());
        trapEngine.processTestInput('w');
        assertFalse(trapEngine.wasJumpscareTriggered());
        assertEquals(1, countDeathMessages(trapEngine));
    }

    @Test
    void restartAfterMonsterDeathAllowsFutureJumpscares() {
        GameEngine engine = newEngine();
        DungeonMap map = engine.getCurrentMapForTest();
        Player hero = engine.getHero();
        map.getMonsters().clear();
        int x=hero.getX(), y=hero.getY();
        if (map.isWalkable(x+1,y)) x++; else if (map.isWalkable(x-1,y)) x--; else if (map.isWalkable(x,y+1)) y++; else y--;
        map.getMonsters().add(new Monster(x,y,'B',"","Test",1.0,200,200,10));
        engine.processMonsterTurns();
        assertTrue(engine.wasJumpscareTriggered());
        engine.processTestInput('r');
        assertFalse(engine.wasJumpscareTriggered());
        assertTrue(engine.getHero().getHp()>0);
    }
}
