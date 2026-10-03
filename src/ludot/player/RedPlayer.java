package ludot.player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;

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
 *   <li>and blocks are avoided "unless it is unavoidable", so a move that ends in a block is only
 *       played when every alternative would end in one too.</li>
 * </ol>
 */
public final class RedPlayer extends Player {

    public RedPlayer(Board board, PathResolver pathResolver) {
        super(PieceColour.RED, board, pathResolver);
    }


    @Override
    protected Optional<PlannedMove> selectMove(List<PlannedMove> options, int rollValue) {
        List<PlannedMove> captures = capturingMoves(options);
        if (!captures.isEmpty()) {
            return mostDamagingCapture(captures);
        }

        // "Red will always keep one piece in the standard path and will not take another piece to
        // the path from the base unless it cannot capture any piece by moving six cells."
        // Reaching this point means no capture is possible with this roll, so the six is used to
        // bring a piece out.
        Optional<PlannedMove> enterBoard = enterBoardMove(options);
        if (enterBoard.isPresent()) {
            return enterBoard;
        }

        // "Red will always avoid creating blocks unless it is unavoidable."
        List<PlannedMove> withoutBlocks = options.stream()
                .filter(move -> !endsInBlock(move))
                .toList();
        return closestToHome(withoutBlocks.isEmpty() ? options : withoutBlocks);
    }

    /**
     * Of several possible captures, the one that hurts most: the victim with the shortest journey
     * left to its own home is the one that loses the most by being sent back to its base.
     */
    private Optional<PlannedMove> mostDamagingCapture(List<PlannedMove> captures) {
        return captures.stream().min(Comparator.comparingInt(this::shortestVictimDistanceToHome));
    }

    private int shortestVictimDistanceToHome(PlannedMove move) {
        return move.capturedPieces().stream()
                .mapToInt(pathResolver::distanceToHome)
                .min()
                .orElse(PathResolver.UNREACHABLE);
    }
}
