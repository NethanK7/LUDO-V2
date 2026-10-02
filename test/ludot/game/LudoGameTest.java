package ludot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.List;
import ludot.LudoTSimulation;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.MoveExecutor;
import ludot.movement.MoveGenerator;
import ludot.movement.PathResolver;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.player.PlayerFactory;
import ludot.random.Coin;
import ludot.random.Dice;
import ludot.random.SeededRandomSource;
import ludot.ui.GameListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/** Whole games, played with a seeded source of chance and a mocked listener. */
class LudoGameTest {

    private final GameListener listener = mock(GameListener.class);

    private LudoGame gameWithSeed(long seed) {
        SeededRandomSource random = new SeededRandomSource(seed);
        Board board = new Board();
        PathResolver pathResolver = new PathResolver(board);
        MysteryCell mysteryCell = new MysteryCell(board, random);
        Dice dice = new Dice(random);
        MoveExecutor executor = new MoveExecutor(board, new Coin(random), mysteryCell,
                new MysteryEffectResolver(board, random, listener), listener);
        TurnEngine turnEngine = new TurnEngine(board, dice, new MoveGenerator(board, pathResolver),
                executor, pathResolver, listener);
        return new LudoGame(board, new PlayerFactory(board, pathResolver, mysteryCell).createAll(),
                turnEngine, new FirstPlayerSelector(dice, listener), mysteryCell, listener);
    }

    @Test
    void aGameIntroducesAllFourPlayersAndDecidesAllFourPlaces() {
        // Rule 11: three finish, the fourth is last by elimination
        LudoGame game = gameWithSeed(42);

        game.play();

        verify(listener, times(4)).introducePlayer(any(), any(), any());
        verify(listener).firstPlayerChosen(any());
        verify(listener).announceWinner(game.finishingOrder().get(0));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PieceColour>> placings = ArgumentCaptor.forClass(List.class);
        verify(listener).announceFinalStandings(placings.capture());
        assertEquals(EnumSet.allOf(PieceColour.class), EnumSet.copyOf(placings.getValue()));
        assertEquals(4, placings.getValue().size());
        assertEquals(game.finishingOrder(), placings.getValue().subList(0, 3));
        verify(listener, never()).gameStoppedAtRoundLimit(anyInt(), any());
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void everySeededGameFinishesWellInsideTheSafetyLimit(long seed) {
        gameWithSeed(seed).play();

        verify(listener, never()).gameStoppedAtRoundLimit(anyInt(), any());
        verify(listener, never()).gameGridlocked(anyInt(), anyInt(), any());
        verify(listener).announceWinner(any());
    }

    @Test
    void aGridlockedBoardEndsTheGameInsteadOfRunningToTheSafetyLimit() {
        // seed 79 ends with blocks of every colour on cells 49, 50, 51, 0 and 1
        gameWithSeed(79).play();

        verify(listener).gameGridlocked(anyInt(), eq(GameRules.GRIDLOCK_ROUNDS), any());
        verify(listener, never()).gameStoppedAtRoundLimit(anyInt(), any());
    }

    @Test
    void theSameSeedReplaysTheSameGameWordForWord() {
        assertEquals(transcript(7), transcript(7));
    }

    @Test
    void differentSeedsPlayDifferentGames() {
        assertFalse(transcript(7).equals(transcript(8)));
    }

    @Test
    void theTranscriptContainsTheRequiredOpeningAndClosingMessages() {
        String transcript = transcript(42);

        assertTrue(transcript.contains("The red player has four (04) pieces named R1, R2, R3, and R4."));
        assertTrue(transcript.contains("player has the highest roll and will begin the game."));
        assertTrue(transcript.contains("The order of a single round is "));
        assertTrue(transcript.contains("player wins!!!"));
        assertTrue(transcript.contains("4th place: "));
    }

    private static String transcript(long seed) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new LudoTSimulation(new SeededRandomSource(seed), new PrintStream(bytes)).run();
        return bytes.toString();
    }
}
