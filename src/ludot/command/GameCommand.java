package ludot.command;

import java.util.Optional;
import ludot.movement.PlannedMove;

/**
 * One action a player takes with a roll (Command pattern).
 *
 * <p>Every kind of action - stepping out of the base, moving a piece or a block, being stopped by a
 * block, or doing nothing at all - is an object with the same {@link #execute()} method. The turn
 * engine therefore runs every roll the same way and never needs to ask which kind of action it has.
 */
public interface GameCommand {

    /**
     * Carries the action out.
     *
     * @return {@code true} when an opponent piece was captured, which Rule T-2 rewards with another
     *         roll.
     */
    boolean execute();

    /** The move this command plays, so the player's strategy can remember it; empty if none. */
    default Optional<PlannedMove> playedMove() {
        return Optional.empty();
    }
}
