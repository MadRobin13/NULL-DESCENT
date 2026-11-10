package com.dungeoncrawler;

import com.dungeoncrawler.engine.FileManager;
import com.dungeoncrawler.engine.TerminalRenderer;
import com.dungeoncrawler.engine.EventManager;
import com.dungeoncrawler.engine.MusicPlayer;
import com.dungeoncrawler.engine.JumpscareManager;
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
    private Player hero;
    private final MusicPlayer musicPlayer;

    private List<DungeonMap> floors;
    private int currentFloorIndex;

    private boolean isInventoryMenuOpen = false;
    private boolean isHelpMenuOpen = false;
    private boolean flashingEnabled = true;
    private boolean musicPausedForDeath = false;
    private Terminal activeTerminal;
    private int inventoryPage = 0;

    // Game state flags
    private boolean isVictorious = false;
    private boolean deathMessagePublished = false;
    private boolean jumpscareTriggered = false;
    private final boolean testMode;
    private final String saveFilePath;

    private final String[] headerText = {
        "  _    _ _    _ _      _          ____  _____  _____  _____  _____  _    _  _____ ",
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
        " Controls: WASD Move | E Interact | I Inventory | H Help | K Save | L Load | F Flashes | Q Quit"
    };

    public GameEngine() {
        this(false, "savegame.txt");
    }

    // Package-private test entry points keep file I/O isolated from the real savegame.
    GameEngine(boolean testMode) {
        this(testMode, "savegame.txt");
    }

    GameEngine(boolean testMode, String saveFilePath) {
        this.testMode = testMode;
        this.saveFilePath = java.util.Objects.requireNonNull(saveFilePath);
        this.renderer = new TerminalRenderer(100, 55);
        this.eventManager = new EventManager();
        this.musicPlayer = new MusicPlayer();
        this.isRunning = true;

        if (!testMode) {
            this.musicPlayer.playLoop("bgm.wav");
            JumpscareManager.initialize();
        }

        initializeNewRun();
    }

    private void initializeNewRun() {
        this.hero = new Player(0, 0);
        this.floors = new ArrayList<>();
        this.currentFloorIndex = 0;
        this.isInventoryMenuOpen = false;
        this.isHelpMenuOpen = false;
        this.inventoryPage = 0;

        // Reset state flags
        this.isVictorious = false;
        this.deathMessagePublished = false;
        this.jumpscareTriggered = false;
        this.eventManager.clear();

        DungeonMap startMap = new DungeonMap(58, 19, false, 0);
        this.floors.add(startMap);
        this.hero.setPosition(startMap.getStartX(), startMap.getStartY());
        resumeMusicAfterDeath();

        eventManager.publishEvent("OS Booted. Infiltration begun.", EventManager.EventType.SYSTEM);
    }

    public void start() {
        try (Terminal terminal = TerminalBuilder.builder().jna(true).system(true).build()) {
            this.activeTerminal = terminal;
            terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            System.out.print("\033[2J\033[?25l"); // Clear screen and hide the cursor.
            System.out.flush();

            // Redraw on input or resize, not continuously during idle time.
            int previousWidth = -1;
            int previousHeight = -1;
            boolean redraw = true;
            while (isRunning) {
                int currentWidth = terminal.getWidth();
                int currentHeight = terminal.getHeight();
                if (redraw || currentWidth != previousWidth || currentHeight != previousHeight) {
                    updateLogic();
                    renderFrame();
                    previousWidth = currentWidth;
                    previousHeight = currentHeight;
                    redraw = false;
                }

                int readChar = reader.read(250);
                if (readChar == NonBlockingReader.READ_EXPIRED) continue;
                if (readChar == NonBlockingReader.EOF) break;

                char command = (char) readChar;
                if (terminalTooSmall(currentWidth, currentHeight)
                        && Character.toLowerCase(command) != 'q') {
                    // Ignore movement and other inputs while the full display cannot fit.
                    continue;
                }
                processInput(command);
                redraw = true;
            }
        } catch (IOException e) {
            System.err.println("Terminal init failed: " + e.getMessage());
        } finally {
            activeTerminal = null;
            if (!testMode) musicPlayer.close();
            System.out.print("\033[0m\033[?7h\033[?25h\033[2J\033[H");
            System.out.println("Connection terminated. Final Score: " + hero.getScore());
            System.out.flush();
        }
    }

    static boolean terminalTooSmall(int width, int height) {
        return width < 100 || height < 55;
    }

    private void renderResizeWarning(int width, int height) {
        // Respect even very narrow terminal widths; don't wrap the warning itself.
        int usableWidth = Math.max(1, width - 1);
        int availableRows = Math.max(1, height);
        String[] lines = {
            "NULL//DESCENT - TERMINAL TOO SMALL",
            "Requires at least 100 columns x 55 rows.",
            "Current size: " + width + " x " + height,
            "Resize the terminal to continue. Q to quit."
        };
        StringBuilder message = new StringBuilder("\033[0m\033[2J\033[H");
        for (int i = 0; i < Math.min(lines.length, availableRows); i++) {
            message.append("\033[").append(i + 1).append(";1H");
            message.append(lines[i], 0, Math.min(usableWidth, lines[i].length()));
        }
        System.out.print(message);
        System.out.flush();
    }

    private DungeonMap getCurrentMap() {
        return floors.get(currentFloorIndex);
    }

    private void updateLogic() {
        if (isVictorious) {
            // Do nothing, state frozen
        } else if (hero.getHp() <= 0) {
            if (!deathMessagePublished) {
                eventManager.publishEvent("CRITICAL FAILURE. CONNECTION LOST. Press 'r' to restart.", EventManager.EventType.DAMAGE);
                deathMessagePublished = true;
                if (!musicPausedForDeath) {
                    musicPlayer.stop();
                    musicPausedForDeath = true;
                }
            }
        } else {
            getCurrentMap().calculateFOV(hero.getX(), hero.getY(), 6);
        }
    }

    // Package-private to verify overlays without starting JLine.
    void renderFrame() {
        if (activeTerminal != null && !testMode) {
            int width = activeTerminal.getWidth();
            int height = activeTerminal.getHeight();
            if (terminalTooSmall(width, height)) {
                renderResizeWarning(width, height);
                return;
            }
        }
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

        if (isVictorious) {
            drawVictoryScreen(mapStartY);
        } else if (hero.getHp() <= 0) {
            drawDeathScreen(mapStartY);
        } else if (isInventoryMenuOpen) {
            drawInventoryMenu(mapStartY);
        }

        if (isHelpMenuOpen) {
            drawHelpMenu(mapStartY);
        }

        renderer.render();
        System.out.flush();
    }

    private void drawVictoryScreen(int mapStartY) {
        int menuX = 22;
        int menuY = mapStartY + 5;

        String clearLine = " ".repeat(55);
        for (int i = 0; i < 10; i++) {
            renderer.drawString(menuX, menuY + i, clearLine, "\033[0m");
        }

        renderer.drawString(menuX, menuY++, "=======================================================", "\033[38;5;46m");
        renderer.drawString(menuX, menuY++, "        MAINFRAME HACKED. MISSION ACCOMPLISHED!        ", "\033[38;5;46m");
        renderer.drawString(menuX, menuY++, "=======================================================", "\033[38;5;46m");
        renderer.drawString(menuX, menuY++, " Final Score: " + hero.getScore(), "\033[38;5;255m");
        renderer.drawString(menuX, menuY++, "                                                       ", "\033[38;5;255m");
        renderer.drawString(menuX, menuY++, " [R] Restart System                                    ", "\033[38;5;51m");
        renderer.drawString(menuX, menuY++, " [L] Load Saved Run                                    ", "\033[38;5;226m");
        renderer.drawString(menuX, menuY++, " [Q] Disconnect (Quit)                                 ", "\033[38;5;196m");
        renderer.drawString(menuX, menuY++, "=======================================================", "\033[38;5;46m");
    }

    private void drawDeathScreen(int mapStartY) {
        int menuX = 22;
        int menuY = mapStartY + 5;
        for (int i = 0; i < 9; i++) {
            renderer.drawString(menuX, menuY + i, " ".repeat(55), TerminalRenderer.RESET);
        }
        renderer.drawString(menuX, menuY++, "=======================================================", "\033[38;5;196m");
        renderer.drawString(menuX, menuY++, "          CRITICAL FAILURE. CONNECTION LOST.           ", "\033[38;5;196m");
        renderer.drawString(menuX, menuY++, "=======================================================", "\033[38;5;196m");
        renderer.drawString(menuX, menuY++, " Final Score: " + hero.getScore(), "\033[38;5;255m");
        renderer.drawString(menuX, menuY++, "                                                       ", TerminalRenderer.RESET);
        renderer.drawString(menuX, menuY++, " [R] Restart | [L] Load Saved Run | [H] Help            ", "\033[38;5;51m");
        renderer.drawString(menuX, menuY++, " [Q] Disconnect (Quit)                                 ", "\033[38;5;196m");
        renderer.drawString(menuX, menuY, "=======================================================", "\033[38;5;196m");
    }

    private void drawInventoryMenu(int mapStartY) {
        int menuX = 5;
        int menuY = mapStartY + 2;

        String clearLine = " ".repeat(100 - menuX);
        for (int i = 0; i < 14; i++) {
            renderer.drawString(menuX, menuY + i, clearLine, "\033[0m");
        }

        renderer.drawString(menuX, menuY++, "========================= INVENTORY MENU =========================", "\033[38;5;226m");

        List<Item> inv = hero.getInventory();
        int maxPages = Math.max(1, (int)Math.ceil(inv.size() / 5.0));

        renderer.drawString(menuX, menuY++, " Page " + (inventoryPage + 1) + "/" + maxPages + " | [1-5] Use Item | [N] Next | [P] Prev | [I/Q] Close ", "\033[38;5;255m");
        renderer.drawString(menuX, menuY++, "------------------------------------------------------------------", "\033[38;5;255m");

        if (inv.isEmpty()) {
            renderer.drawString(menuX + 2, menuY++, "(Inventory is empty)", "\033[38;5;245m");
            menuY += 9;
        } else {
            int startIdx = inventoryPage * 5;
            for (int i = 0; i < 5; i++) {
                int idx = startIdx + i;
                if (idx < inv.size()) {
                    Item it = inv.get(idx);
                    String action = "None";
                    if (it.getName().equals("Health Syringe")) action = "Restore +5 HP";
                    else if (it.getName().equals("Firewall Patch")) action = "Protect vs 1 Trap";
                    else if (it.getName().equals("Corrupted Cache")) action = "Decrypt (Reward/Penalty)";
                    else if (it.getName().equals("Data Fragment")) action = "Collectible (Score already added)";

                    renderer.drawString(menuX, menuY++, " [" + (i + 1) + "] " + it.getName() + " (Value: " + it.getValue() + ")", "\033[38;5;51m");
                    renderer.drawString(menuX + 5, menuY++, "-> Action: " + action, "\033[38;5;245m");
                } else {
                    renderer.drawString(menuX, menuY++, " [" + (i + 1) + "] ---", "\033[38;5;239m");
                    menuY++;
                }
            }
        }
        renderer.drawString(menuX, menuY, "==================================================================", "\033[38;5;226m");
    }

    private void drawHelpMenu(int mapStartY) {
        final String titleColor = "\033[38;5;226m";
        final String headingColor = "\033[38;5;51m";
        final String textColor = "\033[38;5;255m";
        final String detailColor = "\033[38;5;245m";
        final int rightColumn = 52;

        for (int row = 0; row < 20; row++) {
            renderer.drawString(0, mapStartY + row, " ".repeat(100), TerminalRenderer.RESET);
        }

        renderer.drawString(2, mapStartY, "============================ HELP / CONTROLS ============================", titleColor);
        renderer.drawString(2, mapStartY + 1, "Press H or Q to close. The game pauses while this menu is open.", detailColor);

        renderer.drawString(3, mapStartY + 2, "CONTROLS", headingColor);
        renderer.drawString(rightColumn, mapStartY + 2, "ITEMS & PICKUPS", headingColor);
        renderer.drawString(3, mapStartY + 3, "W/A/S/D  Move; bump enemies to attack", textColor);
        renderer.drawString(rightColumn, mapStartY + 3, "F  Firewall Patch: block next T trap", textColor);
        renderer.drawString(3, mapStartY + 4, "E  Use stairs or the mainframe", textColor);
        renderer.drawString(rightColumn, mapStartY + 4, "T  Firewall Trap: -35 HP", textColor);
        renderer.drawString(3, mapStartY + 5, "I  Open inventory (I/Q closes)", textColor);
        renderer.drawString(rightColumn, mapStartY + 5, "H  Health Patch: +25 HP on pickup", textColor);
        renderer.drawString(3, mapStartY + 6, "1/2  Sort by name / value (ascending)", textColor);
        renderer.drawString(rightColumn, mapStartY + 6, "?  Cache, syringe, or -5 HP trap", textColor);
        renderer.drawString(3, mapStartY + 7, "K  Save current run to savegame.txt", textColor);
        renderer.drawString(rightColumn, mapStartY + 7, "$  Data Fragment: +100 score on pickup", textColor);
        renderer.drawString(3, mapStartY + 8, "L  Load saved run (even after death)", textColor);
        renderer.drawString(rightColumn, mapStartY + 8, "Syringe: use for +5 HP (if injured)", textColor);
        renderer.drawString(3, mapStartY + 9, "R  Restart (unsaved progress is lost)", textColor);
        renderer.drawString(rightColumn, mapStartY + 9, "Corrupted Cache: 50% +50 score", textColor);
        renderer.drawString(3, mapStartY + 10, "Q  Quit game (outside menus)", textColor);
        renderer.drawString(rightColumn, mapStartY + 10, "                 50% -10 HP", textColor);
        renderer.drawString(3, mapStartY + 11, "H  Help | F  Toggle jumpscare flashes", textColor);
        renderer.drawString(rightColumn, mapStartY + 11, "Item value is for sorting, not money", textColor);

        renderer.drawString(3, mapStartY + 13, "INVENTORY", headingColor);
        renderer.drawString(rightColumn, mapStartY + 13, "THREATS & EXPLORATION", headingColor);
        renderer.drawString(3, mapStartY + 14, "1-5  Use selected inventory item", textColor);
        renderer.drawString(rightColumn, mapStartY + 14, "B  Logic Bug: fast, weaker attacks", textColor);
        renderer.drawString(3, mapStartY + 15, "N/P  Next / previous inventory page", textColor);
        renderer.drawString(rightColumn, mapStartY + 15, "V  Trojan Virus: slower, stronger", textColor);
        renderer.drawString(3, mapStartY + 16, "Five items per page; no item limit", textColor);
        renderer.drawString(rightColumn, mapStartY + 16, "O  System Overlord: final boss", textColor);
        renderer.drawString(3, mapStartY + 17, "I/Q  Close inventory without using", textColor);
        renderer.drawString(rightColumn, mapStartY + 17, "< / >  Stairs: press E to travel", textColor);
        renderer.drawString(3, mapStartY + 18, "Menus pause monster turns", detailColor);
        renderer.drawString(rightColumn, mapStartY + 18, "Defeat boss to access mainframe", detailColor);
        renderer.drawString(2, mapStartY + 19, "============================ H / Q TO CLOSE =============================", titleColor);
    }

    private void drawHUD(int mapStartY) {
        int lX = 60;

        renderer.drawString(lX, mapStartY,      "--- LEGEND ---", "\033[38;5;255m");
        renderer.drawString(lX, mapStartY + 1,  "@ Player", "\033[38;5;51m");
        renderer.drawString(lX, mapStartY + 2,  "M Mainframe", "\033[38;5;51m");
        renderer.drawString(lX, mapStartY + 3,  "F Firewall Patch", "\033[38;5;51m");
        renderer.drawString(lX, mapStartY + 4,  "< Up Stairs", "\033[38;5;255m");
        renderer.drawString(lX, mapStartY + 5,  "> Down Stairs", "\033[38;5;255m");
        renderer.drawString(lX, mapStartY + 6,  "$ Data (Score)", "\033[38;5;226m");
        renderer.drawString(lX, mapStartY + 7,  "H Health Patch", "\033[38;5;46m");
        renderer.drawString(lX, mapStartY + 8,  "? Unknown Tile", "\033[38;5;207m");
        renderer.drawString(lX, mapStartY + 9,  "O System Overlord", "\033[38;5;201m");
        renderer.drawString(lX, mapStartY + 10, "T Firewall Trap", "\033[38;5;196m");
        renderer.drawString(lX, mapStartY + 11, "B Logic Bug", "\033[38;5;196m");
        renderer.drawString(lX, mapStartY + 12, "  (Fast, Weak)", "\033[38;5;245m");
        renderer.drawString(lX, mapStartY + 13, "V Trojan Virus", "\033[38;5;196m");
        renderer.drawString(lX, mapStartY + 14, "  (Slow, Strong)", "\033[38;5;245m");
        renderer.drawString(lX, mapStartY + 15, "Press H for Help", "\033[38;5;245m");

        int invY = mapStartY + 16;
        renderer.drawString(lX, invY++, "--- INVENTORY ---", "\033[38;5;255m");
        List<Item> inv = hero.getInventory();

        if (inv.isEmpty()) {
            renderer.drawString(lX, invY, "(Empty)", "\033[38;5;245m");
        } else {
            int maxDisplayed = Math.min(inv.size(), 3);
            if (inv.size() > 3) maxDisplayed = 2;

            for (int i = 0; i < maxDisplayed; i++) {
                Item it = inv.get(i);
                renderer.drawString(lX, invY++, "- " + it.getName() + " (V:" + it.getValue() + ")", "\033[38;5;226m");
            }

            if (inv.size() > 3) {
                renderer.drawString(lX, invY, "...+" + (inv.size() - 2) + " more (Press I)", "\033[38;5;245m");
            }
        }

        int hudStartY = mapStartY + 20;

        String patchStatus = hero.isFirewallPatchActive() ? "ACTIVE" : "NONE";
        String stats = String.format(" HP: %d/%d | SECTOR: %d | SCORE: %d | PATCH: %s ",
            hero.getHp(), hero.getMaxHp(), (currentFloorIndex + 1), hero.getScore(), patchStatus);

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
        command = Character.toLowerCase(command);

        if (isHelpMenuOpen) {
            if (command == 'h' || command == 'q') isHelpMenuOpen = false;
            return;
        }

        if (isVictorious) {
            if (command == 'r') initializeNewRun();
            if (command == 'l') handleLoad();
            if (command == 'q') isRunning = false;
            return;
        }

        if (hero.getHp() <= 0) {
            if (command == 'h') isHelpMenuOpen = true;
            if (command == 'r') initializeNewRun();
            if (command == 'l') handleLoad();
            if (command == 'q') isRunning = false;
            return;
        }

        if (isInventoryMenuOpen) {
            processInventoryInput(Character.toLowerCase(command));
            return;
        }

        boolean actionTaken = false;
        switch (Character.toLowerCase(command)) {
            case 'w' -> actionTaken = hero.move(0, -1, getCurrentMap(), eventManager);
            case 's' -> actionTaken = hero.move(0, 1, getCurrentMap(), eventManager);
            case 'a' -> actionTaken = hero.move(-1, 0, getCurrentMap(), eventManager);
            case 'd' -> actionTaken = hero.move(1, 0, getCurrentMap(), eventManager);
            case 'e' -> attemptInteraction();
            case '1' -> { hero.sortInventory("name"); eventManager.publishEvent("Sorted inventory by Name.", EventManager.EventType.SYSTEM); }
            case '2' -> { hero.sortInventory("value"); eventManager.publishEvent("Sorted inventory by Value.", EventManager.EventType.SYSTEM); }
            case 'i' -> { isInventoryMenuOpen = true; inventoryPage = 0; }
            case 'h' -> isHelpMenuOpen = true;
            case 'k' -> handleSave();
            case 'l' -> handleLoad();
            case 'f' -> {
                flashingEnabled = !flashingEnabled;
                eventManager.publishEvent("Jumpscare flashes " + (flashingEnabled ? "ENABLED" : "DISABLED") + ".", EventManager.EventType.SYSTEM);
            }
            case 'r' -> initializeNewRun();
            case 'q' -> isRunning = false;
        }

        if (actionTaken) {
            processMonsterTurns();
        }
    }

    private void handleSave() {
        if (FileManager.saveGame(saveFilePath, hero, floors, currentFloorIndex)) {
            eventManager.publishEvent("Game state saved successfully.", EventManager.EventType.SYSTEM);
        } else {
            eventManager.publishEvent("ERROR: Failed to save game state.", EventManager.EventType.DAMAGE);
        }
    }

    private void handleLoad() {
        FileManager.SaveState state = FileManager.loadGame(saveFilePath);
        if (state != null) {
            this.hero = state.player;
            this.floors = state.floors;
            this.currentFloorIndex = state.currentFloorIndex;

            // Clean load state constraints
            this.isInventoryMenuOpen = false;
            this.isHelpMenuOpen = false;
            this.isVictorious = false;
            this.deathMessagePublished = false;
            this.jumpscareTriggered = false;
            this.inventoryPage = 0;
            this.eventManager.clear();

            getCurrentMap().calculateFOV(hero.getX(), hero.getY(), 6);
            if (hero.getHp() > 0) resumeMusicAfterDeath();
            eventManager.publishEvent("Game loaded successfully.", EventManager.EventType.SYSTEM);
        } else {
            eventManager.publishEvent("ERROR: No valid save found or data corrupted.", EventManager.EventType.DAMAGE);
        }
    }

    private void processInventoryInput(char command) {
        if (command == 'i' || command == 'q') {
            isInventoryMenuOpen = false;
            return;
        }

        List<Item> inv = hero.getInventory();
        int maxPages = Math.max(1, (int)Math.ceil(inv.size() / 5.0));

        if (command == 'n') {
            inventoryPage = (inventoryPage + 1) % maxPages;
        } else if (command == 'p') {
            inventoryPage = (inventoryPage - 1 + maxPages) % maxPages;
        } else if (command >= '1' && command <= '5') {
            int slot = command - '1';
            int idx = inventoryPage * 5 + slot;
            if (idx < inv.size()) {
                useItem(idx);
            }
        }
    }

    private void useItem(int index) {
        Item item = hero.getInventory().get(index);
        String name = item.getName();

        if (name.equals("Health Syringe")) {
            if (hero.getHp() < hero.getMaxHp()) {
                hero.heal(5);
                eventManager.publishEvent("Used Health Syringe: Restored 5 HP.", EventManager.EventType.LOOT);
                hero.removeItem(item);
            } else {
                eventManager.publishEvent("HP is full. Syringe not used.", EventManager.EventType.SYSTEM);
            }
        } else if (name.equals("Firewall Patch")) {
            if (!hero.isFirewallPatchActive()) {
                hero.setFirewallPatchActive(true);
                eventManager.publishEvent("Firewall Patch applied. Next trap nullified.", EventManager.EventType.SYSTEM);
                hero.removeItem(item);
            } else {
                eventManager.publishEvent("Firewall Patch is already active.", EventManager.EventType.SYSTEM);
            }
        } else if (name.equals("Corrupted Cache")) {
            if (Math.random() < 0.5) {
                hero.addScore(50);
                eventManager.publishEvent("Cache Decrypted! +50 Score.", EventManager.EventType.LOOT);
            } else {
                hero.takeDamage(10);
                eventManager.publishEvent("Cache contained Malware! -10 HP.", EventManager.EventType.DAMAGE);
                if (hero.getHp() <= 0) isInventoryMenuOpen = false;
            }
            hero.removeItem(item);
        } else if (name.equals("Data Fragment")) {
            eventManager.publishEvent("Data Fragment has no use action (Score already added).", EventManager.EventType.SYSTEM);
        }

        int currentMaxPages = Math.max(1, (int)Math.ceil(hero.getInventory().size() / 5.0));
        if (inventoryPage >= currentMaxPages) {
            inventoryPage = Math.max(0, currentMaxPages - 1);
        }
    }

    private void resumeMusicAfterDeath() {
        if (musicPausedForDeath) {
            if (!testMode) musicPlayer.resume();
            musicPausedForDeath = false;
        }
    }

    // Package-private so combat transitions can be verified headlessly with seeded maps.
    void processMonsterTurns() {
        DungeonMap map = getCurrentMap();
        for (Monster m : map.getMonsters()) {
            if (hero.getHp() <= 0) break;
            if (Math.random() > m.getSpeed()) continue;

            int distToHero = Math.abs(m.getX() - hero.getX()) + Math.abs(m.getY() - hero.getY());
            if (distToHero <= 8) {
                if (distToHero == 1) {
                    int dmg = m.getMinDmg() + (int)(Math.random() * (m.getMaxDmg() - m.getMinDmg() + 1));
                    hero.takeDamage(dmg);
                    eventManager.publishEvent(m.getName() + " corrupted you for " + dmg + " dmg!", EventManager.EventType.DAMAGE);
                    if (hero.getHp() <= 0) {
                        if (!jumpscareTriggered) {
                            musicPlayer.stop();
                            musicPausedForDeath = true;
                            if (!testMode) {
                                int columns = activeTerminal != null ? activeTerminal.getWidth() : 100;
                                int rows = activeTerminal != null ? activeTerminal.getHeight() : 55;
                                JumpscareManager.playJumpscare(flashingEnabled, columns, rows);
                                // JumpscarePreview restores the cursor for standalone use;
                                // hide it again when returning to the running game.
                                System.out.print("\033[?25l");
                            }
                            jumpscareTriggered = true;
                        }
                        break;
                    }
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
                hero.addScore(5000);
                eventManager.publishEvent("MAINFRAME HACKED. MISSION ACCOMPLISHED!", EventManager.EventType.SYSTEM);
                isVictorious = true;
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

    // --- Package-private accessors for JUnit headless verification ---
    // Tests may install a real generated map without changing the production game flow.
    void setCurrentMapForTest(DungeonMap map) {
        if (!testMode) throw new IllegalStateException("Only available in headless tests");
        this.floors = new ArrayList<>();
        this.floors.add(java.util.Objects.requireNonNull(map));
        this.currentFloorIndex = 0;
        this.hero.setPosition(map.getStartX(), map.getStartY());
    }

    DungeonMap getCurrentMapForTest() {
        if (!testMode) throw new IllegalStateException("Only available in headless tests");
        return getCurrentMap();
    }

    boolean wasJumpscareTriggered() { return jumpscareTriggered; }
    boolean isDeathMessagePublished() { return deathMessagePublished; }
    boolean isVictorious() { return isVictorious; }
    boolean isInventoryMenuOpen() { return isInventoryMenuOpen; }
    boolean isRunning() { return isRunning; }
    Player getHero() { return hero; }
    EventManager getEventManager() { return eventManager; }

    void processTestInput(char command) {
        processInput(command);
        updateLogic();
    }
}
