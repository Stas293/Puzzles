// Create a puzzle element dynamically
let puzzleWidth = 0;
let puzzleHeight = 0;


function createPuzzleElement(puzzle) {
    const puzzleContainer = document.getElementById('puzzle-container');
    const puzzleElement = document.createElement('img');
    puzzleElement.className = 'puzzle-piece';
    puzzleElement.id = puzzle.id;
    puzzleElement.style.width = puzzle.width + 'px';
    puzzleElement.style.height = puzzle.height + 'px';
    puzzleElement.style.left = puzzle.x + 'px';
    puzzleElement.style.top = puzzle.y + 'px';
    puzzleElement.src = '/api/puzzles/' + puzzle.id + '/image';

    puzzleWidth += puzzle.width;
    puzzleHeight += puzzle.height;

    puzzleContainer.appendChild(puzzleElement);
}

function makePuzzlePiecesDraggable() {
    let startLeft = 0;
    let startTop = 0;
    let draggingPiece = null;
    let draggingPieceInitialPosition = null;
    let overlappingTarget = null;

    $('.puzzle-piece').draggable({
        containment: '#puzzle-container',
        snap: '.puzzle-piece',
        snapMode: 'outer',
        snapTolerance: 50,
        start: function (event, ui) {
            draggingPiece = $(this);
            draggingPieceInitialPosition = {
                left: parseInt(draggingPiece.css('left')),
                top: parseInt(draggingPiece.css('top')),
            };
            startLeft = ui.position.left;
            startTop = ui.position.top;

            draggingPiece.addClass('dragging');

            // Bring the dragged piece to the top of the stacking order
            draggingPiece.css('z-index', '9999');
        },
        stop: function (event, ui) {
            draggingPiece.removeClass('dragging');

            // Reset the z-index of the dragged piece
            draggingPiece.css('z-index', '');

            const draggable = $(this);
            const draggableRect = draggable[0].getBoundingClientRect();

            $('.puzzle-piece').not(draggable).each(function () {
                const target = $(this);
                const targetRect = target[0].getBoundingClientRect();

                if (doRectsOverlap(draggableRect, targetRect)) {
                    // Store the overlapping target and its initial position
                    overlappingTarget = target;
                    overlappingTarget.addClass('overlapping');
                    overlappingTarget.css('z-index', '9999');
                    overlappingTarget.animate({
                        left: draggingPieceInitialPosition.left + 'px',
                        top: draggingPieceInitialPosition.top + 'px',
                    }, 200, function () {
                        overlappingTarget.removeClass('overlapping');
                        overlappingTarget.css('z-index', '');
                    });
                    return false;
                }
            });
        }
    });
}


function doRectsOverlap(rect1, rect2) {
    // they overlap if their centers are very close
    const center1 = {
        x: rect1.left + rect1.width / 2,
        y: rect1.top + rect1.height / 2,
    }
    const center2 = {
        x: rect2.left + rect2.width / 2,
        y: rect2.top + rect2.height / 2,
    }

    const dx = Math.abs(center1.x - center2.x);
    const dy = Math.abs(center1.y - center2.y);

    return dx < 10 && dy < 10;
}

