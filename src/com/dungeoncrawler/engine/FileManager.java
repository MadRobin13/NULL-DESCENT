package com.dungeoncrawler.engine;

import com.dungeoncrawler.DungeonMap;
import com.dungeoncrawler.Item;
import com.dungeoncrawler.Monster;
import com.dungeoncrawler.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Saves and restores a complete run using a readable, versioned text file. */
public final class FileManager {
    private static final int SAVE_VERSION = 2;
    private static final int MAX_FLOORS = 100;
    private static final int MAX_ITEMS = 50000;
    private static final int MAX_MONSTERS = 10000;
    private static final int MAX_WIDTH = 200;
    private static final int MAX_HEIGHT = 100;

    private FileManager() { }

    public static class SaveState {
        public Player player;
        public List<DungeonMap> floors;
        public int currentFloorIndex;
    }

    public static boolean saveGame(String filename, Player player,
                                   List<DungeonMap> floors, int currentFloorIndex) {
        Path temporaryFile = null;
        try {
            if (player == null || floors == null || floors.isEmpty()
                    || currentFloorIndex < 0 || currentFloorIndex >= floors.size()) {
                throw new IOException("Invalid current game state");
            }

            Path saveFile = Path.of(filename).toAbsolutePath();
            temporaryFile = Files.createTempFile(saveFile.getParent(), "null-descent-", ".tmp");

            // A BufferedWriter reports I/O errors rather than silently ignoring them.
            try (BufferedWriter out = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                line(out, "VERSION|" + SAVE_VERSION);
                line(out, "PLAYER|" + player.getX() + "|" + player.getY() + "|" + player.getHp()
                        + "|" + player.getMaxHp() + "|" + player.getScore() + "|"
                        + player.isFirewallPatchActive());

                List<Item> inventory = player.getInventory();
                line(out, "INVENTORY|" + inventory.size());
                for (Item item : inventory) {
                    line(out, "ITEM|" + safeField(item.getName()) + "|" + item.getValue());
                }

                line(out, "STATE|" + currentFloorIndex + "|" + floors.size());
                for (DungeonMap map : floors) {
                    line(out, "FLOOR|" + map.getWidth() + "|" + map.getHeight() + "|"
                            + map.getStartX() + "|" + map.getStartY() + "|"
                            + map.getEndX() + "|" + map.getEndY());
                    writeChars(out, "TERRAIN", map.getTerrain());
                    writeChars(out, "INTERACTABLES", map.getInteractables());
                    writeBooleans(out, "EXPLORED", map.getExplored());

                    List<Monster> monsters = map.getMonsters();
                    line(out, "MONSTERS|" + monsters.size());
                    for (Monster m : monsters) {
                        // Store both current and maximum HP, not just the current HP.
                        line(out, "MONSTER|" + m.getX() + "|" + m.getY() + "|" + m.getSymbol()
                                + "|" + safeField(m.getColor()) + "|" + safeField(m.getName())
                                + "|" + m.getSpeed() + "|" + m.getMinDmg() + "|" + m.getMaxDmg()
                                + "|" + m.getHp() + "|" + m.getMaxHp());
                    }
                }
                line(out, "END");
            }

            // Only replace the existing save after the complete temporary file is closed.
            try {
                Files.move(temporaryFile, saveFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, saveFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Save failed: " + e.getMessage());
            return false;
        } finally {
            if (temporaryFile != null) {
                try { Files.deleteIfExists(temporaryFile); }
                catch (IOException ignored) { }
            }
        }
    }

    public static SaveState loadGame(String filename) {
        try (BufferedReader in = Files.newBufferedReader(Path.of(filename), StandardCharsets.UTF_8)) {
            String[] version = fields(in, "VERSION", 2);
            if (number(version[1]) != SAVE_VERSION) throw new IOException("Unsupported save version");

            String[] p = fields(in, "PLAYER", 7);
            int px = number(p[1]);
            int py = number(p[2]);
            int hp = number(p[3]);
            int maxHp = number(p[4]);
            int score = number(p[5]);
            boolean patch = booleanValue(p[6]);
            if (maxHp <= 0 || hp < 0 || hp > maxHp || score < 0)
                throw new IOException("Invalid player statistics");

            Player player = new Player(px, py);
            player.setMaxHp(maxHp);
            player.setHp(hp);
            player.setScore(score);
            player.setFirewallPatchActive(patch);

            int itemCount = number(fields(in, "INVENTORY", 2)[1]);
            checkCount(itemCount, MAX_ITEMS, "items");
            for (int i = 0; i < itemCount; i++) {
                String[] item = fields(in, "ITEM", 3);
                if (item[1].isBlank() || number(item[2]) < 0)
                    throw new IOException("Invalid inventory item");
                player.addItem(new Item(item[1], number(item[2])));
            }

            String[] stateLine = fields(in, "STATE", 3);
            int currentFloor = number(stateLine[1]);
            int floorCount = number(stateLine[2]);
            checkCount(floorCount, MAX_FLOORS, "floors");
            if (floorCount == 0 || currentFloor < 0 || currentFloor >= floorCount)
                throw new IOException("Invalid current floor");

            List<DungeonMap> floors = new ArrayList<>();
            for (int f = 0; f < floorCount; f++) {
                String[] data = fields(in, "FLOOR", 7);
                int width = number(data[1]);
                int height = number(data[2]);
                if (width < 1 || width > MAX_WIDTH || height < 1 || height > MAX_HEIGHT)
                    throw new IOException("Invalid floor dimensions");
                int sx = number(data[3]);
                int sy = number(data[4]);
                int ex = number(data[5]);
                int ey = number(data[6]);
                checkPosition(sx, sy, width, height);
                checkPosition(ex, ey, width, height);

                char[][] terrain = readChars(in, "TERRAIN", width, height);
                char[][] interactables = readChars(in, "INTERACTABLES", width, height);
                boolean[][] explored = readBooleans(in, "EXPLORED", width, height);

                int monsterCount = number(fields(in, "MONSTERS", 2)[1]);
                checkCount(monsterCount, MAX_MONSTERS, "monsters");
                List<Monster> monsters = new ArrayList<>();
                for (int i = 0; i < monsterCount; i++) {
                    String[] m = fields(in, "MONSTER", 11);
                    int mx = number(m[1]);
                    int my = number(m[2]);
                    checkPosition(mx, my, width, height);
                    if (m[3].length() != 1 || m[5].isBlank())
                        throw new IOException("Invalid monster identity");
                    double speed = Double.parseDouble(m[6]);
                    int minDmg = number(m[7]);
                    int maxDmg = number(m[8]);
                    int currentHp = number(m[9]);
                    int maximumHp = number(m[10]);
                    if (!Double.isFinite(speed) || speed < 0 || speed > 1
                            || minDmg < 0 || maxDmg < minDmg
                            || currentHp <= 0 || maximumHp < currentHp)
                        throw new IOException("Invalid monster statistics");

                    Monster monster = new Monster(mx, my, m[3].charAt(0), m[4], m[5],
                            speed, minDmg, maxDmg, maximumHp);
                    monster.takeDamage(maximumHp - currentHp);
                    monsters.add(monster);
                }
                floors.add(new DungeonMap(width, height, sx, sy, ex, ey,
                        terrain, interactables, explored, monsters));
            }

            if (!"END".equals(requiredLine(in)) || in.readLine() != null)
                throw new IOException("Invalid end of save file");
            DungeonMap activeMap = floors.get(currentFloor);
            checkPosition(px, py, activeMap.getWidth(), activeMap.getHeight());
            if (!activeMap.isWalkable(px, py))
                throw new IOException("Saved player is inside a wall");

            // Return the fully validated state; the running game is not modified here.
            SaveState loaded = new SaveState();
            loaded.player = player;
            loaded.floors = floors;
            loaded.currentFloorIndex = currentFloor;
            return loaded;
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Load failed: " + e.getMessage());
            return null;
        }
    }

    private static void line(BufferedWriter out, String text) throws IOException {
        out.write(text);
        out.newLine();
    }

    private static String safeField(String value) throws IOException {
        if (value == null || value.contains("|") || value.contains("\n") || value.contains("\r"))
            throw new IOException("Unsupported character in saved name");
        return value;
    }

    private static String requiredLine(BufferedReader in) throws IOException {
        String s = in.readLine();
        if (s == null) throw new IOException("Save file ends unexpectedly");
        return s;
    }

    private static String[] fields(BufferedReader in, String tag, int count) throws IOException {
        String[] parts = requiredLine(in).split("\\|", -1);
        if (parts.length != count || !tag.equals(parts[0]))
            throw new IOException("Missing or invalid " + tag + " record");
        return parts;
    }

    private static int number(String raw) { return Integer.parseInt(raw); }

    private static boolean booleanValue(String raw) throws IOException {
        if ("true".equals(raw)) return true;
        if ("false".equals(raw)) return false;
        throw new IOException("Invalid boolean value");
    }

    private static void checkCount(int count, int max, String description) throws IOException {
        if (count < 0 || count > max) throw new IOException("Invalid number of " + description);
    }

    private static void checkPosition(int x, int y, int width, int height) throws IOException {
        if (x < 0 || x >= width || y < 0 || y >= height)
            throw new IOException("Position outside dungeon");
    }

    private static void writeChars(BufferedWriter out, String tag, char[][] data) throws IOException {
        line(out, tag);
        for (char[] row : data) line(out, new String(row));
    }

    private static char[][] readChars(BufferedReader in, String tag, int width, int height) throws IOException {
        if (!tag.equals(requiredLine(in))) throw new IOException("Missing " + tag + " header");
        char[][] result = new char[height][width];
        for (int y = 0; y < height; y++) {
            String line = requiredLine(in);
            if (line.length() != width) throw new IOException("Invalid " + tag + " row width");
            result[y] = line.toCharArray();
        }
        return result;
    }

    private static void writeBooleans(BufferedWriter out, String tag, boolean[][] data) throws IOException {
        line(out, tag);
        for (boolean[] row : data) {
            StringBuilder line = new StringBuilder(row.length);
            for (boolean value : row) line.append(value ? '1' : '0');
            line(out, line.toString());
        }
    }

    private static boolean[][] readBooleans(BufferedReader in, String tag, int width, int height) throws IOException {
        if (!tag.equals(requiredLine(in))) throw new IOException("Missing " + tag + " header");
        boolean[][] result = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            String line = requiredLine(in);
            if (line.length() != width) throw new IOException("Invalid " + tag + " row width");
            for (int x = 0; x < width; x++) {
                char ch = line.charAt(x);
                if (ch != '0' && ch != '1') throw new IOException("Invalid exploration character");
                result[y][x] = ch == '1';
            }
        }
        return result;
    }
}
