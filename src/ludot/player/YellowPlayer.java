package ludot.player;

import java.util.List;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;

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
    protected Optional<PlannedMove> selectMove(List<PlannedMove> options, int rollValue) {
        // "Yellow always like to keep an empty base. Therefore, anytime a six is thrown, if there
        // are any pieces in the base, they will be moved to X."
        Optional<PlannedMove> enterBoard = enterBoardMove(options);
        if (enterBoard.isPresent()) {
            return enterBoard;
        }

        // "Yellow will prioritise the pieces that need captures first to see whether any opponent
        // piece is within range. If such a piece is within range then the capture will take place."
        List<PlannedMove> capturesThatUnlockHome = capturesNeededForHomeStraight(options);
        if (!capturesThatUnlockHome.isEmpty()) {
            return closestToHome(capturesThatUnlockHome);
        }

        // "In case no captures could be done, Yellow moves the piece closest to its home by the
        // number specified in the roll."
        return closestToHome(options);
    }
}
