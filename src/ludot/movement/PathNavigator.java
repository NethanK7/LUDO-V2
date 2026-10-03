package ludot.movement;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import ludot.board.BoardSpecification;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;

/** Walks the board one cell at a time and applies the block and home-straight rules. */
public final class PathNavigator {

    public static final int UNREACHABLE = Integer.MAX_VALUE;

    public enum Outcome {
        COMPLETED,
        BLOCKED,
        IMPOSSIBLE
    }

    public static final class Walk {

        private final Outcome outcome;
        private final BoardSquare destination;
        private final int stepsTaken;
        private final int approachArrivals;
        private final GamePiece blockingPiece;

        private Walk(Outcome outcome, BoardSquare destination, int stepsTaken, int approachArrivals,
                GamePiece blockingPiece) {
            this.outcome = outcome;
            this.destination = destination;
            this.stepsTaken = stepsTaken;
            this.approachArrivals = approachArrivals;
            this.blockingPiece = blockingPiece;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public Optional<BoardSquare> getDestination() {
            return Optional.ofNullable(destination);
        }

        public int getStepsTaken() {
            return stepsTaken;
        }

        public int getApproachArrivals() {
            return approachArrivals;
        }

        public Optional<GamePiece> getBlockingPiece() {
            return Optional.ofNullable(blockingPiece);
        }

        public boolean isCompleted() {
            return outcome == Outcome.COMPLETED;
        }

        private static Walk createImpossible() {
            return new Walk(Outcome.IMPOSSIBLE, null, 0, 0, null);
        }
    }

    private final GameBoard board;

    public PathNavigator(GameBoard board) {
        this.board = board;
    }

    public Walk walk(GamePiece piece, TravelDirection direction, int steps) {
        return walk(List.of(piece), piece, direction, steps);
    }

    public Walk walk(List<GamePiece> group, GamePiece leader, TravelDirection direction, int steps) {
        PlayerColour colour = leader.getColour();
        boolean entryEarned = group.stream().allMatch(GamePiece::hasEarnedHomeStraightEntry);
        int approachPasses = group.stream().mapToInt(GamePiece::getApproachPasses).min().orElse(0);
        BoardSquare current = leader.getSquare();
        int approachArrivals = 0;
        BoardSquare furthestReached = null;
        int stepsToFurthestReached = 0;

        for (int step = 1; step <= steps; step++) {
            Optional<BoardSquare> nextStep = findNextSquare(current, colour, direction,
                    approachPasses + approachArrivals, entryEarned);
            if (nextStep.isEmpty()) {
                return Walk.createImpossible();
            }
            BoardSquare next = nextStep.get();

            boolean isFinalStep = step == steps;
            Optional<GamePiece> blocker = findBlockerAt(next, colour, group.size(), isFinalStep);
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

    public BoardSquare findDestinationIgnoringBlocks(GamePiece piece, TravelDirection direction, int steps) {
        BoardSquare current = piece.getSquare();
        int approachArrivals = 0;
        for (int step = 1; step <= steps; step++) {
            Optional<BoardSquare> nextStep = findNextSquare(current, piece.getColour(), direction,
                    piece.getApproachPasses() + approachArrivals, piece.hasEarnedHomeStraightEntry());
            if (nextStep.isEmpty()) {
                return current;
            }
            BoardSquare next = nextStep.get();
            if (next.isApproachCellOf(piece.getColour())) {
                approachArrivals++;
            }
            current = next;
        }
        return current;
    }

    public int calculateDistanceToHome(GamePiece piece) {
        if (!piece.isInPlay()) {
            return UNREACHABLE;
        }
        BoardSquare current = piece.getSquare();
        int approachArrivals = 0;
        int steps = 0;
        while (!current.isHome()) {
            current = findNextSquare(current, piece.getColour(), piece.getDirection(),
                    piece.getApproachPasses() + approachArrivals, true).orElseThrow();
            steps++;
            if (current.isApproachCellOf(piece.getColour())) {
                approachArrivals++;
            }
        }
        return steps;
    }

    public List<GamePiece> findCapturesOnLanding(BoardSquare square, PlayerColour mover) {
        if (!square.isRing()) {
            return List.of();
        }
        return board.getGroupsOn(square).entrySet().stream()
                .filter(group -> group.getKey() != mover)
                .flatMap(group -> group.getValue().stream())
                .toList();
    }

    // Rules 9, T-1 and T-7: enter the home straight only after a capture and enough visits to the approach cell.
    private Optional<BoardSquare> findNextSquare(BoardSquare current, PlayerColour colour, TravelDirection direction,
            int approachPasses, boolean entryEarned) {
        if (current.isHomeStraight()) {
            int nextCell = current.index() + 1;
            return Optional.of(nextCell < BoardSpecification.HOME_STRAIGHT_LENGTH
                    ? BoardSquare.ofHomeStraight(colour, nextCell)
                    : BoardSquare.ofHome(colour));
        }
        if (!current.isRing()) {
            return Optional.empty();
        }
        if (current.isApproachCellOf(colour)
                && entryEarned && approachPasses >= direction.getRequiredApproachPasses()) {
            return Optional.of(BoardSquare.ofHomeStraight(colour, 0));
        }
        return Optional.of(BoardSquare.ofRing(direction.getNextRingCell(current.index())));
    }

    // T-3 and T-8: nobody passes a block, but an equal blockade may land on it.
    private Optional<GamePiece> findBlockerAt(BoardSquare square, PlayerColour mover, int groupSize,
            boolean isFinalStep) {
        if (!square.isRing()) {
            return Optional.empty();
        }
        for (Map.Entry<PlayerColour, List<GamePiece>> group : board.getGroupsOn(square).entrySet()) {
            if (group.getKey() == mover) {
                continue;
            }
            int opponentGroupSize = group.getValue().size();
            if (opponentGroupSize < GameBoard.MINIMUM_BLOCK_SIZE) {
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
