package ludot;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.random.RandomSource;
import org.mockito.Answers;

/**
 * Board positions and test doubles shared by the unit tests.
 *
 * <p>Every test sets up an exact position by hand, so the expected cells in the assertions can be
 * worked out on the numbered board rather than copied from the code under test.
 */
public final class Fixtures {

    private Fixtures() {
        // Static helpers only.
    }

    /** Puts piece {@code number} of {@code colour} on a standard cell, facing {@code direction}. */
    public static Piece place(Board board, PieceColour colour, int number, int cell,
            Direction direction, int captures) {
        return placeOn(board, colour, number, Square.ring(cell), direction, captures);
    }

    /** Puts a piece on any square with a direction and a number of captures already made. */
    public static Piece placeOn(Board board, PieceColour colour, int number, Square square,
            Direction direction, int captures) {
        Piece piece = piece(board, colour, number);
        board.relocate(piece, square);
        piece.assignStartingDirection(direction);
        for (int capture = 0; capture < captures; capture++) {
            piece.recordCapture();
        }
        return piece;
    }

    public static Piece piece(Board board, PieceColour colour, int number) {
        return board.piecesOf(colour).get(number - 1);
    }

    /**
     * A Mockito mock of {@link RandomSource} whose {@code nextInt} always answers {@code index} and
     * whose {@code nextBoolean} always answers {@code heads}. The interface's default
     * {@code pick} method runs for real, so it picks element {@code index} of any list.
     */
    public static RandomSource fixedRandom(int index, boolean heads) {
        RandomSource random = mock(RandomSource.class,
                withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
        when(random.nextInt(anyInt())).thenReturn(index);
        when(random.nextBoolean()).thenReturn(heads);
        return random;
    }
}
