package ludot.strategy;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import ludot.movement.CandidateMove;
import ludot.movement.PathNavigator;

/** Yellow and green: capture only with a piece that still needs a capture (Rule T-7). */
public final class RequiredCaptureRule extends PriorityRule {

    private final PathNavigator pathCalculator;
    private final Predicate<CandidateMove> allowed;

    public RequiredCaptureRule(PathNavigator pathCalculator, Predicate<CandidateMove> allowed,
            MoveSelectionStrategy next) {
        super(next);
        this.pathCalculator = pathCalculator;
        this.allowed = allowed;
    }

    @Override
    protected Optional<CandidateMove> select(List<CandidateMove> options) {
        return findClosestToHome(pathCalculator, options.stream()
                .filter(CandidateMove::capturesAnything)
                .filter(move -> !move.getPrimaryPiece().hasEarnedHomeStraightEntry())
                .filter(allowed));
    }
}
