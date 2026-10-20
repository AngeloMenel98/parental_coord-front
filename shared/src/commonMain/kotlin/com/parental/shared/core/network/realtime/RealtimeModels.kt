package com.parental.shared.core.network.realtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityChangedMessage(
    @SerialName("v")
    val v: Int,
    @SerialName("type")
    val type: String,
    @SerialName("eventId")
    val eventId: String? = null,
    @SerialName("activityId")
    val activityId: String,
    @SerialName("bondId")
    val bondId: String,
    @SerialName("from")
    val from: String? = null,
    @SerialName("to")
    val to: String? = null,
)