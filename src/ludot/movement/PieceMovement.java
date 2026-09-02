package ludot.movement;

import ludot.board.Direction;
import ludot.board.Square;
import ludot.piece.Piece;

/**
 * Where one single piece would end up if a {@link PlannedMove} were carried out.
 *
 * <p>A normal move contains exactly one of these; a block move (Rule T-4) contains one per piece in
 * the block. Modelling it this way means the executor and the log never need to care which kind of
 * move they are dealing with - they just apply every movement in the list.
 */
public final class PieceMovement {

    private final Piece piece;
    private final Square from;
    private final Square to;
    private final Direction direction;
    private final int stepsTaken;
    private final int approachPassesAtDestination;

    public PieceMovement(Piece piece, Square from, Square to, Direction direction, int stepsTaken,
            int approachPassesAtDestination) {
        this.piece = piece;
        this.from = from;
        this.to = to;
        this.direction = direction;
        this.stepsTaken = stepsTaken;
        this.approachPassesAtDestination = approachPassesAtDestination;
    }

    public Piece piece() {
        return piece;
    }

    public Square from() {
        return from;
    }

    public Square to() {
        return to;
    }

    public Direction direction() {
        return direction;
    }

    public int stepsTaken() {
        return stepsTaken;
    }

    /** Rule T-1 bookkeeping: the piece's approach-cell counter once it arrives. */
    public int approachPassesAtDestination() {
        return approachPassesAtDestination;
    }
}
