package com.tictactoe;

import com.tictactoe.enums.GameStatus;
import com.tictactoe.enums.Mark;
import com.tictactoe.exception.GameOverException;
import com.tictactoe.exception.InvalidMoveException;
import com.tictactoe.game.Game;
import com.tictactoe.model.Player;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable checks for core tic-tac-toe behavior (no external test framework).
 * Exits with a non-zero code if any check fails.
 */
public final class TicTacToeVerification {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        checkRowWin();
        checkColumnWin();
        checkMainDiagonalWin();
        checkAntiDiagonalWin();
        checkOWins();
        checkDraw();
        checkWinOnFinalMoveBeatsDraw();
        checkTurnAlternation();
        checkOccupiedCellRejectedWithoutSwitchingTurn();
        checkOutOfBoundsRejected();
        checkMoveAfterGameOverRejected();
        checkPlayerValidation();
        checkBoardSizeValidation();
        checkOneByOneBoard();
        checkLargerBoard();
        checkConcurrentMovesStayConsistent();

        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void checkRowWin() {
        Game g = new Game("A", "B", 3);
        play(g, 0, 0, 1, 0, 0, 1, 1, 1, 0, 2);
        check("row win -> X_WON", g.getStatus() == GameStatus.X_WON);
        check("winner is A", g.getWinner().map(Player::name).orElse("").equals("A"));
    }

    private static void checkColumnWin() {
        Game g = new Game("A", "B", 3);
        play(g, 0, 0, 0, 1, 1, 0, 1, 1, 2, 0);
        check("column win -> X_WON", g.getStatus() == GameStatus.X_WON);
    }

    private static void checkMainDiagonalWin() {
        Game g = new Game("A", "B", 3);
        play(g, 0, 0, 0, 1, 1, 1, 0, 2, 2, 2);
        check("main diagonal win -> X_WON", g.getStatus() == GameStatus.X_WON);
    }

    private static void checkAntiDiagonalWin() {
        Game g = new Game("A", "B", 3);
        play(g, 0, 2, 0, 0, 1, 1, 0, 1, 2, 0);
        check("anti-diagonal win -> X_WON", g.getStatus() == GameStatus.X_WON);
    }

    private static void checkOWins() {
        Game g = new Game("A", "B", 3);
        play(g, 0, 0, 1, 0, 0, 1, 1, 1, 2, 2, 1, 2);
        check("O wins -> O_WON", g.getStatus() == GameStatus.O_WON);
        check("winner is B", g.getWinner().map(Player::name).orElse("").equals("B"));
    }

    private static void checkDraw() {
        Game g = new Game("A", "B", 3);
        // X O X / X O O / O X X
        play(g, 0, 0, 0, 1, 0, 2, 1, 1, 1, 0, 1, 2, 2, 1, 2, 0, 2, 2);
        check("full board without line -> DRAW", g.getStatus() == GameStatus.DRAW);
        check("draw has no winner", g.getWinner().isEmpty());
    }

    private static void checkWinOnFinalMoveBeatsDraw() {
        Game g = new Game("A", "B", 3);
        // Last (9th) move completes X's main diagonal; must be a win, not a draw.
        // X O X / O X O / O X X  -> X plays (2,2) last
        play(g, 0, 0, 0, 1, 0, 2, 1, 0, 1, 1, 1, 2, 2, 1, 2, 0, 2, 2);
        check("win on final move beats draw", g.getStatus() == GameStatus.X_WON);
    }

    private static void checkTurnAlternation() {
        Game g = new Game("A", "B", 3);
        check("X starts", g.getCurrentPlayer().mark() == Mark.X);
        g.playMove(0, 0);
        check("O after X", g.getCurrentPlayer().mark() == Mark.O);
        g.playMove(1, 1);
        check("X after O", g.getCurrentPlayer().mark() == Mark.X);
    }

    private static void checkOccupiedCellRejectedWithoutSwitchingTurn() {
        Game g = new Game("A", "B", 3);
        g.playMove(0, 0);
        boolean threw = throwsInvalidMove(() -> g.playMove(0, 0));
        check("occupied cell rejected", threw);
        check("turn unchanged after rejected move", g.getCurrentPlayer().mark() == Mark.O);
        check("move count unchanged after rejected move", g.getMoveCount() == 1);
    }

