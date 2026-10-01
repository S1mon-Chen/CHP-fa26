package com.bookstore.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RAGService {

    @Autowired
    private BookService bookService;

    private static final String CHROMA_URL = "http://localhost:8000/api/v2";
    private static final String COLLECTION_NAME = "books";
    private static final String TENANT = "default_tenant";
    private static final String DATABASE = "default_database";
    private final HttpClient httpClient;
    private boolean chromaAvailable;
    private List<BookVector> bookVectors;

    private String collectionId; // 存储集合 ID

    public RAGService() {
        this.httpClient = HttpClient.newHttpClient();
        this.chromaAvailable = false;
        this.bookVectors = new ArrayList<>();
        this.collectionId = null;
        try {
            checkChromaAvailability();
        } catch (Exception e) {
            System.out.println("Chroma不可用，将使用内存实现: " + e.getMessage());
        }
    }

    private void checkChromaAvailability() throws IOException, InterruptedException {
        String url = String.format("%s/tenants/%s/databases/%s/collections", CHROMA_URL, TENANT, DATABASE);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            chromaAvailable = true;
            ensureCollectionExists();
        } else {
            System.out.println("Chroma不可用，状态码: " + response.statusCode());
        }
    }

    private void ensureCollectionExists() throws IOException, InterruptedException {
        String url = String.format("%s/tenants/%s/databases/%s/collections", CHROMA_URL, TENANT, DATABASE);
        
        // 先检查集合是否存在
        HttpRequest checkRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> checkResponse = httpClient.send(checkRequest, HttpResponse.BodyHandlers.ofString());
        if (checkResponse.statusCode() == 200) {
            String responseBody = checkResponse.body();
            if (responseBody.contains("\"name\":\"" + COLLECTION_NAME + "\"")) {
                // 提取集合 ID
                int idStart = responseBody.indexOf("\"id\":\"", responseBody.indexOf("\"name\":\"" + COLLECTION_NAME + "\""));
                if (idStart > 0) {
                    idStart += 6; // 跳过 "id":" 前缀
                    int idEnd = responseBody.indexOf("\"", idStart);
                    if (idEnd > idStart) {
                        collectionId = responseBody.substring(idStart, idEnd);
                        System.out.println("找到Chroma集合，ID: " + collectionId);
                    }
                }
            }
            
            if (collectionId == null) {
                // 创建新集合
                Map<String, Object> createBody = new HashMap<>();
                createBody.put("name", COLLECTION_NAME);
                createBody.put("dimension", 3); // 指定向量维度
                createBody.put("get_or_create", true);
                
                HttpRequest createRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.ofString(mapToJson(createBody)))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build();
                
                HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
                if (createResponse.statusCode() == 200 || createResponse.statusCode() == 201) {
                    String createResponseBody = createResponse.body();
                    int idStart = createResponseBody.indexOf("\"id\":\"");
                    if (idStart > 0) {
                        idStart += 6;
                        int idEnd = createResponseBody.indexOf("\"", idStart);
                        if (idEnd > idStart) {
                            collectionId = createResponseBody.substring(idStart, idEnd);
                            System.out.println("成功创建Chroma集合: " + COLLECTION_NAME + "，ID: " + collectionId);
                        }
                    }
                } else {
                    System.out.println("创建Chroma集合失败: " + createResponse.body());
                }
            }
        }
    }

    public void initialize() {
        try {
            var books = bookService.getAllBooks();

            if (chromaAvailable) {
                List<String> documents = new ArrayList<>();
                List<List<Float>> embeddings = new ArrayList<>();
                List<String> ids = new ArrayList<>();
                List<Map<String, Object>> metadatas = new ArrayList<>();

                for (var book : books) {
                    String id = book.getId().toString();
                    String document = book.getTitle() + " " + book.getAuthor() + " " + book.getDescription();
                    List<Float> embedding = generateEmbedding(document);
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("id", book.getId());
                    metadata.put("title", book.getTitle());
                    metadata.put("author", book.getAuthor());
                    metadata.put("price", book.getPrice().doubleValue());

                    ids.add(id);
                    documents.add(document);
                    embeddings.add(embedding);
                    metadatas.add(metadata);
                }

                addToChroma(ids, documents, embeddings, metadatas);
                System.out.println("成功向Chroma添加 " + books.size() + " 本书籍");
            } else {
                bookVectors.clear();
                for (var book : books) {
                    BookVector vector = new BookVector();
                    vector.setId(book.getId());
                    vector.setTitle(book.getTitle());
                    vector.setAuthor(book.getAuthor());
                    vector.setDescription(book.getDescription());
                    vector.setPrice(book.getPrice().doubleValue());
                    vector.setEmbedding(generateEmbedding(book.getTitle() + " " + book.getAuthor() + " " + book.getDescription()));
                    bookVectors.add(vector);
                }
                System.out.println("成功在内存中初始化 " + bookVectors.size() + " 本书籍的向量");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("RAG初始化失败: " + e.getMessage());
            // 回退到内存实现
            try {
                var books = bookService.getAllBooks();
                bookVectors.clear();
                for (var book : books) {
                    BookVector vector = new BookVector();
                    vector.setId(book.getId());
                    vector.setTitle(book.getTitle());
                    vector.setAuthor(book.getAuthor());
                    vector.setDescription(book.getDescription());
                    vector.setPrice(book.getPrice().doubleValue());
                    vector.setEmbedding(generateEmbedding(book.getTitle() + " " + book.getAuthor() + " " + book.getDescription()));
                    bookVectors.add(vector);
                }
                System.out.println("回退到内存实现，成功初始化 " + bookVectors.size() + " 本书籍的向量");
            } catch (Exception ex) {
                ex.printStackTrace();
                System.out.println("内存初始化也失败: " + ex.getMessage());
            }
        }
    }

    private void addToChroma(List<String> ids, List<String> documents,
                             List<List<Float>> embeddings, List<Map<String, Object>> metadatas)
            throws IOException, InterruptedException {
        if (collectionId == null) {
            throw new RuntimeException("Chroma集合ID未初始化");
        }
        
        // 使用正确的 Chroma v2 API 格式
        String url = String.format("%s/tenants/%s/databases/%s/collections/%s/add", 
                                  CHROMA_URL, TENANT, DATABASE, collectionId);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ids", ids);
        requestBody.put("documents", documents);
        requestBody.put("embeddings", embeddings);
        requestBody.put("metadatas", metadatas);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(mapToJson(requestBody)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            String errorMessage = response.body() != null ? response.body() : "无错误信息";
            System.out.println("向Chroma添加数据失败: 状态码=" + response.statusCode() + ", 错误=" + errorMessage);
            throw new RuntimeException("Chroma添加数据失败: " + errorMessage);
        }
        System.out.println("成功向Chroma添加 " + ids.size() + " 条数据");
    }

    private List<Float> generateEmbedding(String text) {
        List<Float> embedding = new ArrayList<>();
        Random random = new Random(text.hashCode());
        for (int i = 0; i < 3; i++) {
            embedding.add(random.nextFloat() * 2 - 1);
        }
        return embedding;
    }

    public List<Map<String, Object>> searchBooks(String query, int k) {
        try {
            if (chromaAvailable && collectionId != null) {
                List<Float> queryEmbedding = generateEmbedding(query);

                // 使用正确的 Chroma v2 API 格式
                String url = String.format("%s/tenants/%s/databases/%s/collections/%s/query", 
                                          CHROMA_URL, TENANT, DATABASE, collectionId);

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("query_embeddings", Collections.singletonList(queryEmbedding));
                requestBody.put("n_results", k);
                requestBody.put("include", Arrays.asList("documents", "metadatas", "distances"));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.ofString(mapToJson(requestBody)))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseChromaResponse(response.body());
                } else {
                    System.out.println("Chroma搜索失败: " + response.body());
                    return searchBooksInMemory(query, k);
                }
            } else {
                return searchBooksInMemory(query, k);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return searchBooksInMemory(query, k);
        }
    }

    private List<Map<String, Object>> searchBooksInMemory(String query, int k) {
        try {
            if (bookVectors.isEmpty()) {
                initialize();
            }

            List<Float> queryEmbedding = generateEmbedding(query);

            List<Map<String, Object>> results = bookVectors.stream()
                .map(vector -> {
                    float similarity = calculateSimilarity(queryEmbedding, vector.getEmbedding());
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", vector.getId());
                    result.put("title", vector.getTitle());
                    result.put("author", vector.getAuthor());
                    result.put("price", vector.getPrice());
                    result.put("score", similarity);
                    return result;
                })
                .sorted((a, b) -> {
                    Number scoreA = (Number) a.get("score");
                    Number scoreB = (Number) b.get("score");
                    return Double.compare(scoreB.doubleValue(), scoreA.doubleValue());
                })
                .limit(k)
                .collect(Collectors.toList());

            return results;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private float calculateSimilarity(List<Float> embedding1, List<Float> embedding2) {
        float dotProduct = 0;
        float norm1 = 0;
        float norm2 = 0;

        for (int i = 0; i < embedding1.size(); i++) {
            dotProduct += embedding1.get(i) * embedding2.get(i);
            norm1 += embedding1.get(i) * embedding1.get(i);
            norm2 += embedding2.get(i) * embedding2.get(i);
        }

        norm1 = (float) Math.sqrt(norm1);
        norm2 = (float) Math.sqrt(norm2);

        if (norm1 == 0 || norm2 == 0) {
            return 0;
        }

        return dotProduct / (norm1 * norm2);
    }

    private List<Map<String, Object>> parseChromaResponse(String responseBody) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            int metadatasStart = responseBody.indexOf("\"metadatas\":");
            int distancesStart = responseBody.indexOf("\"distances\":");

            if (metadatasStart > 0 && distancesStart > 0) {
                int arrayStart = responseBody.indexOf("[", metadatasStart);
                int arrayEnd = responseBody.indexOf("]", arrayStart);
                String metadatasArray = responseBody.substring(arrayStart, arrayEnd + 1);

                int distArrayStart = responseBody.indexOf("[", distancesStart);
                int distArrayEnd = responseBody.indexOf("]", distArrayStart);
                String distancesArray = responseBody.substring(distArrayStart, distArrayEnd + 1);

                String[] metadataObjects = metadatasArray.substring(1, metadatasArray.length() - 1).split("\\},\\{");
                String[] distanceObjects = distancesArray.substring(1, distancesArray.length() - 1).split(",");

                for (int i = 0; i < metadataObjects.length; i++) {
                    Map<String, Object> result = new HashMap<>();
                    String metadataObj = metadataObjects[i].replace("{", "").replace("}", "");
                    String[] keyValuePairs = metadataObj.split(",");
                    for (String pair : keyValuePairs) {
                        String[] parts = pair.split(":");
                        if (parts.length >= 2) {
                            String key = parts[0].trim().replace("\"", "");
                            String value = parts[1].trim().replace("\"", "");
                            if ("id".equals(key)) {
                                result.put(key, Long.parseLong(value));
                            } else if ("price".equals(key)) {
                                result.put(key, Double.parseDouble(value));
                            } else {
                                result.put(key, value);
                            }
                        }
                    }

                    if (i < distanceObjects.length) {
                        String dist = distanceObjects[i].trim();
                        result.put("score", 1.0 - Double.parseDouble(dist));
                    }

                    if (!result.isEmpty()) {
                        results.add(result);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    public void updateBookVectors() {
        initialize();
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Number) {
                sb.append(value);
            } else if (value instanceof List) {
                sb.append(listToJson((List<?>) value));
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                sb.append(mapToJson(mapValue));
            } else if (value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(value).append("\"");
            }
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    private String listToJson(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Object item = list.get(i);
            if (item instanceof String) {
                sb.append("\"").append(item).append("\"");
            } else if (item instanceof Number) {
                sb.append(item);
            } else if (item instanceof List) {
                sb.append(listToJson((List<?>) item));
            } else if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapItem = (Map<String, Object>) item;
                sb.append(mapToJson(mapItem));
            } else {
                sb.append("\"").append(item).append("\"");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static class BookVector {
        private Long id;
        private String title;
        private String author;
        private String description;
        private Double price;
        private List<Float> embedding;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }

        public List<Float> getEmbedding() {
            return embedding;
        }

        public void setEmbedding(List<Float> embedding) {
            this.embedding = embedding;
        }
    }
}