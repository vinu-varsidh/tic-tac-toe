package com.tictactoe.view;

import com.tictactoe.enums.Mark;
import com.tictactoe.game.Game;

/**
 * Renders a {@link Game} as text. Presentation is kept out of the domain.
 */
public final class BoardRenderer {

    private BoardRenderer() {
    }

    public static String render(Game game) {
        int size = game.getBoardSize();
        String separator = "---+".repeat(size - 1) + "---";
        StringBuilder sb = new StringBuilder();

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                Mark mark = game.getCell(r, c);
                sb.append(' ').append(mark == null ? ' ' : mark.name()).append(' ');
                if (c < size - 1) {
                    sb.append('|');
                }
            }
            sb.append('\n');
            if (r < size - 1) {
                sb.append(separator).append('\n');
            }
        }
        return sb.toString();
    }
}
