package ludot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.List;
import ludot.LudoSimulationFacade;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.command.MoveCommandFactory;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.player.GamePlayerFactory;
import ludot.random.CoinToss;
import ludot.random.SeededRandomnessProvider;
import ludot.random.SixSidedDie;
import ludot.ui.GameEventReporter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests that play whole games and check how they end. */
class GameControllerTest {

    private final GameEventReporter listener = mock(GameEventReporter.class);

    private GameController createGame(long seed) {
        return createGame(seed, listener);
    }

    private static GameController createGame(long seed, GameEventReporter listener) {
        SeededRandomnessProvider random = new SeededRandomnessProvider(seed);
        GameBoard board = new GameBoard();
        PathNavigator pathCalculator = new PathNavigator(board);
        MysteryCellScheduler mysteryCell = new MysteryCellScheduler(board, random);
        SixSidedDie dice = new SixSidedDie(random);
        MoveCommandFactory commands = new MoveCommandFactory(board, new CoinToss(random), mysteryCell,
                new TeleportService(board, random, listener), listener);
        TurnController turnEngine = new TurnController(board, dice, new MoveOptionFinder(board, pathCalculator),
                commands, pathCalculator, listener);
        return new GameController(board, new GamePlayerFactory(board, pathCalculator, mysteryCell).createAll(),
                turnEngine, new StartingPlayerSelector(dice, listener), mysteryCell, listener);
    }

    @Test
    void aGameIntroducesAllFourPlayersAndDecidesAllFourPlaces() {
        GameOutcome result = createGame(42).play();

        assertEquals(GameOutcome.Ending.ALL_PLACES_DECIDED, result.ending());
        assertEquals(List.of(PlayerColour.YELLOW, PlayerColour.RED, PlayerColour.BLUE, PlayerColour.GREEN),
                result.placings());
        verify(listener, times(4)).introducePlayer(any(), any());
        verify(listener).reportFirstPlayer(any());
        verify(listener).announceWinner(result.placings().get(0));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlayerColour>> placings = ArgumentCaptor.forClass(List.class);
        verify(listener).announceFinalStandings(placings.capture());
        assertEquals(EnumSet.allOf(PlayerColour.class), EnumSet.copyOf(placings.getValue()));
        assertEquals(4, placings.getValue().size());
        assertEquals(result.placings(), placings.getValue());
        verify(listener, never()).reportRoundLimitReached(anyInt(), any());
    }

    @Test
    void twelveSeededGamesAllFinishWithAWinnerAndFourPlaces() {
        for (long seed = 1; seed <= 12; seed++) {
            GameEventReporter ownListener = mock(GameEventReporter.class);

            GameOutcome result = createGame(seed, ownListener).play();

            assertEquals(GameOutcome.Ending.ALL_PLACES_DECIDED, result.ending(), "seed " + seed);
            assertEquals(4, result.placings().size(), "seed " + seed);
            verify(ownListener).announceWinner(result.placings().get(0));
        }
    }

    @Test
    void aGridlockedBoardEndsTheGameInsteadOfRunningToTheSafetyLimit() {
        GameOutcome result = createGame(79).play();

        assertEquals(GameOutcome.Ending.GRIDLOCK, result.ending());
        assertTrue(result.placings().isEmpty());
        verify(listener).reportGridlock(anyInt(), eq(RuleConstants.GRIDLOCK_ROUNDS), any());
        verify(listener, never()).reportRoundLimitReached(anyInt(), any());
        verify(listener, never()).announceFinalStandings(any());
    }

    @Test
    void seed42AlwaysPlaysTheSameGame() {
        String transcript = recordTranscript(42);

        assertTrue(transcript.contains("yellow rolls 3\nblue rolls 4\nred rolls 1\ngreen rolls 3\n"));
        assertTrue(transcript.contains("blue player has the highest roll and will begin the game."));
        assertTrue(transcript.contains("The order of a single round is blue, red, green, and yellow."));
        assertEquals(1, transcript.lines().filter(line -> line.endsWith("player wins!!!")).count());
        assertTrue(transcript.contains("yellow player wins!!!"));
        assertTrue(transcript.endsWith("1st place: yellow\n2nd place: red\n3rd place: blue\n4th place: green\n"));
        assertEquals(218, transcript.lines().filter(line -> line.equals("Location of pieces yellow")).count());
    }

    @Test
    void theSameSeedReplaysTheSameGameWordForWord() {
        assertEquals(recordTranscript(7), recordTranscript(7));
    }

    private static String recordTranscript(long seed) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new LudoSimulationFacade(new SeededRandomnessProvider(seed), new PrintStream(bytes)).run();
        return bytes.toString();
    }

    @Test
    void aGameThatNeverSettlesStopsAtTheSafetyLimit() {
        GameBoard board = new GameBoard();
        for (GamePiece piece : board.getPiecesOf(PlayerColour.YELLOW)) {
            board.relocate(piece, BoardSquare.ofHome(PlayerColour.YELLOW));
        }
        GamePiece shuffled = board.getPiecesOf(PlayerColour.RED).get(0);
        TurnController turnEngine = mock(TurnController.class);
        doAnswer(turn -> {
            boolean onCell26 = shuffled.getSquare().equals(BoardSquare.ofRing(26));
            board.relocate(shuffled, BoardSquare.ofRing(onCell26 ? 27 : 26));
            return null;
        }).when(turnEngine).playTurn(any());
        SixSidedDie dice = mock(SixSidedDie.class);
        when(dice.roll()).thenReturn(6, 1, 1, 1);
        PathNavigator pathCalculator = new PathNavigator(board);
        MysteryCellScheduler mysteryCell = new MysteryCellScheduler(board, new SeededRandomnessProvider(1));
        GameController game = new GameController(board,
                new GamePlayerFactory(board, pathCalculator, mysteryCell).createAll(), turnEngine,
                new StartingPlayerSelector(dice, listener), mysteryCell, listener);

        GameOutcome result = game.play();

        assertEquals(GameOutcome.Ending.ROUND_LIMIT, result.ending());
        assertEquals(RuleConstants.MAX_ROUNDS, result.rounds());
        assertEquals(List.of(PlayerColour.YELLOW), result.placings());
        verify(listener).announceWinner(PlayerColour.YELLOW);
        verify(listener).reportRoundLimitReached(eq(RuleConstants.MAX_ROUNDS), any());
        verify(listener).announceFinalStandings(List.of(PlayerColour.YELLOW));
    }
}
