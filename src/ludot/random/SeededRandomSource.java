package ludot.random;

import java.util.Random;

/**
 * The production {@link RandomSource}, backed by {@link java.util.Random}.
 *
 * <p>Constructing it with an explicit seed makes a whole simulation reproducible, which is very
 * useful when a particular sequence of events needs to be inspected twice.
 */
public final class SeededRandomSource implements RandomSource {

    private final Random random;

    /** Unpredictable run, seeded by the JVM. */
    public SeededRandomSource() {
        this.random = new Random();
    }

    /** Reproducible run: the same seed always replays the same game. */
    public SeededRandomSource(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int nextInt(int boundExclusive) {
        return random.nextInt(boundExclusive);
    }

    @Override
    public boolean nextBoolean() {
        return random.nextBoolean();
    }
}
