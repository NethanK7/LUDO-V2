import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for starting a game from the command line, with and without a seed. */
class LudoApplicationTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void captureConsole() {
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
    }

    @AfterEach
    void restoreConsole() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void anInvalidSeedPrintsTheProblemInsteadOfAStackTrace() {
        LudoApplication.main(new String[] {"abc"});

        assertTrue(err.toString().contains("The seed must be a whole number, but was: \"abc\""));
        assertEquals("", out.toString());
    }

    @Test
    void aSeededRunPlaysACompleteGame() {
        LudoApplication.main(new String[] {"42"});

        assertTrue(out.toString().contains("player wins!!!"));
        assertTrue(out.toString().contains("4th place: "));
    }

    @Test
    void spacesAroundTheSeedAreIgnoredSoTheSameGameIsPlayed() {
        LudoApplication.main(new String[] {"42"});
        String expected = out.toString();
        out.reset();

        LudoApplication.main(new String[] {"  42 "});

        assertEquals(expected, out.toString());
    }

    @Test
    void runningWithoutASeedStillPlaysAGame() {
        LudoApplication.main(new String[0]);

        assertTrue(out.toString().contains("The order of a single round is"));
        assertEquals("", err.toString());
    }
}
