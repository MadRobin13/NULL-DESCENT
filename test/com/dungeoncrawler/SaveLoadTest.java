package com.dungeoncrawler;

import com.dungeoncrawler.engine.FileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SaveLoadTest {
    @TempDir
    Path tempDir;

    private static class Fixture {
        Player player;
        List<DungeonMap> floors;
    }

    private Fixture fixture() {
        Fixture f = new Fixture();
        f.player = new Player(2, 2);
        f.player.setHp(67);
        f.player.setScore(450);
        f.player.setFirewallPatchActive(true);
        f.player.addItem(new Item("Data Fragment", 100));
        f.player.addItem(new Item("Firewall Patch", 50));
        DungeonMap first = TestMaps.room(7, 7);
        first.getExplored()[3][3] = true;
        first.getInteractables()[2][2] = ' '; // previously collected
        first.getInteractables()[3][4] = 'T'; // still untriggered
        Monster damaged = new Monster(3, 3, 'B', "\033[38;5;196m", "Logic Bug", 0.7, 5, 8, 40);
        damaged.takeDamage(11);
        first.getMonsters().add(damaged);
        DungeonMap second = TestMaps.room(7, 7);
        second.getInteractables()[2][3] = 'F';
        second.getExplored()[2][2] = true;
        f.floors = new ArrayList<>(List.of(first, second));
        return f;
    }

    private Path savePath() { return tempDir.resolve("savegame.txt"); }
    private Path goodSave() {
        Fixture f = fixture();
        assertTrue(FileManager.saveGame(savePath().toString(), f.player, f.floors, 1));
        return savePath();
    }

    @Test
    void roundTripPreservesPlayerAndMultipleFloors() {
        goodSave();
        FileManager.SaveState loaded = FileManager.loadGame(savePath().toString());
        assertNotNull(loaded);
        assertEquals(1, loaded.currentFloorIndex);
        assertEquals(2, loaded.floors.size());
        assertEquals(2, loaded.player.getX());
        assertEquals(2, loaded.player.getY());
        assertEquals(67, loaded.player.getHp());
        assertEquals(100, loaded.player.getMaxHp());
        assertEquals(450, loaded.player.getScore());
        assertTrue(loaded.player.isFirewallPatchActive());
        assertEquals(2, loaded.player.getInventory().size());
        assertEquals("Data Fragment", loaded.player.getInventory().get(0).getName());
        assertEquals("Firewall Patch", loaded.player.getInventory().get(1).getName());
        assertEquals(' ', loaded.floors.get(0).getInteractableAt(2, 2));
        assertEquals('T', loaded.floors.get(0).getInteractableAt(4, 3));
        assertTrue(loaded.floors.get(0).isExplored(3, 3));
        assertEquals('F', loaded.floors.get(1).getInteractableAt(3, 2));
        assertTrue(loaded.floors.get(1).isExplored(2, 2));
    }

    @Test
    void roundTripPreservesMonsterCurrentAndMaximumHp() {
        goodSave();
        FileManager.SaveState loaded = FileManager.loadGame(savePath().toString());
        assertNotNull(loaded);
        assertEquals(1, loaded.floors.get(0).getMonsters().size());
        Monster m = loaded.floors.get(0).getMonsters().get(0);
        assertEquals(29, m.getHp());
        assertEquals(40, m.getMaxHp());
        assertEquals(3, m.getX());
        assertEquals(3, m.getY());
        assertEquals("Logic Bug", m.getName());
        assertTrue(loaded.floors.get(1).getMonsters().isEmpty());
    }

    @Test
    void nonexistentSaveReturnsNull() {
        assertNull(FileManager.loadGame(tempDir.resolve("missing.txt").toString()));
    }

    @Test
    void unsupportedVersionIsRejected() throws IOException {
        goodSave();
        String data = Files.readString(savePath(), StandardCharsets.UTF_8);
        Files.writeString(savePath(), data.replace("VERSION|2", "VERSION|999"), StandardCharsets.UTF_8);
        assertNull(FileManager.loadGame(savePath().toString()));
    }

    @Test
    void invalidFloorIndexIsRejected() throws IOException {
        goodSave();
        String data = Files.readString(savePath(), StandardCharsets.UTF_8);
        Files.writeString(savePath(), data.replace("STATE|1|2", "STATE|9|2"), StandardCharsets.UTF_8);
        assertNull(FileManager.loadGame(savePath().toString()));
    }

    @Test
    void incorrectSectionHeaderIsRejected() throws IOException {
        goodSave();
        String data = Files.readString(savePath(), StandardCharsets.UTF_8);
        Files.writeString(savePath(), data.replaceFirst("TERRAIN", "NOT_TERRAIN"), StandardCharsets.UTF_8);
        assertNull(FileManager.loadGame(savePath().toString()));
    }

    @Test
    void invalidExplorationCharacterIsRejected() throws IOException {
        goodSave();
        String data = Files.readString(savePath(), StandardCharsets.UTF_8);
        int start = data.indexOf("EXPLORED\n");
        assertTrue(start >= 0);
        int index = start + "EXPLORED\n".length();
        String damaged = data.substring(0, index) + "X" + data.substring(index + 1);
        Files.writeString(savePath(), damaged, StandardCharsets.UTF_8);
        assertNull(FileManager.loadGame(savePath().toString()));
    }

    @Test
    void truncatedSaveIsRejectedWithoutChangingExistingObjects() throws IOException {
        Fixture live = fixture();
        goodSave();
        String data = Files.readString(savePath(), StandardCharsets.UTF_8);
        Files.writeString(savePath(), data.substring(0, data.length() / 2), StandardCharsets.UTF_8);
        assertNull(FileManager.loadGame(savePath().toString()));
        assertEquals(67, live.player.getHp());
        assertEquals(2, live.floors.size());
    }

    @Test
    void failedSaveDoesNotOverwriteValidFile() throws IOException {
        goodSave();
        byte[] oldFile = Files.readAllBytes(savePath());
        Fixture invalid = fixture();
        assertFalse(FileManager.saveGame(savePath().toString(), invalid.player, invalid.floors, 99));
        assertArrayEquals(oldFile, Files.readAllBytes(savePath()));
        assertNotNull(FileManager.loadGame(savePath().toString()));
    }

    @Test
    void extraTrailingContentIsRejected() throws IOException {
        goodSave();
        Files.writeString(savePath(), Files.readString(savePath()) + "EXTRA\n", StandardCharsets.UTF_8);
        assertNull(FileManager.loadGame(savePath().toString()));
    }
}
