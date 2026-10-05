/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors / Foldboard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

import android.os.Environment
import java.io.File

object AvfBridge {

    private fun getSharedDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    /**
     * Injects voice text into Debian AVF terminal via .voice_in.txt
     */
    fun sendVoiceToAvf(text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val file = File(getSharedDir(), ".voice_in.txt")
            file.writeText(text.trim() + "\n")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Syncs Android clipboard item to Debian AVF via .clipboard_sync.txt
     */
    fun syncClipboardToAvf(text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val file = File(getSharedDir(), ".clipboard_sync.txt")
            file.writeText(text + "\n")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads pending incoming clipboard items from Debian AVF (.clipboard_to_android.txt)
     */
    fun readIncomingFromAvf(): String? {
        return try {
            val file = File(getSharedDir(), ".clipboard_to_android.txt")
            if (file.exists() && file.length() > 0) {
                val content = file.readText().trim()
                file.delete()
                content.ifEmpty { null }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
