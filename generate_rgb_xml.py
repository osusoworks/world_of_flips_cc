import random

def generate_rgb_xml():
    width = 1080
    height = 1000 # Covers roughly half screen
    
    xml_header = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width}dp"
    android:height="{height}dp"
    android:viewportWidth="{width}"
    android:viewportHeight="{height}">
'''
    xml_footer = "</vector>"
    
    paths = []
    
    # Colors: R, G, B, and some mixes
    colors = [
        "#FFFF0000", # Red
        "#FF00FF00", # Green
        "#FF0000FF", # Blue
        "#FF00FFFF", # Cyan
        "#FFFF00FF", # Magenta
        "#FFFFFF00", # Yellow
        "#FF880000", # Darker Red
        "#FF008800", # Darker Green
        "#FF000088", # Darker Blue
    ]
    
    # Generate random vertical stripes
    # We want to cover significant portion but leave gaps (transparent)
    
    current_x = 0
    while current_x < width:
        # Decide if we draw a line or a gap
        if random.random() < 0.7: # 70% chance of line
             # Line width
             line_w = random.randint(1, 20)
             if random.random() < 0.1: line_w = random.randint(20, 50) # Occasional thick band
             
             color = random.choice(colors)
             # Add alpha sometimes? No, requested "RGB", usually solid or additive. 
             # But user said "White parts are transparent". So we just don't draw on some parts.
             
             path = f'''    <path
        android:fillColor="{color}"
        android:pathData="M{current_x},0 L{current_x + line_w},0 L{current_x + line_w},{height} L{current_x},{height} Z" />'''
             paths.append(path)
             
             current_x += line_w
        else:
            # Gap
            gap_w = random.randint(2, 20)
            current_x += gap_w
            
    return xml_header + "\n".join(paths) + "\n" + xml_footer

if __name__ == "__main__":
    print(generate_rgb_xml())
