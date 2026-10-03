package ludot.random;

/** A six-sided dice. */
public final class SixSidedDie {

    public static final int FACES = 6;

    public static final int SIX = 6;

    private final RandomnessProvider randomSource;

    public SixSidedDie(RandomnessProvider randomSource) {
        this.randomSource = randomSource;
    }

    public int roll() {
        return randomSource.generateInt(FACES) + 1;
    }
}
