package ludot.board;

import static ludot.Fixtures.piece;
import static ludot.Fixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoardTest {

    private final Board board = new Board();

    @Test
    void everyPieceStartsInItsBase() {
        // Rule 3
        for (PieceColour colour : PieceColour.values()) {
            assertEquals(4, board.piecesInBase(colour).size());
            assertTrue(board.piecesInPlay(colour).isEmpty());
        }
        assertFalse(board.hasAnyPieceOnRing());
        // four pieces share the base, but a base is never a block (T-3 is about the board)
        assertFalse(board.isPartOfBlock(piece(board, PieceColour.RED, 1)));
    }

    @Test
    void relocatingMovesThePieceAndKeepsTheIndexInStep() {
        Piece piece = piece(board, PieceColour.RED, 1);

        board.relocate(piece, Square.ring(26));

        assertEquals(Square.ring(26), piece.square());
        assertEquals(List.of(piece), board.groupOn(Square.ring(26), PieceColour.RED));
        assertEquals(3, board.piecesInBase(PieceColour.RED).size());
        assertFalse(board.isRingCellEmpty(26));
    }

    @Test
    void twoPiecesOfOneColourOnOneCellFormABlock() {
        // T-3
        place(board, PieceColour.GREEN, 1, 10, Direction.CLOCKWISE, 0);
        Piece second = place(board, PieceColour.GREEN, 2, 10, Direction.CLOCKWISE, 0);

        assertTrue(board.hasBlockOn(Square.ring(10), PieceColour.GREEN));
        assertTrue(board.isPartOfBlock(second));
        assertEquals(List.of(Square.ring(10)), board.blockSquaresOf(PieceColour.GREEN));
    }

    @Test
    void piecesOfDifferentColoursOnOneCellAreNotABlock() {
        place(board, PieceColour.GREEN, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 10, Direction.CLOCKWISE, 0);

        assertFalse(board.hasBlockOn(Square.ring(10), PieceColour.GREEN));
        assertFalse(board.hasBlockOn(Square.ring(10), PieceColour.RED));
        assertEquals(2, board.groupsOn(Square.ring(10)).size());
    }

    @Test
    void aBlockAlwaysListsItsPiecesInNumberOrder() {
        place(board, PieceColour.BLUE, 3, 5, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 5, Direction.CLOCKWISE, 0);

        List<String> names = board.groupOn(Square.ring(5), PieceColour.BLUE).stream()
                .map(Piece::name).toList();

        assertEquals(List.of("B1", "B3"), names);
    }

    @Test
    void aPlayerWinsOnlyWhenAllFourPiecesAreHome() {
        // Rule 11
        for (int number = 1; number <= 3; number++) {
            board.relocate(piece(board, PieceColour.YELLOW, number), Square.home(PieceColour.YELLOW));
        }
        assertFalse(board.hasAllPiecesHome(PieceColour.YELLOW));

        board.relocate(piece(board, PieceColour.YELLOW, 4), Square.home(PieceColour.YELLOW));

        assertTrue(board.hasAllPiecesHome(PieceColour.YELLOW));
        assertTrue(board.piecesInPlay(PieceColour.YELLOW).isEmpty());
    }

    @Test
    void piecesInTheHomeStraightAreInPlayButNotOnTheRing() {
        board.relocate(piece(board, PieceColour.RED, 1), Square.homeStraight(PieceColour.RED, 1));

        assertEquals(1, board.piecesInPlay(PieceColour.RED).size());
        assertFalse(board.hasAnyPieceOnRing());
        // four pieces share the base, but a base is never a block (T-3 is about the board)
        assertFalse(board.isPartOfBlock(piece(board, PieceColour.RED, 1)));
    }

    @Test
    void aStatusSnapshotCountsAndLabelsEveryPiece() {
        place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        board.relocate(piece(board, PieceColour.RED, 2), Square.home(PieceColour.RED));

        PlayerStatus status = board.statusOf(PieceColour.RED);

        assertEquals(1, status.piecesOnBoard());
        assertEquals(2, status.piecesInBase());
        assertEquals(1, status.piecesHome());
        assertEquals(List.of("26", "Home", "Base", "Base"),
                status.pieces().stream().map(PlayerStatus.PieceLocation::location).toList());
    }
}
