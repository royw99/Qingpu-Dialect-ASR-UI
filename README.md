# Qingpu Dialect ASR (Automatic Speech Recognition)

An Android application for recording and transcribing Qingpu dialect speech. The app provides features for audio recording management and automatic transcription to both Shanghainese text and Mandarin Chinese.

## Features

- **Audio Recording**: Record, pause, resume, and stop functionality
- **Recording Management**: View, play, rename, and delete recordings
- **Transcription**: Convert Qingpu dialect speech to text
  - Shanghainese transcription
  - Mandarin Chinese translation
- **Modern UI**: Material Design interface with intuitive controls

## Technical Details

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 36
- **Language**: Kotlin
- **Architecture**: Single Activity with modular design
- **Audio Format**: WAV for better compatibility
- **Network**: REST API integration for transcription

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/[your-username]/QingpuDialectASR.git
   ```

2. Open in Android Studio:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. Build the project:
   ```bash
   ./gradlew build
   ```

4. Run on device/emulator:
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio or run:
     ```bash
     ./gradlew installDebug
     ```

## API Integration

The app integrates with a speech recognition API:
- Endpoint: `http://47.115.207.128:10000/transcribe-and-translate`
- Method: POST
- Input: Audio file (WAV format)
- Output: JSON with Shanghainese transcription and Mandarin translation

## Contributing

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/my-new-feature`
3. Commit your changes: `git commit -am 'Add some feature'`
4. Push to the branch: `git push origin feature/my-new-feature`
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details. 