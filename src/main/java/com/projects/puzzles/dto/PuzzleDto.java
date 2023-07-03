package com.projects.puzzles.dto;

import lombok.Builder;

@Builder
public record PuzzleDto(
        int id,
        int x,
        int y,
        int width,
        int height) {
}
