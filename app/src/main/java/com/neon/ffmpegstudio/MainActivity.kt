package com.neon.ffmpegstudio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            handlePickedFile(uri)
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            handlePickedFolder(uri)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun pickFile() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("video/*", "audio/*", "image/*")
                    )
                }
                filePickerLauncher.launch(intent)
            }
        }

        @JavascriptInterface
        fun pickFolder() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                folderPickerLauncher.launch(intent)
            }
        }

        @JavascriptInterface
        fun copyToClipboard(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("FFmpeg Command", text))
            }
        }
    }

    private fun handlePickedFile(uri: Uri) {
        val fullPath = resolveRealFilePath(uri)
        val fileName = getDisplayName(uri) ?: File(fullPath).name
        val dirPath = if (fullPath.contains("/")) {
            fullPath.substringBeforeLast("/")
        } else {
            "/storage/emulated/0/Download"
        }

        val meta = extractMediaMetadata(uri, fileName, false, 1)
        pushToWeb(dirPath, fileName, false, meta)
    }

    private fun handlePickedFolder(treeUri: Uri) {
        val dirPath = resolveRealFolderPath(treeUri)
        val docDir = DocumentFile.fromTreeUri(this, treeUri)
        val children = docDir?.listFiles()?.filter { it.isFile } ?: emptyList()
        val firstMedia = children.firstOrNull()
        val firstName = firstMedia?.name ?: "input.mp4"

        val meta = if (firstMedia != null) {
            extractMediaMetadata(firstMedia.uri, firstName, true, children.size)
        } else {
            JSONObject().apply {
                put("format", "FOLDER (${children.size} files)")
                put("queueText", "${children.size} files in folder")
            }
        }
        pushToWeb(dirPath, firstName, true, meta)
    }

    private fun resolveRealFilePath(uri: Uri): String {
        try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                if (uri.authority == "com.android.externalstorage.documents") {
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val relPath = split[1]
                        return if (type.equals("primary", ignoreCase = true)) {
                            "${Environment.getExternalStorageDirectory().absolutePath}/$relPath"
                        } else {
                            "/storage/$type/$relPath"
                        }
                    }
                } else if (uri.authority == "com.android.providers.downloads.documents") {
                    if (docId.startsWith("raw:")) {
                        return docId.removePrefix("raw:")
                    }
                    val name = getDisplayName(uri) ?: "input.mp4"
                    return "${Environment.getExternalStorageDirectory().absolutePath}/Download/$name"
                }
            }
        } catch (_: Exception) {
        }
        val name = getDisplayName(uri) ?: "input.mp4"
        return "${Environment.getExternalStorageDirectory().absolutePath}/Download/$name"
    }

    private fun resolveRealFolderPath(treeUri: Uri): String {
        try {
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            val split = treeId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val relPath = split[1]
                return if (type.equals("primary", ignoreCase = true)) {
                    "${Environment.getExternalStorageDirectory().absolutePath}/$relPath"
                } else {
                    "/storage/$type/$relPath"
                }
            }
        } catch (_: Exception) {
        }
        return "${Environment.getExternalStorageDirectory().absolutePath}/Download"
    }

    private fun getDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }

    private fun getFileSizeBytes(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) return cursor.getLong(idx)
            }
        }
        return 0L
    }

    private fun extractMediaMetadata(uri: Uri, fileName: String, isFolder: Boolean, fileCount: Int): JSONObject {
        val obj = JSONObject()
        val ext = fileName.substringAfterLast('.', "mp4").uppercase(Locale.US)
        val sizeBytes = getFileSizeBytes(uri)
        val sizeMB = sizeBytes / (1024.0 * 1024.0)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 30000L
            val durSec = durMs / 1000.0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                ?: if (durSec > 0 && sizeBytes > 0) ((sizeBytes * 8) / durSec).toLong() else 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "media/$ext"

            val mbps = bitrateBps / 1_000_000.0
            obj.put("format", "$ext ($mime)")
            obj.put("signature", "Native Android MediaMetadataRetriever")
            obj.put("bitrateStr", if (mbps >= 1.0) String.format(Locale.US, "%.2f Mbps", mbps) else "${bitrateBps / 1000} kbps")
            obj.put("bitrateGrade", when {
                mbps > 16 -> "High Camera Bitrate (Great for CRF compression)"
                mbps >= 6 -> "Normal Balanced Bitrate"
                else -> "Compact / Pre-Compressed Bitrate"
            })
            obj.put("resStr", if (width > 0 && height > 0) "$width × $height" else "Audio / Media Stream")
            obj.put("durSizeStr", String.format(Locale.US, "%.1fs · %.2f MB", durSec, sizeMB))
            obj.put("durSec", durSec)
            obj.put("sizeMB", sizeMB)
            obj.put("queueText", if (isFolder) "$fileCount files in folder" else "1 file: $fileName")
        } catch (_: Exception) {
            obj.put("format", ext)
            obj.put("durSizeStr", String.format(Locale.US, "%.2f MB", sizeMB))
            obj.put("sizeMB", sizeMB)
            obj.put("queueText", if (isFolder) "$fileCount files in folder" else "1 file: $fileName")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return obj
    }

    private fun pushToWeb(dirPath: String, fileName: String, isFolder: Boolean, meta: JSONObject) {
        val safeDir = JSONObject.quote(dirPath)
        val safeFile = JSONObject.quote(fileName)
        val safeMeta = JSONObject.quote(meta.toString())
        val js = "window.onAndroidPathPicked($safeDir, $safeFile, $isFolder, $safeMeta);"
        runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
    }
}
