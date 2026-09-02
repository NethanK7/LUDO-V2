package ludot.piece;

/**
 * The effect of the Alpha aura on how far a piece travels (Rule T-12).
 *
 * <p>A piece teleported to Alpha either "gets energised by the aura", doubling the value of its
 * rolls for four rounds, or "gets sick due to the aura", halving them for four rounds. Halving uses
 * integer division, which is the only sensible reading for a board game: half of a 3 is 1 cell.
 * A halved roll can therefore become 0, in which case the piece simply cannot move with that roll.
 */
public enum SpeedModifier {

    NORMAL {
        @Override
        public int apply(int rollValue) {
            return rollValue;
        }
    },

    DOUBLED {
        @Override
        public int apply(int rollValue) {
            return rollValue * 2;
        }
    },

    HALVED {
        @Override
        public int apply(int rollValue) {
            return rollValue / 2;
        }
    };

    /** Converts the face value of the dice into the number of cells the piece actually moves. */
    public abstract int apply(int rollValue);
}
