package com.projects.puzzles.dto;

import lombok.Builder;

@Builder
public record PuzzleCheckDto(
        long id,
        int x,
        int y,
        int width,
        int height
) {
}
