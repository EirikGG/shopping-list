package no.eirikgg.shoppinglist.category;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import no.eirikgg.shoppinglist.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The SentencePiece Unigram tokenizer multilingual-e5-small (XLM-RoBERTa)
// uses, read from its tokenizer.json. It gives the same token ids as the
// HuggingFace tokenizer (TokenizerParityTest checks it), using a fraction of
// the memory: the native HuggingFace tokenizer takes over 400 MB for this
// vocabulary, too much beside Home Assistant on a small computer.
//
// Text is normalised (NFKC, "▁" made a space, runs of spaces made one), each
// word gets "▁" in front, and each word is split into the pieces whose scores
// add up highest.
final class UnigramTokenizer {

    static final int BOS = 0;
    static final int PAD = 1;
    static final int EOS = 2;
    static final int UNK = 3;

    private static final char SPACE = '▁';
    // What a character no piece has costs, below the cheapest piece, as in
    // SentencePiece and HuggingFace tokenizers.
    private static final float UNK_PENALTY = 10;

    private final Map<String, Integer> ids;
    private final float[] scores;
    private final int maxPieceLength;
    private final float unkScore;

    UnigramTokenizer(Map<String, Integer> ids, float[] scores) {
        this.ids = ids;
        this.scores = scores;
        int longest = 1;
        float lowest = 0;
        for (Map.Entry<String, Integer> piece : ids.entrySet()) {
            longest = Math.max(longest, piece.getKey().codePointCount(0, piece.getKey().length()));
            lowest = Math.min(lowest, scores[piece.getValue()]);
        }
        maxPieceLength = longest;
        unkScore = lowest - UNK_PENALTY;
    }

    // Reads model.vocab, a list of [piece, score] where the place is the id,
    // without reading the rest of the 17 MB file into memory.
    static UnigramTokenizer load(Path tokenizerJson) throws IOException {
        Map<String, Integer> ids = new HashMap<>(400_000);
        List<Float> scores = new ArrayList<>(260_000);
        try (JsonParser parser = Json.MAPPER.getFactory().createParser(tokenizerJson.toFile())) {
            while (null != parser.nextToken()) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "vocab".equals(parser.currentName())) {
                    if (parser.nextToken() != JsonToken.START_ARRAY) {
                        throw new IOException("model.vocab is not a list");
                    }
                    while (parser.nextToken() == JsonToken.START_ARRAY) {
                        parser.nextToken();
                        String piece = parser.getText();
                        parser.nextToken();
                        float score = parser.getFloatValue();
                        parser.nextToken();
                        ids.putIfAbsent(piece, scores.size());
                        scores.add(score);
                    }
                    break;
                }
            }
        }
        if (ids.isEmpty()) {
            throw new IOException("No vocabulary in " + tokenizerJson);
        }
        float[] scoreArray = new float[scores.size()];
        for (int i = 0; i < scoreArray.length; i++) {
            scoreArray[i] = scores.get(i);
        }
        // <s>, </s> and the like are in the vocabulary but never in text.
        ids.remove("<s>");
        ids.remove("</s>");
        ids.remove("<pad>");
        ids.remove("<unk>");
        ids.remove("<mask>");
        return new UnigramTokenizer(ids, scoreArray);
    }

    // The ids of text, between <s> and </s>, at most maxTokens in all.
    long[] encode(String text, int maxTokens) {
        List<Integer> pieces = new ArrayList<>();
        pieces.add(BOS);
        for (String word : words(text)) {
            pieces.addAll(splitWord(word));
        }
        if (pieces.size() > maxTokens - 1) {
            pieces = pieces.subList(0, maxTokens - 1);
        }
        pieces.add(EOS);
        return pieces.stream().mapToLong(Integer::longValue).toArray();
    }

    // "Tine  lettmelk" becomes "▁Tine" and "▁lettmelk".
    static List<String> words(String text) {
        String normalised = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace(SPACE, ' ')
                .replaceAll(" {2,}", " ");
        String marked = SPACE + normalised.replace(' ', SPACE);
        List<String> words = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < marked.length(); i++) {
            if (marked.charAt(i) == SPACE) {
                words.add(marked.substring(start, i));
                start = i;
            }
        }
        words.add(marked.substring(start));
        return words;
    }

    // The split of word into pieces with the highest total score (Viterbi).
    // A character no piece starts with becomes <unk>, and <unk>s next to each
    // other become one.
    private List<Integer> splitWord(String word) {
        int[] codePoints = word.codePoints().toArray();
        int n = codePoints.length;
        // For each end position: the best total score of the text before it,
        // where the last piece starts, and its id.
        float[] best = new float[n + 1];
        int[] from = new int[n + 1];
        int[] pieceId = new int[n + 1];
        boolean[] reached = new boolean[n + 1];
        Arrays.fill(best, Float.NEGATIVE_INFINITY);
        best[0] = 0;
        reached[0] = true;

        for (int start = 0; start < n; start++) {
            if (!reached[start]) {
                continue;
            }
            boolean singleCharacter = false;
            for (int end = start + 1; end <= Math.min(n, start + maxPieceLength); end++) {
                Integer id = ids.get(new String(codePoints, start, end - start));
                if (null == id) {
                    continue;
                }
                singleCharacter |= end == start + 1;
                float score = best[start] + scores[id];
                if (!reached[end] || score > best[end]) {
                    best[end] = score;
                    from[end] = start;
                    pieceId[end] = id;
                    reached[end] = true;
                }
            }
            if (!singleCharacter) {
                float score = best[start] + unkScore;
                if (!reached[start + 1] || score > best[start + 1]) {
                    best[start + 1] = score;
                    from[start + 1] = start;
                    pieceId[start + 1] = UNK;
                    reached[start + 1] = true;
                }
            }
        }

        List<Integer> pieces = new ArrayList<>();
        for (int end = n; end > 0; end = from[end]) {
            int id = pieceId[end];
            if (id == UNK && !pieces.isEmpty() && pieces.get(pieces.size() - 1) == UNK) {
                continue;
            }
            pieces.add(id);
        }
        return pieces.reversed();
    }
}
