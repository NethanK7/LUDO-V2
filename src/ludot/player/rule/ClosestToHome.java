package ludot.player.rule;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.player.PlayerStrategy;

/**
 * Of the moves that pass a condition, move the piece closest to its home.
 *
 * <p>The condition is what makes each use different: red uses "does not end in a block", green
 * uses "forms a new block" and "does not break a block", and every colour can use "any move".
 */
public final class ClosestToHome extends MoveRule {

    private final PathResolver pathResolver;
    private final Predicate<PlannedMove> allowed;

    public ClosestToHome(PathResolver pathResolver, Predicate<PlannedMove> allowed,
            PlayerStrategy next) {
        super(next);
        this.pathResolver = pathResolver;
        this.allowed = allowed;
    }

    @Override
    protected Optional<PlannedMove> select(List<PlannedMove> options) {
        return closestToHome(pathResolver, options.stream().filter(allowed));
    }
}
