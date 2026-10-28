#!/usr/bin/env python3
"""Compatibility entrypoint: package the current integrated assignment implementation."""
from pathlib import Path
import runpy

if __name__ == '__main__':
    runpy.run_path(str(Path(__file__).resolve().parents[2] / 'BookStore_s4/scripts/package_assignment4.py'), run_name='__main__')
