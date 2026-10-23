package com.tictactoe.game;

import com.tictactoe.enums.GameStatus;
import com.tictactoe.enums.Mark;
import com.tictactoe.exception.GameOverException;
import com.tictactoe.model.Player;

import java.util.Optional;

/**
 * Orchestrates a single match: turn order, move validation, and status.
 * All public methods are synchronized so concurrent callers see a
 * consistent state; a rejected move never changes that state.
 */
public final class Game {
    private final Board board;
    private final Player playerX;
    private final Player playerO;

    private Player currentPlayer;
    private GameStatus status;

    public Game(String playerXName, String playerOName, int boardSize) {
        this.playerX = new Player(playerXName, Mark.X);
        this.playerO = new Player(playerOName, Mark.O);
        this.board = new Board(boardSize);
        this.currentPlayer = playerX;
        this.status = GameStatus.IN_PROGRESS;
    }

    public synchronized GameStatus playMove(int row, int col) {
        if (status.isFinished()) {
            throw new GameOverException(
                    "Game has already ended: " + status);
        }

        Mark mark = currentPlayer.mark();

        // Board validates bounds and occupancy before mutating anything.
        board.placeMark(row, col, mark);

        if (board.isWinningMove(row, col, mark)) {
            status = GameStatus.wonBy(mark);
        } else if (board.isFull()) {
            status = GameStatus.DRAW;
        } else {
            currentPlayer = currentPlayer == playerX ? playerO : playerX;
        }
        return status;
    }

    public synchronized GameStatus getStatus() {
        return status;
    }

    /** The player whose turn it is; after the game ends, the last mover. */
    public synchronized Player getCurrentPlayer() {
        return currentPlayer;
    }

    public synchronized Optional<Player> getWinner() {
        return switch (status) {
            case X_WON -> Optional.of(playerX);
            case O_WON -> Optional.of(playerO);
            default -> Optional.empty();
        };
    }

    public synchronized Mark getCell(int row, int col) {
        return board.getCell(row, col);
    }

    public synchronized int getBoardSize() {
        return board.getSize();
    }

    public synchronized int getMoveCount() {
        return board.getMoveCount();
    }
}
