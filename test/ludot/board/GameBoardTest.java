package ludot.board;

import static ludot.BoardFixtures.findPiece;
import static ludot.BoardFixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for where pieces stand and when they form a block. */
class GameBoardTest {

    private final GameBoard board = new GameBoard();

    @Test
    void everyPieceStartsInItsBase() {
        for (PlayerColour colour : PlayerColour.values()) {
            assertEquals(4, board.getPiecesInBase(colour).size());
            assertTrue(board.getPiecesInPlay(colour).isEmpty());
        }
        assertFalse(board.hasAnyPieceOnRing());
        assertFalse(board.isPartOfBlock(findPiece(board, PlayerColour.RED, 1)));
    }

    @Test
    void relocatingMovesThePieceAndKeepsTheIndexInStep() {
        GamePiece piece = findPiece(board, PlayerColour.RED, 1);

        board.relocate(piece, BoardSquare.ofRing(26));

        assertEquals(BoardSquare.ofRing(26), piece.getSquare());
        assertEquals(List.of(piece), board.getGroupOn(BoardSquare.ofRing(26), PlayerColour.RED));
        assertEquals(3, board.getPiecesInBase(PlayerColour.RED).size());
        assertFalse(board.isRingCellEmpty(26));
    }

    @Test
    void twoPiecesOfOneColourOnOneCellFormABlock() {
        place(board, PlayerColour.GREEN, 1, 10, TravelDirection.CLOCKWISE, 0);
        GamePiece second = place(board, PlayerColour.GREEN, 2, 10, TravelDirection.CLOCKWISE, 0);

        assertTrue(board.hasBlockOn(BoardSquare.ofRing(10), PlayerColour.GREEN));
        assertTrue(board.isPartOfBlock(second));
        assertEquals(List.of(BoardSquare.ofRing(10)), board.findBlockSquares(PlayerColour.GREEN));
    }

    @Test
    void piecesOfDifferentColoursOnOneCellAreNotABlock() {
        place(board, PlayerColour.GREEN, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 10, TravelDirection.CLOCKWISE, 0);

        assertFalse(board.hasBlockOn(BoardSquare.ofRing(10), PlayerColour.GREEN));
        assertFalse(board.hasBlockOn(BoardSquare.ofRing(10), PlayerColour.RED));
        assertEquals(2, board.getGroupsOn(BoardSquare.ofRing(10)).size());
    }

    @Test
    void aBlockAlwaysListsItsPiecesInNumberOrder() {
        place(board, PlayerColour.BLUE, 3, 5, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 5, TravelDirection.CLOCKWISE, 0);

        List<String> names = board.getGroupOn(BoardSquare.ofRing(5), PlayerColour.BLUE).stream()
                .map(GamePiece::getName).toList();

        assertEquals(List.of("B1", "B3"), names);
    }

    @Test
    void aPlayerWinsOnlyWhenAllFourPiecesAreHome() {
        for (int number = 1; number <= 3; number++) {
            board.relocate(findPiece(board, PlayerColour.YELLOW, number), BoardSquare.ofHome(PlayerColour.YELLOW));
        }
        assertFalse(board.hasAllPiecesHome(PlayerColour.YELLOW));

        board.relocate(findPiece(board, PlayerColour.YELLOW, 4), BoardSquare.ofHome(PlayerColour.YELLOW));

        assertTrue(board.hasAllPiecesHome(PlayerColour.YELLOW));
        assertTrue(board.getPiecesInPlay(PlayerColour.YELLOW).isEmpty());
    }

    @Test
    void piecesInTheHomeStraightAreInPlayButNotOnTheRing() {
        board.relocate(findPiece(board, PlayerColour.RED, 1), BoardSquare.ofHomeStraight(PlayerColour.RED, 1));

        assertEquals(1, board.getPiecesInPlay(PlayerColour.RED).size());
        assertFalse(board.hasAnyPieceOnRing());
        assertFalse(board.isPartOfBlock(findPiece(board, PlayerColour.RED, 1)));
    }

    @Test
    void aStatusSnapshotCountsAndLabelsEveryPiece() {
        place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        board.relocate(findPiece(board, PlayerColour.RED, 2), BoardSquare.ofHome(PlayerColour.RED));

        PlayerStatusSnapshot status = board.createSnapshot(PlayerColour.RED);

        assertEquals(1, status.countOnBoard());
        assertEquals(2, status.countInBase());
        assertEquals(1, status.countAtHome());
        assertEquals(List.of("26", "Home", "Base", "Base"),
                status.pieces().stream().map(piece -> piece.square().getLabel()).toList());
    }
}
