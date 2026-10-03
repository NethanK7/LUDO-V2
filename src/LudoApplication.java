import ludot.LudoSimulationFacade;
import ludot.random.SeededRandomnessProvider;

/** Starts the simulation. An optional whole-number argument is used as the random seed. */
public final class LudoApplication {

    private LudoApplication() {
    }

    public static void main(String[] args) {
        SeededRandomnessProvider randomSource;
        try {
            randomSource = args.length > 0
                    ? new SeededRandomnessProvider(parseSeed(args[0]))
                    : new SeededRandomnessProvider();
        } catch (IllegalArgumentException invalidSeed) {
            System.err.println(invalidSeed.getMessage());
            System.err.println("Usage: java -cp out LudoApplication [seed]");
            return;
        }
        new LudoSimulationFacade(randomSource, System.out).run();
    }

    static long parseSeed(String argument) {
        try {
            return Long.parseLong(argument.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "The seed must be a whole number, but was: \"" + argument + "\"", notANumber);
        }
    }
}
