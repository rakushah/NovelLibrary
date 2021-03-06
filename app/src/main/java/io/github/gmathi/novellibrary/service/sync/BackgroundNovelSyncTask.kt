package io.github.gmathi.novellibrary.service.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.android.gms.gcm.*
import io.github.gmathi.novellibrary.R
import io.github.gmathi.novellibrary.activity.NavDrawerActivity
import io.github.gmathi.novellibrary.database.DBHelper
import io.github.gmathi.novellibrary.database.getAllNovels
import io.github.gmathi.novellibrary.network.NovelApi
import io.github.gmathi.novellibrary.network.getChapterCount
import io.github.gmathi.novellibrary.receiver.sync.SyncNovelUpdateReceiver
import io.github.gmathi.novellibrary.util.Constants
import io.github.gmathi.novellibrary.util.Utils


class BackgroundNovelSyncTask : GcmTaskService() {

    override fun onInitializeTasks() {
        //called when app is updated to a new version, reinstalled etc.
        //you have to schedule your repeating tasks again
        super.onInitializeTasks()
    }

    override fun onRunTask(taskParams: TaskParams): Int {
        //Your periodic code here

        val context = this@BackgroundNovelSyncTask

        val dbHelper = DBHelper(context)

        //TODO: DELETE Test Code
//        val novel = dbHelper.getNovel("The Legend of Randidly Ghosthound")
//        if (novel != null) {
//            novel?.chapterCount = 180
//            novel?.newChapterCount = 180
//            dbHelper.updateNovel(novel!!)
//        }

        try {
            startNovelsSync(dbHelper)
        } catch (e: Exception) {
            return GcmNetworkManager.RESULT_RESCHEDULE
        }
        return GcmNetworkManager.RESULT_SUCCESS
    }

    private fun startNovelsSync(dbHelper: DBHelper) {
        val unfilteredMap: HashMap<String, Int> = HashMap()
        val unfilteredChapMap: HashMap<String, Long> = HashMap()
        dbHelper.getAllNovels().forEach {
            val totalChapters = NovelApi().getChapterCount(it.url!!)
            if (totalChapters != 0 && totalChapters > it.chapterCount.toInt() && totalChapters > it.newChapterCount.toInt()) {
                unfilteredMap.put(it.name!!, (totalChapters - it.chapterCount).toInt())
                unfilteredChapMap.put(it.name!!, totalChapters.toLong())
            }
        }

        val novels = dbHelper.getAllNovels()
        val novelIds = novels.map { it.name }
        val novelMap = unfilteredMap.filter { novelIds.contains(it.key) }
        val novelsChapMap = HashMap(unfilteredChapMap.filter { novelIds.contains(it.key) })

        if (novelMap.isEmpty()) return

        val notificationText: String
        if (novelMap.size > 4) {
            notificationText = getString(R.string.new_chapters_notification_content_full, novelMap.size, novelMap.values.sum())
        } else {
            notificationText = novelMap.keys.map { getString(R.string.new_chapters_notification_content_single, it, novelMap[it]) }.joinToString(separator = "\n")
        }

        val novelDetailsIntent = Intent(this, NavDrawerActivity::class.java)
        novelDetailsIntent.action = Constants.ACTION.MAIN_ACTION
        novelDetailsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val novelDetailsBundle = Bundle()
        novelDetailsBundle.putInt("currentNavId", R.id.nav_library)
        novelDetailsBundle.putSerializable("novelsChapMap", novelsChapMap)
        novelDetailsIntent.putExtras(novelDetailsBundle)
        val contentIntent = PendingIntent.getActivity(this.applicationContext, 0, novelDetailsIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        val broadcastIntent = Intent(this, SyncNovelUpdateReceiver::class.java)
        val broadcastBundle = Bundle()
        broadcastBundle.putSerializable("novelsChapMap", novelsChapMap)
        broadcastIntent.putExtras(broadcastBundle)
        val deleteIntent = PendingIntent.getBroadcast(this.applicationContext, 0, broadcastIntent, 0)


        val mBuilder = NotificationCompat.Builder(this, "default")
            .setContentTitle(getString(R.string.new_chapters_notification_title))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(notificationText))
            .setContentText(notificationText)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mBuilder.setSmallIcon(R.drawable.ic_book_white_vector)
        else
            mBuilder.setSmallIcon(R.drawable.ic_book_white)

        val mNotifyMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mNotifyMgr.notify(Constants.NOTIFICATION_ID.SYNC_CHAPTERS, mBuilder.build())
    }

    private fun isNetworkDown(): Boolean {
        if (!Utils.checkNetwork(this)) {
            return true
        }
        return false
    }

    //region Helper Methods

    companion object {

        val thisClass = BackgroundNovelSyncTask::class.java
        val TAG = "BackgroundNovelSyncTask"

        fun scheduleRepeat(context: Context) {
            cancelAll(context)
            //in this method, single Repeating task is scheduled (the target service that will be called is MyTaskService.class)
            try {
                val periodic = PeriodicTask.Builder()
                    //specify target service - must extend GcmTaskService
                    .setService(thisClass)
                    //repeat every 60 seconds
                    .setPeriod(60 * 60)
                    //specify how much earlier the task can be executed (in seconds)
                    //.setFlex(60*60)
                    //tag that is unique to this task (can be used to cancel task)
                    .setTag(TAG)
                    //whether the task persists after device reboot
                    .setPersisted(true)
                    //if another task with same tag is already scheduled, replace it with this task
                    .setUpdateCurrent(true)
                    //set required network state, this line is optional
                    .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                    //request that charging must be connected, this line is optional
                    .setRequiresCharging(false)
                    .build()
                GcmNetworkManager.getInstance(context).schedule(periodic)
                Log.v(TAG, "repeating task scheduled")
            } catch (e: Exception) {
                Log.e(TAG, "scheduling failed")
                e.printStackTrace()
            }

        }

        fun cancelAll(context: Context) {
            GcmNetworkManager
                .getInstance(context)
                .cancelAllTasks(thisClass)
        }

    }
    //endregion

}