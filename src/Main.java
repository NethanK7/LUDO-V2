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
        LudoTSimulation simulation = args.length > 0
                ? new LudoTSimulation(new SeededRandomSource(Long.parseLong(args[0])), System.out)
                : new LudoTSimulation(new SeededRandomSource(), System.out);
        simulation.run();
    }
}
