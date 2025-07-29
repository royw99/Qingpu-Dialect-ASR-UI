#!/usr/bin/env python3
"""
Qingpu Dialect ASR - Python API Client
This script handles the API communication for transcribing audio files.
Called by the Android app with the audio file path as an argument.
"""

import sys
import os
import requests
import json
import time


def transcribe_audio(
    audio_file_path, api_url="http://47.115.207.128:10000/transcribe-and-translate"
):
    """
    Transcribe audio file using the Qingpu Dialect ASR API

    Args:
        audio_file_path (str): Path to the WAV audio file
        api_url (str): API endpoint URL

    Returns:
        dict: API response with transcription and translation
    """

    # Check if file exists
    if not os.path.exists(audio_file_path):
        return {"success": False, "error": f"Audio file not found: {audio_file_path}"}

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
                api_url, files=files, timeout=30  # 30 second timeout
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
                        "request_time": request_time,
                    }
                else:
                    return {
                        "success": False,
                        "error": f"API Error: {result.get('msg', 'Unknown error')}",
                        "api_code": result.get("code"),
                    }

            except json.JSONDecodeError as e:
                return {
                    "success": False,
                    "error": f"Invalid JSON response: {e}",
                    "raw_response": response.text[:500],
                }
        else:
            return {
                "success": False,
                "error": f"HTTP {response.status_code}: {response.reason}",
                "raw_response": response.text[:500],
            }

    except requests.exceptions.Timeout:
        return {"success": False, "error": "Request timeout (30 seconds)"}
    except requests.exceptions.ConnectionError:
        return {
            "success": False,
            "error": "Connection error - check network and server availability",
        }
    except Exception as e:
        return {"success": False, "error": f"Unexpected error: {str(e)}"}


def main():
    """Main function called from command line"""

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
