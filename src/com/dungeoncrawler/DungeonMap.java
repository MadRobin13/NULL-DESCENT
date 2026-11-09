package com.dungeoncrawler;

import com.dungeoncrawler.engine.TerminalRenderer;
import com.dungeoncrawler.engine.EventManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DungeonMap {
    private final int width, height;
    private final boolean[][] explored;
    private final char[][] terrain;
    private final char[][] interactables;
    private final boolean[][] itemRevealed; 
    private final List<Monster> monsters;
    
    private int startX, startY;
    private int endX, endY;
    private final Random rand = new Random();

    public DungeonMap(int width, int height, boolean hasUpStairs, int floorDepth) {
        this.width = width;
        this.height = height;
        this.explored = new boolean[height][width];
        this.terrain = new char[height][width];
        this.interactables = new char[height][width];
        this.itemRevealed = new boolean[height][width];
        this.monsters = new ArrayList<>();
        
        generateRandomMap(hasUpStairs, floorDepth);
    }

    public int getStartX() { return startX; }
    public int getStartY() { return startY; }
    public int getEndX() { return endX; }
    public int getEndY() { return endY; }

    private void generateRandomMap(boolean hasUpStairs, int floorDepth) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                terrain[y][x] = TerminalRenderer.WALL_SOLID;
                interactables[y][x] = ' '; 
                itemRevealed[y][x] = false;
            }
        }

        int numRooms = rand.nextInt(4) + 6; 
        int lastRoomCenterX = 1;
        int lastRoomCenterY = 1;

        for (int i = 0; i < numRooms; i++) {
            int roomWidth = rand.nextInt(7) + 4;
            int roomHeight = rand.nextInt(5) + 4;
            int roomX = rand.nextInt(width - roomWidth - 2) + 1;
            int roomY = rand.nextInt(height - roomHeight - 2) + 1;

            for (int y = roomY; y < roomY + roomHeight; y++) {
                for (int x = roomX; x < roomX + roomWidth; x++) {
                    terrain[y][x] = TerminalRenderer.FLOOR_LIT;
                }
            }

            int centerX = roomX + roomWidth / 2;
            int centerY = roomY + roomHeight / 2;

            if (i == 0) {
                this.startX = centerX;
                this.startY = centerY;
                if (hasUpStairs) {
                    interactables[centerY][centerX] = '<';
                    itemRevealed[centerY][centerX] = true; 
                }
            } else {
                carveCorridor(lastRoomCenterX, lastRoomCenterY, centerX, centerY);
                
                int roll = rand.nextInt(100);
                if (roll < 30) interactables[centerY][centerX] = '$'; 
                else if (roll < 50) interactables[centerY][centerX] = 'T'; 
                else if (roll < 70) interactables[centerY][centerX] = 'H'; 
                
                if (rand.nextInt(100) < (40 + floorDepth * 3)) {
                    if (rand.nextBoolean()) {
                        monsters.add(new Monster(centerX + 1, centerY, 'B', "\033[38;5;196m", "Logic Bug", 0.75, 3, 8, 20 + (floorDepth * 2)));
                    } else {
                        monsters.add(new Monster(centerX + 1, centerY, 'V', "\033[38;5;196m", "Trojan Virus", 0.45, 10, 20, 45 + (floorDepth * 5)));
                    }
                }
            }

            lastRoomCenterX = centerX;
            lastRoomCenterY = centerY;
        }

        this.endX = lastRoomCenterX;
        this.endY = lastRoomCenterY;
        if (floorDepth >= 9) {
            interactables[lastRoomCenterY][lastRoomCenterX] = 'M';
        } else {
            interactables[lastRoomCenterY][lastRoomCenterX] = '>';
        }
        itemRevealed[lastRoomCenterY][lastRoomCenterX] = true; 
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

    public List<Monster> getMonsters() { return monsters; }

    public char getInteractableAt(int x, int y) {
        return interactables[y][x];
    }

    public void triggerTile(Player player, EventManager events) {
        int px = player.getX();
        int py = player.getY();
        char item = interactables[py][px];
        
        if (item != ' ') {
            itemRevealed[py][px] = true; 

            if (item == '$') {
                events.publishEvent("Acquired Data Fragment (+100 Score).", EventManager.EventType.LOOT);
                player.addScore(100);
                interactables[py][px] = ' '; 
            } else if (item == 'T') {
                events.publishEvent("Security Firewall triggered! Took 15 damage.", EventManager.EventType.DAMAGE);
                player.takeDamage(15);
                interactables[py][px] = ' '; 
            } else if (item == 'H') {
                player.heal(25);
                events.publishEvent("Found System Patch. Restored 25 HP.", EventManager.EventType.LOOT);
                interactables[py][px] = ' '; 
            } else if (item == '>' || item == '<' || item == 'M') {
                events.publishEvent("Terminal found. Press 'e' to interact.", EventManager.EventType.SYSTEM);
            }
        }
    }

    public void calculateFOV(int playerX, int playerY, int radius) {
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radius * radius) {
                    int checkX = playerX + x;
                    int checkY = playerY + y;
                    if (checkX >= 0 && checkX < width && checkY >= 0 && checkY < height) {
                        explored[checkY][checkX] = true;
                    }
                }
            }
        }
    }

    public void drawToBuffer(TerminalRenderer renderer, int xOffset, int yOffset) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!explored[y][x]) {
                    renderer.drawChar(x + xOffset, y + yOffset, TerminalRenderer.FOG_HEAVY, "\033[38;5;236m");
                } else {
                    if (terrain[y][x] == TerminalRenderer.WALL_SOLID) {
                        renderer.drawChar(x + xOffset, y + yOffset, TerminalRenderer.WALL_SOLID, "\033[38;5;244m");
                    } else {
                        renderer.drawChar(x + xOffset, y + yOffset, TerminalRenderer.FLOOR_LIT, "\033[38;5;239m");
                    }
                    
                    if (interactables[y][x] != ' ') {
                        if (!itemRevealed[y][x]) {
                            renderer.drawChar(x + xOffset, y + yOffset, '?', "\033[38;5;207m"); 
                        } else {
                            String color = switch (interactables[y][x]) {
                                case 'H' -> "\033[38;5;46m";  
                                case 'T' -> "\033[38;5;196m"; 
                                case '$' -> "\033[38;5;226m"; 
                                case 'M' -> "\033[38;5;51m";
                                default -> "\033[38;5;255m";  
                            };
                            renderer.drawChar(x + xOffset, y + yOffset, interactables[y][x], color);
                        }
                    }
                }
            }
        }

        for (Monster m : monsters) {
            if (explored[m.getY()][m.getX()]) {
                renderer.drawChar(m.getX() + xOffset, m.getY() + yOffset, m.getSymbol(), m.getColor());
            }
        }
    }
}