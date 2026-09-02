package ludot.board;

/**
 * The four players of LUDO-T.
 *
 * <p>Every colour-specific landmark on the board is derived from one single number: the index of
 * the colour's starting square ("X") on the 52-cell standard path. The Legend of the specification
 * fixes that numbering: <em>"The numbering starts with the Yellow starting square and continues
 * clockwise on the white path. The numbering starts with zero (0) and ends at 51."</em>
 *
 * <p>Reading Figure&nbsp;1 clockwise from the yellow "X" gives the starting squares 13 cells apart:
 *
 * <pre>
 *   YELLOW -> 0      BLUE -> 13      RED -> 26      GREEN -> 39
 * </pre>
 *
 * <p>On the board picture each "Approach" circle sits two cells <em>behind</em> the same colour's
 * "X", which is the same thing as 50 cells <em>in front of</em> it. A clockwise piece therefore
 * walks exactly 50 cells from its start to its own approach cell, then 5 home-straight cells, then
 * home.
 *
 * <p>The declaration order of this enum is also the order of play. The specification states
 * <em>"if R rolled the dice, the next player to roll would be G"</em>, and RED -&gt; GREEN is a step
 * of +13 in the numbering above, so the round order simply follows increasing start cells.
 */
public enum PieceColour {

    YELLOW("yellow", 'Y', 0),
    BLUE("blue", 'B', 13),
    RED("red", 'R', 26),
    GREEN("green", 'G', 39);

    /** Cells walked from the starting square until the piece stands on its own approach cell. */
    private static final int CELLS_FROM_START_TO_APPROACH = 50;

    private final String displayName;
    private final char initial;
    private final int startCell;

    PieceColour(String displayName, char initial, int startCell) {
        this.displayName = displayName;
        this.initial = initial;
        this.startCell = startCell;
    }

    /** Lower-case colour name used by every status message, e.g. {@code "red"}. */
    public String displayName() {
        return displayName;
    }

    /** Single letter used to name pieces, e.g. {@code 'R'} for R1..R4. */
    public char initial() {
        return initial;
    }

    /** The "X" square this colour enters from its base (Rule 2). */
    public int startCell() {
        return startCell;
    }

    /** The "Approach" circle of this colour; the doorway to its home straight (Rule 9). */
    public int approachCell() {
        return BoardGeometry.wrapRing(startCell + CELLS_FROM_START_TO_APPROACH);
    }

    /** The next colour to roll, i.e. the player "to the left". */
    public PieceColour nextInTurnOrder() {
        return values()[(ordinal() + 1) % values().length];
    }
}
