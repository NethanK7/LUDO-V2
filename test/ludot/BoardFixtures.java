package ludot;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.random.RandomnessProvider;
import org.mockito.Answers;

/** Helpers for setting up exact board positions in tests. */
public final class BoardFixtures {

    private BoardFixtures() {
    }

    public static GamePiece place(GameBoard board, PlayerColour colour, int number, int cell,
            TravelDirection direction, int captures) {
        return placeOn(board, colour, number, BoardSquare.ofRing(cell), direction, captures);
    }

    public static GamePiece placeOn(GameBoard board, PlayerColour colour, int number, BoardSquare square,
            TravelDirection direction, int captures) {
        GamePiece piece = findPiece(board, colour, number);
        board.relocate(piece, square);
        piece.assignStartingDirection(direction);
        for (int capture = 0; capture < captures; capture++) {
            piece.recordCapture();
        }
        return piece;
    }

    public static GamePiece findPiece(GameBoard board, PlayerColour colour, int number) {
        return board.getPiecesOf(colour).get(number - 1);
    }

    public static RandomnessProvider createFixedRandom(int index, boolean heads) {
        RandomnessProvider random = mock(RandomnessProvider.class,
                withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
        when(random.generateInt(anyInt())).thenReturn(index);
        when(random.generateBoolean()).thenReturn(heads);
        return random;
    }
}
