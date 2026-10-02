import ludot.LudoTSimulation;
import ludot.random.SeededRandomSource;

/**
 * Entry point of the LUDO-T simulation.
 *
 * <p>The game needs no interaction: running it plays a complete game and prints the result.
 *
 * <pre>
 *   java -cp out Main            # a different game every time
 *   java -cp out Main 12345      # the same game every time, replayed from seed 12345
 * </pre>
 */
public final class Main {

    private Main() {
        // Entry point only.
    }

    public static void main(String[] args) {
        SeededRandomSource randomSource;
        try {
            randomSource = args.length > 0
                    ? new SeededRandomSource(parseSeed(args[0]))
                    : new SeededRandomSource();
        } catch (IllegalArgumentException invalidSeed) {
            System.err.println(invalidSeed.getMessage());
            System.err.println("Usage: java -cp out Main [seed]");
            return;
        }
        new LudoTSimulation(randomSource, System.out).run();
    }

    /**
     * Reads the optional seed argument.
     *
     * @throws IllegalArgumentException with a readable message when it is not a whole number.
     */
    static long parseSeed(String argument) {
        try {
            return Long.parseLong(argument.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "The seed must be a whole number, but was: \"" + argument + "\"", notANumber);
        }
    }
}
