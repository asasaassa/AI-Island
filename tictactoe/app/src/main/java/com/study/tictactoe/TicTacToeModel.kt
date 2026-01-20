package com.study.tictactoe

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite 모델을 사용하여 틱택토 게임의 최적 수를 예측하는 클래스
 *
 * 이 클래스는 assets 폴더의 tictactoe.tflite 파일을 로드하고,
 * 현재 게임 상태를 기반으로 각 위치의 점수를 예측합니다.
 *
 * @param context Android Context (assets 폴더 접근을 위해 필요)
 */
class TicTacToeModel(context: Context) {
    /** TensorFlow Lite 인터프리터 인스턴스 */
    private var interpreter: Interpreter? = null

    init {
        try {
            // assets 폴더에서 모델 파일을 메모리에 매핑
            val modelBuffer = loadModelFile(context)
            // TFLite 인터프리터 초기화
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * assets 폴더에서 TFLite 모델 파일을 메모리 매핑 방식으로 로드
     *
     * 메모리 매핑을 사용하면 파일 전체를 메모리에 복사하지 않고
     * 필요한 부분만 페이지 단위로 로드하여 메모리 효율적입니다.
     *
     * @param context Android Context
     * @return 메모리 매핑된 모델 파일 버퍼
     */
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("tictactoe.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * 현재 플레이어를 위한 최적 수를 예측
     *
     * TensorFlow Lite 모델에 게임 보드 상태를 입력하고,
     * 각 위치에 대한 점수를 예측합니다. 점수가 높을수록 좋은 수입니다.
     *
     * @param board 3x3 게임 보드 배열
     *              - 0 = 빈 칸
     *              - 1 = X
     *              - 2 = O
     * @param currentPlayer 현재 플레이어 (1 = X, 2 = O)
     * @return 3x3 점수 배열 (각 위치의 점수, 높을수록 좋은 수)
     */
    fun predict(board: Array<IntArray>, currentPlayer: Int): Array<FloatArray> {
        // 게임 보드를 모델 입력 형식으로 변환
        val input = convertBoardToInput(board, currentPlayer)

        // 출력 배열 준비 - 모델은 batch dimension 없이 [3, 3] shape 사용
        val output = Array(3) { FloatArray(3) }

        // TFLite 모델 추론 실행
        interpreter?.run(input, output)

        return output
    }

    /**
     * 게임 보드를 TensorFlow Lite 모델의 입력 형식으로 변환
     *
     * 모델 입력 인코딩:
     * - 현재 플레이어의 말 = 1.0
     * - 상대방의 말 = -1.0
     * - 빈 칸 = 0.0
     *
     * 이렇게 인코딩하면 모델이 현재 플레이어 관점에서
     * 최적의 수를 찾을 수 있습니다.
     *
     * @param board 원본 게임 보드 (0, 1, 2로 표현)
     * @param currentPlayer 현재 플레이어 (1 = X, 2 = O)
     * @return 모델 입력용 3x3 float 배열
     */
    private fun convertBoardToInput(board: Array<IntArray>, currentPlayer: Int): Array<FloatArray> {
        val input = Array(3) { FloatArray(3) }

        for (i in 0..2) {
            for (j in 0..2) {
                input[i][j] = when (board[i][j]) {
                    0 -> 0f // 빈 칸
                    currentPlayer -> 1f // 현재 플레이어
                    else -> -1f // 상대방
                }
            }
        }

        return input
    }

    /**
     * TensorFlow Lite 인터프리터 리소스 해제
     *
     * ViewModel이 destroy될 때 호출되어야 합니다.
     */
    fun close() {
        interpreter?.close()
    }
}
