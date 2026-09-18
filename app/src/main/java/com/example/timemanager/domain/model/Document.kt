package com.example.timemanager.domain.model

data class Document(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val photoPaths: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
