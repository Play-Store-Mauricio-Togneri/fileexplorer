package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.Favorite
import kotlinx.coroutines.flow.Flow

interface FavoriteFilesSource {
    val favoritesFlow: Flow<List<Favorite>>
    suspend fun getFavorites(): List<Favorite>
    suspend fun updateFavorites(transform: (List<Favorite>) -> List<Favorite>)
    suspend fun clearFavorites()
}
