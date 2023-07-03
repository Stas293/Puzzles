package com.projects.puzzles.controller;

import com.projects.puzzles.dto.PuzzleCheckDto;
import com.projects.puzzles.dto.PuzzleDto;
import com.projects.puzzles.service.PuzzleService;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/puzzles")
@SessionAttributes("userId")
public class PuzzleController {
    private final PuzzleService puzzleService;

    public PuzzleController(PuzzleService puzzleService) {
        this.puzzleService = puzzleService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Void> uploadImage(@RequestParam("image") MultipartFile image,
                                            Model model,
                                            @SessionAttribute(value = "userId", required = false) UUID userId) {
        if (userId == null) {
            userId = UUID.randomUUID();
            model.addAttribute("userId", userId);
        }
        puzzleService.divideIntoPuzzles(userId, image);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<PuzzleDto>> getPuzzles(@SessionAttribute("userId") UUID id) {
        return ResponseEntity.ok(puzzleService.getPuzzles(id));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getPuzzleImage(@PathVariable("id") int id,
                                                 @SessionAttribute("userId") UUID userId) {
        return ResponseEntity.ok(puzzleService.getPuzzleImage(userId, id));
    }

    @PostMapping("/check")
    public ResponseEntity<Boolean> checkPuzzles(@SessionAttribute("userId") UUID userId,
                                                @RequestBody ArrayList<PuzzleCheckDto> puzzleCheckDtos) {
        return ResponseEntity.ok(puzzleService.checkPuzzles(userId, puzzleCheckDtos));
    }

    @PostMapping("/assemble")
    public ResponseEntity<List<PuzzleDto>> assemblePuzzles(@SessionAttribute("userId") UUID userId) {
        return ResponseEntity.ok(puzzleService.assemblePuzzles(userId));
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetPuzzles(@SessionAttribute("userId") UUID userId) {
        puzzleService.resetPuzzles(userId);
        return ResponseEntity.ok().build();
    }
}


