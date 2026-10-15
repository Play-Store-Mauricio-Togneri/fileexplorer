package com.mauriciotogneri.fileexplorer.data.util

import android.os.Build
import android.webkit.MimeTypeMap
import java.io.File
import java.net.URLConnection

object MimeTypeUtil {

    fun getMimeType(file: File): String {
        return try {
            URLConnection.guessContentTypeFromName(file.absolutePath)
                ?: MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                ?: "*/*"
        } catch (_: Exception) {
            "*/*"
        }
    }

    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")

    fun hasNativeThumbnailSupport(mimeType: String, fileName: String): Boolean {
        if (!isImage(mimeType)) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext !in UNSUPPORTED_IMAGE_EXTENSIONS && mimeType !in UNSUPPORTED_IMAGE_MIME_TYPES
    }

    /**
     * Whether the in-app image viewer can render this file. Stricter than [isImage]: limited to the
     * formats Coil can actually decode, so unsupported image types (tiff, ico, raw, ...) fall through
     * instead of landing on an error screen. The platform-gated formats (HEIF/HEIC, AVIF) are only
     * included where their decoder exists. [sdkInt] is injected so the predicate stays a pure,
     * JVM-unit-testable function.
     */
    fun isViewableImage(mimeType: String, fileName: String, sdkInt: Int): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (mimeType in VIEWABLE_IMAGE_MIME_TYPES || ext in VIEWABLE_IMAGE_EXTENSIONS) {
            return true
        }
        if (sdkInt >= Build.VERSION_CODES.P &&
            (mimeType in HEIF_IMAGE_MIME_TYPES || ext in HEIF_IMAGE_EXTENSIONS)
        ) {
            return true
        }
        if (sdkInt >= Build.VERSION_CODES.S &&
            (mimeType in AVIF_IMAGE_MIME_TYPES || ext in AVIF_IMAGE_EXTENSIONS)
        ) {
            return true
        }
        return false
    }

    internal val UNSUPPORTED_IMAGE_EXTENSIONS = setOf(
        "tiff", "tif",
        "heic", "heif",
        "avif",
        "svg", "svgz",
        "cr2", "cr3", "nef", "arw", "dng", "raf", "orf", "rw2", "pef", "srw"
    )

    private val UNSUPPORTED_IMAGE_MIME_TYPES = setOf(
        "image/tiff",
        "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence",
        "image/avif",
        "image/svg+xml",
        "image/x-canon-cr2", "image/x-canon-cr3", "image/x-nikon-nef", "image/x-sony-arw",
        "image/x-adobe-dng", "image/x-fuji-raf", "image/x-olympus-orf", "image/x-panasonic-rw2",
        "image/x-pentax-pef", "image/x-samsung-srw"
    )

    // Formats the in-app image viewer can decode on every supported API level (BitmapFactory
    // formats + SVG via Coil's SvgDecoder). HEIF/AVIF are kept separate because they are API-gated.
    private val VIEWABLE_IMAGE_MIME_TYPES = setOf(
        "image/png",
        "image/jpeg",
        "image/webp",
        "image/gif",
        "image/bmp", "image/x-ms-bmp",
        "image/svg+xml"
    )

    internal val VIEWABLE_IMAGE_EXTENSIONS = setOf(
        "png",
        "jpg", "jpeg", "jpe", "jfif",
        "webp",
        "gif",
        "bmp",
        "svg", "svgz"
    )

    // Decodable only where the platform decoder exists: HEIF/HEIC on API 28+, AVIF on API 31+.
    private val HEIF_IMAGE_MIME_TYPES = setOf(
        "image/heic", "image/heif",
        "image/heic-sequence", "image/heif-sequence"
    )

    internal val HEIF_IMAGE_EXTENSIONS = setOf(
        "heic", "heif", "heics", "heifs"
    )

    private val AVIF_IMAGE_MIME_TYPES = setOf(
        "image/avif"
    )

    private val AVIF_IMAGE_EXTENSIONS = setOf(
        "avif"
    )

    fun isPdf(mimeType: String): Boolean = mimeType == "application/pdf"

    fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")

    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")

    /**
     * Whether the in-app media viewer can play this audio file. Stricter than [isAudio]: limited to
     * the containers Media3 ships an extractor for, so WMA, RealAudio, MIDI and the like fall
     * through instead of landing on an error screen. Only the container is judged: a codec the
     * device cannot decode still fails in the player, which reports it as unplayable media.
     */
    fun isViewableAudio(mimeType: String, fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return mimeType in VIEWABLE_AUDIO_MIME_TYPES || ext in VIEWABLE_AUDIO_EXTENSIONS
    }

    /** [isViewableAudio] for video: WMV/ASF, RealMedia, Ogg Theora and the like fall through. */
    fun isViewableVideo(mimeType: String, fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return mimeType in VIEWABLE_VIDEO_MIME_TYPES || ext in VIEWABLE_VIDEO_EXTENSIONS
    }

    // Containers with a Media3 extractor: MP3, MP4, ADTS, Ogg (Vorbis/Opus/FLAC), WAV, FLAC, AMR,
    // Matroska/WebM and AC-3/E-AC-3/AC-4.
    private val VIEWABLE_AUDIO_MIME_TYPES = setOf(
        "audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg", "audio/x-mp3",
        "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/x-m4b", "audio/3gpp",
        "audio/aac", "audio/aacp", "audio/x-aac",
        "audio/ogg", "audio/opus", "audio/vorbis",
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave",
        "audio/flac", "audio/x-flac",
        "audio/amr", "audio/amr-wb",
        "audio/webm", "audio/x-matroska",
        "audio/ac3", "audio/eac3", "audio/ac4"
    )

    internal val VIEWABLE_AUDIO_EXTENSIONS = setOf(
        "mp3",
        "m4a", "m4b", "3ga",
        "aac",
        "ogg", "oga", "opus",
        "wav",
        "flac",
        "amr", "awb",
        "weba", "mka",
        "ac3", "eac3", "ec3", "ac4"
    )

    // Containers with a Media3 extractor: MP4/QuickTime/3GP, Matroska/WebM, MPEG-PS, MPEG-TS, FLV
    // and AVI. Ogg is left out: its extractor reads no video, so a Theora file would not play.
    // "ts" and "mts" are left out as extensions: both are TypeScript sources in TEXT_EXTENSIONS.
    private val VIEWABLE_VIDEO_MIME_TYPES = setOf(
        "video/mp4", "video/x-m4v", "video/3gpp", "video/3gpp2", "video/quicktime",
        "video/webm", "video/x-matroska",
        "video/mpeg", "video/mp2p",
        "video/mp2t",
        "video/x-flv",
        "video/avi", "video/x-msvideo", "video/msvideo"
    )

    internal val VIEWABLE_VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "3gp", "3gpp", "3g2", "mov",
        "webm", "mkv",
        "mpg", "mpeg", "mpe", "vob",
        "flv",
        "avi"
    )

    fun isApk(mimeType: String): Boolean = mimeType == "application/vnd.android.package-archive"

    fun isZip(mimeType: String): Boolean = mimeType in ZIP_MIME_TYPES

    fun isArchive(mimeType: String): Boolean = mimeType in ARCHIVE_MIME_TYPES

    fun isOfficeDocument(mimeType: String): Boolean = mimeType in OFFICE_MIME_TYPES

    fun isEpub(mimeType: String): Boolean = mimeType == "application/epub+zip"

    fun isFont(mimeType: String): Boolean = mimeType in FONT_MIME_TYPES

    fun isFontByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in FONT_EXTENSIONS
    }

    fun isSvg(mimeType: String): Boolean = mimeType == "image/svg+xml"

    fun isSvgByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "svg" || ext == "svgz"
    }

    fun isSqlite(mimeType: String): Boolean = mimeType in SQLITE_MIME_TYPES

    fun isVCard(mimeType: String): Boolean = mimeType == "text/vcard" || mimeType == "text/x-vcard"

    fun isICalendar(mimeType: String): Boolean = mimeType == "text/calendar"

    fun isCsv(mimeType: String): Boolean = mimeType == "text/csv" || mimeType == "text/comma-separated-values"

    fun isSqliteByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SQLITE_EXTENSIONS
    }

    fun isVCardByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "vcf" || ext == "vcard"
    }

    fun isICalendarByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "ics" || ext == "ical" || ext == "ifb"
    }

    fun isCsvByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "csv"
    }

    fun isText(mimeType: String): Boolean = mimeType.startsWith("text/")

    fun isTextByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    private val SQLITE_MIME_TYPES = setOf(
        "application/vnd.sqlite3",
        "application/x-sqlite3",
        "application/x-sqlite",
        "application/sqlite"
    )

    internal val SQLITE_EXTENSIONS = setOf(
        "db", "sqlite", "sqlite3", "db3"
    )

    internal val TEXT_EXTENSIONS = setOf(
        // Plain text & docs
        "txt", "text", "md", "markdown", "mdx", "rmd", "qmd", "rst", "adoc",
        "asciidoc", "asc", "tex", "latex", "org", "textile", "bib", "nfo",
        "log", "lst", "po", "pot",
        // Structured data, markup & schemas
        "json", "json5", "jsonc", "jsonl", "ndjson", "jsonld", "xml", "xhtml",
        "rdf", "sgml", "yaml", "yml", "toml", "ini", "conf", "cfg", "config",
        "properties", "env", "csv", "tsv", "psv", "kml", "gpx", "geojson",
        "topojson", "osm", "tcx", "rss", "atom", "xsd", "xsl", "xslt", "dtd",
        "wsdl", "graphql", "gql", "graphqls", "proto", "thrift", "fbs", "avsc",
        "ttl", "dot", "gv", "puml",
        // Subtitles & playlists
        "srt", "vtt", "ass", "ssa", "ttml", "lrc", "sbv", "m3u", "m3u8", "pls",
        "cue", "xspf",
        // Web & templating
        "html", "htm", "css", "scss", "sass", "less", "styl", "js", "mjs",
        "cjs", "jsx", "ts", "tsx", "cts", "mts", "vue", "svelte", "astro",
        "ejs", "erb", "hbs", "handlebars", "mustache", "pug", "haml", "slim",
        "twig", "liquid", "njk", "j2", "jinja", "jinja2", "cshtml", "razor",
        "aspx", "ascx", "jsp", "jspx", "ftl", "vm", "tpl", "phtml", "htaccess",
        // Source code
        "kt", "kts", "java", "py", "pyw", "pyi", "rb", "go", "rs", "c", "cc",
        "cpp", "cxx", "h", "hpp", "hh", "cs", "php", "swift", "scala", "groovy",
        "lua", "dart", "pl", "pm", "r", "jl", "clj", "cljs", "cljc", "edn",
        "ex", "exs", "erl", "hrl", "hs", "lhs", "elm", "ml", "mli", "fs", "fsx",
        "fsi", "nim", "nims", "nimble", "zig", "vala", "d", "pas", "vb", "vbs",
        "asm", "s", "lisp", "scm", "ss", "sld", "rkt", "coffee", "hx", "tcl",
        "tk", "sol", "v", "vh", "svh", "vhdl", "sv", "f", "for", "f90", "f95",
        "f03", "cob", "cbl", "ada", "adb", "ads", "pp", "cr", "purs", "re",
        "sml", "gleam", "wat", "gd", "m", "mm", "el", "vim",
        // Shaders & Qt
        "glsl", "hlsl", "vert", "frag", "geom", "comp", "wgsl", "metal",
        "shader", "qml", "qss", "qrc", "qbs",
        // Shell, build & config
        "sh", "bash", "zsh", "fish", "ksh", "csh", "bat", "cmd", "ps1", "psm1",
        "awk", "sed", "gcode", "gco", "sql", "gradle", "cmake", "mk", "mak",
        "makefile", "dockerfile", "bazel", "bzl", "bazelrc", "ninja", "pro",
        "pri", "cabal", "nuspec", "csproj", "vbproj", "fsproj", "vcxproj",
        "pbxproj", "xcconfig", "props", "targets", "sln", "sbt", "gemspec",
        "podspec", "nix", "dhall", "tf", "tfvars", "hcl", "cnf", "gitignore",
        "gitattributes", "gitmodules", "gitconfig", "dockerignore",
        "editorconfig", "npmrc", "nvmrc", "yarnrc", "babelrc", "eslintrc",
        "eslintignore", "prettierrc", "prettierignore", "stylelintrc",
        "browserslistrc", "npmignore", "pylintrc", "flake8", "clang-format",
        "clang-tidy", "mailmap", "bashrc", "zshrc", "profile", "vimrc",
        "inputrc", "envrc", "tool-versions", "desktop", "service", "diff",
        "patch", "rej", "orig", "mf", "manifest"
    )

    private val ZIP_MIME_TYPES = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/x-zip"
    )

    private val ARCHIVE_MIME_TYPES = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/x-zip",
        "application/rar",
        "application/vnd.rar",
        "application/x-rar-compressed",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/gzip",
        "application/x-gzip",
        "application/x-bzip2",
        "application/x-xz",
        "application/x-lzip",
        "application/x-lzma",
        "application/x-compress",
        "application/zstd",
        "application/x-zstd",
        "application/x-lz4",
        "application/vnd.ms-cab-compressed",
        "application/x-iso9660-image",
        "application/x-apple-diskimage",
        "application/x-cpio"
    )

    private val FONT_MIME_TYPES = setOf(
        "font/ttf",
        "font/otf",
        "font/woff",
        "font/woff2",
        "font/sfnt",
        "application/x-font-ttf",
        "application/x-font-otf",
        "application/font-woff",
        "application/font-woff2",
        "application/vnd.ms-fontobject",
        "application/vnd.ms-opentype",
        "application/font-sfnt"
    )

    internal val FONT_EXTENSIONS = setOf(
        "ttf", "otf", "woff", "woff2", "eot", "sfnt"
    )

    private val OFFICE_MIME_TYPES = setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
        "application/msaccess",
        "application/vnd.ms-access",
        "application/vnd.oasis.opendocument.database",
        "application/vnd.visio",
        "application/vnd.ms-visio.drawing",
        "application/vnd.oasis.opendocument.graphics",
        "application/vnd.oasis.opendocument.formula",
        "application/vnd.oasis.opendocument.chart",
        "application/rtf",
        "text/rtf"
    )
}
