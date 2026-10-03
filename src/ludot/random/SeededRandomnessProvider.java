package ludot.random;

import java.util.Random;

/** The real random source, backed by java.util.Random. */
public final class SeededRandomnessProvider implements RandomnessProvider {

    private final Random random;

    public SeededRandomnessProvider() {
        this.random = new Random();
    }

    public SeededRandomnessProvider(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int generateInt(int boundExclusive) {
        return random.nextInt(boundExclusive);
    }

    @Override
    public boolean generateBoolean() {
        return random.nextBoolean();
    }
}
