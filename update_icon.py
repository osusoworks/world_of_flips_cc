import sys
print("Starting icon generation...")

try:
    from PIL import Image
    print("PIL imported successfully")
except ImportError:
    print("ERROR: PIL not found. Installing pillow...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pillow"])
    from PIL import Image
    print("PIL installed and imported")

import os

source = r"C:\Users\sr44w\.gemini\antigravity\brain\a3bd2ac3-b28d-469b-acdb-8b713c4c5878\uploaded_image_1765515665478.png"
base = r"c:\Users\sr44w\Desktop\1212aWOF\app\src\main\res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

print(f"Opening source image: {source}")
img = Image.open(source)
print(f"Image size: {img.size}, mode: {img.mode}")

if img.mode != 'RGBA':
    img = img.convert('RGBA')
    print("Converted to RGBA")

for folder, size in sizes.items():
    folder_path = os.path.join(base, folder)
    os.makedirs(folder_path, exist_ok=True)
    
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    
    out1 = os.path.join(folder_path, "ic_launcher.png")
    resized.save(out1, "PNG")
    print(f"Saved: {out1}")
    
    out2 = os.path.join(folder_path, "ic_launcher_round.png")
    resized.save(out2, "PNG")
    print(f"Saved: {out2}")

print("\nDone! All icons generated successfully.")
