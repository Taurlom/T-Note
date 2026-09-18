package com.example.timemanager.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val color: Long,
    val position: Int = 0
)
