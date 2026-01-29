package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.JointFactor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class JointExactMatchCount extends JointFactor {

    // Паттерн: всё, что НЕ буквы, НЕ цифры и НЕ пробелы
    private static final Pattern TOKENIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");

    @Override
    public String getName() {
        return "feature_exact_match_count";
    }

    @Override
    public float[] calculateScore(String query, String title, String document, String doc_id) {
        // 1. Токенизация в множества (Set)
        Set<String> qWords = tokenizeSet(query);
        Set<String> dWords = tokenizeSet(document);

        // 2. Находим пересечение (intersection)
        // retainAll оставляет в qWords только те элементы, которые есть в dWords
        qWords.retainAll(dWords);

        // 3. Возвращаем размер пересечения
        return new float[] { (float) qWords.size() };
    }

    @Override
    public int getFeatureQty() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Количество одинаковых уникальных слов в документе и запросе";
    }

    @Override
    public void prepare() {
        // Подготовка не требуется
    }

    /**
     * Токенизация: lowercase -> удаление мусора -> split -> set
     */
    private Set<String> tokenizeSet(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new HashSet<>(); // Возвращаем пустой изменяемый Set
        }

        String lower = text.toLowerCase();
        String cleaned = TOKENIZE_PATTERN.matcher(lower).replaceAll(" ");
        String[] tokens = cleaned.trim().split("\\s+");

        Set<String> result = new HashSet<>();
        Collections.addAll(result, tokens);

        return result;
    }
}