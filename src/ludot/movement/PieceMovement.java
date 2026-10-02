package ludot.movement;

import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.Square;

/**
 * Where one single piece would end up if a {@link PlannedMove} were carried out.
 *
 * <p>A normal move contains exactly one of these; a block move (Rule T-4) contains one per piece in
 * the block. Modelling it this way means the executor and the log never need to care which kind of
 * move they are dealing with - they just apply every movement in the list.
 *
 * @param direction the direction travelled; {@code null} only for a piece stepping out of its base,
 *                  whose direction is decided by the coin toss after it arrives (Rule T-1).
 * @param approachPassesAtDestination Rule T-1 bookkeeping: the piece's approach-cell counter once
 *                                    it arrives.
 */
public record PieceMovement(Piece piece, Square from, Square to, Direction direction,
        int stepsTaken, int approachPassesAtDestination) {
}
