package com.mauriciotogneri.fileexplorer.ui.components

object BreadcrumbPathParser {

    private val INTERNAL_STORAGE_REGEX = Regex("/storage/emulated/\\d+")

    fun parsePath(
        path: String,
        internalStorageName: String,
        rootPath: String?
    ): List<BreadcrumbItem> {
        if (path.isBlank()) return emptyList()

        val segments = path.trimStart('/').split('/').filter { it.isNotEmpty() }
        val items = mutableListOf<BreadcrumbItem>()

        var currentPath = ""
        for (segment in segments) {
            currentPath = "$currentPath/$segment"
            items.add(
                BreadcrumbItem(
                    name = segment,
                    path = currentPath
                )
            )
        }

        // If rootPath is provided, only show items from rootPath onwards
        if (rootPath != null) {
            val rootIndex = items.indexOfFirst { it.path == rootPath }
            if (rootIndex >= 0) {
                return items.drop(rootIndex)
            }
        }

        // Replace internal storage path with friendly name
        return collapseInternalStoragePath(items, internalStorageName)
    }

    private fun collapseInternalStoragePath(
        items: List<BreadcrumbItem>,
        internalStorageName: String
    ): List<BreadcrumbItem> {
        // Check for internal storage pattern: /storage/emulated/0
        val internalStorageIndex = items.indexOfFirst { item ->
            item.path.matches(INTERNAL_STORAGE_REGEX)
        }

        if (internalStorageIndex >= 0) {
            val internalStorageItem = items[internalStorageIndex]
            val collapsedItem = BreadcrumbItem(
                name = internalStorageName,
                path = internalStorageItem.path
            )
            return listOf(collapsedItem) + items.drop(internalStorageIndex + 1)
        }

        return items
    }
}
