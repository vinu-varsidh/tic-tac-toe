package com.tictactoe.enums;

public enum Mark {
    X,
    O;

    public Mark opponent() {
        return this == X ? O : X;
    }
}
