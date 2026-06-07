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

    private val UNSUPPORTED_IMAGE_EXTENSIONS = setOf(
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

    private val VIEWABLE_IMAGE_EXTENSIONS = setOf(
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

    private val HEIF_IMAGE_EXTENSIONS = setOf(
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

    private val SQLITE_EXTENSIONS = setOf(
        "db", "sqlite", "sqlite3", "db3"
    )

    private val TEXT_EXTENSIONS = setOf(
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

    private val FONT_EXTENSIONS = setOf(
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
