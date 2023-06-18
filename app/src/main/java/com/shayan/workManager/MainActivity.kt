package com.shayan.workManager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.await
import com.shayan.workManager.constants.Constant
import com.shayan.workManager.databinding.ActivityMainBinding
import com.shayan.workManager.worker.DownloadWorker
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var downloadRequest: OneTimeWorkRequest
    private lateinit var binding: ActivityMainBinding
    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { Granted ->
            if (Granted) {
                Timber.d("permission Granted")
                // do work here
            } else {
                Timber.d("permission Denied")
            }

        }
    private lateinit var workManager: WorkManager

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        workManager = WorkManager.getInstance(applicationContext)


        binding.btnStartDownload.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val fileName = binding.edFileName.text.toString()
                val url = binding.edUrlName.text.toString()
                if (fileName.isEmpty()) {
                    Toast.makeText(this, "Please insert a file Name", Toast.LENGTH_SHORT).show()
                } else if (url.isEmpty()) {
                    Toast.makeText(this, "Please insert Video Url", Toast.LENGTH_SHORT).show()
                } else {
                    // for download video need network and enough storage
                    startWork(fileName, url)
                }
            }

        }

        binding.btnStopDownload.setOnClickListener {
            val workQuery = WorkQuery.Builder
                .fromUniqueWorkNames(listOf("download"))
                .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
                .build()

            val downloadWorkInfo = workManager.getWorkInfos(workQuery).get()
            if (downloadWorkInfo.isNotEmpty()){
                Timber.d("state is ${downloadWorkInfo.get(0).state}")
                if (downloadWorkInfo.get(0).state == WorkInfo.State.RUNNING){
                    workManager.cancelUniqueWork("download")
                }else {
                    Toast.makeText(this, "download is not running", Toast.LENGTH_SHORT).show()
                }
            }else {
                Toast.makeText(this, "download is not running", Toast.LENGTH_SHORT).show()
            }


        }


    }

    private fun startWork(fileName: String, url: String) {

        val inputData = Data.Builder().apply {
            putString(Constant.FILE_NAME, fileName)
            putString(Constant.FILE_URL, url)
        }.build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .build()

        // for Unique Work
        workManager.enqueueUniqueWork(
            "download",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )

        workManager.getWorkInfosForUniqueWorkLiveData("download").observe(this) {
            val downloadWorkInfo = it.find { it.id == downloadRequest.id }
            when (downloadWorkInfo?.state) {
                WorkInfo.State.RUNNING -> {
                    Timber.d("Running Work")
                }

                WorkInfo.State.SUCCEEDED -> {
                    val successWorkData =
                        downloadWorkInfo.outputData.getString(Constant.SUCCESS_FILE)
                    Timber.d("SUCCEEDED Work")
                    Timber.d("SUCCEEDED Work $successWorkData")
                }

                WorkInfo.State.CANCELLED -> {
                    Timber.d("CANCELLED Work")
                }

                WorkInfo.State.FAILED -> {
                    val errorNetworkData =
                        downloadWorkInfo.outputData.getString(Constant.ERROR_NETWORK)
                    val errorFileData = downloadWorkInfo.outputData.getString(Constant.ERROR_File)
                    val errorUrl = downloadWorkInfo.outputData.getString(Constant.ERROR_FILE_URL)
                    errorUrl?.let {
                        Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                    }
                    Timber.d("FAILED Work")
                    Timber.d("FAILED Work Network $errorNetworkData")
                    Timber.d("FAILED Work File $errorFileData")
                }

                else -> {
                    Timber.d("unknown error")
                }

            }
        }
    }


}