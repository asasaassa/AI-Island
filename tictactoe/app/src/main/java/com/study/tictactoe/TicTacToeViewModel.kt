package com.study.tictactoe

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

/**
 * 틱택토 플레이어를 나타내는 enum
 */
enum class Player {
    NONE,  // 빈 칸
    X,     // X 플레이어 (사용자)
    O      // O 플레이어 (AI)
}

/**
 * AI 난이도
 */
enum class Difficulty(val bestMoveChance: Double) {
    EASY(0.70),    // 초급: 70% 확률로 최고 점수 칸
    MEDIUM(0.85),  // 중급: 85% 확률로 최고 점수 칸
    HARD(1.0)      // 고급: 100% 최고 점수 칸
}

/**
 * 틱택토 게임의 전체 상태를 담는 데이터 클래스
 *
 * @property board 3x3 게임 보드
 * @property currentPlayer 현재 차례의 플레이어
 * @property winner 승자 (null이면 아직 승자 없음)
 * @property predictions AI 모델의 예측 점수 (각 칸마다 얼마나 좋은 수인지)
 * @property isGameOver 게임 종료 여부 (승자가 나오거나 무승부)
 * @property difficulty AI 난이도
 */
data class GameState(
    val board: Array<Array<Player>> = Array(3) { Array(3) { Player.NONE } },
    val currentPlayer: Player = Player.X,
    val winner: Player? = null,
    val predictions: Array<DoubleArray> = Array(3) { DoubleArray(3) },
    val isGameOver: Boolean = false,
    val difficulty: Difficulty = Difficulty.MEDIUM
) {
    // Array는 구조적 동등성을 지원하지 않으므로 직접 구현
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (!board.contentDeepEquals(other.board)) return false
        if (currentPlayer != other.currentPlayer) return false
        if (winner != other.winner) return false
        if (!predictions.contentDeepEquals(other.predictions)) return false
        if (isGameOver != other.isGameOver) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.contentDeepHashCode()
        result = 31 * result + currentPlayer.hashCode()
        result = 31 * result + (winner?.hashCode() ?: 0)
        result = 31 * result + predictions.contentDeepHashCode()
        result = 31 * result + isGameOver.hashCode()
        return result
    }
}

/**
 * 틱택토 게임의 비즈니스 로직과 상태를 관리하는 ViewModel
 *
 * - 게임 보드 상태 관리
 * - 플레이어 수 처리
 * - 승자 판정
 * - TensorFlow Lite 모델을 통한 AI 예측
 *
 * @param application Application context (TFLite 모델 로드를 위해 필요)
 */
class TicTacToeViewModel(application: Application) : AndroidViewModel(application) {
    /** TensorFlow Lite 모델 인스턴스 */
    private val model = TicTacToeModel(application)

    /** 내부 게임 상태 (mutable) */
    private val _gameState = mutableStateOf(GameState())

    /** 외부에 노출되는 게임 상태 (read-only) */
    val gameState: State<GameState> = _gameState

    init {
        // 초기 게임 상태에서도 AI 예측 수행
        updatePredictions()
    }

