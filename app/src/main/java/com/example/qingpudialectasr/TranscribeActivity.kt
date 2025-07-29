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
import java.util.concurrent.TimeUnit

class TranscribeActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "QingpuASR_Automated"
        private const val API_URL = "http://47.115.207.128:10000/transcribe-and-translate"
    }
    
    private lateinit var shanghainese_text: TextView
    private lateinit var mandarin_text: TextView
    private lateinit var backButton: Button
    private lateinit var progressBar: ProgressBar
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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
            startAutomatedTranscription(filePath)
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
    
    private fun startAutomatedTranscription(filePath: String) {
        transcribeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    shanghainese_text.text = "Starting automated transcription..."
                    mandarin_text.text = "Preparing audio file..."
                }
                
                val audioFile = File(filePath)
                Log.d(TAG, "=== AUTOMATED TRANSCRIPTION PROCESS ===")
                Log.d(TAG, "Source audio file: ${audioFile.name}")
                Log.d(TAG, "File path: ${audioFile.absolutePath}")
                Log.d(TAG, "File exists: ${audioFile.exists()}")
                Log.d(TAG, "File size: ${audioFile.length()} bytes")
                
                if (!audioFile.exists() || audioFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        shanghainese_text.text = "Error: Audio file not found or empty"
                        mandarin_text.text = "Please try recording again"
                        Toast.makeText(this@TranscribeActivity, "Invalid audio file", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    shanghainese_text.text = "Audio file ready (${audioFile.length()} bytes)"
                    mandarin_text.text = "Copying to accessible location..."
                }
                
                // Step 1: Copy file to accessible location
                val externalFile = copyToAccessibleLocation(audioFile)
                if (externalFile == null) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        shanghainese_text.text = "Error: Could not save file to accessible location"
                        mandarin_text.text = "Check storage permissions"
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    shanghainese_text.text = "File saved: ${externalFile.name}"
                    mandarin_text.text = "Connecting to transcription API..."
                }
                
                // Step 2: Make direct API call (exactly like Python script)
                val result = makeDirectApiCall(externalFile)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    
                    if (result.success) {
                        shanghainese_text.text = result.transcription
                        mandarin_text.text = result.translation
                        Toast.makeText(this@TranscribeActivity, "Success! (${result.requestTime}s)", Toast.LENGTH_SHORT).show()
                        Log.i(TAG, "Transcription successful!")
                        Log.i(TAG, "Shanghainese: ${result.transcription}")
                        Log.i(TAG, "Mandarin: ${result.translation}")
                    } else {
                        shanghainese_text.text = "Transcription failed: ${result.error}"
                        mandarin_text.text = "File saved to: ${externalFile.absolutePath}"
                        Toast.makeText(this@TranscribeActivity, "API Error: ${result.error}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Transcription failed: ${result.error}")
                        Log.e(TAG, "Details: ${result.details}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in automated transcription: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    shanghainese_text.text = "Unexpected error occurred"
                    mandarin_text.text = "Error: ${e.message}"
                    Toast.makeText(this@TranscribeActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun copyToAccessibleLocation(sourceFile: File): File? {
        return try {
            // Use the app's external files directory (most reliable)
            val externalDir = File(getExternalFilesDir(null), "transcription")
            if (!externalDir.exists()) {
                val created = externalDir.mkdirs()
                Log.d(TAG, "Created transcription directory: $created")
            }
            
            val externalFile = File(externalDir, "audio_${System.currentTimeMillis()}.wav")
            sourceFile.copyTo(externalFile, overwrite = true)
            
            if (externalFile.exists() && externalFile.length() > 0L) {
                Log.d(TAG, "File copied successfully to: ${externalFile.absolutePath}")
                Log.d(TAG, "Copied file size: ${externalFile.length()} bytes")
                externalFile
            } else {
                Log.e(TAG, "File copy verification failed")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file to accessible location: ${e.message}", e)
            null
        }
    }
    
    private suspend fun makeDirectApiCall(audioFile: File): TranscriptionResult {
        return try {
            Log.d(TAG, "=== MAKING DIRECT API CALL ===")
            Log.d(TAG, "API URL: $API_URL")
            Log.d(TAG, "Audio file: ${audioFile.absolutePath}")
            Log.d(TAG, "File size: ${audioFile.length()} bytes")
            
            withContext(Dispatchers.Main) {
                mandarin_text.text = "Uploading to server..."
            }
            
            // Create multipart request exactly like Python's requests.post
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio_file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .build()
            
            val request = Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .addHeader("User-Agent", "QingpuDialectASR-Android/1.0")
                .build()
            
            Log.d(TAG, "Request created, sending to server...")
            Log.d(TAG, "Request body size: ${requestBody.contentLength()} bytes")
            
            val startTime = System.currentTimeMillis()
            
            client.newCall(request).execute().use { response ->
                val requestTime = (System.currentTimeMillis() - startTime) / 1000.0
                val responseBody = response.body?.string()
                
                Log.d(TAG, "=== API RESPONSE ===")
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response message: ${response.message}")
                Log.d(TAG, "Request time: ${requestTime}s")
                Log.d(TAG, "Response body: $responseBody")
                
                withContext(Dispatchers.Main) {
                    mandarin_text.text = "Processing response..."
                }
                
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (jsonResponse.has("code")) {
                            val code = jsonResponse.getInt("code")
                            Log.d(TAG, "API response code: $code")
                            
                            if (code == 200) {
                                val transcription = jsonResponse.optString("transcription", "")
                                val translation = jsonResponse.optString("translation", "")
                                
                                if (transcription.isNotEmpty() && translation.isNotEmpty()) {
                                    TranscriptionResult(
                                        success = true,
                                        transcription = transcription,
                                        translation = translation,
                                        requestTime = requestTime.toString()
                                    )
                                } else {
                                    TranscriptionResult(
                                        success = false,
                                        error = "Empty transcription or translation",
                                        details = "Response: $responseBody"
                                    )
                                }
                            } else {
                                val errorMsg = jsonResponse.optString("msg", "Unknown API error")
                                TranscriptionResult(
                                    success = false,
                                    error = "API Error (code $code): $errorMsg",
                                    details = responseBody
                                )
                            }
                        } else {
                            TranscriptionResult(
                                success = false,
                                error = "Invalid API response format",
                                details = "Missing 'code' field in response"
                            )
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON parsing error: ${e.message}", e)
                        TranscriptionResult(
                            success = false,
                            error = "Failed to parse API response",
                            details = "JSON error: ${e.message}"
                        )
                    }
                } else {
                    TranscriptionResult(
                        success = false,
                        error = "HTTP ${response.code}: ${response.message}",
                        details = responseBody ?: "No response body"
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "API call failed: ${e.message}", e)
            TranscriptionResult(
                success = false,
                error = "Network error: ${e.message}",
                details = "Check network connection and server availability"
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        transcribeJob?.cancel()
    }
    
    data class TranscriptionResult(
        val success: Boolean,
        val transcription: String = "",
        val translation: String = "",
        val requestTime: String = "",
        val error: String = "",
        val details: String? = null
    )
}