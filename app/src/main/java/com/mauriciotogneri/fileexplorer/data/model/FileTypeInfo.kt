package com.mauriciotogneri.fileexplorer.data.model

interface FileTypeInfo {
    val isDirectory: Boolean
    val isImage: Boolean
    val isSvg: Boolean
    val isPdf: Boolean
    val isAudio: Boolean
    val isVideo: Boolean
    val isApk: Boolean
    val isZip: Boolean
    val isArchive: Boolean
    val isOfficeDocument: Boolean
    val isEpub: Boolean
    val isSqlite: Boolean
    val isVCard: Boolean
    val isICalendar: Boolean
    val isCsv: Boolean
}
