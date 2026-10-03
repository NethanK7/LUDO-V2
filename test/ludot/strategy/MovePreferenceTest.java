package ludot.strategy;

import static ludot.BoardFixtures.place;
import static ludot.strategy.MoveFilters.anyMove;
import static ludot.strategy.MoveFilters.releasingFromBase;
import static ludot.strategy.MoveRankings.closestToHome;
import static ludot.strategy.MoveRankings.listOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.movement.CandidateMove;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import org.junit.jupiter.api.Test;

/** Tests for the preference chain: a preference takes its best move or passes the choice on. */
class MovePreferenceTest {

    private final GameBoard board = new GameBoard();
    private final PathNavigator navigator = new PathNavigator(board);

    private List<CandidateMove> listLegalMoves(PlayerColour colour, int roll) {
        return new MoveOptionFinder(board, navigator).listAvailableMoves(colour, roll).playableMoves();
    }

    @Test
    void aPreferenceWithAMatchingMoveNeverAsksTheRestOfTheChain() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        MoveSelectionStrategy mustNotBeAsked = options -> {
            throw new AssertionError("the chain went past a preference that had an answer");
        };

        Optional<CandidateMove> choice = new MovePreference(releasingFromBase(), listOrder(), mustNotBeAsked)
                .chooseMove(listLegalMoves(PlayerColour.YELLOW, 6));

        assertTrue(choice.orElseThrow().isEnteringBoard());
    }

    @Test
    void aPreferenceWithNoMatchingMoveHandsOverToTheNextOne() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        List<CandidateMove> options = listLegalMoves(PlayerColour.YELLOW, 2);
        CandidateMove fallbackChoice = options.get(0);

        Optional<CandidateMove> choice = new MovePreference(releasingFromBase(), listOrder(),
                moves -> Optional.of(fallbackChoice)).chooseMove(options);

        assertSame(fallbackChoice, choice.orElseThrow());
    }

    @Test
    void aPreferenceTakesTheBestRankedOfTheMovesItWants() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 40, TravelDirection.CLOCKWISE, 0);

        List<CandidateMove> options = listLegalMoves(PlayerColour.YELLOW, 2);
        MoveSelectionStrategy byDistance = PreferenceChain.builder()
                .prefer(anyMove(), closestToHome(navigator))
                .build();
        MoveSelectionStrategy byListOrder = PreferenceChain.builder()
                .prefer(anyMove(), listOrder())
                .build();

        assertEquals("Y2", byDistance.chooseMove(options).orElseThrow().getPrimaryPiece().getName());
        assertEquals("Y1", byListOrder.chooseMove(options).orElseThrow().getPrimaryPiece().getName());
    }

    @Test
    void whenNothingIsPreferredTheChainPlaysTheFirstLegalMove() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 40, TravelDirection.CLOCKWISE, 0);
        List<CandidateMove> options = listLegalMoves(PlayerColour.YELLOW, 2);

        MoveSelectionStrategy chain = PreferenceChain.builder()
                .prefer(releasingFromBase(), listOrder())
                .build();

        assertSame(options.get(0), chain.chooseMove(options).orElseThrow());
    }

    @Test
    void thePreferencesAreTriedFromStrongestToWeakest() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 40, TravelDirection.CLOCKWISE, 0);
        List<CandidateMove> options = listLegalMoves(PlayerColour.YELLOW, 6);

        MoveSelectionStrategy strongestFirst = PreferenceChain.builder()
                .prefer(releasingFromBase(), listOrder())
                .prefer(anyMove(), closestToHome(navigator))
                .build();
        MoveSelectionStrategy weakestFirst = PreferenceChain.builder()
                .prefer(anyMove(), closestToHome(navigator))
                .prefer(releasingFromBase(), listOrder())
                .build();

        assertTrue(strongestFirst.chooseMove(options).orElseThrow().isEnteringBoard());
        assertEquals("Y2", weakestFirst.chooseMove(options).orElseThrow().getPrimaryPiece().getName());
    }
}
