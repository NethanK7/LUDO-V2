package ludot.board;

/**
 * The fixed measurements of the LUDO-T board.
 *
 * <p>This class holds no state; it exists so that every "magic number" of the board appears exactly
 * once, next to the sentence of the specification that produced it.
 *
 * <p><b>The standard path.</b> 52 cells numbered 0..51, starting at the yellow "X" and running
 * clockwise (see the Legend of the specification). Colour-specific landmarks are derived in
 * {@link PieceColour}.
 *
 * <p><b>Alpha / Beta / Gamma.</b> Rule&nbsp;T-11 places them at the 9th, 27th and 46th cell counted
 * from the yellow approach cell, with the yellow approach cell itself counting as zero. The yellow
 * approach cell is cell 50, so the three special cells fall on 7, 25 and 44.
 */
public final class BoardGeometry {

    /** Number of cells on the shared standard path (Section 1.1: "52 standard ... cells"). */
    public static final int RING_SIZE = 52;

    /** Cells in one colour's home straight, named {@code [colour]homepath0} .. {@code 4}. */
    public static final int HOME_STRAIGHT_LENGTH = 5;

    /** Steps needed to walk from the approach cell all the way into "Home". */
    public static final int STEPS_FROM_APPROACH_TO_HOME = HOME_STRAIGHT_LENGTH + 1;

    /** Pieces every player owns (Section 1.1: "four pieces named 1 to 4"). */
    public static final int PIECES_PER_PLAYER = 4;

    private static final int ALPHA_OFFSET_FROM_YELLOW_APPROACH = 9;
    private static final int BETA_OFFSET_FROM_YELLOW_APPROACH = 27;
    private static final int GAMMA_OFFSET_FROM_YELLOW_APPROACH = 46;

    /** Cell 7 - the "aura" cell of Rule T-12. */
    public static final int ALPHA_CELL = offsetFromYellowApproach(ALPHA_OFFSET_FROM_YELLOW_APPROACH);

    /** Cell 25 - the "briefing" cell of Rule T-13. */
    public static final int BETA_CELL = offsetFromYellowApproach(BETA_OFFSET_FROM_YELLOW_APPROACH);

    /** Cell 44 - the "clarification" cell of Rule T-14. */
    public static final int GAMMA_CELL = offsetFromYellowApproach(GAMMA_OFFSET_FROM_YELLOW_APPROACH);

    private BoardGeometry() {
        // Utility class: never instantiated.
    }

    /** Maps any integer onto a valid standard-path cell index, wrapping around 0..51. */
    public static int wrapRing(int cell) {
        return ((cell % RING_SIZE) + RING_SIZE) % RING_SIZE;
    }

    private static int offsetFromYellowApproach(int offset) {
        return wrapRing(PieceColour.YELLOW.approachCell() + offset);
    }
}
