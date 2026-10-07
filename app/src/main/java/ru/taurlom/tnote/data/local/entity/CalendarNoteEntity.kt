package ru.taurlom.tnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_notes")
data class CalendarNoteEntity(
    /**
     * ISO-���� �YYYY-MM-DD� � ��������� ����: ���� ������� �� ����.
     * ����-increment id ����� ��� ��������: ������ ���������� ���������
     * ����� ��� ������ ���������� (����� �������� �����).
     */
    @PrimaryKey
    val eventDate: String,
    val text: String,
)
