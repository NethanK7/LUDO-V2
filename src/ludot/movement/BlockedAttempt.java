package ludot.movement;

import ludot.board.Direction;
import ludot.board.Square;
import ludot.piece.Piece;

/**
 * A move that Rule T-3 refused: an opponent block stands on or before the destination.
 *
 * <p>The specification requires the simulation to report exactly this situation, and to react in one
 * of two ways when the player has nothing else to move: either shuffle the piece forward to "the
 * cell before the block", or ignore the throw altogether. Both possibilities are described here, so
 * the turn engine only has to ask {@link #hasPartialMove()}.
 */
public final class BlockedAttempt {

    private final Piece piece;
    private final Square from;
    private final Square intendedDestination;
    private final Piece blockingPiece;
    private final PlannedMove partialMove;

    public BlockedAttempt(Piece piece, Square from, Square intendedDestination, Piece blockingPiece,
            PlannedMove partialMove) {
        this.piece = piece;
        this.from = from;
        this.intendedDestination = intendedDestination;
        this.blockingPiece = blockingPiece;
        this.partialMove = partialMove;
    }

    public Piece piece() {
        return piece;
    }

    public Square from() {
        return from;
    }

    /** Where the piece would have landed had the block not been there (the "L2" of the message). */
    public Square intendedDestination() {
        return intendedDestination;
    }

    /** One of the pieces forming the offending block; named in the status message. */
    public Piece blockingPiece() {
        return blockingPiece;
    }

    public Direction direction() {
        return piece.direction();
    }

    /** True when the piece can at least advance up to the cell before the block. */
    public boolean hasPartialMove() {
        return partialMove != null;
    }

    /** The shortened move, or {@code null} when the block leaves no room to advance at all. */
    public PlannedMove partialMove() {
        return partialMove;
    }
}
