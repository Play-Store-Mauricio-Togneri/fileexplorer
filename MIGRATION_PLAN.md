### DataStore Migration

Replace `UserPreferencesManager` SharedPreferences with DataStore:

```kotlin
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(context: Context) {
    private val dataStore = context.dataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.SYSTEM.name)
    }

    val sortMode: Flow<SortMode> = dataStore.data.map { prefs ->
        SortMode.valueOf(prefs[SORT_KEY] ?: SortMode.NAME_ASC.name)
    }

    val showHiddenFiles: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_HIDDEN_KEY] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setSortMode(mode: SortMode) {
        dataStore.edit { it[SORT_KEY] = mode.name }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        dataStore.edit { it[SHOW_HIDDEN_KEY] = show }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val SORT_KEY = stringPreferencesKey("sort_mode")
        private val SHOW_HIDDEN_KEY = booleanPreferencesKey("show_hidden_files")
    }
}
```