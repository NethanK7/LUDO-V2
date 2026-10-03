package ludot.movement;

import ludot.board.BoardSquare;
import ludot.board.GamePiece;
import ludot.board.TravelDirection;

/** Where one piece goes in a move: from, to, direction and steps. */
public record PieceTransition(GamePiece piece, BoardSquare from, BoardSquare to, TravelDirection direction,
        int stepsTaken, int approachPassesAtDestination) {
}
