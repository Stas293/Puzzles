package com.projects.puzzles.service;

import com.projects.puzzles.dto.PuzzleCheckDto;
import com.projects.puzzles.dto.PuzzleDto;
import com.projects.puzzles.mapper.PuzzleCheckDtoMapper;
import com.projects.puzzles.mapper.PuzzleDtoMapper;
import com.projects.puzzles.model.Puzzle;
import com.projects.puzzles.utility.Adjacent;
import com.projects.puzzles.utility.Pair;
import com.projects.puzzles.utility.PuzzleConfig;
import com.projects.puzzles.utility.PuzzleDimentions;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toCollection;

@Service
@Slf4j
@RequiredArgsConstructor
public class PuzzleService {
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final PuzzleConfig puzzleConfig;
    private final PuzzleDtoMapper puzzleDtoMapper;
    private final PuzzleCheckDtoMapper puzzleCheckDtoMapper;
    private final Map<UUID, Map<Integer, Puzzle>> puzzlesMap = new ConcurrentHashMap<>();
    private final Map<UUID, Pair<Integer, Integer>> puzzleSizeMap = new ConcurrentHashMap<>();

    private double getMeanDiff(int[] edge1, int[] edge2) {
        int totalDiff = 0;

        for (int i = 0; i < edge1.length; i++) {
            int pixel1 = edge1[i];
            int pixel2 = edge2[i];

            int r1 = (pixel1 >> 16) & 0xFF;
            int g1 = (pixel1 >> 8) & 0xFF;
            int b1 = pixel1 & 0xFF;

            int r2 = (pixel2 >> 16) & 0xFF;
            int g2 = (pixel2 >> 8) & 0xFF;
            int b2 = pixel2 & 0xFF;

            boolean colorsMatch = Math.abs(r1 - r2) <= puzzleConfig.colorThreshold() &&
                    Math.abs(g1 - g2) <= puzzleConfig.colorThreshold() &&
                    Math.abs(b1 - b2) <= puzzleConfig.colorThreshold();

            if (!colorsMatch) {
                log.info("Pixels at index {} do not match", i);
                log.info("Pixel 1: ({}, {}, {})", r1, g1, b1);
                log.info("Pixel 2: ({}, {}, {})", r2, g2, b2);
                totalDiff++;
            }
        }

        return (double) totalDiff / edge1.length;
    }

    private Puzzle setPuzzle(PuzzleDimentions puzzleDimention, Integer puzzleId, String puzzleImageName) {
        return Puzzle.builder()
                .id(puzzleId)
                .x(puzzleId % puzzleConfig.numPuzzlesX() * puzzleDimention.puzzleWidth())
                .y(puzzleId / puzzleConfig.numPuzzlesX() * puzzleDimention.puzzleHeight())
                .width(puzzleDimention.puzzleWidth())
                .height(puzzleDimention.puzzleHeight())
                .imageName(puzzleImageName)
                .build();
    }

    private static BufferedImage getBufferedImage(MultipartFile image) throws IOException {
        byte[] imageBytes = image.getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        return ImageIO.read(bais);
    }

    private static void updateCoordinatesOfCurrentFragments(List<List<Puzzle>> puzzleRows,
                                                            int puzzleWidth, int puzzleHeight) {
        for (int i = 0; i < puzzleRows.size(); i++) {
            List<Puzzle> puzzleRow = puzzleRows.get(i);
            for (int j = 0; j < puzzleRow.size(); j++) {
                Puzzle puzzle = puzzleRow.get(j);
                puzzle.setX(j * puzzleWidth);
                puzzle.setY(i * puzzleHeight);
            }
        }
    }

    private static void deleteWorseAdjacent(Map<Adjacent, Double> error1, List<Adjacent> copyAdjacents) {
        Adjacent adjacentWithHighestError = error1.entrySet()
                .stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .get()
                .getKey();
        copyAdjacents.remove(adjacentWithHighestError);
    }

    private static void removeExistingAdjacents(List<Adjacent> adjacents, Map<Adjacent, Double> error1,
                                                Map<Adjacent, Double> error2, List<Adjacent> copyAdjacents) {
        adjacents.stream()
                .filter(adjacent1 -> error2.get(adjacent1) != null)
                .filter(adjacent1 -> error1.get(adjacent1) > error2.get(adjacent1))
                .forEach(copyAdjacents::remove);
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down executor");
        executor.shutdown();
    }

