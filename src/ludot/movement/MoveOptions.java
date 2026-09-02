package ludot.movement;

import java.util.List;

/**
 * Everything a player may do with one roll, split into the moves it <em>can</em> make and the moves
 * an opponent block <em>refused</em>.
 *
 * <p>The specification treats those two lists very differently. A player picks freely from the
 * playable moves; but when there is nothing playable at all it must report
 * <em>"[Color X] does not have other pieces in the board to move instead of the blocked piece"</em>
 * and then either shuffle up to the cell before the block or ignore the throw. Keeping both lists in
 * one returned object lets the turn engine make that decision with a single {@code isEmpty()} test.
 */
public final class MoveOptions {

    private final List<PlannedMove> playableMoves;
    private final List<BlockedAttempt> blockedAttempts;

    public MoveOptions(List<PlannedMove> playableMoves, List<BlockedAttempt> blockedAttempts) {
        this.playableMoves = List.copyOf(playableMoves);
        this.blockedAttempts = List.copyOf(blockedAttempts);
    }

    /** Moves that fully satisfy the rules and may be chosen by the player's strategy. */
    public List<PlannedMove> playableMoves() {
        return playableMoves;
    }

    /** Moves that an opponent block cut short (Rule T-3), kept for reporting and fall-backs. */
    public List<BlockedAttempt> blockedAttempts() {
        return blockedAttempts;
    }

    public boolean hasBlockedAttempt() {
        return !blockedAttempts.isEmpty();
    }

    /** True when the roll simply cannot be used: nothing to move and nothing even blocked. */
    public boolean isEmpty() {
        return playableMoves.isEmpty() && blockedAttempts.isEmpty();
    }
}
