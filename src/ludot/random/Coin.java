package ludot.random;

import ludot.board.Direction;

/**
 * The coin of Rule T-1, tossed once for each piece as it steps from its base onto "X".
 *
 * <p>"if <b>heads</b> was received, the piece would move in a clockwise direction as in the
 * traditional game, and if a <b>tail</b> was received, the piece moved in the counterclockwise
 * direction."
 */
public final class Coin {

    /** The two faces of the coin, each mapped to the direction it awards. */
    public enum Face {
        HEADS("heads", Direction.CLOCKWISE),
        TAILS("tails", Direction.COUNTER_CLOCKWISE);

        private final String displayName;
        private final Direction awardedDirection;

        Face(String displayName, Direction awardedDirection) {
            this.displayName = displayName;
            this.awardedDirection = awardedDirection;
        }

        public String displayName() {
            return displayName;
        }

        public Direction awardedDirection() {
            return awardedDirection;
        }
    }

    private final RandomSource randomSource;

    public Coin(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    public Face toss() {
        return randomSource.nextBoolean() ? Face.HEADS : Face.TAILS;
    }
}
