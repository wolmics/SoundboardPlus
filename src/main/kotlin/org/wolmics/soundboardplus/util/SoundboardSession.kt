package org.wolmics.soundboardplus.util

import org.wolmics.soundboardplus.gui.SoundboardScreen.SortMethod

object SoundboardSession {
    private var selectedCategory: String? = null
    private var selectedCategoryPage: Int? = null
    private var sortingMethod: SortMethod? = null

    fun getSelectedCategory(): String? {
        return selectedCategory
    }

    fun setSelectedCategory(category: String?) {
        selectedCategory = category
    }

    fun getSelectedCategoryPage(): Int? {
        return selectedCategoryPage
    }

    fun setSelectedCategoryPage(page: Int?) {
        selectedCategoryPage = page
    }

    fun getSortingMethod(): SortMethod? {
        return sortingMethod
    }

    fun setSortingMethod(sort: SortMethod?) {
        this.sortingMethod = sort
    }
}