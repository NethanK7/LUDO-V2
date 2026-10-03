package ludot.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Pattern;
import ludot.LudoSimulationFacade;
import ludot.random.SeededRandomnessProvider;
import org.junit.jupiter.api.Test;

/** Plays whole games and checks every printed line is a message from the brief. */
class OutputFormatComplianceTest {

    private static final String COLOUR = "(red|yellow|blue|green)";
    private static final String PIECE = "[RYBG][1-4]";
    private static final String SQUARE = "(\\d+|[a-z]+homepath\\d)";

    private static final List<Pattern> SECTION_3_1 = List.of(
            matchLine("The " + COLOUR + " player has four \\(04\\) pieces named R1, R2, R3, and R4\\."
                    .replace("R1, R2, R3, and R4", "[RYBG]1, [RYBG]2, [RYBG]3, and [RYBG]4")),
            matchLine(COLOUR + " rolls [1-6]"),
            matchLine(COLOUR + " player has the highest roll and will begin the game\\."),
            matchLine("The order of a single round is " + COLOUR + ", " + COLOUR + ", " + COLOUR
                    + ", and " + COLOUR + "\\."),
            matchLine(COLOUR + " player rolled [1-6]\\."),
            matchLine(COLOUR + " player moves piece " + PIECE + " to the starting point\\."),
            matchLine(COLOUR + " player now has [0-4]/4 on pieces on the board and [0-4]/4 pieces on "
                    + "the base\\."),
            matchLine(COLOUR + " moves piece " + PIECE + " from location " + SQUARE + " to "
                    + "(" + SQUARE + "|Home) by [1-9]\\d* units in (clockwise|counter-clockwise) "
                    + "direction\\."),
            matchLine(COLOUR + " piece " + PIECE + " is blocked from moving from (" + SQUARE
                    + "|Base) to " + SQUARE + " by " + COLOUR + " piece " + PIECE + "\\."),
            matchLine(COLOUR + " does not have other pieces in the board to move instead of the "
                    + "blocked piece\\. Ignoring the throw and moving on to the next player\\."),
            matchLine(COLOUR + " does not have other pieces in the board to move instead of the "
                    + "blocked piece\\. Moved the piece to square \\d+ which is the cell before "
                    + "the block\\."),
            matchLine(COLOUR + " piece " + PIECE + " lands on square \\d+, captures " + COLOUR
                    + " piece " + PIECE + ", and returns it to the base\\."),
            matchLine("============================"),
            matchLine("Location of pieces " + COLOUR),
            matchLine("Piece " + PIECE + " -> (" + SQUARE + "|Base|Home)\\."),
            matchLine("The mystery cell is at \\d+ and will be at that location for the next \\d+ "
                    + "values\\."),
            matchLine("A mystery cell has spawned in location \\d+ and will be at this location for "
                    + "the next four rounds\\."),
            matchLine(COLOUR + " player lands on a mystery cell and is teleported to (\\d+|Base)\\."),
            matchLine(COLOUR + " piece " + PIECE + " teleported to "
                    + "(Alpha|Beta|Gamma|Approach|X|Base)\\."),
            matchLine(COLOUR + " piece " + PIECE + " feels energized, and movement speed doubles\\."),
            matchLine(COLOUR + " piece " + PIECE + " feels sick, and movement speed halves\\."),
            matchLine(COLOUR + " piece " + PIECE + " attends briefing and cannot move for four "
                    + "rounds\\."),
            matchLine(COLOUR + " piece " + PIECE + " is movement-restricted and has rolled three "
                    + "consecutively\\. Teleporting piece " + PIECE + " to base\\."),
            matchLine("The " + COLOUR + " piece " + PIECE + ", which was moving clockwise, has changed "
                    + "to moving counterclockwise\\."),
            matchLine("The " + COLOUR + " piece " + PIECE + " is moving in a counterclockwise "
                    + "direction\\. Teleporting to Beta from Gamma\\."),
            matchLine(COLOUR + " player wins!!!"),
            matchLine("Final standings"),
            matchLine("(1st|2nd|3rd|4th) place: " + COLOUR),
            matchLine(""));

    private static Pattern matchLine(String regex) {
        return Pattern.compile(regex);
    }

    @Test
    void everyPrintedLineOfFortyGamesIsOneOfTheRequiredMessages() {
        for (long seed = 1; seed <= 40; seed++) {
            for (String printed : recordTranscript(seed).split("\n", -1)) {
                boolean required = SECTION_3_1.stream().anyMatch(p -> p.matcher(printed).matches());
                assertTrue(required, "seed " + seed + ", not a Section 3.1 message: \"" + printed
                        + "\"");
            }
        }
    }

    @Test
    void theWinnerIsAnnouncedAndAllFourPlacesAreListed() {
        String transcript = recordTranscript(42);

        assertEquals(1, transcript.lines().filter(l -> l.endsWith("player wins!!!")).count());
        for (String place : List.of("1st", "2nd", "3rd", "4th")) {
            assertEquals(1, transcript.lines().filter(l -> l.startsWith(place + " place: ")).count());
        }
    }

    private static String recordTranscript(long seed) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new LudoSimulationFacade(new SeededRandomnessProvider(seed), new PrintStream(bytes)).run();
        return bytes.toString().replace(System.lineSeparator(), "\n");
    }
}
