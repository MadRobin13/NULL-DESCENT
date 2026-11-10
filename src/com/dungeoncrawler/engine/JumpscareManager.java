package com.dungeoncrawler.engine;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Renders the game's original cyberpunk pixel-art jumpscare inside the terminal. */
public final class JumpscareManager {
    static {
        // ImageIO and BufferedImage work without an X11/macOS GUI display.
        System.setProperty("java.awt.headless", "true");
    }

    private static final int MAX_COLUMNS = 100;
    private static final int MAX_ROWS = 55;
    private static final String ESC = "\u001b[";
    private static BufferedImage sourceImage1;
    private static BufferedImage sourceImage2;
    private static int cachedColumns = -1;
    private static int cachedRows = -1;
    private static String normalFrame;
    private static String zoomedFrame;
    private static String whiteFrame;

    private JumpscareManager() { }

    /** Load or generate the two sprites and prepare the default frames before gameplay. */
    public static synchronized void initialize() {
        ensureFrames(MAX_COLUMNS, MAX_ROWS);
    }

    /** Useful for testing without a running GameEngine. */
    public static void playJumpscare(boolean flashingEnabled) {
        playJumpscare(flashingEnabled, MAX_COLUMNS, MAX_ROWS);
    }

    /**
     * Play a roughly two-second blocking animation. No game turns occur while it runs.
     * Actual terminal dimensions are passed by GameEngine; no JLine coupling is required.
     */
    public static void playJumpscare(boolean flashingEnabled, int terminalColumns, int terminalRows) {
        // Standalone fallback. GameEngine/JumpscarePreview use the JLine writer below.
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        playJumpscare(flashingEnabled, terminalColumns, terminalRows, out, true);
    }

    /**
     * Render via the same Unicode-capable JLine writer used by the game.
     * The running game owns an alternate screen already; only standalone
     * previews should create and release their own alternate screen.
     */
    public static synchronized void playJumpscare(boolean flashingEnabled, int terminalColumns,
                                                   int terminalRows, PrintWriter out,
                                                   boolean standalonePreview) {
        int columns = terminalColumns > 0 ? Math.min(MAX_COLUMNS, terminalColumns) : MAX_COLUMNS;
        int rows = terminalRows > 0 ? Math.min(MAX_ROWS, terminalRows) : MAX_ROWS;
        if (columns < 8 || rows < 5) return; // Terminal too small to show anything useful.
        ensureFrames(columns, rows);

        Clip sound = null;
        String soundError = null;
        try {
            File audio = new File("jumpscare.wav");
            if (!audio.isFile()) audio = new File("jumpscare.WAV");
            if (audio.isFile()) {
                try (AudioInputStream stream = AudioSystem.getAudioInputStream(audio)) {
                    sound = AudioSystem.getClip();
                    sound.open(stream);
                } catch (Exception ex) {
                    soundError = ex.getMessage();
                    if (sound != null) { sound.close(); sound = null; }
                }
            } else {
                soundError = "jumpscare.wav not found (visual animation will still play)";
            }

            // GameEngine is already in its own alternate screen. Avoid nesting
            // alternate buffers, which can restore the wrong Windows screen.
            if (standalonePreview) out.print(ESC + "?1049h");
            out.print(ESC + "?25l" + ESC + "0m" + ESC + "2J" + ESC + "H");
            out.flush();

            long beginning = System.nanoTime();
            if (sound != null) sound.start();

            show(out, normalFrame);
            sleepUntil(beginning, 320);
            if (flashingEnabled) show(out, whiteFrame);
            sleepUntil(beginning, 390);
            show(out, zoomedFrame);
            sleepUntil(beginning, 850);
            if (flashingEnabled) show(out, whiteFrame);
            sleepUntil(beginning, 920);
            show(out, zoomedFrame);
            sleepUntil(beginning, 1310);
            if (flashingEnabled) show(out, whiteFrame);
            sleepUntil(beginning, 1380);
            show(out, normalFrame);
            sleepUntil(beginning, 2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (sound != null) {
                sound.stop();
                sound.close();
            }
            // Never alter autowrap, terminal font, codepage or console size.
            out.print(ESC + "0m");
            if (standalonePreview) {
                out.print(ESC + "?25h" + ESC + "?1049l");
            } else {
                // The game keeps the cursor hidden and redraws the next frame.
                out.print(ESC + "2J" + ESC + "H" + ESC + "?25l");
            }
            out.flush();
            if (soundError != null && Boolean.getBoolean("null.descent.debug")) {
                System.err.println("Jumpscare audio: " + soundError);
            }
        }
    }

    private static void show(PrintWriter out, String frame) {
        // A JLine PrintWriter handles Windows console Unicode and colour modes.
        out.print(frame);
        out.flush();
    }

    private static void sleepUntil(long startTime, long milliseconds) throws InterruptedException {
        long remaining = startTime + milliseconds * 1_000_000L - System.nanoTime();
        if (remaining > 0) Thread.sleep(remaining / 1_000_000L, (int)(remaining % 1_000_000L));
    }

    private static void ensureFrames(int columns, int rows) {
        if (columns == cachedColumns && rows == cachedRows && normalFrame != null) return;
        if (sourceImage1 == null) sourceImage1 = loadImageOrFallback("jumpscare1.png", false);
        if (sourceImage2 == null) sourceImage2 = loadImageOrFallback("jumpscare2.png", true);
        int imageColumns = Math.max(2, columns - 2);
        int imageRows = Math.max(2, rows - 4);
        // Image subpixels are half as tall as a character: roughly square on common fonts.
        int squareEdge = Math.min(imageColumns, imageRows * 2);
        int pixelWidth = squareEdge;
        int pixelHeight = squareEdge % 2 == 0 ? squareEdge : squareEdge - 1;
        int left = Math.max(1, (columns - pixelWidth) / 2 + 1);
        int top = Math.max(1, (rows - pixelHeight / 2) / 2 + 1);

        normalFrame = frameFor(sourceImage1, columns, rows, pixelWidth, pixelHeight, left, top, 1.0);
        zoomedFrame = frameFor(sourceImage2, columns, rows, pixelWidth, pixelHeight, left, top, 1.12);
        whiteFrame = solidScreen(columns, rows, 255, 255, 255);
        cachedColumns = columns;
        cachedRows = rows;
    }

    private static BufferedImage loadImageOrFallback(String filename, boolean lunging) {
        try {
            File imageFile = new File(filename);
            if (imageFile.isFile()) {
                BufferedImage image = ImageIO.read(imageFile);
                if (image != null && image.getWidth() > 0 && image.getHeight() > 0) return image;
                System.err.println(filename + " is not a readable PNG; using generated sprite.");
            } else {
                System.err.println(filename + " missing; using built-in generated sprite.");
            }
        } catch (Exception ex) {
            System.err.println("Unable to read " + filename + ": " + ex.getMessage());
        }
        // Same original character as the optional PNGs, not a white flash.
        return CyberpunkSpriteGenerator.createFrame(lunging);
    }

    private static String frameFor(BufferedImage source, int columns, int rows,
                                   int pixelWidth, int pixelHeight, int left, int top, double zoom) {
        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, pixelWidth, pixelHeight);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double scale = Math.min((double)pixelWidth / source.getWidth(),
                                (double)pixelHeight / source.getHeight()) * zoom;
        int w = Math.max(1, (int)Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int)Math.round(source.getHeight() * scale));
        g.drawImage(source, (pixelWidth - w) / 2, (pixelHeight - h) / 2, w, h, null);
        g.dispose();

