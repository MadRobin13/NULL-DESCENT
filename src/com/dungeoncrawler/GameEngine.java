package com.dungeoncrawler;

import com.dungeoncrawler.engine.TerminalRenderer;
import com.dungeoncrawler.engine.EventManager;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GameEngine {
    private boolean isRunning;
    private final TerminalRenderer renderer;
    private final EventManager eventManager;
    private final Player hero;
    
    private final List<DungeonMap> floors;
    private int currentFloorIndex; 

    public GameEngine() {
        this.renderer = new TerminalRenderer(80, 24);
        this.eventManager = new EventManager();
        this.hero = new Player(0, 0); 
        this.floors = new ArrayList<>();
        this.isRunning = true;
        
        initializeNewRun();
    }
    
    private void initializeNewRun() {
        this.floors.clear();
        this.currentFloorIndex = 0;
        this.hero.reset();
        
        DungeonMap startMap = new DungeonMap(58, 19, false);
        this.floors.add(startMap);
        this.hero.setPosition(startMap.getStartX(), startMap.getStartY());
        
        eventManager.publishEvent("OS Booted. Infiltration begun.", EventManager.EventType.SYSTEM);
    }

    public void start() {
        try (Terminal terminal = TerminalBuilder.builder().jna(true).system(true).build()) {
            terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();
            System.out.print("\033[2J"); 

            while (isRunning) {
                updateLogic();
                renderFrame();
                
                int readChar = reader.read();
                if (readChar != -1) {
                    processInput((char) readChar);
                }
            }
        } catch (IOException e) {
            System.err.println("Terminal init failed: " + e.getMessage());
        } finally {
            System.out.print("\033[2J\033[H"); 
            System.out.println("Connection terminated. Final Score: " + hero.getScore());
        }
    }

    private DungeonMap getCurrentMap() {
        return floors.get(currentFloorIndex);
    }

    private void updateLogic() {
        if (hero.getHp() <= 0) {
            eventManager.publishEvent("CRITICAL FAILURE. CONNECTION LOST. Press 'r' to restart.", EventManager.EventType.DAMAGE);
        } else {
            getCurrentMap().calculateFOV(hero.getX(), hero.getY(), 6); 
        }
    }

    private void renderFrame() {
        renderer.clearBuffer();
        getCurrentMap().drawToBuffer(renderer);
        
        if (hero.getHp() <= 0) {
            renderer.drawChar(hero.getX(), hero.getY(), 'X', "\033[38;5;196m");
        } else {
            renderer.drawChar(hero.getX(), hero.getY(), '@', "\033[38;5;51m"); 
        }
        
        drawHUD();
        renderer.render();
        System.out.flush(); 
    }

    private void drawHUD() {
        // --- Draw Legend on the right side ---
        int lX = 60;
        renderer.drawString(lX, 1,  "--- LEGEND ---", "\033[38;5;255m");
        renderer.drawString(lX, 3,  "@ Player", "\033[38;5;51m");
        renderer.drawString(lX, 4,  "< Up Stairs", "\033[38;5;255m");
        renderer.drawString(lX, 5,  "> Down Stairs", "\033[38;5;255m");
        renderer.drawString(lX, 6,  "$ Data (Score)", "\033[38;5;226m");
        renderer.drawString(lX, 7,  "H Health Patch", "\033[38;5;46m");
        renderer.drawString(lX, 8,  "T Firewall Trap", "\033[38;5;196m");
        renderer.drawString(lX, 10, "B Logic Bug", "\033[38;5;196m");
        renderer.drawString(lX, 11, "  (Fast, Weak)", "\033[38;5;245m");
        renderer.drawString(lX, 12, "V Trojan Virus", "\033[38;5;196m");
        renderer.drawString(lX, 13, "  (Slow, Strong)", "\033[38;5;245m");
        renderer.drawString(lX, 15, "? Unknown Tile", "\033[38;5;207m");
        
        // --- Draw Lower HUD ---
        String stats = String.format(" HP: %d/%d | FLOOR: %d | SCORE: %d ", 
            hero.getHp(), hero.getMaxHp(), (currentFloorIndex + 1), hero.getScore());
            
        renderer.drawString(0, 19, "==============================================================================", "\033[38;5;255m");
        
        String hpColor = (hero.getHp() <= 25) ? "\033[38;5;196m" : "\033[38;5;11m"; 
        renderer.drawString(2, 19, stats, hpColor); 
        
        // Clear previous logs to prevent artifacting/weird whitespaces
        for (int i = 20; i < 24; i++) {
            renderer.drawString(0, i, "                                                                              ", "\033[38;5;255m");
        }

        // Draw new logs with correct colors applied HERE instead of inside the string
        int logY = 20;
        for (EventManager.EventLog log : eventManager.getRecentLogs()) {
            String logColor = switch (log.type) {
                case DAMAGE -> "\033[38;5;196m"; 
                case LOOT -> "\033[38;5;226m";   
                case COMBAT -> "\033[38;5;208m"; 
                case SYSTEM -> "\033[38;5;245m"; 
                default -> "\033[38;5;255m";     
            };
            renderer.drawString(2, logY++, log.message, logColor);
        }
    }

    private void processInput(char command) {
        if (hero.getHp() <= 0) {
            if (Character.toLowerCase(command) == 'r') initializeNewRun();
            if (Character.toLowerCase(command) == 'q') isRunning = false;
            return;
        }

        boolean actionTaken = false;
        switch (Character.toLowerCase(command)) {
            case 'w' -> actionTaken = hero.move(0, -1, getCurrentMap(), eventManager);
            case 's' -> actionTaken = hero.move(0, 1, getCurrentMap(), eventManager);
            case 'a' -> actionTaken = hero.move(-1, 0, getCurrentMap(), eventManager);
            case 'd' -> actionTaken = hero.move(1, 0, getCurrentMap(), eventManager);
            case 'e' -> attemptStaircase();
            case 'r' -> initializeNewRun(); 
            case 'q' -> isRunning = false;
        }

        if (actionTaken) {
            processMonsterTurns();
        }
    }
    
    private void processMonsterTurns() {
        DungeonMap map = getCurrentMap();
        for (Monster m : map.getMonsters()) {
            
            if (Math.random() > m.getSpeed()) continue; 
            
            if (Math.abs(m.getX() - hero.getX()) + Math.abs(m.getY() - hero.getY()) <= 8) {
                if (Math.abs(m.getX() - hero.getX()) <= 1 && Math.abs(m.getY() - hero.getY()) <= 1) {
                    
                    int dmg = m.getMinDmg() + (int)(Math.random() * (m.getMaxDmg() - m.getMinDmg() + 1));
                    hero.takeDamage(dmg);
                    eventManager.publishEvent(m.getName() + " hits you for " + dmg + " dmg!", EventManager.EventType.DAMAGE);
                } else {
                    m.moveTowards(hero.getX(), hero.getY(), map);
                }
            }
        }
    }

    private void attemptStaircase() {
        char currentTile = getCurrentMap().getInteractableAt(hero.getX(), hero.getY());
        
        if (currentTile == '>') { 
            if (currentFloorIndex >= 9) { // Win condition at Floor 10
                System.out.print("\033[2J\033[H");
                System.out.println("\033[38;5;46mMAINFRAME HACKED. YOU WIN!\033[0m");
                System.out.println("Final Score: " + hero.getScore());
                isRunning = false;
                return;
            }

            eventManager.publishEvent("Descending deeper...", EventManager.EventType.SYSTEM);
            currentFloorIndex++;
            
            if (currentFloorIndex >= floors.size()) {
                floors.add(new DungeonMap(58, 19, true));
            }
            DungeonMap nextMap = getCurrentMap();
            hero.setPosition(nextMap.getStartX(), nextMap.getStartY()); 
            
        } else if (currentTile == '<') { 
            if (currentFloorIndex > 0) {
                eventManager.publishEvent("Returning to previous sector...", EventManager.EventType.SYSTEM);
                currentFloorIndex--;
                DungeonMap prevMap = getCurrentMap();
                hero.setPosition(prevMap.getEndX(), prevMap.getEndY()); 
            }
        } else {
            eventManager.publishEvent("No valid terminal here to interact with.", EventManager.EventType.SYSTEM);
        }
    }
}