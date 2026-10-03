package ludot.strategy;

import java.util.Comparator;
import ludot.movement.CandidateMove;
import ludot.movement.PathNavigator;

/** Ready-made ways to rank moves. When two moves rank equally, the earlier one wins. */
public final class MoveRankings {

    private MoveRankings() {
    }

    public static Comparator<CandidateMove> closestToHome(PathNavigator navigator) {
        return Comparator.comparingInt(move -> navigator.calculateDistanceToHome(move.getPrimaryPiece()));
    }

    public static Comparator<CandidateMove> victimClosestToHome(PathNavigator navigator) {
        return Comparator.comparingInt(move -> move.capturedPieces().stream()
                .mapToInt(navigator::calculateDistanceToHome)
                .min()
                .orElse(PathNavigator.UNREACHABLE));
    }

    public static Comparator<CandidateMove> listOrder() {
        return (first, second) -> 0;
    }
}