        StringBuilder sb = new StringBuilder(solidScreen(columns, rows, 0, 0, 0));
        for (int y = 0; y < pixelHeight / 2; y++) {
            sb.append(ESC).append(top + y).append(';').append(left).append('H');
            int previousTop = -1;
            int previousBottom = -1;
            for (int x = 0; x < pixelWidth; x++) {
                int rgbTop = image.getRGB(x, y * 2) & 0xffffff;
                int rgbBottom = image.getRGB(x, y * 2 + 1) & 0xffffff;
                if (rgbTop != previousTop || rgbBottom != previousBottom) {
                    appendColour(sb, 38, rgbTop);
                    appendColour(sb, 48, rgbBottom);
                    previousTop = rgbTop;
                    previousBottom = rgbBottom;
                }
                sb.append('\u2580'); // Foreground is upper subpixel, background is lower.
            }
        }
        return sb.append(ESC).append("0m").toString();
    }

    private static String solidScreen(int columns, int rows, int red, int green, int blue) {
        StringBuilder sb = new StringBuilder(ESC).append("48;2;")
            .append(red).append(';').append(green).append(';').append(blue).append('m');
        // Reserve the rightmost cell to prevent wrap on Windows and macOS.
        String spaces = " ".repeat(Math.max(0, columns - 1));
        for (int y = 1; y <= rows; y++) {
            sb.append(ESC).append(y).append(";1H").append(spaces);
        }
        return sb.append(ESC).append("0m").toString();
    }

    // Headless verification of the actual terminal frame without a two-second animation.
    static synchronized String frameForTest(int columns, int rows) {
        ensureFrames(columns, rows);
        return normalFrame;
    }

    private static void appendColour(StringBuilder sb, int code, int rgb) {
        sb.append(ESC).append(code).append(";2;")
          .append((rgb >> 16) & 255).append(';')
          .append((rgb >> 8) & 255).append(';')
          .append(rgb & 255).append('m');
    }
}
