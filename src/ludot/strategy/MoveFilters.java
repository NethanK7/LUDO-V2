package ludot.strategy;

import java.util.function.Predicate;
import ludot.movement.CandidateMove;

/** Ready-made conditions that a preference can ask of a move. */
public final class MoveFilters {

    private MoveFilters() {
    }

    public static Predicate<CandidateMove> capturing() {
        return CandidateMove::capturesAnything;
    }

    public static Predicate<CandidateMove> releasingFromBase() {
        return CandidateMove::isEnteringBoard;
    }

    public static Predicate<CandidateMove> movingABlock() {
        return CandidateMove::isBlockMove;
    }

    // T-7: a piece that has never captured still needs one to reach its home straight.
    public static Predicate<CandidateMove> capturingForTheFirstTime() {
        return capturing().and(move -> !move.getPrimaryPiece().hasEarnedHomeStraightEntry());
    }

    public static Predicate<CandidateMove> anyMove() {
        return move -> true;
    }
}
