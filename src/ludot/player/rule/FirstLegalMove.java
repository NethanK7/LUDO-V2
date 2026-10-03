package ludot.player.rule;

import java.util.List;
import java.util.Optional;
import ludot.movement.PlannedMove;
import ludot.player.PlayerStrategy;

/** The last link of every chain: when no rule has an opinion, play the first legal move. */
public final class FirstLegalMove implements PlayerStrategy {

    @Override
    public Optional<PlannedMove> chooseMove(List<PlannedMove> options) {
        return options.stream().findFirst();
    }
}
