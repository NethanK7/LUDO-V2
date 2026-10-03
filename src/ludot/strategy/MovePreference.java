package ludot.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import ludot.movement.CandidateMove;

/** Chain of Responsibility: takes the best move it wants, or hands the choice to the next preference. */
public final class MovePreference implements MoveSelectionStrategy {

    private final Predicate<CandidateMove> wanted;
    private final Comparator<CandidateMove> ranking;
    private final MoveSelectionStrategy fallback;

    public MovePreference(Predicate<CandidateMove> wanted, Comparator<CandidateMove> ranking,
            MoveSelectionStrategy fallback) {
        this.wanted = wanted;
        this.ranking = ranking;
        this.fallback = fallback;
    }

    @Override
    public Optional<CandidateMove> chooseMove(List<CandidateMove> options) {
        return options.stream().filter(wanted).min(ranking).or(() -> fallback.chooseMove(options));
    }
}
