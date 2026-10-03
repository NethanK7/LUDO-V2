package ludot.ui;

import java.util.List;
import ludot.board.Board;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.effects.SpeedModifier;
import ludot.movement.BlockedAttempt;
import ludot.movement.PieceMovement;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.TeleportDestination;

/**
 * Everything that can happen during a game that somebody might want to hear about.
 *
 * <p>The rule classes report <em>what happened</em> through this interface and never decide how it is
 * shown. {@link GameLog} turns each event into the status message required by Section&nbsp;3, while a
 * unit test can plug in a mock and simply verify that the right event was raised. This is the
 * Observer pattern, and it is also what keeps the rules independent of the console (Dependency
 * Inversion): nothing outside {@code ludot.ui} knows that output goes to a {@code PrintStream}.
 */
public interface GameListener {

    // ---------------------------------------------------------------- before the game begins

    void introducePlayer(PieceColour colour, List<Piece> pieces);

    void openingRoll(PieceColour colour, int value);

    void firstPlayerChosen(PieceColour colour);

    void roundOrder(List<PieceColour> order);

    // ---------------------------------------------------------------- rounds and turns

    void turnStarted(PieceColour colour);

    void diceRolled(PieceColour colour, int value);

    // ---------------------------------------------------------------- moving

    void movesToStartingPoint(Piece piece);

    void movesPiece(PieceMovement movement);

    // ---------------------------------------------------------------- blocks

    void pieceIsBlocked(BlockedAttempt attempt);

    void blockedWithNothingElseToMove(PieceColour colour);

    void blockedButMovedUpToTheBlock(PieceColour colour, PlannedMove partialMove);

    // ---------------------------------------------------------------- captures

    void capture(Piece capturer, Piece captured, String squareLabel);

    // ---------------------------------------------------------------- mystery cell

    void mysteryCellSpawned(int cell);

    void mysteryCellStatus(MysteryCell mysteryCell);

    void landsOnMysteryCell(Piece piece, TeleportDestination destination);

    void teleported(Piece piece, TeleportDestination destination);

    void alphaAura(Piece piece, SpeedModifier modifier);

    void betaBriefing(Piece piece);

    void briefingEndedByConsecutiveThrees(Piece piece);

    void gammaTurnedPieceAround(Piece piece);

    void gammaSendsPieceToBeta(Piece piece);

    // ---------------------------------------------------------------- status and results

    void playerPieceCounts(Board board, PieceColour colour);

    void pieceLocations(Board board, PieceColour colour);

    /** Raised once every player has had its turn, just before the end-of-round report. */
    void roundEnded();

    void announceWinner(PieceColour colour);

    void announceFinalStandings(List<PieceColour> placings);

    void gameStoppedAtRoundLimit(int roundLimit, Board board);

    /** No piece has changed square for {@code stillRounds} rounds, so the game cannot go on. */
    void gameGridlocked(int round, int stillRounds, Board board);
}
