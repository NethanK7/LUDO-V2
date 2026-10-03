package ludot.player.rule;

import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.PlannedMove;

/**
 * Questions about blocks (Rule T-3) that the red and green rules ask about a move.
 *
 * <p>They need the board, so they live here once instead of being repeated in several rules.
 */
public final class BlockFacts {

    private final Board board;
    private final PieceColour colour;

    public BlockFacts(Board board, PieceColour colour) {
        this.board = board;
        this.colour = colour;
    }

    /**
     * True when a single piece arrives on a cell already holding one of this player's pieces, so a
     * block exists afterwards that did not exist before. Moving an existing block does not count.
     */
    public boolean formsNewBlock(PlannedMove move) {
        Square destination = move.destination();
        if (move.isBlockMove() || !destination.isRing()) {
            return false;
        }
        return board.groupOn(destination, colour).stream()
                .anyMatch(piece -> !move.movedPieces().contains(piece));
    }

    /** True when the moved piece or pieces stand in a block once the move is over. */
    public boolean endsInBlock(PlannedMove move) {
        return move.isBlockMove() || formsNewBlock(move);
    }

    /** True when this move takes a single piece out of an existing block, breaking it up. */
    public boolean breaksBlock(PlannedMove move) {
        return !move.isBlockMove() && !move.isEnteringBoard()
                && board.isPartOfBlock(move.primaryPiece());
    }
}
