package ludot.mystery;

import ludot.board.BoardSpecification;
import ludot.board.BoardSquare;
import ludot.board.PlayerColour;

/** The six places the mystery cell can send a piece (Rule T-11). */
public enum TeleportTarget {

    ALPHA("Alpha") {
        @Override
        public BoardSquare resolveSquare(PlayerColour colour) {
            return BoardSquare.ofRing(BoardSpecification.ALPHA_CELL);
        }
    },

    BETA("Beta") {
        @Override
        public BoardSquare resolveSquare(PlayerColour colour) {
            return BoardSquare.ofRing(BoardSpecification.BETA_CELL);
        }
    },

    GAMMA("Gamma") {
        @Override
        public BoardSquare resolveSquare(PlayerColour colour) {
            return BoardSquare.ofRing(BoardSpecification.GAMMA_CELL);
        }
    },

    BASE("Base") {
        @Override
        public BoardSquare resolveSquare(PlayerColour colour) {
            return BoardSquare.ofBase(colour);
        }
    },

    START("X") {
        @Override
        public BoardSquare resolveSquare(PlayerColour colour) {
            return BoardSquare.ofRing(colour.getStartCell());
        }
    },

    APPROACH("Approach") {
        @Override
        public BoardSquare resolveSquare(PlayerColour colour) {
            return BoardSquare.ofRing(colour.getApproachCell());
        }
    };

    private final String displayName;

    TeleportTarget(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public abstract BoardSquare resolveSquare(PlayerColour colour);
}
