package com.tictactoe.enums;

public enum GameStatus {
    IN_PROGRESS,
    X_WON,
    O_WON,
    DRAW;

    public boolean isFinished() {
        return this != IN_PROGRESS;
    }

    public static GameStatus wonBy(Mark mark) {
        return mark == Mark.X ? X_WON : O_WON;
    }
}
