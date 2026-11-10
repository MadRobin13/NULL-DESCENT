package com.dungeoncrawler.engine;

/**
 * Turns terminal input into single game commands. A terminal sends arrow keys
 * and mouse events as multi-character escape sequences; passing those bytes
 * directly to WASD accidentally treated an arrow's trailing 'A' as "left".
 */
public final class TerminalInputParser {
    private static final int NORMAL = 0;
    private static final int ESCAPE = 1;
    private static final int CSI = 2;
    private static final int SS3 = 3;
    private static final int X10_MOUSE = 4;

    private int state = NORMAL;
    private final StringBuilder parameters = new StringBuilder();
    private int mouseBytesRemaining;

    /**
     * @return a complete, accepted game key, or null for an incomplete sequence,
     * mouse wheel event, unknown special key, or other control character.
     */
    public Character accept(int code) {
        if (code < 0) return null;
        switch (state) {
            case NORMAL -> {
                if (code == 27) {
                    state = ESCAPE;
                    return null;
                }
                if (code == 3 || code == 4) return (char) code; // Ctrl+C / Ctrl+D
                if (code == '\n' || code == '\r') return '\n';
                return code >= 32 && code <= 126 ? (char) code : null;
            }
            case ESCAPE -> {
                if (code == '[') {
                    state = CSI;
                    parameters.setLength(0);
                } else if (code == 'O') {
                    state = SS3;
                } else if (code != 27) {
                    // Ignore Alt+key sequences rather than triggering the game key.
                    state = NORMAL;
                }
                return null;
            }
            case SS3 -> {
                state = NORMAL;
                return arrowDirection(code);
            }
            case CSI -> {
                if (code == 'M' && parameters.isEmpty()) {
                    // Old X10 mouse format: ESC [ M, followed by three raw bytes.
                    mouseBytesRemaining = 3;
                    state = X10_MOUSE;
                    return null;
                }
                if (code >= 0x40 && code <= 0x7e) {
                    state = NORMAL;
                    // CSI <...M and <...m are SGR mouse reports. CSI 64;...M
                    // can also represent older mouse reports. Never treat them as keys.
                    String args = parameters.toString();
                    parameters.setLength(0);
                    if (args.isEmpty() || args.matches("[0-9;]+")) {
                        return arrowDirection(code);
                    }
                    return null;
                }
                if (code >= 0x20 && code <= 0x3f && parameters.length() < 64) {
                    parameters.append((char) code);
                    return null;
                }
                state = NORMAL; // Invalid or excessively long sequence.
                parameters.setLength(0);
                return null;
            }
            case X10_MOUSE -> {
                if (--mouseBytesRemaining == 0) state = NORMAL;
                return null;
            }
            default -> {
                state = NORMAL;
                return null;
            }
        }
    }

    /** Drop an incomplete sequence after an idle read timeout. */
    public void resetIncompleteSequence() {
        state = NORMAL;
        parameters.setLength(0);
        mouseBytesRemaining = 0;
    }

    private Character arrowDirection(int code) {
        return switch (code) {
            case 'A' -> 'w'; // up
            case 'B' -> 's'; // down
            case 'C' -> 'd'; // right
            case 'D' -> 'a'; // left
            default -> null;
        };
    }
}
