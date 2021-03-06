package io.github.gmathi.novellibrary

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.multidex.MultiDex
import android.support.multidex.MultiDexApplication
import android.support.v7.app.AppCompatDelegate
import android.util.Log
import android.webkit.WebView
import com.squareup.leakcanary.LeakCanary
import io.github.gmathi.novellibrary.database.DBHelper
import io.github.gmathi.novellibrary.network.HostNames
import io.github.gmathi.novellibrary.service.sync.BackgroundNovelSyncTask
import io.github.gmathi.novellibrary.util.DataCenter
import java.io.File
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager




val dataCenter: DataCenter by lazy {
    NovelLibraryApplication.dataCenter!!
}

val dbHelper: DBHelper by lazy {
    NovelLibraryApplication.dbHelper!!
}

class NovelLibraryApplication : MultiDexApplication() {
    companion object {
        var dataCenter: DataCenter? = null
        var dbHelper: DBHelper? = null
    }

    override fun onCreate() {
        dataCenter = DataCenter(applicationContext)
        dbHelper = DBHelper(applicationContext)
        HostNames.setHostNamesList(dataCenter!!.getVerifiedHosts())
        super.onCreate()
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        if (LeakCanary.isInAnalyzerProcess(this)) {
            return
        }
        LeakCanary.install(this)

        val imagesDir = File(filesDir, "images")
        if (!imagesDir.exists())
            imagesDir.mkdir()

        try {
            enableSSLSocket()
        } catch (e: Exception) {
            Log.e("NovelLibraryApplication", e.localizedMessage, e)
        }

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        initChannels(applicationContext)

// TODO: DELETE TEST CODE
//        val novel = dbHelper!!.getNovel("Everyone Else is a Returnee")
//        if (novel !=null) {
//            novel?.chapterCount = 180
//            novel?.newChapterCount = 180
//            dbHelper!!.updateNovel(novel!!)
//        }

        startSyncService()
    }

    @Throws(KeyManagementException::class, NoSuchAlgorithmException::class)
    fun enableSSLSocket() {

        HttpsURLConnection.setDefaultHostnameVerifier {
            hostName: String?, _ ->
            if (hostName != null) HostNames.isVerifiedHost(hostName) else false
        }

        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<X509TrustManager>(object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate?> {
                return arrayOfNulls(0)
            }
        }), SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
    }

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(getString(R.string.default_notification_channel_id),
            "Default Channel",
            NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Default Channel Description"
        notificationManager.createNotificationChannel(channel)
    }

    fun startSyncService() {
        BackgroundNovelSyncTask.scheduleRepeat(applicationContext)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }



}