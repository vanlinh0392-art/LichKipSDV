package com.sdv.lichnoti

import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.time.LocalDate
import java.util.concurrent.Executors

class DayNoteRepository private constructor(context: Context) {
    private val database = NoteDatabaseHelper(context.applicationContext)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lich-noti-notes").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getNote(date: LocalDate, callback: (Result<DayNote?>) -> Unit) {
        execute(callback) {
            database.readableDatabase.query(
                NoteDatabaseHelper.TABLE_NOTES,
                arrayOf(
                    NoteDatabaseHelper.COLUMN_DATE,
                    NoteDatabaseHelper.COLUMN_CONTENT_HTML,
                    NoteDatabaseHelper.COLUMN_PLAIN_TEXT,
                    NoteDatabaseHelper.COLUMN_UPDATED_AT
                ),
                "${NoteDatabaseHelper.COLUMN_DATE} = ?",
                arrayOf(date.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                DayNote(
                    date = LocalDate.parse(cursor.getString(0)),
                    html = cursor.getString(1),
                    plainText = cursor.getString(2),
                    updatedAt = cursor.getLong(3)
                )
            }
        }
    }

    fun getAllNoteDates(callback: (Result<Set<LocalDate>>) -> Unit) {
        execute(callback) {
            buildSet {
                database.readableDatabase.query(
                    NoteDatabaseHelper.TABLE_NOTES,
                    arrayOf(NoteDatabaseHelper.COLUMN_DATE),
                    null,
                    null,
                    null,
                    null,
                    NoteDatabaseHelper.COLUMN_DATE
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        runCatching { LocalDate.parse(cursor.getString(0)) }
                            .getOrNull()
                            ?.let(::add)
                    }
                }
            }
        }
    }

    /** Saves a note or deletes it when [plainText] contains no visible content. */
    fun save(
        date: LocalDate,
        html: String,
        plainText: String,
        callback: (Result<Boolean>) -> Unit
    ) {
        execute(callback) {
            val normalizedPlainText = plainText.trim()
            if (normalizedPlainText.isEmpty()) {
                database.writableDatabase.delete(
                    NoteDatabaseHelper.TABLE_NOTES,
                    "${NoteDatabaseHelper.COLUMN_DATE} = ?",
                    arrayOf(date.toString())
                )
                false
            } else {
                val values = ContentValues().apply {
                    put(NoteDatabaseHelper.COLUMN_DATE, date.toString())
                    put(NoteDatabaseHelper.COLUMN_CONTENT_HTML, html)
                    put(NoteDatabaseHelper.COLUMN_PLAIN_TEXT, normalizedPlainText)
                    put(NoteDatabaseHelper.COLUMN_UPDATED_AT, System.currentTimeMillis())
                }
                database.writableDatabase.insertWithOnConflict(
                    NoteDatabaseHelper.TABLE_NOTES,
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
                true
            }
        }
    }

    fun delete(date: LocalDate, callback: (Result<Unit>) -> Unit) {
        execute(callback) {
            database.writableDatabase.delete(
                NoteDatabaseHelper.TABLE_NOTES,
                "${NoteDatabaseHelper.COLUMN_DATE} = ?",
                arrayOf(date.toString())
            )
            Unit
        }
    }

    private fun <T> execute(callback: (Result<T>) -> Unit, block: () -> T) {
        executor.execute {
            val result = runCatching(block).onFailure {
                Log.e(TAG, "Lỗi truy cập ghi chú", it)
            }
            mainHandler.post { callback(result) }
        }
    }

    companion object {
        private const val TAG = "DayNoteRepository"

        @Volatile
        private var instance: DayNoteRepository? = null

        fun getInstance(context: Context): DayNoteRepository {
            return instance ?: synchronized(this) {
                instance ?: DayNoteRepository(context).also { instance = it }
            }
        }
    }
}