    @SneakyThrows
    public void divideIntoPuzzles(UUID id, MultipartFile image) {
        BufferedImage fullImage = getBufferedImage(image);

        PuzzleDimentions puzzleDimention = getPuzzleDimentions(id, fullImage);

        Map<Integer, Puzzle> puzzles = new HashMap<>();
        if (checkIfTheUserFolderExists(id)) {
            deletePuzzleImages(id);
        }
        List<Integer> shuffledPuzzleIds = IntStream.range(0, puzzleConfig.numPuzzlesX() * puzzleConfig.numPuzzlesY())
                .boxed()
                .collect(toCollection(ArrayList::new));
        Collections.shuffle(shuffledPuzzleIds);
        savePuzzles(id, image, fullImage, puzzleDimention, puzzles, shuffledPuzzleIds);
        puzzlesMap.put(id, puzzles);
    }

    private void savePuzzles(UUID id, MultipartFile image, BufferedImage fullImage, PuzzleDimentions puzzleDimention,
                             Map<Integer, Puzzle> puzzles, List<Integer> shuffledPuzzleIds) {
        for (int y = 0; y < puzzleConfig.numPuzzlesY(); y++) {
            for (int x = 0; x < puzzleConfig.numPuzzlesX(); x++) {
                BufferedImage puzzleImage = fullImage.getSubimage(
                        x * puzzleDimention.puzzleWidth(),
                        y * puzzleDimention.puzzleHeight(),
                        puzzleDimention.puzzleWidth(),
                        puzzleDimention.puzzleHeight());
                Integer puzzleId = shuffledPuzzleIds.get(y * puzzleConfig.numPuzzlesX() + x);
                String puzzleImageName = "%s/%s_%d.jpg".formatted(id, image.getName(), puzzleId);

                Puzzle puzzle = setPuzzle(puzzleDimention, puzzleId, puzzleImageName);

                savePuzzleImage(puzzleImage, puzzleImageName);

                puzzles.put(puzzleId, puzzle);
            }
        }
    }

    private PuzzleDimentions getPuzzleDimentions(UUID id, BufferedImage fullImage) {
        int puzzleWidth = fullImage.getWidth() / puzzleConfig.numPuzzlesX();
        int puzzleHeight = fullImage.getHeight() / puzzleConfig.numPuzzlesY();
        puzzleSizeMap.put(id, new Pair<>(puzzleWidth, puzzleHeight));
        return new PuzzleDimentions(puzzleWidth, puzzleHeight);
    }

    private boolean checkIfTheUserFolderExists(UUID id) {
        String filePath = puzzleConfig.pathToPuzzleImagesDirectory() + id.toString();
        File file = new File(filePath);
        return file.exists() && file.isDirectory();
    }

