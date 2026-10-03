package ludot.board;

/** Clockwise or counter-clockwise travel around the 52 standard cells. */
public enum TravelDirection {

    CLOCKWISE("clockwise", +1, 1),
    COUNTER_CLOCKWISE("counter-clockwise", -1, 2);

    private final String displayName;
    private final int ringStep;
    private final int requiredApproachPasses;

    TravelDirection(String displayName, int ringStep, int requiredApproachPasses) {
        this.displayName = displayName;
        this.ringStep = ringStep;
        this.requiredApproachPasses = requiredApproachPasses;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getNextRingCell(int cell) {
        return BoardSpecification.wrapRing(cell + ringStep);
    }

    public int getRequiredApproachPasses() {
        return requiredApproachPasses;
    }
}
