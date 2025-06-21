package com.example.overflo

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Overflo"
        private const val DEFAULT_DAYS_THRESHOLD = 7
    }

    private lateinit var deviceListLayout: LinearLayout
    private var fromPath: String = ""
    private var toPath: String = ""
    private var selectedAction: String = ""
    private var selectedPercentage: Int = 50

    private var selectedSizeFilter: String = "Any"
    private var selectedTimeFilter: String = "Any"
    private var selectedExtensionFilter: String = "All"
    private var minSizeMB: Int = 100
    private var maxSizeMB: Int = 100
    private var daysThreshold: Int = DEFAULT_DAYS_THRESHOLD

    private lateinit var fromFolderText: TextView
    private lateinit var toFolderText: TextView
    private lateinit var daysInputLayout: LinearLayout
    private lateinit var daysEditText: EditText

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val path = uri.toString()
                if (selectedAction == "from") {
                    fromPath = path
                    fromFolderText.text = "From Folder: $path"
                } else if (selectedAction == "to") {
                    toPath = path
                    toFolderText.text = "To Folder: $path"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceListLayout = findViewById(R.id.deviceListLayout)
        val analyzeButton: Button = findViewById(R.id.analyzeButton)
        val moveButton: Button = findViewById(R.id.moveButton)
        val copyButton: Button = findViewById(R.id.copyButton)

        analyzeButton.setOnClickListener { listStorageDevices() }
        moveButton.setOnClickListener { showMoveOrCopyDialog("Move") }
        copyButton.setOnClickListener { showMoveOrCopyDialog("Copy") }

        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                101
            )
        }
    }

    private fun listStorageDevices() {
        deviceListLayout.removeAllViews()

        val dirs = listOf(
            Environment.getExternalStorageDirectory(),
            getExternalFilesDirs(null).getOrNull(1)
        )

        for (dir in dirs) {
            if (dir != null && dir.exists()) {
                val stat = android.os.StatFs(dir.path)
                val total = stat.totalBytes
                val free = stat.availableBytes

                val card = TextView(this)
                card.text = """
                    Path: ${dir.path}
                    Total: ${total / (1024 * 1024)} MB
                    Free: ${free / (1024 * 1024)} MB
                """.trimIndent()
                card.setPadding(20, 20, 20, 20)
                card.setBackgroundColor(0xFFE0E0E0.toInt())
                card.setTextColor(0xFF000000.toInt())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 30)
                card.layoutParams = params

                deviceListLayout.addView(card)
            }
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val initialUri = DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        folderPickerLauncher.launch(intent)
    }

    private fun isImage(fileName: String?): Boolean {
        if (fileName.isNullOrEmpty()) return false
        return fileName.endsWith(".jpg", ignoreCase = true) ||
                fileName.endsWith(".jpeg", ignoreCase = true) ||
                fileName.endsWith(".png", ignoreCase = true) ||
                fileName.endsWith(".gif", ignoreCase = true) ||
                fileName.endsWith(".webp", ignoreCase = true) ||
                fileName.endsWith(".bmp", ignoreCase = true)
    }

    private fun isVideo(fileName: String?): Boolean {
        if (fileName.isNullOrEmpty()) return false
        return fileName.endsWith(".mp4", ignoreCase = true) ||
                fileName.endsWith(".mkv", ignoreCase = true) ||
                fileName.endsWith(".avi", ignoreCase = true) ||
                fileName.endsWith(".mov", ignoreCase = true) ||
                fileName.endsWith(".wmv", ignoreCase = true) ||
                fileName.endsWith(".flv", ignoreCase = true)
    }

    private fun isDocument(fileName: String?): Boolean {
        if (fileName.isNullOrEmpty()) return false
        return fileName.endsWith(".pdf", ignoreCase = true) ||
                fileName.endsWith(".doc", ignoreCase = true) ||
                fileName.endsWith(".docx", ignoreCase = true) ||
                fileName.endsWith(".txt", ignoreCase = true) ||
                fileName.endsWith(".ppt", ignoreCase = true) ||
                fileName.endsWith(".pptx", ignoreCase = true) ||
                fileName.endsWith(".xls", ignoreCase = true) ||
                fileName.endsWith(".xlsx", ignoreCase = true)
    }

    private fun getMimeType(fileName: String?): String {
        return when {
            fileName == null -> "application/octet-stream"
            isImage(fileName) -> "image/*"
            isVideo(fileName) -> "video/*"
            isDocument(fileName) -> when {
                fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                fileName.endsWith(".doc", ignoreCase = true) || fileName.endsWith(".docx", ignoreCase = true) -> "application/msword"
                fileName.endsWith(".xls", ignoreCase = true) || fileName.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.ms-excel"
                fileName.endsWith(".ppt", ignoreCase = true) || fileName.endsWith(".pptx", ignoreCase = true) -> "application/vnd.ms-powerpoint"
                else -> "application/octet-stream"
            }
            else -> "application/octet-stream"
        }
    }

    private fun getLastModifiedFromContentResolver(uri: Uri): Long {
        return try {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && cursor.columnCount > 0) {
                    cursor.getLong(0)
                } else {
                    0L
                }
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last modified time", e)
            0L
        }
    }

    private fun showMoveOrCopyDialog(action: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("$action Data")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 20, 40, 10)

        fromFolderText = TextView(this)
        toFolderText = TextView(this)

        val fromButton = Button(this).apply {
            text = "Select From Folder"
            setOnClickListener {
                selectedAction = "from"
                openFolderPicker()
            }
        }

        val toButton = Button(this).apply {
            text = "Select To Folder"
            setOnClickListener {
                selectedAction = "to"
                openFolderPicker()
            }
        }

        layout.addView(fromButton)
        layout.addView(fromFolderText)
        layout.addView(toButton)
        layout.addView(toFolderText)

        // Size Filter
        val sizeSpinner = Spinner(this)
        val sizeOptions = arrayOf("Any", "Large", "Small")
        sizeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizeOptions)
        layout.addView(TextView(this).apply { text = "Size Filter:" })
        layout.addView(sizeSpinner)

        val sizeSliderLabel = TextView(this).apply { visibility = View.GONE }
        val sizeSlider = SeekBar(this).apply {
            max = 1000
            progress = 100
            visibility = View.GONE
        }

        layout.addView(sizeSliderLabel)
        layout.addView(sizeSlider)

        sizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (sizeOptions[position]) {
                    "Large" -> {
                        sizeSliderLabel.visibility = View.VISIBLE
                        sizeSlider.visibility = View.VISIBLE
                        sizeSliderLabel.text = "Minimum Size: ${sizeSlider.progress} MB"
                        minSizeMB = sizeSlider.progress
                        maxSizeMB = Int.MAX_VALUE
                    }
                    "Small" -> {
                        sizeSliderLabel.visibility = View.VISIBLE
                        sizeSlider.visibility = View.VISIBLE
                        sizeSliderLabel.text = "Maximum Size: ${sizeSlider.progress} MB"
                        maxSizeMB = sizeSlider.progress
                        minSizeMB = 0
                    }
                    else -> {
                        sizeSlider.visibility = View.GONE
                        sizeSliderLabel.visibility = View.GONE
                        minSizeMB = 0
                        maxSizeMB = Int.MAX_VALUE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val type = sizeOptions[sizeSpinner.selectedItemPosition]
                sizeSliderLabel.text = if (type == "Large") "Minimum Size: $progress MB" else "Maximum Size: $progress MB"
                if (type == "Large") minSizeMB = progress else maxSizeMB = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Modified Filter
        val timeSpinner = Spinner(this)
        val timeOptions = arrayOf("Any", "Older", "Newer")
        timeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timeOptions)
        layout.addView(TextView(this).apply { text = "Modified Filter:" })
        layout.addView(timeSpinner)

        // Days Input (only visible when Older/Newer is selected)
        daysInputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }

        daysEditText = EditText(this).apply {
            setText(daysThreshold.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setHint("Enter days")
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        daysInputLayout.addView(TextView(this).apply { text = "Days:" })
        daysInputLayout.addView(daysEditText)
        layout.addView(daysInputLayout)

        timeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTimeFilter = timeOptions[position]
                daysInputLayout.visibility = if (selectedTimeFilter in listOf("Older", "Newer")) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Extension Filter
        val extensionSpinner = Spinner(this)
        val extensionOptions = arrayOf("All", "Image", "Video", "Document")
        extensionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, extensionOptions)
        layout.addView(TextView(this).apply { text = "Extension Filter:" })
        layout.addView(extensionSpinner)

        // Percentage
        val percentLabel = TextView(this).apply {
            text = "Select Percentage: $selectedPercentage%"
        }

        val seekBar = SeekBar(this).apply {
            max = 100
            progress = selectedPercentage
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedPercentage = progress
                    percentLabel.text = "Select Percentage: $progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        layout.addView(percentLabel)
        layout.addView(seekBar)

        val scrollView = ScrollView(this)
        scrollView.addView(layout)
        builder.setView(scrollView)

        builder.setPositiveButton("Next") { _, _ ->
            selectedSizeFilter = sizeOptions[sizeSpinner.selectedItemPosition]
            selectedTimeFilter = timeOptions[timeSpinner.selectedItemPosition]
            selectedExtensionFilter = extensionOptions[extensionSpinner.selectedItemPosition]

            // Get days threshold from input
            daysThreshold = try {
                daysEditText.text.toString().toInt().coerceAtLeast(1)
            } catch (e: Exception) {
                DEFAULT_DAYS_THRESHOLD
            }

            if (fromPath.isNotEmpty() && toPath.isNotEmpty()) {
                performFileTransfer(isMove = action == "Move")
            } else {
                Toast.makeText(this, "Please select both source and destination folders", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun performFileTransfer(isMove: Boolean) {
        if (fromPath.isEmpty() || toPath.isEmpty()) {
            Toast.makeText(this, "Please select both source and destination", Toast.LENGTH_SHORT).show()
            return
        }

        val fromDoc = DocumentFile.fromTreeUri(this, Uri.parse(fromPath)) ?: run {
            Toast.makeText(this, "Source folder not accessible", Toast.LENGTH_SHORT).show()
            return
        }

        val toDoc = DocumentFile.fromTreeUri(this, Uri.parse(toPath)) ?: run {
            Toast.makeText(this, "Destination folder not accessible", Toast.LENGTH_SHORT).show()
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeThresholdMillis = daysThreshold * 24L * 60 * 60 * 1000
        var files = fromDoc.listFiles().filter { it.isFile }

        // Apply all filters
        files = files.filter { file ->
            // Size filter
            val sizeMB = file.length() / (1024 * 1024)
            val sizePasses = sizeMB in minSizeMB..maxSizeMB

            // Time filter
            val lastModified = getLastModifiedFromContentResolver(file.uri)
            val timePasses = when (selectedTimeFilter) {
                "Older" -> lastModified > 0 && lastModified < currentTime - timeThresholdMillis
                "Newer" -> lastModified > 0 && lastModified >= currentTime - timeThresholdMillis
                else -> true
            }

            // Type filter
            val typePasses = when (selectedExtensionFilter) {
                "Image" -> isImage(file.name)
                "Video" -> isVideo(file.name)
                "Document" -> isDocument(file.name)
                else -> true
            }

            sizePasses && timePasses && typePasses
        }

        val filesToTransfer = files.take((selectedPercentage * files.size / 100).coerceAtLeast(1))

        val progressDialog = ProgressDialog(this).apply {
            setTitle("${if (isMove) "Moving" else "Copying"} files")
            setMessage("Filter: ${selectedTimeFilter.lowercase()} than $daysThreshold days")
            max = filesToTransfer.size
            setCancelable(false)
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            show()
        }

        Thread {
            var successCount = 0
            var failedCount = 0

            filesToTransfer.forEachIndexed { index, file ->
                runOnUiThread {
                    progressDialog.progress = index
                    progressDialog.setMessage("${if (isMove) "Moving" else "Copying"} ${file.name}...")
                }

                try {
                    val inputStream = contentResolver.openInputStream(file.uri) ?: throw Exception("Cannot open input stream")
                    val mimeType = file.type ?: getMimeType(file.name)
                    val outputFile = toDoc.createFile(mimeType, file.name ?: "file_${System.currentTimeMillis()}")
                        ?: throw Exception("Cannot create output file")

                    val outputStream = contentResolver.openOutputStream(outputFile.uri)
                        ?: throw Exception("Cannot open output stream")

                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (isMove) {
                        if (!file.delete()) {
                            throw Exception("Failed to delete source file")
                        }
                    }
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error transferring file ${file.name}", e)
                    failedCount++
                }
            }

            runOnUiThread {
                progressDialog.dismiss()
                val message = buildString {
                    append("${if (isMove) "Moved" else "Copied"} $successCount files\n")
                    append("Filter: ${selectedTimeFilter.lowercase()} than $daysThreshold days\n")
                    if (failedCount > 0) append("Failed: $failedCount")
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}