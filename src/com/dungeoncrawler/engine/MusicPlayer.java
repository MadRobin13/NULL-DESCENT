package com.dungeoncrawler.engine;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import java.io.File;

/** Plays background music without leaking an old clip when a track is reloaded. */
public class MusicPlayer {
    @FunctionalInterface
    interface ClipFactory {
        Clip create() throws LineUnavailableException;
    }

    private final ClipFactory clipFactory;
    private Clip clip;

    public MusicPlayer() {
        this(AudioSystem::getClip);
    }

    // Package-private seam allows audio-state tests without sound hardware.
    MusicPlayer(ClipFactory clipFactory) {
        this.clipFactory = clipFactory;
    }

    /** Load and loop a track, replacing any previously playing track. */
    public void playLoop(String filePath) {
        close();
        if (filePath == null || filePath.isBlank()) {
            System.err.println("Background music disabled: no file specified.");
            return;
        }

        File audioFile = new File(filePath);
        if (!audioFile.isFile()) {
            System.err.println("Background music unavailable: " + filePath);
            return;
        }

        Clip newClip = null;
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(audioFile)) {
            newClip = clipFactory.create();
            newClip.open(stream);
            newClip.loop(Clip.LOOP_CONTINUOUSLY); // loop() starts the clip.
            clip = newClip;
        } catch (Exception error) {
            // A clip can be allocated even if open() or loop() subsequently fails.
            if (newClip != null) {
                newClip.close();
            }
            System.err.println("Background music disabled: " + error.getMessage());
        }
    }

    /** Resume the already loaded clip without opening another sound device. */
    public void resume() {
        if (clip != null && clip.isOpen()) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    /** Pause playback without losing the loaded track. */
    public void stop() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }

    /** Release the loaded audio device and make repeated calls harmless. */
    public void close() {
        if (clip != null) {
            try {
                if (clip.isRunning()) {
                    clip.stop();
                }
            } finally {
                clip.close();
                clip = null;
            }
        }
    }
}
