package com.dungeoncrawler.engine;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class MusicPlayer {
    private Clip clip;

    public void playLoop(String filePath) {
        try {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                System.out.println("Audio file not found: " + filePath);
                return;
            }
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.out.println("Audio Error: " + e.getMessage());
        }
    }

    /** Restart the existing loaded music clip instead of allocating another one. */
    public void resume() {
        if (clip != null && clip.isOpen()) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    /** Release the audio device when the game is finally closing. */
    public void close() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    public void stop() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }
}