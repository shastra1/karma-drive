package com.karmadrive.incar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.MutableStateFlow
import com.karmadrive.incar.ui.KarmaRoot
import com.karmadrive.incar.ui.KarmaTheme

class MainActivity : ComponentActivity() {

    /** 음성 명령 등 외부에서 특정 화면을 열어 달라는 요청 */
    val openRequest = MutableStateFlow<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            KarmaApp.instance.startLive()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            KarmaTheme {
                KarmaRoot(app = KarmaApp.instance, activity = this)
            }
        }
        permissionLauncher.launch(CAR_PERMISSIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN)?.let { openRequest.value = it }
    }

    override fun onStop() {
        super.onStop()
        // 백그라운드 진입 시 음성 · 애니메이션 정지 (기록은 RunRecorder가 계속)
        KarmaApp.instance.voice.stop()
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_REPLAY = "replay"

        val CAR_PERMISSIONS = arrayOf(
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_POWERTRAIN",
            "android.car.permission.CAR_EXTERIOR_LIGHTS",
            "android.car.permission.READ_CAR_STEERING",
            "android.car.permission.READ_WINDSHIELD_WIPERS",
            "android.car.permission.READ_CAR_SEAT_BELTS",
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_INFO",
            // Pleos 와이퍼는 실행 중 권한 확인이 필요할 수 있어 함께 요청 (일반 권한이면 바로 허용됨)
            "pleos.car.permission.READ_WINDSHIELD_WIPERS",
            "pleos.car.permission.CAR_WINDOWS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        )
    }
}
