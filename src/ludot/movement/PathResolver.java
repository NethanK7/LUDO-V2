package ludot.movement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.BoardGeometry;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;

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
         * still reach - "the cell before the block" - and it is empty when the block sits
         * immediately in front of the piece. It is also empty for {@link Outcome#IMPOSSIBLE}.
         */
        public Optional<Square> destination() {
            return Optional.ofNullable(destination);
        }

        public int stepsTaken() {
            return stepsTaken;
        }

        /** How many times this walk arrived on the piece's own approach cell (Rule T-1). */
        public int approachArrivals() {
            return approachArrivals;
        }

        /** One of the pieces forming the block that stopped the walk; empty unless BLOCKED. */
        public Optional<Piece> blockingPiece() {
            return Optional.ofNullable(blockingPiece);
        }

        public boolean isCompleted() {
            return outcome == Outcome.COMPLETED;
        }

        private static Walk impossible() {
            return new Walk(Outcome.IMPOSSIBLE, null, 0, 0, null);
        }
    }

    private final Board board;

    public PathResolver(Board board) {
        this.board = board;
    }

    /**
     * Walks a single {@code piece} {@code steps} cells in {@code direction}, honouring every block
     * rule.
     */
    public Walk walk(Piece piece, Direction direction, int steps) {
        return walk(List.of(piece), piece, direction, steps);
    }

    /**
     * Walks a group of pieces standing on the same square {@code steps} cells as one body.
     *
     * <p>A single piece is a group of one. For a block (Rule T-4) the group size decides whether an
     * opponent blockade may be captured (T-8), and the whole block may turn into the home straight
     * only when <em>every</em> piece in it is allowed to (Rule T-7): one piece without a capture
     * keeps the block on the standard path.
     *
     * @param leader the piece whose position and approach-cell history the walk starts from.
     */
    public Walk walk(List<Piece> group, Piece leader, Direction direction, int steps) {
        PieceColour colour = leader.colour();
        boolean entryEarned = group.stream().allMatch(Piece::hasEarnedHomeStraightEntry);
        int approachPasses = group.stream().mapToInt(Piece::approachPasses).min().orElse(0);
        Square current = leader.square();
        int approachArrivals = 0;
        Square furthestReached = null;
        int stepsToFurthestReached = 0;

        for (int step = 1; step <= steps; step++) {
            Optional<Square> nextStep = nextSquare(current, colour, direction,
                    approachPasses + approachArrivals, entryEarned);
            if (nextStep.isEmpty()) {
                return Walk.impossible();
            }
            Square next = nextStep.get();

            boolean isFinalStep = step == steps;
            Optional<Piece> blocker = blockerAt(next, colour, group.size(), isFinalStep);
            if (blocker.isPresent()) {
                return new Walk(Outcome.BLOCKED, furthestReached, stepsToFurthestReached,
                        approachArrivals, blocker.get());
            }

            if (next.isApproachCellOf(colour)) {
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
            Optional<Square> nextStep = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, piece.hasEarnedHomeStraightEntry());
            if (nextStep.isEmpty()) {
                return current;
            }
            Square next = nextStep.get();
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
        if (!piece.isInPlay()) {
            return UNREACHABLE;
        }
        Square current = piece.square();
        int approachArrivals = 0;
        int steps = 0;
        while (!current.isHome()) {
            current = nextSquare(current, piece.colour(), piece.direction(),
                    piece.approachPasses() + approachArrivals, true).orElseThrow();
            steps++;
            if (current.isApproachCellOf(piece.colour())) {
                approachArrivals++;
            }
        }
        return steps;
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
     * @return the next square, or empty when the step would carry the piece past home, which
     *         Rule 10 turns into "this roll cannot be played by this piece".
     */
    private Optional<Square> nextSquare(Square current, PieceColour colour, Direction direction,
            int approachPasses, boolean entryEarned) {
        if (current.isHomeStraight()) {
            int nextCell = current.index() + 1;
            return Optional.of(nextCell < BoardGeometry.HOME_STRAIGHT_LENGTH
                    ? Square.homeStraight(colour, nextCell)
                    : Square.home(colour));
        }
        if (!current.isRing()) {
            return Optional.empty();
        }
        if (current.isApproachCellOf(colour)
                && entryEarned && approachPasses >= direction.requiredApproachPasses()) {
            return Optional.of(Square.homeStraight(colour, 0));
        }
        return Optional.of(Square.ring(direction.nextRingCell(current.index())));
    }

    /**
     * Returns the opponent piece that forbids this square, or empty when the square may be used.
     *
     * <p>Travelling <em>through</em> a square is refused by any opponent block (Rule T-3). Landing
     * <em>on</em> a square is refused by an opponent block as well, unless the arriving group is a
     * blockade of exactly the same size, which Rule T-8 allows to capture it. A lone opponent piece
     * never blocks anything: it is jumped over (Rule 5) or captured (Rule 6).
     */
    private Optional<Piece> blockerAt(Square square, PieceColour mover, int groupSize,
            boolean isFinalStep) {
        if (!square.isRing()) {
            return Optional.empty();
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
                return Optional.of(group.getValue().get(0));
            }
        }
        return Optional.empty();
    }
}
