package com.example.timemanager.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigration {

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = OFF")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS document_photos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    documentId INTEGER NOT NULL,
                    photoPath TEXT NOT NULL,
                    orderIndex INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_document_photos_documentId ON document_photos(documentId)"
            )

            db.execSQL(
                """
                INSERT INTO document_photos (documentId, photoPath, orderIndex)
                SELECT id, photoPath, 0 FROM documents WHERE photoPath IS NOT NULL
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE documents_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO documents_new (id, title, description, createdAt)
                SELECT id, title, description, createdAt FROM documents
                """.trimIndent()
            )
            db.execSQL("DROP TABLE documents")
            db.execSQL("ALTER TABLE documents_new RENAME TO documents")

            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}
