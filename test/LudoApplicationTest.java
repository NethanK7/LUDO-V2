import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for reading the seed and starting a game. */
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
    void aWholeNumberIsASeed() {
        assertEquals(42L, LudoApplication.parseSeed("42"));
        assertEquals(-7L, LudoApplication.parseSeed(" -7 "));
    }

    @Test
    void anythingElseIsRefusedWithAReadableMessage() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> LudoApplication.parseSeed("abc"));

        assertEquals("The seed must be a whole number, but was: \"abc\"", error.getMessage());
    }

    @Test
    void anInvalidSeedPrintsTheProblemInsteadOfAStackTrace() {
        LudoApplication.main(new String[] {"abc"});

        assertTrue(err.toString().contains("The seed must be a whole number"));
        assertEquals("", out.toString());
    }

    @Test
    void aSeededRunPlaysACompleteGame() {
        LudoApplication.main(new String[] {"42"});

        assertTrue(out.toString().contains("player wins!!!"));
        assertTrue(out.toString().contains("4th place: "));
    }

    @Test
    void runningWithoutASeedStillPlaysACompleteGame() {
        LudoApplication.main(new String[0]);

        String printed = out.toString();
        assertTrue(printed.contains("The order of a single round is"));
        assertTrue(printed.contains("player wins!!!") || printed.contains("No piece has moved"));
        assertEquals("", err.toString());
    }
}
