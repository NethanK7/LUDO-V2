package ludot.movement;

import java.util.List;
import ludot.board.Direction;
import ludot.board.Square;
import ludot.piece.Piece;

/**
 * One complete, legal action a player could take with the roll it has just made.
 *
 * <p>A planned move is pure data: it has been fully checked against the rules but nothing has been
 * changed on the board yet. That separation is what lets each player's strategy compare its options
 * freely - "does this one capture?", "does this one create a block?", "does this one land on the
 * mystery cell?" - without any risk of half-applying a move it then rejects.
 *
 * <p>{@link MoveGenerator} produces these, the players choose one, and {@link MoveExecutor} is the
 * only class that turns the chosen one into real changes on the board.
 */
public final class PlannedMove {

    private final MoveKind kind;
    private final List<PieceMovement> movements;
    private final List<Piece> capturedPieces;
    private final Piece blockingPiece;

    public PlannedMove(MoveKind kind, List<PieceMovement> movements, List<Piece> capturedPieces,
            Piece blockingPiece) {
        this.kind = kind;
        this.movements = List.copyOf(movements);
        this.capturedPieces = List.copyOf(capturedPieces);
        this.blockingPiece = blockingPiece;
    }

    public MoveKind kind() {
        return kind;
    }

    /** Every piece this move relocates: one piece normally, the whole block for Rule T-4. */
    public List<PieceMovement> movements() {
        return movements;
    }

    /** The piece the message log talks about; for a block move, the first piece of the block. */
    public Piece primaryPiece() {
        return movements.get(0).piece();
    }

    public Square from() {
        return movements.get(0).from();
    }

    public Square destination() {
        return movements.get(0).to();
    }

    public Direction direction() {
        return movements.get(0).direction();
    }

    /** Cells actually travelled, which is fewer than the roll for a partial or block move. */
    public int stepsTaken() {
        return movements.get(0).stepsTaken();
    }

    /** Opponent pieces sent back to their base by this move (Rules 6 and T-8). */
    public List<Piece> capturedPieces() {
        return capturedPieces;
    }

    public boolean capturesAnything() {
        return !capturedPieces.isEmpty();
    }

    /** The opponent block that cut a {@link MoveKind#PARTIAL_ADVANCE} short, if any. */
    public Piece blockingPiece() {
        return blockingPiece;
    }

    public boolean isEnteringBoard() {
        return kind == MoveKind.ENTER_BOARD;
    }

    public boolean isBlockMove() {
        return kind == MoveKind.BLOCK_ADVANCE;
    }

    /** How many pieces travel together; the divisor of Rule T-4. */
    public int groupSize() {
        return movements.size();
    }

    /** The pieces moved by this move, in board order. */
    public List<Piece> movedPieces() {
        return movements.stream().map(PieceMovement::piece).toList();
    }

    /** True when the destination is the given standard-path cell (used by the blue strategy). */
    public boolean landsOnRingCell(int cell) {
        Square destination = destination();
        return destination.isRing() && destination.index() == cell;
    }

    @Override
    public String toString() {
        return kind + " " + primaryPiece().name() + " " + from().label() + "->"
                + destination().label();
    }
}
