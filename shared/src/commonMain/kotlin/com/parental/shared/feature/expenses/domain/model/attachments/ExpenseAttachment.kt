package com.parental.shared.feature.expenses.domain.model.attachments

import kotlinx.serialization.Serializable

@Serializable
data class ConvertExpenseAttachmentReq(
    val activityAttachmentId: String,
)
