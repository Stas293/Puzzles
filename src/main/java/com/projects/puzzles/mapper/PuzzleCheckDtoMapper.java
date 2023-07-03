package com.projects.puzzles.mapper;

import com.projects.puzzles.dto.PuzzleCheckDto;
import com.projects.puzzles.model.Puzzle;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MapperConfig;

import java.util.List;

@Mapper(componentModel = "spring")
@MapperConfig(injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PuzzleCheckDtoMapper {
    List<Puzzle> puzzleCheckDtos(List<PuzzleCheckDto> puzzles);
}
