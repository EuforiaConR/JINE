package com.euforia.jine.data.model

/** Catalogs can fail independently. Sticker IDs are scoped to their pack. */
internal class StickerCatalogs(val pchan: List<Sticker>?, val ame: List<Sticker>? = null) {
    private val pchanById = pchan.orEmpty().associateBy { it.id }
    private val ameById = ame.orEmpty().associateBy { it.id }

    fun resolve(sender: MessageSender, content: MessageContent.Sticker): Sticker? = when (sender) {
        MessageSender.PCHAN -> if (content.packId == PCHAN_PACK_ID) pchanById[content.stickerId] else null
        MessageSender.AME -> if (content.packId == AME_PACK_ID) ameById[content.stickerId] else null
    }
}
