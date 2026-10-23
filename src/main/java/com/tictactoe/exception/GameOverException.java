package com.tictactoe.exception;

/**
 * Thrown when a move is attempted after the game has already ended.
 */
public final class GameOverException extends IllegalStateException {
    public GameOverException(String message) {
        super(message);
    }
}
