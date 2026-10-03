package ludot.board;

/** The four colours, with their start (X) and approach cells on the board. */
public enum PlayerColour {

    YELLOW("yellow", 'Y', 0),
    BLUE("blue", 'B', 13),
    RED("red", 'R', 26),
    GREEN("green", 'G', 39);

    private static final int CELLS_FROM_START_TO_APPROACH = 50;

    private final String displayName;
    private final char initial;
    private final int startCell;

    PlayerColour(String displayName, char initial, int startCell) {
        this.displayName = displayName;
        this.initial = initial;
        this.startCell = startCell;
    }

    public String getDisplayName() {
        return displayName;
    }

    public char getInitial() {
        return initial;
    }

    public int getStartCell() {
        return startCell;
    }

    public int getApproachCell() {
        return BoardSpecification.wrapRing(startCell + CELLS_FROM_START_TO_APPROACH);
    }

    public PlayerColour getNextInTurnOrder() {
        return values()[(ordinal() + 1) % values().length];
    }
}
