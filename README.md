# Picture Puzzle Creator and Solver

This project is designed to transform your pictures into puzzles and verify their correctness. It even has the
capability to solve the puzzles without the need for the original picture (though the results may not be perfect).

## Technologies Used

- Java 20 (+ Virtual Threads)
- Spring Boot 3.1
- Maven
- Lombok

## Description

The Picture Puzzle Creator and Solver is an innovative application that empowers you to turn your cherished pictures
into engaging puzzles. With this project, you can create puzzles from your own images and assess their accuracy.
Additionally, the application offers a unique feature where it can attempt to solve the puzzles even without access to
the original picture, although the results may not be flawless.

## Features

- **Picture Puzzle Creation**: Easily generate puzzles from your personal images.
- **Puzzle Correctness Verification**: Check the correctness of the puzzles created using your pictures.
- **Image Solver**: Attempt to solve puzzles even without the original picture (results may not be perfect).

## Installation and Setup

To use this project locally, please follow these steps:

1. Clone the repository: `git clone https://github.com/Stas293/Puzzles.git
2. Navigate to the project directory: `cd puzzles`
3. Ensure you have Java 20, Spring Boot 3.1, Maven, and Lombok installed on your system.
4. Build the project: `mvn clean install`
5. Run the application: `java -jar target/Puzzles-0.0.1-SNAPSHOT.jar`

## Usage

1. Launch the application.
2. Choose the image you want to convert into a puzzle.
3. Click the "Choose File" button to generate the puzzle from the image.
4. Verify the correctness of the puzzle with "Check Puzzle" button.
5. (Optional) If you want the application to solve the puzzle without the original picture, click the "Assemble Puzzle"
   button.
6. When you are done, click the "Reset Puzzle" button to start over.

## Contributing

Contributions are always welcome! If you'd like to contribute to this project, please follow these steps:

1. Fork the repository.
2. Create a new branch: `git checkout -b feature/your-feature-name`
3. Make your changes and commit them: `git commit -am 'Add some feature'`
4. Push to the branch: `git push origin feature/your-feature-name`
5. Submit a pull request.

## Contact

If you have any questions, suggestions, or issues, please feel free to reach out to me.

Thank you for using Picture Puzzle Creator and Solver!