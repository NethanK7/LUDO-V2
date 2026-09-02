import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import ludot.board.Board;
import ludot.board.BoardGeometry;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.BlockedAttempt;
import ludot.movement.MoveExecutor;
import ludot.movement.MoveGenerator;
import ludot.movement.MoveOptions;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.piece.Piece;
import ludot.piece.SpeedModifier;
import ludot.random.Coin;
import ludot.random.RandomSource;
import ludot.ui.GameLog;

/**
 * Deterministic checks for the rules that are easy to get wrong.
 *
 * <p>These are not part of the simulation. They set up an exact board position, ask the rule classes
 * one question, and compare the answer with the rule book. Because every source of chance in the
 * program goes through {@link RandomSource}, and every printed line through {@link GameLog}, a test
 * can run the real rule classes with no randomness and no output at all.
 *
 * <pre>
 *   javac -d out $(find src test -name '*.java')
 *   java -cp out RuleChecks
 * </pre>
 */
public final class RuleChecks {

    private static int checksRun;
    private static int checksFailed;

    public static void main(String[] args) {
        checkBoardGeometry();
        checkClockwiseJourneyLength();
        checkCounterClockwiseJourneyLength();
        checkRuleT7HoldsAPieceOutOfItsHomeStraight();
        checkRule10NeedsAnExactRoll();
        checkRuleT3StopsAPieceBeforeTheBlock();
        checkRuleT3LetsAPieceJumpALonePiece();
        checkRuleT8LetsAnEqualBlockadeCaptureABlockade();
        checkRuleT8RefusesAnUnequalBlockade();
        checkRuleT4DividesTheRollAndPicksTheDirection();
        checkRuleT12SpeedModifiers();
        checkRuleT9ResetsACapturedPiece();
        checkRuleT14TurnsAClockwisePieceAround();
        checkMysteryCellSpawnRules();

        System.out.printf("%n%d checks run, %d failed.%n", checksRun, checksFailed);
        if (checksFailed > 0) {
            throw new AssertionError(checksFailed + " rule check(s) failed");
        }
    }

    // ------------------------------------------------------------------------------- the checks

    /** The Legend of the specification, read off Figure 1. */
    private static void checkBoardGeometry() {
        expect("yellow starts on cell 0", 0, PieceColour.YELLOW.startCell());
        expect("blue starts on cell 13", 13, PieceColour.BLUE.startCell());
        expect("red starts on cell 26", 26, PieceColour.RED.startCell());
        expect("green starts on cell 39", 39, PieceColour.GREEN.startCell());

        expect("yellow approach is cell 50", 50, PieceColour.YELLOW.approachCell());
        expect("blue approach is cell 11", 11, PieceColour.BLUE.approachCell());
        expect("red approach is cell 24", 24, PieceColour.RED.approachCell());
        expect("green approach is cell 37", 37, PieceColour.GREEN.approachCell());

        // Rule T-11: the 9th, 27th and 46th cell from the yellow approach cell (which counts as 0).
        expect("Alpha is cell 7", 7, BoardGeometry.ALPHA_CELL);
        expect("Beta is cell 25", 25, BoardGeometry.BETA_CELL);
        expect("Gamma is cell 44", 44, BoardGeometry.GAMMA_CELL);

        // "if R rolled the dice, the next player to roll would be G"
        expect("red passes the dice to green", PieceColour.GREEN,
                PieceColour.RED.nextInTurnOrder());
    }

    /** 50 cells from X to the approach cell, then 5 home-straight cells, then home. */
    private static void checkClockwiseJourneyLength() {
        Board board = new Board();
        PathResolver resolver = new PathResolver(board);
        Piece piece = place(board, PieceColour.YELLOW, 1, 0, Direction.CLOCKWISE, 1);
        expect("a clockwise piece is 56 cells from home at X", 56, resolver.distanceToHome(piece));
    }

    /** Rule T-1: counter-clockwise, the approach cell must be passed a second time. */
    private static void checkCounterClockwiseJourneyLength() {
        Board board = new Board();
        PathResolver resolver = new PathResolver(board);
        Piece piece = place(board, PieceColour.YELLOW, 1, 0, Direction.COUNTER_CLOCKWISE, 1);
        expect("a counter-clockwise piece is 60 cells from home at X", 60,
                resolver.distanceToHome(piece));
    }

