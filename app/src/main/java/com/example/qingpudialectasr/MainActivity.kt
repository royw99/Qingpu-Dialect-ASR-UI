package com.example.qingpudialectasr

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "QingpuASR"
    }
    
    private lateinit var recordButton: Button
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var recordingsRecyclerView: RecyclerView
    private lateinit var bottomToolbar: LinearLayout
    private lateinit var deleteButton: Button
    private lateinit var renameButton: Button
    private lateinit var transcribeButton: Button
    
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingStartTime = 0L
    private var pausedTime = 0L
    private var currentRecordingFile: String? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    
    private val recordings = mutableListOf<Recording>()
    private lateinit var recordingAdapter: RecordingAdapter
    private var selectedRecording: Recording? = null
    
    private val PERMISSION_REQUEST_CODE = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        setupBackPressHandler()
        checkPermissions()
        loadExistingRecordings()
    }
    
    private fun initViews() {
        recordButton = findViewById(R.id.recordButton)
        pauseButton = findViewById(R.id.pauseButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)
        timerText = findViewById(R.id.timerText)
        recordingsRecyclerView = findViewById(R.id.recordingsRecyclerView)
        bottomToolbar = findViewById(R.id.bottomToolbar)
        deleteButton = findViewById(R.id.deleteButton)
        renameButton = findViewById(R.id.renameButton)
        transcribeButton = findViewById(R.id.transcribeButton)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    }
    
    private fun setupRecyclerView() {
        recordingAdapter = RecordingAdapter(
            recordings,
            { recording, action ->
                when (action) {
                    "play" -> playRecording(recording)
                }
            },
            { recording, position ->
                selectedRecording = recording
                showBottomToolbar()
            }
        )
        recordingsRecyclerView.layoutManager = LinearLayoutManager(this)
        recordingsRecyclerView.adapter = recordingAdapter
    }
    
    private fun setupClickListeners() {
        recordButton.setOnClickListener {
            if (isPaused) {
                resumeRecording()
            } else if (!isRecording) {
                startRecording()
            }
        }
        
        pauseButton.setOnClickListener {
            pauseRecording()
        }
        
        stopButton.setOnClickListener {
            stopRecording()
        }
        
        deleteButton.setOnClickListener {
            selectedRecording?.let { recording ->
                deleteRecording(recording)
                hideBottomToolbar()
            }
        }
        
        renameButton.setOnClickListener {
            selectedRecording?.let { recording ->
                showRenameDialog(recording)
            }
        }
        
        transcribeButton.setOnClickListener {
            selectedRecording?.let { recording ->
                openTranscribeActivity(recording)
                hideBottomToolbar()
            }
        }
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>().apply {
            add(Manifest.permission.RECORD_AUDIO)
            // Only request storage permissions for older Android versions
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for audio recording", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startRecording() {
        try {
            // Try WAV format first for better API compatibility
            val fileName = generateWavFileName()
            val file = File(getExternalFilesDir(null), fileName)
            currentRecordingFile = file.absolutePath
            
            Log.d(TAG, "Starting recording to file: $fileName")
            Log.d(TAG, "File path: ${file.absolutePath}")
            Log.d(TAG, "Storage directory: ${getExternalFilesDir(null)?.absolutePath}")
            
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                setOutputFile(file.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                
                prepare()
                start()
            }
            
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            
            updateUI()
            startTimer()
            
            statusText.text = "Recording..."
            
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun pauseRecording() {
        try {
            mediaRecorder?.pause()
            isPaused = true
            pausedTime = System.currentTimeMillis()
            stopTimer()
            updateUI()
            statusText.text = "Paused"
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to pause recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun resumeRecording() {
        try {
            mediaRecorder?.resume()
            isPaused = false
            recordingStartTime += (System.currentTimeMillis() - pausedTime)
            startTimer()
            updateUI()
            statusText.text = "Recording..."
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to resume recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            isRecording = false
            isPaused = false
            stopTimer()
            updateUI()
            
            statusText.text = "Recording saved"
            timerText.text = "00:00"
            
            loadExistingRecordings()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to stop recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateUI() {
        when {
            isPaused -> {
                recordButton.text = "Resume"
                recordButton.isEnabled = true
                pauseButton.isEnabled = false
                stopButton.isEnabled = true
            }
            isRecording -> {
                recordButton.text = "Record"
                recordButton.isEnabled = false
                pauseButton.isEnabled = true
                stopButton.isEnabled = true
            }
            else -> {
                recordButton.text = "Record"
                recordButton.isEnabled = true
                pauseButton.isEnabled = false
                stopButton.isEnabled = false
            }
        }
    }
    
    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording && !isPaused) {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    val minutes = (elapsed / 60000).toInt()
                    val seconds = ((elapsed % 60000) / 1000).toInt()
                    timerText.text = String.format("%02d:%02d", minutes, seconds)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timerRunnable!!)
    }
    
    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
    }
    
    private fun generateFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault())
        return "recording_${dateFormat.format(Date())}.3gp"
    }
    
    private fun generateWavFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault())
        return "recording_${dateFormat.format(Date())}.wav"
    }
    
    private fun loadExistingRecordings() {
        recordings.clear()
        val directory = getExternalFilesDir(null)
        directory?.listFiles()?.filter { 
            it.name.endsWith(".3gp") || it.name.endsWith(".wav") || it.name.endsWith(".mp4")
        }?.forEach { file ->
            recordings.add(Recording(file.name, file.absolutePath, getDuration(file.absolutePath)))
        }
        recordings.sortByDescending { File(it.filePath).lastModified() }
        recordingAdapter.notifyDataSetChanged()
    }
    
    private fun getDuration(filePath: String): String {
        return try {
            val mp = MediaPlayer()
            mp.setDataSource(filePath)
            mp.prepare()
            val duration = mp.duration
            mp.release()
            
            val minutes = (duration / 60000)
            val seconds = ((duration % 60000) / 1000)
            String.format("%02d:%02d", minutes, seconds)
        } catch (e: Exception) {
            "00:00"
        }
    }
    
    private fun playRecording(recording: Recording) {
        try {
            Log.d(TAG, "Attempting to play recording: ${recording.fileName}")
            Log.d(TAG, "File path: ${recording.filePath}")
            Log.d(TAG, "File exists: ${File(recording.filePath).exists()}")
            Log.d(TAG, "File size: ${File(recording.filePath).length()} bytes")
            
            // Request audio focus
            requestAudioFocus()
            
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                // Set audio attributes for proper playback
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                
                setDataSource(recording.filePath)
                prepareAsync() // Use async prepare to avoid blocking
                
                setOnPreparedListener {
                    start()
                    Toast.makeText(this@MainActivity, "Playing ${recording.fileName}", Toast.LENGTH_SHORT).show()
                }
                
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                    abandonAudioFocus()
                    Toast.makeText(this@MainActivity, "Playback finished", Toast.LENGTH_SHORT).show()
                }
                
                setOnErrorListener { mp, what, extra ->
                    Toast.makeText(this@MainActivity, "Playback error: $what, $extra", Toast.LENGTH_SHORT).show()
                    release()
                    mediaPlayer = null
                    abandonAudioFocus()
                    true
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to play recording: ${e.message}", Toast.LENGTH_SHORT).show()
            abandonAudioFocus()
        }
    }
    
    private fun requestAudioFocus() {
        audioManager?.let { am ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .build()
                audioFocusRequest?.let { request ->
                    am.requestAudioFocus(request)
                }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        }
    }
    
    private fun abandonAudioFocus() {
        audioManager?.let { am ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    am.abandonAudioFocusRequest(request)
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }
    
    private fun deleteRecording(recording: Recording) {
        val file = File(recording.filePath)
        if (file.delete()) {
            recordings.remove(recording)
            recordingAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to delete recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showBottomToolbar() {
        bottomToolbar.visibility = View.VISIBLE
    }
    
    private fun hideBottomToolbar() {
        bottomToolbar.visibility = View.GONE
        selectedRecording = null
    }
    
    private fun showRenameDialog(recording: Recording) {
        val editText = EditText(this)
        editText.setText(recording.fileName.substringBeforeLast("."))
        
        AlertDialog.Builder(this)
            .setTitle("Rename Recording")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    renameRecording(recording, newName)
                }
                hideBottomToolbar()
            }
            .setNegativeButton("Cancel") { _, _ ->
                hideBottomToolbar()
            }
            .show()
    }
    
    private fun renameRecording(recording: Recording, newName: String) {
        val oldFile = File(recording.filePath)
        val newFileName = "$newName.3gp"
        val newFile = File(oldFile.parent, newFileName)
        
        if (oldFile.renameTo(newFile)) {
            loadExistingRecordings()
            Toast.makeText(this, "Recording renamed successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to rename recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openTranscribeActivity(recording: Recording) {
        val intent = Intent(this, TranscribeActivity::class.java)
        intent.putExtra("file_path", recording.filePath)
        startActivity(intent)
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomToolbar.visibility == View.VISIBLE) {
                    hideBottomToolbar()
                } else {
                    finish()
                }
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaPlayer?.release()
        stopTimer()
    }
}

data class Recording(
    val fileName: String,
    val filePath: String,
    val duration: String
) 