package ludot.strategy;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import ludot.movement.CandidateMove;
import ludot.movement.PathNavigator;

/** Move the piece closest to home, among the moves that pass a condition. */
public final class NearestToHomeRule extends PriorityRule {

    private final PathNavigator pathCalculator;
    private final Predicate<CandidateMove> allowed;

    public NearestToHomeRule(PathNavigator pathCalculator, Predicate<CandidateMove> allowed,
            MoveSelectionStrategy next) {
        super(next);
        this.pathCalculator = pathCalculator;
        this.allowed = allowed;
    }

    @Override
    protected Optional<CandidateMove> select(List<CandidateMove> options) {
        return findClosestToHome(pathCalculator, options.stream().filter(allowed));
    }
}
