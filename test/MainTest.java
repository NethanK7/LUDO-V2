import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MainTest {

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
        assertEquals(42L, Main.parseSeed("42"));
        assertEquals(-7L, Main.parseSeed(" -7 "));
    }

    @Test
    void anythingElseIsRefusedWithAReadableMessage() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> Main.parseSeed("abc"));

        assertEquals("The seed must be a whole number, but was: \"abc\"", error.getMessage());
    }

    @Test
    void anInvalidSeedPrintsTheProblemInsteadOfAStackTrace() {
        Main.main(new String[] {"abc"});

        assertTrue(err.toString().contains("The seed must be a whole number"));
        assertEquals("", out.toString());
    }

    @Test
    void aSeededRunPlaysACompleteGame() {
        Main.main(new String[] {"42"});

        assertTrue(out.toString().contains("player wins!!!"));
        assertTrue(out.toString().contains("4th place: "));
    }
}
