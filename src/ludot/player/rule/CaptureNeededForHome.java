package ludot.player.rule;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.player.PlayerStrategy;

/**
 * Yellow and green: capture only "what is required to enter the home straight" (Rule T-7), so only
 * with a piece that has not captured yet. Of several such captures, the piece closest to home wins.
 */
public final class CaptureNeededForHome extends MoveRule {

    private final PathResolver pathResolver;
    private final Predicate<PlannedMove> allowed;

    /** @param allowed an extra condition, e.g. green will not break a block to capture. */
    public CaptureNeededForHome(PathResolver pathResolver, Predicate<PlannedMove> allowed,
            PlayerStrategy next) {
        super(next);
        this.pathResolver = pathResolver;
        this.allowed = allowed;
    }

    @Override
    protected Optional<PlannedMove> select(List<PlannedMove> options) {
        return closestToHome(pathResolver, options.stream()
                .filter(PlannedMove::capturesAnything)
                .filter(move -> !move.primaryPiece().hasEarnedHomeStraightEntry())
                .filter(allowed));
    }
}