    /** Rule T-7: without a capture the piece walks straight past its own approach cell. */
    private static void checkRuleT7HoldsAPieceOutOfItsHomeStraight() {
        Board board = new Board();
        PathResolver resolver = new PathResolver(board);

        Piece withoutCapture = place(board, PieceColour.YELLOW, 1, 48, Direction.CLOCKWISE, 0);
        PathResolver.Walk blockedOut = resolver.walk(withoutCapture, Direction.CLOCKWISE, 3, 1);
        expect("a piece with no capture stays on the ring", Square.ring(51),
                blockedOut.destination());

        Piece withCapture = place(board, PieceColour.YELLOW, 2, 48, Direction.CLOCKWISE, 1);
        PathResolver.Walk allowedIn = resolver.walk(withCapture, Direction.CLOCKWISE, 3, 1);
        expect("a piece that has captured turns into its home straight",
                Square.homeStraight(PieceColour.YELLOW, 0), allowedIn.destination());
    }

    /** Rule 10: "the player must roll the exact number to reach home". */
    private static void checkRule10NeedsAnExactRoll() {
        Board board = new Board();
        PathResolver resolver = new PathResolver(board);
        Piece piece = board.piecesOf(PieceColour.YELLOW).get(0);
        piece.assignStartingDirection(Direction.CLOCKWISE);
        piece.recordCapture();
        board.relocate(piece, Square.homeStraight(PieceColour.YELLOW, 3));

        expect("the exact roll reaches home", Square.home(PieceColour.YELLOW),
                resolver.walk(piece, Direction.CLOCKWISE, 2, 1).destination());
        expect("too large a roll cannot be played", PathResolver.Outcome.IMPOSSIBLE,
                resolver.walk(piece, Direction.CLOCKWISE, 3, 1).outcome());
    }

