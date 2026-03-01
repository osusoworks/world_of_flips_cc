import shutil

source = r"C:\Users\sr44w\.gemini\antigravity\brain\a3bd2ac3-b28d-469b-acdb-8b713c4c5878\uploaded_image_1765515665478.png"
dest = r"c:\Users\sr44w\Desktop\1212aWOF\app\src\main\res\drawable\ic_launcher_custom.png"

shutil.copy2(source, dest)
print(f"Copied icon to: {dest}")
