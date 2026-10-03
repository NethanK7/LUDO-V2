import java.util.OptionalLong;
import ludot.LudoSimulationFacade;
import ludot.random.RandomnessProvider;
import ludot.random.SeededRandomnessProvider;

/** Starts the simulation. An optional whole-number argument is used as the random seed. */
public final class LudoApplication {

    private LudoApplication() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            play(new SeededRandomnessProvider());
            return;
        }
        OptionalLong seed = readSeed(args[0]);
        if (seed.isEmpty()) {
            System.err.println("The seed must be a whole number, but was: \"" + args[0] + "\"");
            System.err.println("Usage: java -cp out LudoApplication [seed]");
            return;
        }
        play(new SeededRandomnessProvider(seed.getAsLong()));
    }

    private static OptionalLong readSeed(String text) {
        try {
            return OptionalLong.of(Long.parseLong(text.trim()));
        } catch (NumberFormatException notANumber) {
            return OptionalLong.empty();
        }
    }

    private static void play(RandomnessProvider randomness) {
        new LudoSimulationFacade(randomness, System.out).run();
    }
}
