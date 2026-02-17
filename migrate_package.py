import os
import shutil

base_path = r"c:\Users\sr44w\Desktop\wof\app\src"
old_pkg = "com.example.orimekun"
new_pkg = "com.worldofflips.app"
old_path = old_pkg.replace(".", os.sep)
new_path = new_pkg.replace(".", os.sep)

# Create new directories
for root_dir in ["main", "test", "androidTest"]:
    src_dir = os.path.join(base_path, root_dir, "java", old_path)
    dst_dir = os.path.join(base_path, root_dir, "java", new_path)
    
    if os.path.exists(src_dir):
        print(f"Copying {src_dir} to {dst_dir}")
        os.makedirs(dst_dir, exist_ok=True)
        for item in os.listdir(src_dir):
            s = os.path.join(src_dir, item)
            d = os.path.join(dst_dir, item)
            if os.path.isdir(s):
                shutil.copytree(s, d, dirs_exist_ok=True)
            else:
                shutil.copy2(s, d)

# Update package names in all kotlin files
for root, dirs, files in os.walk(os.path.join(base_path)):
    for file in files:
        if file.endswith(".kt"):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = content.replace(old_pkg, new_pkg)
            
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                print(f"Updated: {filepath}")

# Update layout XML files
layout_dir = os.path.join(base_path, "main", "res", "layout")
for file in os.listdir(layout_dir):
    if file.endswith(".xml"):
        filepath = os.path.join(layout_dir, file)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        new_content = content.replace(old_pkg, new_pkg)
        
        if new_content != content:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print(f"Updated: {filepath}")

# Delete old package directories
for root_dir in ["main", "test", "androidTest"]:
    old_dir = os.path.join(base_path, root_dir, "java", old_path)
    if os.path.exists(old_dir):
        print(f"Removing old directory: {old_dir}")
        shutil.rmtree(old_dir)

print("Done!")
