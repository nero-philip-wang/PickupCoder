package com.example.smspicker

import java.io.Serializable

data class SmsInfo(
    val id: String,
    val pickupCode: String,
    val station: String,
    val sender: String,
    val body: String,
    val time: Long
) : Serializable
