package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.JointFactor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class JointTitleMatchCount extends JointFactor {

    // Паттерн: всё, что НЕ буквы, НЕ цифры и НЕ пробелы
    private static final Pattern TOKENIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");

    @Override
    public String getName() {
        return "feature_title_match_count";
    }

    @Override
    public float[] calculateScore(String query, String title, String document, String doc_id) {
        // Получаем множества уникальных слов
        Set<String> qWords = tokenizeSet(query);
        Set<String> tWords = tokenizeSet(title);

        // Ищем пересечение (intersection)
        // Создаем копию qWords, чтобы не менять оригинал (хотя здесь это не критично)
        Set<String> intersection = new HashSet<>(qWords);

        // Оставляем только те слова, которые есть и в tWords
        intersection.retainAll(tWords);

        return new float[] { (float) intersection.size() };
    }

    @Override
    public int getFeatureQty() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Количество одинаковых уникальных слов в заголовке и запросе";
    }

    @Override
    public void prepare() {
        // Подготовка не требуется
    }

    /**
     * Токенизация в множество (уникальные слова).
     * Аналог Python: set(_TOKENIZE_PATTERN.sub(" ", text.lower()).split())
     */
    private Set<String> tokenizeSet(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptySet();
        }

        // 1. Lowercase
        String lower = text.toLowerCase();

        // 2. Замена спецсимволов на пробел
        String cleaned = TOKENIZE_PATTERN.matcher(lower).replaceAll(" ");

        // 3. Split по пробелам
        String[] tokens = cleaned.trim().split("\\s+");

        // 4. Собираем в Set (автоматически убирает дубликаты)
        Set<String> result = new HashSet<>();
        Collections.addAll(result, tokens);

        return result;
    }
}