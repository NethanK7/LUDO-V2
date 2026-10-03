package ludot.board;

/** Fixed board sizes and the Alpha, Beta and Gamma cells (7, 25 and 44). */
public final class BoardSpecification {

    public static final int RING_SIZE = 52;

    public static final int HOME_STRAIGHT_LENGTH = 5;

    public static final int PIECES_PER_PLAYER = 4;

    private static final int ALPHA_OFFSET_FROM_YELLOW_APPROACH = 9;
    private static final int BETA_OFFSET_FROM_YELLOW_APPROACH = 27;
    private static final int GAMMA_OFFSET_FROM_YELLOW_APPROACH = 46;

    public static final int ALPHA_CELL = calculateCellFromYellowApproach(ALPHA_OFFSET_FROM_YELLOW_APPROACH);

    public static final int BETA_CELL = calculateCellFromYellowApproach(BETA_OFFSET_FROM_YELLOW_APPROACH);

    public static final int GAMMA_CELL = calculateCellFromYellowApproach(GAMMA_OFFSET_FROM_YELLOW_APPROACH);

    private BoardSpecification() {
    }

    public static int wrapRing(int cell) {
        return Math.floorMod(cell, RING_SIZE);
    }

    private static int calculateCellFromYellowApproach(int offset) {
        return wrapRing(PlayerColour.YELLOW.getApproachCell() + offset);
    }
}
