package com.dungeoncrawler.engine;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Terminal mode and Unicode checks without changing the machine's terminal. */
class WindowsTerminalTest {
    @Test
    void gameSessionLeavesOriginalScreenAndInputSettingsIntact() {
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output, true);
        Attributes initialAttributes = new Attributes();
        AtomicInteger rawModes = new AtomicInteger();
        AtomicInteger restoredAttributes = new AtomicInteger();

        Terminal fake = (Terminal) Proxy.newProxyInstance(Terminal.class.getClassLoader(),
            new Class<?>[] { Terminal.class }, (proxy, method, args) -> switch (method.getName()) {
                case "enterRawMode" -> { rawModes.incrementAndGet(); yield initialAttributes; }
                case "getAttributes" -> initialAttributes;
                case "setAttributes" -> {
                    restoredAttributes.incrementAndGet();
                    yield null;
                }
                case "getType" -> "xterm-256color";
                case "close" -> null;
                case "writer" -> writer;
                case "flush" -> { writer.flush(); yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });

        try (TerminalSession ignored = new TerminalSession(fake)) {
            assertEquals(1, rawModes.get());
            assertTrue(output.toString().contains("\033[?1049h"));
            assertTrue(output.toString().contains("\033[?25l"));
            assertTrue(output.toString().contains("\033[?1000h"));
            assertTrue(output.toString().contains("\033[?1006h"));
            assertEquals(1, restoredAttributes.get());
        }
        assertEquals(2, restoredAttributes.get());
        assertTrue(output.toString().endsWith(TerminalSession.EXIT_SCREEN));
        assertTrue(output.toString().contains("\033[?1006l"));
        assertTrue(output.toString().contains("\033[?1000l"));
        assertFalse(output.toString().contains("\033[?7"),
            "Neither startup nor cleanup may change the original wrapping mode");
        assertFalse(output.toString().contains("65001"),
            "The session must never change the shell's code page");
    }

    @Test
    void framesUseUnicodeHalfBlocksAndTrueColourWithin80By24() {
        String frame = JumpscareManager.frameForTest(80, 24);
        assertTrue(frame.contains("▀"), "Pixel art must use Unicode half-block glyphs");
        assertTrue(frame.contains("\033[38;2;"));
        assertTrue(frame.contains("\033[48;2;"));
        assertFalse(frame.contains("\033[?7"), "Jumpscare must not modify autowrap");
        assertFalse(frame.contains("\n"), "Frame lines cannot trigger scrolling");
        assertTrue(frame.contains("\033[24;1H"));
    }

    @Test
    void renderNeverWritesTheFinalColumnOfTheTerminal() {
        TerminalRenderer renderer = new TerminalRenderer(80, 24);
        renderer.drawChar(79, 23, 'X', TerminalRenderer.RESET);
        renderer.drawChar(78, 23, 'Y', TerminalRenderer.RESET);
        String output = renderer.buildFrame(80, 24);
        assertFalse(output.contains("X"));
        assertTrue(output.contains("Y"));
    }
}
