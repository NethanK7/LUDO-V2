package ludot.mystery;

import ludot.board.BoardGeometry;
import ludot.board.PieceColour;
import ludot.board.Square;

/**
 * The six places a mystery cell can throw a piece to (Rule T-11).
 *
 * <p>Three of them - Alpha, Beta and Gamma - are fixed cells shared by everybody, worked out in
 * {@link BoardGeometry} from the yellow approach cell. The other three are relative to the piece
 * that was teleported, so each constant knows how to turn itself into a real square for a colour.
 */
public enum TeleportDestination {

    /** Cell 7. Rule T-12: the aura either energises or sickens the piece. */
    ALPHA("Alpha") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.ring(BoardGeometry.ALPHA_CELL);
        }
    },

    /** Cell 25. Rule T-13: the piece attends a briefing and cannot move for four rounds. */
    BETA("Beta") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.ring(BoardGeometry.BETA_CELL);
        }
    },

    /** Cell 44. Rule T-14: the piece needs clarification and may be turned around. */
    GAMMA("Gamma") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.ring(BoardGeometry.GAMMA_CELL);
        }
    },

    /** Straight back to the player's own base. */
    BASE("Base") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.base(colour);
        }
    },

    /** The "X" starting square of the piece's own colour. */
    START("X") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.ring(colour.startCell());
        }
    },

    /** The approach circle of the piece's own colour - the doorway to its home straight. */
    APPROACH("Approach") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.ring(colour.approachCell());
        }
    };

    private final String displayName;

    TeleportDestination(String displayName) {
        this.displayName = displayName;
    }

    /** The wording used by the status messages, e.g. {@code "teleported to Alpha."}. */
    public String displayName() {
        return displayName;
    }

    /** Where this destination actually is for a piece of the given colour. */
    public abstract Square squareFor(PieceColour colour);
}
