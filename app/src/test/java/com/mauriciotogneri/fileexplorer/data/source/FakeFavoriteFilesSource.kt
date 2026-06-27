package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.Favorite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeFavoriteFilesSource(
    initialFiles: List<Favorite> = emptyList()
) : FavoriteFilesSource {

    private val _files = MutableStateFlow(initialFiles)

    var updateCount = 0
        private set

    override val favoritesFlow: Flow<List<Favorite>> = _files

    override suspend fun getFavorites(): List<Favorite> = _files.value

    override suspend fun updateFavorites(transform: (List<Favorite>) -> List<Favorite>) {
        updateCount++
        _files.update { transform(it) }
    }

    override suspend fun clearFavorites() {
        _files.value = emptyList()
    }
}
