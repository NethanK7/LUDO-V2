package ludot.player;

import static ludot.Fixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.MoveGenerator;
import ludot.movement.MoveKind;
import ludot.movement.MoveOptions;
import ludot.movement.PathResolver;
import ludot.movement.PieceMovement;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import org.junit.jupiter.api.Test;

class PlayerFactoryTest {

    private final Board board = new Board();
    private final PlayerFactory factory =
            new PlayerFactory(board, new PathResolver(board), mock(MysteryCell.class));

    @Test
    void allFourPlayersAreCreatedInBoardOrder() {
        List<PieceColour> colours = factory.createAll().stream().map(Player::colour).toList();

        assertEquals(List.of(PieceColour.YELLOW, PieceColour.BLUE, PieceColour.RED,
                PieceColour.GREEN), colours);
    }

    @Test
    void aPlayerWithNoLegalMoveChoosesNothing() {
        Player red = factory.create(PieceColour.RED);

        assertTrue(red.chooseMove(new MoveOptions(List.of(), List.of())).isEmpty());
    }

    @Test
    void aPlayerNeverPlaysAMoveItsStrategyMadeUp() {
        // the strategy answers with a move that is not legal; the player falls back to a legal one
        Piece r1 = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        MoveOptions options = new MoveGenerator(board, new PathResolver(board))
                .optionsFor(PieceColour.RED, 3);
        PlannedMove invented = new PlannedMove(MoveKind.ADVANCE, List.of(new PieceMovement(r1,
                Square.ring(26), Square.ring(40), Direction.CLOCKWISE, 14, 0)), List.of());
        Player player = new Player(PieceColour.RED, legalMoves -> Optional.of(invented));

        assertEquals(options.playableMoves().get(0), player.chooseMove(options).orElseThrow());
    }
}
