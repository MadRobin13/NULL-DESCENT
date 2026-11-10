package com.dungeoncrawler.engine;

import java.io.PrintWriter;

public class TerminalRenderer {
    public static final char WALL_SOLID = '\u2588';
    public static final char FOG_HEAVY  = '\u2593';
    public static final char SHADOW     = '\u2592';
    public static final char FLOOR_LIT  = '\u2591';

    public static final String RESET = "\033[0m";
    private static final String COLOR_WALL = "\033[38;5;240m";
    private static final String COLOR_HERO = "\033[38;5;214m";

    private final int width;
    private final int height;
    private final char[][] screenBuffer;
    private final String[][] colorBuffer;

    public TerminalRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        this.screenBuffer = new char[height][width];
        this.colorBuffer = new String[height][width];
        clearBuffer();
    }

    public void clearBuffer() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                screenBuffer[y][x] = ' ';
                colorBuffer[y][x] = RESET;
            }
        }
    }

    public void drawChar(int x, int y, char c, String colorCode) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            screenBuffer[y][x] = c;
            colorBuffer[y][x] = colorCode == null ? RESET : colorCode;
        }
    }

    // Overload for default colors
    public void drawChar(int x, int y, char c) {
        if (c == WALL_SOLID) drawChar(x, y, c, COLOR_WALL);
        else if (c == '@') drawChar(x, y, c, COLOR_HERO);
        else drawChar(x, y, c, RESET);
    }

    public void drawString(int x, int y, String text, String colorCode) {
        for (int i = 0; i < text.length(); i++) {
            drawChar(x + i, y, text.charAt(i), colorCode);
        }
    }

    /** Render through the active JLine terminal, preserving its output encoding. */
    public void render(PrintWriter out, int columns, int rows) {
        out.print(buildFrame(columns, rows));
        out.flush();
    }

    /** Keep the headless render entry points used by the existing tests. */
    public void render() { System.out.print(buildFrame()); }
    public void render(int columns, int rows) { System.out.print(buildFrame(columns, rows)); }

    // Package-private for headless rendering tests.
    String buildFrame() { return buildFrame(width, height); }

    /**
     * Each row uses absolute positioning. Never write the terminal's rightmost
     * cell: some Windows console hosts wrap when it is filled, even without a
     * newline. This also avoids changing the user's autowrap setting at all.
     */
    String buildFrame(int columns, int rows) {
        if (columns < 1 || rows < 1 || columns > width || rows > height) {
            throw new IllegalArgumentException("Invalid render dimensions");
        }
        StringBuilder frame = new StringBuilder(columns * rows + 512);
        String currentColor = RESET;
        for (int y = 0; y < rows; y++) {
            frame.append("\033[").append(y + 1).append(";1H");
            for (int x = 0; x < columns - 1; x++) {
                String targetColor = colorBuffer[y][x];
                if (!targetColor.equals(currentColor)) {
                    frame.append(targetColor);
                    currentColor = targetColor;
                }
                frame.append(screenBuffer[y][x]);
            }
        }
        return frame.append(RESET).toString();
    }

    // Package-private to allow tests to inspect the buffer without a terminal.
    char getCharAt(int x, int y) { return screenBuffer[y][x]; }
    String getColorAt(int x, int y) { return colorBuffer[y][x]; }
}
