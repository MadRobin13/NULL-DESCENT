package com.dungeoncrawler;

import com.dungeoncrawler.engine.TerminalRenderer;
import com.dungeoncrawler.engine.EventManager;
import com.dungeoncrawler.engine.MusicPlayer;
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
    private final MusicPlayer musicPlayer;
    
    private final List<DungeonMap> floors;
    private int currentFloorIndex; 
    
    private final String[] headerText = {
        "  _   _ _   _ _      _          ____  _____  _____  _____  _____  _   _  _____ ",
        " | \\ | | | | | |    | |        |  _ \\|  ___|/  ___|/  __ \\|  ___|| \\ | ||_   _|",
        " |  \\| | | | | |    | |  _____ | | | | |__  \\ `--. | /  \\/| |__  |  \\| |  | |  ",
        " | . ` | | | | |    | | |_____|| | | |  __|  `--. \\| |    |  __| | . ` |  | |  ",
        " | |\\  | |_| | |____| |____    | |/ /| |___ /\\__/ /| \\__/\\| |___ | |\\  |  | |  ",
        " \\_| \\_/\\___/\\_____/\\_____/    |___/ \\____/ \\____/  \\____/\\____/ \\_| \\_/  \\_/  ",
        "",
        " System Breach Detected...",
        "",
        " You initialize in the darkened sectors of the Core Network late one cycle to find yourself",
        " trapped. All of the outbound ports are sealed and at the main gateway there appears to be an",
        " active breach to a subnet that smells like fried silicon and malware, as breaches generally do.",
        " You hear disturbing fan noises from the server that are slowly getting louder. You get the",
        " distinct impression that if those noises get too loud some very bad things will happen. Spooky.",
        "",
        " Surrounding the gateway are a series of directories that look like quarantine bins for corrupted",
        " files. There's a log scrawled on the terminal in an unknown hash: \"Root Keys...\" You remember",
        " from your cybersecurity class that a Root Key is a piece of code sought by both the white hats",
        " and black hats on the web: Admin Privileges, Master Passwords, Encryption Tokens. All Root Keys!",
        " Perhaps if you were to collect all the Root Keys and place them in the quarantine bins the",
        " breach would close? It's worth a shot!",
        "",
        " Setting off into the network, you find a number of unlocked ports equal to the number of bins.",
        " Coincidence? I think NOT! You decide to enter each sector and see if you can find the Root Keys.",
        "",
        " Let's begin! [W,A,S,D to Move | E to Interact | Q to Quit]"
    };

    public GameEngine() {
        this.renderer = new TerminalRenderer(100, 55);
        this.eventManager = new EventManager();
        this.hero = new Player(0, 0); 
        this.floors = new ArrayList<>();
        this.musicPlayer = new MusicPlayer();
        this.isRunning = true;
        
        this.musicPlayer.playLoop("bgm.wav");
        
        initializeNewRun();
    }
    
    private void initializeNewRun() {
        this.floors.clear();
        this.currentFloorIndex = 0;
        this.hero.reset();
        
        DungeonMap startMap = new DungeonMap(58, 19, false, 0);
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
            musicPlayer.stop();
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
        
        int yOffset = 0;
        for (String line : headerText) {
            renderer.drawString(0, yOffset++, line, "\033[38;5;129m"); 
        }
        
        int mapStartY = yOffset + 1; 
        
        getCurrentMap().drawToBuffer(renderer, 0, mapStartY);
        
        if (hero.getHp() <= 0) {
            renderer.drawChar(hero.getX(), hero.getY() + mapStartY, 'X', "\033[38;5;196m");
        } else {
            renderer.drawChar(hero.getX(), hero.getY() + mapStartY, '@', "\033[38;5;51m"); 
        }
        
        drawHUD(mapStartY);
        
        renderer.render();
        System.out.flush(); 
    }

    private void drawHUD(int mapStartY) {
        int lX = 60;
        
        renderer.drawString(lX, mapStartY,      "--- LEGEND ---", "\033[38;5;255m");
        renderer.drawString(lX, mapStartY + 1,  "@ Player", "\033[38;5;51m");
        renderer.drawString(lX, mapStartY + 2,  "< Up Stairs", "\033[38;5;255m");
        renderer.drawString(lX, mapStartY + 3,  "> Down Stairs", "\033[38;5;255m");
        renderer.drawString(lX, mapStartY + 4,  "M Mainframe", "\033[38;5;51m");
        renderer.drawString(lX, mapStartY + 5,  "$ Data (Score)", "\033[38;5;226m");
        renderer.drawString(lX, mapStartY + 6,  "H Health Patch", "\033[38;5;46m");
        renderer.drawString(lX, mapStartY + 7,  "T Firewall Trap", "\033[38;5;196m");
        renderer.drawString(lX, mapStartY + 8,  "  (Inanimate, Fatal)", "\033[38;5;245m");
        renderer.drawString(lX, mapStartY + 9,  "B Logic Bug", "\033[38;5;196m");
        renderer.drawString(lX, mapStartY + 10, "  (Fast, Weak)", "\033[38;5;245m");
        renderer.drawString(lX, mapStartY + 11, "V Trojan Virus", "\033[38;5;196m");
        renderer.drawString(lX, mapStartY + 12, "  (Slow, Strong)", "\033[38;5;245m");
        renderer.drawString(lX, mapStartY + 13, "O System Overlord", "\033[38;5;201m");
        renderer.drawString(lX, mapStartY + 14, "? Unknown Tile", "\033[38;5;207m");
        
        int hudStartY = mapStartY + 20;
        
        String stats = String.format(" HP: %d/%d | SECTOR: %d | SCORE: %d ", 
            hero.getHp(), hero.getMaxHp(), (currentFloorIndex + 1), hero.getScore());
            
        renderer.drawString(0, hudStartY, "==============================================================================", "\033[38;5;255m");
        
        String hpColor = (hero.getHp() <= 25) ? "\033[38;5;196m" : "\033[38;5;11m"; 
        renderer.drawString(2, hudStartY, stats, hpColor); 
        
        int logY = hudStartY + 1;
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
            case 'e' -> attemptInteraction(); 
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
            
            int distToHero = Math.abs(m.getX() - hero.getX()) + Math.abs(m.getY() - hero.getY());
            
            if (distToHero <= 8) {
                if (distToHero == 1) {
                    int dmg = m.getMinDmg() + (int)(Math.random() * (m.getMaxDmg() - m.getMinDmg() + 1));
                    hero.takeDamage(dmg);
                    eventManager.publishEvent(m.getName() + " corrupted you for " + dmg + " dmg!", EventManager.EventType.DAMAGE);
                } else {
                    m.moveTowards(hero.getX(), hero.getY(), map);
                }
            }
        }
    }

    private void attemptInteraction() {
        char currentTile = getCurrentMap().getInteractableAt(hero.getX(), hero.getY());
        
        if (currentTile == 'M') { 
            boolean bossAlive = getCurrentMap().getMonsters().stream().anyMatch(m -> m.getSymbol() == 'O');
            if (bossAlive) {
                eventManager.publishEvent("ACCESS DENIED: System Overlord is still active!", EventManager.EventType.SYSTEM);
            } else {
                System.out.print("\033[2J\033[H");
                System.out.println("\033[38;5;46mMAINFRAME HACKED. MISSION ACCOMPLISHED!\033[0m");
                hero.addScore(5000); 
                System.out.println("Final Score: " + hero.getScore());
                isRunning = false;
            }
            
        } else if (currentTile == '>') { 
            eventManager.publishEvent("Descending deeper into the subnet...", EventManager.EventType.SYSTEM);
            currentFloorIndex++;
            
            if (currentFloorIndex >= floors.size()) {
                floors.add(new DungeonMap(58, 19, true, currentFloorIndex));
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
            eventManager.publishEvent("No valid port here to interact with.", EventManager.EventType.SYSTEM);
        }
    }
}