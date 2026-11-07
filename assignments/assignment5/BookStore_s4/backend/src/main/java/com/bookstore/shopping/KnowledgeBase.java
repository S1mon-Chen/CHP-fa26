package com.bookstore.shopping;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable in-memory cosine index; lazily built once, retried after service failures. */
@Component
public class KnowledgeBase {
    public record Chunk(String id, String section, int start, int end, String text) {}
    private record Index(double[][] policies, double[][] books) {}
    private final ObjectMapper mapper;
    private final EmbeddingClient embeddings;
    private final List<Chunk> chunks;
    private final JsonNode catalog;
    private final String sourceHash;
    private volatile Index index;

    public KnowledgeBase(ObjectMapper mapper, EmbeddingClient embeddings) throws IOException {
        this.mapper = mapper;
        this.embeddings = embeddings;
        String document;
        try (var in = new ClassPathResource("shopping/store-policy.md").getInputStream()) {
            document = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        chunks = split(document, 320, 60);
        if (chunks.isEmpty()) throw new IllegalArgumentException("Policy document has no sections");
        try {
            sourceHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(document.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        try (var in = new ClassPathResource("shopping/catalog.json").getInputStream()) {
            catalog = mapper.readTree(in);
        }
    }

    /** Heading-aware windows preserve clauses; offsets are UTF-16 offsets in the source. */
    public static List<Chunk> split(String document, int size, int overlap) {
        if (size <= 0 || overlap < 0 || overlap >= size) throw new IllegalArgumentException("invalid chunk window");
        var matcher = Pattern.compile("(?m)^## (P\\d+ .+)\\R").matcher(document);
        var headings = new ArrayList<String>();
        var starts = new ArrayList<Integer>();
        var ends = new ArrayList<Integer>();
        while (matcher.find()) { headings.add(matcher.group(1)); starts.add(matcher.start()); ends.add(matcher.end()); }
        var result = new ArrayList<Chunk>();
        for (int i = 0; i < headings.size(); i++) {
            int limit = i + 1 < starts.size() ? starts.get(i + 1) : document.length();
            while (limit > ends.get(i) && Character.isWhitespace(document.charAt(limit - 1))) limit--;
            int number = 1;
            for (int start = ends.get(i); start < limit;) {
                int end = Math.min(start + size, limit);
                result.add(new Chunk(headings.get(i).split(" ", 2)[0] + "-" + number++, headings.get(i),
                        start, end, document.substring(start, end)));
                if (end == limit) break;
                start = end - overlap;
            }
        }
        return List.copyOf(result);
    }

    private synchronized Index index() {
        if (index == null) {
            var texts = new ArrayList<String>();
            for (Chunk c : chunks) texts.add(c.section() + "\n" + c.text());
            for (var book : catalog) texts.add(book.path("title").asText() + " "
                    + book.path("description").asText() + " " + book.path("tags").asText());
            double[][] vectors = embeddings.embed(texts, "passage");
            index = new Index(Arrays.copyOfRange(vectors, 0, chunks.size()),
                    Arrays.copyOfRange(vectors, chunks.size(), vectors.length));
        }
        return index;
    }

    public ObjectNode search(String query, int k, boolean policy) {
        if (query == null || query.isBlank() || query.length() > 500 || k < 1 || k > 5)
            throw new IllegalArgumentException("invalid search arguments");
        Index snapshot = index();
        double[] q = embeddings.embed(List.of(query), "query")[0];
        double[][] vectors = policy ? snapshot.policies() : snapshot.books();
        record Hit(int index, double score) {}
        var hits = new ArrayList<Hit>();
        for (int i = 0; i < vectors.length; i++) hits.add(new Hit(i, cosine(q, vectors[i])));
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        var result = mapper.createObjectNode().put("ok", true).put("simulated", true)
                .put("embedding_model", EmbeddingClient.MODEL).put("dimensions", EmbeddingClient.DIMENSIONS)
                .put("score_note", "余弦相似度用于排序，不是事实置信度；须检查原文是否回答问题。");
        ArrayNode matches = result.putArray("matches");
        // A conservative floor only removes clearly unrelated results; it is not a correctness guarantee.
        for (Hit hit : hits.stream().filter(h -> h.score() >= 0.45).limit(k).toList()) {
            ObjectNode entry;
            if (policy) {
                Chunk c = chunks.get(hit.index());
                entry = mapper.createObjectNode().put("chunk_id", c.id()).put("section", c.section())
                        .put("source", "shopping/store-policy.md").put("source_sha256", sourceHash)
                        .put("start", c.start()).put("end", c.end()).put("text", c.text());
            } else entry = ((ObjectNode) catalog.get(hit.index())).deepCopy();
            entry.put("score", Math.round(hit.score() * 1000000.0) / 1000000.0);
            matches.add(entry);
        }
        result.put("found", !matches.isEmpty());
        if (matches.isEmpty()) result.put("message", "未检索到足够相关的依据，请澄清需求或联系人工客服，不要编造政策或书目。");
        return result;
    }

    public ObjectNode manifest() {
        Index snapshot = index();
        var result = mapper.createObjectNode().put("source", "shopping/store-policy.md")
                .put("source_sha256", sourceHash).put("model", EmbeddingClient.MODEL)
                .put("dimensions", EmbeddingClient.DIMENSIONS).put("chunk_size", 320).put("overlap", 60)
                .put("chunk_count", chunks.size()).put("catalog_count", catalog.size());
        var items = result.putArray("chunks");
        for (int i = 0; i < chunks.size(); i++) {
            var c = chunks.get(i);
            var row = items.addObject().put("chunk_id", c.id()).put("section", c.section())
                    .put("start", c.start()).put("end", c.end()).put("text", c.text());
            row.set("embedding", mapper.valueToTree(snapshot.policies()[i]));
        }
        return result;
    }

    public static double cosine(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) throw new IllegalArgumentException("vector dimensions mismatch");
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i]; }
        if (aa == 0 || bb == 0) return 0;
        return dot / Math.sqrt(aa * bb);
    }
}
