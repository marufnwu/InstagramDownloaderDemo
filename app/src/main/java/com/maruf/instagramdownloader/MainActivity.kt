package com.maruf.instagramdownloader

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.logicline.mydining.utils.MyDownloadManager
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.io.*
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity() {

    val url = "https://m.facebook.com/story.php?story_fbid=pfbid0gzRtoR2scq88gN3tRacnMLszLvce98TYs4hABfBoyak1ZetJGqz7MgeBkF9hNqPdl&id=100084364966825&sfnsn=wiwspwa&mibextid=6aamW6"
    val agent ="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    var hit = 0;

    @SuppressLint("MissingInflatedId", "JavascriptInterface", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val web = findViewById<WebView>(R.id.web)

//        webViewSetupNotLoggedIn(web, url)
        //return

        web.settings.javaScriptEnabled  =true
        web.settings.domStorageEnabled =true
        //web.settings.userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Mobile Safari/537.36 (compatible; Facebookbot/1.0; +http://www.facebook.com/externalhit_uatext.php)"

        web.settings.setUseWideViewPort(true);
        web.settings.setLoadWithOverviewMode(true);
        //web.getSettings().setAppCacheMaxSize( 10 * 1024 * 1024 );

        web.settings.cacheMode  = WebSettings.LOAD_DEFAULT

//        web.addJavascriptInterface(object : Any() {
//            @JavascriptInterface
//            fun getVideoUrl(url: String) {
//                // The url parameter contains the video URL
//                Log.d("VideoUrl", "Video URL: $url")
//                downloadBlob(url)
//
//                // Use a download manager library to download the video file
//                // ...
//            }
//        }, "Android")

        // Execute JavaScript code to extract the video URL and pass it to the injected interface

        // Execute JavaScript code to extract the video URL and pass it to the injected interface





        //web.addJavascriptInterface(LoadListener(), "HTMLOUT")

        Handler()
            .postDelayed(object: Runnable {
                override fun run() {
                    web.evaluateJavascript(
                        "(function() { return document.querySelector('video').src; })();",
                        ValueCallback<String> { value -> // Call the injected interface with the video URL
                            web.loadUrl("javascript:Android.getVideoUrl('$value');")
                        }
                    )
                }

            }, 10000)



        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                //view?.loadUrl("javascript:window.HTMLOUT.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');")
                web.evaluateJavascript(
                    "(function(){return window.document.body.outerHTML})();"
                ) { value ->
                    Log.d("WebViewHtml", value)
                    val reader = JsonReader(StringReader(value))
                    reader.isLenient = true
                    try {
                        if (reader.peek() == JsonToken.STRING) {
                            val domStr = reader.nextString()
                            domStr?.let {
                                val xmlString = it
                                getVideoResolutionsFromPageSource(it){
                                    MergeVideoAudio(it)
                                }

                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        reader.close()
                    }
                }



                super.onPageFinished(view, url)
            }

            override fun onLoadResource(view: WebView?, url: String?) {



                Log.d("UrlAll", url!!)
                if(url!!.contains("mime_type=video_mp4")){
                    Log.d("UrlFound", url!!)
                    if(hit==0){
                        Toast.makeText(this@MainActivity, url, Toast.LENGTH_SHORT).show()
                        download(url)
                        hit =1
                    }
                }

            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {

                if (request!!.url.toString().startsWith("fb://")){
                    Toast.makeText(this@MainActivity, "hit", Toast.LENGTH_SHORT).show()
                    return true
                }else{
                    return false
                }

            }
        }


        web.loadUrl(url)





    }

    private fun MergeVideoAudio(it: ArrayList<MainActivity.ResolutionDetail>) {
        var audioUrl:String? = null
        var videoUrl:String? = null
        it.forEach {
            Log.d("VideoUrl", it.mimetype+" "+it.FBQualityLabel+" "+it.FBDefaultQuality)
            if(audioUrl==null){
                if(it.mimetype.equals("audio/mp4")){
                    audioUrl = it.videoQualityURL
                }
            }

            if(videoUrl==null){
                if(it.mimetype.equals("video/mp4") && it.FBQualityLabel.equals("360p")){
                    videoUrl = it.videoQualityURL
                }
            }
        }

        val downDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val newFile = File(downDir, "fb_story_$"+System.currentTimeMillis()+".mp4")

        //newFile.createNewFile()

        Log.d("FFMpeg", "OutputPath: "+newFile.absolutePath)
        audioUrl?.let {
            Log.d("FFMpeg", "AudioPath: "+newFile.absolutePath)

        }

        videoUrl?.let {
            Log.d("FFMpeg", "VideoPath: "+newFile.absolutePath)

        }


        val cmd = "-i $videoUrl -i  $audioUrl -c:v copy -c:a copy ${newFile.absoluteFile}"

        FFmpegKit.executeAsync(cmd,
            { session ->
                val state = session.state
                val returnCode = session.returnCode

                // CALLED WHEN SESSION IS EXECUTED
                Log.d(
                    "FFMpeg",
                    String.format(
                        "FFmpeg process exited with state %s and rc %s.%s",
                        state,
                        returnCode,
                        session.failStackTrace
                    )
                )
            }, {
                // CALLED WHEN SESSION PRINTS LOGS
                Log.d("FFMpeg", "Log: "+it.level.name+"--"+it.message)

            }) {
            // CALLED WHEN SESSION GENERATES STATISTICS
            Log.d("FFMpeg", "MergeVideoAudio: "+it.videoQuality)

        }


    }


    @SuppressLint("JavascriptInterface")
    private fun webViewSetupNotLoggedIn(web:WebView?, url: String) {
        web?.settings?.javaScriptEnabled = true
        //web?.settings?.userAgentString = AppConstants.USER_AGENT
        web?.settings?.useWideViewPort = true
        web?.settings?.loadWithOverviewMode = true
        web?.addJavascriptInterface(this, "mJava")
        web?.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)

            }
        }

        web?.webViewClient = object : WebViewClient(){
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (request!!.url.toString().startsWith("fb://")){
                    Toast.makeText(this@MainActivity, "hit", Toast.LENGTH_SHORT).show()
                    return true
                }else{
                    return false
                }

            }
        }
        web?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                try {
                    if (web?.progress == 100) {
                        var original = web?.originalUrl
                        var post_link = "url of your video"
                        if (original.equals(post_link)) {


                            //Fetch resoultions
                            web.evaluateJavascript(
                                "(function(){return window.document.body.outerHTML})();"
                            ) { value ->
                                val reader = JsonReader(StringReader(value))
                                reader.isLenient = true
                                try {
                                    if (reader.peek() == JsonToken.STRING) {
                                        val domStr = reader.nextString()
                                        domStr?.let {
                                            val xmlString = it
                                            getVideoResolutionsFromPageSource(it){
                                                for (i in it){
                                                    MyDownloadManager.download(i.videoQualityURL, i.FBQualityLabel, this@MainActivity)
                                                }
                                            }

                                        }
                                    }
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                } finally {
                                    reader.close()
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                super.onPageFinished(view, url)
            }

            @TargetApi(android.os.Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError
            ) {

            }

            @SuppressWarnings("deprecation")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                Log.e("getData", "onLoadResource")

                super.onLoadResource(view, url)
            }
        }


        web?.loadUrl(url)


    }

    private fun downloadBlob(link:String){
        try {
            // Replace the "your_blob_url_here" with the actual blob URL of the video.
            val url = URL(link)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.setRequestMethod("GET")
            connection.setDoOutput(true)
            connection.connect()
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "video.mp4"
            )
            val outputStream = FileOutputStream(file)
            val inputStream: InputStream = connection.getInputStream()
            val buffer = ByteArray(1024)
            var len1 = 0
            while (inputStream.read(buffer).also { len1 = it } != -1) {
                outputStream.write(buffer, 0, len1)
            }
            outputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

    fun getVideoResolutionsFromPageSource(
        pageSourceXmlString: String?,
        finished: (listOfRes: ArrayList<ResolutionDetail>) -> Unit
    ) {
        //pageSourceXmlString is the Page Source of WebPage of that specific copied video
        //We need to find list of Base URLs from pageSourceXmlString
        //Base URLs are inside an attribute named data-store which is inside a div whose class name starts with  '_53mw;
        //We need to find that div then get data-store which has a JSON as string
        //Parse that JSON and we will get list of adaptationset
        //Each adaptationset has list of representation tags
        // representation is the actual div which contains BASE URLs
        //Note that: BASE URLs have a specific attribute called mimeType
        //mimeType has audio/mp4 and video/mp4 which helps us to figure out whether the url is of an audio or a video
        val listOfResolutions = arrayListOf<ResolutionDetail>()
        if (!pageSourceXmlString?.isEmpty()!!) {
            val document: org.jsoup.nodes.Document = Jsoup.parse(pageSourceXmlString)
            val sampleDiv = document.getElementsByTag("body")
            if (!sampleDiv.isEmpty()) {
                val bodyDocument: org.jsoup.nodes.Document = Jsoup.parse(sampleDiv.html())
                val dataStoreDiv: org.jsoup.nodes.Element? = bodyDocument.select("div._53mw").first()
                val dataStoreAttr = dataStoreDiv?.attr("data-store")
                val jsonObject = JSONObject(dataStoreAttr)
                if (jsonObject.has("dashManifest")) {
                    val dashManifestString: String = jsonObject.getString("dashManifest")
                    val dashManifestDoc: org.jsoup.nodes.Document = Jsoup.parse(dashManifestString)
                    val mdpTagVal = dashManifestDoc.getElementsByTag("MPD")
                    val mdpDoc: org.jsoup.nodes.Document = Jsoup.parse(mdpTagVal.html())
                    val periodTagVal = mdpDoc.getElementsByTag("Period")
                    val periodDocument: org.jsoup.nodes.Document = Jsoup.parse(periodTagVal.html())
                    val subBodyDiv: org.jsoup.nodes.Element? = periodDocument.select("body").first()
                    subBodyDiv?.children()?.forEach {
                        val adaptionSetDiv: org.jsoup.nodes.Element? =
                            it.select("adaptationset").first()
                        adaptionSetDiv?.children()?.forEach {
                            if (it is org.jsoup.nodes.Element) {
                                val representationDiv: org.jsoup.nodes.Element? =
                                    it.select("representation").first()
                                val resolutionDetail = ResolutionDetail()
                                if (representationDiv?.hasAttr("mimetype")!!) {
                                    resolutionDetail.mimetype = representationDiv?.attr("mimetype")!!
                                }
                                if (representationDiv?.hasAttr("width")!!) {
                                    resolutionDetail.width =
                                        representationDiv?.attr("width")?.toLong()!!
                                }
                                if (representationDiv?.hasAttr("height")!!) {
                                    resolutionDetail.height =
                                        representationDiv.attr("height").toLong()
                                }
                                if (representationDiv?.hasAttr("FBDefaultQuality")!!) {
                                    resolutionDetail.FBDefaultQuality =
                                        representationDiv.attr("FBDefaultQuality")
                                }
                                if (representationDiv?.hasAttr("FBQualityClass")!!) {
                                    resolutionDetail.FBQualityClass =
                                        representationDiv.attr("FBQualityClass")
                                }
                                if (representationDiv?.hasAttr("FBQualityLabel")!!) {
                                    resolutionDetail.FBQualityLabel =
                                        representationDiv.attr("FBQualityLabel")
                                }
                                val representationDoc: org.jsoup.nodes.Document =
                                    Jsoup.parse(representationDiv.html())
                                val baseUrlTag = representationDoc.getElementsByTag("BaseURL")
                                if (!baseUrlTag.isEmpty() && !resolutionDetail.FBQualityLabel.equals(
                                        "Source",
                                        ignoreCase = true
                                    )
                                ) {
                                    resolutionDetail.videoQualityURL = baseUrlTag[0].text()
                                    listOfResolutions.add(resolutionDetail)
                                }
                            }
                        }
                    }
                }
            }
        }
        finished(listOfResolutions)
    }
    class ResolutionDetail {
        var width: Long = 0
        var height: Long = 0
        var FBQualityLabel = ""
        var FBDefaultQuality = ""
        var FBQualityClass = ""
        var videoQualityURL = ""
        var mimetype = ""  // [audio/mp4 for audios and video/mp4 for videos]
    }
}