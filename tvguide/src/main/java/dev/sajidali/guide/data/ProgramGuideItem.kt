package dev.sajidali.guide.data

sealed class ProgramGuideItem {
    data class Channel(
        val index: Int,
        val channelIndex: Int,
        val title: String,
        val logo: String
    ) : ProgramGuideItem()

    data class Program(
        val itemIndex: Int,
        val channelIndex: Int,
        val title: String,
        val description: String,
        val start: Long,
        val end: Long
    ) : ProgramGuideItem()
}
