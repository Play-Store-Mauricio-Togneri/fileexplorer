"""
Lays out the Play Store listing's phone screenshots from the WebsiteScreenshots class's captures.

Each capture sits under a caption that names what it lets the user do. play_screenshots.html draws
one screenshot and headless Chrome renders it, so every script and right-to-left language gets its
text shaped by a browser. Play takes 1080x1920 (9:16 qualifies for its larger placements) in PNG
without transparency.

Usage: python3 scripts/play_screenshots.py <captures> <output> <language...>
       python3 scripts/play_screenshots.py --check <language...>
Languages are the website's codes; each one's screenshots land in <output>/<Play language code>/.
--check only stops with an error when a language has no captions yet, before anything is captured.
"""

import html
import pathlib
import shutil
import string
import subprocess
import sys
import tempfile

from PIL import Image

WIDTH, HEIGHT = 1080, 1920
CAPTURE_WIDTH, CAPTURE_HEIGHT = 1080, 2400

# The caption's band; two lines of it stay under Play's limit of 20% of the image for taglines.
CAPTION_HEIGHT = 320
BOTTOM_MARGIN = 60
SHEET_RADIUS = 36

# In listing order: the first three carry most of the conversion.
SLOTS = [
    ("drives", "light"),
    ("analyzer", "light"),
    ("search", "light"),
    ("select", "light"),
    ("pdf", "light"),
    ("actions", "light"),
    ("settings", "light"),
    ("home", "dark"),
]