    private static void checkOutOfBoundsRejected() {
        Game g = new Game("A", "B", 3);
        check("negative row rejected", throwsInvalidMove(() -> g.playMove(-1, 0)));
        check("col == size rejected", throwsInvalidMove(() -> g.playMove(0, 3)));
        check("getCell out of bounds rejected", throwsInvalidMove(() -> g.getCell(3, 3)));
        check("state untouched", g.getMoveCount() == 0 && g.getCurrentPlayer().mark() == Mark.X);
    }

    private static void checkMoveAfterGameOverRejected() {
        Game g = new Game("A", "B", 3);
        play(g, 0, 0, 1, 0, 0, 1, 1, 1, 0, 2);
        boolean threw = false;
        try {
            g.playMove(2, 2);
        } catch (GameOverException e) {
            threw = true;
        }
        check("move after game over rejected", threw);
    }

    private static void checkPlayerValidation() {
        check("blank name rejected", throwsIllegalArgument(() -> new Game(" ", "B", 3)));
        check("null name rejected", throwsIllegalArgument(() -> new Game("A", null, 3)));
        check("null mark rejected", throwsIllegalArgument(() -> new Player("A", null)));
        check("player name stripped", new Player("  A ", Mark.X).name().equals("A"));
    }

    private static void checkBoardSizeValidation() {
        check("size 0 rejected", throwsIllegalArgument(() -> new Game("A", "B", 0)));
        check("negative size rejected", throwsIllegalArgument(() -> new Game("A", "B", -3)));
    }

    private static void checkOneByOneBoard() {
        Game g = new Game("A", "B", 1);
        g.playMove(0, 0);
        check("1x1: first move wins", g.getStatus() == GameStatus.X_WON);
    }

    private static void checkLargerBoard() {
        Game g = new Game("A", "B", 5);
        // X fills row 2; O plays elsewhere on row 0 (never completes it).
        play(g, 2, 0, 0, 0, 2, 1, 0, 1, 2, 2, 0, 2, 2, 3, 0, 3, 2, 4);
        check("5x5 row win", g.getStatus() == GameStatus.X_WON);

        Game d = new Game("A", "B", 4);
        // X main diagonal on 4x4, O on row 0 columns 1..3
        play(d, 0, 0, 0, 1, 1, 1, 0, 2, 2, 2, 0, 3, 3, 3);
        check("4x4 main diagonal win", d.getStatus() == GameStatus.X_WON);

        Game a = new Game("A", "B", 4);
        play(a, 0, 3, 0, 0, 1, 2, 0, 1, 2, 1, 0, 2, 3, 0);
        check("4x4 anti-diagonal win", a.getStatus() == GameStatus.X_WON);
    }

    private static void checkConcurrentMovesStayConsistent() throws Exception {
        int size = 10;
        Game g = new Game("A", "B", size);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger accepted = new AtomicInteger();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                final int row = r;
                final int col = c;
                pool.submit(() -> {
                    try {
                        g.playMove(row, col);
                        accepted.incrementAndGet();
                    } catch (GameOverException | InvalidMoveException ignored) {
                        // expected once the game ends
                    }
                });
            }
        }
        pool.shutdown();
        check("pool terminated", pool.awaitTermination(10, TimeUnit.SECONDS));
        check("accepted moves match board count", accepted.get() == g.getMoveCount());
        check("no moves after finish", g.getStatus().isFinished() || g.getMoveCount() == size * size);
    }

    /** Plays alternating moves given as (row, col) pairs. */
    private static void play(Game g, int... coords) {
        for (int i = 0; i < coords.length; i += 2) {
            g.playMove(coords[i], coords[i + 1]);
        }
    }

    private static boolean throwsInvalidMove(Runnable r) {
        try {
            r.run();
            return false;
        } catch (InvalidMoveException e) {
            return true;
        }
    }

    private static boolean throwsIllegalArgument(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }
}