function changePuzzleContainerSize() {
    const puzzleContainer = document.getElementById('puzzle-container');
    const puzzlePieces = document.getElementsByClassName('puzzle-piece');
    let maxRight = 0;
    let maxBottom = 0;

    for (let i = 0; i < puzzlePieces.length; i++) {
        const puzzlePiece = puzzlePieces[i];
        const puzzleRect = puzzlePiece.getBoundingClientRect();
        const right = puzzleRect.right;
        const bottom = puzzleRect.bottom;

        if (right > maxRight) {
            maxRight = right;
        }

        if (bottom > maxBottom) {
            maxBottom = bottom;
        }
    }

    const containerWidth = maxRight + window.scrollX - puzzleContainer.offsetLeft;
    const containerHeight = maxBottom + window.scrollY - puzzleContainer.offsetTop;

    const displayWidth = window.innerWidth;
    const displayHeight = window.innerHeight;

    const scaleFactor = Math.min(displayWidth / containerWidth, displayHeight / containerHeight);

    for (let i = 0; i < puzzlePieces.length; i++) {
        const puzzlePiece = puzzlePieces[i];
        const newWidth = puzzlePiece.offsetWidth * scaleFactor;
        const newHeight = puzzlePiece.offsetHeight * scaleFactor;

        // Adjust the position to reduce the space between pieces
        const adjustedLeft = puzzlePiece.offsetLeft * scaleFactor;
        const adjustedTop = puzzlePiece.offsetTop * scaleFactor;

        puzzlePiece.style.width = newWidth + 'px';
        puzzlePiece.style.height = newHeight + 'px';
        puzzlePiece.style.left = adjustedLeft + 'px';
        puzzlePiece.style.top = adjustedTop + 'px';
    }

    puzzleContainer.style.width = containerWidth * scaleFactor + 'px';
    puzzleContainer.style.height = containerHeight * scaleFactor + 'px';
}


// Load an image
function loadImage(event) {
    const file = event.target.files[0];
    const formData = new FormData();
    formData.append('image', file);

    const puzzleContainer = document.getElementById('puzzle-container');
    if (puzzleContainer.children.length > 0) {
        puzzleContainer.innerHTML = '';
        event.target.files = null;
    }


    $.ajax({
        url: '/api/puzzles/upload',
        type: 'POST',
        data: formData,
        processData: false,
        contentType: false,
        success: function (puzzles) {
            $.ajax({
                url: '/api/puzzles',
                type: 'GET',
                success: function (puzzles) {
                    puzzles.forEach(puzzle => {
                        createPuzzleElement(puzzle);
                    });
                    changePuzzleContainerSize();
                    makePuzzlePiecesDraggable();
                }
            });
        }
    });
}

// Check the puzzle
// Check the puzzle
function checkPuzzle() {
    const puzzlePieces = Array.from(document.getElementsByClassName('puzzle-piece'));
    const puzzleData = puzzlePieces.map(puzzlePiece => {
        return {
            id: puzzlePiece.id,
            x: parseInt(puzzlePiece.style.left),
            y: parseInt(puzzlePiece.style.top),
            width: puzzlePiece.offsetWidth,
            height: puzzlePiece.offsetHeight,
        };
    });

    fetch('/api/puzzles/check', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(puzzleData),
    })
        .then(response => response.ok ? response.json() : Promise.reject('Error checking puzzle'))
        .then(result => {
            if (result === true) {
                alert('Puzzle is correct!');
            } else {
                alert('Puzzle is incorrect!');
            }
        })
        .catch(error => console.error('Error:', error));
}


function updatePuzzleElement(puzzle) {
    const puzzleElement = document.getElementById(puzzle.id);
    puzzleElement.style.left = puzzle.x + 'px';
    puzzleElement.style.top = puzzle.y + 'px';
    puzzleElement.style.width = puzzle.width + 'px';
    puzzleElement.style.height = puzzle.height + 'px';
}

function updatePuzzlesMap(result) {
    result.forEach(puzzle => {
        updatePuzzleElement(puzzle);
    });
    changePuzzleContainerSize();
}

// Assemble the puzzle
function assemblePuzzle() {
    fetch('/api/puzzles/assemble', {
            method: 'POST'
        }
    )
        .then(response => response.ok ? response.json() : Promise.reject('Error assembling puzzle'))
        .then(result => {
            alert('Puzzle successfully assembled!');
            updatePuzzlesMap(result);
        })
        .catch(error => console.error('Error:', error)).then(r => r);
}


function resetPuzzle() {
    fetch('/api/puzzles/reset', {
            method: 'POST'
    }).then(response => response.ok ? response.json() : Promise.reject('Error assembling puzzle'))
    window.location.reload();
}