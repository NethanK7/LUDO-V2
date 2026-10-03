package ludot.command;

import ludot.board.GamePiece;
import ludot.movement.CandidateMove;

/** Rules 2 and T-1: a six brings a piece onto X, then a coin toss sets its direction. */
public final class ReleaseFromBaseCommand extends AbstractMoveCommand {

    ReleaseFromBaseCommand(CandidateMove move, CommandContext context) {
        super(move, context);
    }

    @Override
    protected void arrive() {
        CommandContext context = getContext();
        GamePiece piece = getMove().getPrimaryPiece();
        context.board().relocate(piece, getMove().getDestination());
        context.log().reportPieceReleased(piece);
        context.log().reportPieceCounts(context.board().createSnapshot(piece.getColour()));
        piece.assignStartingDirection(context.coin().toss().getAwardedDirection());
    }
}
