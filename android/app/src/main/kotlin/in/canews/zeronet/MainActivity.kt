package `in`.canews.zeronet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import java.io.File
import java.io.IOException
import java.io.InputStream


const val BATTERY_OPTIMISATION_RESULT_CODE = 1001
const val PICK_USERJSON_FILE = 1002
const val SAVE_USERJSON_FILE = 1003
const val PICK_ZIP_FILE = 1004
const val TAG = "MainActivity"

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MethodChannel(flutterEngine?.dartExecutor, "in.canews.zeronet").setMethodCallHandler { call, result ->
            when (call.method) {
                "batteryOptimisations" -> getBatteryOptimizations(result)
                "isBatteryOptimized" -> isBatteryOptimized(result)
                "nativeDir" -> result.success(applicationInfo.nativeLibraryDir)
                "openJsonFile" -> openJsonFile(result)
                "openZipFile" -> openZipFile(result)
                "readJsonFromUri" -> readJsonFromUri(call.arguments.toString(), result)
                "readZipFromUri" -> readZipFromUri(call.arguments.toString(), result)
                "saveUserJsonFile" -> saveUserJsonFile(this, call.arguments.toString(), result)
                "moveTaskToBack" -> {
                    moveTaskToBack(true)
                    result.success(true)
                }
            }
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine)
    }

    private fun isBatteryOptimized(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            result.success(pm.isIgnoringBatteryOptimizations(packageName))
        } else {
            result.success(false)
        }

    }

    private lateinit var result: MethodChannel.Result

    @SuppressLint("BatteryLife")
    private fun getBatteryOptimizations(resultT: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, BATTERY_OPTIMISATION_RESULT_CODE)
                result = resultT
            } else {
                resultT.success(true)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == BATTERY_OPTIMISATION_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                result.success(true)
            } else {
                result.success(false)
            }
        } else if (requestCode == SAVE_USERJSON_FILE) {
            if (resultCode == Activity.RESULT_OK) {
                result.success("successfully saved users.json file")
            } else {
                result.success("failed to save file")
            }
        } else if (requestCode == PICK_USERJSON_FILE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data?.data != null) {
                    result.success(data.data.toString())
                }
            } else {
                result.error("526", "Error Picking User Json File", "Error Picking User Json File")
            }
        } else if (requestCode == PICK_ZIP_FILE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data?.data != null) {
                    result.success(data.data.toString())
                }
            } else {
                result.error("527", "Error Picking Plugin File", "Error Picking Plugin File")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun openZipFile(resultT: MethodChannel.Result) =
            openFileIntent(
                    resultT,
                    Intent.ACTION_OPEN_DOCUMENT,
                    "application/zip",
                    PICK_ZIP_FILE
            )

    private fun openJsonFile(resultT: MethodChannel.Result) =
            openFileIntent(
                    resultT,
                    Intent.ACTION_OPEN_DOCUMENT,
                    "application/json",
                    PICK_USERJSON_FILE
            )

    private fun openFileIntent(resultT: MethodChannel.Result,intentAction : String,intentType : String,intentCode : Int){
        val intent = Intent(intentAction).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = intentType
            result = resultT
        }
        startActivityForResult(intent, intentCode)
    }

    private fun readJsonFromUri(path: String, resultT: MethodChannel.Result) = copyFileToTempPath(path,resultT,"/users.json")

    private fun readZipFromUri(path: String, resultT: MethodChannel.Result) = copyFileToTempPath(path,resultT,"/plugin.zip")

    @Throws(IOException::class)
    private fun copyFileToTempPath(path: String, resultT: MethodChannel.Result, filename : String) {
        var inputstream: InputStream? = null
        if (path.startsWith("content://")) {
            inputstream = contentResolver.openInputStream(Uri.parse(path))
        } else if (path.startsWith("/")) {
            inputstream = File(path).inputStream()
        }
        inputstream.use { inputStream ->
            val tempFilePath = cacheDir.path + filename
            val tempFile = File(tempFilePath)
            if (tempFile.exists()) tempFile.delete()
            tempFile.createNewFile()
            inputStream?.toFile(tempFilePath)
            resultT.success(File(tempFilePath).absoluteFile.absolutePath)
            tempFile.deleteOnExit()
        }
    }

    private fun InputStream.toFile(path: String) {
        use { input ->
            File(path).outputStream().use { input.copyTo(it) }
        }
    }

    private fun saveUserJsonFile(context: Activity, path: String, resultT: MethodChannel.Result) {
        Log.d(TAG, "backing up user json file")
        val file = File(path)
        val authority = context.packageName + ".fileprovider"
        Log.d(TAG, "authority: $authority")
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent: Intent = ShareCompat.IntentBuilder.from(context)
                .setType("application/octet-stream")
                .setStream(contentUri)
                .setText("users.json")
                .intent
        shareIntent.data = contentUri
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.putExtra("finishActivityOnSaveCompleted", true)
        context.startActivityForResult(Intent.createChooser(
                shareIntent, "Backup Users.json File"), SAVE_USERJSON_FILE)
        result = resultT
    }
}
