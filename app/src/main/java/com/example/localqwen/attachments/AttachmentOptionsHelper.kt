package com.example.localqwen.attachments

/**
 * Static helper that provides labels and option arrays for the attachment picker dialogs.
 * Extracted from MainActivity to reduce its size without changing any behavior.
 */
object AttachmentOptionsHelper {

    /** Options shown in the main attachment-type picker ("صورة", "PDF"). */
    fun mainAttachmentOptions(): Array<String> = arrayOf("صورة", "PDF")

    /** Options shown after an image is selected ("استخراج النص من الصورة", "اسأل عن الصورة"). */
    fun imageActionOptions(): Array<String> = arrayOf(
        "استخراج النص من الصورة",
        "اسأل عن الصورة"
    )

    /** Dialog title for the main attachment-type picker. */
    fun attachmentDialogTitle(): String = "ملف"

    /** Dialog title for the image-action picker. */
    fun imageActionDialogTitle(): String = "إجراء الصورة"
}
