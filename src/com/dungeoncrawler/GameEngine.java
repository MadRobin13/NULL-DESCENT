package com.dungeoncrawler;

import com.dungeoncrawler.engine.FileManager;
import com.dungeoncrawler.engine.TerminalRenderer;
import com.dungeoncrawler.engine.TerminalSession;
import com.dungeoncrawler.engine.TerminalInputParser;
import com.dungeoncrawler.engine.EventManager;
import com.dungeoncrawler.engine.MusicPlayer;
import com.dungeoncrawler.engine.JumpscareManager;
import org.jline.terminal.Terminal;
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
    private int lastLayout = -1; // 0 = normal, 1 = compact, 2 = resize warning
    private boolean isStoryOpen = false;
    private int storyPage = 0;

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
        " Scanning the local directory, you find logs detailing the network's corruption. The system has",
        " been completely overrun by malware, and a rogue entity known as the System Overlord has seized",
        " control of the mainframe at the bottom of the subnet. You remember from your cybersecurity class",
        " that the only way to take the network back is to fight through its defences, defeat the",
        " System Overlord, and access the mainframe directly. Sounds simple enough!",
        "",
        " Setting off into the network, you see a terminal port leading to the first of ten increasingly",
        " dangerous sectors. You decide to descend through the network, sector by sector, and see if",
        " you can reach the mainframe. Time to reboot this system.",
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
        this.isStoryOpen = false;
        this.storyPage = 0;

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
        // JLine chooses the native Windows or Unix terminal provider. Both the
        // renderer and the jumpscare must write through terminal.writer().
        try (TerminalSession session = TerminalSession.open()) {
            Terminal terminal = session.terminal();
            this.activeTerminal = terminal;
            NonBlockingReader reader = terminal.reader();
            TerminalInputParser inputParser = new TerminalInputParser();
            boolean firstPlayableFrame = true;
            terminal.handle(Terminal.Signal.INT, signal -> isRunning = false);

            // Redraw on input or resize, not continuously during idle time.
            int previousWidth = -1;
            int previousHeight = -1;
            boolean redraw = true;
            while (isRunning) {
                int currentWidth = terminal.getWidth();
                int currentHeight = terminal.getHeight();
                if (redraw || currentWidth != previousWidth || currentHeight != previousHeight) {
                    if (firstPlayableFrame && !terminalTooSmall(currentWidth, currentHeight)) {
                        firstPlayableFrame = false;
                        // The full layout already shows the whole introduction above the map.
                        // Compact terminals show it as a readable two-page opening screen.
                        if (currentWidth < 100 || currentHeight < 55) openStory();
                    }
                    updateLogic();
                    renderFrame();
                    previousWidth = currentWidth;
                    previousHeight = currentHeight;
                    redraw = false;
                }

                int readChar = reader.read(250);
                if (readChar == NonBlockingReader.READ_EXPIRED) {
                    inputParser.resetIncompleteSequence();
                    continue;
                }
                if (readChar == NonBlockingReader.EOF) break;

                Character parsed = inputParser.accept(readChar);
                if (parsed == null) continue;
                char command = parsed;
                // In raw mode Ctrl+C is a character, not a terminal shutdown.
                if (command == 3 || command == 4) {
                    isRunning = false;
                    break;
                }
                if (terminalTooSmall(currentWidth, currentHeight)) {
                    // Keep the resize warning visible; Q always exits even if a menu was open.
                    if (Character.toLowerCase(command) == 'q') isRunning = false;
                    continue;
                }
                processInput(command);
                redraw = true;
            }
        } catch (IOException | IllegalStateException e) {
            System.err.println("Terminal init failed: " + e.getMessage());
        } finally {
            // TerminalSession has already restored the user's original screen,
            // cursor and raw-mode attributes before this is printed.
            activeTerminal = null;
            if (!testMode) musicPlayer.close();
            System.out.println("Connection terminated. Final Score: " + hero.getScore());
        }
    }

    static boolean terminalTooSmall(int width, int height) {
        return width < 80 || height < 24;
    }

    private void renderResizeWarning(int width, int height) {
        // Respect even very narrow terminal widths; don't wrap the warning itself.
        int usableWidth = Math.max(1, width - 1);
        int availableRows = Math.max(1, height);
        String[] lines = {
            "NULL//DESCENT - TERMINAL TOO SMALL",
            "Requires at least 80 columns x 24 rows.",
            "Current size: " + width + " x " + height,
            "Resize the terminal to continue. Q to quit."
        };
        StringBuilder message = new StringBuilder("\033[0m\033[2J\033[H");
        for (int i = 0; i < Math.min(lines.length, availableRows); i++) {
            message.append("\033[").append(i + 1).append(";1H");
            message.append(lines[i], 0, Math.min(usableWidth, lines[i].length()));
        }
        writeTerminal(message.toString());
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
        int width = activeTerminal != null && !testMode ? activeTerminal.getWidth() : 100;
        int height = activeTerminal != null && !testMode ? activeTerminal.getHeight() : 55;
        renderFrameAtSize(width, height);
    }

    // Keep the original full layout on large displays, but allow 80x24 Windows consoles.
    void renderFrameAtSize(int width, int height) {
        int layout = terminalTooSmall(width, height) ? 2 : (width < 100 || height < 55 ? 1 : 0);
        if (layout != lastLayout) {
            writeTerminal("\033[2J"); // Clear any remnants when switching layouts.
            lastLayout = layout;
        }
        if (layout == 2) {
            renderResizeWarning(width, height);
            return;
        }
        renderer.clearBuffer();
        if (isStoryOpen) {
            drawStoryScreen();
            renderBuffer(layout == 1 ? 80 : 100, layout == 1 ? 24 : 55);
        } else if (layout == 1) {
            drawCompactFrame();
            renderBuffer(80, 24);
        } else {
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
            if (isHelpMenuOpen) drawHelpMenu(mapStartY);
            renderBuffer(100, 55);
        }
        if (activeTerminal != null && !testMode) activeTerminal.flush();
        else System.out.flush();
    }

    private void renderBuffer(int columns, int rows) {
        if (activeTerminal != null && !testMode) {
            renderer.render(activeTerminal.writer(), columns, rows);
        } else {
            renderer.render(columns, rows);
        }
    }

    private void writeTerminal(String output) {
        if (activeTerminal != null && !testMode) {
            activeTerminal.writer().print(output);
            activeTerminal.flush();
        } else {
            System.out.print(output);
        }
    }

    private void openStory() {
        isStoryOpen = true;
        storyPage = 0;
    }

    /**
     * The original introduction doesn't fit above a 19-row map in 24 rows.
     * Present it separately on compact displays instead of silently hiding it.
     * A full-size display still retains its original permanent introduction.
     */
    private void drawStoryScreen() {
        final String purple = "\033[38;5;129m";
        final String cyan = "\033[38;5;51m";
        final String gray = "\033[38;5;245m";
        final String yellow = "\033[38;5;226m";

        if (storyPage == 0) {
            // Original ASCII banner, trimming only whitespace that exceeds 79 columns.
            for (int row = 0; row < 6; row++) {
                drawCompactText(0, row + 1, headerText[row].stripTrailing(), purple);
            }
            drawCompactText(2, 9, "System Breach Detected...", cyan);
            drawStoryParagraph(9, 13, 11, gray);
            drawCompactText(2, 20, "MISSION: Descend ten sectors and reclaim the mainframe.", yellow);
        } else {
            drawCompactText(2, 2, "NULL//DESCENT  //  INCOMING TRANSMISSION", purple);
            drawCompactText(2, 4, "RECOVERED SYSTEM LOGS", cyan);
            int y = drawStoryParagraph(15, 19, 6, gray);
            drawCompactText(2, y + 1, "YOUR OBJECTIVE", cyan);
            drawStoryParagraph(21, 23, y + 3, gray);
        }
        drawCompactText(1, 22, storyPage == 0
            ? "[ENTER / SPACE / N] Continue   [T / Q] Skip to game"
            : "[ENTER / SPACE / N] Start game   [P] Previous   [T / Q] Close", yellow);
        drawCompactText(1, 23, "Arrow keys: movement   |   Mouse scrolling: ignored", gray);
    }

    /** Rewrap the existing intro; maintain its wording rather than duplicating it. */
    private int drawStoryParagraph(int first, int last, int y, String color) {
        StringBuilder paragraph = new StringBuilder();
        for (int i = first; i <= last; i++) {
            if (!paragraph.isEmpty()) paragraph.append(' ');
            paragraph.append(headerText[i].trim());
        }
        StringBuilder row = new StringBuilder();
        for (String word : paragraph.toString().split(" +")) {
            if (row.length() > 0 && row.length() + word.length() + 1 > 75) {
                drawCompactText(2, y++, row.toString(), color);
                row.setLength(0);
            }
            if (!row.isEmpty()) row.append(' ');
            row.append(word);
        }
        if (!row.isEmpty()) drawCompactText(2, y++, row.toString(), color);
        return y;
    }

    /** A compact version of the same game, not a different game mode. */
    private void drawCompactFrame() {
        final String purple = "\033[38;5;129m";
        final String cyan = "\033[38;5;51m";
        final String yellow = "\033[38;5;226m";
        final String gray = "\033[38;5;245m";
        final String red = "\033[38;5;196m";
        drawCompactText(0, 0,
            " NULL//DESCENT  |  CORE NETWORK  |  SECTOR " + (currentFloorIndex + 1)
                + "/10  |  H: Help  T: Story", purple);

        // The real 58x19 map fits entirely into 80 columns without a scrolling camera.
        int mapY = 1;
        getCurrentMap().drawToBuffer(renderer, 0, mapY);
        renderer.drawChar(hero.getX(), hero.getY() + mapY,
            hero.getHp() <= 0 ? 'X' : '@', hero.getHp() <= 0 ? red : cyan);

        int x = 60;
        drawCompactText(x, 1, "-- SYSTEM STATUS --", yellow);
        drawCompactText(x, 2, "HP: " + hero.getHp() + "/" + hero.getMaxHp(),
            hero.getHp() <= 25 ? red : cyan);
        drawCompactText(x, 3, "Score: " + hero.getScore(), yellow);
        drawCompactText(x, 4, "Patch: " + (hero.isFirewallPatchActive() ? "ACTIVE" : "NONE"), cyan);
        drawCompactText(x, 6, "-- MAP LEGEND --", yellow);
        drawCompactText(x, 7, "@ Player", cyan);
        drawCompactText(x, 8, "< > Stairs", gray);
        drawCompactText(x, 9, "M Mainframe", cyan);
        drawCompactText(x, 10, "B/V Virus enemies", red);
        drawCompactText(x, 11, "O Final boss", red);
        drawCompactText(x, 12, "T Trap / F Patch", yellow);
        drawCompactText(x, 13, "H Heal / ? Unknown", cyan);
        drawCompactText(x, 14, "$ Score fragment", yellow);
        drawCompactText(x, 16, "-- INVENTORY --", yellow);
        List<Item> inv = hero.getInventory();
        drawCompactText(x, 17, inv.size() + " item(s)", gray);
        drawCompactText(x, 18, "I: View / Use", cyan);
        drawCompactText(x, 19, "1/2: Sort items", gray);

        drawCompactText(0, 20, "=".repeat(79), gray);
        List<EventManager.EventLog> logs = eventManager.getRecentLogs();
        int from = Math.max(0, logs.size() - 2);
        for (int i = from; i < logs.size(); i++) {
            EventManager.EventLog log = logs.get(i);
            String color = switch (log.type) {
                case DAMAGE -> red;
                case LOOT -> yellow;
                case COMBAT -> "\033[38;5;208m";
                case SYSTEM -> gray;
            };
            drawCompactText(1, 21 + i - from, log.message, color);
        }
        drawCompactText(0, 23, " WASD Move / Arrows | E Use | I Bag | H Help | T Story | K/L Save/Load | Q Quit", cyan);

        if (isVictorious) drawCompactEndScreen(true);
        else if (hero.getHp() <= 0) drawCompactEndScreen(false);
        else if (isInventoryMenuOpen) drawCompactInventory();
        if (isHelpMenuOpen) drawCompactHelp();
    }

    private void drawCompactText(int x, int y, String text, String color) {
        int available = 80 - x;
        if (available > 0 && y >= 0 && y < 24) {
            renderer.drawString(x, y,
                text.length() <= available ? text : text.substring(0, available), color);
        }
    }

    private void clearCompactOverlay(int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            drawCompactText(x, y + row, " ".repeat(columns), TerminalRenderer.RESET);
        }
    }

    private void drawCompactEndScreen(boolean won) {
        int x = 8, y = 6;
        String color = won ? "\033[38;5;46m" : "\033[38;5;196m";
        clearCompactOverlay(x, y, 63, 9);
        drawCompactText(x + 1, y + 1, "==========================================================", color);
        drawCompactText(x + 2, y + 2, won ? "MAINFRAME HACKED. MISSION ACCOMPLISHED!"
            : "CRITICAL FAILURE. CONNECTION LOST.", color);
        drawCompactText(x + 2, y + 4, "Final Score: " + hero.getScore(), "\033[38;5;255m");
        drawCompactText(x + 2, y + 6, "[R] Restart  |  [L] Load Run  |  [Q] Quit", "\033[38;5;51m");
        drawCompactText(x + 1, y + 7, "==========================================================", color);
    }

    private void drawCompactInventory() {
        int x = 3, y = 2;
        clearCompactOverlay(x, y, 74, 18);
        String yellow = "\033[38;5;226m";
        String cyan = "\033[38;5;51m";
        String gray = "\033[38;5;245m";
        List<Item> inv = hero.getInventory();
        int maxPages = Math.max(1, (inv.size() + 4) / 5);
        drawCompactText(x, y, "========================== INVENTORY ===========================", yellow);
        drawCompactText(x + 2, y + 1, "Page " + (inventoryPage + 1) + "/" + maxPages
            + "  |  1-5 Use  |  N/P Pages  |  I/Q Close", cyan);
        if (inv.isEmpty()) drawCompactText(x + 3, y + 4, "(Inventory is empty)", gray);
        for (int i = 0; i < 5; i++) {
            int index = inventoryPage * 5 + i;
            if (index >= inv.size()) break;
            Item item = inv.get(index);
            drawCompactText(x + 3, y + 3 + i * 2,
                "[" + (i + 1) + "] " + item.getName() + " (Value: " + item.getValue() + ")", cyan);
            String action = switch (item.getName()) {
                case "Health Syringe" -> "Restore 5 HP";
                case "Firewall Patch" -> "Block next trap";
                case "Corrupted Cache" -> "Random reward or damage";
                case "Data Fragment" -> "Score already collected";
                default -> "No action";
            };
            drawCompactText(x + 7, y + 4 + i * 2, action, gray);
        }
        drawCompactText(x, y + 17, "===============================================================", yellow);
    }

    private void drawCompactHelp() {
        clearCompactOverlay(0, 1, 80, 23);
        String cyan = "\033[38;5;51m";
        String yellow = "\033[38;5;226m";
        String gray = "\033[38;5;245m";
        drawCompactText(1, 1, "========================= HELP / CONTROLS =========================", yellow);
        drawCompactText(2, 2, "System Overlord controls the corrupted network. Descend 10 sectors, defeat", gray);
        drawCompactText(2, 3, "the boss, and hack the mainframe (M) to reclaim the system.", gray);
        drawCompactText(2, 5, "CONTROLS", cyan);
        drawCompactText(2, 6, "WASD/Arrows Move / bump enemies to attack   E Interact on stairs/mainframe", gray);
        drawCompactText(2, 7, "I Inventory (1-5 use, N/P pages, I/Q close)", gray);
        drawCompactText(2, 8, "1/2 Sort items by name/value           H/Q Close this help screen", gray);
        drawCompactText(2, 9, "K Save   L Load   R Restart   Q Quit   F Toggle jumpscare flashes", gray);
        drawCompactText(2, 11, "MAP / ITEMS", cyan);
        drawCompactText(2, 12, "@ You      < / > Stairs      M Mainframe      B/V Viruses      O Boss", gray);
        drawCompactText(2, 13, "F Firewall Patch (activate from inventory; blocks next T trap)", gray);
        drawCompactText(2, 14, "T Trap (-35 HP)    H Health (+25 HP)    $ Data (+100 score)", gray);
        drawCompactText(2, 15, "? Unknown: item cache, health syringe, or -5 HP trap", gray);
        drawCompactText(2, 16, "Syringe: +5 HP when used   Corrupted Cache: +50 score or -10 HP", gray);
        drawCompactText(2, 18, "Enemies take turns when you move; menus pause combat.", cyan);
        drawCompactText(2, 20, "Press T to read the complete title and story on any terminal.", gray);
        drawCompactText(1, 22, "=========================== H / Q CLOSE ==========================", yellow);
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
        renderer.drawString(rightColumn, mapStartY + 3, "F tile: Firewall Patch; activate in inventory", textColor);
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
        renderer.drawString(3, mapStartY + 11, "H  Help | T  Story | F  Toggle jumpscare flashes", textColor);
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

        if (isStoryOpen) {
            if (command == 't' || command == 'q' || command == 27) isStoryOpen = false;
            else if (command == 'p') storyPage = Math.max(0, storyPage - 1);
            else if (command == 'n' || command == ' ' || command == '\n') {
                if (storyPage == 0) storyPage = 1;
                else isStoryOpen = false;
            }
            return;
        }

        if (isHelpMenuOpen) {
            if (command == 't') { isHelpMenuOpen = false; openStory(); }
            else if (command == 'h' || command == 'q') isHelpMenuOpen = false;
            return;
        }

        if (isVictorious) {
            if (command == 'r') initializeNewRun();
            if (command == 'l') handleLoad();
            if (command == 't') openStory();
            if (command == 'q') isRunning = false;
            return;
        }

        if (hero.getHp() <= 0) {
            if (command == 'h') isHelpMenuOpen = true;
            if (command == 'r') initializeNewRun();
            if (command == 'l') handleLoad();
            if (command == 't') openStory();
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
            case 't' -> openStory();
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
                                if (activeTerminal != null) {
                                    JumpscareManager.playJumpscare(flashingEnabled, columns, rows,
                                        activeTerminal.writer(), false);
                                }
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

    boolean isStoryOpenForTest() { return isStoryOpen; }
    int storyPageForTest() { return storyPage; }

    void processTestInput(char command) {
        processInput(command);
        updateLogic();
    }
}
