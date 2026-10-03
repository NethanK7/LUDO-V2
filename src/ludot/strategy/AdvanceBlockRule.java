package ludot.strategy;

import java.util.List;
import java.util.Optional;
import ludot.movement.CandidateMove;
import ludot.movement.PathNavigator;

/** Green: move a whole block forward (Rule T-4). */
public final class AdvanceBlockRule extends PriorityRule {

    private final PathNavigator pathCalculator;

    public AdvanceBlockRule(PathNavigator pathCalculator, MoveSelectionStrategy next) {
        super(next);
        this.pathCalculator = pathCalculator;
    }

    @Override
    protected Optional<CandidateMove> select(List<CandidateMove> options) {
        return findClosestToHome(pathCalculator, options.stream().filter(CandidateMove::isBlockMove));
    }
}
