package com.projects.puzzles.utility;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "puzzle")
public record PuzzleConfig(
        int numPuzzlesX,
        int numPuzzlesY,
        int colorThreshold,
        double meanErrorProbabilityThreshold) {
}
