package ludot.player;

import static ludot.BoardFixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.movement.AvailableMoves;
import ludot.movement.CandidateMove;
import ludot.movement.MoveCategory;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import ludot.movement.PieceTransition;
import ludot.mystery.MysteryCellScheduler;
import org.junit.jupiter.api.Test;

/** Tests for building players. */
class GamePlayerFactoryTest {

    private final GameBoard board = new GameBoard();
    private final GamePlayerFactory factory =
            new GamePlayerFactory(board, new PathNavigator(board), mock(MysteryCellScheduler.class));

    @Test
    void allFourPlayersAreCreatedInBoardOrder() {
        List<PlayerColour> colours = factory.createAll().stream().map(GamePlayer::getColour).toList();

        assertEquals(List.of(PlayerColour.YELLOW, PlayerColour.BLUE, PlayerColour.RED,
                PlayerColour.GREEN), colours);
    }

    @Test
    void aPlayerWithNoLegalMoveChoosesNothing() {
        GamePlayer red = factory.create(PlayerColour.RED);

        assertTrue(red.chooseMove(new AvailableMoves(List.of(), List.of())).isEmpty());
    }

    @Test
    void aPlayerNeverPlaysAMoveItsStrategyMadeUp() {
        GamePiece r1 = place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        AvailableMoves options = new MoveOptionFinder(board, new PathNavigator(board))
                .findOptions(PlayerColour.RED, 3);
        CandidateMove invented = new CandidateMove(MoveCategory.ADVANCE, List.of(new PieceTransition(r1,
                BoardSquare.ofRing(26), BoardSquare.ofRing(40), TravelDirection.CLOCKWISE, 14, 0)), List.of());
        GamePlayer player = new GamePlayer(PlayerColour.RED, legalMoves -> Optional.of(invented));

        assertEquals(options.playableMoves().get(0), player.chooseMove(options).orElseThrow());
    }
}
