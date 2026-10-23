package com.tictactoe.exception;

/**
 * Thrown when a move targets an out-of-bounds or already occupied cell.
 * The game state is left unchanged, so the same player may retry.
 */
public final class InvalidMoveException extends RuntimeException {
    public InvalidMoveException(String message) {
        super(message);
    }
}
