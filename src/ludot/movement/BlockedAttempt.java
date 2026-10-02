package ludot.movement;

import java.util.Optional;
import ludot.board.Piece;
import ludot.board.Square;

/**
 * A move that Rule T-3 refused: an opponent block stands on or before the destination.
 *
 * <p>The specification requires the simulation to report exactly this situation, and to react in one
 * of two ways when the player has nothing else to move: either shuffle the piece forward to "the
 * cell before the block", or ignore the throw altogether. Both possibilities are described here, so
 * the turn engine only has to ask {@link #partialMove()}.
 *
 * @param intendedDestination where the piece would have landed had the block not been there (the
 *                            "L2" of the message).
 * @param blockingPiece       one of the pieces forming the offending block; named in the message.
 * @param partialMove         the shortened move up to the cell before the block, or empty when the
 *                            block leaves no room to advance at all.
 */
public record BlockedAttempt(Piece piece, Square from, Square intendedDestination,
        Piece blockingPiece, Optional<PlannedMove> partialMove) {
}
