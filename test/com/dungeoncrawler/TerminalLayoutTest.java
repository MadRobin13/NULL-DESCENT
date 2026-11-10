package com.dungeoncrawler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerminalLayoutTest {
    @Test
    void exactlyOneHundredByFiftyFiveFits() {
        assertFalse(GameEngine.terminalTooSmall(100, 55));
        assertFalse(GameEngine.terminalTooSmall(130, 60));
    }

    @Test
    void rejectsSmallerOrUnknownDimensions() {
        assertTrue(GameEngine.terminalTooSmall(99, 55));
        assertTrue(GameEngine.terminalTooSmall(100, 54));
        assertTrue(GameEngine.terminalTooSmall(80, 24));
        assertTrue(GameEngine.terminalTooSmall(0, 0));
    }
}
