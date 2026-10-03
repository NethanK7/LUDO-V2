package ludot.command;

import ludot.board.GamePiece;
import ludot.movement.CandidateMove;
import ludot.movement.PieceTransition;

/** Moves one piece, or a whole block, along the board. */
public final class AdvancePieceCommand extends AbstractMoveCommand {

    AdvancePieceCommand(CandidateMove move, CommandContext context) {
        super(move, context);
    }

    @Override
    protected void arrive() {
        CommandContext context = getContext();
        for (PieceTransition movement : getMove().movements()) {
            GamePiece piece = movement.piece();
            if (!getMove().isBlockMove()) {
                piece.setDirection(movement.direction());
            }
            piece.setApproachPasses(movement.approachPassesAtDestination());
            context.board().relocate(piece, movement.to());
            context.log().reportPieceMoved(movement);
        }
    }
}
