package com.absinthe.unapkmgui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.InputStream

class ConvertActivity : AppCompatActivity() {

    private var inputStream: InputStream? = null
    private lateinit var converting: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_convert)
        converting = findViewById(R.id.tv_converting)
        converting.text = ""

        intent.data?.scheme?.let { scheme ->
            if (scheme == "content") {
                intent.data?.let {
                    inputStream = contentResolver.openInputStream(it)
                    createFile(this, "*/*", it.toString().split(File.separator).last() + ".apks")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            data?.data?.let {
                converting.text = getString(R.string.converting)

                Thread {
                    UnApkm.decryptFile(inputStream, contentResolver.openOutputStream(it))
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            this@ConvertActivity,
                            getString(R.string.toast_convert_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    finish()
                }.start()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun createFile(activity: Activity, mimeType: String, fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            // Filter to only show results that can be "opened", such as
            // a file (as opposed to a list of contacts or timezones).
            addCategory(Intent.CATEGORY_OPENABLE)

            // Create a file with the requested MIME type.
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        try {
            activity.startActivityForResult(intent, 1)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            Toast.makeText(activity, activity.getString(R.string.toast_no_document_app), Toast.LENGTH_LONG).show()
        }
    }
}