CAPTIONS = {
    "en": [
        "Phone, SD card and USB in one place",
        "See what's filling your storage",
        "Find what you need, fast",
        "Manage your files with ease",
        "Open files without another app",
        "Everything you need, one tap away",
        "Tailor the app to how you work",
        "Light or dark, your choice",
    ],
    "ar": [
        "الهاتف وبطاقة SD ومحرك USB في مكان واحد",
        "اكتشف ما يملأ مساحة التخزين لديك",
        "اعثر على ما تحتاجه بسرعة",
        "أدِر ملفاتك بسهولة",
        "افتح الملفات دون الحاجة إلى تطبيق آخر",
        "كل ما تحتاجه بلمسة واحدة",
        "خصّص التطبيق ليناسب طريقة عملك",
        "فاتح أو داكن، الخيار لك",
    ],
    "bn": [
        "ফোন, SD কার্ড ও USB এক জায়গায়",
        "দেখুন কী আপনার স্টোরেজ ভরিয়ে ফেলছে",
        "যা দরকার, দ্রুত খুঁজে নিন",
        "সহজেই আপনার ফাইল ম্যানেজ করুন",
        "অন্য কোনো অ্যাপ ছাড়াই ফাইল খুলুন",
        "যা দরকার, সবই এক ট্যাপে",
        "আপনার কাজের ধরন অনুযায়ী অ্যাপ সাজিয়ে নিন",
        "হালকা বা গাঢ়, আপনার পছন্দ",
    ],
    "ca": [
        "Telèfon, targeta SD i USB en un sol lloc",
        "Descobreix què omple el teu emmagatzematge",
        "Troba el que necessites, ràpid",
        "Gestiona els teus fitxers amb facilitat",
        "Obre fitxers sense cap altra app",
        "Tot el que necessites, a un toc",
        "Adapta l’app a la teva manera de treballar",
        "Clar o fosc, tu tries",
    ],
    "de": [
        "Handy, SD-Karte und USB an einem Ort",
        "Sehen Sie, was Ihren Speicher füllt",
        "Finden Sie schnell, was Sie brauchen",
        "Verwalten Sie Ihre Dateien ganz einfach",
        "Öffnen Sie Dateien ohne weitere App",
        "Alles, was Sie brauchen, mit einem Tippen",
        "Passen Sie die App an Ihre Arbeitsweise an",
        "Hell oder dunkel, Sie entscheiden",
    ],
    "el": [
        "Τηλέφωνο, κάρτα SD και USB σε ένα σημείο",
        "Δείτε τι γεμίζει τον αποθηκευτικό σας χώρο",
        "Βρείτε γρήγορα ό,τι χρειάζεστε",
        "Διαχειριστείτε τα αρχεία σας εύκολα",
        "Ανοίξτε αρχεία χωρίς άλλη εφαρμογή",
        "Ό,τι χρειάζεστε, με ένα πάτημα",
        "Προσαρμόστε την εφαρμογή στις ανάγκες σας",
        "Φωτεινό ή σκοτεινό, εσείς επιλέγετε",
    ],
    "es": [
        "Teléfono, tarjeta SD y USB en un solo lugar",
        "Descubre qué ocupa tu almacenamiento",
        "Encuentra lo que necesitas, rápido",
        "Gestiona tus archivos con facilidad",
        "Abre archivos sin otra app",
        "Todo lo que necesitas, a un toque",
        "Adapta la app a tu forma de trabajar",
        "Claro u oscuro, tú eliges",
    ],
    "fr": [
        "Téléphone, carte SD et USB au même endroit",
        "Découvrez ce qui encombre votre stockage",
        "Trouvez vite ce que vous cherchez",
        "Gérez vos fichiers en toute simplicité",
        "Ouvrez vos fichiers sans autre appli",
        "Tout ce qu’il vous faut, en un geste",
        "Adaptez l’appli à votre façon de travailler",
        "Clair ou sombre, à vous de choisir",
    ],
    "hi": [
        "फ़ोन, SD कार्ड और USB एक ही जगह",
        "देखें कि आपकी स्टोरेज किससे भर रही है",
        "जो चाहिए, तुरंत खोजें",
        "अपनी फ़ाइलें आसानी से प्रबंधित करें",
        "किसी दूसरे ऐप के बिना फ़ाइलें खोलें",
        "आपकी ज़रूरत की हर चीज़, बस एक टैप पर",
        "ऐप को अपने काम करने के तरीके के अनुसार ढालें",
        "लाइट या डार्क, आपकी पसंद",
    ],
    "id": [
        "Ponsel, kartu SD, dan USB di satu tempat",
        "Lihat apa yang memenuhi penyimpanan Anda",
        "Temukan yang Anda perlukan dengan cepat",
        "Kelola file Anda dengan mudah",
        "Buka file tanpa aplikasi lain",
        "Semua yang Anda perlukan, cukup sekali ketuk",
        "Sesuaikan aplikasi dengan cara kerja Anda",
        "Terang atau gelap, Anda yang memilih",
    ],
    "it": [
        "Telefono, scheda SD e USB in un unico posto",
        "Scopri cosa occupa la tua memoria",
        "Trova ciò che ti serve, in un attimo",
        "Gestisci i tuoi file con facilità",
        "Apri i file senza altre app",
        "Tutto ciò che ti serve, in un tocco",
        "Adatta l’app al tuo modo di lavorare",
        "Chiaro o scuro, scegli tu",
    ],
    "ja": [
        "スマホもSDカードもUSBも、ひとつの画面で",
        "ストレージを圧迫しているものがひと目でわかる",
        "探しているものが、すぐ見つかる",
        "ファイル管理をもっと手軽に",
        "ほかのアプリなしでファイルを開ける",
        "必要な操作は、ワンタップで",
        "使い方に合わせてアプリをカスタマイズ",
        "ライトもダークも、お好みで",
    ],
    "nl": [
        "Telefoon, SD-kaart en USB op één plek",
        "Zie wat je opslag vult",
        "Vind snel wat je zoekt",
        "Beheer je bestanden met gemak",
        "Open bestanden zonder extra app",
        "Alles wat je nodig hebt, met één tik",
        "Stem de app af op jouw manier van werken",
        "Licht of donker, jij kiest",
    ],
    "pt": [
        "Celular, cartão SD e USB em um só lugar",
        "Veja o que está ocupando seu armazenamento",
        "Encontre o que você precisa, rápido",
        "Gerencie seus arquivos com facilidade",
        "Abra arquivos sem outro app",
        "Tudo o que você precisa, a um toque",
        "Adapte o app ao seu jeito de trabalhar",
        "Claro ou escuro, você escolhe",
    ],
    "ro": [
        "Telefon, card SD și USB într-un singur loc",
        "Vezi ce îți ocupă spațiul de stocare",
        "Găsește rapid ce cauți",
        "Gestionează-ți fișierele cu ușurință",
        "Deschide fișiere fără altă aplicație",
        "Tot ce îți trebuie, la o atingere distanță",
        "Adaptează aplicația la felul în care lucrezi",
        "Luminos sau întunecat, tu alegi",
    ],
    "ru": [
        "Телефон, SD-карта и USB в одном месте",
        "Узнайте, что занимает память",
        "Быстро находите то, что нужно",
        "Управляйте файлами с лёгкостью",
        "Открывайте файлы без других приложений",
        "Всё необходимое — в одно касание",
        "Настройте приложение под свой стиль работы",
        "Светлая или тёмная тема — на ваш выбор",
    ],
    "tr": [
        "Telefon, SD kart ve USB tek bir yerde",
        "Depolama alanınızı neyin doldurduğunu görün",
        "Aradığınızı hızla bulun",
        "Dosyalarınızı kolayca yönetin",
        "Dosyaları başka uygulama olmadan açın",
        "İhtiyacınız olan her şey tek dokunuşla",
        "Uygulamayı çalışma şeklinize göre uyarlayın",
        "Açık veya koyu, seçim sizin",
    ],
    "ur": [
        "فون، SD کارڈ اور USB ایک ہی جگہ",
        "دیکھیں کہ آپ کی اسٹوریج کس چیز سے بھر رہی ہے",
        "جو چاہیے، فوراً تلاش کریں",
        "اپنی فائلیں آسانی سے منظم کریں",
        "کسی اور ایپ کے بغیر فائلیں کھولیں",
        "ضرورت کی ہر چیز، بس ایک ٹیپ پر",
        "ایپ کو اپنے کام کے انداز کے مطابق ڈھالیں",
        "ہلکی یا گہری تھیم، انتخاب آپ کا",
    ],
    "vi": [
        "Điện thoại, thẻ SD và USB ở cùng một nơi",
        "Xem thứ gì đang chiếm bộ nhớ của bạn",
        "Tìm thứ bạn cần, thật nhanh",
        "Quản lý tệp của bạn thật dễ dàng",
        "Mở tệp mà không cần ứng dụng khác",
        "Mọi thứ bạn cần, chỉ một chạm",
        "Tùy chỉnh ứng dụng theo cách bạn làm việc",
        "Sáng hay tối, tùy bạn chọn",
    ],
    "zh": [
        "手机、SD卡和U盘，一处管理",
        "看清是什么占满了存储空间",
        "快速找到所需内容",
        "轻松管理您的文件",
        "无需其他应用即可打开文件",
        "所需功能，一触即达",
        "按您的工作方式定制应用",
        "浅色或深色，由您选择",
    ],
}