    /**
     * 지정된 위치에 현재 플레이어의 말을 놓음
     *
     * Player X (사용자)만 직접 호출할 수 있습니다.
     * 게임이 끝났거나 해당 칸이 이미 차있으면 무시합니다.
     * 수를 둔 후 승자를 확인하고, 게임이 계속되면 플레이어를 교체합니다.
     *
     * @param row 행 인덱스 (0-2)
     * @param col 열 인덱스 (0-2)
     */
    fun makeMove(row: Int, col: Int) {
        val currentState = _gameState.value

        // Player X (사용자)만 직접 수를 둘 수 있음
        if (currentState.currentPlayer != Player.X) {
            return
        }

        // 게임이 끝났거나 이미 말이 있는 칸이면 무시
        if (currentState.isGameOver || currentState.board[row][col] != Player.NONE) {
            return
        }

        // 사용자의 수를 둠
        makeMoveInternal(row, col)

        // AI 차례이면 자동으로 수를 둠
        if (_gameState.value.currentPlayer == Player.O && !_gameState.value.isGameOver) {
            // AI가 생각하는 시간 (1.2초)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                makeAIMove()
            }, 1200)
        }
    }

    /**
     * 실제로 수를 두는 내부 함수
     */
    private fun makeMoveInternal(row: Int, col: Int) {
        val currentState = _gameState.value

        // 새 보드 생성 (불변성 유지)
        val newBoard = currentState.board.map { it.clone() }.toTypedArray()
        newBoard[row][col] = currentState.currentPlayer

        // 승자 확인
        val winner = checkWinner(newBoard)
        val isGameOver = winner != null || isBoardFull(newBoard)

        // 플레이어 교체
        val nextPlayer = if (currentState.currentPlayer == Player.X) Player.O else Player.X

        // 게임 상태 업데이트
        _gameState.value = currentState.copy(
            board = newBoard,
            currentPlayer = nextPlayer,
            winner = winner,
            isGameOver = isGameOver
        )

        // 게임이 계속되면 다음 플레이어를 위한 AI 예측 업데이트
        if (!isGameOver) {
            updatePredictions()
        }
    }

    /**
     * AI가 자동으로 수를 둠
     *
     * 우선순위:
     * 1. 이길 수 있으면 바로 이기기
     * 2. 상대가 이기려고 하면 막기
     * 3. 모델 예측 사용
     *
     * 난이도에 따라:
     * - 초급: 승리만 체크 (70% 확률), 방어 안 함
     * - 중급: 승리 100%, 방어 85% 확률
     * - 고급: 승리 + 방어 100%, 나머지는 모델 최고점
     */
    private fun makeAIMove() {
        val currentState = _gameState.value

        if (currentState.isGameOver || currentState.currentPlayer != Player.O) {
            return
        }

        val board = currentState.board
        val predictions = currentState.predictions
        val difficulty = currentState.difficulty

        var selectedCell: Pair<Int, Int>? = null
        var moveReason = "model"

        // 1순위: 이길 수 있는 칸 찾기
        when (difficulty) {
            Difficulty.EASY -> {
                // 초급: 70% 확률로만 승리 체크
                if (Math.random() < 0.7) {
                    selectedCell = findWinningMove(board, Player.O)
                    if (selectedCell != null) moveReason = "win"
                }
            }
            else -> {
                // 중급, 고급: 항상 승리 체크
                selectedCell = findWinningMove(board, Player.O)
                if (selectedCell != null) moveReason = "win"
            }
        }

        // 2순위: 상대가 이기려고 하면 막기
        if (selectedCell == null) {
            when (difficulty) {
                Difficulty.EASY -> {
                    // 초급: 방어 안 함
                }
                Difficulty.MEDIUM -> {
                    // 중급: 85% 확률로 방어
                    if (Math.random() < 0.85) {
                        selectedCell = findWinningMove(board, Player.X)
                        if (selectedCell != null) moveReason = "block"
                    }
                }
                Difficulty.HARD -> {
                    // 고급: 항상 방어
                    selectedCell = findWinningMove(board, Player.X)
                    if (selectedCell != null) moveReason = "block"
                }
            }
        }

        // 3순위: 모델 예측 사용
        if (selectedCell == null) {
            val emptyCells = mutableListOf<Pair<Int, Int>>()
            for (i in 0..2) {
                for (j in 0..2) {
                    if (board[i][j] == Player.NONE) {
                        emptyCells.add(Pair(i, j))
                    }
                }
            }

            if (emptyCells.isEmpty()) {
                return
            }

            selectedCell = if (Math.random() < difficulty.bestMoveChance) {
                // 최고 점수 칸 선택
                emptyCells.maxByOrNull { (i, j) -> predictions[i][j] }!!
            } else {
                // 랜덤 칸 선택
                emptyCells.random()
            }
        }

        Log.d("TicTacToe", "AI (${difficulty.name}) selects [$moveReason]: ${selectedCell.first}, ${selectedCell.second}")

        makeMoveInternal(selectedCell.first, selectedCell.second)
    }

    /**
     * 승리 가능한 칸 찾기 (또는 막아야 할 칸 찾기)
     *
     * 가로/세로/대각선에서 2개가 같은 플레이어이고 1개가 빈 칸인 경우를 찾습니다.
     *
     * @param board 게임 보드
     * @param player 찾을 플레이어 (Player.O면 AI 승리칸, Player.X면 막을칸)
     * @return 승리/방어 가능한 칸 (없으면 null)
     */
    private fun findWinningMove(board: Array<Array<Player>>, player: Player): Pair<Int, Int>? {
        // 가로 확인
        for (i in 0..2) {
            val emptyCol = checkLine(board[i][0], board[i][1], board[i][2], player)
            if (emptyCol != -1) {
                return Pair(i, emptyCol)
            }
        }

        // 세로 확인
        for (j in 0..2) {
            val emptyRow = checkLine(board[0][j], board[1][j], board[2][j], player)
            if (emptyRow != -1) {
                return Pair(emptyRow, j)
            }
        }

        // 대각선 확인 (좌상 -> 우하)
        val diag1Empty = checkLine(board[0][0], board[1][1], board[2][2], player)
        if (diag1Empty != -1) {
            return Pair(diag1Empty, diag1Empty)
        }

        // 대각선 확인 (우상 -> 좌하)
        val diag2Empty = checkLine(board[0][2], board[1][1], board[2][0], player)
        if (diag2Empty != -1) {
            return Pair(diag2Empty, 2 - diag2Empty)
        }

        return null
    }

    /**
     * 라인에서 2개가 같은 플레이어이고 1개가 빈 칸인지 확인
     *
     * @return 빈 칸의 인덱스 (0, 1, 2), 없으면 -1
     */
    private fun checkLine(cell1: Player, cell2: Player, cell3: Player, player: Player): Int {
        val cells = listOf(cell1, cell2, cell3)
        val playerCount = cells.count { it == player }
        val noneCount = cells.count { it == Player.NONE }

        // 2개가 해당 플레이어이고 1개가 빈 칸
        if (playerCount == 2 && noneCount == 1) {
            return cells.indexOfFirst { it == Player.NONE }
        }

        return -1
    }

    /**
     * TensorFlow Lite 모델을 사용하여 현재 플레이어의 최적 수 예측
     *
     * 게임 보드를 정수 배열로 변환하고 모델에 입력하여
     * 각 위치의 점수를 얻습니다. UI에서 이 점수를 시각화합니다.
     */
    private fun updatePredictions() {
        val currentState = _gameState.value

        // 보드를 모델 입력용 정수 배열로 변환
        val boardInt = Array(3) { IntArray(3) }
        for (i in 0..2) {
            for (j in 0..2) {
                boardInt[i][j] = when (currentState.board[i][j]) {
                    Player.NONE -> 0  // 빈 칸
                    Player.X -> 1     // X
                    Player.O -> 2     // O
                }
            }
        }

        // 현재 플레이어를 정수로 변환
        val currentPlayerInt = if (currentState.currentPlayer == Player.X) 1 else 2

        // AI 모델로 예측 수행
        val predictions = model.predict(boardInt, currentPlayerInt)

        // 디버깅: 예측 결과 로그 출력
        Log.d("TicTacToe", "Predictions for player $currentPlayerInt:")
        for (i in 0..2) {
            Log.d("TicTacToe", "  [${predictions[i][0]}, ${predictions[i][1]}, ${predictions[i][2]}]")
        }

        // 예측 결과를 게임 상태에 반영
        _gameState.value = currentState.copy(predictions = predictions)
    }

    /**
     * 게임 보드에서 승자가 있는지 확인
     *
     * 가로, 세로, 대각선을 모두 확인하여
     * 3개가 연속으로 같은 플레이어의 말이면 승리입니다.
     *
     * @param board 확인할 게임 보드
     * @return 승자 (없으면 null)
     */
    private fun checkWinner(board: Array<Array<Player>>): Player? {
        // 가로 확인 (3줄)
        for (i in 0..2) {
            if (board[i][0] != Player.NONE &&
                board[i][0] == board[i][1] &&
                board[i][1] == board[i][2]) {
                return board[i][0]
            }
        }

        // 세로 확인 (3줄)
        for (j in 0..2) {
            if (board[0][j] != Player.NONE &&
                board[0][j] == board[1][j] &&
                board[1][j] == board[2][j]) {
                return board[0][j]
            }
        }

        // 대각선 확인 (좌상 -> 우하)
        if (board[0][0] != Player.NONE &&
            board[0][0] == board[1][1] &&
            board[1][1] == board[2][2]) {
            return board[0][0]
        }

        // 대각선 확인 (우상 -> 좌하)
        if (board[0][2] != Player.NONE &&
            board[0][2] == board[1][1] &&
            board[1][1] == board[2][0]) {
            return board[0][2]
        }

        // 승자 없음
        return null
    }

    /**
     * 게임 보드가 꽉 찼는지 확인 (무승부 판정용)
     *
     * @param board 확인할 게임 보드
     * @return 빈 칸이 없으면 true
     */
    private fun isBoardFull(board: Array<Array<Player>>): Boolean {
        for (i in 0..2) {
            for (j in 0..2) {
                if (board[i][j] == Player.NONE) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * 게임을 초기 상태로 리셋
     *
     * 새 게임을 시작하고 AI 예측을 업데이트합니다.
     */
    fun resetGame() {
        val currentDifficulty = _gameState.value.difficulty
        _gameState.value = GameState(difficulty = currentDifficulty)
        updatePredictions()
    }

    /**
     * AI 난이도 변경
     *
     * 난이도 변경 시 게임을 자동으로 리셋합니다.
     */
    fun setDifficulty(difficulty: Difficulty) {
        Log.d("TicTacToe", "Difficulty changed to: ${difficulty.name}")
        _gameState.value = GameState(difficulty = difficulty)
        updatePredictions()
    }

    /**
     * ViewModel이 destroy될 때 호출
     *
     * TensorFlow Lite 모델 리소스를 해제합니다.
     */
    override fun onCleared() {
        super.onCleared()
        model.close()
    }
}

/**
 * TicTacToeViewModel을 생성하기 위한 Factory 클래스
 *
 * AndroidViewModel은 Application을 생성자 파라미터로 받기 때문에
 * 기본 ViewModelProvider로는 생성할 수 없습니다.
 * 이 Factory를 사용하여 Application 인스턴스를 주입합니다.
 *
 * @param application Application context
 */
class TicTacToeViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TicTacToeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TicTacToeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
