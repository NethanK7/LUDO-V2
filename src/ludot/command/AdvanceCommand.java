package ludot.command;

import ludot.board.Board;
import ludot.board.Piece;
import ludot.movement.PieceMovement;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.ui.GameListener;

/**
 * Rules 1, T-4 and T-5: one piece, or a whole block, walks along the board.
 *
 * <p>It also plays the shortened "up to the cell before the block" move of Rule T-3 and the forced
 * moves of Rule T-6, because those are ordinary walks too.
 */
public final class AdvanceCommand extends MoveCommand {

    AdvanceCommand(PlannedMove move, Board board, MysteryCell mysteryCell,
            MysteryEffectResolver mysteryEffects, GameListener log) {
        super(move, board, mysteryCell, mysteryEffects, log);
    }

    @Override
    protected void arrive() {
        for (PieceMovement movement : move().movements()) {
            Piece piece = movement.piece();
            if (!move().isBlockMove()) {
                // Rule T-5: a piece moving on its own uses the direction it was given at "X". A block
                // move leaves each piece's own direction alone for Rule T-4 to compare next time.
                piece.setDirection(movement.direction());
            }
            piece.setApproachPasses(movement.approachPassesAtDestination());
            board().relocate(piece, movement.to());
            log().movesPiece(movement);
        }
    }
}
