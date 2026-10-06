"""Static native UI illustration, not a screenshot or a substitute for device tests.

Long scrolling screens are condensed so the main controls are visible together.
Run from project root with Pillow installed: python3 tools/RenderPreview.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
BG, SURFACE, TEXT, MUTED, ACCENT, TEAL, GOLD, LINE = (
    "#111020", "#1E1B32", "#F5F0FF", "#A49AB8", "#C3A6FF", "#7BE3CF", "#FFCE86", "#322B48"
)
REGULAR = Path("/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf")
BOLD = Path("/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf")
if not REGULAR.exists():
    REGULAR = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    BOLD = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")


def font(size, bold=False):
    return ImageFont.truetype(str(BOLD if bold else REGULAR), size)


def write(draw, xy, value, size=14, color=TEXT, bold=False, width=None, leading=4):
    x, y = xy
    f, lines = font(size, bold), []
    for paragraph in value.split("\n"):
        if width is None:
            lines.append(paragraph)
            continue
        line = ""
        for word in paragraph.split():
            trial = (line + " " + word).strip()
            if line and draw.textlength(trial, font=f) > width:
                lines.append(line)
                line = word
            else:
                line = trial
        lines.append(line)
    for line in lines:
        draw.text((x, y), line, font=f, fill=color)
        y += size + leading
    return y


def card(draw, y, height):
    draw.rounded_rectangle((17, y, 343, y + height), radius=20, fill=SURFACE)
    return 34, y + 17


def button(draw, x, y, width, label, primary=False, size=13, height=45):
    draw.rounded_rectangle((x, y, x + width, y + height), radius=13, fill=ACCENT if primary else LINE)
    f = font(size, True)
    tw = draw.textlength(label, font=f)
    draw.text((x + (width - tw) / 2, y + (height - size) / 2 - 2), label,
              font=f, fill=BG if primary else TEXT)


def base():
    phone = Image.new("RGB", (360, 960), BG)
    draw = ImageDraw.Draw(phone)
    write(draw, (22, 10), "10:10", 11, TEXT, True)
    write(draw, (286, 10), "●  ▰", 11, TEXT)
    write(draw, (21, 48), "MELO LAB", 24, ACCENT, True)
    write(draw, (21, 83), "РУСЛАН · МУЗЫКАЛЬНАЯ МАСТЕРСКАЯ", 9, MUTED, True)
    write(draw, (297, 54), "FL\nSTUDIO", 10, TEAL, True)
    return phone, draw


def nav(draw, selected):
    draw.rectangle((0, 885, 360, 960), fill=BG)
    for index, label in enumerate(["Идея", "Теория", "Проекты", "Google"]):
        x = 8 + 86 * index
        if index == selected:
            draw.rounded_rectangle((x, 894, x + 86, 940), radius=12, fill=SURFACE)
        f = font(12, True)
        draw.text((x + (86 - draw.textlength(label, font=f)) / 2, 908), label,
                  font=f, fill=ACCENT if index == selected else MUTED)
    draw.rounded_rectangle((130, 951, 230, 954), radius=2, fill=MUTED)


def keyboard(draw, x, y, width, height):
    white = [("C", 0), ("D", 2), ("E", 4), ("F", 5), ("G", 7), ("A", 9), ("B", 11)]
    scale = {0, 1, 3, 5, 7, 8, 10}
    key_width = width / 7
    for index, (label, pc) in enumerate(white):
        left = x + index * key_width
        draw.rectangle((left + 1, y, left + key_width - 1, y + height), fill="#F0EAF7")
        write(draw, (left + key_width / 2 - 4, y + height - 22), label, 11, "#333046")
        if pc in scale:
            xx, yy = left + key_width / 2, y + height - 35
            draw.ellipse((xx - 3, yy - 3, xx + 3, yy + 3), fill="#3F9E8D")
    for boundary, pc in [(1, 1), (2, 3), (4, 6), (5, 8), (6, 10)]:
        middle = x + boundary * key_width
        draw.rectangle((middle - key_width * .31, y, middle + key_width * .31, y + height * .62), fill="#282338")
        if pc in scale:
            yy = y + height * .62 - 12
            draw.ellipse((middle - 3, yy - 3, middle + 3, yy + 3), fill=TEAL)


def melody_grid(draw, x, y, width, height):
    labels = ["B5", "A#5", "A5", "G#5", "G5", "F#5", "F5", "E5", "D#5", "D5", "C#5", "C5"]
    left, top = x + 27, y + 15
    cw, rh = (width - 27) / 16, (height - 15) / 12
    for row, label in enumerate(labels):
        pc = (71 - row) % 12
        fill = "#24203A" if pc in {0, 1, 3, 5, 7, 8, 10} else "#191626"
        draw.rectangle((left, top + row * rh, x + width, top + (row + 1) * rh), fill=fill)
        write(draw, (x, top + row * rh), label, 8, MUTED, leading=0)
        draw.line((left, top + row * rh, x + width, top + row * rh), fill=LINE)
    for col in range(17):
        xx = left + col * cw
        draw.line((xx, top, xx, y + height), fill="#655975" if col % 4 == 0 else LINE)
        if col < 16 and col % 4 == 0:
            write(draw, (xx + 3, y), str(col // 4 + 1), 9, MUTED)
    # Visible notes of MusicData.defaults(): seed=1, first bar, C5–B5 viewport.
    # Two C6 notes at steps4/6 are above the selected octave, as in the real UI.
    for midi, start, length in [(68, 0, 4), (68, 8, 2), (67, 10, 4)]:
        row = 71 - midi
        draw.rounded_rectangle((left + start * cw + 1, top + row * rh + 1,
                                left + (start + length) * cw - 1, top + (row + 1) * rh - 1),
                               radius=2, fill=ACCENT)


def idea():
    phone, d = base()
    write(d, (17, 116), "Стартовая идея · F minor", 23, TEXT, True)
    write(d, (17, 152), "Мелодия начинается с нескольких нот.", 12, MUTED)
    x, y = card(d, 181, 140)
    button(d, x, y, 140, "Трэп ▾")
    button(d, x + 147, y, 145, "140 BPM")
    button(d, x, y + 53, 292, "F · Натуральный минор ▾")
    write(d, (x, y + 106), "F  G  G#  A#  C  C#  D#", 14, TEAL, True)
    x, y = card(d, 335, 296)
    write(d, (x, y), "Аккорды", 21, TEXT, True)
    write(d, (x, y + 31), "4 такта · 4/4 · нажми для настройки", 11, MUTED)
    chords = [("Fm", "F4 · G#4 · C5"), ("C#", "C#4 · F4 · G#4"),
              ("A#m", "A#4 · C#5 · F5"), ("D#", "D#4 · G4 · A#4")]
    for index, (name, notes) in enumerate(chords):
        yy = y + 55 + index * 33
        d.rounded_rectangle((x, yy, x + 241, yy + 28), radius=9, fill=BG)
        write(d, (x + 8, yy + 4), f"{index + 1}  {name}", 13, TEXT, True)
        write(d, (x + 76, yy + 6), notes, 10, MUTED)
        button(d, x + 248, yy, 44, "▶", height=28)
    button(d, x, y + 190, 292, "▶ Слушать идею", True)
    for index, label in enumerate(["Аккорды", "Мелодия", "Бас"]):
        xx = x + index * 99
        d.rectangle((xx + 1, y + 250, xx + 13, y + 262), fill=ACCENT)
        write(d, (xx + 19, y + 248), label, 10, MUTED)
    x, y = card(d, 645, 226)
    write(d, (x, y), "Твоя мелодия", 20, TEXT, True)
    write(d, (x, y + 31), "Такт 1 / 4 · 4 клетки = одна доля", 11, TEAL)
    melody_grid(d, x, y + 55, 292, 104)
    button(d, x, y + 170, 292, "Предложить новую мелодию", True, 12, height=39)
    nav(d, 0)
    return phone


def theory():
    phone, d = base()
    write(d, (17, 116), "Понять музыку", 27, TEXT, True)
    write(d, (17, 157), "Ноты, интервалы и гармония —\nс живыми примерами.", 12, MUTED)
    x, y = card(d, 202, 358)
    write(d, (x, y), "F · Натуральный минор", 20, TEXT, True)
    write(d, (x, y + 32), "F  G  G#  A#  C  C#  D#", 16, TEAL, True)
    write(d, (x, y + 61), "Основа многих trap и hip-hop идей;\nVI и VII дают мягкое движение.", 12, MUTED)
    keyboard(d, x, y + 110, 292, 99)
    button(d, x, y + 222, 143, "Выбрать гамму", size=11, height=42)
    button(d, x + 150, y + 222, 142, "Слушать гамму", size=11, height=42)
    button(d, x, y + 272, 292, "Аккорды этой гаммы", height=42)
    d.rectangle((x + 2, y + 326, x + 14, y + 338), outline=MUTED, width=1)
    write(d, (x + 23, y + 324), "Показывать бемоли: Db вместо C#", 10, MUTED)
    x, y = card(d, 574, 299)
    write(d, (x, y), "Собери аккорд", 21, TEXT, True)
    write(d, (x, y + 34), "Fm", 31, ACCENT, True)
    write(d, (x, y + 77), "F4 · G#4 · C5", 16, TEAL, True)
    write(d, (x, y + 107), "Интервалы: [0, 3, 7] полутонов", 11, MUTED)
    button(d, x, y + 134, 143, "Основная нота", size=11, height=40)
    button(d, x + 150, y + 134, 142, "Тип аккорда", size=11, height=40)
    button(d, x, y + 181, 143, "Обращение 0", size=11, height=40)
    button(d, x + 150, y + 181, 142, "Октава 4", size=11, height=40)
    button(d, x, y + 230, 292, "▶ Послушать аккорд", True, height=45)
    nav(d, 1)
    return phone


def google():
    phone, d = base()
    write(d, (17, 116), "Спросить Google", 27, TEXT, True)
    write(d, (17, 157), "Подготовим вопрос с твоими нотами\nи откроем режим ИИ Google.", 12, MUTED)
    x, y = card(d, 202, 460)
    write(d, (x, y), "О чём спросить?", 20, TEXT, True)
    write(d, (x + 2, y + 43), "Предложи три варианта продолжения моей гармонии для хип-хопа. Покажи состав аккордов и объясни, чем они отличаются.",
          14, TEXT, width=288)
    d.line((x, y + 161, x + 292, y + 161), fill=ACCENT, width=2)
    d.rectangle((x + 2, y + 179, x + 16, y + 193), fill=ACCENT)
    write(d, (x + 25, y + 176), "Добавить жанр, тональность\nи мои аккорды", 11, MUTED)
    button(d, x, y + 224, 292, "Выбрать пример вопроса")
    button(d, x, y + 277, 292, "Скопировать и открыть Google ИИ", True, 12)
    button(d, x, y + 330, 292, "Только скопировать вопрос", size=12)
    write(d, (x, y + 388), "Ответ откроется на странице Google.\nЕсли вопрос не подставится, вставь\nскопированный текст в строку ввода.", 11, MUTED)
    x, y = card(d, 676, 194)
    write(d, (x, y), "В контексте твоей идеи", 18, TEXT, True)
    write(d, (x, y + 35), "Трэп · 140 BPM\nF · Натуральный минор\nFm → C# → A#m → D#", 14, TEAL)
    write(d, (x, y + 108), "API-ключ и сервер не нужны.\nДля Google требуется интернет\nи доступ к режиму ИИ в аккаунте.", 11, MUTED)
    nav(d, 3)
    return phone


image = Image.new("RGB", (1320, 1260), "#090812")
draw = ImageDraw.Draw(image)
write(draw, (60, 30), "Melo Lab · Руслан", 34, ACCENT, True)
write(draw, (60, 85), "Собирай гармонию, слышь ноты и переноси идею в FL Studio.", 18, MUTED)
for x, label, phone in zip([60, 480, 900], ["ИДЕЯ И МЕЛОДИЯ", "ТЕОРИЯ В ЗВУКЕ", "ВОПРОС ДЛЯ GOOGLE"], [idea(), theory(), google()]):
    write(draw, (x, 134), label, 12, MUTED, True)
    draw.rounded_rectangle((x - 6, 164, x + 366, 1136), radius=38, fill="#453B59")
    mask = Image.new("L", (360, 960))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 359, 959), radius=32, fill=255)
    image.paste(phone, (x, 170), mask)
write(draw, (60, 1170), "Иллюстрация интерфейса, не снимок с телефона. Длинные экраны с прокруткой сокращены, чтобы показать основные элементы вместе.",
      13, MUTED, width=1200)
write(draw, (60, 1210), "Стартовая гармония и видимые ноты взяты из исходников. Ответ Google остаётся в браузере.", 13, MUTED)
path = ROOT / "docs" / "interface-preview.png"
path.parent.mkdir(parents=True, exist_ok=True)
image.save(path)
print(path)
