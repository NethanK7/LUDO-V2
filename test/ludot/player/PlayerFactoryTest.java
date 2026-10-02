package ludot.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.MoveOptions;
import ludot.movement.PathResolver;
import ludot.mystery.MysteryCell;
import org.junit.jupiter.api.Test;

class PlayerFactoryTest {

    private final Board board = new Board();
    private final PlayerFactory factory =
            new PlayerFactory(board, new PathResolver(board), mock(MysteryCell.class));

    @Test
    void eachColourGetsItsOwnBehaviour() {
        assertInstanceOf(RedPlayer.class, factory.create(PieceColour.RED));
        assertInstanceOf(GreenPlayer.class, factory.create(PieceColour.GREEN));
        assertInstanceOf(YellowPlayer.class, factory.create(PieceColour.YELLOW));
        assertInstanceOf(BluePlayer.class, factory.create(PieceColour.BLUE));
    }

    @Test
    void allFourPlayersAreCreatedInBoardOrder() {
        List<PieceColour> colours = factory.createAll().stream().map(Player::colour).toList();

        assertEquals(List.of(PieceColour.YELLOW, PieceColour.BLUE, PieceColour.RED,
                PieceColour.GREEN), colours);
    }

    @Test
    void aPlayerWithNoLegalMoveChoosesNothing() {
        Player red = factory.create(PieceColour.RED);

        assertTrue(red.chooseMove(new MoveOptions(List.of(), List.of()), 3).isEmpty());
    }
}
