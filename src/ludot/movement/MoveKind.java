package ludot.movement;

/** The kinds of move a player can make in one roll. */
public enum MoveKind {

    /** Rule 2: a six moves one piece from the base onto its "X" square. */
    ENTER_BOARD,

    /** Rule 1: one piece walks the number of cells shown on the dice. */
    ADVANCE,

    /** Rule T-4: a whole block moves together, each piece by {@code roll / blockSize} cells. */
    BLOCK_ADVANCE,

    /**
     * Rule T-3 / Section 3: the piece could not travel the full distance because of an opponent
     * block, so it stopped on "the cell before the block". Only offered when the player has no
     * other piece able to move.
     */
    PARTIAL_ADVANCE
}
