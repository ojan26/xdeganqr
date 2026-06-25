package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AttendanceLog
import com.example.data.AttendanceSession
import com.example.data.Attendee
import com.example.ui.AttendanceViewModel
import com.example.ui.ScanResultState
import com.example.utils.QrHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }
    
    // Collecting flows
    val attendees by viewModel.attendees.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val selectedAttendee by viewModel.selectedAttendee.collectAsStateWithLifecycle()

    // Real-time Clock
    var currentClockTime by remember { mutableStateOf("") }
    var currentClockDate by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentClockTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            currentClockDate = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    // Dialog trigger states
    var showAddAttendeeDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAddSessionDialog by remember { mutableStateOf(false) }
    var qrDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) } // pair of (title, encoded_content)
    var qrDialogAttendee by remember { mutableStateOf<Attendee?>(null) }
    var showSelectProfileDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var manualAttendanceAttendee by remember { mutableStateOf<Attendee?>(null) }

    // School Identity states
    val schoolName by viewModel.schoolName.collectAsStateWithLifecycle()
    val schoolAddress by viewModel.schoolAddress.collectAsStateWithLifecycle()
    val schoolLogoPath by viewModel.schoolLogoPath.collectAsStateWithLifecycle()

    var showManualSelectStudentDialog by remember { mutableStateOf(false) }
    var photoCaptureAttendee by remember { mutableStateOf<Attendee?>(null) }

    val attendeePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val attendee = photoCaptureAttendee
        if (bitmap != null && attendee != null) {
            try {
                val photoDir = java.io.File(context.filesDir, "photos")
                if (!photoDir.exists()) photoDir.mkdirs()
                val file = java.io.File(photoDir, "student_${attendee.uid}.jpg")
                val out = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
                out.close()
                viewModel.updateAttendeePhoto(attendee, file.absolutePath)
                Toast.makeText(context, "Foto ${attendee.name} berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        photoCaptureAttendee = null
    }

    val attendeePhotoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            attendeePhotoLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto.", Toast.LENGTH_SHORT).show()
            photoCaptureAttendee = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = "X-Degan QR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Sistem Absensi Pemindaian Real-time",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Absen") },
                    label = { Text("Absen", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Anggota") },
                    label = { Text("Anggota", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Place, contentDescription = "Sesi QR") },
                    label = { Text("Sesi QR", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Riwayat") },
                    label = { Text("Riwayat", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.School, contentDescription = "Sekolah") },
                    label = { Text("Sekolah", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                0 -> ScannerTab(
                    clockTime = currentClockTime,
                    clockDate = currentClockDate,
                    selectedAttendee = selectedAttendee,
                    schoolName = schoolName,
                    schoolAddress = schoolAddress,
                    schoolLogoPath = schoolLogoPath,
                    attendees = attendees,
                    logs = logs,
                    onSelectProfileClick = { showSelectProfileDialog = true },
                    onScanClick = { type, mode ->
                        viewModel.startQrScanner(context, type, mode)
                    },
                    onManualAttendanceClick = {
                        showManualSelectStudentDialog = true
                    }
                )
                1 -> AttendeesTab(
                    attendees = attendees,
                    onAddClick = { showAddAttendeeDialog = true },
                    onImportClick = { showImportDialog = true },
                    onDeleteClick = { viewModel.deleteAttendee(it) },
                    onShowQrClick = { name, uid ->
                        val found = attendees.find { it.uid == uid }
                        if (found != null) {
                            qrDialogAttendee = found
                        } else {
                            qrDialogContent = Pair("QR CODE ANGGOTA\n$name ($uid)", uid)
                        }
                    },
                    onManualAttendanceClick = { attendee ->
                        manualAttendanceAttendee = attendee
                    },
                    onPhotoCaptureClick = { attendee ->
                        photoCaptureAttendee = attendee
                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        )
                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            attendeePhotoLauncher.launch(null)
                        } else {
                            attendeePhotoPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    onPrintQrCardsClick = {
                        QrHelper.printStudentQrCards(context, attendees, schoolName, schoolAddress, schoolLogoPath)
                    }
                )
                2 -> SessionsTab(
                    sessions = sessions,
                    onAddClick = { showAddSessionDialog = true },
                    onDeleteClick = { viewModel.deleteSession(it) },
                    onShowQrClick = { title, code ->
                        qrDialogContent = Pair("QR CODE LOKASI\n$title ($code)", code)
                    }
                )
                3 -> HistoryTab(
                    logs = logs,
                    attendees = attendees,
                    viewModel = viewModel,
                    onClearAll = { viewModel.clearAllLogs() }
                )
                4 -> SchoolTab(
                    schoolName = schoolName,
                    schoolAddress = schoolAddress,
                    schoolLogoPath = schoolLogoPath,
                    onSaveClick = { name, address, logoPath ->
                        viewModel.updateSchoolInfo(name, address, logoPath)
                    }
                )
            }

            // QR Overlay Result Notification
            scanResult?.let { result ->
                ScanResultOverlay(
                    result = result,
                    onDismiss = { viewModel.clearScanResult() }
                )
            }
        }
    }

    // --- DIALOGS ---

    // 1. Add Attendee Dialog
    if (showAddAttendeeDialog) {
        AddAttendeeDialog(
            onDismiss = { showAddAttendeeDialog = false },
            onConfirm = { name, role, customUid ->
                viewModel.addAttendee(name, role, customUid)
                showAddAttendeeDialog = false
            },
            onImportClick = {
                showAddAttendeeDialog = false
                showImportDialog = true
            }
        )
    }

    // 1b. Import Dialog
    if (showImportDialog) {
        ImportDialog(
            viewModel = viewModel,
            onDismiss = { showImportDialog = false }
        )
    }

    // 2. Add Session Dialog
    if (showAddSessionDialog) {
        AddSessionDialog(
            onDismiss = { showAddSessionDialog = false },
            onConfirm = { title ->
                viewModel.addSession(title)
                showAddSessionDialog = false
            }
        )
    }

    // 3. QR Displayer Dialog
    qrDialogContent?.let { (title, contentText) ->
        QrDisplayerDialog(
            title = title,
            content = contentText,
            onDismiss = { qrDialogContent = null }
        )
    }

    // 3b. Student ID Card Dialog
    qrDialogAttendee?.let { attendee ->
        StudentCardDialog(
            attendee = attendee,
            viewModel = viewModel,
            onDismiss = { qrDialogAttendee = null }
        )
    }

    // Manual Student Selection Dialog (for ScannerTab "Absen Manual" button)
    if (showManualSelectStudentDialog) {
        ManualSelectStudentDialog(
            attendees = attendees,
            onDismiss = { showManualSelectStudentDialog = false },
            onSelect = { student ->
                manualAttendanceAttendee = student
                showManualSelectStudentDialog = false
            }
        )
    }

    // 4. Profile Selector Dialog (for Self Mode)
    if (showSelectProfileDialog) {
        ProfileSelectorDialog(
            attendees = attendees,
            onDismiss = { showSelectProfileDialog = false },
            onSelect = {
                viewModel.selectAttendee(it)
                showSelectProfileDialog = false
            }
        )
    }

    // 5. Sync Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // 6. Manual Attendance Dialog (Izin / Sakit / Hadir)
    manualAttendanceAttendee?.let { attendee ->
        ManualAttendanceDialog(
            attendee = attendee,
            onDismiss = { manualAttendanceAttendee = null },
            onConfirm = { type, status ->
                viewModel.recordManualAttendance(attendee, type, status)
                manualAttendanceAttendee = null
            }
        )
    }
}

// =====================================
// TAB 1: SCANNER TAB
// =====================================
@Composable
fun ScannerTab(
    clockTime: String,
    clockDate: String,
    selectedAttendee: Attendee?,
    schoolName: String,
    schoolAddress: String,
    schoolLogoPath: String,
    attendees: List<Attendee>,
    logs: List<AttendanceLog>,
    onSelectProfileClick: () -> Unit,
    onScanClick: (type: String, mode: String) -> Unit,
    onManualAttendanceClick: () -> Unit
) {
    val schoolLogoBitmap = remember(schoolLogoPath) {
        if (!schoolLogoPath.isNullOrEmpty()) {
            try {
                android.graphics.BitmapFactory.decodeFile(schoolLogoPath)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Dynamic attendance type calculation: MASUK or PULANG
    val students = remember(attendees) {
        attendees.filter { it.role.equals("siswa", ignoreCase = true) }
    }
    val targetAttendees = remember(students, attendees) {
        if (students.isNotEmpty()) students else attendees
    }

    val todaySdf = remember { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()) }
    val todayStr = remember(clockDate) { todaySdf.format(java.util.Date()) }

    val presentUidsToday = remember(logs, clockDate) {
        logs.filter { log ->
            log.type == "MASUK" && todaySdf.format(java.util.Date(log.timestamp)) == todayStr
        }.map { it.uid }.toSet()
    }

    val studentCount = targetAttendees.size
    val presentCount = targetAttendees.count { it.uid in presentUidsToday }

    // If all target attendees are present today, toggle type to PULANG. Otherwise, MASUK.
    val attendanceType = if (studentCount > 0 && presentCount == studentCount) "PULANG" else "MASUK"
    val scanMode = "TERMINAL"

    val buttonColor = if (attendanceType == "MASUK") Color(0xFF10B981) else Color(0xFFEF4444)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Digital Clock Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = clockTime.ifEmpty { "00:00:00" },
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = clockDate.ifEmpty { "Memuat Tanggal..." },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // School Identity Header Card (Dihalaman absen tampilkan logo sekolah dan nama sekolah)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // School Logo / Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (schoolLogoBitmap != null) {
                            Image(
                                bitmap = schoolLogoBitmap.asImageBitmap(),
                                contentDescription = "Logo Sekolah",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Logo Sekolah",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // School Details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = schoolName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = schoolAddress,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Dynamic Progress and Status Indicator Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (attendanceType == "MASUK") {
                        Color(0xFF10B981).copy(alpha = 0.08f)
                    } else {
                        Color(0xFFEF4444).copy(alpha = 0.08f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (attendanceType == "MASUK") Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (attendanceType == "MASUK") Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (attendanceType == "MASUK") "STATUS: ABSEN MASUK" else "STATUS: ABSEN PULANG",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = if (attendanceType == "MASUK") Color(0xFF047857) else Color(0xFFB91C1C)
                        )
                    }

                    // Progress Bar
                    val progress = if (studentCount > 0) presentCount.toFloat() / studentCount else 0f
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (attendanceType == "MASUK") Color(0xFF10B981) else Color(0xFFEF4444),
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )

                    Text(
                        text = "$presentCount dari $studentCount Siswa Telah Absen Masuk",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (studentCount > 0 && presentCount == studentCount) {
                        Text(
                            text = "Semua siswa sudah absen masuk! Sistem beralih ke Absen Pulang otomatis.",
                            fontSize = 11.sp,
                            color = Color(0xFFB91C1C),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = "Sistem beralih ke Absen Pulang otomatis setelah semua siswa absen masuk.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Scanner Launch Button
        item {
            Button(
                onClick = { onScanClick(attendanceType, scanMode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("scan_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Scan",
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (attendanceType == "MASUK") "MULAI ABSEN MASUK (QR)" else "MULAI ABSEN PULANG (QR)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Manual Attendance Button
        item {
            OutlinedButton(
                onClick = onManualAttendanceClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Absen Manual",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ABSEN MANUAL (TANPA QR)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// =====================================
// TAB 2: ATTENDEES TAB (ANGGOTA)
// =====================================
@Composable
fun AttendeesTab(
    attendees: List<Attendee>,
    onAddClick: () -> Unit,
    onImportClick: () -> Unit,
    onDeleteClick: (Int) -> Unit,
    onShowQrClick: (name: String, uid: String) -> Unit,
    onManualAttendanceClick: (Attendee) -> Unit,
    onPhotoCaptureClick: (Attendee) -> Unit,
    onPrintQrCardsClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (attendees.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyStateView(
                    icon = Icons.Default.Person,
                    title = "Belum Ada Anggota",
                    description = "Silakan tambah anggota secara manual atau gunakan tombol Impor di bawah untuk memasukkan daftar siswa dari Spreadsheet/Excel."
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Impor Data Siswa", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Anggota (${attendees.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onPrintQrCardsClick,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Cetak QR", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cetak QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = onImportClick,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Import", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Impor", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                items(attendees, key = { it.id }) { attendee ->
                    val studentBitmap = remember(attendee.photoPath) {
                        if (!attendee.photoPath.isNullOrEmpty()) {
                            try {
                                android.graphics.BitmapFactory.decodeFile(attendee.photoPath)
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Photo / Initials Avatar
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (studentBitmap != null) {
                                        Image(
                                            bitmap = studentBitmap.asImageBitmap(),
                                            contentDescription = "Foto ${attendee.name}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = attendee.name.take(2).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = attendee.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${attendee.role}  •  ID: ${attendee.uid}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Manual Attendance Button (for Siswa)
                                if (attendee.role.lowercase() == "siswa") {
                                    IconButton(
                                        onClick = { onManualAttendanceClick(attendee) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Absen Manual",
                                            tint = Color(0xFF16A34A)
                                        )
                                    }
                                }

                                // Take Photo Button (Direct from Camera)
                                IconButton(
                                    onClick = { onPhotoCaptureClick(attendee) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Ambil Foto",
                                        tint = Color(0xFFE28743)
                                    )
                                }

                                // Show QR Button
                                IconButton(
                                    onClick = { onShowQrClick(attendee.name, attendee.uid) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = "Generate QR",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { onDeleteClick(attendee.id) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Member Floating Action Button
        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_attendee_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah Anggota")
        }
    }
}

// =====================================
// TAB 3: SESSIONS TAB (LOKASI QR)
// =====================================
@Composable
fun SessionsTab(
    sessions: List<AttendanceSession>,
    onAddClick: () -> Unit,
    onDeleteClick: (Int) -> Unit,
    onShowQrClick: (title: String, code: String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (sessions.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Place,
                title = "Belum Ada Sesi / Lokasi",
                description = "Buat lokasi absensi baru (seperti Ruang Kantor Utama, Kelas X, atau Area Rapat). QR code akan digenerate agar anggota bisa memindai dengan HP mereka sendiri."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Daftar Lokasi Absensi QR (${sessions.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(sessions, key = { it.id }) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = "Sesi",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Column {
                                    Text(
                                        text = session.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Kode Keamanan: ${session.code}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Show QR Button
                                IconButton(
                                    onClick = { onShowQrClick(session.title, session.code) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = "Show QR",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { onDeleteClick(session.id) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Session Floating Action Button
        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_session_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah Lokasi")
        }
    }
}

// =====================================
// TAB 4: HISTORY TAB (RIWAYAT LOG)
// =====================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTab(
    logs: List<AttendanceLog>,
    attendees: List<Attendee>,
    viewModel: AttendanceViewModel,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var showConfirmClearDialog by remember { mutableStateOf(false) }
    
    // States for Google Sheets sync
    var appsScriptUrl by remember(viewModel.getAppsScriptUrl()) { mutableStateOf(viewModel.getAppsScriptUrl()) }
    var isSyncing by remember { mutableStateOf(false) }
    var showSetupTutorial by remember { mutableStateOf(false) }
    
    // Dynamic calculation of who's Present and who's Absent today
    val todayString = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val registeredStudents = remember(attendees) { attendees.filter { it.role.lowercase() == "siswa" } }
    
    val todayMasukLogs = remember(logs, todayString) {
        logs.filter { log ->
            val logDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(log.timestamp))
            logDateString == todayString && log.type == "MASUK"
        }
    }
    
    val studentLogsMap = remember(todayMasukLogs) { todayMasukLogs.associateBy { it.uid } }
    
    val presentStudents = remember(registeredStudents, studentLogsMap) {
        registeredStudents.filter { s ->
            val status = studentLogsMap[s.uid]?.status
            status != null && status != "Ijin" && status != "Sakit"
        }
    }
    
    val ijinStudents = remember(registeredStudents, studentLogsMap) {
        registeredStudents.filter { s -> studentLogsMap[s.uid]?.status == "Ijin" }
    }
    
    val sakitStudents = remember(registeredStudents, studentLogsMap) {
        registeredStudents.filter { s -> studentLogsMap[s.uid]?.status == "Sakit" }
    }
    
    val absentStudents = remember(registeredStudents, studentLogsMap) {
        registeredStudents.filter { s -> !studentLogsMap.containsKey(s.uid) }
    }

    // Google Apps Script Code Template
    val appsScriptTemplate = """
function doGet(e) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  var data = sheet.getDataRange().getValues();
  var result = [];
  for (var i = 1; i < data.length; i++) {
    result.push({
      uid: String(data[i][0] || ""),
      name: String(data[i][1] || ""),
      role: String(data[i][2] || "Siswa")
    });
  }
  return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  var params = JSON.parse(e.postData.contents);
  if (Array.isArray(params)) {
    for (var i = 0; i < params.length; i++) {
      sheet.appendRow([
        params[i].timestamp,
        params[i].uid,
        params[i].name,
        params[i].role,
        params[i].type,
        params[i].status,
        params[i].sessionName || ""
      ]);
    }
  } else {
    sheet.appendRow([
      params.timestamp,
      params.uid,
      params.name,
      params.role,
      params.type,
      params.status,
      params.sessionName || ""
    ]);
  }
  return ContentService.createTextOutput("SUCCESS").setMimeType(ContentService.MimeType.TEXT);
}
""".trim()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CARD 1: GOOGLE SHEETS SYNC
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Kirim ke Google Spreadsheet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        TextButton(
                            onClick = { showSetupTutorial = !showSetupTutorial },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = if (showSetupTutorial) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(if (showSetupTutorial) "Sembunyikan" else "Bantuan", fontSize = 12.sp)
                        }
                    }

                    // Expandable Tutorial Block
                    if (showSetupTutorial) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Cara Setup Google Spreadsheet Sync:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "1. Buat Google Spreadsheet baru.\n" +
                                            "2. Klik Ekstensi -> Apps Script.\n" +
                                            "3. Hapus kode bawaan, lalu paste kode template script.\n" +
                                            "4. Klik Terapkan -> Penerapan Baru -> Aplikasi Web.\n" +
                                            "5. Siapa saja yang memiliki akses: Pilih 'Siapa Saja' / 'Anyone', klik Terapkan.\n" +
                                            "6. Salin URL Aplikasi Web yang dihasilkan dan tempel di bawah.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                                
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("AppsScriptTemplate", appsScriptTemplate)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Script disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salin Kode Apps Script", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = appsScriptUrl,
                        onValueChange = { appsScriptUrl = it },
                        label = { Text("URL Web App Google Apps Script") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )

                    Button(
                        onClick = {
                            if (appsScriptUrl.isBlank()) {
                                Toast.makeText(context, "Tolong masukkan URL Apps Script terlebih dahulu.", Toast.LENGTH_SHORT).show()
                            } else {
                                isSyncing = true
                                viewModel.saveAppsScriptUrl(appsScriptUrl)
                                viewModel.syncLogsToGoogleSheets(appsScriptUrl.trim()) { success, msg ->
                                    isSyncing = false
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mengirim data...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kirim Data Kehadiran Ke Google Sheets", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // CARD 2: WHATSAPP NOTIFICATION FOR PARENT GROUP (WALI MURID)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color(0xFF128C7E) // WhatsApp signature color
                        )
                        Text(
                            text = "Laporan WhatsApp Wali Murid",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Bagikan rekapitulasi presensi siswa hari ini ke WhatsApp Group Wali Murid secara instan.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Stats indicators Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Terdaftar", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text("${registeredStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Hadir", fontSize = 9.sp, color = Color(0xFF047857), maxLines = 1)
                                Text("${presentStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    // Stats indicators Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Izin", fontSize = 9.sp, color = Color(0xFF1D4ED8), maxLines = 1)
                                Text("${ijinStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Sakit", fontSize = 9.sp, color = Color(0xFFD97706), maxLines = 1)
                                Text("${sakitStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Alpa/Absen", fontSize = 9.sp, color = Color(0xFFBE123C), maxLines = 1)
                                Text("${absentStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (registeredStudents.isEmpty()) {
                                Toast.makeText(context, "Daftarkan siswa dengan role 'Siswa' terlebih dahulu.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val reportText = buildString {
                                append("*LAPORAN KEHADIRAN SISWA*\n")
                                append("Hari/Tanggal: ${SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())}\n")
                                append("=========================\n\n")
                                
                                append("✅ *HADIR:* (${presentStudents.size} Siswa)\n")
                                if (presentStudents.isEmpty()) {
                                    append("- Belum ada siswa masuk\n")
                                } else {
                                    presentStudents.forEachIndexed { index, student ->
                                        val log = todayMasukLogs.find { it.uid == student.uid }
                                        val timeStr = log?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestamp)) } ?: "--:--"
                                        val statusStr = log?.status ?: "Hadir"
                                        append("${index + 1}. ${student.name} ($timeStr - $statusStr)\n")
                                    }
                                }
                                append("\n")

                                append("📬 *IZIN:* (${ijinStudents.size} Siswa)\n")
                                if (ijinStudents.isEmpty()) {
                                    append("- Nihil\n")
                                } else {
                                    ijinStudents.forEachIndexed { index, student ->
                                        append("${index + 1}. ${student.name}\n")
                                    }
                                }
                                append("\n")

                                append("🤒 *SAKIT:* (${sakitStudents.size} Siswa)\n")
                                if (sakitStudents.isEmpty()) {
                                    append("- Nihil\n")
                                } else {
                                    sakitStudents.forEachIndexed { index, student ->
                                        append("${index + 1}. ${student.name}\n")
                                    }
                                }
                                append("\n")
                                
                                append("❌ *TANPA KETERANGAN / ALPA:* (${absentStudents.size} Siswa)\n")
                                if (absentStudents.isEmpty()) {
                                    append("- Nihil (Semua hadir / berketerangan)\n")
                                } else {
                                    absentStudents.forEachIndexed { index, student ->
                                        append("${index + 1}. ${student.name}\n")
                                    }
                                }
                                append("\n_Laporan presensi otomatis via X-Degan QR_")
                            }

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reportText)
                            }
                            shareIntent.setPackage("com.whatsapp")

                            try {
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                // Fallback to normal chooser if WA is not installed
                                val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, reportText)
                                }, "Kirim Laporan Wali Murid")
                                context.startActivity(chooser)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366) // WA brand color
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bagikan Laporan WA Wali Murid", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }

        // --- SUBTITLE LOGS ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Riwayat Log Absensi (${logs.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = { showConfirmClearDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus Semua", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("Log Riwayat Masih Kosong", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Absensi scan yang sukses hari ini akan muncul di sini.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(logs) { log ->
                val formattedTime = remember(log.timestamp) {
                    SimpleDateFormat("HH:mm  •  dd MMM", Locale("id", "ID")).format(Date(log.timestamp))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = log.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                // Masuk / Pulang badge
                                val typeColor = if (log.type == "MASUK") Color(0xFF0369A1) else Color(0xFF64748B)
                                val typeBg = if (log.type == "MASUK") Color(0xFFE0F2FE) else Color(0xFFF1F5F9)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(typeBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = log.type,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = typeColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Role: ${log.role}  •  $formattedTime",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            log.sessionName?.let { session ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Lokasi: $session",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Attendance Status Badge
                        val statusBgColor = if (log.status == "Tepat Waktu") Color(0xFFECFDF5) else Color(0xFFFFF1F2)
                        val statusTextColor = if (log.status == "Tepat Waktu") Color(0xFF047857) else Color(0xFFBE123C)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusBgColor)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = log.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusTextColor
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus seluruh riwayat log absensi? Tindakan ini permanen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showConfirmClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

// =====================================
// HELPER VIEW COMPONENTS & DIALOGS
// =====================================

@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun ScanResultOverlay(
    result: ScanResultState,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large Status Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (result.success) Color(0xFFECFDF5) else Color(0xFFFFF1F2)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (result.success) Color(0xFF059669) else Color(0xFFE11D48),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = result.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAttendeeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, role: String, customUid: String?) -> Unit,
    onImportClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Staf") }
    var customUid by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrasi Anggota Baru", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (isError) isError = false
                    },
                    label = { Text("Nama Lengkap") },
                    placeholder = { Text("Misal: Ahmad Fauzi") },
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (isError) {
                    Text("Nama tidak boleh kosong", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                // Simple custom segmented selection for Role (Staf, Siswa, Tamu)
                Text("Role/Kategori:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Staf", "Siswa", "Tamu").forEach { option ->
                        val selected = role == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { role = option }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customUid,
                    onValueChange = { customUid = it },
                    label = { Text("Nomor ID / NIP / NIS (Opsional)") },
                    placeholder = { Text("Dibuat otomatis jika dikosongi") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Beautiful, eye-catching card for importing via Excel & Spreadsheet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onImportClick() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Import",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Impor Banyak Siswa Sekaligus",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Impor massal dari file Excel, CSV, atau Google Spreadsheet",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Buka",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(name, role, customUid.ifBlank { null })
                    }
                }
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Lokasi / Kelas QR", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Buat titik penanda QR Code untuk dipindai oleh peserta absensi mandiri.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (isError) isError = false
                    },
                    label = { Text("Nama Lokasi atau Kelas") },
                    placeholder = { Text("Misal: Aula Kantor Utama") },
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (isError) {
                    Text("Nama lokasi tidak boleh kosong", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(title)
                    }
                }
            ) {
                Text("Buat QR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun QrDisplayerDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(12.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                // Render QR code Bitmap reactively inside a white protective box for scan contrast
                val qrBitmap = remember(content) { QrHelper.generateQrCode(content, 400) }
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Gunakan QR Code ini saat pemindaian absensi.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Kode Raw: $content",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProfileSelectorDialog(
    attendees: List<Attendee>,
    onDismiss: () -> Unit,
    onSelect: (Attendee) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Pilih Profil Absen Mandiri",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (attendees.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Silakan daftarkan anggota baru di tab 'Anggota' terlebih dahulu.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(attendees) { attendee ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(attendee) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = attendee.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${attendee.role}  •  ID: ${attendee.uid}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedMethod by remember { mutableStateOf(0) } // 0: File CSV, 1: Google Link, 2: Apps Script
    
    var googleSheetUrl by remember { mutableStateOf("") }
    var appsScriptUrl by remember(viewModel.getAppsScriptUrl()) { mutableStateOf(viewModel.getAppsScriptUrl()) }
    
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(true) }

    // File Picker for local CSV files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            statusMessage = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    viewModel.importFromCsvText(content) { success, count ->
                        isLoading = false
                        if (success) {
                            isSuccessMessage = true
                            statusMessage = "Berhasil mengimpor $count siswa dari file CSV!"
                        } else {
                            isSuccessMessage = false
                            statusMessage = "Gagal mengimpor file. Pastikan format CSV valid."
                        }
                    }
                } else {
                    isLoading = false
                    isSuccessMessage = false
                    statusMessage = "File tidak dapat dibaca atau kosong."
                }
            } catch (e: Exception) {
                isLoading = false
                isSuccessMessage = false
                statusMessage = "Error: ${e.localizedMessage}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Impor Data Siswa", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Method Selection Tab/Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Excel / CSV", "Google Sheets", "Apps Script").forEachIndexed { index, title ->
                        val selected = selectedMethod == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { 
                                    selectedMethod = index 
                                    statusMessage = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Render Content based on selected method
                when (selectedMethod) {
                    0 -> { // File CSV Local
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Langkah Impor dari Excel/CSV:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "1. Buat daftar siswa di Excel atau Google Sheets dengan template kolom berikut.\n" +
                                        "2. Pastikan urutan dan nama kolom sesuai (No, Nama Siswa, NISN, Kelas).\n" +
                                        "3. Simpan/Ekspor file sebagai format .CSV (Comma Separated Values).\n" +
                                        "4. Klik tombol di bawah dan pilih file CSV tersebut.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            Text(
                                text = "Struktur Kolom Template (MANDATORI):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("No", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Nama Siswa", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("NISN", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Kelas", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("1", modifier = Modifier.weight(0.5f), fontSize = 10.sp)
                                        Text("Ahmad Dani", modifier = Modifier.weight(1.5f), fontSize = 10.sp)
                                        Text("201987654", modifier = Modifier.weight(1.2f), fontSize = 10.sp)
                                        Text("XI-RPL", modifier = Modifier.weight(1f), fontSize = 10.sp)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("2", modifier = Modifier.weight(0.5f), fontSize = 10.sp)
                                        Text("Siti Aminah", modifier = Modifier.weight(1.5f), fontSize = 10.sp)
                                        Text("201954321", modifier = Modifier.weight(1.2f), fontSize = 10.sp)
                                        Text("XI-TKJ", modifier = Modifier.weight(1f), fontSize = 10.sp)
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clipData = android.content.ClipData.newPlainText(
                                            "Template CSV Siswa",
                                            "No,Nama Siswa,NISN,Kelas\n1,Ahmad Dani,201987654,XI-RPL\n2,Siti Aminah,201954321,XI-TKJ\n"
                                        )
                                        clipboardManager.setPrimaryClip(clipData)
                                        Toast.makeText(context, "Template CSV berhasil disalin!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Salin Contoh CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { filePickerLauncher.launch("text/comma-separated-values|text/plain|application/octet-stream|*/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pilih File .CSV dari HP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    1 -> { // Published Google Sheet CSV
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Impor Google Spreadsheet via Link Publik:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "1. Buka Google Spreadsheet berisi daftar siswa Anda.\n" +
                                        "2. Klik File -> Bagikan -> Publikasikan ke Web.\n" +
                                        "3. Pilih 'Seluruh Dokumen' / Lembar tertentu, ubah format ke 'Nilai yang dipisahkan koma (.csv)'.\n" +
                                        "4. Klik 'Publikasikan', salin link yang diberikan dan tempel di bawah ini.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            OutlinedTextField(
                                value = googleSheetUrl,
                                onValueChange = { googleSheetUrl = it },
                                label = { Text("Link Google Spreadsheet Publik (.csv)") },
                                placeholder = { Text("https://docs.google.com/spreadsheets/d/.../pub?output=csv") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (googleSheetUrl.isBlank()) {
                                        isSuccessMessage = false
                                        statusMessage = "Tolong masukkan link URL terlebih dahulu."
                                    } else {
                                        isLoading = true
                                        statusMessage = null
                                        viewModel.importFromGoogleSheetsCsv(googleSheetUrl.trim()) { success, count, msg ->
                                            isLoading = false
                                            isSuccessMessage = success
                                            statusMessage = msg
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isLoading
                            ) {
                                Text("Mulai Impor Link", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    2 -> { // Apps Script Integration
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Impor Melalui Apps Script (Sinkronisasi Dua Arah):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Tarik daftar siswa langsung dari Google Spreadsheet menggunakan Apps Script Web App URL.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            OutlinedTextField(
                                value = appsScriptUrl,
                                onValueChange = { appsScriptUrl = it },
                                label = { Text("URL Web App Google Apps Script") },
                                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (appsScriptUrl.isBlank()) {
                                        isSuccessMessage = false
                                        statusMessage = "Tolong masukkan URL Web App terlebih dahulu."
                                    } else {
                                        isLoading = true
                                        statusMessage = null
                                        viewModel.saveAppsScriptUrl(appsScriptUrl)
                                        viewModel.importFromGoogleAppsScript(appsScriptUrl.trim()) { success, count, msg ->
                                            isLoading = false
                                            isSuccessMessage = success
                                            statusMessage = msg
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isLoading
                            ) {
                                Text("Tarik Data Siswa", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Status Message display
                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Memproses data...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                statusMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccessMessage) Color(0xFFECFDF5) else Color(0xFFFFF1F2)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = if (isSuccessMessage) Color(0xFF047857) else Color(0xFFBE123C),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Selesai")
            }
        }
    )
}

@Composable
fun StudentCardDialog(
    attendee: Attendee,
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val photoDir = java.io.File(context.filesDir, "photos")
                if (!photoDir.exists()) photoDir.mkdirs()
                val file = java.io.File(photoDir, "student_${attendee.uid}.jpg")
                val out = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
                out.close()
                viewModel.updateAttendeePhoto(attendee, file.absolutePath)
                Toast.makeText(context, "Foto berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Indonesian School Student Card Header (Dark Blue)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
                                )
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "KARTU TANDA ANGGOTA / SISWA",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "SISTEM ABSENSI ONLINE - ABSENQR",
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Card Body
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Profile Photo / Avatar
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.CAMERA
                                        )
                                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            cameraLauncher.launch(null)
                                        } else {
                                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val studentBitmap = remember(attendee.photoPath) {
                                    if (!attendee.photoPath.isNullOrEmpty()) {
                                        try {
                                            android.graphics.BitmapFactory.decodeFile(attendee.photoPath)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else {
                                        null
                                    }
                                }

                                if (studentBitmap != null) {
                                    Image(
                                        bitmap = studentBitmap.asImageBitmap(),
                                        contentDescription = "Foto Siswa",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Photo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                // Mini Camera Overlay Badge
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Ambil Foto",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (attendee.role.lowercase() == "siswa") Color(0xFFE0F2FE) else Color(0xFFF1F5F9)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val roleText = attendee.role.uppercase()
                                Text(
                                    text = roleText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (attendee.role.lowercase() == "siswa") Color(0xFF0369A1) else Color(0xFF475569)
                                )
                            }
                        }

                        // Middle Info Details
                        Column(
                            modifier = Modifier.weight(1.5f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "NAMA LENGKAP",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = attendee.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "NOMOR IDENTITAS (UID)",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = attendee.uid,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right QR Code
                        val qrBitmap = remember(attendee.uid) { QrHelper.generateQrCode(attendee.uid, 250) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = "PINDAI SAYA",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Card Footer Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE2E8F0))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Tunjukkan kartu ini kepada Guru untuk memindai presensi.",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Action Buttons below ID Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        )
                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null)
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE28743))
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ambil Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("ID_Siswa", attendee.uid)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "ID Siswa disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salin ID", fontSize = 11.sp)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Tutup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var appsScriptUrl by remember { mutableStateOf(viewModel.getAppsScriptUrl()) }
    var isAutoSyncEnabled by remember { mutableStateOf(viewModel.isAutoSyncEnabled()) }
    var isTesting by remember { mutableStateOf(false) }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pengaturan Sinkronisasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Google Apps Script Web App URL:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = appsScriptUrl,
                        onValueChange = { appsScriptUrl = it },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                    Text(
                        text = "Web App URL ini digunakan untuk sinkronisasi dua arah otomatis dengan Google Sheets.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Auto Sync Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1.5f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Sinkronisasi Otomatis",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Kirim otomatis data absensi secara real-time saat HP terkoneksi internet.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isAutoSyncEnabled,
                        onCheckedChange = { isAutoSyncEnabled = it }
                    )
                }

                // Test Connection Status Button & Feedback
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (appsScriptUrl.isBlank()) {
                                isTestSuccess = false
                                testStatusMessage = "Masukkan URL terlebih dahulu untuk menguji."
                            } else {
                                isTesting = true
                                testStatusMessage = null
                                viewModel.importFromGoogleAppsScript(appsScriptUrl.trim()) { success, count, msg ->
                                    isTesting = false
                                    isTestSuccess = success
                                    testStatusMessage = if (success) {
                                        "Koneksi Berhasil! Ditemukan $count data siswa pada Google Sheets."
                                    } else {
                                        "Koneksi Gagal: $msg"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Menguji...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Uji Koneksi Web App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    testStatusMessage?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isTestSuccess) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveAppsScriptUrl(appsScriptUrl.trim())
                    viewModel.setAutoSyncEnabled(isAutoSyncEnabled)
                    Toast.makeText(context, "Pengaturan sinkronisasi disimpan!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Simpan & Tutup", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAttendanceDialog(
    attendee: Attendee,
    onDismiss: () -> Unit,
    onConfirm: (type: String, status: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Absen Manual",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Siswa: ${attendee.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pilih keterangan atau status kehadiran untuk siswa ini tanpa memindai QR Code:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Sakit Option (Amber/Yellow)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm("MASUK", "Sakit") },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBEB)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Sakit",
                                tint = Color(0xFFD97706)
                            )
                        }
                        Column {
                            Text(
                                text = "Sakit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFB45309)
                            )
                            Text(
                                text = "Tandai siswa berhalangan karena Sakit",
                                fontSize = 11.sp,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                }

                // Izin Option (Blue)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm("MASUK", "Ijin") },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEFF6FF)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDBEAFE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Izin",
                                tint = Color(0xFF1D4ED8)
                            )
                        }
                        Column {
                            Text(
                                text = "Izin / Ijin",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1D4ED8)
                            )
                            Text(
                                text = "Tandai siswa berhalangan dengan Izin resmi",
                                fontSize = 11.sp,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))

                // Alternative manual Hadir/Masuk (Green)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm("MASUK", "Tepat Waktu") },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFECFDF5)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD1FAE5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Hadir",
                                tint = Color(0xFF059669)
                            )
                        }
                        Column {
                            Text(
                                text = "Hadir Manual (Masuk)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF047857)
                            )
                            Text(
                                text = "Absen masuk manual tanpa pindai QR",
                                fontSize = 11.sp,
                                color = Color(0xFF059669)
                            )
                        }
                    }
                }

                // Alternative manual Pulang (Slate/Grey)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm("PULANG", "Tepat Waktu") },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF1F5F9)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Pulang",
                                tint = Color(0xFF475569)
                            )
                        }
                        Column {
                            Text(
                                text = "Pulang Manual",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "Absen pulang manual tanpa pindai QR",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// =====================================
// TAB 5: SCHOOL CONFIGURATION TAB (SEKOLAH)
// =====================================
@Composable
fun SchoolTab(
    schoolName: String,
    schoolAddress: String,
    schoolLogoPath: String,
    onSaveClick: (name: String, address: String, logoPath: String) -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember(schoolName) { mutableStateOf(schoolName) }
    var addressInput by remember(schoolAddress) { mutableStateOf(schoolAddress) }
    var logoPathInput by remember(schoolLogoPath) { mutableStateOf(schoolLogoPath) }

    // Setup Gallery Picker for Logo
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                // Copy selected URI content to a local private file
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val logoDir = java.io.File(context.filesDir, "logos")
                    if (!logoDir.exists()) logoDir.mkdirs()
                    val destFile = java.io.File(logoDir, "school_logo_${System.currentTimeMillis()}.jpg")
                    val outputStream = java.io.FileOutputStream(destFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    logoPathInput = destFile.absolutePath
                    Toast.makeText(context, "Logo berhasil dipilih!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat logo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Setup Camera Capture for Logo
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val logoDir = java.io.File(context.filesDir, "logos")
                if (!logoDir.exists()) logoDir.mkdirs()
                val destFile = java.io.File(logoDir, "school_logo_${System.currentTimeMillis()}.jpg")
                val out = java.io.FileOutputStream(destFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
                out.close()
                logoPathInput = destFile.absolutePath
                Toast.makeText(context, "Foto logo berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengambil foto logo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto logo.", Toast.LENGTH_SHORT).show()
        }
    }

    val schoolLogoBitmap = remember(logoPathInput) {
        if (!logoPathInput.isNullOrEmpty()) {
            try {
                android.graphics.BitmapFactory.decodeFile(logoPathInput)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // School Preview Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Pratinjau Identitas Sekolah",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Logo circle with fallback icon
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (schoolLogoBitmap != null) {
                            Image(
                                bitmap = schoolLogoBitmap.asImageBitmap(),
                                contentDescription = "Logo Sekolah",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Logo Sekolah",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }

                    Text(
                        text = nameInput.ifBlank { "Nama Sekolah Belum Diisi" },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = addressInput.ifBlank { "Alamat Sekolah Belum Diisi" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Configuration Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Edit Identitas Sekolah",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nama Sekolah") },
                        placeholder = { Text("Contoh: SMA Negeri 1 Jakarta") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text("Alamat Sekolah") },
                        placeholder = { Text("Contoh: Jl. Budi Utomo No. 7, Jakarta Pusat") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "Ubah Logo Sekolah",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Gallery picker button
                        OutlinedButton(
                            onClick = { galleryPickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dari Galeri", fontSize = 11.sp)
                        }

                        // Camera capture button
                        OutlinedButton(
                            onClick = {
                                val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CAMERA
                                )
                                if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ambil Foto", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Save Button
        item {
            Button(
                onClick = {
                    onSaveClick(nameInput.trim(), addressInput.trim(), logoPathInput)
                    Toast.makeText(context, "Identitas sekolah berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = "Simpan", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SIMPAN IDENTITAS SEKOLAH",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// =====================================
// MANUAL ATTENDANCE STUDENT SELECTOR
// =====================================
@Composable
fun ManualSelectStudentDialog(
    attendees: List<Attendee>,
    onDismiss: () -> Unit,
    onSelect: (Attendee) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter to only display students (role == "siswa") who match the search query
    val filteredStudents = remember(attendees, searchQuery) {
        attendees.filter { attendee ->
            attendee.role.lowercase() == "siswa" &&
            (attendee.name.contains(searchQuery, ignoreCase = true) || 
             attendee.uid.contains(searchQuery, ignoreCase = true))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pilih Siswa",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Pilih siswa untuk absensi manual",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama siswa atau NISN...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // List of filtered students
                if (filteredStudents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Siswa tidak ditemukan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredStudents, key = { it.id }) { student ->
                            val studentBitmap = remember(student.photoPath) {
                                if (!student.photoPath.isNullOrEmpty()) {
                                    try {
                                        android.graphics.BitmapFactory.decodeFile(student.photoPath)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(student) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Profile photo or initials
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (studentBitmap != null) {
                                            Image(
                                                bitmap = studentBitmap.asImageBitmap(),
                                                contentDescription = student.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = student.name.take(2).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = student.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "ID: ${student.uid}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Batal")
                }
            }
        }
    }
}
