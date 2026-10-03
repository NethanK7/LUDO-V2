package ludot.command;

import ludot.board.Board;
import ludot.board.Piece;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.random.Coin;
import ludot.ui.GameListener;

/** Rules 2 and T-1: a six brings a piece onto its "X", then a coin decides its direction. */
public final class EnterBoardCommand extends MoveCommand {

    private final Coin coin;

    EnterBoardCommand(PlannedMove move, Board board, Coin coin, MysteryCell mysteryCell,
            MysteryEffectResolver mysteryEffects, GameListener log) {
        super(move, board, mysteryCell, mysteryEffects, log);
        this.coin = coin;
    }

    @Override
    protected void arrive() {
        Piece piece = move().primaryPiece();
        board().relocate(piece, move().destination());
        log().movesToStartingPoint(piece);
        log().playerPieceCounts(board().statusOf(piece.colour()));
        piece.assignStartingDirection(coin.toss().awardedDirection());
    }
}
