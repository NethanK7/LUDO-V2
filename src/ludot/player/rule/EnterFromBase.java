package ludot.player.rule;

import java.util.List;
import java.util.Optional;
import ludot.movement.PlannedMove;
import ludot.player.PlayerStrategy;

/** Bring a piece out of the base onto "X" whenever a six makes that possible. */
public final class EnterFromBase extends MoveRule {

    public EnterFromBase(PlayerStrategy next) {
        super(next);
    }

    @Override
    protected Optional<PlannedMove> select(List<PlannedMove> options) {
        return options.stream().filter(PlannedMove::isEnteringBoard).findFirst();
    }
}
