package com.projects.puzzles.service;

import com.projects.puzzles.dto.PuzzleCheckDto;
import com.projects.puzzles.dto.PuzzleDto;
import com.projects.puzzles.mapper.PuzzleCheckDtoMapper;
import com.projects.puzzles.mapper.PuzzleDtoMapper;
import com.projects.puzzles.model.Puzzle;
import com.projects.puzzles.utility.Adjacent;
import com.projects.puzzles.utility.Pair;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class PuzzleService {
    private static final String PATH_TO_PUZZLE_IMAGES_DIRECTORY = "./puzzles/";
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final int NUM_PUZZLES_X = 5;
    private static final int NUM_PUZZLES_Y = 4;
    private final PuzzleDtoMapper puzzleDtoMapper;
    private final PuzzleCheckDtoMapper puzzleCheckDtoMapper;
    private final Map<UUID, Map<Integer, Puzzle>> puzzlesMap = new HashMap<>();
    private final Map<UUID, Pair<Integer, Integer>> puzzleSizeMap = new HashMap<>();

    private static double getMeanDiff(int[] edge1, int[] edge2) {
        int totalDiff = 0;
        for (int i = 0; i < edge1.length; i++) {
            int pixel1 = edge1[i];
            int pixel2 = edge2[i];

            // Extract RGB values
            int r1 = (pixel1 >> 16) & 0xFF;
            int g1 = (pixel1 >> 8) & 0xFF;
            int b1 = pixel1 & 0xFF;

            int r2 = (pixel2 >> 16) & 0xFF;
            int g2 = (pixel2 >> 8) & 0xFF;
            int b2 = pixel2 & 0xFF;

            // Define a threshold for color similarity
            int colorThreshold = 9;

            // Check if the RGB values are within the color threshold
            if (Math.abs(r1 - r2) > colorThreshold ||
                    Math.abs(g1 - g2) > colorThreshold ||
                    Math.abs(b1 - b2) > colorThreshold) {
                log.info("Pixels at index {} do not match", i);
                log.info("Pixel 1: ({}, {}, {})", r1, g1, b1);
                log.info("Pixel 2: ({}, {}, {})", r2, g2, b2);
                totalDiff++;
            }
        }

        return (double) totalDiff / edge1.length;
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down executor");
        executor.shutdown();
    }

    @SneakyThrows
    public void divideIntoPuzzles(UUID id, MultipartFile image) {
        byte[] imageBytes = image.getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage fullImage = ImageIO.read(bais);

        int puzzleWidth = fullImage.getWidth() / NUM_PUZZLES_X;
        int puzzleHeight = fullImage.getHeight() / NUM_PUZZLES_Y;
        puzzleSizeMap.put(id, new Pair<>(puzzleWidth, puzzleHeight));

        Map<Integer, Puzzle> puzzles = new HashMap<>();
        if (checkIfTheUserFolderExists(id)) {
            deletePuzzleImages(id);
        }
        List<Integer> shuffledPuzzleIds = IntStream.range(0, NUM_PUZZLES_X * NUM_PUZZLES_Y)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(shuffledPuzzleIds);
        for (int y = 0; y < NUM_PUZZLES_Y; y++) {
            for (int x = 0; x < NUM_PUZZLES_X; x++) {
                BufferedImage puzzleImage = fullImage.getSubimage(x * puzzleWidth, y * puzzleHeight, puzzleWidth, puzzleHeight);
                Integer puzzleId = shuffledPuzzleIds.get(y * NUM_PUZZLES_X + x);
                String puzzleImageName = "%s/%s_%d.jpg".formatted(id, image.getName(), puzzleId);

                Puzzle puzzle = new Puzzle();
                puzzle.setId(puzzleId);
                int correspondingX = puzzleId % NUM_PUZZLES_X;
                int correspondingY = puzzleId / NUM_PUZZLES_X;
                int newX = correspondingX * puzzleWidth;
                int newY = correspondingY * puzzleHeight;
                puzzle.setX(newX);
                puzzle.setY(newY);
                puzzle.setWidth(puzzleWidth);
                puzzle.setHeight(puzzleHeight);
                puzzle.setImageName(puzzleImageName);

                // Save the puzzle image to disk
                savePuzzleImage(puzzleImage, puzzleImageName);

                puzzles.put(puzzleId, puzzle);
            }
        }
        if (puzzlesMap.containsKey(id)) {
            puzzlesMap.replace(id, puzzles);
        } else {
            puzzlesMap.put(id, puzzles);
        }
    }

    private boolean checkIfTheUserFolderExists(UUID id) {
        String filePath = PATH_TO_PUZZLE_IMAGES_DIRECTORY + id.toString();
        File file = new File(filePath);
        return file.exists() && file.isDirectory();
    }

    @SneakyThrows
    private void deletePuzzleImages(UUID id) {
        String filePath = PATH_TO_PUZZLE_IMAGES_DIRECTORY + id.toString();
        File file = new File(filePath);
        if (file.exists() && file.isDirectory()) {
            Path userDirectory = Paths.get(filePath);
            try (var filesStream = Files.walk(userDirectory)) {
                filesStream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    private void savePuzzleImage(BufferedImage image, String imageName) throws IOException {
        String filePath = PATH_TO_PUZZLE_IMAGES_DIRECTORY + imageName;
        File outputFile = new File(filePath);

        // Create the parent directories if they don't exist
        Path outputDirectory = outputFile.toPath().getParent();
        Files.createDirectories(outputDirectory);

        // Save the image to disk
        ImageIO.write(image, "jpg", outputFile);
    }

    public List<PuzzleDto> getPuzzles(UUID id) {
        List<Puzzle> puzzles = puzzlesMap.get(id).values()
                .stream()
                .toList();
        return puzzleDtoMapper.puzzleDtos(puzzles);
    }

    public List<PuzzleDto> assemblePuzzles(UUID id) {
        List<Puzzle> puzzles = puzzlesMap.get(id).values()
                .stream()
                .toList();
        assemblePuzzles(id, puzzles);
        return puzzleDtoMapper.puzzleDtos(puzzles);
    }

    public void assemblePuzzles(UUID id, List<Puzzle> puzzles) {
        Map<Pair<Puzzle, Puzzle>, List<Adjacent>> adjacentListPuzzles = new ConcurrentHashMap<>();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (Puzzle puzzle : puzzles) {
            tasks.add(() -> {
                puzzles.stream()
                        .filter(puzzle2 -> puzzle.getId() != puzzle2.getId())
                        .forEach(puzzle2 -> getAdjacents(adjacentListPuzzles, puzzle, puzzle2));
                return null;
            });
        }

        try {
            executor.invokeAll(tasks);
            log.info("All tasks are finished");
        } catch (InterruptedException e) {
            log.error("Error while invoking tasks", e);
            executor.shutdown();
            throw new RuntimeException(e);
        }

        Map<Pair<Puzzle, Puzzle>, Adjacent> adjacentPuzzles = new ConcurrentHashMap<>();
        adjacentListPuzzles.forEach((pair, adjacents) -> {
            Puzzle puzzle1 = pair.getFirst();
            Puzzle puzzle2 = pair.getSecond();
            Adjacent adjacent = adjacents.get(0);
            if (adjacents.size() > 2)
                throw new RuntimeException("There are more than 2 adjacents");
            if (adjacents.size() > 1) {
                adjacent = findCorrectAdjacent(adjacentListPuzzles, adjacents, puzzle1, puzzle2);
            }
            adjacentPuzzles.put(pair, adjacent);
        });

        Puzzle currentPuzzle = calculateTopLeftPuzzle(puzzles, adjacentPuzzles);
        List<List<Puzzle>> puzzleRows = calculatePuzzleFragmentMatrix(adjacentPuzzles, currentPuzzle);
        log.info("Puzzle rows: {}", puzzleRows);

        int puzzleWidth = puzzleSizeMap.get(id).getFirst();
        int puzzleHeight = puzzleSizeMap.get(id).getSecond();

        updateCoordinatesOfCurrentFragments(puzzleRows, puzzleWidth, puzzleHeight);
        log.info("Puzzles: {}", puzzleRows);
    }

    private static void updateCoordinatesOfCurrentFragments(List<List<Puzzle>> puzzleRows, int puzzleWidth, int puzzleHeight) {
        for (int i = 0; i < puzzleRows.size(); i++) {
            List<Puzzle> puzzleRow = puzzleRows.get(i);
            for (int j = 0; j < puzzleRow.size(); j++) {
                Puzzle puzzle = puzzleRow.get(j);
                puzzle.setX(j * puzzleWidth);
                puzzle.setY(i * puzzleHeight);
            }
        }
    }

    private List<List<Puzzle>> calculatePuzzleFragmentMatrix(Map<Pair<Puzzle, Puzzle>, Adjacent> adjacentPuzzles, Puzzle currentPuzzle) {
        List<List<Puzzle>> puzzleRows = new ArrayList<>();
        for (int i = 0; i < NUM_PUZZLES_Y; i++) {
            List<Puzzle> puzzleRow = new ArrayList<>();
            for (int j = 0; j < NUM_PUZZLES_X; j++) {
                puzzleRow.add(currentPuzzle);
                Optional<Puzzle> nextPuzzle = getAdjacentPuzzle(adjacentPuzzles, currentPuzzle, Adjacent.RIGHT);
                if (nextPuzzle.isPresent()) {
                    currentPuzzle = nextPuzzle.get();
                }
            }
            puzzleRows.add(puzzleRow);
            Optional<Puzzle> nextPuzzle = getAdjacentPuzzle(adjacentPuzzles, puzzleRow.get(0), Adjacent.BOTTOM);
            if (nextPuzzle.isPresent()) {
                currentPuzzle = nextPuzzle.get();
            }
        }
        return puzzleRows;
    }

    private Puzzle calculateTopLeftPuzzle(List<Puzzle> puzzles, Map<Pair<Puzzle, Puzzle>, Adjacent> adjacentPuzzles) {
        Puzzle firstPuzzle = puzzles.get(0);
        while (getAdjacentPuzzleInverted(adjacentPuzzles, firstPuzzle, Adjacent.LEFT).isPresent()) {
            firstPuzzle = getAdjacentPuzzleInverted(adjacentPuzzles, firstPuzzle, Adjacent.LEFT).get();
        }
        while (getAdjacentPuzzleInverted(adjacentPuzzles, firstPuzzle, Adjacent.TOP).isPresent()) {
            firstPuzzle = getAdjacentPuzzleInverted(adjacentPuzzles, firstPuzzle, Adjacent.TOP).get();
        }
        return firstPuzzle;
    }

    private Adjacent findCorrectAdjacent(Map<Pair<Puzzle, Puzzle>, List<Adjacent>> adjacentListPuzzles, List<Adjacent> adjacents, Puzzle puzzle1, Puzzle puzzle2) {
        Map<Adjacent, Double> error1 = calculateError(puzzle1, puzzle2, adjacents);
        List<Pair<Puzzle, Puzzle>> pairWithSameFirstButDifferentSecond = adjacentListPuzzles.keySet()
                .stream()
                .filter(p -> p.getFirst().getId() == puzzle1.getId() && p.getSecond().getId() != puzzle2.getId())
                .toList();
        Map<Adjacent, Double> error2 = new EnumMap<>(Adjacent.class);
        calculateErrorForPairsWithSameFirst(adjacentListPuzzles, puzzle1, pairWithSameFirstButDifferentSecond, error2);
        List<Adjacent> copyAdjacents = new ArrayList<>(adjacents);
        removeExistingAdjacents(adjacents, error1, error2, copyAdjacents);
        if (copyAdjacents.size() != 1) {
            deleteWorseAdjacent(error1, copyAdjacents);
        }
        return copyAdjacents.get(0);
    }

    private static void deleteWorseAdjacent(Map<Adjacent, Double> error1, List<Adjacent> copyAdjacents) {
        Adjacent adjacentWithHighestError = error1.entrySet()
                .stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .get()
                .getKey();
        copyAdjacents.remove(adjacentWithHighestError);
    }

    private static void removeExistingAdjacents(List<Adjacent> adjacents, Map<Adjacent, Double> error1, Map<Adjacent, Double> error2, List<Adjacent> copyAdjacents) {
        for (Adjacent adjacent1 : adjacents) {
            if (error2.get(adjacent1) == null)
                continue;
            if (error1.get(adjacent1) > error2.get(adjacent1)) {
                copyAdjacents.remove(adjacent1);
            }
        }
    }

    private void calculateErrorForPairsWithSameFirst(Map<Pair<Puzzle, Puzzle>, List<Adjacent>> adjacentListPuzzles, Puzzle puzzle1, List<Pair<Puzzle, Puzzle>> pairWithSameFirstButDifferentSecond, Map<Adjacent, Double> error2) {
        for (Pair<Puzzle, Puzzle> pair2 : pairWithSameFirstButDifferentSecond) {
            Puzzle puzzle3 = pair2.getSecond();
            List<Adjacent> adjacents2 = adjacentListPuzzles.get(pair2);
            if (adjacents2.size() != 1)
                throw new RuntimeException("There are more than 1 adjacents");
            error2.put(adjacents2.get(0), calculateError(puzzle1, puzzle3, adjacents2).get(adjacents2.get(0)));
        }
    }

    private void getAdjacents(Map<Pair<Puzzle, Puzzle>, List<Adjacent>> adjacentListPuzzles, Puzzle puzzle, Puzzle puzzle2) {
        List<Adjacent> adjacents = areAdjacent(puzzle, puzzle2);
        if (!adjacents.isEmpty()) {
            adjacentListPuzzles.put(new Pair<>(puzzle, puzzle2), adjacents);
        }
    }

    private Map<Adjacent, Double> calculateError(Puzzle puzzle1, Puzzle puzzle2, List<Adjacent> adjacents) {
        Map<Adjacent, Double> error = new EnumMap<>(Adjacent.class);
        for (Adjacent adjacent : adjacents) {
            error.put(adjacent, calculateError(puzzle1, puzzle2, adjacent));
        }
        return error;
    }

    private Double calculateError(Puzzle puzzle1, Puzzle puzzle2, Adjacent adjacent) {
        if (adjacent == Adjacent.LEFT) {
            return getMeanDiff(getLeftEdge(getFragmentImage(puzzle1)), getRightEdge(getFragmentImage(puzzle2)));
        } else if (adjacent == Adjacent.TOP) {
            return getMeanDiff(getTopEdge(getFragmentImage(puzzle1)), getBottomEdge(getFragmentImage(puzzle2)));
        } else if (adjacent == Adjacent.RIGHT) {
            return getMeanDiff(getRightEdge(getFragmentImage(puzzle1)), getLeftEdge(getFragmentImage(puzzle2)));
        } else if (adjacent == Adjacent.BOTTOM) {
            return getMeanDiff(getBottomEdge(getFragmentImage(puzzle1)), getTopEdge(getFragmentImage(puzzle2)));
        } else {
            throw new IllegalArgumentException("Unknown adjacent: " + adjacent);
        }
    }

    private Optional<Puzzle> getAdjacentPuzzle(Map<Pair<Puzzle, Puzzle>, Adjacent> adjacentPuzzles, Puzzle firstPuzzle, Adjacent adjacent) {
        if (adjacent == Adjacent.LEFT || adjacent == Adjacent.TOP) {
            List<Puzzle> puzzleList = adjacentPuzzles.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().getFirst().getId() == firstPuzzle.getId())
                    .filter(entry -> entry.getValue() == adjacent)
                    .map(entry -> entry.getKey().getSecond())
                    .toList();
            if (puzzleList.isEmpty()) {
                return Optional.empty();
            } else {
                return checkMorePreciseAdjacentLeftTop(firstPuzzle, adjacent, puzzleList);
            }
        }
        List<Puzzle> list = adjacentPuzzles.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getFirst().getId() == firstPuzzle.getId())
                .filter(entry -> entry.getValue() == adjacent)
                .map(entry -> entry.getKey().getSecond())
                .toList();
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            return checkMorePreciseAdjacentRightBottom(firstPuzzle, adjacent, list);
        }
    }

    private Optional<Puzzle> getAdjacentPuzzleInverted(Map<Pair<Puzzle, Puzzle>, Adjacent> adjacentPuzzles, Puzzle firstPuzzle, Adjacent adjacent) {
        if (adjacent == Adjacent.LEFT || adjacent == Adjacent.TOP) {
            return adjacentPuzzles.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().getFirst().getId() == firstPuzzle.getId())
                    .filter(entry -> entry.getValue() == adjacent)
                    .map(entry -> entry.getKey().getSecond())
                    .findFirst();
        }
        return adjacentPuzzles.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getSecond().getId() == firstPuzzle.getId())
                .filter(entry -> entry.getValue() == adjacent)
                .map(entry -> entry.getKey().getFirst())
                .findFirst();
    }

    private Optional<Puzzle> checkMorePreciseAdjacentRightBottom(Puzzle firstPuzzle, Adjacent adjacent, List<Puzzle> list) {
        if (adjacent == Adjacent.RIGHT) {
            Map<Puzzle, Double> puzzleDifference = new HashMap<>();
            for (Puzzle puzzle : list) {
                BufferedImage image1 = getFragmentImage(firstPuzzle);
                BufferedImage image2 = getFragmentImage(puzzle);
                puzzleDifference.put(puzzle, getMeanDiff(getRightEdge(image1), getLeftEdge(image2)));
            }
            return puzzleDifference.entrySet()
                    .stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey);
        } else {
            Map<Puzzle, Double> puzzleDifference = new HashMap<>();
            for (Puzzle puzzle : list) {
                BufferedImage image1 = getFragmentImage(firstPuzzle);
                BufferedImage image2 = getFragmentImage(puzzle);
                puzzleDifference.put(puzzle, getMeanDiff(getBottomEdge(image1), getTopEdge(image2)));
            }
            return puzzleDifference.entrySet()
                    .stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey);
        }
    }

    private Optional<Puzzle> checkMorePreciseAdjacentLeftTop(Puzzle firstPuzzle, Adjacent adjacent, List<Puzzle> puzzleList) {
        if (adjacent == Adjacent.LEFT) {
            Map<Puzzle, Double> puzzleDifference = new HashMap<>();
            for (Puzzle puzzle : puzzleList) {
                BufferedImage image1 = getFragmentImage(firstPuzzle);
                BufferedImage image2 = getFragmentImage(puzzle);
                puzzleDifference.put(puzzle, getMeanDiff(getLeftEdge(image1), getRightEdge(image2)));
            }
            return puzzleDifference.entrySet()
                    .stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey);
        } else {
            Map<Puzzle, Double> puzzleDifference = new HashMap<>();
            for (Puzzle puzzle : puzzleList) {
                BufferedImage image1 = getFragmentImage(firstPuzzle);
                BufferedImage image2 = getFragmentImage(puzzle);
                puzzleDifference.put(puzzle, getMeanDiff(getTopEdge(image1), getBottomEdge(image2)));
            }
            return puzzleDifference.entrySet()
                    .stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey);
        }
    }

    private List<Adjacent> areAdjacent(Puzzle puzzle1, Puzzle puzzle2) {
        BufferedImage image1 = getFragmentImage(puzzle1);
        BufferedImage image2 = getFragmentImage(puzzle2);
        List<Adjacent> adjacents = new ArrayList<>();

        if (areEdgesMatching(getRightEdge(image1), getLeftEdge(image2))) {
            adjacents.add(Adjacent.RIGHT);
        }

        if (areEdgesMatching(getLeftEdge(image1), getRightEdge(image2))) {
            adjacents.add(Adjacent.LEFT);
        }

        if (areEdgesMatching(getBottomEdge(image1), getTopEdge(image2))) {
            adjacents.add(Adjacent.BOTTOM);
        }

        if (areEdgesMatching(getTopEdge(image1), getBottomEdge(image2))) {
            adjacents.add(Adjacent.TOP);
        }

        return adjacents;
    }

    private int[] getRightEdge(BufferedImage image) {
        int height = image.getHeight();
        int[] edgePixels = new int[height];
        for (int y = 0; y < height; y++) {
            edgePixels[y] = image.getRGB(image.getWidth() - 1, y);
        }
        return edgePixels;
    }

    private int[] getLeftEdge(BufferedImage image) {
        int height = image.getHeight();
        int[] edgePixels = new int[height];
        for (int y = 0; y < height; y++) {
            edgePixels[y] = image.getRGB(0, y);
        }
        return edgePixels;
    }

    private int[] getTopEdge(BufferedImage image) {
        int width = image.getWidth();
        int[] edgePixels = new int[width];
        for (int x = 0; x < width; x++) {
            edgePixels[x] = image.getRGB(x, 0);
        }
        return edgePixels;
    }

    private int[] getBottomEdge(BufferedImage image) {
        int width = image.getWidth();
        int[] edgePixels = new int[width];
        for (int x = 0; x < width; x++) {
            edgePixels[x] = image.getRGB(x, image.getHeight() - 1);
        }
        return edgePixels;
    }

    private boolean areEdgesMatching(int[] edge1, int[] edge2) {
        // Compare the edges by matching the colors of corresponding pixels
        log.info("Comparing edges of length: {} and {}", edge1.length, edge2.length);
        // use mean percentage of color difference
        double meanDiff = getMeanDiff(edge1, edge2);
        log.info("Mean difference: {}", meanDiff);
        return meanDiff <= 0.13;
    }

    @SneakyThrows
    public BufferedImage getFragmentImage(Puzzle puzzle) {
        String filePath = PATH_TO_PUZZLE_IMAGES_DIRECTORY + puzzle.getImageName();
        return ImageIO.read(new File(filePath));
    }


    @SneakyThrows
    public byte[] getPuzzleImage(UUID userId, int id) {
        Map<Integer, Puzzle> puzzles = puzzlesMap.get(userId);
        if (puzzles == null) {
            throw new Exception("Puzzle not found");
        }
        Puzzle puzzle = puzzles.get(id);
        if (puzzle == null) {
            throw new Exception("Puzzle not found");
        }
        String filePath = PATH_TO_PUZZLE_IMAGES_DIRECTORY + puzzle.getImageName();

        return Files.readAllBytes(Paths.get(filePath));
    }

    @SneakyThrows
    public boolean checkPuzzles(UUID userId, List<PuzzleCheckDto> puzzleCheckDtos) {
        Map<Integer, Puzzle> puzzles = puzzlesMap.get(userId);
        if (puzzles == null) {
            return false;
        }
        log.info("Checking puzzles for user: {}", userId);
        int puzzleWidth = puzzleSizeMap.get(userId).first();
        int puzzleHeight = puzzleSizeMap.get(userId).second();
        log.info("Puzzle width: {}, height: {}", puzzleWidth, puzzleHeight);
        puzzleCheckDtos.sort((o1, o2) -> {
            if (Math.abs(o1.y() - o2.y()) < puzzleHeight / 2) {
                return o1.x() - o2.x();
            }
            return o1.y() - o2.y();
        });
        List<Puzzle> puzzleList = puzzleCheckDtoMapper.puzzleCheckDtos(puzzleCheckDtos)
                .stream()
                .map(puzzleCheckDto -> puzzles.get(puzzleCheckDto.getId()))
                .toList();
        List<List<Puzzle>> puzzleMatrix = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            puzzleMatrix.add(new ArrayList<>());
            for (int j = 0; j < 5; j++) {
                puzzleMatrix.get(i).add(puzzleList.get(i * 5 + j));
            }
        }
        log.info("Puzzle matrix: {}", puzzleMatrix);
        return checkPuzzleMatrix(puzzleMatrix);
    }

    private boolean checkPuzzleMatrix(List<List<Puzzle>> puzzleMatrix) {
        for (int i = 0; i < puzzleMatrix.size(); i++) {
            for (int j = 0; j < puzzleMatrix.get(i).size(); j++) {
                log.info("Checking puzzle: {}", puzzleMatrix.get(i).get(j));
                if (i > 0 && (!checkPuzzleBottom(puzzleMatrix.get(i - 1).get(j), puzzleMatrix.get(i).get(j)))) {
                    log.info("Puzzle bottom check failed for puzzle: {} and puzzle: {}", puzzleMatrix.get(i - 1).get(j), puzzleMatrix.get(i).get(j));
                    return false;
                }
                if (j > 0 && (!checkPuzzleRight(puzzleMatrix.get(i).get(j - 1), puzzleMatrix.get(i).get(j)))) {
                    log.info("Puzzle right check failed for puzzle: {} and puzzle: {}", puzzleMatrix.get(i).get(j - 1), puzzleMatrix.get(i).get(j));
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkPuzzleRight(Puzzle puzzleLeft, Puzzle puzzleRight) {
        log.info("Checking puzzle right for puzzle: {} and puzzle: {}", puzzleLeft, puzzleRight);
        BufferedImage fragmentLeft = getFragmentImage(puzzleLeft);
        BufferedImage fragmentRight = getFragmentImage(puzzleRight);
        int[] edgeLeft = getRightEdge(fragmentLeft);
        int[] edgeRight = getLeftEdge(fragmentRight);
        return areEdgesMatching(edgeLeft, edgeRight);
    }

    private boolean checkPuzzleBottom(Puzzle puzzleTop, Puzzle puzzleBottom) {
        log.info("Checking puzzle bottom for puzzle: {} and puzzle: {}", puzzleTop, puzzleBottom);
        BufferedImage fragmentTop = getFragmentImage(puzzleTop);
        BufferedImage fragmentBottom = getFragmentImage(puzzleBottom);
        int[] edgeTop = getBottomEdge(fragmentTop);
        int[] edgeBottom = getTopEdge(fragmentBottom);
        return areEdgesMatching(edgeTop, edgeBottom);
    }


    public void resetPuzzles(UUID userId) {
        puzzlesMap.remove(userId);
        puzzleSizeMap.remove(userId);
        deletePuzzleImages(userId);
    }
}


