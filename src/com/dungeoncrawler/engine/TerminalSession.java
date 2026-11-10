package com.dungeoncrawler.engine;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the native terminal for a game or preview session. Draw only on the
 * alternate screen, and restore the user's original shell on every exit path.
 */
public final class TerminalSession implements AutoCloseable {
    public static final String ENTER_SCREEN = "\033[?1049h\033[?25l\033[?1000h\033[?1006h\033[0m\033[2J\033[H";
    public static final String EXIT_SCREEN = "\033[?1006l\033[?1000l\033[0m\033[?25h\033[?1049l";

    private final Terminal terminal;
    private final Attributes originalAttributes;
    private final String originalUnixStty;
    private final AtomicBoolean cleanedUp = new AtomicBoolean(false);
    private final Thread shutdownHook;

    /** Open a real system terminal. JLine selects its platform-specific provider. */
    public static TerminalSession open() throws IOException {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        try {
            return new TerminalSession(terminal);
        } catch (RuntimeException ex) {
            terminal.close();
            throw ex;
        }
    }

    /** Package-visible for headless terminal lifecycle tests. */
    TerminalSession(Terminal terminal) {
        this.terminal = java.util.Objects.requireNonNull(terminal);
        if (Terminal.TYPE_DUMB.equals(terminal.getType())) {
            throw new IllegalStateException("An interactive ANSI-capable terminal is required");
        }
        // JLine does not preserve baud rates on every Unix PTY. Saving the
        // original stty state before raw mode lets us restore it exactly.
        originalUnixStty = captureUnixStty();
        originalAttributes = terminal.enterRawMode();
        // Ctrl+C should arrive as character 3, letting the Java finally block
        // restore the screen instead of leaving the console in an altered mode.
        Attributes playingAttributes = new Attributes(terminal.getAttributes());
        playingAttributes.setLocalFlag(Attributes.LocalFlag.ISIG, false);
        terminal.setAttributes(playingAttributes);
        shutdownHook = new Thread(this::cleanup, "null-descent-terminal-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            terminal.writer().print(ENTER_SCREEN);
            terminal.flush();
        } catch (RuntimeException ex) {
            close();
            throw ex;
        }
    }

    public Terminal terminal() { return terminal; }

    /** Also called by the shutdown hook if Ctrl+C or process exit skips normal cleanup. */
    private void cleanup() {
        if (!cleanedUp.compareAndSet(false, true)) return;
        try {
            PrintWriter output = terminal.writer();
            output.print(EXIT_SCREEN);
            output.flush();
            if (output.checkError()) {
                // JLine may have closed its writer in a parallel JVM shutdown hook.
                // The restoration escape codes are ASCII and safe on System.out.
                System.out.print(EXIT_SCREEN);
                System.out.flush();
            }
        } catch (RuntimeException ignored) {
            try {
                System.out.print(EXIT_SCREEN);
                System.out.flush();
            } catch (RuntimeException ignoredAgain) {
                // Continue restoring native input settings regardless.
            }
        }
        try {
            terminal.setAttributes(originalAttributes);
        } catch (RuntimeException ignored) {
            // Native terminal.close() is a second restoration opportunity.
        }
        try {
            terminal.close();
        } catch (IOException ignored) {
            // Continue with the original Unix terminal mode restoration.
        } finally {
            restoreUnixStty(originalUnixStty);
        }
    }

    @Override
    public void close() {
        cleanup();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // The JVM is already shutting down and running the hook.
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
            .contains("windows");
    }

    private static String captureUnixStty() {
        if (isWindows()) return null;
        try {
            Process process = new ProcessBuilder("stty", "-g")
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
            if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
                    && value.matches("[a-zA-Z0-9:;_\\-]+")) return value;
            process.destroyForcibly();
        } catch (IOException | InterruptedException | SecurityException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return null; // JLine will still restore flags if stty is unavailable.
    }

    private static void restoreUnixStty(String saved) {
        if (saved == null) return;
        try {
            Process process = new ProcessBuilder("stty", saved)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (IOException | InterruptedException | SecurityException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }
}
