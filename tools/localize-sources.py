"""Compatibility entry point; language generation never edits Java sources."""
from pathlib import Path
import runpy

if __name__ == "__main__":
    runpy.run_path(str(Path(__file__).with_name("generate-languages.py")), run_name="__main__")
