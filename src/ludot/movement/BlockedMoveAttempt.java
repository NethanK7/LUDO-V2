package ludot.movement;

import java.util.Optional;
import ludot.board.BoardSquare;
import ludot.board.GamePiece;

/** A move that an opponent block stopped (Rule T-3). */
public record BlockedMoveAttempt(GamePiece piece, BoardSquare from, BoardSquare intendedDestination,
        GamePiece blockingPiece, Optional<CandidateMove> partialMove) {
}
