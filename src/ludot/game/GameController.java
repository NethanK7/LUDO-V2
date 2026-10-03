package ludot.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.PlayerStatusSnapshot;
import ludot.mystery.MysteryCellScheduler;
import ludot.player.GamePlayer;
import ludot.ui.GameEventReporter;

/** Plays the whole game: turn order, rounds, the round report and the final places. */
public final class GameController {

    private final GameBoard board;
    private final Map<PlayerColour, GamePlayer> players = new LinkedHashMap<>();
    private final TurnController turnEngine;
    private final StartingPlayerSelector firstPlayerSelector;
    private final MysteryCellScheduler mysteryCell;
    private final GameEventReporter log;

    private final List<PlayerColour> finishingOrder = new ArrayList<>();

    public GameController(GameBoard board, List<GamePlayer> players, TurnController turnEngine,
            StartingPlayerSelector firstPlayerSelector, MysteryCellScheduler mysteryCell, GameEventReporter log) {
        this.board = board;
        this.turnEngine = turnEngine;
        this.firstPlayerSelector = firstPlayerSelector;
        this.mysteryCell = mysteryCell;
        this.log = log;
        for (GamePlayer player : players) {
            this.players.put(player.getColour(), player);
        }
    }

    public GameOutcome play() {
        introducePlayers();
        List<PlayerColour> turnOrder = decideTurnOrder();

        List<BoardSquare> previousPosition = getBoardPosition();
        int roundsWithoutMovement = 0;
        for (int round = 1; round <= RuleConstants.MAX_ROUNDS; round++) {
            playRound(turnOrder);
            reportEndOfRound();

            if (finishingOrder.size() >= RuleConstants.PLACES_TO_DECIDE) {
                List<PlayerColour> placings = decidePlacings(turnOrder);
                log.announceFinalStandings(placings);
                return new GameOutcome(placings, GameOutcome.Ending.ALL_PLACES_DECIDED, round);
            }

            List<BoardSquare> position = getBoardPosition();
            roundsWithoutMovement = position.equals(previousPosition) ? roundsWithoutMovement + 1 : 0;
            previousPosition = position;
            // The board has been frozen by blocks for too long, so end the game.
            if (roundsWithoutMovement >= RuleConstants.GRIDLOCK_ROUNDS) {
                log.reportGridlock(round, RuleConstants.GRIDLOCK_ROUNDS, takeSnapshots());
                announceStandingsDecidedSoFar();
                return new GameOutcome(finishingOrder, GameOutcome.Ending.GRIDLOCK, round);
            }
        }

        log.reportRoundLimitReached(RuleConstants.MAX_ROUNDS, takeSnapshots());
        announceStandingsDecidedSoFar();
        return new GameOutcome(finishingOrder, GameOutcome.Ending.ROUND_LIMIT, RuleConstants.MAX_ROUNDS);
    }

    private List<PlayerStatusSnapshot> takeSnapshots() {
        return Arrays.stream(PlayerColour.values()).map(board::createSnapshot).toList();
    }

    private void announceStandingsDecidedSoFar() {
        if (!finishingOrder.isEmpty()) {
            log.announceFinalStandings(finishingOrder);
        }
    }

    // Rule 11: once three players are home, the fourth place is decided.
    private List<PlayerColour> decidePlacings(List<PlayerColour> turnOrder) {
        Stream<PlayerColour> stillPlaying = turnOrder.stream().filter(c -> !finishingOrder.contains(c));
        return Stream.concat(finishingOrder.stream(), stillPlaying).toList();
    }

    private List<BoardSquare> getBoardPosition() {
        return board.getAllPieces().stream().map(GamePiece::getSquare).toList();
    }

    private void introducePlayers() {
        for (PlayerColour colour : PlayerColour.values()) {
            log.introducePlayer(colour, board.getPiecesOf(colour));
        }
    }

    private List<PlayerColour> decideTurnOrder() {
        PlayerColour first = firstPlayerSelector.determineFirstPlayer();
        log.reportFirstPlayer(first);
        List<PlayerColour> order = firstPlayerSelector.buildRoundOrder(first);
        log.reportRoundOrder(order);
        return order;
    }

    private void playRound(List<PlayerColour> turnOrder) {
        for (PlayerColour colour : turnOrder) {
            if (finishingOrder.contains(colour)) {
                continue;
            }
            log.reportTurnStart(colour);
            turnEngine.playTurn(players.get(colour));
            recordIfFinished(colour);
            if (finishingOrder.size() >= RuleConstants.PLACES_TO_DECIDE) {
                return;
            }
        }
    }

    private void recordIfFinished(PlayerColour colour) {
        if (!board.hasAllPiecesHome(colour)) {
            return;
        }
        finishingOrder.add(colour);
        if (finishingOrder.size() == 1) {
            log.announceWinner(colour);
        }
    }

    private void reportEndOfRound() {
        log.reportRoundEnd();
        for (PlayerStatusSnapshot status : takeSnapshots()) {
            log.reportPieceCounts(status);
            log.reportPieceLocations(status);
        }

        OptionalInt spawnedCell = mysteryCell.finishRound();
        spawnedCell.ifPresent(log::reportMysteryCellSpawn);
        log.reportMysteryCellStatus(mysteryCell);

        for (GamePiece piece : board.getAllPieces()) {
            piece.getEffects().finishRound();
        }
        players.values().forEach(GamePlayer::finishRound);
    }
}
