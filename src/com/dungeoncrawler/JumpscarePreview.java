package com.dungeoncrawler;

import com.dungeoncrawler.engine.JumpscareManager;

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
        JumpscareManager.playJumpscare(flashing);
        System.out.println("Preview complete. Terminal restored.");
    }
}
