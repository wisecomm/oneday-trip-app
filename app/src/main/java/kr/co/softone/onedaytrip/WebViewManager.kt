package kr.co.softone.onedaytrip

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.net.URISyntaxException
import java.util.Locale

class WebViewManager(
    private val context: Context,
    private val webView: WebView,
    private val onFileChooser: (ValueCallback<Array<Uri>>?) -> Unit
) {

    companion object {
        private const val TAG = "WebViewManager"
        private const val NAVER_MAP_PACKAGE = "com.nhn.android.nmap"
    }

    fun setup() {
        // 0. 웹뷰 디버깅 활성화 (chrome://inspect/#devices 원격 디버깅용)
        WebView.setWebContentsDebuggingEnabled(true)

        // 1. 웹뷰 설정 (WebSettings)
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.setGeolocationEnabled(true)
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // User Agent에 앱 식별자 추가 (웹에서 웹뷰인지 판별할 때 유용)
        val defaultUserAgent = webSettings.userAgentString
        webSettings.userAgentString = "$defaultUserAgent OneDayTripApp/1.0.0"

        // 쿠키 설정 (로그인 세션 유지)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // 2. 웹 클라이언트 설정
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val url = uri.toString()

                // http, https 는 웹뷰 내부에서 처리
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }

                // 네이버 지도 스킴 (nmap://route/walk 등)
                if (url.startsWith("nmap://")) {
                    return handleNaverMapScheme(uri)
                }

                // intent:, tel:, mailto:, sms: 등 외부 앱 및 스킴 처리
                try {
                    if (url.startsWith("intent:")) {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        if (context.packageManager.resolveActivity(intent, 0) != null) {
                            context.startActivity(intent)
                            return true
                        }
                        // 앱 미설치 시 마켓으로 이동 시도
                        val packageName = intent.`package`
                        if (!packageName.isNullOrEmpty()) {
                            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                            context.startActivity(marketIntent)
                            return true
                        }
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle scheme: $url", e)
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "Page Started: $url")
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                Log.e(TAG, "Page Error: ${error?.description}, Code: ${error?.errorCode}")

                if (request?.isForMainFrame == true) {
                    AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.dialog_error_title))
                        .setMessage("${context.getString(R.string.msg_page_load_error)}\n${error?.description}")
                        .setPositiveButton(context.getString(R.string.btn_confirm), null)
                        .show()
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e(TAG, "HTTP Error: ${errorResponse?.statusCode}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // JSON 에러 응답 감지 및 파일 다운로드 훅
                view?.evaluateJavascript(
                    """
                    (function() {
                        try {
                            var content = document.body.innerText;
                            if (content.trim().startsWith('{"status":"error"')) {
                                var json = JSON.parse(content);
                                if (window.AndroidBridge && window.AndroidBridge.handleServerError) {
                                    window.AndroidBridge.handleServerError(json.message);
                                }
                            }
                        } catch(e) {}

                        // Blob Revocation 방지
                        window.URL.revokeObjectURL = function(url) {
                             console.log("WebViewManager: Blocked revokeObjectURL for " + url);
                        };

                        // 다운로드 링크 캡처
                        if (!window.__downloadMap) {
                            window.__downloadMap = {};
                            var oldClick = HTMLAnchorElement.prototype.click;
                            HTMLAnchorElement.prototype.click = function() {
                                var href = this.href;
                                var download = this.download;
                                if (href && download) {
                                    window.__downloadMap[href] = download;
                                }
                                oldClick.apply(this, arguments);
                            };
                        }
                    })();
                    """.trimIndent(), null
                )
            }
        }

        // 3. WebChromeClient 설정
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.dialog_alert_title))
                    .setMessage(message)
                    .setPositiveButton(context.getString(R.string.btn_confirm)) { _, _ -> result?.confirm() }
                    .setNegativeButton(context.getString(R.string.btn_cancel)) { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.dialog_alert_title))
                    .setMessage(message)
                    .setPositiveButton(context.getString(R.string.btn_confirm)) { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                Log.d(TAG, "onShowFileChooser called")
                onFileChooser(filePathCallback)
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebViewConsole", "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()}")
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                Log.d(TAG, "onGeolocationPermissionsShowPrompt: $origin")
                if (context is MainActivity) {
                    context.requestLocationPermission { isGranted ->
                        callback?.invoke(origin, isGranted, true)
                    }
                } else {
                    callback?.invoke(origin, true, true)
                }
            }
        }

        // 4. Javascript Interface 등록
        webView.addJavascriptInterface(WebAppInterface(context, webView), "AndroidBridge")

        // 5. 다운로드 리스너 설정
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.startsWith("blob:")) {
                handleBlobDownload(url, mimetype, contentDisposition)
                return@setDownloadListener
            }

            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimetype)
                val cookies = CookieManager.getInstance().getCookie(url)
                request.addRequestHeader("cookie", cookies)
                request.addRequestHeader("User-Agent", userAgent)
                request.setDescription("Downloading file...")
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)

                Toast.makeText(context, context.getString(R.string.msg_download_start), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                Toast.makeText(context, "${context.getString(R.string.msg_download_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 네이버 지도 스킴 처리:
     * 네이버 지도 앱 설치 여부를 확인하여, 있으면 실행하고 없으면 웹 지도 또는 플레이스토어로 연결
     */
    private fun handleNaverMapScheme(uri: Uri): Boolean {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // 앱 설치 여부 확인
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(intent, 0)
            if (activities.isNotEmpty()) {
                context.startActivity(intent)
                return true
            }

            // 미설치 시 네이버 지도 웹으로 열기 또는 플레이스토어 이동
            val webFallbackUrl = "https://map.naver.com"
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$NAVER_MAP_PACKAGE"))
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(marketIntent)
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webFallbackUrl))
                context.startActivity(webIntent)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "handleNaverMapScheme failed", e)
            return false
        }
    }

    private fun handleBlobDownload(url: String, mimetype: String, contentDisposition: String) {
        val js = """
            (function() {
                var bestFilename = (window.__downloadMap && window.__downloadMap['$url']) || '';
                fetch('$url')
                    .then(function(r) { return r.blob(); })
                    .then(function(blob) {
                        var reader = new FileReader();
                        reader.readAsDataURL(blob);
                        reader.onloadend = function() {
                            var base64data = reader.result.split(",")[1];
                            if(window.AndroidBridge) {
                                var finalType = blob.type || '$mimetype';
                                var finalName = bestFilename || '$contentDisposition';
                                window.AndroidBridge.downloadBlob(base64data, finalType, finalName);
                            }
                        };
                    })
                    .catch(function(err) {
                        console.error("Blob download error: " + err);
                    });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
