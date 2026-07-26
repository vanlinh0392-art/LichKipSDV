package com.sdv.lichnoti

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class NoteDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NOTES (
                $COLUMN_DATE TEXT PRIMARY KEY NOT NULL,
                $COLUMN_CONTENT_HTML TEXT NOT NULL,
                $COLUMN_PLAIN_TEXT TEXT NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        private const val DATABASE_NAME = "day_notes.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NOTES = "day_notes"
        const val COLUMN_DATE = "date"
        const val COLUMN_CONTENT_HTML = "content_html"
        const val COLUMN_PLAIN_TEXT = "plain_text"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
