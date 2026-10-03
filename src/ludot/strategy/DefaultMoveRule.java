package ludot.strategy;

import java.util.List;
import java.util.Optional;
import ludot.movement.CandidateMove;

/** The last rule of every chain: play the first legal move. */
public final class DefaultMoveRule implements MoveSelectionStrategy {

    @Override
    public Optional<CandidateMove> chooseMove(List<CandidateMove> options) {
        return options.stream().findFirst();
    }
}
