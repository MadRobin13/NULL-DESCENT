package com.dungeoncrawler.engine;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates two deterministic, original pixel-art frames for the NULL//DESCENT
 * jumpscare. Run once from the project root to create jumpscare1.png and
 * jumpscare2.png. The running game can also create them in memory if absent.
 * Uses only Java's built-in 2D image API; no GUI window is created.
 */
public final class CyberpunkSpriteGenerator {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static final int SIZE = 112;
    private static final Color VOID = new Color(7, 9, 18);
    private static final Color OUTLINE = new Color(7, 11, 23);
    private static final Color STEEL = new Color(34, 40, 62);
    private static final Color LIGHT_STEEL = new Color(79, 87, 113);
    private static final Color PURPLE = new Color(82, 26, 121);
    private static final Color MAGENTA = new Color(195, 21, 146);
    private static final Color CYAN = new Color(12, 227, 239);
    private static final Color RED = new Color(235, 31, 65);
    private static final Color PALE = new Color(230, 244, 239);

    private CyberpunkSpriteGenerator() { }

    public static void main(String[] args) {
        try {
            ImageIO.write(createFrame(false), "png", new File("jumpscare1.png"));
            ImageIO.write(createFrame(true), "png", new File("jumpscare2.png"));
            System.out.println("Generated jumpscare1.png and jumpscare2.png in the current directory.");
        } catch (IOException ex) {
            System.err.println("Could not write jumpscare images: " + ex.getMessage());
            System.exit(1);
        }
    }

    /** Draw the same digital predator twice, with a different expression and jaw. */
    public static BufferedImage createFrame(boolean lunging) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        rect(g, VOID, 0, 0, SIZE, SIZE);
        Random rng = new Random(lunging ? 76014 : 76013);

        // Quiet corrupted-network texture behind the mask, consistent across renders.
        for (int i = 0; i < 42; i++) {
            int x = rng.nextInt(SIZE), y = rng.nextInt(SIZE);
            rect(g, i % 2 == 0 ? new Color(19, 24, 47) : new Color(29, 15, 43),
                 x, y, 2 + rng.nextInt(10), 1 + rng.nextInt(2));
        }

        // Broken antennae and hard-edged shoulders: deliberately not a bear/animal face.
        polygon(g, OUTLINE, new int[] {18, 23, 24, 19, 24, 2, 30, 2, 38, 18, 46, 21},
                             new int[] {23, 23, 12, 12, 21, 21, 27, 30});
        polygon(g, OUTLINE, new int[] {67, 78, 84, 91, 91, 99, 96},
                             new int[] {30, 21, 5, 5, 20, 20, 30});
        rect(g, CYAN, 26, 5, 3, 10);
        rect(g, RED, 86, 7, 3, 12);
        rect(g, MAGENTA, 11, 34, 5, 22);
        rect(g, CYAN, 98, 34, 5, 22);

        // Silhouette is polygonal rather than rounded: an infected security sentinel.
        polygon(g, OUTLINE,
                new int[] {18, 30, 82, 96, 105, 107, 100, 93, 91, 80, 31, 19, 9, 6},
                new int[] {17, 10, 11, 18, 32, 70, 92, 106, 111, 108, 108, 104, 90, 30});
        polygon(g, LIGHT_STEEL,
                new int[] {20, 31, 81, 94, 100, 99, 89, 79, 33, 21, 12, 12},
                new int[] {21, 15, 15, 23, 36, 84, 98, 103, 102, 95, 83, 35});
        polygon(g, STEEL,
                new int[] {23, 33, 77, 91, 97, 93, 84, 76, 37, 24, 17, 17},
                new int[] {26, 20, 20, 28, 40, 80, 91, 95, 95, 90, 75, 37});
        // Forehead corrupted alloy and asymmetric damaged panel.
        polygon(g, PURPLE, new int[] {28, 40, 76, 86, 82, 63, 49, 27},
                            new int[] {25, 19, 23, 34, 44, 41, 46, 40});
        rect(g, MAGENTA, 33, 23, 13, 3);
        rect(g, CYAN, 63, 25, 12, 2);
        rect(g, new Color(16, 24, 39), 47, 19, 18, 15);
        rect(g, RED, 54, 20, 3, 10);
        rect(g, CYAN, 59, 20, 2, 5);

