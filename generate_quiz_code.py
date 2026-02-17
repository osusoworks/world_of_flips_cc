import re
import os

def parse_quiz(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()

    blocks = content.split('---')
    questions = []

    for block in blocks:
        block = block.strip()
        if not block: continue
        
        lines = block.split('\n')
        q_text = ""
        options = ["", "", "", ""]
        correct_str = ""
        
        for line in lines:
            line = line.strip()
            if line.startswith("Q:"):
                q_text = line[2:].strip()
            elif line.startswith("A1:"):
                options[0] = line[3:].strip()
            elif line.startswith("A2:"):
                options[1] = line[3:].strip()
            elif line.startswith("A3:"):
                options[2] = line[3:].strip()
            elif line.startswith("A4:"):
                options[3] = line[3:].strip()
            elif line.startswith("CORRECT:"):
                correct_str = line.split(":", 1)[1].strip()
        
        if q_text and all(options):
            correct_indices = []
            for digit in correct_str:
                if digit.isdigit():
                    correct_indices.append(int(digit) - 1)
            
            # Escape quotes and backslashes in strings
            q_text = q_text.replace('\\', '\\\\').replace('"', '\\"')
            options = [opt.replace('\\', '\\\\').replace('"', '\\"') for opt in options]
            
            questions.append({
                "text": q_text,
                "options": options,
                "correct": correct_indices
            })
    
    return questions

def generate_kotlin(questions):
    lines = []
    lines.append("package com.example.orimekun")
    lines.append("")
    lines.append("data class Question(")
    lines.append("    val text: String,")
    lines.append("    val options: List<String>,")
    lines.append("    val correctIndices: List<Int>")
    lines.append(")")
    lines.append("")
    lines.append("object QuizData {")
    lines.append("    val allQuestions = listOf(")
    
    for q in questions:
        opts = ", ".join([f'"{opt}"' for opt in q['options']])
        cor = ", ".join([str(c) for c in q['correct']])
        lines.append(f'        Question("{q["text"]}", listOf({opts}), listOf({cor})),')
        
    lines.append("    )")
    lines.append("}")
    return "\n".join(lines)

if __name__ == "__main__":
    print("Starting processing...")
    try:
        qs = parse_quiz("c:/Users/sr44w/Desktop/0104a/quiz_raw.txt")
        print(f"Parsed {len(qs)} questions.")
        kotlin_code = generate_kotlin(qs)
        output_path = "c:/Users/sr44w/Desktop/0104a/app/src/main/java/com/example/orimekun/QuizData.kt"
        print(f"Writing to {output_path}...")
        with open(output_path, "w", encoding='utf-8') as f:
            f.write(kotlin_code)
        print("Done.")
    except Exception as e:
        print(f"Error: {e}")
