package com.example.data

import kotlinx.coroutines.flow.Flow

class AttendanceRepository(
    private val attendeeDao: AttendeeDao,
    private val attendanceLogDao: AttendanceLogDao,
    private val attendanceSessionDao: AttendanceSessionDao
) {
    val allAttendees: Flow<List<Attendee>> = attendeeDao.getAllAttendees()
    val allLogs: Flow<List<AttendanceLog>> = attendanceLogDao.getAllLogs()
    val allSessions: Flow<List<AttendanceSession>> = attendanceSessionDao.getAllSessions()

    suspend fun getAttendeeByUid(uid: String): Attendee? {
        return attendeeDao.getAttendeeByUid(uid)
    }

    suspend fun insertAttendee(attendee: Attendee) {
        attendeeDao.insertAttendee(attendee)
    }

    suspend fun deleteAttendee(id: Int) {
        attendeeDao.deleteAttendee(id)
    }

    suspend fun insertLog(log: AttendanceLog) {
        attendanceLogDao.insertLog(log)
    }

    suspend fun deleteLog(id: Int) {
        attendanceLogDao.deleteLog(id)
    }

    suspend fun clearAllLogs() {
        attendanceLogDao.clearAllLogs()
    }

    suspend fun getSessionByCode(code: String): AttendanceSession? {
        return attendanceSessionDao.getSessionByCode(code)
    }

    suspend fun insertSession(session: AttendanceSession) {
        attendanceSessionDao.insertSession(session)
    }

    suspend fun deleteSession(id: Int) {
        attendanceSessionDao.deleteSession(id)
    }
}