        // Severe jagged eye cavities. Two different corrupted optical systems.
        int socketTop = lunging ? 35 : 39;
        int socketBottom = lunging ? 72 : 67;
        polygon(g, OUTLINE,
                new int[] {22, 29, 42, 50, 48, 43, 27, 18},
                new int[] {socketTop + 3, socketTop - 3, socketTop - 3, socketTop + 5,
                           socketBottom - 7, socketBottom, socketBottom, socketBottom - 8});
        polygon(g, OUTLINE,
                new int[] {62, 70, 84, 95, 92, 84, 67, 59},
                new int[] {socketTop + 5, socketTop - 3, socketTop - 1, socketTop + 8,
                           socketBottom - 6, socketBottom, socketBottom, socketBottom - 8});
        // LEFT: damaged cyan optic with white-hot focal point.
        rect(g, new Color(11, 51, 67), 26, lunging ? 48 : 49, 17, 14);
        rect(g, CYAN, lunging ? 28 : 32, lunging ? 47 : 51, lunging ? 13 : 7, lunging ? 12 : 8);
        rect(g, PALE, lunging ? 32 : 34, lunging ? 50 : 52, 4, lunging ? 7 : 4);
        rect(g, new Color(28, 128, 147), 23, socketBottom - 5, 20, 2);
        // RIGHT: exposed red optic, displaced and glitching rather than mirrored.
        rect(g, new Color(78, 7, 24), 69, lunging ? 46 : 49, 17, 17);
        rect(g, RED, lunging ? 68 : 73, lunging ? 46 : 52, lunging ? 19 : 10, lunging ? 15 : 8);
        rect(g, PALE, lunging ? 74 : 76, lunging ? 50 : 53, 4, lunging ? 8 : 4);
        rect(g, MAGENTA, 68, socketBottom - 2, 20, 2);
        // Angular cracks and live wires reaching the jaw.
        line(g, CYAN, 19, 42, 13, 67, 2);
        line(g, CYAN, 13, 67, 24, 75, 2);
        line(g, RED, 93, 49, 101, 58, 2);
        line(g, MAGENTA, 101, 58, 95, 78, 2);
        line(g, LIGHT_STEEL, 56, 38, 53, 62, 2);
        polygon(g, OUTLINE, new int[] {52, 61, 62, 60, 58}, new int[] {72, 72, 76, 78, 80});

        // Artificial mouth: black abyss with prominent angled pixel teeth.
        int mouthTop = lunging ? 69 : 76;
        int mouthBottom = lunging ? 107 : 101;
        polygon(g, OUTLINE,
                new int[] {19, 31, 83, 96, 94, 82, 33, 20},
                new int[] {mouthTop + 3, mouthTop - 4, mouthTop - 4, mouthTop + 3,
                           mouthBottom - 9, mouthBottom, mouthBottom, mouthBottom - 8});
        rect(g, new Color(62, 8, 33), 27, mouthTop + 4, 61, 4);
        rect(g, new Color(76, 12, 51), 32, mouthBottom - 10, 51, 3);
        for (int i = 0; i < 8; i++) {
            int x = 28 + i * 8;
            int topBase = mouthTop + (i % 2 == 0 ? 2 : 4);
            int topDepth = lunging ? 14 + (i % 3) * 2 : 11 + (i % 3);
            polygon(g, PALE, new int[] {x, x + 6, x + 3},
                             new int[] {topBase, topBase, topBase + topDepth});
            int bottomBase = mouthBottom - (i % 2 == 0 ? 3 : 5);
            int bottomDepth = lunging ? 12 + (i % 3) * 2 : 9 + (i % 3);
            polygon(g, new Color(178, 206, 224), new int[] {x, x + 6, x + 3},
                             new int[] {bottomBase, bottomBase, bottomBase - bottomDepth});
        }
        rect(g, RED, 30, mouthTop + 1, 57, 2);
        rect(g, MAGENTA, 33, mouthBottom - 2, 52, 2);

        // Chipped armour / warning strips / digital interference.
        rect(g, LIGHT_STEEL, 12, 77, 6, 15);
        rect(g, CYAN, 16, 74, 4, 5);
        rect(g, LIGHT_STEEL, 94, 79, 8, 15);
        rect(g, RED, 95, 84, 4, 6);
        rect(g, PURPLE, 37, 32, 8, 4);
        rect(g, MAGENTA, 77, 34, 8, 3);
        if (lunging) {
            rect(g, CYAN, 8, 46, 26, 2);
            rect(g, MAGENTA, 75, 61, 32, 3);
            rect(g, RED, 16, 89, 20, 2);
            rect(g, PALE, 60, 46, 3, 19);
            rect(g, new Color(190, 21, 61), 95, 32, 7, 3);
        } else {
            rect(g, new Color(76, 39, 107), 34, 44, 11, 2);
            rect(g, new Color(67, 117, 151), 71, 44, 11, 2);
        }
        for (int i = 0; i < 8; i++) {
            int x = 11 + rng.nextInt(91), y = 23 + rng.nextInt(80);
            rect(g, i % 2 == 0 ? CYAN : MAGENTA, x, y, 2 + rng.nextInt(6), 1);
        }
        g.dispose();
        return image;
    }

    private static void rect(Graphics2D g, Color color, int x, int y, int w, int h) {
        g.setColor(color);
        g.fillRect(x, y, w, h);
    }

    private static void polygon(Graphics2D g, Color color, int[] xs, int[] ys) {
        g.setColor(color);
        g.fillPolygon(xs, ys, Math.min(xs.length, ys.length));
    }

    private static void line(Graphics2D g, Color color, int x1, int y1, int x2, int y2, int thickness) {
        g.setColor(color);
        g.setStroke(new java.awt.BasicStroke(thickness));
        g.drawLine(x1, y1, x2, y2);
    }
}
