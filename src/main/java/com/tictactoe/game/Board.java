package com.tictactoe.game;

import com.tictactoe.enums.Mark;
import com.tictactoe.exception.InvalidMoveException;

/**
 * N x N grid. Owns cell state and win detection; knows nothing about
 * players or turns. Not thread-safe on its own: {@link Game} guards it.
 */
final class Board {
    private final int size;
    private final Mark[][] cells;
    private int moves;

    Board(int size) {
        if (size < 1) {
            throw new IllegalArgumentException(
                    "Board size must be positive");
        }
        this.size = size;
        this.cells = new Mark[size][size];
    }

    void placeMark(int row, int col, Mark mark) {
        if (mark == null) {
            throw new IllegalArgumentException("Mark cannot be null");
        }
        validateCoordinates(row, col);
        if (cells[row][col] != null) {
            throw new InvalidMoveException(
                    "Cell is already occupied: (" + row + ", " + col + ")");
        }
        cells[row][col] = mark;
        moves++;
    }

    /**
     * Checks only the lines passing through the last-placed cell,
     * which is O(N) instead of scanning the whole board.
     */
    boolean isWinningMove(int row, int col, Mark mark) {
        validateCoordinates(row, col);
        if (mark == null || cells[row][col] != mark) {
            return false;
        }

        return rowComplete(row, mark)
                || columnComplete(col, mark)
                || (row == col && mainDiagonalComplete(mark))
                || (row + col == size - 1 && antiDiagonalComplete(mark));
    }

    boolean isFull() {
        return moves == size * size;
    }

    Mark getCell(int row, int col) {
        validateCoordinates(row, col);
        return cells[row][col];
    }

    int getSize() {
        return size;
    }

    int getMoveCount() {
        return moves;
    }

    private boolean rowComplete(int row, Mark mark) {
        for (int c = 0; c < size; c++) {
            if (cells[row][c] != mark) {
                return false;
            }
        }
        return true;
    }

    private boolean columnComplete(int col, Mark mark) {
        for (int r = 0; r < size; r++) {
            if (cells[r][col] != mark) {
                return false;
            }
        }
        return true;
    }

    private boolean mainDiagonalComplete(Mark mark) {
        for (int i = 0; i < size; i++) {
            if (cells[i][i] != mark) {
                return false;
            }
        }
        return true;
    }

    private boolean antiDiagonalComplete(Mark mark) {
        for (int i = 0; i < size; i++) {
            if (cells[i][size - 1 - i] != mark) {
                return false;
            }
        }
        return true;
    }

    private void validateCoordinates(int row, int col) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new InvalidMoveException(
                    "Invalid cell: (" + row + ", " + col + ")");
        }
    }
}
