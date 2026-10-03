package ludot.board;

import java.util.List;
import java.util.function.Predicate;

/** DTO: a read-only snapshot of one player's pieces for the round report. */
public record PlayerStatusSnapshot(PlayerColour colour, List<PieceSnapshot> pieces) {

    public record PieceSnapshot(String name, BoardSquare square) {
    }

    public PlayerStatusSnapshot {
        pieces = List.copyOf(pieces);
    }

    public int countOnBoard() {
        return countWhere(square -> square.isRing() || square.isHomeStraight());
    }

    public int countInBase() {
        return countWhere(BoardSquare::isBase);
    }

    public int countAtHome() {
        return countWhere(BoardSquare::isHome);
    }

    public boolean isFinished() {
        return countAtHome() == BoardSpecification.PIECES_PER_COLOUR;
    }

    private int countWhere(Predicate<BoardSquare> condition) {
        return (int) pieces.stream().filter(piece -> condition.test(piece.square())).count();
    }
}
