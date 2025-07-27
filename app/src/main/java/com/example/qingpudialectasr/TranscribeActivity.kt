package com.example.qingpudialectasr

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

class TranscribeActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "QingpuASR_API"
    }
    
    private lateinit var shanghainese_text: TextView
    private lateinit var mandarin_text: TextView
    private lateinit var backButton: Button
    private lateinit var progressBar: ProgressBar
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var transcribeJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transcribe)
        
        initViews()
        setupClickListeners()
        
        // Get the file path from intent
        val filePath = intent.getStringExtra("file_path")
        if (filePath != null) {
            transcribeAudio(filePath)
        } else {
            Toast.makeText(this, "Error: No audio file provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun initViews() {
        shanghainese_text = findViewById(R.id.shanghainese_text)
        mandarin_text = findViewById(R.id.mandarin_text)
        backButton = findViewById(R.id.backButton)
        progressBar = findViewById(R.id.progressBar)
    }
    
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun transcribeAudio(filePath: String) {
        transcribeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    shanghainese_text.text = "Transcribing audio..."
                    mandarin_text.text = "Please wait..."
                }
                
                val audioFile = File(filePath)
                Log.d(TAG, "Attempting to transcribe file: ${audioFile.name}")
                Log.d(TAG, "File path: ${audioFile.absolutePath}")
                Log.d(TAG, "File exists: ${audioFile.exists()}")
                Log.d(TAG, "File size: ${audioFile.length()} bytes")
                Log.d(TAG, "File can read: ${audioFile.canRead()}")
                Log.d(TAG, "File last modified: ${Date(audioFile.lastModified())}")
                
                if (!audioFile.exists()) {
                    Log.e(TAG, "Audio file not found at path: ${audioFile.absolutePath}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TranscribeActivity, "Audio file not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }
                
                // Debug logging
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TranscribeActivity, "File: ${audioFile.name}, Size: ${audioFile.length()} bytes", Toast.LENGTH_LONG).show()
                }
                
                // Read first few bytes to check file format
                try {
                    val header = ByteArray(12)
                    audioFile.inputStream().use { 
                        val bytesRead = it.read(header)
                        Log.d(TAG, "File header bytes ($bytesRead): ${header.joinToString(", ") { b -> "%02x".format(b) }}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read file header: ${e.message}")
                }
                
                // Test different content types
                val contentTypes = listOf(
                    "audio/3gpp",
                    "audio/wav", 
                    "audio/mpeg",
                    "application/octet-stream"
                )
                
                for (contentType in contentTypes) {
                    try {
                        val success = attemptTranscription(audioFile, contentType)
                        if (success) {
                            return@launch
                        }
                    } catch (e: Exception) {
                        println("Failed with content type $contentType: ${e.message}")
                    }
                }
                
                // If all content types failed, show final error
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    shanghainese_text.text = "All transcription attempts failed"
                    mandarin_text.text = "File format may not be supported by the API"
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    shanghainese_text.text = "Unexpected error"
                    mandarin_text.text = "Please try again: ${e.message}"
                    Toast.makeText(this@TranscribeActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private suspend fun attemptTranscription(audioFile: File, contentType: String): Boolean {
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio_file",
                    audioFile.name,
                    audioFile.asRequestBody(contentType.toMediaType())
                )
                .build()
            
                            // Log request details
                Log.d(TAG, "Preparing API request with content type: $contentType")
                Log.d(TAG, "Request body size: ${requestBody.contentLength()} bytes")
                
                val request = Request.Builder()
                    .url("http://47.115.207.128:10000/transcribe-and-translate")
                    .post(requestBody)
                    .addHeader("User-Agent", "QingpuDialectASR/1.0")
                    .addHeader("Accept", "application/json")
                    .build()
                
                Log.d(TAG, "Request headers: ${request.headers}")
            
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                withContext(Dispatchers.Main) {
                    // Debug: Show response details
                    Toast.makeText(this@TranscribeActivity, "Trying $contentType - Response: ${response.code}", Toast.LENGTH_SHORT).show()
                }
                
                if (response.isSuccessful && responseBody != null) {
                    try {
                        // Debug: Show raw response
                        println("API Response ($contentType): $responseBody")
                        
                        val jsonResponse = JSONObject(responseBody)
                        val code = jsonResponse.getInt("code")
                        
                        if (code == 200) {
                            val transcription = jsonResponse.getString("transcription")
                            val translation = jsonResponse.getString("translation")
                            
                            withContext(Dispatchers.Main) {
                                progressBar.visibility = View.GONE
                                shanghainese_text.text = transcription
                                mandarin_text.text = translation
                                Toast.makeText(this@TranscribeActivity, "Success with $contentType!", Toast.LENGTH_SHORT).show()
                            }
                            return@use true
                        } else {
                            val errorMsg = jsonResponse.optString("msg", "Unknown error")
                            println("API Error ($contentType): $errorMsg")
                            return@use false
                        }
                    } catch (e: Exception) {
                        println("JSON Parse Error ($contentType): ${e.message}")
                        println("Raw response: $responseBody")
                        return@use false
                    }
                } else {
                    println("HTTP Error ($contentType): ${response.code}")
                    println("Response body: $responseBody")
                    return@use false
                }
            }
        } catch (e: Exception) {
            println("Network Error ($contentType): ${e.message}")
            false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        transcribeJob?.cancel()
    }
} 