package com.maruf.instagramdownloader

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    val url = "https://www.instagram.com/reel/CncH26hhAgb/?utm_source=ig_web_copy_link"
    val agent ="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/535.21 (KHTML, like Gecko) Chrome/19.0.1042.0 Safari/535.21"

    @SuppressLint("MissingInflatedId", "JavascriptInterface", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val web = findViewById<WebView>(R.id.web)
        web.settings.javaScriptEnabled  =true
        web.settings.domStorageEnabled =true
        //web.settings.userAgentString = "Android"

        web.settings.setUseWideViewPort(true);
        web.settings.setLoadWithOverviewMode(true);


        web.addJavascriptInterface(LoadListener(), "HTMLOUT")

        web.webChromeClient = object : WebChromeClient() {
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                //view?.loadUrl("javascript:window.HTMLOUT.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');")

                super.onPageFinished(view, url)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                if(url!!.contains(".mp4")){
                    Log.d("Url Found", url)
                    download(url)
                }

            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                view?.loadUrl(request?.url.toString())
                return true
            }
        }


        web.loadUrl(url)





    }

    private fun download(url: String) {
        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(url))

        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setTitle("download")
        request.setDescription("downloading")
        request.setAllowedOverRoaming(false)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "test.mp4")

        val dm = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager)

        val downloadID: Long = dm.enqueue(request)

    }


    fun work(){
        try {
            val response: Connection.Response = Jsoup
                .connect(url)
                .userAgent(
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/535.21 (KHTML, like Gecko) Chrome/19.0.1042.0 Safari/535.21"
                )
                .execute()


            val statusCode: Int = response.statusCode()
            Log.d("TAG", " status code is: $statusCode")



            if (statusCode == 200) {
                val doc = Jsoup.connect(url).timeout(1000 * 1000)
                    .userAgent(agent).get()
                Log.d("Content", doc.html())

                val meta: Elements = doc.select("meta[property=og:video]")
                for (src in meta) {
                    if (src.tagName().equals("meta")) Log.d(
                        "TAG",
                        " content: " + src.attr("content")
                    ) else Log.d("TAG", src.tagName())
                }
            } else {
                println("received error code : $statusCode")
            }
        } catch (e: IOException) {
            Log.d("TAG", " Exception $e")
            e.printStackTrace()
        }
    }

    internal class LoadListener {
        @JavascriptInterface
        fun processHTML(html: String?) {
            Log.e("result", html!!)
        }
    }
}