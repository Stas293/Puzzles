package com.projects.puzzles.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Puzzle {
    private int id;
    private int x;
    private int y;
    private int width;
    private int height;
    private String imageName;
}


