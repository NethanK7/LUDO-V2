package ludot.player.rule;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.player.PlayerStrategy;

/**
 * Red: "if more than one piece can be captured ... red prioritises capturing the opponent piece
 * closest to its home" - the victim that loses the most by going back to its base.
 */
public final class CaptureClosestToVictimHome extends MoveRule {

    private final PathResolver pathResolver;

    public CaptureClosestToVictimHome(PathResolver pathResolver, PlayerStrategy next) {
        super(next);
        this.pathResolver = pathResolver;
    }

    @Override
    protected Optional<PlannedMove> select(List<PlannedMove> options) {
        return options.stream()
                .filter(PlannedMove::capturesAnything)
                .min(Comparator.comparingInt(this::closestVictimDistanceToHome));
    }

    private int closestVictimDistanceToHome(PlannedMove move) {
        return move.capturedPieces().stream()
                .mapToInt(pathResolver::distanceToHome)
                .min()
                .orElse(PathResolver.UNREACHABLE);
    }
}
