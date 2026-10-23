package com.tictactoe.model;

import com.tictactoe.enums.Mark;

public record Player(String name, Mark mark) {
    public Player {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Player name cannot be empty");
        }
        if (mark == null) {
            throw new IllegalArgumentException(
                    "Player mark cannot be null");
        }
        name = name.strip();
    }
}
