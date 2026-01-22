import sys

def get_strings(filename, min_len=4):
    with open(filename, "rb") as f:
        content = f.read()
    
    result = ""
    for byte in content:
        char = chr(byte)
        if 32 <= byte <= 126: # Printable ASCII
            result += char
        else:
            if len(result) >= min_len:
                yield result
            result = ""
    if len(result) >= min_len:
        yield result

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python jni_scanner.py <file>")
        sys.exit(1)
        
    target_file = sys.argv[1]
    print(f"Scanning {target_file}...")
    
    for s in get_strings(target_file):
        if "Java_" in s or "org/acestream" in s or "start" in s and "Engine" in s:
            print(s)
