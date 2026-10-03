package ludot.movement;

import java.util.List;
import ludot.board.BoardSquare;
import ludot.board.GamePiece;
import ludot.board.TravelDirection;

/** One legal move, worked out but not played yet. */
public record CandidateMove(MoveCategory type, List<PieceTransition> movements, List<GamePiece> capturedPieces) {

    public CandidateMove {
        movements = List.copyOf(movements);
        capturedPieces = List.copyOf(capturedPieces);
    }

    public GamePiece getPrimaryPiece() {
        return movements.get(0).piece();
    }

    public BoardSquare getDestination() {
        return movements.get(0).to();
    }

    public TravelDirection getDirection() {
        return movements.get(0).direction();
    }

    public int getStepsTaken() {
        return movements.get(0).stepsTaken();
    }

    public boolean capturesAnything() {
        return !capturedPieces.isEmpty();
    }

    public boolean isEnteringBoard() {
        return type == MoveCategory.ENTER_BOARD;
    }

    public boolean isBlockMove() {
        return type == MoveCategory.BLOCK_ADVANCE;
    }

    public int getGroupSize() {
        return movements.size();
    }

    public List<GamePiece> getMovedPieces() {
        return movements.stream().map(PieceTransition::piece).toList();
    }
}
