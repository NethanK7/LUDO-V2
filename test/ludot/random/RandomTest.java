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
import ludot.board.Direction;
import org.junit.jupiter.api.Test;

/** The dice, the coin of Rule T-1 and the seeded source of chance behind both. */
class RandomTest {

    @Test
    void diceTurnsTheZeroBasedRandomValueIntoAFaceFromOneToSix() {
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(Dice.FACES)).thenReturn(0, 5);
        Dice dice = new Dice(random);

        assertEquals(1, dice.roll());
        assertEquals(6, dice.roll());
        verify(random, times(2)).nextInt(Dice.FACES);
    }

    @Test
    void aRealRollIsAlwaysAFaceOfTheDice() {
        Dice dice = new Dice(new SeededRandomSource());

        for (int roll = 0; roll < 1000; roll++) {
            int face = dice.roll();
            assertTrue(face >= 1 && face <= Dice.FACES, "rolled " + face);
        }
    }

    @Test
    void headsMeansClockwiseAndTailsMeansCounterClockwise() {
        // Rule T-1
        RandomSource random = mock(RandomSource.class);
        when(random.nextBoolean()).thenReturn(true, false);
        Coin coin = new Coin(random);

        Coin.Face first = coin.toss();
        Coin.Face second = coin.toss();

        assertEquals(Coin.Face.HEADS, first);
        assertEquals(Direction.CLOCKWISE, first.awardedDirection());
        assertEquals(Coin.Face.TAILS, second);
        assertEquals(Direction.COUNTER_CLOCKWISE, second.awardedDirection());
    }

    @Test
    void theSameSeedReplaysTheSameSequence() {
        SeededRandomSource first = new SeededRandomSource(42);
        SeededRandomSource second = new SeededRandomSource(42);

        assertEquals(draw(first), draw(second));
    }

    @Test
    void pickReturnsTheElementAtTheRandomIndex() {
        RandomSource random = mock(RandomSource.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(random.nextInt(3)).thenReturn(2);

        assertEquals("c", random.pick(List.of("a", "b", "c")));
    }

    @Test
    void pickingFromAnEmptyListIsRefused() {
        RandomSource random = new SeededRandomSource(1);

        assertThrows(IllegalArgumentException.class, () -> random.pick(List.of()));
    }

    private static List<Object> draw(RandomSource random) {
        List<Object> values = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            values.add(random.nextInt(6));
            values.add(random.nextBoolean());
        }
        return values;
    }
}
