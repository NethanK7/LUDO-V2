package ludot.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import ludot.movement.CandidateMove;
import ludot.movement.PathNavigator;

/** Chain of Responsibility: each rule picks a move or passes the choice to the next rule. */
public abstract class PriorityRule implements MoveSelectionStrategy {

    private final MoveSelectionStrategy next;

    protected PriorityRule(MoveSelectionStrategy next) {
        this.next = next;
    }

    @Override
    public final Optional<CandidateMove> chooseMove(List<CandidateMove> options) {
        return select(options).or(() -> next.chooseMove(options));
    }

    protected abstract Optional<CandidateMove> select(List<CandidateMove> options);

    protected static Optional<CandidateMove> findClosestToHome(PathNavigator pathCalculator,
            Stream<CandidateMove> moves) {
        return moves.min(Comparator.comparingInt(
                move -> pathCalculator.calculateDistanceToHome(move.getPrimaryPiece())));
    }
}
