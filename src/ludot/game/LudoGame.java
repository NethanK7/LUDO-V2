package ludot.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import ludot.board.Board;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.mystery.MysteryCell;
import ludot.player.Player;
import ludot.ui.GameListener;

/**
 * The simulation itself: introduce the players, find out who starts, then play round after round.
 *
 * <p>This class is deliberately the shortest interesting class in the program. It knows the shape of
 * a game - rounds, turn order, the end-of-round report, when somebody has won - and delegates
 * everything else: one turn to {@link TurnEngine}, the wandering mystery cell to {@link MysteryCell}
 * and every printed line to a {@link GameListener}.
 */
public final class LudoGame {

    private final Board board;
    private final Map<PieceColour, Player> players = new LinkedHashMap<>();
    private final TurnEngine turnEngine;
    private final FirstPlayerSelector firstPlayerSelector;
    private final MysteryCell mysteryCell;
    private final GameListener log;

    /** Colours that have brought all four pieces home, in the order they managed it (Rule 11). */
    private final List<PieceColour> finishingOrder = new ArrayList<>();

    public LudoGame(Board board, List<Player> players, TurnEngine turnEngine,
            FirstPlayerSelector firstPlayerSelector, MysteryCell mysteryCell, GameListener log) {
        this.board = board;
        this.turnEngine = turnEngine;
        this.firstPlayerSelector = firstPlayerSelector;
        this.mysteryCell = mysteryCell;
        this.log = log;
        for (Player player : players) {
            this.players.put(player.colour(), player);
        }
    }

    /** Runs the whole simulation from the opening rolls to the final standings. */
    public void play() {
        introducePlayers();
        List<PieceColour> turnOrder = decideTurnOrder();

        List<Square> previousPosition = boardPosition();
        int roundsWithoutMovement = 0;
        for (int round = 1; round <= GameRules.MAX_ROUNDS; round++) {
            playRound(turnOrder);
            reportEndOfRound();

            if (finishingOrder.size() >= GameRules.PLACES_TO_DECIDE) {
                log.announceFinalStandings(placings(turnOrder));
                return;
            }

            List<Square> position = boardPosition();
            roundsWithoutMovement = position.equals(previousPosition) ? roundsWithoutMovement + 1 : 0;
            previousPosition = position;
            if (roundsWithoutMovement >= GameRules.GRIDLOCK_ROUNDS) {
                log.gameGridlocked(round, GameRules.GRIDLOCK_ROUNDS, board);
                announceStandingsDecidedSoFar();
                return;
            }
        }

        log.gameStoppedAtRoundLimit(GameRules.MAX_ROUNDS, board);
        announceStandingsDecidedSoFar();
    }

    /** When a game is cut short, only the players that really finished are given a place. */
    private void announceStandingsDecidedSoFar() {
        if (!finishingOrder.isEmpty()) {
            log.announceFinalStandings(finishingOrder);
        }
    }

    /**
     * Rule 11: "The game may continue to find second, third, and fourth places." Once three players
     * have brought every piece home, the one still playing can only be fourth.
     */
    private List<PieceColour> placings(List<PieceColour> turnOrder) {
        List<PieceColour> placings = new ArrayList<>(finishingOrder);
        for (PieceColour colour : turnOrder) {
            if (!placings.contains(colour)) {
                placings.add(colour);
            }
        }
        return placings;
    }

    /** Where every piece stands, in a fixed order, so two rounds can be compared. */
    private List<Square> boardPosition() {
        return board.allPieces().stream().map(Piece::square).toList();
    }

    /** Colours in the order they brought all four pieces home; exposed for the tests. */
    List<PieceColour> finishingOrder() {
        return List.copyOf(finishingOrder);
    }

    /** Section 3: "Before Game Begins" - one introduction line per player. */
    private void introducePlayers() {
        for (PieceColour colour : PieceColour.values()) {
            Player player = players.get(colour);
            log.introducePlayer(colour, board.piecesOf(colour));
        }
    }

    /** Section 3: "Once the First Player is Chosen" - the roll-off and the resulting order. */
    private List<PieceColour> decideTurnOrder() {
        PieceColour first = firstPlayerSelector.determineFirstPlayer();
        log.firstPlayerChosen(first);
        List<PieceColour> order = firstPlayerSelector.roundOrderStartingWith(first);
        log.roundOrder(order);
        return order;
    }

    /** "A single round is where each player rolls the dice once." */
    private void playRound(List<PieceColour> turnOrder) {
        for (PieceColour colour : turnOrder) {
            if (finishingOrder.contains(colour)) {
                // All four pieces are already home, so this player has nothing left to move.
                continue;
            }
            log.turnStarted(colour);
            turnEngine.playTurn(players.get(colour));
            recordIfFinished(colour);
            if (finishingOrder.size() >= GameRules.PLACES_TO_DECIDE) {
                return;
            }
        }
    }

    /** Rule 11: the first player home wins, and the game carries on to fill the other places. */
    private void recordIfFinished(PieceColour colour) {
        if (finishingOrder.contains(colour) || !board.hasAllPiecesHome(colour)) {
            return;
        }
        finishingOrder.add(colour);
        if (finishingOrder.size() == 1) {
            log.announceWinner(colour);
        }
    }

    /**
     * Section 3: "After Each Round, status of each player has to be shown". The mystery cell, the
     * four-round Alpha and Beta timers and blue's cycle are also advanced here, so that "a round"
     * means exactly one thing everywhere in the program.
     */
    private void reportEndOfRound() {
        log.roundEnded();
        for (PieceColour colour : PieceColour.values()) {
            log.playerPieceCounts(board, colour);
            log.pieceLocations(board, colour);
        }

        OptionalInt spawnedCell = mysteryCell.onRoundCompleted();
        spawnedCell.ifPresent(log::mysteryCellSpawned);
        log.mysteryCellStatus(mysteryCell);

        for (Piece piece : board.allPieces()) {
            piece.effects().onRoundCompleted();
        }
        players.values().forEach(Player::onRoundCompleted);
    }
}
