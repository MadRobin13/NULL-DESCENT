package com.dungeoncrawler.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises asset handling using simulated clips; no sound card is required. */
class AssetLoadingTest {
    @TempDir Path tempDir;

    @Test
    void missingTrackNeverTriesToOpenAnAudioDevice() {
        AtomicInteger requested = new AtomicInteger();
        MusicPlayer player = new MusicPlayer(() -> {
            requested.incrementAndGet();
            throw new AssertionError("A missing file must not request a clip");
        });
        assertDoesNotThrow(() -> player.playLoop(tempDir.resolve("missing.wav").toString()));
        assertDoesNotThrow(player::stop);
        assertDoesNotThrow(player::resume);
        assertDoesNotThrow(player::close);
        assertEquals(0, requested.get());
    }

    @Test
    void unsupportedAudioNeverTriesToOpenAnAudioDevice() throws IOException {
        Path invalid = tempDir.resolve("not-wav.wav");
        Files.writeString(invalid, "This is not audio data");
        AtomicInteger requested = new AtomicInteger();
        MusicPlayer player = new MusicPlayer(() -> {
            requested.incrementAndGet();
            throw new AssertionError("An unsupported file must not request a clip");
        });
        assertDoesNotThrow(() -> player.playLoop(invalid.toString()));
        assertEquals(0, requested.get());
    }

    @Test
    void replacingMusicClosesThePreviousClip() throws IOException {
        Path wav = createTinyWaveFile();
        FakeClip first = new FakeClip();
        FakeClip second = new FakeClip();
        Queue<Clip> clips = new ArrayDeque<>();
        clips.add(first.proxy());
        clips.add(second.proxy());
        MusicPlayer player = new MusicPlayer(clips::remove);

        player.playLoop(wav.toString());
        assertEquals(1, first.opens);
        assertEquals(1, first.loops);
        assertFalse(first.closed);

        player.playLoop(wav.toString());
        assertTrue(first.closed, "Reloading music must close the old clip");
        assertEquals(1, second.opens);
        assertEquals(1, second.loops);
        assertFalse(second.closed);

        player.close();
        assertTrue(second.closed);
        assertDoesNotThrow(player::close);
    }

    @Test
    void stoppingAndResumingReusesTheExistingClip() throws IOException {
        Path wav = createTinyWaveFile();
        FakeClip fake = new FakeClip();
        AtomicInteger requested = new AtomicInteger();
        MusicPlayer player = new MusicPlayer(() -> {
            requested.incrementAndGet();
            return fake.proxy();
        });

        player.playLoop(wav.toString());
        player.stop();
        assertEquals(1, fake.stops);
        assertFalse(fake.running);
        player.resume();
        assertEquals(2, fake.loops);
        assertTrue(fake.running);
        assertEquals(1, requested.get(), "Resume must not allocate another Clip");
        player.close();
        assertTrue(fake.closed);
    }

    @Test
    void failedClipOpenReleasesTheAllocatedClip() throws IOException {
        Path wav = createTinyWaveFile();
        FakeClip fake = new FakeClip();
        fake.failOnOpen = true;
        MusicPlayer player = new MusicPlayer(fake::proxy);

        assertDoesNotThrow(() -> player.playLoop(wav.toString()));
        assertTrue(fake.closed, "An unsuccessful Clip.open() must not leak its audio device");
        assertDoesNotThrow(player::resume);
        assertDoesNotThrow(player::close);
    }

    @Test
    void builtInJumpscareSpritesCanBeGeneratedWithoutImagesOrSoundHardware() {
        assertNotNull(CyberpunkSpriteGenerator.createFrame(false));
        assertNotNull(CyberpunkSpriteGenerator.createFrame(true));
        assertDoesNotThrow(JumpscareManager::initialize);
    }

    private Path createTinyWaveFile() throws IOException {
        Path wav = tempDir.resolve("silent.wav");
        AudioFormat format = new AudioFormat(8000f, 16, 1, true, false);
        byte[] samples = new byte[320];
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(samples), format, samples.length / format.getFrameSize())) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav.toFile());
        }
        return wav;
    }

    private static class FakeClip {
        boolean closed;
        boolean opened;
        boolean running;
        boolean failOnOpen;
        int opens;
        int loops;
        int stops;

        Clip proxy() {
            return (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(), new Class<?>[] {Clip.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "open" -> {
                            opens++;
                            if (failOnOpen) throw new LineUnavailableException("Simulated device failure");
                            opened = true;
                            yield null;
                        }
                        case "loop" -> {
                            loops++;
                            running = true;
                            yield null;
                        }
                        case "isRunning", "isActive" -> running;
                        case "isOpen" -> opened && !closed;
                        case "stop" -> {
                            stops++;
                            running = false;
                            yield null;
                        }
                        case "close" -> {
                            closed = true;
                            opened = false;
                            running = false;
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException("Unexpected audio method: " + method.getName());
                    };
                });
        }
    }
}
