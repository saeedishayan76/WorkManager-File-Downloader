package com.shayan.workManager.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.webkit.URLUtil
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shayan.workManager.R
import com.shayan.workManager.constants.Constant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resumeWithException

/*
    According to developer.android document, Kotlin developers should use CoroutineWorker instead of Worker.

    You Can Use Retrofit for Download file But im Using OkHttp For Download video link

    All File Store in internal path (data/data/packageName/cache) Which the User Does not have access to (only Root)

 */
class DownloadWorker(val context: Context, val workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        Timber.d("work execute")
        val fileName = inputData.getString(Constant.FILE_NAME).plus(".mp4")
        val fileUrl = inputData.getString(Constant.FILE_URL)!!

        return try {
            setForeground(createForegroundInfo(0))
            //start a delaly before download file
            delay(5000L)
            downloadFile(fileName, fileUrl)
        } catch (e: Exception) {
            Timber.d("exception ${e.localizedMessage}")
            Result.failure()
        }


    }

    private suspend fun downloadFile(fileName: String, fileUrl: String): Result {
        Timber.d("file Name $fileName  url $fileUrl")
        val request = Request.Builder()
            .url(fileUrl)
            .build()
        val client = OkHttpClient()

        val response = client.newCall(request).awaitResponse()
        Timber.d("response Code ${response.code}")

        response.body?.let { body ->
            // work with file should be in I/O Thread
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, fileName)
                val outputStream = FileOutputStream(file)
                val byteStream = body.byteStream()
                byteStream.use { inputStream ->
                    outputStream.use { outputStream ->
                        try {
                            val total = body.contentLength()
                            var progressByte = 0L
                            val data = ByteArray(8_192)
                            while (true) {
                                val bytes = inputStream.read(data)
                                if (bytes == -1) {
                                    break
                                }
                                outputStream.write(data, 0, bytes)
                                progressByte += bytes
                                val percent = ((progressByte * 100) / total).toInt()
                                setForeground(createForegroundInfo(percent))
                            }

                        } catch (e: Exception) {
                            return@withContext Result.failure(workDataOf(Constant.ERROR_File to e.localizedMessage))
                        }
                    }
                }

            }
            return Result.success()
        } ?: kotlin.run {
            return Result.failure(workDataOf(Constant.ERROR_NETWORK to "error in fetch"))
        }

    }


    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downlaod Video")
            .setContentText("downloading... $progress %")
            .setSmallIcon(R.drawable.baseline_file_download_24)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setSilent(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "14"
        const val CHANNEL_NAME = "download_channel"
        const val NOTIFICATION_ID = 14

    }

    /*
    because i want to call a suspend method in on response create a suspendCancellableCoroutine
    write this method
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend inline fun Call.awaitResponse(): Response {
        return suspendCancellableCoroutine { cancellableContinuation ->
            val call = OkHttpClient().newCall(this.request())
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cancellableContinuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    cancellableContinuation.resume(response) {

                    }
                }
            })
            cancellableContinuation.invokeOnCancellation {
                call.cancel()
            }
        }
    }
}