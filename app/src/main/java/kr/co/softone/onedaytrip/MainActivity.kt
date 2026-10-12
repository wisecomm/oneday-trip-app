package kr.co.softone.onedaytrip

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        // 기본 서비스 URL (원격 배포된 하루여행 웹 URL)
        // 로컬 테스트 시 adb reverse tcp:5173 tcp:5173 후 "http://localhost:5173" 또는 "http://10.0.2.2:5173" 사용 가능
        const val DEFAULT_WEB_URL = "https://oneday-trip.vercel.app"
        private const val BACK_PRESS_INTERVAL_MS = 2000L
    }

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var locationPermissionCallback: ((Boolean) -> Unit)? = null
    private var lastBackPressTime = 0L

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d(TAG, "File Chooser Result: ${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            var results: Array<Uri>? = null
            if (intent != null) {
                val dataString = intent.dataString
                val clipData = intent.clipData

                if (clipData != null) {
                    results = Array(clipData.itemCount) { i ->
                        clipData.getItemAt(i).uri
                    }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
            Log.d(TAG, "File Selected: $results")
            fileUploadCallback?.onReceiveValue(results)
        } else {
            Log.d(TAG, "File Selection Cancelled")
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val isGranted = fineGranted || coarseGranted
        Log.d(TAG, "Location Permission Result: fine=$fineGranted, coarse=$coarseGranted")
        if (isGranted) {
            checkLocationServiceEnabled()
        }
        locationPermissionCallback?.invoke(isGranted)
        locationPermissionCallback = null
    }

    fun requestLocationPermission(onResult: (Boolean) -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            checkLocationServiceEnabled()
            onResult(true)
        } else {
            locationPermissionCallback = onResult
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkLocationServiceEnabled() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false

        if (!isGpsEnabled && !isNetworkEnabled) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.location_permission_title))
                .setMessage(getString(R.string.location_permission_message))
                .setPositiveButton(getString(R.string.btn_go_settings)) { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 웹뷰 인스턴스 생성 및 레이아웃 설정
        webView = WebView(this)
        setContentView(webView)

        // 2. WebViewManager 설정
        val webViewManager = WebViewManager(this, webView) { callback ->
            Log.d(TAG, "Launching File Chooser Intent")
            fileUploadCallback = callback
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            try {
                fileChooserLauncher.launch(Intent.createChooser(intent, "File Chooser"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch chooser", e)
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
            }
        }
        webViewManager.setup()

        // 3. URL 로딩
        webView.loadUrl(DEFAULT_WEB_URL)

        // 4. 뒤로가기 제어: 웹 히스토리 뒤로가기 우선, 메인에서는 2초 내 더블 클릭 시 종료
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL_MS) {
                        finish()
                    } else {
                        lastBackPressTime = currentTime
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.msg_back_press_exit),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
