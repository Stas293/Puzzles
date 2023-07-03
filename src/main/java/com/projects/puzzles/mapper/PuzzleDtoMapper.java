package com.projects.puzzles.mapper;

import com.projects.puzzles.dto.PuzzleDto;
import com.projects.puzzles.model.Puzzle;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MapperConfig;

import java.util.List;

@Mapper(componentModel = "spring")
@MapperConfig(injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PuzzleDtoMapper {
    PuzzleDto puzzleDto(Puzzle puzzle);

    List<PuzzleDto> puzzleDtos(List<Puzzle> puzzles);
}
