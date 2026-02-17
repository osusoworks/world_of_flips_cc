from PIL import Image
import os

# 元画像のパス
source_image = r"C:/Users/sr44w/.gemini/antigravity/brain/a3bd2ac3-b28d-469b-acdb-8b713c4c5878/uploaded_image_1765515665478.png"

# プロジェクトのベースパス
base_path = r"c:\Users\sr44w\Desktop\1212aWOF\app\src\main\res"

# 各解像度とサイズの定義
icon_sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

# 元画像を開く
try:
    img = Image.open(source_image)
    print(f"元画像を読み込みました: {img.size}")
    
    # RGBAモードに変換（透明度を保持）
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
    
    # 各解像度のアイコンを生成
    for folder, size in icon_sizes.items():
        # フォルダパス
        folder_path = os.path.join(base_path, folder)
        
        # フォルダが存在しない場合は作成
        if not os.path.exists(folder_path):
            os.makedirs(folder_path)
        
        # 画像をリサイズ
        resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # 通常のアイコンを保存
        output_path = os.path.join(folder_path, "ic_launcher.png")
        resized_img.save(output_path, "PNG")
        print(f"生成: {output_path} ({size}x{size})")
        
        # ラウンドアイコンも同じものを保存
        output_path_round = os.path.join(folder_path, "ic_launcher_round.png")
        resized_img.save(output_path_round, "PNG")
        print(f"生成: {output_path_round} ({size}x{size})")
    
    print("\n✓ すべてのアイコンを生成しました！")
    
except Exception as e:
    print(f"エラーが発生しました: {e}")
    import traceback
    traceback.print_exc()
