package com.study.tictactoe

import android.app.Application
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
    X,     // X 플레이어
    O      // O 플레이어
}

/**
 * 틱택토 게임의 전체 상태를 담는 데이터 클래스
 *
 * @property board 3x3 게임 보드
 * @property currentPlayer 현재 차례의 플레이어
 * @property winner 승자 (null이면 아직 승자 없음)
 * @property predictions AI 모델의 예측 점수 (각 칸마다 얼마나 좋은 수인지)
 * @property isGameOver 게임 종료 여부 (승자가 나오거나 무승부)
 */
data class GameState(
    val board: Array<Array<Player>> = Array(3) { Array(3) { Player.NONE } },
    val currentPlayer: Player = Player.X,
    val winner: Player? = null,
    val predictions: Array<FloatArray> = Array(3) { FloatArray(3) },
    val isGameOver: Boolean = false
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
     * 게임이 끝났거나 해당 칸이 이미 차있으면 무시합니다.
     * 수를 둔 후 승자를 확인하고, 게임이 계속되면 플레이어를 교체합니다.
     *
     * @param row 행 인덱스 (0-2)
     * @param col 열 인덱스 (0-2)
     */
    fun makeMove(row: Int, col: Int) {
        val currentState = _gameState.value

        // 게임이 끝났거나 이미 말이 있는 칸이면 무시
        if (currentState.isGameOver || currentState.board[row][col] != Player.NONE) {
            return
        }

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
        _gameState.value = GameState()
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
