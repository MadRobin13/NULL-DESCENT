package com.dungeoncrawler;

import com.dungeoncrawler.engine.TerminalRenderer;
import java.util.ArrayList;

/** Deterministic map fixtures: no procedural generation or random monster placement. */
final class TestMaps {
    private TestMaps() { }

    static DungeonMap room(int width, int height) {
        char[][] terrain = new char[height][width];
        char[][] interactables = new char[height][width];
        boolean[][] explored = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                terrain[y][x] = (x == 0 || x == width - 1 || y == 0 || y == height - 1)
                        ? TerminalRenderer.WALL_SOLID : TerminalRenderer.FLOOR_LIT;
                interactables[y][x] = ' ';
            }
        }
        return new DungeonMap(width, height, 1, 1, width - 2, height - 2,
                terrain, interactables, explored, new ArrayList<>());
    }
}
