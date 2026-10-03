package ludot.player.rule;

import static ludot.Fixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.movement.MoveGenerator;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.player.PlayerStrategy;
import org.junit.jupiter.api.Test;

/** The Chain of Responsibility: each rule decides or passes the decision on. */
class MoveRuleTest {

    private final Board board = new Board();
    private final PathResolver pathResolver = new PathResolver(board);

    private List<PlannedMove> legalMoves(PieceColour colour, int roll) {
        return new MoveGenerator(board, pathResolver).optionsFor(colour, roll).playableMoves();
    }

    @Test
    void aRuleThatFindsAMoveDecidesAndTheRestOfTheChainIsNotAsked() {
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        PlayerStrategy mustNotBeAsked = options -> {
            throw new AssertionError("the chain went past a rule that had an answer");
        };

        PlannedMove choice = new EnterFromBase(mustNotBeAsked)
                .chooseMove(legalMoves(PieceColour.YELLOW, 6)).orElseThrow();

        assertEquals(true, choice.isEnteringBoard());
    }

    @Test
    void aRuleWithNoAnswerPassesTheDecisionToTheNextRule() {
        // a roll of 2 cannot bring a piece out, so EnterFromBase passes on
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        List<PlannedMove> options = legalMoves(PieceColour.YELLOW, 2);
        PlannedMove fallback = options.get(0);

        Optional<PlannedMove> choice =
                new EnterFromBase(moves -> Optional.of(fallback)).chooseMove(options);

        assertSame(fallback, choice.orElseThrow());
    }

    @Test
    void closestToHomeOnlyConsidersMovesThatPassItsCondition() {
        // Y2 on 40 is nearer home, but the condition only allows Y1's move
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 40, Direction.CLOCKWISE, 0);
        ClosestToHome rule = new ClosestToHome(pathResolver,
                move -> move.primaryPiece().name().equals("Y1"), new FirstLegalMove());

        assertEquals("Y1", rule.chooseMove(legalMoves(PieceColour.YELLOW, 2)).orElseThrow()
                .primaryPiece().name());
    }

    @Test
    void theLastLinkOfEveryChainPlaysTheFirstLegalMove() {
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 40, Direction.CLOCKWISE, 0);
        List<PlannedMove> options = legalMoves(PieceColour.YELLOW, 2);

        assertSame(options.get(0), new FirstLegalMove().chooseMove(options).orElseThrow());
    }
}
