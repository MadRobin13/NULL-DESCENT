package com.dungeoncrawler;

import com.dungeoncrawler.engine.FileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Checks procedural-map structural invariants and regression-prone integration paths. */
public class DungeonGenerationTest {

    @Test
    void startingSectorsHaveAReachableDownstairsAndNoUpstairs() {
        for (long seed = 0; seed < 1000; seed++) {
            DungeonMap map = new DungeonMap(58, 19, false, 0, seed);
            verifyMap(map, false, false, seed);
        }
    }

    @Test
    void intermediateSectorsPreserveBothStaircases() {
        for (long seed = 0; seed < 1000; seed++) {
            DungeonMap map = new DungeonMap(58, 19, true, 5, seed);
            verifyMap(map, true, false, seed);
        }
    }

    @Test
    void finalSectorsPreserveTheUpstairsMainframeAndBoss() {
        for (long seed = 0; seed < 1000; seed++) {
            DungeonMap map = new DungeonMap(58, 19, true, 9, seed * 1337L);
            verifyMap(map, true, true, seed);
        }
    }

    @Test
    void seededGenerationReproducesTerrainItemsAndMonsters() {
        DungeonMap first = new DungeonMap(58, 19, true, 9, 104729L);
        DungeonMap second = new DungeonMap(58, 19, true, 9, 104729L);
        compareMaps(first, second);
    }

    @Test
    void generatedFloorsRoundTripThroughTheExistingSaveFormat(@TempDir Path temp) {
        DungeonMap first = new DungeonMap(58, 19, false, 0, 100L);
        DungeonMap middle = new DungeonMap(58, 19, true, 5, 200L);
        DungeonMap finalMap = new DungeonMap(58, 19, true, 9, 300L);
        List<DungeonMap> floors = List.of(first, middle, finalMap);
        Player player = new Player(finalMap.getStartX(), finalMap.getStartY());
        Path savePath = temp.resolve("generation-save.txt");

        assertTrue(FileManager.saveGame(savePath.toString(), player, floors, 2));
        FileManager.SaveState restored = FileManager.loadGame(savePath.toString());
        assertNotNull(restored, "Generated floors should load using the existing save format");
        assertEquals(2, restored.currentFloorIndex);
        assertEquals(3, restored.floors.size());
        for (int i = 0; i < floors.size(); i++) {
            compareMaps(floors.get(i), restored.floors.get(i));
        }
    }

    private static void verifyMap(DungeonMap map, boolean hasUpstairs, boolean isFinal, long seed) {
        String context = "seed=" + seed + ", final=" + isFinal;
        int sx = map.getStartX(), sy = map.getStartY();
        int ex = map.getEndX(), ey = map.getEndY();
        assertTrue(map.isWalkable(sx, sy), "Start is impassable: " + context);
        assertTrue(map.isWalkable(ex, ey), "Objective is impassable: " + context);
        assertFalse(sx == ex && sy == ey, "Start and objective overlap: " + context);
        assertEquals(hasUpstairs ? '<' : ' ', map.getInteractableAt(sx, sy),
                "Unexpected starting tile: " + context);
        assertEquals(isFinal ? 'M' : '>', map.getInteractableAt(ex, ey),
                "Missing floor objective: " + context);
        assertEquals(hasUpstairs ? 1 : 0, countTile(map, '<'), "Incorrect up-stair count: " + context);
        assertEquals(isFinal ? 0 : 1, countTile(map, '>'), "Incorrect down-stair count: " + context);
        assertEquals(isFinal ? 1 : 0, countTile(map, 'M'), "Incorrect mainframe count: " + context);
        assertTrue(isReachable(map, sx, sy, ex, ey),
                "The objective cannot be reached by walking through floor tiles: " + context);

        Set<Long> occupied = new HashSet<>();
        long bossCount = 0;
        for (Monster m : map.getMonsters()) {
            int x = m.getX(), y = m.getY();
            assertTrue(map.isWalkable(x, y), "Monster out of bounds or inside a wall: " + context);
            assertFalse(x == sx && y == sy, "Monster occupies player spawn: " + context);
            assertFalse(x == ex && y == ey, "Monster occupies objective: " + context);
            assertEquals(' ', map.getInteractableAt(x, y), "Monster occupies interactive tile: " + context);
            assertTrue(occupied.add((((long) y) << 32) | (x & 0xffffffffL)),
                    "Overlapping monsters at " + x + "," + y + ": " + context);
            assertTrue(isReachable(map, sx, sy, x, y), "Monster is in an isolated chamber: " + context);
            if (m.getSymbol() == 'O') bossCount++;
        }
        assertEquals(isFinal ? 1 : 0, bossCount, "Incorrect final-boss count: " + context);
    }

    /** Terrain-only reachability: monsters can be defeated, so they are not permanent walls. */
    private static boolean isReachable(DungeonMap map, int startX, int startY, int endX, int endY) {
        int width = map.getWidth(), height = map.getHeight();
        boolean[][] visited = new boolean[height][width];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        visited[startY][startX] = true;
        queue.addLast(new int[]{startX, startY});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.removeFirst();
            if (p[0] == endX && p[1] == endY) return true;
            for (int[] step : steps) {
                int x = p[0] + step[0], y = p[1] + step[1];
                if (x >= 0 && x < width && y >= 0 && y < height
                        && !visited[y][x] && map.isWalkable(x, y)) {
                    visited[y][x] = true;
                    queue.addLast(new int[]{x, y});
                }
            }
        }
        return false;
    }

    private static int countTile(DungeonMap map, char tile) {
        int count = 0;
        for (char[] row : map.getInteractables()) {
            for (char value : row) if (value == tile) count++;
        }
        return count;
    }

    private static void compareMaps(DungeonMap expected, DungeonMap actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        assertEquals(expected.getStartX(), actual.getStartX());
        assertEquals(expected.getStartY(), actual.getStartY());
        assertEquals(expected.getEndX(), actual.getEndX());
        assertEquals(expected.getEndY(), actual.getEndY());
        for (int y = 0; y < expected.getHeight(); y++) {
            assertArrayEquals(expected.getTerrain()[y], actual.getTerrain()[y], "Terrain row " + y);
            assertArrayEquals(expected.getInteractables()[y], actual.getInteractables()[y],
                    "Interactable row " + y);
        }
        List<Monster> a = expected.getMonsters(), b = actual.getMonsters();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).getX(), b.get(i).getX());
            assertEquals(a.get(i).getY(), b.get(i).getY());
            assertEquals(a.get(i).getSymbol(), b.get(i).getSymbol());
            assertEquals(a.get(i).getHp(), b.get(i).getHp());
        }
    }
}
