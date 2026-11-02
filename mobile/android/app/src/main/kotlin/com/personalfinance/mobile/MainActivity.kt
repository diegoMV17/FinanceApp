package com.personalfinance.mobile

import android.os.Bundle
import android.view.WindowManager
import io.flutter.embedding.android.FlutterFragmentActivity

// FlutterFragmentActivity y no FlutterActivity: local_auth muestra el diálogo
// de huella o PIN del sistema (BiometricPrompt), que necesita una
// FragmentActivity.
class MainActivity : FlutterFragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE antes de que exista ninguna vista: la app sale en negro
        // en el selector de apps recientes y no admite capturas ni grabación
        // de pantalla. En una app que muestra saldos, deudas y nombres, una
        // captura es un dato que sale del teléfono sin que nadie lo decida.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        super.onCreate(savedInstanceState)
    }
}
