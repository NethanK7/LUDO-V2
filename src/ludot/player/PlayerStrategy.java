package ludot.player;

import java.util.List;
import java.util.Optional;
import ludot.movement.PlannedMove;

/**
 * How a player picks one move from the legal ones (Strategy pattern).
 *
 * <p>Each colour gets its own strategy from {@link PlayerFactory}. {@link Player} only holds one and
 * asks it, so a new behaviour is a new strategy and nothing else in the game has to change.
 */
public interface PlayerStrategy {

    /**
     * Picks one of the legal moves, or returns empty when this strategy has no preference.
     *
     * @param options the legal moves for this roll; never empty.
     */
    Optional<PlannedMove> chooseMove(List<PlannedMove> options);

    /** Called after one of the player's moves has been played; blue uses it to follow its cycle. */
    default void onMoveExecuted(PlannedMove move) {
        // Most strategies remember nothing.
    }

    /** Called once at the end of every round; blue uses it to advance its cycle. */
    default void onRoundCompleted() {
        // Most strategies remember nothing.
    }
}
