package ludot.movement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.random.SixSidedDie;

/** Lists every legal move for a roll: enter the board, move a piece, or move a block. */
public final class MoveOptionFinder {

    private final GameBoard board;
    private final PathNavigator pathCalculator;

    public MoveOptionFinder(GameBoard board, PathNavigator pathCalculator) {
        this.board = board;
        this.pathCalculator = pathCalculator;
    }

    public AvailableMoves findOptions(PlayerColour colour, int rollValue) {
        List<CandidateMove> playable = new ArrayList<>();
        List<BlockedMoveAttempt> blocked = new ArrayList<>();

        addEnterBoardMove(colour, rollValue, playable, blocked);
        addSinglePieceMoves(colour, rollValue, playable, blocked);
        addBlockMoves(colour, rollValue, playable);

        return new AvailableMoves(playable, blocked);
    }

    // T-5: a piece leaving a block moves in the direction it got at X.
    private TravelDirection getTravelDirection(GamePiece piece) {
        return board.isPartOfBlock(piece) ? piece.getInitialDirection() : piece.getDirection();
    }

    public Optional<CandidateMove> planForcedMove(GamePiece piece, TravelDirection direction, int steps) {
        PathNavigator.Walk walk = pathCalculator.walk(piece, direction, steps);
        if (!walk.isCompleted()) {
            return Optional.empty();
        }
        return Optional.of(createSinglePieceMove(MoveCategory.ADVANCE, piece, direction, walk));
    }

    private void addEnterBoardMove(PlayerColour colour, int rollValue, List<CandidateMove> playable,
            List<BlockedMoveAttempt> blocked) {
        if (rollValue != SixSidedDie.SIX) {
            return;
        }
        List<GamePiece> waitingInBase = board.getPiecesInBase(colour);
        if (waitingInBase.isEmpty()) {
            return;
        }
        GamePiece piece = waitingInBase.get(0);
        BoardSquare startSquare = BoardSquare.ofRing(colour.getStartCell());
        Optional<GamePiece> blocker = findOpponentBlocker(startSquare, colour);
        if (blocker.isPresent()) {
            blocked.add(new BlockedMoveAttempt(piece, piece.getSquare(), startSquare, blocker.get(),
                    Optional.empty()));
            return;
        }

        PieceTransition movement = new PieceTransition(piece, piece.getSquare(), startSquare, null, 0, 0);
        List<GamePiece> captured = pathCalculator.findCapturesOnLanding(startSquare, colour);
        playable.add(new CandidateMove(MoveCategory.ENTER_BOARD, List.of(movement), captured));
    }

    private void addSinglePieceMoves(PlayerColour colour, int rollValue, List<CandidateMove> playable,
            List<BlockedMoveAttempt> blocked) {
        for (GamePiece piece : board.getPiecesInPlay(colour)) {
            if (piece.getEffects().isAttendingBriefing()) {
                continue;
            }
            int steps = piece.getEffects().adjustRoll(rollValue);
            if (steps <= 0) {
                continue;
            }

            TravelDirection direction = getTravelDirection(piece);
            PathNavigator.Walk walk = pathCalculator.walk(piece, direction, steps);
            if (walk.isCompleted()) {
                playable.add(createSinglePieceMove(MoveCategory.ADVANCE, piece, direction, walk));
            } else if (walk.getOutcome() == PathNavigator.Outcome.BLOCKED) {
                blocked.add(createBlockedMove(piece, direction, steps, walk));
            }
        }
    }

    private void addBlockMoves(PlayerColour colour, int rollValue, List<CandidateMove> playable) {
        for (BoardSquare blockSquare : board.findBlockSquares(colour)) {
            List<GamePiece> block = board.getGroupOn(blockSquare, colour);
            if (containsRestrictedPiece(block)) {
                continue;
            }
            int steps = rollValue / block.size();
            if (steps <= 0) {
                continue;
            }

            GamePiece leader = findDirectionLeader(block);
            TravelDirection direction = leader.getDirection();
            PathNavigator.Walk walk = pathCalculator.walk(block, leader, direction, steps);
            if (!walk.isCompleted()) {
                continue;
            }

            BoardSquare destination = walk.getDestination().orElseThrow();
            List<PieceTransition> movements = block.stream()
                    .map(piece -> new PieceTransition(piece, blockSquare, destination, direction,
                            steps, piece.getApproachPasses() + walk.getApproachArrivals()))
                    .toList();
            List<GamePiece> captured = pathCalculator.findCapturesOnLanding(destination, colour);
            playable.add(new CandidateMove(MoveCategory.BLOCK_ADVANCE, movements, captured));
        }
    }

    // T-4: a mixed block follows the piece with the longest way home.
    private GamePiece findDirectionLeader(List<GamePiece> block) {
        GamePiece firstPiece = block.get(0);
        boolean directionsAgree = block.stream()
                .allMatch(piece -> piece.getDirection() == firstPiece.getDirection());
        if (directionsAgree) {
            return firstPiece;
        }
        return block.stream()
                .max(Comparator.comparingInt(pathCalculator::calculateDistanceToHome))
                .orElseThrow();
    }

    private Optional<GamePiece> findOpponentBlocker(BoardSquare square, PlayerColour mover) {
        return board.getGroupsOn(square).entrySet().stream()
                .filter(group -> group.getKey() != mover)
                .filter(group -> group.getValue().size() >= GameBoard.MINIMUM_BLOCK_SIZE)
                .map(group -> group.getValue().get(0))
                .findFirst();
    }

    private boolean containsRestrictedPiece(List<GamePiece> block) {
        return block.stream().anyMatch(piece -> piece.getEffects().isAttendingBriefing());
    }

    private CandidateMove createSinglePieceMove(MoveCategory type, GamePiece piece, TravelDirection direction,
            PathNavigator.Walk walk) {
        BoardSquare destination = walk.getDestination().orElseThrow();
        PieceTransition movement = new PieceTransition(piece, piece.getSquare(), destination, direction,
                walk.getStepsTaken(), piece.getApproachPasses() + walk.getApproachArrivals());
        List<GamePiece> captured = pathCalculator.findCapturesOnLanding(destination, piece.getColour());
        return new CandidateMove(type, List.of(movement), captured);
    }

    private BlockedMoveAttempt createBlockedMove(GamePiece piece, TravelDirection direction, int steps,
            PathNavigator.Walk walk) {
        BoardSquare intendedDestination = pathCalculator.findDestinationIgnoringBlocks(piece, direction, steps);
        Optional<CandidateMove> partialMove = walk.getDestination()
                .map(reached -> createSinglePieceMove(MoveCategory.PARTIAL_ADVANCE, piece, direction, walk));
        return new BlockedMoveAttempt(piece, piece.getSquare(), intendedDestination,
                walk.getBlockingPiece().orElseThrow(), partialMove);
    }
}
