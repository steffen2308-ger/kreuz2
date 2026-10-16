package de.steffen.kreuz2;

import org.junit.Test;
import static org.junit.Assert.*;

public class GridDetectorRobustnessTest {
    @Test public void doesNotInventGridsInTextRulesOrNoise() {
        for (GridDetectionScenarios.Scene s : GridDetectionScenarios.negativeScenes()) assertNull(s.name, new GridDetector(s.pixels, s.width, s.height).detect());
    }
    @Test public void preservesDimensionsAndAlignmentAcrossChallengingScreenshots() {
        for (GridDetectionScenarios.Scene s : GridDetectionScenarios.scenes()) {
            Puzzle p = new GridDetector(s.pixels, s.width, s.height).detect();
            assertNull(s.name + ": " + GridDetectionScenarios.mismatch(s, p), GridDetectionScenarios.mismatch(s, p));
        }
    }
}
