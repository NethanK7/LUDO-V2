package ludot.player;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import ludot.board.BoardGeometry;
import ludot.board.Direction;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;

/**
 * Blue's strategy: "a random player that prioritises mystery cells" (Section 2.1.4).
 *
 * <p>Blue is the only behaviour with memory. It "always moves in a cyclic manner. That is, if B1 is
 * moved in the current round, B2 is considered in the next and so on". The piece blue considers
 * first is therefore fixed for a whole round - even when a six gives blue several rolls in one turn
 * - and only advances at the end of the round, to the piece after the one blue moved first.
 *
 * <p>The two mystery-cell sentences both begin "If the piece to be moved is movable", so they apply
 * to the piece the cycle has reached:
 *
 * <ul>
 *   <li>a counter-clockwise piece prefers whichever of its moves lands on the mystery cell;</li>
 *   <li>a clockwise piece whose only moves land on the mystery cell is skipped, and the cycle moves
 *       on to the next piece. If every piece is in that position, the dodge is given up rather than
 *       wasting the roll.</li>
 * </ul>
 *
 * <p>When the scheduled piece cannot move at all, the next piece in the cycle is used.
 */
public final class CyclicMysteryStrategy implements PlayerStrategy {

    private static final int NO_PIECE = 0;

    private final MysteryCell mysteryCell;

    /** Number (1..4) of the piece blue considers first this round. */
    private int scheduledPieceNumber = 1;

    /** Number of the first piece blue moved this round, or {@link #NO_PIECE}. */
    private int firstPieceMovedThisRound = NO_PIECE;

    public CyclicMysteryStrategy(MysteryCell mysteryCell) {
        this.mysteryCell = mysteryCell;
    }

    @Override
    public Optional<PlannedMove> chooseMove(List<PlannedMove> options) {
        for (int offset = 0; offset < BoardGeometry.PIECES_PER_PLAYER; offset++) {
            List<PlannedMove> movesOfPiece = movesOfPiece(options, pieceNumberAt(offset));
            Optional<PlannedMove> choice = preferredMoveOf(movesOfPiece);
            if (choice.isPresent()) {
                return choice;
            }
        }
        // Every movable piece is a clockwise piece that could only land on the mystery cell.
        return firstMoveInCycle(options);
    }

    @Override
    public void onMoveExecuted(PlannedMove move) {
        if (firstPieceMovedThisRound == NO_PIECE) {
            firstPieceMovedThisRound = move.primaryPiece().number();
        }
    }

    /** "if B1 is moved in the current round, B2 is considered in the next and so on." */
    @Override
    public void onRoundCompleted() {
        if (firstPieceMovedThisRound != NO_PIECE) {
            scheduledPieceNumber = firstPieceMovedThisRound % BoardGeometry.PIECES_PER_PLAYER + 1;
            firstPieceMovedThisRound = NO_PIECE;
        }
    }

    /** The piece blue will consider first in the current round (exposed for the tests). */
    int scheduledPieceNumber() {
        return scheduledPieceNumber;
    }

    /**
     * The move blue wants for one piece, or empty when that piece should be skipped: it has no move,
     * or it is moving clockwise and every one of its moves lands on the mystery cell.
     */
    private Optional<PlannedMove> preferredMoveOf(List<PlannedMove> movesOfPiece) {
        if (movesOfPiece.isEmpty()) {
            return Optional.empty();
        }
        Direction direction = movesOfPiece.get(0).primaryPiece().direction();
        if (direction == Direction.COUNTER_CLOCKWISE) {
            return Optional.of(movesOfPiece.stream()
                    .filter(this::landsOnMysteryCell)
                    .findFirst()
                    .orElse(movesOfPiece.get(0)));
        }
        if (direction == Direction.CLOCKWISE) {
            return movesOfPiece.stream().filter(move -> !landsOnMysteryCell(move)).findFirst();
        }
        // A piece still in its base has no direction until its coin is tossed on "X".
        return Optional.of(movesOfPiece.get(0));
    }

    private Optional<PlannedMove> firstMoveInCycle(List<PlannedMove> options) {
        return IntStream.range(0, BoardGeometry.PIECES_PER_PLAYER)
                .mapToObj(offset -> movesOfPiece(options, pieceNumberAt(offset)))
                .flatMap(List::stream)
                .findFirst();
    }

    private int pieceNumberAt(int offsetInCycle) {
        return (scheduledPieceNumber - 1 + offsetInCycle) % BoardGeometry.PIECES_PER_PLAYER + 1;
    }

    private List<PlannedMove> movesOfPiece(List<PlannedMove> options, int pieceNumber) {
        return options.stream()
                .filter(move -> move.movedPieces().stream()
                        .anyMatch(piece -> piece.number() == pieceNumber))
                .toList();
    }

    private boolean landsOnMysteryCell(PlannedMove move) {
        return mysteryCell.isOn(move.destination());
    }
}
