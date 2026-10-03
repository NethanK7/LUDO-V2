package ludot.strategy;

import static ludot.BoardFixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.movement.CandidateMove;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import org.junit.jupiter.api.Test;

/** Tests for the rule chain: a rule decides or passes on. */
class PriorityRuleTest {

    private final GameBoard board = new GameBoard();
    private final PathNavigator pathCalculator = new PathNavigator(board);

    private List<CandidateMove> findLegalMoves(PlayerColour colour, int roll) {
        return new MoveOptionFinder(board, pathCalculator).findOptions(colour, roll).playableMoves();
    }

    @Test
    void aRuleThatFindsAMoveDecidesAndTheRestOfTheChainIsNotAsked() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        MoveSelectionStrategy mustNotBeAsked = options -> {
            throw new AssertionError("the chain went past a rule that had an answer");
        };

        CandidateMove choice = new ReleaseFromBaseRule(mustNotBeAsked)
                .chooseMove(findLegalMoves(PlayerColour.YELLOW, 6)).orElseThrow();

        assertEquals(true, choice.isEnteringBoard());
    }

    @Test
    void aRuleWithNoAnswerPassesTheDecisionToTheNextRule() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        List<CandidateMove> options = findLegalMoves(PlayerColour.YELLOW, 2);
        CandidateMove fallback = options.get(0);

        Optional<CandidateMove> choice =
                new ReleaseFromBaseRule(moves -> Optional.of(fallback)).chooseMove(options);

        assertSame(fallback, choice.orElseThrow());
    }

    @Test
    void closestToHomeOnlyConsidersMovesThatPassItsCondition() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 40, TravelDirection.CLOCKWISE, 0);
        NearestToHomeRule rule = new NearestToHomeRule(pathCalculator,
                move -> move.getPrimaryPiece().getName().equals("Y1"), new DefaultMoveRule());

        assertEquals("Y1", rule.chooseMove(findLegalMoves(PlayerColour.YELLOW, 2)).orElseThrow()
                .getPrimaryPiece().getName());
    }

    @Test
    void theLastLinkOfEveryChainPlaysTheFirstLegalMove() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 40, TravelDirection.CLOCKWISE, 0);
        List<CandidateMove> options = findLegalMoves(PlayerColour.YELLOW, 2);

        assertSame(options.get(0), new DefaultMoveRule().chooseMove(options).orElseThrow());
    }
}
