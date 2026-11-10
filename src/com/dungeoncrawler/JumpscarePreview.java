package com.dungeoncrawler;

import com.dungeoncrawler.engine.JumpscareManager;
import org.jline.terminal.Terminal;
import com.dungeoncrawler.engine.TerminalSession;
import java.io.IOException;

/** Run from the project root to preview the effect without risking a saved game. */
public final class JumpscarePreview {
    public static void main(String[] args) {
        boolean flashing = args.length == 0 || !"--no-flash".equalsIgnoreCase(args[0]);
        System.out.println("Previewing jumpscare in one second. Press Ctrl+C to cancel.");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
        try (TerminalSession session = TerminalSession.open()) {
            Terminal terminal = session.terminal();
            JumpscareManager.playJumpscare(flashing, terminal.getWidth(), terminal.getHeight(),
                terminal.writer(), false);
        } catch (IOException | IllegalStateException ex) {
            System.err.println("Cannot open interactive terminal for preview: " + ex.getMessage());
            return;
        }
        System.out.println("Preview complete. Terminal restored.");
    }
}
