package com.bookstore.functioncalling;

import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BookstoreToolsTest {

    static final List<Book> BOOKS = List.of(
            book("深入理解计算机系统", "12345", "99.00", 100),
            book("算法导论", "99901", "77.00", 80),
            book("设计模式", "9787111075752", "66.00", 3));

    private final ObjectMapper mapper = new ObjectMapper();
    private BookstoreTools tools;

    static Book book(String title, String isbn, String price, int stock) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("作者");
        book.setIsbn(isbn);
        book.setPrice(new BigDecimal(price));
        book.setStock(stock);
        return book;
    }

    static BookRepository mockRepository() {
        BookRepository repository = mock(BookRepository.class);
        when(repository.findAll()).thenReturn(BOOKS);
        when(repository.findFirstByIsbn(anyString())).thenAnswer(invocation -> BOOKS.stream()
                .filter(b -> b.getIsbn().equals(invocation.getArgument(0)))
                .findFirst());
        return repository;
    }

    @BeforeEach
    void setUp() {
        tools = new BookstoreTools(mockRepository(), mapper, 200, 500);
    }

    @Test
    void toolSchemasDeclareRequiredStringIsbn() {
        ArrayNode schemas = ToolDefinitions.tools(mapper);
        assertEquals(2, schemas.size());
        for (var tool : schemas) {
            var function = tool.path("function");
            assertFalse(function.path("description").asText().isBlank());
            assertEquals("string", function.at("/parameters/properties/isbn/type").asText());
            assertEquals("isbn", function.at("/parameters/required/0").asText());
        }
    }

    @Test
    void checkInventoryReturnsStock() {
        ObjectNode result = tools.execute("check_inventory", "{\"isbn\":\"12345\"}", new ToolCallContext());
        assertTrue(result.path("success").asBoolean());
        assertEquals(100, result.path("stock").asInt());
        assertEquals("有货", result.path("stock_status").asText());
    }

    @Test
    void isbnWithHyphensIsNormalized() {
        ObjectNode result = tools.execute("check_inventory", "{\"isbn\":\"978-7-111-07575-2\"}", new ToolCallContext());
        assertTrue(result.path("success").asBoolean());
        assertEquals("库存紧张", result.path("stock_status").asText());
    }

    @Test
    void invalidIsbnReturnsStructuredError() {
        ObjectNode result = tools.execute("check_inventory", "{\"isbn\":\"12a45\"}", new ToolCallContext());
        assertFalse(result.path("success").asBoolean());
        assertEquals("INVALID_ISBN", result.path("error").asText());
        assertFalse(result.path("hint").asText().isBlank());
    }

    @Test
    void unknownIsbnSuggestsSimilarOnes() {
        ObjectNode result = tools.execute("check_inventory", "{\"isbn\":\"12354\"}", new ToolCallContext());
        assertEquals("BOOK_NOT_FOUND", result.path("error").asText());
        assertEquals("12345", result.path("similar_isbns").path(0).asText());
    }

    @Test
    void competitorPriceReturnsThreeQuotes() {
        ObjectNode result = tools.execute("get_competitor_price", "{\"isbn\":\"12345\"}", new ToolCallContext());
        assertTrue(result.path("success").asBoolean());
        assertEquals(3, result.path("competitors").size());
        for (var quote : result.path("competitors")) {
            double price = quote.path("price").asDouble();
            assertTrue(price >= 99 * 0.8 && price <= 99 * 1.1, "price out of range: " + price);
        }
    }

    @Test
    void slowUpstreamTimesOutOnFirstAttemptThenSucceeds() {
        ToolCallContext context = new ToolCallContext();
        ObjectNode first = tools.execute("get_competitor_price", "{\"isbn\":\"99901\"}", context);
        assertEquals("UPSTREAM_TIMEOUT", first.path("error").asText());
        assertTrue(first.path("retryable").asBoolean());

        ObjectNode retry = tools.execute("get_competitor_price", "{\"isbn\":\"99901\"}", context);
        assertTrue(retry.path("success").asBoolean());
        assertEquals(2, retry.path("attempt").asInt());
    }

    @Test
    void malformedArgumentsAndUnknownFunctionAreReported() {
        ToolCallContext context = new ToolCallContext();
        assertEquals("INVALID_ARGUMENTS", tools.execute("check_inventory", "{isbn:", context).path("error").asText());
        assertEquals("MISSING_ARGUMENT", tools.execute("check_inventory", "{}", context).path("error").asText());
        assertEquals("UNKNOWN_FUNCTION", tools.execute("delete_book", "{\"isbn\":\"12345\"}", context).path("error").asText());
    }

    @Test
    void editDistanceCountsTranspositionAsOne() {
        assertEquals(1, BookstoreTools.editDistance("12345", "12354"));
        assertEquals(0, BookstoreTools.editDistance("12345", "12345"));
        assertEquals(2, BookstoreTools.editDistance("12345", "13354"));
    }
}
