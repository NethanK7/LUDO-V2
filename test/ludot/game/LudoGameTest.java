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
import ludot.LudoTSimulation;
import ludot.board.Board;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.command.CommandFactory;
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
import org.mockito.ArgumentCaptor;

/** Whole games, played with a seeded source of chance and a mocked listener. */
class LudoGameTest {

    private final GameListener listener = mock(GameListener.class);

    private LudoGame gameWithSeed(long seed) {
        return gameWithSeed(seed, listener);
    }

    private static LudoGame gameWithSeed(long seed, GameListener listener) {
        SeededRandomSource random = new SeededRandomSource(seed);
        Board board = new Board();
        PathResolver pathResolver = new PathResolver(board);
        MysteryCell mysteryCell = new MysteryCell(board, random);
        Dice dice = new Dice(random);
        CommandFactory commands = new CommandFactory(board, new Coin(random), mysteryCell,
                new MysteryEffectResolver(board, random, listener), listener);
        TurnEngine turnEngine = new TurnEngine(board, dice, new MoveGenerator(board, pathResolver),
                commands, pathResolver, listener);
        return new LudoGame(board, new PlayerFactory(board, pathResolver, mysteryCell).createAll(),
                turnEngine, new FirstPlayerSelector(dice, listener), mysteryCell, listener);
    }

    @Test
    void aGameIntroducesAllFourPlayersAndDecidesAllFourPlaces() {
        // Rule 11: three finish, the fourth is last by elimination
        GameResult result = gameWithSeed(42).play();

        assertEquals(GameResult.Ending.ALL_PLACES_DECIDED, result.ending());
        verify(listener, times(4)).introducePlayer(any(), any());
        verify(listener).firstPlayerChosen(any());
        verify(listener).announceWinner(result.placings().get(0));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PieceColour>> placings = ArgumentCaptor.forClass(List.class);
        verify(listener).announceFinalStandings(placings.capture());
        assertEquals(EnumSet.allOf(PieceColour.class), EnumSet.copyOf(placings.getValue()));
        assertEquals(4, placings.getValue().size());
        assertEquals(result.placings(), placings.getValue());
        verify(listener, never()).gameStoppedAtRoundLimit(anyInt(), any());
    }

    @Test
    void twelveSeededGamesAllFinishWithAWinnerAndFourPlaces() {
        for (long seed = 1; seed <= 12; seed++) {
            GameListener ownListener = mock(GameListener.class);

            GameResult result = gameWithSeed(seed, ownListener).play();

            assertEquals(GameResult.Ending.ALL_PLACES_DECIDED, result.ending(), "seed " + seed);
            assertEquals(4, result.placings().size(), "seed " + seed);
            verify(ownListener).announceWinner(result.placings().get(0));
        }
    }

    @Test
    void aGridlockedBoardEndsTheGameInsteadOfRunningToTheSafetyLimit() {
        // seed 79 ends with blocks of every colour on cells 49, 50, 51, 0 and 1
        GameResult result = gameWithSeed(79).play();

        assertEquals(GameResult.Ending.GRIDLOCK, result.ending());
        assertTrue(result.placings().isEmpty());
        verify(listener).gameGridlocked(anyInt(), eq(GameRules.GRIDLOCK_ROUNDS), any());
        verify(listener, never()).gameStoppedAtRoundLimit(anyInt(), any());
        // nobody got a piece home in this game, so there are no places to announce
        verify(listener, never()).announceFinalStandings(any());
    }

    @Test
    void theSameSeedReplaysTheSameGameWordForWord() {
        assertEquals(transcript(7), transcript(7));
    }



    private static String transcript(long seed) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new LudoTSimulation(new SeededRandomSource(seed), new PrintStream(bytes)).run();
        return bytes.toString();
    }

    @Test
    void aGameThatNeverSettlesStopsAtTheSafetyLimit() {
        // Yellow is already home; every other turn just shuffles R1 between cells 26 and 27, so the
        // board keeps changing (no gridlock) but nobody else can ever finish.
        Board board = new Board();
        for (Piece piece : board.piecesOf(PieceColour.YELLOW)) {
            board.relocate(piece, Square.home(PieceColour.YELLOW));
        }
        Piece shuffled = board.piecesOf(PieceColour.RED).get(0);
        TurnEngine turnEngine = mock(TurnEngine.class);
        doAnswer(turn -> {
            boolean onCell26 = shuffled.square().equals(Square.ring(26));
            board.relocate(shuffled, Square.ring(onCell26 ? 27 : 26));
            return null;
        }).when(turnEngine).playTurn(any());
        Dice dice = mock(Dice.class);
        when(dice.roll()).thenReturn(6, 1, 1, 1);
        PathResolver pathResolver = new PathResolver(board);
        MysteryCell mysteryCell = new MysteryCell(board, new SeededRandomSource(1));
        LudoGame game = new LudoGame(board,
                new PlayerFactory(board, pathResolver, mysteryCell).createAll(), turnEngine,
                new FirstPlayerSelector(dice, listener), mysteryCell, listener);

        GameResult result = game.play();

        assertEquals(GameResult.Ending.ROUND_LIMIT, result.ending());
        assertEquals(GameRules.MAX_ROUNDS, result.rounds());
        assertEquals(List.of(PieceColour.YELLOW), result.placings());
        verify(listener).announceWinner(PieceColour.YELLOW);
        verify(listener).gameStoppedAtRoundLimit(eq(GameRules.MAX_ROUNDS), any());
        verify(listener).announceFinalStandings(List.of(PieceColour.YELLOW));
    }
}
