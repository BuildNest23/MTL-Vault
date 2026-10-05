package com.example.ui.screens.detail

import com.example.data.model.ChapterSummary

object ChapterListScrollHelper {

    /**
     * Default number of header items before chapters in the LazyColumn:
     * - Item 0: Novel metadata card (including quick action buttons)
     * - Item 1: Chapter list header card ("Chapters (N)" / "Select" row)
     */
    const val DEFAULT_HEADER_ITEM_COUNT = 2

    /**
     * Calculates the target LazyColumn item index to scroll to.
     *
     * Rules:
     * - If chapters list is empty: returns null (do nothing)
     * - If enabled is false (setting OFF): returns 0 (leave list at the top)
     * - If first unread chapter is index 0: returns 0 (leave list at the top)
     * - If middle unread chapter exists: returns headerItemCount + firstUnreadIndex
     * - If all chapters are read: returns headerItemCount + (chapters.size - 1) (positions last chapter at top)
     */
    fun calculateTargetScrollIndex(
        chapters: List<ChapterSummary>,
        headerItemCount: Int = DEFAULT_HEADER_ITEM_COUNT,
        enabled: Boolean = true
    ): Int? {
        if (chapters.isEmpty()) return null
        if (!enabled) return 0

        val firstUnreadIndex = chapters.indexOfFirst { !it.read }

        return when {
            // If the first unread chapter is index 0, leave at top (index 0)
            firstUnreadIndex == 0 -> 0
            // If there's an unread chapter further down the list
            firstUnreadIndex > 0 -> headerItemCount + firstUnreadIndex
            // If all chapters are read, position so the last chapter is at the top
            else -> headerItemCount + (chapters.size - 1)
        }
    }
}
