import random

def generate_rgb_xml():
    width = 1080
    height = 1000 
    
    xml_header = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width}dp"
    android:height="{height}dp"
    android:viewportWidth="{width}"
    android:viewportHeight="{height}">
'''
    xml_footer = "</vector>"
    
    paths = []
    
    colors = [
        "#FFFF0000", "#FF00FF00", "#FF0000FF", 
        "#FF00FFFF", "#FFFF00FF", "#FFFFFF00", 
        "#FF880000", "#FF008800", "#FF000088",
    ]
    
    current_x = 0
    while current_x < width:
        if random.random() < 0.7: 
             line_w = random.randint(1, 20)
             if random.random() < 0.1: line_w = random.randint(20, 50) 
             
             color = random.choice(colors)
             
             path = f'''    <path
        android:fillColor="{color}"
        android:pathData="M{current_x},0 L{current_x + line_w},0 L{current_x + line_w},{height} L{current_x},{height} Z" />'''
             paths.append(path)
             
             current_x += line_w
        else:
            gap_w = random.randint(2, 20)
            current_x += gap_w
            
    content = xml_header + "\n".join(paths) + "\n" + xml_footer
    with open("crease_rgb_gen.xml", "w", encoding="utf-8") as f:
        f.write(content)

if __name__ == "__main__":
    generate_rgb_xml()