    @SneakyThrows
    private void deletePuzzleImages(UUID id) {
        String filePath = puzzleConfig.pathToPuzzleImagesDirectory() + id.toString();
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

    @SneakyThrows
    private void savePuzzleImage(BufferedImage image, String imageName) {
        String filePath = puzzleConfig.pathToPuzzleImagesDirectory() + imageName;
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

        List<Callable<Void>> tasks = puzzles.stream().<Callable<Void>>map(puzzle -> () -> {
            puzzles.stream()
                    .filter(puzzle2 -> puzzle.getId() != puzzle2.getId())
                    .forEach(puzzle2 -> getAdjacents(adjacentListPuzzles, puzzle, puzzle2));
            return null;
        }).toList();

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

    private List<List<Puzzle>> calculatePuzzleFragmentMatrix(Map<Pair<Puzzle, Puzzle>, Adjacent> adjacentPuzzles,
                                                             Puzzle currentPuzzle) {
        List<List<Puzzle>> puzzleRows = new ArrayList<>();
        for (int i = 0; i < puzzleConfig.numPuzzlesY(); i++) {
            List<Puzzle> puzzleRow = new ArrayList<>();
            for (int j = 0; j < puzzleConfig.numPuzzlesX(); j++) {
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

    private Adjacent findCorrectAdjacent(Map<Pair<Puzzle, Puzzle>, List<Adjacent>> adjacentListPuzzles,
                                         List<Adjacent> adjacents, Puzzle puzzle1, Puzzle puzzle2) {
        log.info("Puzzle1: {}, Puzzle2: {}", puzzle1.getId(), puzzle2.getId());
        Map<Adjacent, Double> error1 = calculateError(puzzle1, puzzle2, adjacents);
        log.info("Error1: {}", error1);
        List<Pair<Puzzle, Puzzle>> pairWithSameFirstButDifferentSecond = adjacentListPuzzles.keySet()
                .stream()
                .filter(p -> p.getFirst().getId() == puzzle1.getId() && p.getSecond().getId() != puzzle2.getId())
                .toList();
        log.info("Pair with same first but different second: {}", pairWithSameFirstButDifferentSecond);
        Map<Adjacent, Double> error2 = new EnumMap<>(Adjacent.class);
        calculateErrorForPairsWithSameFirst(adjacentListPuzzles, puzzle1, pairWithSameFirstButDifferentSecond, error2, adjacents);
        log.info("Error2: {}", error2);
        List<Adjacent> copyAdjacents = new ArrayList<>(adjacents);
        removeExistingAdjacents(adjacents, error1, error2, copyAdjacents);
        log.info("Copy adjacents: {}", copyAdjacents);
        if (copyAdjacents.size() > 1) {
            deleteWorseAdjacent(error1, copyAdjacents);
        }
        return copyAdjacents.get(0);
    }

    private void calculateErrorForPairsWithSameFirst(Map<Pair<Puzzle, Puzzle>, List<Adjacent>> adjacentListPuzzles,
                                                     Puzzle puzzle1, List<Pair<Puzzle, Puzzle>> pairWithSameFirstButDifferentSecond,
                                                     Map<Adjacent, Double> error2, List<Adjacent> adjacents) {
        pairWithSameFirstButDifferentSecond.forEach(pair2 -> {
            Puzzle puzzle3 = pair2.getSecond();
            List<Adjacent> adjacents2 = adjacentListPuzzles.get(pair2);
            if (adjacents2.size() != 1) {
                List<Adjacent> commonAdjacents = adjacents.stream()
                        .filter(adjacents2::contains)
                        .toList();
                if (commonAdjacents.size() > 1) {
                    log.info("There are more than 1 common adjacents: {}", commonAdjacents);
                    log.info("Adjacents: {}", adjacents);
                    log.info("Adjacents pair2: {}", adjacents2);
                    log.info("Pair2: {}", pair2);
                    throw new RuntimeException("There are more than 1 adjacents");
                }
            }
            error2.put(adjacents2.get(0), calculateError(puzzle1, puzzle3, adjacents2).get(adjacents2.get(0)));
        });
    }

    private void getAdjacents(Map<Pair<Puzzle, Puzzle>, List<Adjacent>> adjacentListPuzzles, Puzzle puzzle, Puzzle puzzle2) {
        List<Adjacent> adjacents = areAdjacent(puzzle, puzzle2);
        if (!adjacents.isEmpty()) {
            adjacentListPuzzles.put(new Pair<>(puzzle, puzzle2), adjacents);
        }
    }

    private Map<Adjacent, Double> calculateError(Puzzle puzzle1, Puzzle puzzle2, List<Adjacent> adjacents) {
        return adjacents.stream()
                .collect(Collectors.toMap(
                        adjacent -> adjacent,
                        adjacent -> calculateError(puzzle1, puzzle2, adjacent),
                        (a, b) -> b, () -> new EnumMap<>(Adjacent.class))
                );
    }

    private Double calculateError(Puzzle puzzle1, Puzzle puzzle2, Adjacent adjacent) {
        return switch (adjacent) {
            case LEFT -> getMeanDiff(getLeftEdge(getFragmentImage(puzzle1)), getRightEdge(getFragmentImage(puzzle2)));
            case TOP -> getMeanDiff(getTopEdge(getFragmentImage(puzzle1)), getBottomEdge(getFragmentImage(puzzle2)));
            case RIGHT -> getMeanDiff(getRightEdge(getFragmentImage(puzzle1)), getLeftEdge(getFragmentImage(puzzle2)));
            case BOTTOM -> getMeanDiff(getBottomEdge(getFragmentImage(puzzle1)), getTopEdge(getFragmentImage(puzzle2)));
            case null -> throw new RuntimeException("Adjacent not found");
        };
    }

    private Optional<Puzzle> getAdjacentPuzzle(Map<Pair<Puzzle, Puzzle>, Adjacent> adjacentPuzzles, Puzzle firstPuzzle, Adjacent adjacent) {
        List<Puzzle> puzzleList = adjacentPuzzles.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getFirst().getId() == firstPuzzle.getId() && entry.getValue() == adjacent)
                .map(entry -> entry.getKey().getSecond())
                .toList();

        return checkMorePreciseAdjacent(firstPuzzle, adjacent, puzzleList);
    }

    private Optional<Puzzle> getAdjacentPuzzleInverted(Map<Pair<Puzzle, Puzzle>, Adjacent> adjacentPuzzles, Puzzle firstPuzzle, Adjacent adjacent) {
        return adjacentPuzzles.entrySet()
                .stream()
                .filter(entry -> {
                    Puzzle first = entry.getKey().getFirst();
                    Puzzle second = entry.getKey().getSecond();
                    Adjacent value = entry.getValue();

                    if (adjacent == Adjacent.LEFT || adjacent == Adjacent.TOP) {
                        return first.getId() == firstPuzzle.getId() && value == adjacent;
                    } else {
                        return second.getId() == firstPuzzle.getId() && value == adjacent;
                    }
                })
                .map(entry -> {
                    if (adjacent == Adjacent.LEFT || adjacent == Adjacent.TOP) {
                        return entry.getKey().getSecond();
                    } else {
                        return entry.getKey().getFirst();
                    }
                })
                .findFirst();

    }

    private Optional<Puzzle> checkMorePreciseAdjacent(Puzzle firstPuzzle, Adjacent adjacent, List<Puzzle> puzzleList) {
        Map<Puzzle, Double> puzzleDifference = new HashMap<>();

        for (Puzzle puzzle : puzzleList) {
            BufferedImage image1 = getFragmentImage(firstPuzzle);
            BufferedImage image2 = getFragmentImage(puzzle);
            double difference = switch (adjacent) {
                case LEFT -> getMeanDiff(getLeftEdge(image1), getRightEdge(image2));
                case TOP -> getMeanDiff(getTopEdge(image1), getBottomEdge(image2));
                case RIGHT -> getMeanDiff(getRightEdge(image1), getLeftEdge(image2));
                case null, default ->  // Adjacent.BOTTOM
                        getMeanDiff(getBottomEdge(image1), getTopEdge(image2));
            };

            puzzleDifference.put(puzzle, difference);
        }

        return puzzleDifference.entrySet()
                .stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
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
        return IntStream.range(0, height)
                .map(y -> image.getRGB(image.getWidth() - 1, y))
                .toArray();
    }

    private int[] getLeftEdge(BufferedImage image) {
        int height = image.getHeight();
        return IntStream.range(0, height)
                .map(y -> image.getRGB(0, y))
                .toArray();
    }

    private int[] getTopEdge(BufferedImage image) {
        int width = image.getWidth();
        return IntStream.range(0, width)
                .map(x -> image.getRGB(x, 0))
                .toArray();
    }

    private int[] getBottomEdge(BufferedImage image) {
        int width = image.getWidth();
        return IntStream.range(0, width)
                .map(x -> image.getRGB(x, image.getHeight() - 1))
                .toArray();
    }

    private boolean areEdgesMatching(int[] edge1, int[] edge2) {
        // Compare the edges by matching the colors of corresponding pixels
        log.info("Comparing edges of length: {} and {}", edge1.length, edge2.length);
        // use mean percentage of color difference
        double meanDiff = getMeanDiff(edge1, edge2);
        log.info("Mean difference: {}", meanDiff);
        return meanDiff <= puzzleConfig.meanErrorProbabilityThreshold();
    }

    @SneakyThrows
    public BufferedImage getFragmentImage(Puzzle puzzle) {
        String filePath = puzzleConfig.pathToPuzzleImagesDirectory() + puzzle.getImageName();
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
        String filePath = puzzleConfig.pathToPuzzleImagesDirectory() + puzzle.getImageName();

        return Files.readAllBytes(Paths.get(filePath));
    }

    @SneakyThrows
    public boolean checkPuzzles(UUID userId, List<PuzzleCheckDto> puzzleCheckDtos) {
        Map<Integer, Puzzle> puzzles = puzzlesMap.get(userId);
        if (puzzles == null) {
            return false;
        }
        log.info("Checking puzzles for user: {}", userId);
        PuzzleDimentions puzzleDimentions = getPuzzleDimentions(userId);
        log.info("Puzzle width: {}, height: {}", puzzleDimentions.puzzleWidth(), puzzleDimentions.puzzleHeight());
        puzzleCheckDtos.sort((o1, o2) -> {
            if (Math.abs(o1.y() - o2.y()) < puzzleDimentions.puzzleHeight() / 2) {
                return o1.x() - o2.x();
            }
            return o1.y() - o2.y();
        });
        List<List<Puzzle>> puzzleMatrix = getPuzzleMatrix(puzzleCheckDtos, puzzles);
        log.info("Puzzle matrix: {}", puzzleMatrix);
        return checkPuzzleMatrix(puzzleMatrix);
    }

    private List<List<Puzzle>> getPuzzleMatrix(List<PuzzleCheckDto> puzzleCheckDtos, Map<Integer, Puzzle> puzzles) {
        List<Puzzle> puzzleList = puzzleCheckDtoMapper.puzzleCheckDtos(puzzleCheckDtos)
                .stream()
                .map(puzzleCheckDto -> puzzles.get(puzzleCheckDto.getId()))
                .toList();
        List<List<Puzzle>> puzzleMatrix = new ArrayList<>();
        for (int i = 0; i < puzzleConfig.numPuzzlesY(); i++) {
            puzzleMatrix.add(new ArrayList<>());
            for (int j = 0; j < puzzleConfig.numPuzzlesX(); j++) {
                puzzleMatrix.get(i).add(puzzleList.get(i * puzzleConfig.numPuzzlesX() + j));
            }
        }
        return puzzleMatrix;
    }

    private PuzzleDimentions getPuzzleDimentions(UUID userId) {
        int puzzleWidth = puzzleSizeMap.get(userId).first();
        int puzzleHeight = puzzleSizeMap.get(userId).second();
        return new PuzzleDimentions(puzzleWidth, puzzleHeight);
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


