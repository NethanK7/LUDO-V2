package ludot.strategy;

import java.util.List;
import java.util.Optional;
import ludot.movement.CandidateMove;

/** Bring a piece out of the base whenever a six allows it. */
public final class ReleaseFromBaseRule extends PriorityRule {

    public ReleaseFromBaseRule(MoveSelectionStrategy next) {
        super(next);
    }

    @Override
    protected Optional<CandidateMove> select(List<CandidateMove> options) {
        return options.stream().filter(CandidateMove::isEnteringBoard).findFirst();
    }
}
