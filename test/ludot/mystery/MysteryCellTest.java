package ludot.mystery;

import static ludot.Fixtures.fixedRandom;
import static ludot.Fixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.board.Square;
import org.junit.jupiter.api.Test;

/** Rule T-10: when and where the mystery cell appears, and how long it stays. */
class MysteryCellTest {

    private final Board board = new Board();
    // nextInt always answers 0, so the mystery cell takes the first free candidate cell
    private final MysteryCell mysteryCell = new MysteryCell(board, fixedRandom(0, true));

    @Test
    void neverAppearsWhileEveryPieceIsInItsBase() {
        for (int round = 0; round < 10; round++) {
            assertTrue(mysteryCell.onRoundCompleted().isEmpty());
        }
        assertFalse(mysteryCell.isActive());
    }

    @Test
    void appearsOnlyAfterTwoFullRoundsWithPiecesOnTheStandardPath() {
        // T-10: the round in which the first piece arrives is only partly spent on the path
        place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);

        assertTrue(mysteryCell.onRoundCompleted().isEmpty(), "round of arrival");
        assertTrue(mysteryCell.onRoundCompleted().isEmpty(), "first full round");
        OptionalInt spawned = mysteryCell.onRoundCompleted();

        assertEquals(OptionalInt.of(0), spawned, "after the second full round");
        assertEquals(MysteryCell.LIFETIME_IN_ROUNDS, mysteryCell.roundsRemaining());
        assertTrue(mysteryCell.isOn(Square.ring(0)));
    }

    @Test
    void spawnsOnlyOnACellWithNoPieceOnIt() {
        // T-10: cell 0 is taken, so the first free cell is 1
        place(board, PieceColour.YELLOW, 1, 0, Direction.CLOCKWISE, 0);

        assertEquals(OptionalInt.of(1), spawnFirstMysteryCell());
    }

    @Test
    void staysForFourRoundsAndThenMovesToADifferentCell() {
        // T-10
        place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        int firstCell = spawnFirstMysteryCell().getAsInt();

        for (int round = 1; round <= 3; round++) {
            assertTrue(mysteryCell.onRoundCompleted().isEmpty(), "round " + round);
            assertEquals(firstCell, mysteryCell.cell());
        }
        OptionalInt secondCell = mysteryCell.onRoundCompleted();

        assertTrue(secondCell.isPresent());
        assertNotEquals(firstCell, secondCell.getAsInt());
        assertNotEquals(26, secondCell.getAsInt());
    }

    @Test
    void countsDownTheRoundsItWillStay() {
        place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        spawnFirstMysteryCell();

        mysteryCell.onRoundCompleted();

        assertEquals(3, mysteryCell.roundsRemaining());
    }

    @Test
    void hasNoCellBeforeItHasSpawned() {
        assertThrows(IllegalStateException.class, mysteryCell::cell);
        assertFalse(mysteryCell.isOn(Square.ring(0)));
    }

    private OptionalInt spawnFirstMysteryCell() {
        mysteryCell.onRoundCompleted();
        mysteryCell.onRoundCompleted();
        return mysteryCell.onRoundCompleted();
    }
}
