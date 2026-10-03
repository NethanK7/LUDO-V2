package ludot.player.rule;

import java.util.List;
import java.util.Optional;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.player.PlayerStrategy;

/** Green: "always attempts to move forward using the block move explained in Rule T-4". */
public final class BlockMove extends MoveRule {

    private final PathResolver pathResolver;

    public BlockMove(PathResolver pathResolver, PlayerStrategy next) {
        super(next);
        this.pathResolver = pathResolver;
    }

    @Override
    protected Optional<PlannedMove> select(List<PlannedMove> options) {
        return closestToHome(pathResolver, options.stream().filter(PlannedMove::isBlockMove));
    }
}
