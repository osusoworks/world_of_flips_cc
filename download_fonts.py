import urllib.request
import os

font_dir = r"c:\Users\sr44w\Desktop\WOF_D\app\src\main\res\font"
os.makedirs(font_dir, exist_ok=True)

urllib.request.urlretrieve(
    'https://github.com/google/fonts/raw/main/ofl/kosugimaru/KosugiMaru-Regular.ttf',
    os.path.join(font_dir, 'kosugi_maru.ttf')
)

urllib.request.urlretrieve(
    'https://github.com/google/fonts/raw/main/ofl/dotgothic16/DotGothic16-Regular.ttf',
    os.path.join(font_dir, 'dotgothic16.ttf')
)
print("Fonts downloaded successfully.")
