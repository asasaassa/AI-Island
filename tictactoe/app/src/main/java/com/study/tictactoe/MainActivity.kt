package com.study.tictactoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.study.tictactoe.ui.theme.TictactoeTheme

/**
 * 틱택토 앱의 메인 액티비티
 *
 * TensorFlow Lite 모델을 사용하여 AI가 추천하는 수를 시각화하는
 * 틱택토 게임을 제공합니다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 바 뒤로 컨텐츠 확장 (edge-to-edge UI)
        enableEdgeToEdge()

        setContent {
            TictactoeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Application context 가져오기 (ViewModel Factory에 필요)
                    val context = LocalContext.current

                    // ViewModel 초기화 (Factory를 통해 Application 주입)
                    val viewModel: TicTacToeViewModel = viewModel(
                        factory = TicTacToeViewModelFactory(
                            context.applicationContext as android.app.Application
                        )
                    )

                    // 게임 화면 표시
                    TicTacToeScreen(
                        gameState = viewModel.gameState.value,
                        onCellClick = { row, col -> viewModel.makeMove(row, col) },
                        onResetClick = { viewModel.resetGame() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}