# Each website language's listing on Play, which has exactly these 20 and no regional variants.
PLAY_LANGUAGES = {
    "en": "en-US",
    "ar": "ar",
    "bn": "bn-BD",
    "ca": "ca",
    "de": "de-DE",
    "el": "el-GR",
    "es": "es-ES",
    "fr": "fr-FR",
    "hi": "hi-IN",
    "id": "id",
    "it": "it-IT",
    "ja": "ja-JP",
    "nl": "nl-NL",
    "pt": "pt-BR",
    "ro": "ro",
    "ru": "ru-RU",
    "tr": "tr-TR",
    "ur": "ur",
    "vi": "vi",
    "zh": "zh-CN",
}

RIGHT_TO_LEFT = {"ar", "ur"}

# Bricolage Grotesque covers Latin and Vietnamese only. Every other script gets a Google Noto face
# at the caption's weight, 800 (700 where the face stops there), which also draws the caption's
# Latin letters (SD, USB), so one caption never mixes two typefaces.
SCRIPT_FONTS = {
    "ar": ("Noto Sans Arabic", 800),
    "bn": ("Noto Sans Bengali", 800),
    "el": ("Noto Sans", 800),
    "hi": ("Noto Sans Devanagari", 800),
    "ja": ("Noto Sans JP", 800),
    "ru": ("Noto Sans", 800),
    "ur": ("Noto Nastaliq Urdu", 700),
    "zh": ("Noto Sans SC", 800),
}

# Scripts with marks far above and below the letters need more room between a caption's two lines.
LINE_HEIGHTS = {"ar": 1.4, "bn": 1.35, "hi": 1.35, "ja": 1.25, "ur": 1.9, "zh": 1.25}
DEFAULT_LINE_HEIGHT = 1.08

# Nastaliq's letters rise and fall far past its line, so it starts smaller to stay inside the band.
FONT_SIZES = {"ur": 60}
DEFAULT_FONT_SIZE = 80


