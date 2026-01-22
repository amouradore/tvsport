import zipfile
import sys

def inspect_zip(path):
    try:
        with zipfile.ZipFile(path, 'r') as z:
            print(f"Inspecting {path}...")
            # Print first 20 file likely to be python scripts or packages
            count = 0
            for info in z.infolist():
                if "acestream" in info.filename or info.filename.endswith(".py"):
                    print(info.filename)
                    count += 1
                    if count > 50:
                        break
            if count == 0:
                print("No obvious python files found.")
                # Print first 10 anyway
                for info in z.infolist()[:10]:
                    print(f"Generic: {info.filename}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python zip_inspector.py <zipfile>")
        sys.exit(1)
    inspect_zip(sys.argv[1])
