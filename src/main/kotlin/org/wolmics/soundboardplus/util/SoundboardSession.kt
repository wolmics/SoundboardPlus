package org.wolmics.soundboardplus.util

object SoundboardSession {
    private var selectedCategory: String? = null

    fun getSelectedCategory(): String? {
        return selectedCategory
    }

    fun setSelectedCategory(category: String?) {
        selectedCategory = category
    }
}