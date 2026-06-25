package com.example.utils

import android.util.Log
import com.example.data.AttendanceLog
import com.example.data.Attendee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat

object SpreadsheetSyncHelper {

    private const val TAG = "SpreadsheetSyncHelper"

    /**
     * Parses a CSV string (from a file or Google Sheets CSV export link)
     * and returns a list of Attendees.
     * Expects headers like Name, Role, ID or simple values.
     */
    fun parseCsvToAttendees(csvContent: String): List<Attendee> {
        val attendees = mutableListOf<Attendee>()
        val lines = csvContent.lines()
        if (lines.isEmpty()) return emptyList()

        // Detect columns or headers
        val firstLine = lines.firstOrNull()?.trim() ?: return emptyList()
        val separator = if (firstLine.contains(";")) ";" else ","
        val headers = firstLine.split(separator).map { it.trim().lowercase() }

        // Find indices of columns
        var nameIndex = headers.indexOfFirst { it.contains("nama siswa") || it.contains("nama") || it.contains("name") }
        var roleIndex = headers.indexOfFirst { it.contains("kelas") || it.contains("role") || it.contains("jabatan") || it.contains("kategori") }
        var idIndex = headers.indexOfFirst { it.contains("nisn") || it.contains("nis") || it.contains("id") || it.contains("uid") || it.contains("nip") }

        // Fallback to default indices if headers not found
        if (nameIndex == -1) nameIndex = 1 // default second column (Nama Siswa)
        if (roleIndex == -1) roleIndex = 3 // default fourth column (Kelas)
        if (idIndex == -1) idIndex = 2 // default third column (NISN)

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            // Split handling potential quotes in CSV
            val tokens = splitCsvLine(line, separator)
            if (tokens.size <= nameIndex) continue

            val name = tokens.getOrNull(nameIndex)?.trim() ?: ""
            if (name.isEmpty()) continue

            val role = tokens.getOrNull(roleIndex)?.trim() ?: "Siswa"
            val rawUid = tokens.getOrNull(idIndex)?.trim() ?: ""
            val uid = if (rawUid.isNotBlank()) rawUid.uppercase() else "ID-${(100000..999999).random()}"

            attendees.add(Attendee(uid = uid, name = name, role = role))
        }
        return attendees
    }

    private fun splitCsvLine(line: String, separator: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = line.length
        while (i < len) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < len && line[i + 1] == '\"') {
                        curVal.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (line.startsWith(separator, i)) {
                    result.add(curVal.toString())
                    curVal = StringBuilder()
                    i += separator.length - 1
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }

    /**
     * Fetch standard published Google Sheet CSV and parse it to list of Attendees.
     */
    suspend fun fetchPublishedSheetCsv(urlString: String): List<Attendee> = withContext(Dispatchers.IO) {
        val content = fetchPublishedSheetCsvRaw(urlString)
        return@withContext parseCsvToAttendees(content)
    }

    /**
     * Fetch standard published Google Sheet CSV raw content as string.
     */
    suspend fun fetchPublishedSheetCsvRaw(urlString: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                return@withContext reader.use { it.readText() }
            } else {
                Log.e(TAG, "Server returned response code $responseCode")
                throw Exception("Gagal mengunduh sheet. Kode respon: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching published sheet raw", e)
            throw e
        }
    }

    /**
     * Post attendance logs list to a deployed Google Apps Script Web App.
     */
    suspend fun syncLogsToGoogleSheets(webAppUrlString: String, logs: List<AttendanceLog>): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(webAppUrlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("Accept", "application/json")

            // Format JSON Array payload
            val jsonArray = JSONArray()
            logs.forEach { log ->
                val jsonObj = JSONObject().apply {
                    put("id", log.id)
                    put("uid", log.uid)
                    put("name", log.name)
                    put("role", log.role)
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp)))
                    put("type", log.type)
                    put("status", log.status)
                    put("sessionName", log.sessionName ?: "")
                }
                jsonArray.put(jsonObj)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonArray.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_SEE_OTHER || responseCode == 302) {
                // Apps script might redirect
                var finalConnection = connection
                if (responseCode == 302 || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    val newUrl = connection.getHeaderField("Location")
                    val redirectUrl = URL(newUrl)
                    finalConnection = redirectUrl.openConnection() as HttpURLConnection
                    finalConnection.requestMethod = "GET" // standard for script redirects
                }
                val reader = BufferedReader(InputStreamReader(finalConnection.inputStream))
                val responseText = reader.use { it.readText() }
                Log.d(TAG, "Response from Google Apps Script: $responseText")
                return@withContext true
            } else {
                Log.e(TAG, "Sync failed with status: $responseCode")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing logs to spreadsheet", e)
            return@withContext false
        }
    }

    /**
     * Import attendee list from Apps Script (doGet).
     */
    suspend fun importFromAppsScript(webAppUrlString: String): List<Attendee> = withContext(Dispatchers.IO) {
        val responseText = importFromAppsScriptRaw(webAppUrlString)
        return@withContext parseJsonToAttendees(responseText)
    }

    /**
     * Import attendee list from Apps Script (doGet) raw response string.
     */
    suspend fun importFromAppsScriptRaw(webAppUrlString: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(webAppUrlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 302) {
                var finalConnection = connection
                if (responseCode == 302) {
                    val newUrl = connection.getHeaderField("Location")
                    val redirectUrl = URL(newUrl)
                    finalConnection = redirectUrl.openConnection() as HttpURLConnection
                    finalConnection.requestMethod = "GET"
                }
                val reader = BufferedReader(InputStreamReader(finalConnection.inputStream))
                return@withContext reader.use { it.readText() }
            } else {
                throw Exception("Response code $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing from Apps Script raw", e)
            throw e
        }
    }

    /**
     * Parse Apps Script JSON response to Attendees list.
     */
    fun parseJsonToAttendees(responseText: String): List<Attendee> {
        val jsonArray = JSONArray(responseText)
        val list = mutableListOf<Attendee>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val uid = obj.optString("uid", "ID-${(100000..999999).random()}")
            val name = obj.optString("name", "")
            val role = obj.optString("role", "Siswa")
            if (name.isNotEmpty()) {
                list.add(Attendee(uid = uid, name = name, role = role))
            }
        }
        return list
    }
}
