package ludot.movement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ludot.board.Board;
import ludot.board.BoardGeometry;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.piece.Piece;

/**
 * Walks the board one cell at a time and reports what happens.
 *
 * <p>This is the single place where the geometry rules of LUDO-T live:
 *
 * <ul>
 *   <li><b>Rule 1 / Rule 8 / Rule T-1</b> - a step moves to the next or the previous standard cell,
 *       depending on the direction the piece was given by its coin toss.</li>
 *   <li><b>Rule 9 / Rule T-1 / Rule T-7</b> - standing on its own approach cell, a piece turns into
 *       its home straight, but only after it has visited that cell often enough for its direction
 *       (once clockwise, twice counter-clockwise) and only once it has captured an opponent.</li>
 *   <li><b>Rule 10</b> - the home straight must be finished with an exact roll; a longer roll is not
 *       a legal move for that piece at all.</li>
 *   <li><b>Rule 5 / Rule T-3</b> - a lone piece can be jumped over, a block cannot.</li>
 *   <li><b>Rule 6 / Rule T-8</b> - landing is allowed on a lone opponent piece (a capture) and on an
 *       opponent block only when the arriving group is a blockade of exactly the same size.</li>
 * </ul>
 *
 * <p>Walking is done step by step instead of with modular arithmetic. The ring is only 52 cells and
 * a single roll never exceeds 12 cells (a six doubled by the Alpha aura), so the cost is negligible
 * while the code stays a direct, checkable transcription of the rule book.
 */
public final class PathResolver {

    /** Distance value meaning "this piece cannot reach home from where it currently stands". */
    public static final int UNREACHABLE = Integer.MAX_VALUE;

    /**
     * The longest journey any piece can face: two laps of the ring - a counter-clockwise piece must
     * see its approach cell twice (Rule T-1) - plus the home straight, with a cell to spare.
     */
    private static final int MAXIMUM_JOURNEY_LENGTH =
            2 * BoardGeometry.RING_SIZE + BoardGeometry.STEPS_FROM_APPROACH_TO_HOME + 1;

    /** How a walk ended. */
    public enum Outcome {
        /** The piece travelled the full requested distance. */
        COMPLETED,
        /** An opponent block stood in the way (Rule T-3). */
        BLOCKED,
        /** Rule 10: the distance would carry the piece beyond home, so it cannot be played. */
        IMPOSSIBLE
    }

    /** The result of walking a piece a given number of cells. */
    public static final class Walk {

        private final Outcome outcome;
        private final Square destination;
        private final int stepsTaken;
        private final int approachArrivals;
        private final Piece blockingPiece;

        private Walk(Outcome outcome, Square destination, int stepsTaken, int approachArrivals,
                Piece blockingPiece) {
            this.outcome = outcome;
            this.destination = destination;
            this.stepsTaken = stepsTaken;
            this.approachArrivals = approachArrivals;
            this.blockingPiece = blockingPiece;
        }

        public Outcome outcome() {
            return outcome;
        }

        /**
         * Where the piece ends up. For {@link Outcome#BLOCKED} this is the furthest cell it could
         * still reach - "the cell before the block" - and it is {@code null} when the block sits
         * immediately in front of the piece.
         */
        public Square destination() {
            return destination;
        }

        public int stepsTaken() {
            return stepsTaken;
        }

        /** How many times this walk arrived on the piece's own approach cell (Rule T-1). */
        public int approachArrivals() {
            return approachArrivals;
        }

        /** One of the pieces forming the block that stopped the walk. */
        public Piece blockingPiece() {
            return blockingPiece;
        }

        public boolean isCompleted() {
            return outcome == Outcome.COMPLETED;
        }
    }

    private final Board board;

    public PathResolver(Board board) {
        this.board = board;
    }

    /**
     * Walks {@code piece} {@code steps} cells in {@code direction}, honouring every block rule.
     *
     * @param groupSize how many pieces travel together: 1 for a normal move, the size of the block
     *                  for Rule T-4. It decides whether an opponent blockade may be captured (T-8).
     */
    public Walk walk(Piece piece, Direction direction, int steps, int groupSize) {
        if (!piece.isInPlay() || direction == null || steps <= 0) {
            return new Walk(Outcome.IMPOSSIBLE, null, 0, 0, null);
        }

        Square current = piece.square();
        int approachArrivals = 0;
        Square furthestReached = null;
        int stepsToFurthestReached = 0;

        for (int step = 1; step <= steps; step++) {
            Square next = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, piece.hasEarnedHomeStraightEntry());
            if (next == null) {
                return new Walk(Outcome.IMPOSSIBLE, null, 0, 0, null);
            }

            boolean isFinalStep = step == steps;
            Piece blocker = blockerAt(next, piece.colour(), groupSize, isFinalStep);
            if (blocker != null) {
                return new Walk(Outcome.BLOCKED, furthestReached, stepsToFurthestReached,
                        approachArrivals, blocker);
            }

            if (next.isApproachCellOf(piece.colour())) {
                approachArrivals++;
            }
            current = next;
            furthestReached = next;
            stepsToFurthestReached = step;
        }

