package ludot.strategy;

import java.util.List;
import java.util.Optional;
import ludot.movement.CandidateMove;

/** Strategy: how a player chooses one move from the legal ones. */
public interface MoveSelectionStrategy {

    Optional<CandidateMove> chooseMove(List<CandidateMove> options);

    default void rememberMove(CandidateMove move) {
    }

    default void finishRound() {
    }
}
