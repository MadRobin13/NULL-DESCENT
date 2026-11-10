package com.dungeoncrawler.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerminalRendererTest {
    @Test
    void clearingResetsCharactersAndColours() {
        TerminalRenderer renderer = new TerminalRenderer(10, 5);
        renderer.drawChar(5, 2, 'X', "\033[31m");
        assertEquals('X', renderer.getCharAt(5, 2));
        renderer.clearBuffer();
        assertEquals(' ', renderer.getCharAt(5, 2));
        assertEquals(TerminalRenderer.RESET, renderer.getColorAt(5, 2));
    }

    @Test
    void drawingOutOfBoundsIsSafelyClipped() {
        TerminalRenderer renderer = new TerminalRenderer(10, 5);
        assertDoesNotThrow(() -> renderer.drawChar(-1, 2, 'X', "\033[31m"));
        assertDoesNotThrow(() -> renderer.drawChar(10, 2, 'X', "\033[31m"));
        assertDoesNotThrow(() -> renderer.drawString(8, 0, "TestString", "\033[31m"));
        assertEquals('T', renderer.getCharAt(8, 0));
        assertEquals('e', renderer.getCharAt(9, 0));
    }

    @Test
    void drawingPreservesColourAttributes() {
        TerminalRenderer renderer = new TerminalRenderer(10, 5);
        renderer.drawString(0, 0, "AB", "\033[31m");
        assertEquals("\033[31m", renderer.getColorAt(0, 0));
        assertEquals("\033[31m", renderer.getColorAt(1, 0));
        renderer.drawChar(2, 0, 'C', null);
        assertEquals(TerminalRenderer.RESET, renderer.getColorAt(2, 0));
    }

    @Test
    void fullWidthLastRowUsesAbsoluteCursorPositionAndDoesNotScroll() {
        TerminalRenderer renderer = new TerminalRenderer(100, 55);
        renderer.drawChar(99, 54, '#', "\033[31m");
        String output = renderer.buildFrame();
        assertTrue(output.startsWith("\033[?7l\033[1;1H"));
        assertTrue(output.contains("\033[55;1H"));
        assertFalse(output.contains("\n"), "Full-width rows must never emit scrolling newlines");
        assertTrue(output.endsWith("\033[0m\033[?7h"), "Always restore colors and autowrap");
    }

    @Test
    void consecutiveSameColourCharactersDoNotRepeatEscapeCodes() {
        TerminalRenderer renderer = new TerminalRenderer(3, 1);
        renderer.drawString(0, 0, "ABC", "\033[31m");
        String output = renderer.buildFrame();
        assertEquals(1, countOccurrences(output, "\033[31m"));
        assertTrue(output.contains("\033[31mABC"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int position = 0;
        while ((position = text.indexOf(needle, position)) >= 0) {
            count++;
            position += needle.length();
        }
        return count;
    }
}
