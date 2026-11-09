package com.karmadrive.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * KARMA 앱 (스마트폰) 프로토타입.
 * 운전자는 주행 중 화면을 못 보지만, 동승자 · 가족은 이 앱으로 트윈카가 지금 어떻게 망가지고 있는지,
 * 왜 그런지(사유)를 실시간으로 보고, 응원 스티커를 보내고, 주차 후 정비소에서 고쳐 줄 수 있다.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B1220")
        window.navigationBarColor = Color.parseColor("#141E31")
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0B1220"))
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
        }
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        setContentView(web)
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("file:///android_asset/app/index.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
