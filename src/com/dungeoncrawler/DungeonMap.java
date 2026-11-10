package com.dungeoncrawler;

import com.dungeoncrawler.engine.TerminalRenderer;
import com.dungeoncrawler.engine.EventManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DungeonMap {
    private final int width, height;
    private final boolean[][] explored;
    private final boolean[][] visible; 
    private final char[][] terrain;
    private final char[][] interactables;
    private final List<Monster> monsters;
    
    private int startX, startY;
    private int endX, endY;
    private final Random rand;

    // Standard constructor for new floors (randomized)
    public DungeonMap(int width, int height, boolean hasUpStairs, int floorDepth) {
        this(width, height, hasUpStairs, floorDepth, new Random().nextLong());
    }
    
    // Deterministic constructor for repeatable testing
    public DungeonMap(int width, int height, boolean hasUpStairs, int floorDepth, long seed) {
        this.width = width;
        this.height = height;
        this.explored = new boolean[height][width];
        this.visible = new boolean[height][width];
        this.terrain = new char[height][width];
        this.interactables = new char[height][width];
        this.monsters = new ArrayList<>();
        this.rand = new Random(seed);
        
        generateRandomMap(hasUpStairs, floorDepth);
    }

    // Restoration constructor for loading saved floors
    public DungeonMap(int width, int height, int startX, int startY, int endX, int endY, 
                      char[][] terrain, char[][] interactables, boolean[][] explored, List<Monster> monsters) {
        this.width = width;
        this.height = height;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.terrain = terrain;
        this.interactables = interactables;
        this.explored = explored;
        this.monsters = monsters;
        this.visible = new boolean[height][width];
        this.rand = new Random(); // Resumed states do not need to preserve procedural seed
    }

    // --- Getters for FileManager Serialization ---
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getStartX() { return startX; }
    public int getStartY() { return startY; }
    public int getEndX() { return endX; }
    public int getEndY() { return endY; }
    public char[][] getTerrain() { return terrain; }
    public char[][] getInteractables() { return interactables; }
    public boolean[][] getExplored() { return explored; }
    public List<Monster> getMonsters() { return monsters; }

    private void generateRandomMap(boolean hasUpStairs, int floorDepth) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                terrain[y][x] = TerminalRenderer.WALL_SOLID;
                interactables[y][x] = ' '; 
            }
        }

        int numRooms = rand.nextInt(4) + 6; 
        int lastRoomCenterX = 1;
        int lastRoomCenterY = 1;
        int firstRoomX = 0, firstRoomY = 0;

        // 1. Generate Rooms and Corridors
        for (int i = 0; i < numRooms; i++) {
            int roomWidth = rand.nextInt(7) + 4;
            int roomHeight = rand.nextInt(5) + 4;
            int roomX = rand.nextInt(width - roomWidth - 2) + 1;
            int roomY = rand.nextInt(height - roomHeight - 2) + 1;
            
            // Fix unbounded loop risk by capping attempts
            if (i == numRooms - 1) {
                int attempts = 0;
                while (attempts < 50 && Math.abs(roomX - firstRoomX) < width / 3 && Math.abs(roomY - firstRoomY) < height / 3) {
                    roomX = rand.nextInt(width - roomWidth - 2) + 1;
                    roomY = rand.nextInt(height - roomHeight - 2) + 1;
                    attempts++;
                }
            }

            for (int y = roomY; y < roomY + roomHeight; y++) {
                for (int x = roomX; x < roomX + roomWidth; x++) {
                    terrain[y][x] = TerminalRenderer.FLOOR_LIT;
                }
            }

            int centerX = roomX + roomWidth / 2;
            int centerY = roomY + roomHeight / 2;

            if (i == 0) {
                firstRoomX = roomX;
                firstRoomY = roomY;
                this.startX = centerX;
                this.startY = centerY;
            } else {
                carveCorridor(lastRoomCenterX, lastRoomCenterY, centerX, centerY);
            }

            lastRoomCenterX = centerX;
            lastRoomCenterY = centerY;
        }

        this.endX = lastRoomCenterX;
        this.endY = lastRoomCenterY;

        // 2. Place structural interactables (Stairs/Mainframe)
        if (hasUpStairs) {
            interactables[this.startY][this.startX] = '<';
        }
        
        if (floorDepth >= 9) {
            interactables[this.endY][this.endX] = 'M';
        } else {
            interactables[this.endY][this.endX] = '>';
        }

        // 3. Collect all free, walkable coordinates to prevent overlap
        List<int[]> freeCoords = new ArrayList<>();
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (terrain[y][x] == TerminalRenderer.FLOOR_LIT && interactables[y][x] == ' ') {
                    // Strictly protect start and end tiles
                    if ((x == this.startX && y == this.startY) || (x == this.endX && y == this.endY)) continue;
                    freeCoords.add(new int[]{x, y});
                }
            }
        }

        Collections.shuffle(freeCoords, rand);

        // 4. Place items and traps (maintaining original density)
        int itemsToSpawn = Math.max(0, numRooms - 2);
        for (int i = 0; i < itemsToSpawn && !freeCoords.isEmpty(); i++) {
            int[] coord = freeCoords.remove(freeCoords.size() - 1);
            int roll = rand.nextInt(100);
            if (roll < 15) interactables[coord[1]][coord[0]] = '$';      
            else if (roll < 40) interactables[coord[1]][coord[0]] = 'T'; 
            else if (roll < 55) interactables[coord[1]][coord[0]] = 'F'; 
            else interactables[coord[1]][coord[0]] = '?';                
        }

        int healthPackCount = rand.nextInt(3); 
        for (int h = 0; h < healthPackCount && !freeCoords.isEmpty(); h++) {
            int[] coord = freeCoords.remove(freeCoords.size() - 1);
            interactables[coord[1]][coord[0]] = 'H';
        }

        // 5. Place Monsters
        int bugCount = rand.nextInt(3) + 1; 
        for (int b = 0; b < bugCount && !freeCoords.isEmpty(); b++) {
            int[] coord = freeCoords.remove(freeCoords.size() - 1);
            monsters.add(new Monster(coord[0], coord[1], 'B', "\033[38;5;196m", "Logic Bug", 0.70, 5, 8, 10 + (floorDepth * 2)));
        }

        int trojanCount = rand.nextInt(2) + 1; 
        for (int t = 0; t < trojanCount && !freeCoords.isEmpty(); t++) {
            int[] coord = freeCoords.remove(freeCoords.size() - 1);
            monsters.add(new Monster(coord[0], coord[1], 'V', "\033[38;5;196m", "Trojan Virus", 0.45, 15, 25, 25 + (floorDepth * 3)));
        }

        // Place System Overlord
        if (floorDepth >= 9) {
            boolean placedOverlord = false;
            int[][] adjacent = { {0,1}, {1,0}, {0,-1}, {-1,0}, {1,1}, {-1,-1}, {1,-1}, {-1,1} };
            
            // Prefer placing boss directly adjacent to mainframe
            for (int[] offset : adjacent) {
                int ox = this.endX + offset[0];
                int oy = this.endY + offset[1];
                if (isWalkable(ox, oy) && interactables[oy][ox] == ' ' && !hasMonsterAt(ox, oy) && !(ox == startX && oy == startY)) {
                    monsters.add(new Monster(ox, oy, 'O', "\033[38;5;201m", "System Overlord", 0.75, 15, 30, 100));
                    final int fox = ox;
                    final int foy = oy;
                    freeCoords.removeIf(c -> c[0] == fox && c[1] == foy);
                    placedOverlord = true;
                    break;
                }
            }
            
            // Fallback to random free coordinate if blocked
            if (!placedOverlord && !freeCoords.isEmpty()) {
                int[] coord = freeCoords.remove(freeCoords.size() - 1);
                monsters.add(new Monster(coord[0], coord[1], 'O', "\033[38;5;201m", "System Overlord", 0.75, 15, 30, 100));
            }
        }
    }

    private void carveCorridor(int x1, int y1, int x2, int y2) {
        int x = x1;
        int y = y1;
        while (x != x2) { terrain[y][x] = TerminalRenderer.FLOOR_LIT; x += (x < x2) ? 1 : -1; }
        while (y != y2) { terrain[y][x] = TerminalRenderer.FLOOR_LIT; y += (y < y2) ? 1 : -1; }
    }

    public boolean isWalkable(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return false;
        return terrain[y][x] != TerminalRenderer.WALL_SOLID;
    }

    public boolean hasMonsterAt(int x, int y) {
        return getMonsterAt(x, y) != null;
    }

    public Monster getMonsterAt(int x, int y) {
        for (Monster m : monsters) if (m.getX() == x && m.getY() == y) return m;
        return null;
    }
    
    public void removeMonster(Monster m) {
        monsters.remove(m);
    }

    public char getInteractableAt(int x, int y) {
        return interactables[y][x];
    }

    public void triggerTile(Player player, EventManager events) {
        int px = player.getX();
        int py = player.getY();
        char item = interactables[py][px];
        
        if (item != ' ') {
            if (item == '$') {
                events.publishEvent("Acquired Data Fragment (+100 Score).", EventManager.EventType.LOOT);
                player.addScore(100);
                player.addItem(new Item("Data Fragment", 100));
                interactables[py][px] = ' '; 
            } else if (item == 'F') {
                events.publishEvent("Found Firewall Patch! Use in inventory to block a trap.", EventManager.EventType.LOOT);
                player.addItem(new Item("Firewall Patch", 50));
                interactables[py][px] = ' ';
            } else if (item == 'T') {
                if (player.isFirewallPatchActive()) {
                    events.publishEvent("Firewall Patch activated! Trap was nullified.", EventManager.EventType.SYSTEM);
                    player.setFirewallPatchActive(false);
                } else {
                    events.publishEvent("Red Firewall Trap triggered! Severe -35 HP damage.", EventManager.EventType.DAMAGE);
                    player.takeDamage(35);
                }
                interactables[py][px] = ' '; 
            } else if (item == '?') {
                int chance = rand.nextInt(100);
                if (chance < 33) {
                    events.publishEvent("Question Mark contained a Corrupted Cache.", EventManager.EventType.LOOT);
                    player.addItem(new Item("Corrupted Cache", 25));
                } else if (chance < 66) {
                    events.publishEvent("Question Mark Trap sprung! -5 HP damage.", EventManager.EventType.DAMAGE);
                    player.takeDamage(5);
                } else {
                    events.publishEvent("Question Mark contained a Health Syringe.", EventManager.EventType.LOOT);
                    player.addItem(new Item("Health Syringe", 5));
                }
                interactables[py][px] = ' ';
            } else if (item == 'H') {
                player.heal(25);
                events.publishEvent("Found Rare Health Patch. Restored 25 HP.", EventManager.EventType.LOOT);
                interactables[py][px] = ' '; 
            } else if (item == '>' || item == '<' || item == 'M') {
                events.publishEvent("Terminal found. Press 'e' to interact.", EventManager.EventType.SYSTEM);
            }
        }
    }

    public void calculateFOV(int playerX, int playerY, int radius) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                visible[y][x] = false;
            }
        }

        if (radius < 0 || playerX < 0 || playerX >= width || playerY < 0 || playerY >= height) return;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy > radius * radius) continue;

                int targetX = playerX + dx;
                int targetY = playerY + dy;
                if (targetX < 0 || targetX >= width || targetY < 0 || targetY >= height) continue;

                int deltaX = Math.abs(dx);
                int deltaY = Math.abs(dy);
                traceRayRecursive(playerX, playerY, targetX, targetY,
                        deltaX, deltaY, Integer.compare(targetX, playerX),
                        Integer.compare(targetY, playerY), deltaX - deltaY);
            }
        }
    }

    private void traceRayRecursive(int x, int y, int targetX, int targetY, int deltaX, int deltaY, int stepX, int stepY, int error) {
        visible[y][x] = true;
        explored[y][x] = true;

        if (terrain[y][x] == TerminalRenderer.WALL_SOLID || (x == targetX && y == targetY)) return;

        int twiceError = 2 * error;
        int nextX = x;
        int nextY = y;
        int nextError = error;

        if (twiceError > -deltaY) {
            nextError -= deltaY;
            nextX += stepX;
        }
        if (twiceError < deltaX) {
            nextError += deltaX;
            nextY += stepY;
        }

        if (nextX != x && nextY != y && terrain[y][nextX] == TerminalRenderer.WALL_SOLID && terrain[nextY][x] == TerminalRenderer.WALL_SOLID) return;

        traceRayRecursive(nextX, nextY, targetX, targetY, deltaX, deltaY, stepX, stepY, nextError);
    }

    public boolean isVisible(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height && visible[y][x];
    }

    public boolean isExplored(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height && explored[y][x];
    }

    public void drawToBuffer(TerminalRenderer renderer, int xOffset, int yOffset) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!explored[y][x]) {
                    renderer.drawChar(x + xOffset, y + yOffset, TerminalRenderer.FOG_HEAVY, "\033[38;5;236m");
                } else {
                    if (terrain[y][x] == TerminalRenderer.WALL_SOLID) {
                        renderer.drawChar(x + xOffset, y + yOffset, TerminalRenderer.WALL_SOLID,
                                visible[y][x] ? "\033[38;5;244m" : "\033[38;5;238m");
                    } else {
                        renderer.drawChar(x + xOffset, y + yOffset, TerminalRenderer.FLOOR_LIT,
                                visible[y][x] ? "\033[38;5;239m" : "\033[38;5;236m");
                    }
                    
                    if (interactables[y][x] != ' ') {
                        String color = switch (interactables[y][x]) {
                            case 'H' -> "\033[38;5;46m";  
                            case 'T' -> "\033[38;5;196m"; 
                            case '?' -> "\033[38;5;207m"; 
                            case '$' -> "\033[38;5;226m"; 
                            case 'M' -> "\033[38;5;51m";  
                            case 'F' -> "\033[38;5;51m";  
                            default -> "\033[38;5;255m";  
                        };
                        renderer.drawChar(x + xOffset, y + yOffset, interactables[y][x],
                                visible[y][x] ? color : "\033[38;5;238m");
                    }
                }
            }
        }

        for (Monster m : monsters) {
            if (isVisible(m.getX(), m.getY())) {
                renderer.drawChar(m.getX() + xOffset, m.getY() + yOffset, m.getSymbol(), m.getColor());
            }
        }
    }
}

