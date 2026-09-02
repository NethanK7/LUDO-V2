package ludot.player;

import java.util.List;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.piece.Piece;

/**
 * Yellow: "always prioritises winning" (Section 2.1.3).
 *
 * <p>Yellow captures only as much as Rule&nbsp;T-7 forces it to. A piece may not turn into its home
 * straight until it has captured once, so yellow looks for captures with exactly those pieces that
 * have not captured yet - "the pieces that need captures first" - and ignores captures for pieces
 * that have already earned their entry. Everything else is pure progress: move whichever piece is
 * closest to home.
 */
public final class YellowPlayer extends Player {

    public YellowPlayer(Board board, PathResolver pathResolver) {
        super(PieceColour.YELLOW, board, pathResolver);
    }

    @Override
    public String behaviourSummary() {
        return "racer - empties its base, captures only to satisfy Rule T-7, then runs for home";
    }

    @Override
    protected PlannedMove selectMove(List<PlannedMove> options, int rollValue) {
        // "Yellow always like to keep an empty base. Therefore, anytime a six is thrown, if there
        // are any pieces in the base, they will be moved to X."
        PlannedMove enterBoard = enterBoardMove(options);
        if (enterBoard != null) {
            return enterBoard;
        }

        List<PlannedMove> capturesThatUnlockHome = capturingMoves(options).stream()
                .filter(move -> stillNeedsACapture(move.primaryPiece()))
                .toList();
        if (!capturesThatUnlockHome.isEmpty()) {
            return closestToHome(capturesThatUnlockHome);
        }

        // "In case no captures could be done, Yellow moves the piece closest to its home by the
        // number specified in the roll."
        return closestToHome(options);
    }

    /** Rule T-7: this piece cannot enter its home straight until it has captured an opponent. */
    private boolean stillNeedsACapture(Piece piece) {
        return !piece.hasEarnedHomeStraightEntry();
    }
}
