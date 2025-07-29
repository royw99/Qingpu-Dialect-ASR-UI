package com.example.qingpudialectasr

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
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
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "QingpuASR"
        private const val SAMPLE_RATE = 16000 // Whisper's preferred sample rate
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
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
    
    private var audioRecord: AudioRecord? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingStartTime = 0L
    private var pausedTime = 0L
    private var currentRecordingFile: String? = null
    private var recordingJob: Job? = null
    
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
            // Create WAV file directly
            val fileName = generateWavFileName()
            val file = File(getExternalFilesDir(null), fileName)
            currentRecordingFile = file.absolutePath
            
            Log.d(TAG, "Starting PCM recording to file: $fileName")
            Log.d(TAG, "File path: ${file.absolutePath}")
            Log.d(TAG, "Sample rate: $SAMPLE_RATE Hz")
            
            // Calculate buffer size
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            Log.d(TAG, "AudioRecord buffer size: $bufferSize bytes")
            
            // Create AudioRecord instance
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord not initialized properly")
            }
            
            // Start recording
            audioRecord?.startRecording()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            
            // Start background recording job
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                recordAudioToWav(file, bufferSize)
            }
            
            updateUI()
            startTimer()
            statusText.text = "Recording..."
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun recordAudioToWav(outputFile: File, bufferSize: Int) {
        try {
            val audioBuffer = ShortArray(bufferSize / 2) // 16-bit samples
            val audioDataList = mutableListOf<Short>()
            
            Log.d(TAG, "Starting audio capture loop...")
            
            while (isRecording && !isPaused) {
                val readCount = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (readCount > 0) {
                    // Add audio data to our list
                    for (i in 0 until readCount) {
                        audioDataList.add(audioBuffer[i])
                    }
                }
                
                // Check if we should continue
                if (readCount == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "AudioRecord read error: INVALID_OPERATION")
                    break
                }
            }
            
            Log.d(TAG, "Audio capture finished. Total samples: ${audioDataList.size}")
            
            // Convert to byte array and write WAV file
            val audioBytes = audioDataList.flatMap { sample ->
                listOf(
                    (sample.toInt() and 0xFF).toByte(),
                    ((sample.toInt() shr 8) and 0xFF).toByte()
                )
            }.toByteArray()
            
            writeWavFile(outputFile, audioBytes)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio recording: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun writeWavFile(outputFile: File, audioData: ByteArray) {
        try {
            FileOutputStream(outputFile).use { output ->
                // WAV file parameters
                val channels = 1
                val bitsPerSample = 16
                val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
                val blockAlign = channels * bitsPerSample / 8
                val audioDataSize = audioData.size
                
                Log.d(TAG, "Writing WAV file:")
                Log.d(TAG, "  Audio data size: $audioDataSize bytes")
                Log.d(TAG, "  Sample rate: $SAMPLE_RATE Hz")
                Log.d(TAG, "  Channels: $channels")
                Log.d(TAG, "  Bits per sample: $bitsPerSample")
                
                // Write WAV header (44 bytes)
                val header = ByteArray(44)
                var pos = 0
                
                // RIFF header
                "RIFF".toByteArray().copyInto(header, pos); pos += 4
                writeInt32LE(header, pos, 36 + audioDataSize); pos += 4
                "WAVE".toByteArray().copyInto(header, pos); pos += 4
                
                // fmt chunk
                "fmt ".toByteArray().copyInto(header, pos); pos += 4
                writeInt32LE(header, pos, 16); pos += 4 // fmt chunk size
                writeInt16LE(header, pos, 1); pos += 2 // PCM format
                writeInt16LE(header, pos, channels); pos += 2
                writeInt32LE(header, pos, SAMPLE_RATE); pos += 4
                writeInt32LE(header, pos, byteRate); pos += 4
                writeInt16LE(header, pos, blockAlign); pos += 2
                writeInt16LE(header, pos, bitsPerSample); pos += 2
                
                // data chunk
                "data".toByteArray().copyInto(header, pos); pos += 4
                writeInt32LE(header, pos, audioDataSize); pos += 4
                
                // Write header and audio data
                output.write(header)
                output.write(audioData)
                
                Log.d(TAG, "WAV file written successfully: ${outputFile.absolutePath}")
                Log.d(TAG, "Total file size: ${outputFile.length()} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing WAV file: ${e.message}", e)
            throw e
        }
    }
    
    private fun writeInt32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
    
    private fun writeInt16LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
    
    private fun pauseRecording() {
        try {
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
            isRecording = false
            isPaused = false
            
            // Stop AudioRecord
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null
            
            // Wait for recording job to complete
            runBlocking {
                recordingJob?.join()
            }
            recordingJob = null
            
            stopTimer()
            updateUI()
            
            statusText.text = "Recording saved"
            timerText.text = "00:00"
            
            currentRecordingFile?.let { filePath ->
                val file = File(filePath)
                Log.d(TAG, "Recording completed: ${file.name}, size: ${file.length()} bytes")
                
                // Also copy to external accessible location for Python script
                copyToExternalLocation(file)
                
                Toast.makeText(this, "Recording saved: ${file.name}", Toast.LENGTH_SHORT).show()
            }
            
            loadExistingRecordings()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}", e)
            Toast.makeText(this, "Failed to stop recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun copyToExternalLocation(sourceFile: File) {
        try {
            // Use multiple accessible directories
            val possibleDirs = listOf(
                File(getExternalFilesDir(null), "shared"),  // App's external files dir
                File("/sdcard/Download/QingpuASR"),         // Download folder
                File("/sdcard/Documents/QingpuASR"),        // Documents folder
                File("/storage/emulated/0/QingpuASR")       // Primary external storage
            )
            
            var successfulDir: File? = null
            
            for (dir in possibleDirs) {
                try {
                    if (!dir.exists()) {
                        val created = dir.mkdirs()
                        Log.d(TAG, "Attempted to create directory ${dir.absolutePath}: $created")
                    }
                    
                    if (dir.exists() && dir.canWrite()) {
                        val externalFile = File(dir, sourceFile.name)
                        sourceFile.copyTo(externalFile, overwrite = true)
                        
                        if (externalFile.exists() && externalFile.length() > 0) {
                            successfulDir = dir
                            Log.d(TAG, "Successfully copied WAV file to: ${externalFile.absolutePath}")
                            Log.d(TAG, "File size: ${externalFile.length()} bytes")
                            
                            // Also copy the Python script to the same directory
                            copyPythonScriptToExternal(dir)
                            break
                        }
                    } else {
                        Log.w(TAG, "Directory not writable: ${dir.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to use directory ${dir.absolutePath}: ${e.message}")
                }
            }
            
            if (successfulDir == null) {
                Log.e(TAG, "Failed to copy file to any accessible location")
                Toast.makeText(this, "Warning: Could not copy to external location", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "File copied to: ${successfulDir.name}", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file to external location: ${e.message}", e)
        }
    }
    
    private fun copyPythonScriptToExternal(externalDir: File) {
        try {
            val pythonScript = """#!/usr/bin/env python3
# Qingpu Dialect ASR - Python API Client
# This script handles the API communication for transcribing audio files.
# Called by the Android app with the audio file path as an argument.

import sys
import os
import requests
import json
import time

def transcribe_audio(audio_file_path, api_url="http://47.115.207.128:10000/transcribe-and-translate"):
    # Check if file exists
    if not os.path.exists(audio_file_path):
        return {
            "success": False,
            "error": f"Audio file not found: {audio_file_path}"
        }
    
    # Check file size
    file_size = os.path.getsize(audio_file_path)
    print(f"Processing audio file: {os.path.basename(audio_file_path)}")
    print(f"File size: {file_size} bytes")
    
    try:
        # Open and send file to API
        with open(audio_file_path, "rb") as f:
            files = {"audio_file": f}
            
            print(f"Sending request to: {api_url}")
            start_time = time.time()
            
            response = requests.post(
                api_url,
                files=files,
                timeout=30
            )
            
            request_time = time.time() - start_time
            print(f"Request completed in {request_time:.2f} seconds")
            print(f"Response status: {response.status_code}")
            
        # Parse response
        if response.status_code == 200:
            try:
                result = response.json()
                print(f"API Response: {result}")
                
                if result.get("code") == 200:
                    return {
                        "success": True,
                        "transcription": result.get("transcription", ""),
                        "translation": result.get("translation", ""),
                        "request_time": request_time
                    }
                else:
                    return {
                        "success": False,
                        "error": f"API Error: {result.get('msg', 'Unknown error')}",
                        "api_code": result.get("code")
                    }
                    
            except json.JSONDecodeError as e:
                return {
                    "success": False,
                    "error": f"Invalid JSON response: {e}",
                    "raw_response": response.text[:500]
                }
        else:
            return {
                "success": False,
                "error": f"HTTP {response.status_code}: {response.reason}",
                "raw_response": response.text[:500]
            }
            
    except requests.exceptions.Timeout:
        return {
            "success": False,
            "error": "Request timeout (30 seconds)"
        }
    except requests.exceptions.ConnectionError:
        return {
            "success": False,
            "error": "Connection error - check network and server availability"
        }
    except Exception as e:
        return {
            "success": False,
            "error": f"Unexpected error: {str(e)}"
        }

def main():
    if len(sys.argv) != 2:
        print("Usage: python transcribe_audio.py <audio_file_path>")
        sys.exit(1)
    
    audio_file_path = sys.argv[1]
    
    print("=" * 50)
    print("Qingpu Dialect ASR - Python Client")
    print("=" * 50)
    
    result = transcribe_audio(audio_file_path)
    
    # Output result as JSON for Android to parse
    print("\n" + "=" * 20 + " RESULT " + "=" * 20)
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
"""
            
            val scriptFile = File(externalDir, "transcribe_audio.py")
            scriptFile.writeText(pythonScript)
            
            Log.d(TAG, "Python script copied to: ${scriptFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy Python script: ${e.message}", e)
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
            it.name.endsWith(".wav")
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
        val extension = oldFile.extension
        val newFileName = "$newName.$extension"
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
        // Clean up audio recording
        isRecording = false
        audioRecord?.release()
        recordingJob?.cancel()
        
        mediaPlayer?.release()
        stopTimer()
    }
}

data class Recording(
    val fileName: String,
    val filePath: String,
    val duration: String
) 