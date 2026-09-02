package ludot.movement;

import java.util.ArrayList;
import java.util.List;
import ludot.board.Board;
import ludot.board.Square;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.piece.Piece;
import ludot.random.Coin;
import ludot.ui.GameLog;

/**
 * Carries out the move a player has chosen, and only then changes the board.
 *
 * <p>Everything that happens <em>because</em> a piece arrived somewhere is handled here, in the
 * order the rules imply:
 *
 * <ol>
 *   <li>the piece (or the whole block, Rule T-4) is relocated;</li>
 *   <li>a piece leaving the base has its coin tossed for a direction (Rule T-1);</li>
 *   <li>opponent pieces on the destination are captured and reset (Rules 6, T-8 and T-9);</li>
 *   <li>a piece that landed on the mystery cell is teleported (Rules T-10 and T-11).</li>
 * </ol>
 *
 * <p>Keeping this apart from {@link MoveGenerator} means the rules are checked once, before
 * anything is touched, and a chosen move can never be applied halfway.
 */
public final class MoveExecutor {

    private final Board board;
    private final Coin coin;
    private final MysteryCell mysteryCell;
    private final MysteryEffectResolver mysteryEffectResolver;
    private final GameLog log;

    public MoveExecutor(Board board, Coin coin, MysteryCell mysteryCell,
            MysteryEffectResolver mysteryEffectResolver, GameLog log) {
        this.board = board;
        this.coin = coin;
        this.mysteryCell = mysteryCell;
        this.mysteryEffectResolver = mysteryEffectResolver;
        this.log = log;
    }

    /**
     * Applies the move and reports whether it captured anything.
     *
     * @return {@code true} when at least one opponent piece was captured, which Rule&nbsp;T-2 
     *         rewards with another roll.
     */
    public boolean execute(PlannedMove move) {
        List<Piece> movedPieces = move.movedPieces();
        Square destination = move.destination();

        if (move.isEnteringBoard()) {
            enterBoard(move);
        } else {
            advance(move);
        }

        boolean captured = applyCaptures(move, destination);

        // Rule T-11: the teleport happens after the arrival is complete, so a piece can capture an
        // opponent on the mystery cell and only then be whisked away.
        if (mysteryCell.isOn(destination)) {
            for (Piece piece : movedPieces) {
                if (piece.square().equals(destination)) {
                    mysteryEffectResolver.resolveLandingOnMysteryCell(piece);
                }
            }
        }

        reportPiecesThatReachedHome(movedPieces);
        return captured;
    }

    /** Rules 2 and T-1: the piece steps onto "X" and its travel direction is tossed for. */
    private void enterBoard(PlannedMove move) {
        Piece piece = move.primaryPiece();
        board.relocate(piece, move.destination());
        log.movesToStartingPoint(piece);
        log.playerPieceCounts(board, piece.colour());

        Coin.Face face = coin.toss();
        piece.assignStartingDirection(face.awardedDirection());
        log.coinTossed(piece, face);
    }

    /** Rule 1, Rule T-4 and Rule T-5: the piece or block travels and its bookkeeping is updated. */
    private void advance(PlannedMove move) {
        for (PieceMovement movement : move.movements()) {
            Piece piece = movement.piece();
            if (!move.isBlockMove()) {
                // Rule T-5: a piece that steps out of a block on its own travels in the direction it
                // was given at "X", so that direction becomes its current one again here. A block
                // move deliberately does not touch it: the pieces are carried in the block's
                // direction but each keeps its own, which is what Rule T-4 compares next time.
                piece.setDirection(movement.direction());
            }
            piece.setApproachPasses(movement.approachPassesAtDestination());
            board.relocate(piece, movement.to());
        }

        if (move.isBlockMove()) {
            log.movesBlock(move);
        } else {
            log.movesPiece(move);
        }
    }

    /**
     * Rules 6, T-8 and T-9: opponents standing on the destination go back to their base with all of
     * their information reset, and the arriving pieces record the capture that Rule&nbsp;T-7 needs.
     */
    private boolean applyCaptures(PlannedMove move, Square destination) {
        if (!move.capturesAnything()) {
            return false;
        }

        Piece capturer = move.primaryPiece();
        for (Piece captured : new ArrayList<>(move.capturedPieces())) {
            board.relocate(captured, Square.base(captured.colour()));
            captured.resetAfterCapture();
            log.capture(capturer, captured, destination.label());
            log.playerPieceCounts(board, captured.colour());
        }

        if (move.isBlockMove()) {
            // Rule T-8: "The number of captures for each piece participating in the capturing
            // blockade will be incremented by one (1)."
            for (Piece piece : move.movedPieces()) {
                piece.recordCapture();
            }
        } else {
            // Rule 6: the single arriving piece is credited with every piece it removed.
            for (int index = 0; index < move.capturedPieces().size(); index++) {
                capturer.recordCapture();
            }
        }

        log.playerPieceCounts(board, capturer.colour());
        return true;
    }

    private void reportPiecesThatReachedHome(List<Piece> movedPieces) {
        for (Piece piece : movedPieces) {
            if (piece.isAtHome()) {
                log.pieceReachedHome(piece, board.piecesAtHome(piece.colour()).size());
            }
        }
    }
}
