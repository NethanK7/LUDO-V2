package ludot.piece;

import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.board.Square;

/**
 * One of the sixteen pieces in the game, e.g. {@code R1}.
 *
 * <p>A piece owns everything that is true about <em>itself</em> and nothing about the rest of the
 * board. It deliberately does not know how to move: the rules for stepping around the ring live in
 * the {@code ludot.movement} package, so this class stays a small, readable record of state.
 *
 * <p>The four pieces of information that only exist because of the LUDO-T twists are:
 *
 * <ul>
 *   <li>{@link #initialDirection()} - the coin toss taken at "X" (Rule T-1), remembered forever
 *       because Rule&nbsp;T-5 restores it when a piece leaves a block on its own.</li>
 *   <li>{@link #captureCount()} - Rule&nbsp;T-7 only lets a piece enter its home straight after it
 *       has captured at least one opponent piece.</li>
 *   <li>{@link #approachPasses()} - Rule&nbsp;T-1 makes a counter-clockwise piece wait until its
 *       <em>second</em> visit to the approach cell.</li>
 *   <li>{@link #effects()} - the Alpha / Beta timers of Rules T-12 and T-13.</li>
 * </ul>
 */
public final class Piece {

    private final PieceColour colour;
    private final int number;
    private final String name;

    private Square square;
    private Direction direction;
    private Direction initialDirection;
    private int captureCount;
    private int approachPasses;
    private final PieceEffects effects = new PieceEffects();

    public Piece(PieceColour colour, int number) {
        this.colour = colour;
        this.number = number;
        this.name = "" + colour.initial() + number;
        this.square = Square.base(colour);
    }

    public PieceColour colour() {
        return colour;
    }

    public int number() {
        return number;
    }

    /** Display name used by every status message, e.g. {@code "R1"}. */
    public String name() {
        return name;
    }

    public Square square() {
        return square;
    }

    /**
     * Moves the piece's own record of where it stands.
     *
     * <p>Package-visible on purpose is not possible across packages, so this stays public, but it is
     * only ever called by {@code Board}, which keeps its cell index in step with it.
     */
    public void setSquare(Square square) {
        this.square = square;
    }

    public boolean isInBase() {
        return square.isBase();
    }

    public boolean isOnRing() {
        return square.isRing();
    }

    public boolean isInHomeStraight() {
        return square.isHomeStraight();
    }

    public boolean isAtHome() {
        return square.isHome();
    }

    /** True when the piece stands somewhere it can be asked to move from. */
    public boolean isInPlay() {
        return square.isRing() || square.isHomeStraight();
    }

    /** The direction this piece is travelling in right now (Rules T-1 and T-14). */
    public Direction direction() {
        return direction;
    }

    /** The direction decided by the coin toss at "X"; restored by Rule T-5. */
    public Direction initialDirection() {
        return initialDirection;
    }

    /** Called once, when the piece steps out of the base onto "X" and the coin is tossed. */
    public void assignStartingDirection(Direction tossedDirection) {
        this.direction = tossedDirection;
        this.initialDirection = tossedDirection;
    }

    /** Rule T-14: Gamma turns a clockwise piece around. */
    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    /** Rule T-7: how many opponent pieces this piece has captured so far. */
    public int captureCount() {
        return captureCount;
    }

    public void recordCapture() {
        captureCount++;
    }

    /** Rule T-7: only a piece that has captured at least once may turn into its home straight. */
    public boolean hasEarnedHomeStraightEntry() {
        return captureCount > 0;
    }

    /** Rule T-1: how many times this piece has arrived at its own approach cell. */
    public int approachPasses() {
        return approachPasses;
    }

    public void setApproachPasses(int approachPasses) {
        this.approachPasses = approachPasses;
    }

    public void recordApproachPass() {
        approachPasses++;
    }

    public PieceEffects effects() {
        return effects;
    }

    /**
     * Rule T-9: "If any piece is captured and returned to base, all information in that piece will
     * be reset." The caller is responsible for putting the piece back into its base.
     */
    public void resetAfterCapture() {
        direction = null;
        initialDirection = null;
        captureCount = 0;
        approachPasses = 0;
        effects.clear();
    }

    @Override
    public String toString() {
        return name + "@" + square.label();
    }
}
