package ludot.player;

import java.util.List;
import java.util.Optional;
import ludot.board.PieceColour;
import ludot.movement.MoveOptions;
import ludot.movement.PlannedMove;

/**
 * One of the four players: a colour plus the strategy that decides its moves.
 *
 * <p>The player never decides anything itself. It hands the legal moves to its
 * {@link PlayerStrategy} and makes sure the answer really is one of them, so even a faulty strategy
 * can never play an illegal move or waste a roll that could have been used.
 */
public final class Player {

    private final PieceColour colour;
    private final PlayerStrategy strategy;

    public Player(PieceColour colour, PlayerStrategy strategy) {
        this.colour = colour;
        this.strategy = strategy;
    }

    public PieceColour colour() {
        return colour;
    }

    /** The move to play with this roll, or empty when no piece can use it. */
    public Optional<PlannedMove> chooseMove(MoveOptions options) {
        List<PlannedMove> legalMoves = options.playableMoves();
        if (legalMoves.isEmpty()) {
            return Optional.empty();
        }
        return strategy.chooseMove(legalMoves)
                .filter(legalMoves::contains)
                .or(() -> Optional.of(legalMoves.get(0)));
    }

    public void onMoveExecuted(PlannedMove move) {
        strategy.onMoveExecuted(move);
    }

    public void onRoundCompleted() {
        strategy.onRoundCompleted();
    }
}
