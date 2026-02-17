
import random
import math

def generate_cracked_xml():
    width = 1080
    height = 1920
    center_x = 540  # Center of screen as requested "across entire screen" usually implies a central burst or covering everything
    center_y = 960
    
    # However, to look like a realistic impact, maybe slightly off-center? 
    # Let's stick to center for "radial cracks spreading across the entire screen" usually implies symmetry or full coverage.
    # Actually, let's offset slightly for realism: (400, 700)
    center_x = 600
    center_y = 800

    xml_header = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width}dp"
    android:height="{height}dp"
    android:viewportWidth="{width}"
    android:viewportHeight="{height}">
'''
    
    paths = []
    
    # Colors
    stroke_color = "#FFFFFF" # White cracks
    
    # 1. Radial Cracks
    num_radials = 25
    radial_paths = []
    
    radials_angles = sorted([random.uniform(0, 2*math.pi) for _ in range(num_radials)])
    
    # Store points for web connections
    # list of lists of (x,y)
    ray_points = [] 

    for theta in radials_angles:
        current_x, current_y = center_x, center_y
        points = [(current_x, current_y)]
        
        # Extend until off screen
        # We step in chunks to allow jaggedness
        step_len = random.randint(50, 150)
        
        while 0 <= current_x <= width and 0 <= current_y <= height:
            # Wiggle angle slightly
            wobble = random.uniform(-0.1, 0.1) 
            curr_theta = theta + wobble
            
            dx = math.cos(curr_theta) * step_len
            dy = math.sin(curr_theta) * step_len
            
            current_x += dx
            current_y += dy
            
            points.append((current_x, current_y))
            
            if len(points) > 20: break # Safety break
        
        ray_points.append(points)
        
        # Build path string
        d = f"M{points[0][0]},{points[0][1]}"
        for p in points[1:]:
            d += f" L{p[0]:.1f},{p[1]:.1f}"
        
        radial_paths.append(d)

    # 2. Concentric "Web" Cracks
    # Connect adjacent rays at random intervals
    web_paths = []
    for i in range(len(ray_points)):
        ray_a = ray_points[i]
        ray_b = ray_points[(i + 1) % len(ray_points)] # Wrap around
        
        # Try to connect corresponding segments roughly
        # This is a simplification. A real web connects arbitrary points.
        # Let's just iterate along ray_a and try to connect to ray_b
        
        min_len = min(len(ray_a), len(ray_b))
        for j in range(1, min_len):
            if random.random() < 0.7: # 70% chance to connect at this 'level'
                # Connect ray_a[j] to ray_b[j] or ray_b[j-1] or ray_b[j+1]
                idx_b = j + random.randint(-1, 0)
                if idx_b < 1: idx_b = 1
                if idx_b >= len(ray_b): idx_b = len(ray_b) - 1
                
                p1 = ray_a[j]
                p2 = ray_b[idx_b]
                
                # Add a midpoint jitter for more jaggedness?
                # For now straight line between rays
                d = f"M{p1[0]:.1f},{p1[1]:.1f} L{p2[0]:.1f},{p2[1]:.1f}"
                web_paths.append(d)

    # Combine all paths into fewer path elements to reduce file size / complexity
    # We can handle maybe all radials in one path, all webs in another
    
    full_radial_d = " ".join(radial_paths)
    full_web_d = " ".join(web_paths)
    
    paths.append(f'''    <path
        android:strokeColor="{stroke_color}"
        android:strokeWidth="1.5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="{full_radial_d}" />''')

    paths.append(f'''    <path
        android:strokeColor="{stroke_color}"
        android:strokeWidth="1.0"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeAlpha="0.8"
        android:pathData="{full_web_d}" />''')
        
    # Add some random chaotic scratches
    scratch_paths = []
    for _ in range(10):
        # Random start
        sx = random.uniform(0, width)
        sy = random.uniform(0, height)
        points = [(sx,sy)]
        for _ in range(random.randint(3,6)):
            sx += random.uniform(-100, 100)
            sy += random.uniform(-100, 100)
            points.append((sx, sy))
        
        d = f"M{points[0][0]:.1f},{points[0][1]:.1f}"
        for p in points[1:]:
            d += f" L{p[0]:.1f},{p[1]:.1f}"
        scratch_paths.append(d)
        
    full_scratch_d = " ".join(scratch_paths)
    paths.append(f'''    <path
        android:strokeColor="{stroke_color}"
        android:strokeWidth="0.5"
        android:strokeAlpha="0.6"
        android:pathData="{full_scratch_d}" />''')

    xml_footer = "</vector>"
    
    xml_content = xml_header + "\n".join(paths) + "\n" + xml_footer
    
    with open(r'c:\Users\sr44w\Desktop\orimekun\app\src\main\res\drawable\crease_cracked.xml', 'w', encoding='utf-8') as f:
        f.write(xml_content)
    print("File written successfully.")

if __name__ == "__main__":
    generate_cracked_xml()
