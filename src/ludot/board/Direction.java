package ludot.board;

/**
 * Travel direction of a piece around the 52-cell standard path.
 *
 * <p>Rule&nbsp;T-1 replaces the traditional "always clockwise" rule with a coin toss that happens
 * once, right after the piece steps from its base onto "X". Heads means clockwise, tails means
 * counter-clockwise.
 *
 * <p>The same rule also decides when a piece is allowed to turn into its home straight: a clockwise
 * piece may do so the <em>first</em> time it reaches its approach cell, while a counter-clockwise
 * piece "can only move into the home straight if it passes the approach cell for the second time".
 * That single difference is captured by {@link #requiredApproachPasses()}, which keeps the rule in
 * one place instead of scattering {@code if (direction == ...)} checks through the movement code.
 */
public enum Direction {

    CLOCKWISE("clockwise", +1, 1),
    COUNTER_CLOCKWISE("counter-clockwise", -1, 2);

    private final String displayName;
    private final int ringStep;
    private final int requiredApproachPasses;

    Direction(String displayName, int ringStep, int requiredApproachPasses) {
        this.displayName = displayName;
        this.ringStep = ringStep;
        this.requiredApproachPasses = requiredApproachPasses;
    }

    /** Wording used by the status messages: {@code "clockwise"} / {@code "counter-clockwise"}. */
    public String displayName() {
        return displayName;
    }

    /** The standard-path cell reached by taking one single step from {@code cell}. */
    public int nextRingCell(int cell) {
        return BoardGeometry.wrapRing(cell + ringStep);
    }

    /** How many times a piece must reach its approach cell before it may enter the home straight. */
    public int requiredApproachPasses() {
        return requiredApproachPasses;
    }
}
