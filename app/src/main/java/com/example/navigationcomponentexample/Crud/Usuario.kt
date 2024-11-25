package com.example.navigationcomponentexample.Crud

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey var usuario: String,
    @ColumnInfo(name = "pais") var pais: String
)