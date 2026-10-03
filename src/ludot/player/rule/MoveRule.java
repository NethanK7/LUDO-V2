package ludot.player.rule;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.player.PlayerStrategy;

/**
 * One priority in a player's list of priorities (Chain of Responsibility and Template Method).
 *
 * <p>Red, green and yellow are each described in the brief as an ordered list: "first capture, else
 * bring a piece out, else ...". Every line of such a list is one {@code MoveRule}. A rule either picks
 * a move or passes the decision to the next rule in the chain, and every chain ends with
 * {@link FirstLegalMove}, so a move is always found.
 *
 * <p>{@link #chooseMove} is {@code final}: it fixes the "try me, otherwise ask the next rule" steps
 * once, and each rule only fills in {@link #select}, the part that differs.
 */
public abstract class MoveRule implements PlayerStrategy {

    private final PlayerStrategy next;

    protected MoveRule(PlayerStrategy next) {
        this.next = next;
    }

    @Override
    public final Optional<PlannedMove> chooseMove(List<PlannedMove> options) {
        return select(options).or(() -> next.chooseMove(options));
    }

    /** This rule's own choice, or empty to let the next rule decide. */
    protected abstract Optional<PlannedMove> select(List<PlannedMove> options);

    /**
     * Of some moves, the one whose piece has the shortest journey left to home. Several rules break
     * ties this way; on equal distances the earlier move is kept.
     */
    protected static Optional<PlannedMove> closestToHome(PathResolver pathResolver,
            Stream<PlannedMove> moves) {
        return moves.min(Comparator.comparingInt(
                move -> pathResolver.distanceToHome(move.primaryPiece())));
    }
}
