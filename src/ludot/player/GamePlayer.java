package ludot.player;

import java.util.List;
import java.util.Optional;
import ludot.board.PlayerColour;
import ludot.movement.AvailableMoves;
import ludot.movement.CandidateMove;
import ludot.strategy.MoveSelectionStrategy;

/** A player is a colour plus the strategy that picks its moves. */
public final class GamePlayer {

    private final PlayerColour colour;
    private final MoveSelectionStrategy strategy;

    public GamePlayer(PlayerColour colour, MoveSelectionStrategy strategy) {
        this.colour = colour;
        this.strategy = strategy;
    }

    public PlayerColour getColour() {
        return colour;
    }

    public Optional<CandidateMove> chooseMove(AvailableMoves options) {
        List<CandidateMove> legalMoves = options.playableMoves();
        if (legalMoves.isEmpty()) {
            return Optional.empty();
        }
        return strategy.chooseMove(legalMoves)
                .filter(legalMoves::contains)
                .or(() -> Optional.of(legalMoves.get(0)));
    }

    public void rememberMove(CandidateMove move) {
        strategy.rememberMove(move);
    }

    public void finishRound() {
        strategy.finishRound();
    }
}
