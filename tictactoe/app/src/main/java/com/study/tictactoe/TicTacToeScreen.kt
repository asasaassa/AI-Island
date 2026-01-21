package com.study.tictactoe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * 틱택토 게임의 메인 화면 Composable
 *
 * 게임 제목, 현재 플레이어/승자 표시, 게임 보드, 리셋 버튼으로 구성됩니다.
 * AI 예측은 빈 칸의 배경색으로 시각화됩니다 (어두울수록 좋은 수).
 *
 * @param gameState 현재 게임 상태
 * @param onCellClick 셀 클릭 이벤트 핸들러 (row, col)
 * @param onResetClick 리셋 버튼 클릭 이벤트 핸들러
 * @param modifier Composable modifier
 */
@Composable
fun TicTacToeScreen(
    gameState: GameState,
    onCellClick: (Int, Int) -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = "Tic Tac Toe",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Current player or game status
        Text(
            text = when {
                gameState.winner != null -> "Winner: ${gameState.winner}"
                gameState.isGameOver -> "Game Over - Draw!"
                else -> "Current Player: ${gameState.currentPlayer}"
            },
            style = MaterialTheme.typography.titleLarge,
            color = when (gameState.currentPlayer) {
                Player.X -> Color(0xFF2196F3) // Blue for X
                Player.O -> Color(0xFFF44336) // Red for O
                Player.NONE -> Color.Gray
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Game Board
        TicTacToeBoard(
            board = gameState.board,
            predictions = gameState.predictions,
            isGameOver = gameState.isGameOver,
            onCellClick = onCellClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Reset Button
        Button(
            onClick = onResetClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Text("Reset Game", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Hint
        if (!gameState.isGameOver) {
            Text(
                text = "밝은 칸 = AI가 추천하는 좋은 수",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * 3x3 틱택토 게임 보드 Composable
 *
 * 각 셀은 현재 플레이어의 말(X 또는 O)을 표시하고,
 * 빈 칸은 AI 예측 점수에 따라 배경색이 조절됩니다.
 *
 * @param board 게임 보드 상태
 * @param predictions AI 예측 점수 배열
 * @param isGameOver 게임 종료 여부
 * @param onCellClick 셀 클릭 이벤트 핸들러
 * @param modifier Composable modifier
 */
@Composable
fun TicTacToeBoard(
    board: Array<Array<Player>>,
    predictions: Array<DoubleArray>,
    isGameOver: Boolean,
    onCellClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // AI 예측 점수를 0-1 범위로 정규화 (시각화를 위해)
    // 빈 칸만 대상으로 최소값과 최대값을 찾음
    var minPred = Double.MAX_VALUE
    var maxPred = Double.MIN_VALUE

    for (i in 0..2) {
        for (j in 0..2) {
            if (board[i][j] == Player.NONE) {
                minPred = min(minPred, predictions[i][j])
                maxPred = max(maxPred, predictions[i][j])
            }
        }
    }

    val range = maxPred - minPred

    Column(modifier = modifier) {
        for (i in 0..2) {
            Row {
                for (j in 0..2) {
                    // 각 셀의 예측 점수를 0-1 범위로 정규화
                    // range가 0이면 (모든 예측이 같으면) 중간값 0.5 사용
                    val normalizedScore = if (board[i][j] == Player.NONE) {
                        if (range > 0.001) {
                            ((predictions[i][j] - minPred) / range).toFloat()
                        } else {
                            0.5f // 모든 예측이 같으면 중간 회색
                        }
                    } else {
                        0f
                    }

                    GameCell(
                        player = board[i][j],
                        predictionScore = normalizedScore,
                        isGameOver = isGameOver,
                        onClick = { onCellClick(i, j) }
                    )
                }
            }
        }
    }
}

/**
 * 게임 보드의 개별 셀 Composable
 *
 * X 또는 O를 표시하거나, 빈 칸인 경우 AI 예측 점수에 따라
 * 배경색을 조절합니다 (밝을수록 좋은 수).
 *
 * @param player 현재 셀의 플레이어 (NONE, X, O)
 * @param predictionScore AI 예측 점수 (0-1 정규화됨)
 * @param isGameOver 게임 종료 여부
 * @param onClick 클릭 이벤트 핸들러
 * @param modifier Composable modifier
 */
@Composable
fun GameCell(
    player: Player,
    predictionScore: Float,
    isGameOver: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // AI 예측 기반 배경색 (밝을수록 = 점수가 높음 = 좋은 수)
    // 0.3 ~ 1.0 범위로 조절 (너무 어둡거나 밝지 않게)
    val backgroundColor = if (player == Player.NONE && !isGameOver) {
        val intensity = 0.3f + predictionScore * 0.7f
        Color(intensity, intensity, intensity)
    } else {
        Color.White
    }

    Box(
        modifier = modifier
            .size(100.dp)
            .background(backgroundColor)
            .border(2.dp, Color.Black)
            // 게임이 끝나지 않고 빈 칸일 때만 클릭 가능
            .clickable(enabled = !isGameOver && player == Player.NONE) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (player) {
                Player.X -> "X"
                Player.O -> "O"
                Player.NONE -> ""
            },
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = when (player) {
                Player.X -> Color(0xFF2196F3) // 파란색 (X)
                Player.O -> Color(0xFFF44336) // 빨간색 (O)
                Player.NONE -> Color.Transparent
            }
        )
    }
}
