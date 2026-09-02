package ludot.player;

import java.util.List;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.piece.Piece;

/**
 * Red: "a very aggressive player who prioritises capturing opponent pieces rather than winning"
 * (Section 2.1.1).
 *
 * <p>The three sentences of the specification map onto the three steps of {@link #selectMove}:
 *
 * <ol>
 *   <li>capture whenever a capture is available, and when several are, take "the opponent piece
 *       closest to its home", i.e. the one that would lose the most progress;</li>
 *   <li>only when nothing can be captured does a six bring another piece out of the base;</li>
 *   <li>and blocks are avoided "unless it is unavoidable", so a move that would form one is only
 *       played when every alternative would form one too.</li>
 * </ol>
 */
public final class RedPlayer extends Player {

    public RedPlayer(Board board, PathResolver pathResolver) {
        super(PieceColour.RED, board, pathResolver);
    }

    @Override
    public String behaviourSummary() {
        return "aggressive - hunts captures, prefers the opponent piece closest to its home";
    }

    @Override
    protected PlannedMove selectMove(List<PlannedMove> options, int rollValue) {
        List<PlannedMove> captures = capturingMoves(options);
        if (!captures.isEmpty()) {
            return mostDamagingCapture(captures);
        }

        // "Red will always keep one piece in the standard path and will not take another piece to
        // the path from the base unless it cannot capture any piece by moving six cells."
        // Reaching this point means no capture is possible with this roll, so the six is used to
        // bring a piece out.
        PlannedMove enterBoard = enterBoardMove(options);
        if (enterBoard != null) {
            return enterBoard;
        }

        // "Red will always avoid creating blocks unless it is unavoidable."
        List<PlannedMove> withoutNewBlocks = options.stream()
                .filter(move -> !createsBlock(move))
                .toList();
        return closestToHome(withoutNewBlocks.isEmpty() ? options : withoutNewBlocks);
    }

    /**
     * Of several possible captures, the one that hurts most: the victim with the shortest journey
     * left to its own home is the one that loses the most by being sent back to its base.
     */
    private PlannedMove mostDamagingCapture(List<PlannedMove> captures) {
        PlannedMove best = null;
        int bestVictimDistance = PathResolver.UNREACHABLE;
        for (PlannedMove move : captures) {
            int victimDistance = shortestVictimDistanceToHome(move);
            if (best == null || victimDistance < bestVictimDistance) {
                best = move;
                bestVictimDistance = victimDistance;
            }
        }
        return best;
    }

    private int shortestVictimDistanceToHome(PlannedMove move) {
        int shortest = PathResolver.UNREACHABLE;
        for (Piece victim : move.capturedPieces()) {
            shortest = Math.min(shortest, pathResolver.distanceToHome(victim));
        }
        return shortest;
    }
}
