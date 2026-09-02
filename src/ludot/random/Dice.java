package ludot.random;

/** The six-sided dice of Ludo. */
public final class Dice {

    /** "Value = 1, 2, 3, 4, 5, and 6" (Legend). */
    public static final int FACES = 6;

    /** The face that lets a piece leave the base and grants an extra roll (Rules 2 and 4). */
    public static final int SIX = 6;

    private final RandomSource randomSource;

    public Dice(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    /** Rolls the dice, returning a face value from 1 to 6. */
    public int roll() {
        return randomSource.nextInt(FACES) + 1;
    }
}
