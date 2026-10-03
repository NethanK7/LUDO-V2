package ludot.command;

import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.movement.CandidateMove;
import ludot.movement.PieceTransition;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.ui.GameEventReporter;

/** Moves one piece, or a whole block, along the board. */
public final class AdvancePieceCommand extends AbstractMoveCommand {

    AdvancePieceCommand(CandidateMove move, GameBoard board, MysteryCellScheduler mysteryCell,
            TeleportService teleporter, GameEventReporter log) {
        super(move, board, mysteryCell, teleporter, log);
    }

    @Override
    protected void arrive() {
        for (PieceTransition movement : getMove().movements()) {
            GamePiece piece = movement.piece();
            if (!getMove().isBlockMove()) {
                piece.setDirection(movement.direction());
            }
            piece.setApproachPasses(movement.approachPassesAtDestination());
            getBoard().relocate(piece, movement.to());
            getLog().reportPieceMoved(movement);
        }
    }
}