        return new Walk(Outcome.COMPLETED, current, steps, approachArrivals, null);
    }

    /**
     * The cell a piece would have reached if no block existed. Used only to fill in the "L2" of the
     * required "piece is blocked from moving from L1 to L2" status message.
     */
    public Square destinationIgnoringBlocks(Piece piece, Direction direction, int steps) {
        Square current = piece.square();
        int approachArrivals = 0;
        for (int step = 1; step <= steps; step++) {
            Square next = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, piece.hasEarnedHomeStraightEntry());
            if (next == null) {
                return current;
            }
            if (next.isApproachCellOf(piece.colour())) {
                approachArrivals++;
            }
            current = next;
        }
        return current;
    }

    /**
     * How many cells this piece still has to travel to reach home, ignoring every other piece.
     *
     * <p>Three different rules are phrased in terms of this distance - yellow "moves the piece
     * closest to its home", red captures "the opponent piece closest to its home", and Rule T-4
     * moves a mixed block "in the direction of the longest distance from home" - so it is measured
     * once, here, and reused everywhere.
     *
     * <p>The measurement deliberately assumes the piece is allowed into its home straight. Rule T-7
     * may still be holding it out for want of a capture, but that is a temporary condition, and
     * treating those pieces as infinitely far from home would make the distance useless as a measure
     * of progress.
     *
     * @return the number of cells to home, or {@link #UNREACHABLE} for a piece in its base or home.
     */
    public int distanceToHome(Piece piece) {
        return piece.direction() == null ? UNREACHABLE : distanceToHome(piece, piece.direction());
    }

    /** The same measurement, but assuming the piece travelled in the given direction. */
    public int distanceToHome(Piece piece, Direction direction) {
        if (!piece.isInPlay() || direction == null) {
            return UNREACHABLE;
        }
        Square current = piece.square();
        int approachArrivals = 0;
        for (int steps = 1; steps <= MAXIMUM_JOURNEY_LENGTH; steps++) {
            Square next = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, true);
            if (next == null) {
                return UNREACHABLE;
            }
            if (next.isHome()) {
                return steps;
            }
            if (next.isApproachCellOf(piece.colour())) {
                approachArrivals++;
            }
            current = next;
        }
        return UNREACHABLE;
    }

    /** Opponent pieces that would be captured by landing on {@code square} (Rules 6 and T-8). */
    public List<Piece> capturesOnLanding(Square square, PieceColour mover) {
        List<Piece> captured = new ArrayList<>();
        if (!square.isRing()) {
            return captured;
        }
        for (Map.Entry<PieceColour, List<Piece>> group : board.groupsOn(square).entrySet()) {
            if (group.getKey() != mover) {
                captured.addAll(group.getValue());
            }
        }
        return captured;
    }

    /**
     * One single step.
     *
     * @param entryEarned whether Rule T-7 is satisfied, i.e. the piece has captured at least once.
     * @return the next square, or {@code null} when the step would carry the piece past home, which
     *         Rule 10 turns into "this roll cannot be played by this piece".
     */
    private Square nextSquare(Square current, PieceColour colour, Direction direction,
            int approachPasses, boolean entryEarned) {
        if (current.isHomeStraight()) {
            int nextCell = current.index() + 1;
            return nextCell < BoardGeometry.HOME_STRAIGHT_LENGTH
                    ? Square.homeStraight(colour, nextCell)
                    : Square.home(colour);
        }
        if (!current.isRing()) {
            return null;
        }
        if (current.isApproachCellOf(colour)
                && entryEarned && approachPasses >= direction.requiredApproachPasses()) {
            return Square.homeStraight(colour, 0);
        }
        return Square.ring(direction.nextRingCell(current.index()));
    }

    /**
     * Returns the opponent piece that forbids this square, or {@code null} when the square may be
     * used.
     *
     * <p>Travelling <em>through</em> a square is refused by any opponent block (Rule T-3). Landing
     * <em>on</em> a square is refused by an opponent block as well, unless the arriving group is a
     * blockade of exactly the same size, which Rule T-8 allows to capture it. A lone opponent piece
     * never blocks anything: it is jumped over (Rule 5) or captured (Rule 6).
     */
    private Piece blockerAt(Square square, PieceColour mover, int groupSize, boolean isFinalStep) {
        if (!square.isRing()) {
            return null;
        }
        for (Map.Entry<PieceColour, List<Piece>> group : board.groupsOn(square).entrySet()) {
            if (group.getKey() == mover) {
                continue;
            }
            int opponentGroupSize = group.getValue().size();
            if (opponentGroupSize < Board.MINIMUM_BLOCK_SIZE) {
                continue;
            }
            boolean blockadeCapturesBlockade = isFinalStep && opponentGroupSize == groupSize;
            if (!blockadeCapturesBlockade) {
                return group.getValue().get(0);
            }
        }
        return null;
    }
}
