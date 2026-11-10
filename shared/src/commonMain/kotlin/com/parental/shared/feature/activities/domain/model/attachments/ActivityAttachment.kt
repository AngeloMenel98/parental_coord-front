package com.parental.shared.feature.activities.domain.model.attachments

import kotlinx.serialization.Serializable

@Serializable
data class ActivityAttachmentDto(
    val id: String,
    val activityId: String,
    val fileName: String,
    val fileType: String,
    val uploadedBy: UploadedByDto? = null,
    val uploadedAt: String,
    val viewUrl: String? = null,
)

@Serializable
data class UploadedByDto(
    val id: String,
    val firstName: String? = null,
    val lastName: String? = null,
)

@Serializable
data class PresignAttachmentReq(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

@Serializable
data class PresignAttachmentRes(
    val uploadUrl: String,
    val path: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val expiresIn: Long,
)

@Serializable
data class ConfirmAttachmentReq(
    val path: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

@Serializable
data class RenameAttachmentReq(
    val fileName: String,
)

@Serializable
data class ReplaceAttachmentReq(
    val path: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
)
