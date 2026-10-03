package ludot.command;

import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.movement.CandidateMove;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.random.CoinToss;
import ludot.ui.GameEventReporter;

/** Rules 2 and T-1: a six brings a piece onto X, then a coin toss sets its direction. */
public final class ReleaseFromBaseCommand extends AbstractMoveCommand {

    private final CoinToss coin;

    ReleaseFromBaseCommand(CandidateMove move, GameBoard board, CoinToss coin, MysteryCellScheduler mysteryCell,
            TeleportService teleporter, GameEventReporter log) {
        super(move, board, mysteryCell, teleporter, log);
        this.coin = coin;
    }

    @Override
    protected void arrive() {
        GamePiece piece = getMove().getPrimaryPiece();
        getBoard().relocate(piece, getMove().getDestination());
        getLog().reportPieceReleased(piece);
        getLog().reportPieceCounts(getBoard().createSnapshot(piece.getColour()));
        piece.assignStartingDirection(coin.toss().getAwardedDirection());
    }
}
