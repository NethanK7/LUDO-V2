package ludot.mystery;

import static ludot.BoardFixtures.createFixedRandom;
import static ludot.BoardFixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import org.junit.jupiter.api.Test;

/** Tests for when and where the mystery cell appears. */
class MysteryCellSchedulerTest {

    private final GameBoard board = new GameBoard();
    private final MysteryCellScheduler mysteryCell = new MysteryCellScheduler(board, createFixedRandom(0, true));

    @Test
    void neverAppearsWhileEveryPieceIsInItsBase() {
        for (int round = 0; round < 10; round++) {
            assertTrue(mysteryCell.finishRound().isEmpty());
        }
        assertFalse(mysteryCell.isActive());
    }

    @Test
    void appearsOnlyAfterTwoFullRoundsWithPiecesOnTheStandardPath() {
        place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);

        assertTrue(mysteryCell.finishRound().isEmpty(), "round of arrival");
        assertTrue(mysteryCell.finishRound().isEmpty(), "first full round");
        OptionalInt spawned = mysteryCell.finishRound();

        assertEquals(OptionalInt.of(0), spawned, "after the second full round");
        assertEquals(MysteryCellScheduler.LIFETIME_IN_ROUNDS, mysteryCell.getRoundsRemaining());
        assertTrue(mysteryCell.isOn(BoardSquare.ofRing(0)));
    }

    @Test
    void spawnsOnlyOnACellWithNoPieceOnIt() {
        place(board, PlayerColour.YELLOW, 1, 0, TravelDirection.CLOCKWISE, 0);

        assertEquals(OptionalInt.of(1), spawnFirstMysteryCell());
    }

    @Test
    void staysForFourRoundsAndThenMovesToADifferentCell() {
        place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        int firstCell = spawnFirstMysteryCell().getAsInt();

        for (int round = 1; round <= 3; round++) {
            assertTrue(mysteryCell.finishRound().isEmpty(), "round " + round);
            assertEquals(firstCell, mysteryCell.getCell());
        }
        OptionalInt secondCell = mysteryCell.finishRound();

        assertTrue(secondCell.isPresent());
        assertNotEquals(firstCell, secondCell.getAsInt());
        assertNotEquals(26, secondCell.getAsInt());
    }

    @Test
    void countsDownTheRoundsItWillStay() {
        place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        spawnFirstMysteryCell();

        mysteryCell.finishRound();

        assertEquals(3, mysteryCell.getRoundsRemaining());
    }

    @Test
    void hasNoCellBeforeItHasSpawned() {
        assertThrows(IllegalStateException.class, mysteryCell::getCell);
        assertFalse(mysteryCell.isOn(BoardSquare.ofRing(0)));
    }

    private OptionalInt spawnFirstMysteryCell() {
        mysteryCell.finishRound();
        mysteryCell.finishRound();
        return mysteryCell.finishRound();
    }
}
