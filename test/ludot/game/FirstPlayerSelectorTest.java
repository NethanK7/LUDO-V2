package ludot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import ludot.board.PieceColour;
import ludot.random.Dice;
import ludot.ui.GameListener;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** "The player who rolls the highest will be the first to roll." */
class FirstPlayerSelectorTest {

    private final Dice dice = mock(Dice.class);
    private final GameListener listener = mock(GameListener.class);
    private final FirstPlayerSelector selector = new FirstPlayerSelector(dice, listener);

    @Test
    void theHighestRollerStarts() {
        // yellow, blue, red, green roll in that order
        when(dice.roll()).thenReturn(3, 5, 2, 4);

        assertEquals(PieceColour.BLUE, selector.determineFirstPlayer());
        InOrder order = inOrder(listener);
        order.verify(listener).openingRoll(PieceColour.YELLOW, 3);
        order.verify(listener).openingRoll(PieceColour.BLUE, 5);
        order.verify(listener).openingRoll(PieceColour.RED, 2);
        order.verify(listener).openingRoll(PieceColour.GREEN, 4);
    }

    @Test
    void onlyTheTiedPlayersRollAgain() {
        // yellow and red tie on 6, then red wins the roll-off
        when(dice.roll()).thenReturn(6, 1, 6, 2, 3, 5);

        assertEquals(PieceColour.RED, selector.determineFirstPlayer());
        verify(dice, times(6)).roll();
        verify(listener).openingRoll(PieceColour.RED, 5);
    }

    @Test
    void theDicePassesToTheLeftFromTheFirstPlayer() {
        assertEquals(List.of(PieceColour.RED, PieceColour.GREEN, PieceColour.YELLOW,
                PieceColour.BLUE), selector.roundOrderStartingWith(PieceColour.RED));
    }

    @Test
    void aThreeWayTieIsRolledOffBetweenThoseThreeOnly() {
        // yellow, blue and red tie on 5; green (2) is out; blue wins the roll-off
        when(dice.roll()).thenReturn(5, 5, 5, 2, 1, 4, 3);

        assertEquals(PieceColour.BLUE, selector.determineFirstPlayer());
        verify(dice, times(7)).roll();
        verify(listener, times(1)).openingRoll(eq(PieceColour.GREEN), anyInt());
    }
}