    /**
     * The worked example of Rule T-3: "let's assume G1 is in cell 0, and there is a block by R1 and
     * R2 in cell 4. If G rolls 6, that would take him to cell 6, which G1 cannot do due to the
     * block. In such a case, G1 can move up until cell 3."
     */
    private static void checkRuleT3StopsAPieceBeforeTheBlock() {
        Board board = new Board();
        MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));

        place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);

        // A six also offers to bring a piece out of the base; the worked example is only about the
        // piece already on the path, so base entries are set aside here.
        MoveOptions options = generator.optionsFor(PieceColour.GREEN, 6);
        expect("G1 has no playable move past the block", 0, advances(options).size());
        expect("the block is reported", 1, options.blockedAttempts().size());

        BlockedAttempt attempt = options.blockedAttempts().get(0);
        expect("the intended destination was cell 6", Square.ring(6),
                attempt.intendedDestination());
        expect("the block is identified", "R1", attempt.blockingPiece().name());
        expect("G1 may move up to cell 3", Square.ring(3), attempt.partialMove().destination());
    }

    /** Rule 5: a lone opponent piece is jumped over, not blocked, and can be captured. */
    private static void checkRuleT3LetsAPieceJumpALonePiece() {
        Board board = new Board();
        MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));

        place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);

        List<PlannedMove> moves = advances(generator.optionsFor(PieceColour.GREEN, 6));
        expect("a lone piece does not block the way", 1, moves.size());
        expect("the piece lands beyond it", Square.ring(6), moves.get(0).destination());
        expect("nothing is captured on the way past", false, moves.get(0).capturesAnything());
    }

    /** Rule T-8: "A blockade of the same size can capture a blockade." */
    private static void checkRuleT8LetsAnEqualBlockadeCaptureABlockade() {
        Board board = new Board();
        MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));

        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 12, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 12, Direction.CLOCKWISE, 0);

        PlannedMove blockMove = onlyBlockMove(generator.optionsFor(PieceColour.YELLOW, 4));
        expect("the block moves roll / size = 2 cells", 2, blockMove.stepsTaken());
        expect("it lands on the opposing blockade", Square.ring(12), blockMove.destination());
        expect("both opponent pieces are captured", 2, blockMove.capturedPieces().size());
    }

    /** Rule T-8 only allows equal sizes, so a pair cannot walk onto a trio. */
    private static void checkRuleT8RefusesAnUnequalBlockade() {
        Board board = new Board();
        MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));

        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 12, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 12, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 3, 12, Direction.CLOCKWISE, 0);

        MoveOptions options = generator.optionsFor(PieceColour.YELLOW, 4);
        expect("a pair cannot capture a trio", 0, blockMoves(options).size());
    }

    /**
     * Rule T-4: the roll is divided by the size of the block, and a block whose pieces disagree
     * travels "in the direction of the longest distance from home".
     */
    private static void checkRuleT4DividesTheRollAndPicksTheDirection() {
        Board board = new Board();
        MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));
        PathResolver resolver = new PathResolver(board);

        // Yellow's approach cell is 50, so from cell 20 the clockwise piece has 36 cells to go
        // while the counter-clockwise one has to lap the board and has far more.
        Piece clockwise = place(board, PieceColour.YELLOW, 1, 20, Direction.CLOCKWISE, 1);
        Piece counterClockwise =
                place(board, PieceColour.YELLOW, 2, 20, Direction.COUNTER_CLOCKWISE, 1);
        expect("the counter-clockwise piece is further from home", true,
                resolver.distanceToHome(counterClockwise) > resolver.distanceToHome(clockwise));

        PlannedMove blockMove = onlyBlockMove(generator.optionsFor(PieceColour.YELLOW, 6));
        expect("a block of two moves 6 / 2 = 3 cells", 3, blockMove.stepsTaken());
        expect("the block follows the longest distance from home", Direction.COUNTER_CLOCKWISE,
                blockMove.direction());
        expect("so it travels backwards to cell 17", Square.ring(17), blockMove.destination());
        expect("and both pieces travel together", 2, blockMove.groupSize());
    }

    /** Rule T-12: the Alpha aura doubles or halves the distance travelled. */
    private static void checkRuleT12SpeedModifiers() {
        Board board = new Board();
        Piece piece = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);

        expect("a normal piece moves the face value", 5, piece.effects().adjustRoll(5));
        piece.effects().applyAlphaAura(SpeedModifier.DOUBLED);
        expect("an energised piece doubles it", 10, piece.effects().adjustRoll(5));
        piece.effects().applyAlphaAura(SpeedModifier.HALVED);
        expect("a sick piece halves it", 2, piece.effects().adjustRoll(5));

        for (int round = 0; round < 4; round++) {
            piece.effects().onRoundCompleted();
        }
        expect("the aura lasts exactly four rounds", 5, piece.effects().adjustRoll(5));
    }

    /** Rules 6 and T-9: the captured piece goes back to base and loses everything it carried. */
    private static void checkRuleT9ResetsACapturedPiece() {
        Board board = new Board();
        MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));
        MoveExecutor executor = executorFor(board);

        Piece attacker = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        Piece victim = place(board, PieceColour.GREEN, 1, 29, Direction.CLOCKWISE, 2);
        victim.effects().applyAlphaAura(SpeedModifier.DOUBLED);
        victim.setApproachPasses(1);

        PlannedMove capture = generator.optionsFor(PieceColour.RED, 3).playableMoves().get(0);
        expect("the move is a capture", true, capture.capturesAnything());
        executor.execute(capture);

        expect("the victim is back in its base", Square.base(PieceColour.GREEN), victim.square());
        expect("its captures are reset", 0, victim.captureCount());
        expect("its approach passes are reset", 0, victim.approachPasses());
        expect("its aura is reset", 5, victim.effects().adjustRoll(5));
        expect("its direction is reset", null, victim.direction());
        expect("the attacker is credited with the capture", 1, attacker.captureCount());
        expect("the attacker may now enter its home straight", true,
                attacker.hasEarnedHomeStraightEntry());
    }

    /** Rule T-14: a clockwise piece teleported to Gamma turns around. */
    private static void checkRuleT14TurnsAClockwisePieceAround() {
        Board board = new Board();
        // A random source that always answers "the third option", i.e. Gamma of the six.
        RandomSource alwaysGamma = new RandomSource() {
            @Override
            public int nextInt(int boundExclusive) {
                return 2;
            }

            @Override
            public boolean nextBoolean() {
                return true;
            }
        };
        MysteryEffectResolver resolver =
                new MysteryEffectResolver(board, alwaysGamma, silentLog());

        Piece piece = place(board, PieceColour.RED, 1, 30, Direction.CLOCKWISE, 0);
        resolver.resolveLandingOnMysteryCell(piece);
        expect("the piece is standing on Gamma", Square.ring(BoardGeometry.GAMMA_CELL),
                piece.square());
        expect("and is now travelling counter-clockwise", Direction.COUNTER_CLOCKWISE,
                piece.direction());

        Piece alreadyCounterClockwise =
                place(board, PieceColour.RED, 2, 30, Direction.COUNTER_CLOCKWISE, 0);
        resolver.resolveLandingOnMysteryCell(alreadyCounterClockwise);
        expect("a counter-clockwise piece is sent on to Beta",
                Square.ring(BoardGeometry.BETA_CELL), alreadyCounterClockwise.square());
        expect("and has to attend the briefing", true,
                alreadyCounterClockwise.effects().isAttendingBriefing());
    }

    /** Rule T-10: not before two rounds, never twice in the same place, four rounds each time. */
    private static void checkMysteryCellSpawnRules() {
        Board board = new Board();
        RandomSource firstFreeCell = new RandomSource() {
            @Override
            public int nextInt(int boundExclusive) {
                return 0;
            }

            @Override
            public boolean nextBoolean() {
                return true;
            }
        };
        MysteryCell mysteryCell = new MysteryCell(board, firstFreeCell);

        mysteryCell.onRoundCompleted();
        expect("no mystery cell while every piece is in its base", false, mysteryCell.isActive());

        // Put a piece on the standard path so the two-round countdown can start.
        place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        expect("still nothing after one round with pieces on the path", null,
                mysteryCell.onRoundCompleted());
        Integer firstSpawn = mysteryCell.onRoundCompleted();
        expect("it spawns after the second round", Integer.valueOf(0), firstSpawn);
        expect("and stays for four rounds", 4, mysteryCell.roundsRemaining());

        for (int round = 0; round < 3; round++) {
            expect("it does not move early", null, mysteryCell.onRoundCompleted());
        }
        Integer secondSpawn = mysteryCell.onRoundCompleted();
        expect("it reappears on the fourth round", true, secondSpawn != null);
        expect("never in the same place twice in a row", true, secondSpawn != 0);
        expect("and never on an occupied cell", true, secondSpawn != 26);
    }

    // ------------------------------------------------------------------------------- test tools

    /** Puts a piece on a standard cell with a direction and a capture history. */
    private static Piece place(Board board, PieceColour colour, int number, int cell,
            Direction direction, int captures) {
        Piece piece = board.piecesOf(colour).get(number - 1);
        board.relocate(piece, Square.ring(cell));
        piece.assignStartingDirection(direction);
        for (int index = 0; index < captures; index++) {
            piece.recordCapture();
        }
        return piece;
    }

    /** The moves of pieces already on the board, i.e. everything but a base entry. */
    private static List<PlannedMove> advances(MoveOptions options) {
        return options.playableMoves().stream().filter(move -> !move.isEnteringBoard()).toList();
    }

    private static List<PlannedMove> blockMoves(MoveOptions options) {
        return options.playableMoves().stream().filter(PlannedMove::isBlockMove).toList();
    }

    private static PlannedMove onlyBlockMove(MoveOptions options) {
        List<PlannedMove> blockMoves = blockMoves(options);
        if (blockMoves.size() != 1) {
            throw new AssertionError("expected exactly one block move, found " + blockMoves.size());
        }
        return blockMoves.get(0);
    }

    private static MoveExecutor executorFor(Board board) {
        RandomSource heads = new RandomSource() {
            @Override
            public int nextInt(int boundExclusive) {
                return 0;
            }

            @Override
            public boolean nextBoolean() {
                return true;
            }
        };
        GameLog log = silentLog();
        return new MoveExecutor(board, new Coin(heads), new MysteryCell(board, heads),
                new MysteryEffectResolver(board, heads, log), log);
    }

    /** A log that throws its output away, so the checks stay readable. */
    private static GameLog silentLog() {
        return new GameLog(new PrintStream(new ByteArrayOutputStream()));
    }

    private static void expect(String description, Object expected, Object actual) {
        checksRun++;
        boolean passed = expected == null ? actual == null : expected.equals(actual);
        if (!passed) {
            checksFailed++;
            System.out.printf("FAIL  %s%n        expected <%s> but was <%s>%n", description,
                    expected, actual);
        } else {
            System.out.printf("pass  %s%n", description);
        }
    }
}
