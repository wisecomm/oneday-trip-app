package kr.co.softone.onedaytrip

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class WebAppInterface(private val mContext: Context, private val webView: WebView) {

    companion object {
        private const val TAG = "WebAppInterface"
        private const val CHANNEL_ID = "download_channel"
    }

    /**
     * [Unified Bridge Architecture]
     * Web에서 JSON 문자열을 보냅니다:
     * { "action": "ACTION_NAME", "data": { ... }, "callbackId": "optional_id" }
     */
    @JavascriptInterface
    fun postMessage(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val action = jsonObject.optString("action")
            val data = jsonObject.optJSONObject("data")
            val callbackId = jsonObject.optString("callbackId")

            when (action) {
                "LOGOUT" -> logout()
                "EXIT_APP" -> exitApp()
                "SHOW_TOAST" -> {
                    val message = data?.optString("message") ?: ""
                    showToast(message)
                }
                "ERROR_DIALOG" -> {
                    val message = data?.optString("message") ?: mContext.getString(R.string.dialog_error_title)
                    handleServerError(message)
                }
                "GET_APP_VERSION" -> {
                    val versionInfo = JSONObject()
                    try {
                        val pInfo = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
                        versionInfo.put("versionName", pInfo.versionName)
                        versionInfo.put(
                            "versionCode",
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get app version", e)
                    }
                    sendResponse(callbackId, versionInfo)
                }
                // 기기 언어 확인 브릿지: oneday-trip의 다국어 (ko, en, ja, zh-CN, zh-TW)와 연동
                "GET_DEVICE_LANGUAGE" -> {
                    val langInfo = JSONObject()
                    val appLang = getDeviceAppLanguage()
                    val currentLocale = Locale.getDefault()
                    langInfo.put("language", appLang) // ko, en, ja, zh-CN, zh-TW
                    langInfo.put("languageTag", currentLocale.toLanguageTag())
                    sendResponse(callbackId, langInfo)
                }
                else -> Log.w(TAG, "Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bridge Error: ${e.message}")
        }
    }

    /**
     * 시스템 언어를 oneday-trip 지원 언어 코드(ko, en, ja, zh-CN, zh-TW)로 매핑
     * 기본값: ko (한국어)
     */
    private fun getDeviceAppLanguage(): String {
        val locale = Locale.getDefault()
        val language = locale.language.lowercase()
        val script = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) locale.script.lowercase() else ""
        val country = locale.country.uppercase()

        return when {
            language == "ko" -> "ko"
            language == "ja" -> "ja"
            language == "en" -> "en"
            language == "zh" -> {
                if (script.contains("hant") || country in setOf("TW", "HK", "MO")) {
                    "zh-TW"
                } else {
                    "zh-CN"
                }
            }
            else -> "ko" // 기본값은 한국어
        }
    }

    /**
     * Web으로 비동기 응답을 보냅니다 (Request-Response 패턴)
     */
    private fun sendResponse(callbackId: String?, data: JSONObject) {
        if (callbackId.isNullOrEmpty()) return

        val responseString = data.toString()
        webView.post {
            val js = "javascript:if(window.onNativeResponse) { window.onNativeResponse('$callbackId', JSON.stringify($responseString)); }"
            webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Native에서 Web으로 이벤트를 보냅니다 (Event-Listening 패턴)
     */
    fun sendEvent(eventName: String, data: JSONObject) {
        val dataString = data.toString()
        webView.post {
            val js = "javascript:if(window.onNativeEvent) { window.onNativeEvent('$eventName', JSON.stringify($dataString)); }"
            webView.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun showToast(toast: String) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun exitApp() {
        (mContext as? MainActivity)?.finish()
    }

    @JavascriptInterface
    fun logout() {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        Toast.makeText(mContext, mContext.getString(R.string.msg_logout_complete), Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun syncCookies() {
        android.webkit.CookieManager.getInstance().flush()
    }

    @JavascriptInterface
    fun handleServerError(message: String) {
        if (mContext is MainActivity) {
            mContext.runOnUiThread {
                AlertDialog.Builder(mContext)
                    .setTitle(mContext.getString(R.string.dialog_server_error_title))
                    .setMessage(message)
                    .setPositiveButton(mContext.getString(R.string.btn_confirm)) { _, _ -> }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    @JavascriptInterface
    fun downloadBlob(base64: String, mimeType: String, fileName: String) {
        if (mContext is MainActivity) {
            mContext.runOnUiThread {
                try {
                    val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
                    val finalFileName = if (fileName.isEmpty() || fileName == "null") {
                        "onedaytrip_${System.currentTimeMillis()}.${getExtensionFromMimeType(mimeType)}"
                    } else {
                        fileName
                    }

                    var finalMimeType = mimeType
                    val extension = finalFileName.substringAfterLast('.', "")
                    if (extension.isNotEmpty()) {
                        val typeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                        if (typeFromExt != null) {
                            finalMimeType = typeFromExt
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, finalMimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }

                        val resolver = mContext.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(decodedBytes)
                            }
                            Toast.makeText(
                                mContext,
                                "${mContext.getString(R.string.msg_download_complete)}: $finalFileName",
                                Toast.LENGTH_LONG
                            ).show()
                            showDownloadNotification(uri, finalMimeType, finalFileName)
                        } else {
                            Toast.makeText(mContext, mContext.getString(R.string.msg_download_failed), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadDir, finalFileName)
                        FileOutputStream(file).use { outputStream ->
                            outputStream.write(decodedBytes)
                        }
                        Toast.makeText(
                            mContext,
                            "${mContext.getString(R.string.msg_download_complete)}: ${file.name}",
                            Toast.LENGTH_LONG
                        ).show()

                        try {
                            val uri = FileProvider.getUriForFile(mContext, "${mContext.packageName}.provider", file)
                            showDownloadNotification(uri, finalMimeType, finalFileName)
                        } catch (e: Exception) {
                            Log.e(TAG, "FileProvider error", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Blob Download Failed", e)
                    Toast.makeText(mContext, "${mContext.getString(R.string.msg_download_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getExtensionFromMimeType(mimeType: String): String {
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            else -> "bin"
        }
    }

    private fun showDownloadNotification(uri: Uri, mimeType: String, fileName: String) {
        val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "File Download Notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        val pendingIntent = PendingIntent.getActivity(
            mContext,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(mContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(mContext.getString(R.string.msg_download_complete))
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
