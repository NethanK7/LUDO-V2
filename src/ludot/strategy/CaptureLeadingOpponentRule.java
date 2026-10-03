package ludot.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ludot.movement.CandidateMove;
import ludot.movement.PathNavigator;

/** Red: capture, choosing the opponent piece closest to its home. */
public final class CaptureLeadingOpponentRule extends PriorityRule {

    private final PathNavigator pathCalculator;

    public CaptureLeadingOpponentRule(PathNavigator pathCalculator, MoveSelectionStrategy next) {
        super(next);
        this.pathCalculator = pathCalculator;
    }

    @Override
    protected Optional<CandidateMove> select(List<CandidateMove> options) {
        return options.stream()
                .filter(CandidateMove::capturesAnything)
                .min(Comparator.comparingInt(this::calculateClosestVictimDistance));
    }

    private int calculateClosestVictimDistance(CandidateMove move) {
        return move.capturedPieces().stream()
                .mapToInt(pathCalculator::calculateDistanceToHome)
                .min()
                .orElse(PathNavigator.UNREACHABLE);
    }
}
