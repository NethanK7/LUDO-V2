package ludot.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Pattern;
import ludot.LudoTSimulation;
import ludot.random.SeededRandomSource;
import org.junit.jupiter.api.Test;

/**
 * Section 3.1 of the brief lists the messages the simulation must print. This test plays whole
 * games and checks that <em>every single line</em> is one of them, so nothing unrequested slips in.
 */
class OutputMatchesBriefTest {

    private static final String COLOUR = "(red|yellow|blue|green)";
    private static final String PIECE = "[RYBG][1-4]";
    private static final String SQUARE = "(\\d+|[a-z]+homepath\\d)";

    private static final List<Pattern> SECTION_3_1 = List.of(
            // before the game begins, and choosing the first player
            line("The " + COLOUR + " player has four \\(04\\) pieces named R1, R2, R3, and R4\\."
                    .replace("R1, R2, R3, and R4", "[RYBG]1, [RYBG]2, [RYBG]3, and [RYBG]4")),
            line(COLOUR + " rolls [1-6]"),
            line(COLOUR + " player has the highest roll and will begin the game\\."),
            line("The order of a single round is " + COLOUR + ", " + COLOUR + ", " + COLOUR
                    + ", and " + COLOUR + "\\."),
            // rolling and moving
            line(COLOUR + " player rolled [1-6]\\."),
            line(COLOUR + " player moves piece " + PIECE + " to the starting point\\."),
            line(COLOUR + " player now has [0-4]/4 on pieces on the board and [0-4]/4 pieces on "
                    + "the base\\."),
            line(COLOUR + " moves piece " + PIECE + " from location " + SQUARE + " to "
                    + "(" + SQUARE + "|Home) by [1-9]\\d* units in (clockwise|counter-clockwise) "
                    + "direction\\."),
            // blocks
            line(COLOUR + " piece " + PIECE + " is blocked from moving from (" + SQUARE
                    + "|Base) to " + SQUARE + " by " + COLOUR + " piece " + PIECE + "\\."),
            line(COLOUR + " does not have other pieces in the board to move instead of the "
                    + "blocked piece\\. Ignoring the throw and moving on to the next player\\."),
            line(COLOUR + " does not have other pieces in the board to move instead of the "
                    + "blocked piece\\. Moved the piece to square \\d+ which is the cell before "
                    + "the block\\."),
            // captures
            line(COLOUR + " piece " + PIECE + " lands on square \\d+, captures " + COLOUR
                    + " piece " + PIECE + ", and returns it to the base\\."),
            // after each round
            line("============================"),
            line("Location of pieces " + COLOUR),
            line("Piece " + PIECE + " -> (" + SQUARE + "|Base|Home)\\."),
            line("The mystery cell is at \\d+ and will be at that location for the next \\d+ "
                    + "values\\."),
            // mystery cell
            line("A mystery cell has spawned in location \\d+ and will be at this location for "
                    + "the next four rounds\\."),
            line(COLOUR + " player lands on a mystery cell and is teleported to (\\d+|Base)\\."),
            line(COLOUR + " piece " + PIECE + " teleported to "
                    + "(Alpha|Beta|Gamma|Approach|X|Base)\\."),
            line(COLOUR + " piece " + PIECE + " feels energized, and movement speed doubles\\."),
            line(COLOUR + " piece " + PIECE + " feels sick, and movement speed halves\\."),
            line(COLOUR + " piece " + PIECE + " attends briefing and cannot move for four "
                    + "rounds\\."),
            line(COLOUR + " piece " + PIECE + " is movement-restricted and has rolled three "
                    + "consecutively\\. Teleporting piece " + PIECE + " to base\\."),
            line("The " + COLOUR + " piece " + PIECE + ", which was moving clockwise, has changed "
                    + "to moving counterclockwise\\."),
            line("The " + COLOUR + " piece " + PIECE + " is moving in a counterclockwise "
                    + "direction\\. Teleporting to Beta from Gamma\\."),
            // winning, and Rule 11: "The game may continue to find second, third, and fourth places"
            line(COLOUR + " player wins!!!"),
            line("Final standings"),
            line("(1st|2nd|3rd|4th) place: " + COLOUR),
            // blank lines between turns and rounds
            line(""));

    private static Pattern line(String regex) {
        return Pattern.compile(regex);
    }

    @Test
    void everyPrintedLineOfFortyGamesIsOneOfTheRequiredMessages() {
        for (long seed = 1; seed <= 40; seed++) {
            for (String printed : transcript(seed).split("\n", -1)) {
                boolean required = SECTION_3_1.stream().anyMatch(p -> p.matcher(printed).matches());
                assertTrue(required, "seed " + seed + ", not a Section 3.1 message: \"" + printed
                        + "\"");
            }
        }
    }

    @Test
    void theWinnerIsAnnouncedAndAllFourPlacesAreListed() {
        String transcript = transcript(42);

        assertEquals(1, transcript.lines().filter(l -> l.endsWith("player wins!!!")).count());
        for (String place : List.of("1st", "2nd", "3rd", "4th")) {
            assertEquals(1, transcript.lines().filter(l -> l.startsWith(place + " place: ")).count());
        }
    }

    private static String transcript(long seed) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new LudoTSimulation(new SeededRandomSource(seed), new PrintStream(bytes)).run();
        return bytes.toString().replace(System.lineSeparator(), "\n");
    }
}
