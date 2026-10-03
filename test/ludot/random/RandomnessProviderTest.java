package ludot.random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import ludot.board.TravelDirection;
import org.junit.jupiter.api.Test;

/** Tests for the dice, the coin and seeded randomness. */
class RandomnessProviderTest {

    @Test
    void diceTurnsTheZeroBasedRandomValueIntoAFaceFromOneToSix() {
        RandomnessProvider random = mock(RandomnessProvider.class);
        when(random.generateInt(SixSidedDie.FACES)).thenReturn(0, 5);
        SixSidedDie dice = new SixSidedDie(random);

        assertEquals(1, dice.roll());
        assertEquals(6, dice.roll());
        verify(random, times(2)).generateInt(SixSidedDie.FACES);
    }

    @Test
    void aRealRollIsAlwaysAFaceOfTheDice() {
        SixSidedDie dice = new SixSidedDie(new SeededRandomnessProvider());

        for (int roll = 0; roll < 1000; roll++) {
            int face = dice.roll();
            assertTrue(face >= 1 && face <= SixSidedDie.FACES, "rolled " + face);
        }
    }

    @Test
    void headsMeansClockwiseAndTailsMeansCounterClockwise() {
        RandomnessProvider random = mock(RandomnessProvider.class);
        when(random.generateBoolean()).thenReturn(true, false);
        CoinToss coin = new CoinToss(random);

        CoinToss.Face first = coin.toss();
        CoinToss.Face second = coin.toss();

        assertEquals(CoinToss.Face.HEADS, first);
        assertEquals(TravelDirection.CLOCKWISE, first.getAwardedDirection());
        assertEquals(CoinToss.Face.TAILS, second);
        assertEquals(TravelDirection.COUNTER_CLOCKWISE, second.getAwardedDirection());
    }

    @Test
    void theSameSeedReplaysTheSameSequence() {
        SeededRandomnessProvider first = new SeededRandomnessProvider(42);
        SeededRandomnessProvider second = new SeededRandomnessProvider(42);

        assertEquals(drawValues(first), drawValues(second));
    }

    @Test
    void pickReturnsTheElementAtTheRandomIndex() {
        RandomnessProvider random = mock(RandomnessProvider.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(random.generateInt(3)).thenReturn(2);

        assertEquals("c", random.pick(List.of("a", "b", "c")));
    }

    @Test
    void pickingFromAnEmptyListIsRefused() {
        RandomnessProvider random = new SeededRandomnessProvider(1);

        assertThrows(IllegalArgumentException.class, () -> random.pick(List.of()));
    }

    private static List<Object> drawValues(RandomnessProvider random) {
        List<Object> values = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            values.add(random.generateInt(6));
            values.add(random.generateBoolean());
        }
        return values;
    }
}
