package ludot.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import ludot.movement.CandidateMove;

/** Builder: lists a player's priorities from strongest to weakest and links them into one chain. */
public final class PreferenceChain {

    private record Priority(Predicate<CandidateMove> wanted, Comparator<CandidateMove> ranking) {
    }

    private final List<Priority> priorities = new ArrayList<>();

    public static PreferenceChain builder() {
        return new PreferenceChain();
    }

    public PreferenceChain prefer(Predicate<CandidateMove> wanted, Comparator<CandidateMove> ranking) {
        priorities.add(new Priority(wanted, ranking));
        return this;
    }

    public MoveSelectionStrategy build() {
        MoveSelectionStrategy chain = options -> options.stream().findFirst();
        for (Priority priority : priorities.reversed()) {
            chain = new MovePreference(priority.wanted(), priority.ranking(), chain);
        }
        return chain;
    }
}
