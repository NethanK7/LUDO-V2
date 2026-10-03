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
import ludot.board.PlayerColour;
import ludot.random.SixSidedDie;
import ludot.ui.GameEventReporter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Tests for choosing the first player and the turn order. */
class StartingPlayerSelectorTest {

    private final SixSidedDie dice = mock(SixSidedDie.class);
    private final GameEventReporter listener = mock(GameEventReporter.class);
    private final StartingPlayerSelector selector = new StartingPlayerSelector(dice, listener);

    @Test
    void theHighestRollerStarts() {
        when(dice.roll()).thenReturn(3, 5, 2, 4);

        assertEquals(PlayerColour.BLUE, selector.determineFirstPlayer());
        InOrder order = inOrder(listener);
        order.verify(listener).reportOpeningRoll(PlayerColour.YELLOW, 3);
        order.verify(listener).reportOpeningRoll(PlayerColour.BLUE, 5);
        order.verify(listener).reportOpeningRoll(PlayerColour.RED, 2);
        order.verify(listener).reportOpeningRoll(PlayerColour.GREEN, 4);
    }

    @Test
    void onlyTheTiedPlayersRollAgain() {
        when(dice.roll()).thenReturn(6, 1, 6, 2, 3, 5);

        assertEquals(PlayerColour.RED, selector.determineFirstPlayer());
        verify(dice, times(6)).roll();
        verify(listener).reportOpeningRoll(PlayerColour.RED, 5);
    }

    @Test
    void theDicePassesToTheLeftFromTheFirstPlayer() {
        assertEquals(List.of(PlayerColour.RED, PlayerColour.GREEN, PlayerColour.YELLOW,
                PlayerColour.BLUE), selector.buildRoundOrder(PlayerColour.RED));
    }

    @Test
    void aThreeWayTieIsRolledOffBetweenThoseThreeOnly() {
        when(dice.roll()).thenReturn(5, 5, 5, 2, 1, 4, 3);

        assertEquals(PlayerColour.BLUE, selector.determineFirstPlayer());
        verify(dice, times(7)).roll();
        verify(listener, times(1)).reportOpeningRoll(eq(PlayerColour.GREEN), anyInt());
    }
}
