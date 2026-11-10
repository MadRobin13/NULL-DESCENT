package com.dungeoncrawler.engine;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TerminalInputParserTest {
    private static List<Character> commands(String sequence) {
        TerminalInputParser parser = new TerminalInputParser();
        List<Character> result = new ArrayList<>();
        for (char c : sequence.toCharArray()) {
            Character key = parser.accept(c);
            if (key != null) result.add(key);
        }
        return result;
    }

    @Test
    void arrowKeysMoveInTheirActualDirections() {
        assertEquals(List.of('w'), commands("\033[A"));
        assertEquals(List.of('s'), commands("\033[B"));
        assertEquals(List.of('d'), commands("\033[C"));
        assertEquals(List.of('a'), commands("\033[D"));
        assertEquals(List.of('w'), commands("\033OA"));
        assertEquals(List.of('s'), commands("\033OB"));
        assertEquals(List.of('d'), commands("\033OC"));
        assertEquals(List.of('a'), commands("\033OD"));
        assertEquals(List.of('d'), commands("\033[1;5C"));
    }

    @Test
    void scrollingAndMouseClickReportsNeverGenerateGameplayCommands() {
        assertEquals(List.of(), commands("\033[<64;14;9M"));
        assertEquals(List.of(), commands("\033[<65;14;9M"));
        assertEquals(List.of(), commands("\033[<0;13;8M\033[<0;13;8m"));
        assertEquals(List.of(), commands("\033[M" + (char)96 + (char)40 + (char)41));
        assertEquals(List.of(), commands("\033[5~\033[6~"));
    }

    @Test
    void escapeSequencesCannotTriggerUnintendedSaveQuitOrMovement() {
        assertEquals(List.of('a', 'd'), commands("\033[Dd"));
        assertEquals(List.of('w', 'q'), commands("w\033[<64;4;7Mq"));
        assertEquals(List.of(), commands("\033q"));
        assertEquals(List.of(), commands("\033[?25l"));
    }

    @Test
    void normalKeysAndTimeoutsStillWork() {
        assertEquals(List.of('w', 'a', 's', 'd', 't', '\n', ' ', (char)3),
            commands("w a".replace(" ", "") + "sdt\r \003"));
        TerminalInputParser parser = new TerminalInputParser();
        assertNull(parser.accept(27));
        parser.resetIncompleteSequence();
        assertEquals('a', parser.accept('a'));
        assertNull(parser.accept(27));
        assertNull(parser.accept('['));
        parser.resetIncompleteSequence();
        assertEquals('d', parser.accept('d'));
    }
}