def fonts(language: str) -> dict:
    families = ["family=Bricolage+Grotesque:opsz,wght@12..96,800"]
    stack = ['"Bricolage Grotesque"', "sans-serif"]
    if language in SCRIPT_FONTS:
        name, weight = SCRIPT_FONTS[language]
        families.append(f"family={name.replace(' ', '+')}:wght@{weight}")
        stack.insert(0, f'"{name}"')
    return {
        "font_families": "&".join(families),
        "font_family": ", ".join(stack),
        "line_height": LINE_HEIGHTS.get(language, DEFAULT_LINE_HEIGHT),
        "font_size": FONT_SIZES.get(language, DEFAULT_FONT_SIZE),
    }


def layout() -> dict:
    sheet_top = CAPTION_HEIGHT + 10
    sheet_height = HEIGHT - BOTTOM_MARGIN - sheet_top
    sheet_width = round(sheet_height * CAPTURE_WIDTH / CAPTURE_HEIGHT)
    return {
        "width": WIDTH,
        "height": HEIGHT,
        "caption_height": CAPTION_HEIGHT,
        "sheet_top": sheet_top,
        "sheet_left": (WIDTH - sheet_width) // 2,
        "sheet_width": sheet_width,
        "sheet_height": sheet_height,
        "sheet_radius": SHEET_RADIUS,
    }


def check_languages(languages: list[str]) -> None:
    missing = [language for language in languages if language not in CAPTIONS or language not in PLAY_LANGUAGES]
    if missing:
        sys.exit(f"No captions for {', '.join(missing)} yet: add them to CAPTIONS and PLAY_LANGUAGES first.")


def render(chrome: str, page: pathlib.Path, png: pathlib.Path, profile: str) -> None:
    result = subprocess.run(
        [
            chrome,
            "--headless=new",
            "--hide-scrollbars",
            "--force-device-scale-factor=1",
            f"--window-size={WIDTH},{HEIGHT}",
            # Long enough for the caption's web font to arrive and the fitting script to run.
            "--virtual-time-budget=10000",
            f"--user-data-dir={profile}",
            f"--screenshot={png}",
            page.as_uri(),
        ],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        sys.exit(f"Chrome could not render {page.name}:\n{result.stderr}")


def main() -> None:
    if sys.argv[1] == "--check":
        check_languages(sys.argv[2:])
        return

    captures, output = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    languages = sys.argv[3:]
    check_languages(languages)
    template = string.Template(
        (pathlib.Path(__file__).parent / "play_screenshots.html").read_text(encoding="utf-8")
    )
    chrome = shutil.which("google-chrome") or shutil.which("chromium")
    if chrome is None:
        sys.exit("Install Google Chrome or Chromium first.")

    count = 0
    with tempfile.TemporaryDirectory() as work:
        workdir = pathlib.Path(work)
        for language in languages:
            target = output / PLAY_LANGUAGES[language]
            shutil.rmtree(target, ignore_errors=True)
            target.mkdir(parents=True)
            for slot, ((screen, theme), caption) in enumerate(zip(SLOTS, CAPTIONS[language], strict=True), start=1):
                capture = captures / language / f"{screen}-{theme}.png"
                with Image.open(capture) as image:
                    if image.size != (CAPTURE_WIDTH, CAPTURE_HEIGHT):
                        sys.exit(f"{capture} is {image.size[0]}x{image.size[1]}, not {CAPTURE_WIDTH}x{CAPTURE_HEIGHT}")

                page = workdir / f"{language}-{slot}.html"
                page.write_text(template.substitute(
                    layout(),
                    **fonts(language),
                    lang=PLAY_LANGUAGES[language],
                    direction="rtl" if language in RIGHT_TO_LEFT else "ltr",
                    theme=theme,
                    caption=html.escape(caption),
                    screenshot=capture.resolve().as_uri(),
                ), encoding="utf-8")
                rendered = workdir / f"{language}-{slot}.png"
                render(chrome, page, rendered, str(workdir / "chrome"))

                with Image.open(rendered) as image:
                    if image.size != (WIDTH, HEIGHT):
                        sys.exit(f"Chrome rendered {image.size[0]}x{image.size[1]}, not {WIDTH}x{HEIGHT}")
                    image.convert("RGB").save(target / f"{slot}-{screen}.png", optimize=True)
                count += 1

    print(f"Wrote {count} screenshots to {output}")


if __name__ == "__main__":
    main()
