package ludot.strategy;

import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.movement.CandidateMove;

/** Answers block questions (Rule T-3) for the red and green rules. */
public final class BlockInspector {

    private final GameBoard board;
    private final PlayerColour colour;

    public BlockInspector(GameBoard board, PlayerColour colour) {
        this.board = board;
        this.colour = colour;
    }

    public boolean formsNewBlock(CandidateMove move) {
        BoardSquare destination = move.getDestination();
        if (move.isBlockMove() || !destination.isRing()) {
            return false;
        }
        return !board.getGroupOn(destination, colour).isEmpty();
    }

    public boolean endsInBlock(CandidateMove move) {
        return move.isBlockMove() || formsNewBlock(move);
    }

    public boolean breaksBlock(CandidateMove move) {
        return !move.isBlockMove() && !move.isEnteringBoard()
                && board.isPartOfBlock(move.getPrimaryPiece());
    }
}
