package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AttendanceLog
import com.example.data.AttendanceRepository
import com.example.data.AttendanceSession
import com.example.data.Attendee
import com.example.utils.SpreadsheetSyncHelper
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class ScanResultState(
    val success: Boolean,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class AttendanceViewModel(
    application: Application,
    private val repository: AttendanceRepository
) : AndroidViewModel(application) {

    // SharedPreferences setup
    private val sharedPrefs = application.getSharedPreferences("absenqr_prefs", Context.MODE_PRIVATE)

    fun getAppsScriptUrl(): String {
        return sharedPrefs.getString("apps_script_url", "") ?: ""
    }

    fun saveAppsScriptUrl(url: String) {
        sharedPrefs.edit().putString("apps_script_url", url.trim()).apply()
    }

    fun isAutoSyncEnabled(): Boolean {
        return sharedPrefs.getBoolean("auto_sync_enabled", true)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_sync_enabled", enabled).apply()
    }

    // School Info States & Helpers
    fun getSchoolName(): String {
        return sharedPrefs.getString("school_name", "Sekolah X-Degan QR") ?: "Sekolah X-Degan QR"
    }

    fun getSchoolAddress(): String {
        return sharedPrefs.getString("school_address", "Jl. Pendidikan No. 123") ?: "Jl. Pendidikan No. 123"
    }

    fun getSchoolLogoPath(): String {
        return sharedPrefs.getString("school_logo_path", "") ?: ""
    }

    private val _schoolName = MutableStateFlow(getSchoolName())
    val schoolName: StateFlow<String> = _schoolName.asStateFlow()

    private val _schoolAddress = MutableStateFlow(getSchoolAddress())
    val schoolAddress: StateFlow<String> = _schoolAddress.asStateFlow()

    private val _schoolLogoPath = MutableStateFlow(getSchoolLogoPath())
    val schoolLogoPath: StateFlow<String> = _schoolLogoPath.asStateFlow()

    fun updateSchoolInfo(name: String, address: String, logoPath: String) {
        sharedPrefs.edit()
            .putString("school_name", name.trim())
            .putString("school_address", address.trim())
            .putString("school_logo_path", logoPath)
            .apply()
        _schoolName.value = name
        _schoolAddress.value = address
        _schoolLogoPath.value = logoPath
    }

    // Student Photo Update Helper
    fun updateAttendeePhoto(attendee: Attendee, photoPath: String) {
        viewModelScope.launch {
            repository.insertAttendee(attendee.copy(photoPath = photoPath))
        }
    }

    // Network Connectivity monitoring
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("AttendanceViewModel", "Internet connected! Auto-sync triggering...")
                    if (isAutoSyncEnabled() && getAppsScriptUrl().isNotBlank()) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(1000) // slight delay to let network stabilize
                            autoSyncLogs()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Failed to register network callback", e)
        }
    }

    suspend fun autoSyncLogs() {
        val url = getAppsScriptUrl()
        if (url.isBlank()) return
        try {
            // Get current logs list safely
            val currentLogs = repository.allLogs.stateIn(viewModelScope).value
            if (currentLogs.isNotEmpty()) {
                val success = SpreadsheetSyncHelper.syncLogsToGoogleSheets(url, currentLogs)
                if (success) {
                    Log.d("AttendanceViewModel", "Auto-sync logs successful: ${currentLogs.size} logs.")
                } else {
                    Log.e("AttendanceViewModel", "Auto-sync failed.")
                }
            }
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Auto-sync logs failed with exception", e)
        }
    }

    // UI States from Repository
    val attendees: StateFlow<List<Attendee>> = repository.allAttendees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<AttendanceLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<AttendanceSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Interactive States
    private val _scanResult = MutableStateFlow<ScanResultState?>(null)
    val scanResult = _scanResult.asStateFlow()

    // Currently logged-in/selected attendee for "Self Attendance" mode
    private val _selectedAttendee = MutableStateFlow<Attendee?>(null)
    val selectedAttendee = _selectedAttendee.asStateFlow()

    fun selectAttendee(attendee: Attendee?) {
        _selectedAttendee.value = attendee
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    // Insert Attendees (Karyawan/Siswa)
    fun addAttendee(name: String, role: String, customUid: String?) {
        viewModelScope.launch {
            val finalUid = if (customUid.isNullOrBlank()) {
                "ID-${UUID.randomUUID().toString().take(6).uppercase()}"
            } else {
                customUid.trim().uppercase()
            }
            val attendee = Attendee(uid = finalUid, name = name.trim(), role = role.trim())
            repository.insertAttendee(attendee)
        }
    }

    fun deleteAttendee(id: Int) {
        viewModelScope.launch {
            repository.deleteAttendee(id)
        }
    }

    // Insert Attendance Sessions (Locations/Classes)
    fun addSession(title: String) {
        viewModelScope.launch {
            val randomCode = "LOC-${(10000..99999).random()}"
            val session = AttendanceSession(code = randomCode, title = title.trim())
            repository.insertSession(session)
        }
    }

    fun deleteSession(id: Int) {
        viewModelScope.launch {
            repository.deleteSession(id)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    // Import from manual CSV text / CSV file content
    fun importFromCsvText(csvText: String, onFinished: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            try {
                val list = SpreadsheetSyncHelper.parseCsvToAttendees(csvText)
                if (list.isNotEmpty()) {
                    list.forEach { repository.insertAttendee(it) }
                    onFinished(true, list.size)
                } else {
                    onFinished(false, 0)
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error importing CSV text", e)
                onFinished(false, 0)
            }
        }
    }

    private fun saveOfflineCache(url: String, data: String) {
        sharedPrefs.edit()
            .putString("offline_cache_data_$url", data)
            .putLong("offline_cache_time_$url", System.currentTimeMillis())
            .apply()
    }

    private fun getOfflineCache(url: String): Pair<String?, Long> {
        val data = sharedPrefs.getString("offline_cache_data_$url", null)
        val time = sharedPrefs.getLong("offline_cache_time_$url", 0L)
        return Pair(data, time)
    }

    // Import from Google Sheet CSV export link
    fun importFromGoogleSheetsCsv(publishedUrl: String, onFinished: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch {
            try {
                val csvContent = SpreadsheetSyncHelper.fetchPublishedSheetCsvRaw(publishedUrl)
                if (csvContent.isNotEmpty()) {
                    saveOfflineCache(publishedUrl, csvContent)
                    val list = SpreadsheetSyncHelper.parseCsvToAttendees(csvContent)
                    if (list.isNotEmpty()) {
                        list.forEach { repository.insertAttendee(it) }
                        onFinished(true, list.size, "Berhasil mengimpor ${list.size} anggota dari Google Sheets!")
                    } else {
                        onFinished(false, 0, "File kosong atau format kolom tidak dikenali.")
                    }
                } else {
                    onFinished(false, 0, "Koneksi mengembalikan data kosong.")
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error importing Sheets CSV, checking cache", e)
                val (cachedData, cachedTime) = getOfflineCache(publishedUrl)
                if (!cachedData.isNullOrEmpty()) {
                    val list = SpreadsheetSyncHelper.parseCsvToAttendees(cachedData)
                    if (list.isNotEmpty()) {
                        list.forEach { repository.insertAttendee(it) }
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                        val formattedDate = sdf.format(java.util.Date(cachedTime))
                        onFinished(true, list.size, "Offline/Error. Berhasil memuat ${list.size} siswa dari cache offline terakhir ($formattedDate)!")
                    } else {
                        onFinished(false, 0, "Gagal mengimpor dan tidak ada cache valid.")
                    }
                } else {
                    onFinished(false, 0, "Gagal mengimpor: ${e.localizedMessage ?: "Masalah jaringan."} (Tidak ada cache offline)")
                }
            }
        }
    }

    // Sync all current attendance logs to Google Sheets (Web App)
    fun syncLogsToGoogleSheets(webAppUrl: String, onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentLogs = repository.allLogs.stateIn(viewModelScope).value
                if (currentLogs.isEmpty()) {
                    onFinished(false, "Belum ada log riwayat absensi untuk dikirim.")
                    return@launch
                }
                val success = SpreadsheetSyncHelper.syncLogsToGoogleSheets(webAppUrl, currentLogs)
                if (success) {
                    onFinished(true, "Sinkronisasi berhasil! ${currentLogs.size} log kehadiran terkirim.")
                } else {
                    onFinished(false, "Gagal menyambung ke Google Apps Script Web App.")
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error syncing logs", e)
                onFinished(false, "Terjadi kesalahan: ${e.localizedMessage}")
            }
        }
    }

    // Import from Google Sheets via Apps Script Web App JSON
    fun importFromGoogleAppsScript(webAppUrl: String, onFinished: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonContent = SpreadsheetSyncHelper.importFromAppsScriptRaw(webAppUrl)
                if (jsonContent.isNotEmpty()) {
                    saveOfflineCache(webAppUrl, jsonContent)
                    val list = SpreadsheetSyncHelper.parseJsonToAttendees(jsonContent)
                    if (list.isNotEmpty()) {
                        list.forEach { repository.insertAttendee(it) }
                        onFinished(true, list.size, "Berhasil mengimpor ${list.size} siswa dari Google Spreadsheet!")
                    } else {
                        onFinished(false, 0, "Google Spreadsheet kosong atau tidak mengembalikan data.")
                    }
                } else {
                    onFinished(false, 0, "Data kosong.")
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error importing Apps Script, checking cache", e)
                val (cachedData, cachedTime) = getOfflineCache(webAppUrl)
                if (!cachedData.isNullOrEmpty()) {
                    val list = SpreadsheetSyncHelper.parseJsonToAttendees(cachedData)
                    if (list.isNotEmpty()) {
                        list.forEach { repository.insertAttendee(it) }
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                        val formattedDate = sdf.format(java.util.Date(cachedTime))
                        onFinished(true, list.size, "Offline/Error. Berhasil memuat ${list.size} siswa dari cache offline terakhir ($formattedDate)!")
                    } else {
                        onFinished(false, 0, "Gagal mengimpor dan tidak ada cache valid.")
                    }
                } else {
                    onFinished(false, 0, "Gagal mengimpor: ${e.localizedMessage ?: "Koneksi Web App error."} (Tidak ada cache offline)")
                }
            }
        }
    }

    /**
     * Launch the Google Play Services Code Scanner to perform QR scanning.
     * @param type "MASUK" or "PULANG"
     * @param mode "TERMINAL" (scan attendee QRs) or "MANDIRI" (employee scans location QR)
     */
    fun startQrScanner(context: Context, type: String, mode: String = "TERMINAL") {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(context, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (rawValue != null) {
                    vibrateDevice(context)
                    processScannedCode(rawValue, type, mode)
                } else {
                    _scanResult.value = ScanResultState(
                        success = false,
                        title = "Pemindaian Gagal",
                        message = "QR Code kosong atau tidak terbaca."
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceViewModel", "Scan failed", e)
                _scanResult.value = ScanResultState(
                    success = false,
                    title = "Batal / Gagal",
                    message = "Pemindaian dibatalkan atau terjadi masalah koneksi Google Play Services."
                )
            }
    }

    private fun processScannedCode(rawValue: String, type: String, mode: String) {
        viewModelScope.launch {
            if (mode == "TERMINAL") {
                // TERMINAL MODE: The scanner is at the office door. It scans the Attendee's QR code (rawValue is Attendee UID).
                val attendee = repository.getAttendeeByUid(rawValue)
                if (attendee != null) {
                    // Check lateness
                    val status = calculateAttendanceStatus(type)
                    
                    val log = AttendanceLog(
                        uid = attendee.uid,
                        name = attendee.name,
                        role = attendee.role,
                        type = type,
                        status = status
                    )
                    repository.insertLog(log)
                    
                    if (isAutoSyncEnabled() && getAppsScriptUrl().isNotBlank()) {
                        viewModelScope.launch {
                            autoSyncLogs()
                        }
                    }
                    
                    _scanResult.value = ScanResultState(
                        success = true,
                        title = "Absen ${type.lowercase().replaceFirstChar { it.uppercase() }} Berhasil!",
                        message = "Nama: ${attendee.name}\nJabatan/Role: ${attendee.role}\nStatus: $status"
                    )
                } else {
                    _scanResult.value = ScanResultState(
                        success = false,
                        title = "ID Tidak Dikenali",
                        message = "Kode QR (${rawValue}) tidak terdaftar sebagai Anggota/Karyawan."
                    )
                }
            } else {
                // MANDIRI MODE: The scanner is on the Attendee's own phone. They scan a Location/Session QR code (rawValue is Location Code).
                val session = repository.getSessionByCode(rawValue)
                val attendee = _selectedAttendee.value

                if (attendee == null) {
                    _scanResult.value = ScanResultState(
                        success = false,
                        title = "Pilih Profil Dahulu",
                        message = "Pilih profil Anggota terlebih dahulu sebelum melakukan Absensi Mandiri."
                    )
                } else if (session != null) {
                    val status = calculateAttendanceStatus(type)
                    val log = AttendanceLog(
                        uid = attendee.uid,
                        name = attendee.name,
                        role = attendee.role,
                        type = type,
                        status = status,
                        sessionName = session.title
                    )
                    repository.insertLog(log)

                    if (isAutoSyncEnabled() && getAppsScriptUrl().isNotBlank()) {
                        viewModelScope.launch {
                            autoSyncLogs()
                        }
                    }

                    _scanResult.value = ScanResultState(
                        success = true,
                        title = "Absen Mandiri Berhasil!",
                        message = "Profil: ${attendee.name}\nLokasi: ${session.title}\nStatus: $status"
                    )
                } else {
                    _scanResult.value = ScanResultState(
                        success = false,
                        title = "Lokasi Tidak Valid",
                        message = "Kode QR (${rawValue}) tidak cocok dengan lokasi/kelas absensi manapun."
                    )
                }
            }
        }
    }

    private fun calculateAttendanceStatus(type: String): String {
        if (type == "PULANG") return "Tepat Waktu"
        
        // Let's assume late threshold is 08:00 AM
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        return if (hour > 8 || (hour == 8 && minute > 0)) {
            "Terlambat"
        } else {
            "Tepat Waktu"
        }
    }

    fun recordManualAttendance(attendee: Attendee, type: String, status: String, sessionName: String? = null) {
        viewModelScope.launch {
            val log = AttendanceLog(
                uid = attendee.uid,
                name = attendee.name,
                role = attendee.role,
                type = type,
                status = status,
                sessionName = sessionName
            )
            repository.insertLog(log)
            if (isAutoSyncEnabled() && getAppsScriptUrl().isNotBlank()) {
                autoSyncLogs()
            }
        }
    }

    private fun vibrateDevice(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class AttendanceViewModelFactory(
    private val application: Application,
    private val repository: AttendanceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AttendanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AttendanceViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
