import json
from pathlib import Path
from fastmcp import FastMCP

mcp = FastMCP("BookStore")
DATA_PATH = Path(__file__).parent.parent.parent / "data" / "books.json"


def load_books() -> list[dict]:
    try:
        with open(DATA_PATH, "r") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return []


def save_books(books):
    try:
        DATA_PATH.parent.mkdir(parents=True, exist_ok=True)
        with open(DATA_PATH, "w") as f:
            json.dump(books, f, indent=4)
        return True
    except Exception:
        return False


@mcp.tool()
def get_all_books():
    """Get all books."""
    return load_books()


@mcp.tool()
def get_book_by_id(book_id: int):
    """Get book by ID."""
    books = load_books()
    return next((book for book in books if book["id"] == book_id), None)


@mcp.tool()
def search_books_by_title(title_query: str):
    """Search books by title."""
    books = load_books()
    return [book for book in books if title_query.lower() in book["title"].lower()]


@mcp.tool()
def search_books_by_author(author_query: str):
    """Search books by author."""
    books = load_books()
    return [book for book in books if author_query.lower() in book["author"].lower()]


@mcp.tool()
def get_books_in_stock():
    """Get books with count > 0."""
    return [book for book in load_books() if book["count"] > 0]


@mcp.tool()
def buy_book(book_id: int, quantity: int = 1):
    """Buy a book."""
    if quantity <= 0:
        return {"success": False, "error": "Quantity must be > 0"}

    books = load_books()
    book = next((b for b in books if b["id"] == book_id), None)

    if not book:
        return {"success": False, "error": f"Book {book_id} not found"}

    if book["count"] < quantity:
        return {"success": False, "error": f"Only {book['count']} available"}

    book["count"] -= quantity

    if save_books(books):
        return {
            "success": True,
            "message": f"Purchased {quantity} of '{book['title']}'",
        }
    else:
        return {"success": False, "error": "Save failed"}


@mcp.tool()
def check_availability(book_id: int, quantity: int = 1):
    """Check if book is available."""
    books = load_books()
    book = next((b for b in books if b["id"] == book_id), None)

    if not book:
        return {"available": False, "error": f"Book {book_id} not found"}

    return {
        "book_id": book_id,
        "title": book["title"],
        "available_count": book["count"],
        "available": book["count"] >= quantity,
    }


@mcp.tool()
def restock_book(book_id: int, quantity: int):
    """Add stock to a book."""
    if quantity <= 0:
        return {"success": False, "error": "Quantity must be > 0"}

    books = load_books()
    book = next((b for b in books if b["id"] == book_id), None)

    if not book:
        return {"success": False, "error": f"Book {book_id} not found"}

    book["count"] += quantity

    if save_books(books):
        return {
            "success": True,
            "message": f"Restocked {quantity} of '{book['title']}'",
        }
    else:
        return {"success": False, "error": "Save failed"}
