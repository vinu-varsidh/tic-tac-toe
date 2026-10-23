package com.tictactoe;

import com.tictactoe.game.Game;
import com.tictactoe.view.BoardRenderer;

public final class TicTacToeDemo {

    public static void main(String[] args) {
        Game game = new Game("Alice", "Bob", 3);

        game.playMove(0, 0); // Alice: X
        game.playMove(1, 0); // Bob: O
        game.playMove(0, 1); // Alice: X
        game.playMove(1, 1); // Bob: O
        game.playMove(0, 2); // Alice: X completes the top row

        System.out.print(BoardRenderer.render(game));
        System.out.println("Status: " + game.getStatus());
        game.getWinner().ifPresent(w -> System.out.println("Winner: " + w.name()));
    }
}
