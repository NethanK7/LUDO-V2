package ludot.movement;

import java.util.List;

/** The legal moves for one roll, plus the moves that a block stopped. */
public record AvailableMoves(List<CandidateMove> playableMoves, List<BlockedMoveAttempt> blockedMoves) {

    public AvailableMoves {
        playableMoves = List.copyOf(playableMoves);
        blockedMoves = List.copyOf(blockedMoves);
    }
}
