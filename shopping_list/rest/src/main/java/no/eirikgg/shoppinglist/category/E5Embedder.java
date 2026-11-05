package no.eirikgg.shoppinglist.category;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Turns text into vectors with multilingual-e5-small: the ONNX model and its
// tokenizer.json, in one folder. Texts with the same meaning, in Norwegian or
// English, get vectors pointing the same way. Safe to use from many threads.
// The model takes about 150 MB outside the Java heap.
public final class E5Embedder implements AutoCloseable {

    public static final String MODEL_FILE = "model.onnx";
    public static final String TOKENIZER_FILE = "tokenizer.json";

    // Item names are short; this only stops a pasted essay from being slow.
    private static final int MAX_TOKENS = 64;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final UnigramTokenizer tokenizer;
    private final boolean wantsTokenTypes;

    public E5Embedder(Path modelDir) throws IOException, OrtException {
        tokenizer = UnigramTokenizer.load(modelDir.resolve(TOKENIZER_FILE));
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        // One request at a time is fast enough, and leaves the rest of the
        // Home Assistant box alone.
        options.setIntraOpNumThreads(1);
        session = env.createSession(modelDir.resolve(MODEL_FILE).toString(), options);
        wantsTokenTypes = session.getInputNames().contains("token_type_ids");
    }

    // One normalised vector for each text. E5 wants "query: " in front of
    // text that is compared with other short text.
    public float[][] embed(List<String> texts) throws OrtException {
        long[][] encoded = texts.stream()
                .map(text -> tokenizer.encode("query: " + text.strip().toLowerCase(Locale.ROOT), MAX_TOKENS))
                .toArray(long[][]::new);
        int batch = encoded.length;
        int length = Arrays.stream(encoded).mapToInt(tokens -> tokens.length).max().orElse(0);

        // Padded to the longest, with the padding masked out.
        long[][] ids = new long[batch][length];
        long[][] mask = new long[batch][length];
        long[][] types = new long[batch][length];
        for (int i = 0; i < batch; i++) {
            Arrays.fill(ids[i], UnigramTokenizer.PAD);
            System.arraycopy(encoded[i], 0, ids[i], 0, encoded[i].length);
            Arrays.fill(mask[i], 0, encoded[i].length, 1);
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(env, ids));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, mask));
            if (wantsTokenTypes) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(env, types));
            }
            try (OrtSession.Result result = session.run(inputs)) {
                // [batch][token][dimension]
                float[][][] hidden = (float[][][]) result.get(0).getValue();
                float[][] vectors = new float[batch][];
                for (int i = 0; i < batch; i++) {
                    vectors[i] = normalised(meanOfTokens(hidden[i], mask[i]));
                }
                return vectors;
            }
        }
        finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    public float[] embed(String text) throws OrtException {
        return embed(List.of(text))[0];
    }

    // The mean of the vectors of the real tokens, leaving out the padding.
    private static float[] meanOfTokens(float[][] tokens, long[] mask) {
        float[] sum = new float[tokens[0].length];
        int count = 0;
        for (int t = 0; t < tokens.length; t++) {
            if (mask[t] == 0) {
                continue;
            }
            count++;
            for (int d = 0; d < sum.length; d++) {
                sum[d] += tokens[t][d];
            }
        }
        for (int d = 0; d < sum.length; d++) {
            sum[d] /= Math.max(count, 1);
        }
        return sum;
    }

    private static float[] normalised(float[] vector) {
        double length = 0;
        for (float value : vector) {
            length += value * value;
        }
        float scale = (float) (1 / Math.max(Math.sqrt(length), 1e-12));
        for (int d = 0; d < vector.length; d++) {
            vector[d] *= scale;
        }
        return vector;
    }

    // Both vectors normalised, so this is their cosine similarity.
    static float dot(float[] a, float[] b) {
        float sum = 0;
        for (int d = 0; d < a.length; d++) {
            sum += a[d] * b[d];
        }
        return sum;
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
