package de.steffen.kreuz2;

import org.junit.Test;
import static org.junit.Assert.*;

public class PuzzleTest {
    private Puzzle board(int cols, int rows) { return new Puzzle(Puzzle.evenly(10, 10 + cols * 40, cols), Puzzle.evenly(20, 20 + rows * 40, rows)); }
    @Test public void typingAtRightEdgeTurnsDown() {
        Puzzle p = board(3, 3); p.select(2, false); p.direction = Puzzle.RIGHT;
        p.put("A"); assertEquals("A", p.letters[2]); assertEquals(5, p.selected); assertEquals(Puzzle.DOWN, p.direction);
    }
    @Test public void manualDirectionDoesNotTurnOrCrossBlock() {
        Puzzle p = board(3, 3); p.automatic = false; p.selected = 2; p.put("A"); assertEquals(2, p.selected);
        p.selected = 0; p.types[1] = Puzzle.BLOCK; p.put("B"); assertEquals(0, p.selected);
    }
    @Test public void clueArrowOverridesLongerHorizontalRun() {
        Puzzle p = board(6, 4); p.types[1] = Puzzle.CLUE; p.arrows[1] = 1 << Puzzle.DOWN;
        p.select(7, false); assertEquals(Puzzle.DOWN, p.direction);
        p.selected = -1; p.select(1, false); assertEquals(7, p.selected); assertEquals(Puzzle.DOWN, p.direction);
    }
    @Test public void backspaceMovesBackAndClears() {
        Puzzle p = board(3, 3); p.selected = 0; p.put("Ä"); p.backspace(); assertEquals(0, p.selected); assertEquals("", p.letters[0]);
    }
    @Test public void previousDoesNotWrapAcrossLeftEdge() {
        Puzzle p = board(3, 3); assertEquals(-1, p.neighbor(3, Puzzle.LEFT)); assertEquals(-1, p.neighbor(2, Puzzle.RIGHT));
    }
    @Test public void blockedFieldsCannotBeSelectedOrWritten() {
        Puzzle p = board(3, 3); p.types[0] = Puzzle.BLOCK; p.select(0, false); p.put("A"); assertEquals(-1, p.selected); assertEquals("", p.letters[0]);
    }
    @Test public void atWordEndFindsNextEmptyFieldWhenTurnBlocked() {
        Puzzle p = board(3, 3); p.selected = 2; p.types[5] = Puzzle.CLUE; p.types[3] = Puzzle.BLOCK;
        p.put("X"); assertEquals(4, p.selected);
    }
    @Test public void tappingSelectedCellTogglesDirection() {
        Puzzle p = board(3, 3); p.select(0, false); p.select(0, true); assertEquals(Puzzle.DOWN, p.direction);
    }
    @Test public void leftAndUpArrowsHaveCorrectNeighbors() {
        Puzzle p = board(4, 4); p.types[6] = Puzzle.CLUE; p.arrows[6] = 1 << Puzzle.LEFT;
        p.select(5, false); assertEquals(Puzzle.LEFT, p.direction); p.put("L"); assertEquals(4, p.selected);
        p.types[6] = Puzzle.OPEN; p.arrows[6] = 0;
        p.types[10] = Puzzle.CLUE; p.arrows[10] = 1 << Puzzle.UP; p.select(10, false); assertEquals(6, p.selected);
        p.put("U"); assertEquals(2, p.selected);
    }
    @Test(expected = IllegalArgumentException.class) public void refusesNonIncreasingGrid() { new Puzzle(new float[] {0, 40, 20}, new float[] {0, 30}); }
}
