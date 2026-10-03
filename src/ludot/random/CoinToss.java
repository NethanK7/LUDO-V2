package ludot.random;

import ludot.board.TravelDirection;

/** Rule T-1: heads means clockwise, tails means counter-clockwise. */
public final class CoinToss {

    public enum Face {
        HEADS(TravelDirection.CLOCKWISE),
        TAILS(TravelDirection.COUNTER_CLOCKWISE);

        private final TravelDirection awardedDirection;

        Face(TravelDirection awardedDirection) {
            this.awardedDirection = awardedDirection;
        }

        public TravelDirection getAwardedDirection() {
            return awardedDirection;
        }
    }

    private final RandomnessProvider randomSource;

    public CoinToss(RandomnessProvider randomSource) {
        this.randomSource = randomSource;
    }

    public Face toss() {
        return randomSource.generateBoolean() ? Face.HEADS : Face.TAILS;
    }
}
