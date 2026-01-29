package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.JointFactor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class JointQueryTfSum extends JointFactor {

    // Паттерн: всё, что НЕ буквы, НЕ цифры и НЕ пробелы (для очистки)
    private static final Pattern TOKENIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");

    @Override
    public String getName() {
        return "feature_query_tf_sum";
    }

    @Override
    public float[] calculateScore(String query, String title, String document, String doc_id) {
        // 1. Токенизация запроса (список слов, так как важны повторы в запросе, если они есть)
        String[] queryTokens = tokenize(query);

        // 2. Токенизация документа
        String[] docTokens = tokenize(document);

        // 3. Строим частотный словарь документа (аналог Python Counter)
        Map<String, Integer> docTf = new HashMap<>();
        for (String token : docTokens) {
            docTf.put(token, docTf.getOrDefault(token, 0) + 1);
        }

        // 4. Считаем сумму TF для слов запроса
        // Python: sum(doc_tf.get(term, 0) for term in query_tokens)
        float sumTf = 0;
        for (String term : queryTokens) {
            sumTf += docTf.getOrDefault(term, 0);
        }

        return new float[] { sumTf };
    }

    @Override
    public int getFeatureQty() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Суммарная частота слов запроса в документе";
    }

    @Override
    public void prepare() {
        // Подготовка не требуется
    }

    /**
     * Токенизация: lowercase -> удаление мусора -> split
     */
    private String[] tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new String[0];
        }

        String lower = text.toLowerCase();
        String cleaned = TOKENIZE_PATTERN.matcher(lower).replaceAll(" ");
        return cleaned.trim().split("\\s+");
    }
}