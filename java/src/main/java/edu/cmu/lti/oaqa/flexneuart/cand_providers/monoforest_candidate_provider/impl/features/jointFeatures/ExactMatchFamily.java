package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.features.jointFeatures;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.AppConfig;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.DocumentMarco;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.features.FeatureBase;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.features.FeatureFamily;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import java.util.*;

public class ExactMatchFamily implements FeatureFamily {


    @FunctionalInterface
    private interface FeatureCalculator {
        FeatureBase calculate(String[] queryTokenStream, DocumentMarco document);
    }

    private final Map<String, FeatureCalculator> featureCalculators = new LinkedHashMap<>();

    public ExactMatchFamily() {
        featureCalculators.put("ExactMatchRatio", this::calcExactMatchRatio);
        featureCalculators.put("ExactMatchCount", this::calcExactMatchCount);
    }

    @Override
    public String getNameFamily() {
        return "JointExactMatch";
    }

    @Override
    public String getDescription() {
        return "Семейство фичей, считающее точное совпадение уникальных слов между запросом и документом (абсолютное количество и пропорция).";
    }

    @Override
    public List<String> getAllFeaturesNames() {
        return new ArrayList<>(featureCalculators.keySet());
    }

    @Override
    public void prepare() {
        // Этому семейству не нужно обращаться к структурам Lucene (частотам и т.д.)
        // Все вычисления идут исключительно в памяти над токенами документа.
    }

    @Override
    public Query buildLuceneQuery(String featureName, String[] queryTokenStream, Object... args) {
        if (queryTokenStream == null || queryTokenStream.length == 0) {
            return new MatchNoDocsQuery();
        }

        Set<String> uniqueTerms = new HashSet<>(Arrays.asList(queryTokenStream));
        if (uniqueTerms.isEmpty()) {
            return new MatchNoDocsQuery();
        }

        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();

        for (String term : uniqueTerms) {
            booleanQueryBuilder.add(new TermQuery(new Term(AppConfig.getTextField(), term)), BooleanClause.Occur.SHOULD);
        }

        int minMatches = 1;

        if (featureName.equals("ExactMatchRatio")) {
            // Для Ratio аргумент — это доля (от 0.0 до 1.0)
            float coefficient = (args.length > 0 && args[0] instanceof Number) ? ((Number) args[0]).floatValue() : 0.0f;

            // Считаем порог: нам нужно СТРОГО БОЛЬШЕ (length * coef)
            minMatches = (int) Math.floor(uniqueTerms.size() * coefficient) + 1;

        } else if (featureName.equals("ExactMatchCount")) {
            // Для Count аргумент — это желаемое абсолютное количество (например, 2)
            int targetCount = (args.length > 0 && args[0] instanceof Number) ? ((Number) args[0]).intValue() : 0;

            // Нам нужно СТРОГО БОЛЬШЕ этого числа
            minMatches = targetCount + 1;

        }

        // Ограничиваем сверху количеством уникальных слов (нельзя искать больше, чем есть в запросе)
        minMatches = Math.min(minMatches, uniqueTerms.size());

        // Ограничиваем снизу 1 совпадением (если пороги нулевые)
        minMatches = Math.max(1, minMatches);

        booleanQueryBuilder.setMinimumNumberShouldMatch(minMatches);
        return booleanQueryBuilder.build();
    }

    @Override
    public List<FeatureBase> calculateAllFeaturesInFamily(String[] queryTokenStream, DocumentMarco document) {
        List<FeatureBase> results = new ArrayList<>();
        for (Map.Entry<String, FeatureCalculator> entry : featureCalculators.entrySet()) {
            results.add(entry.getValue().calculate(queryTokenStream, document));
        }
        return results;
    }

    @Override
    public FeatureBase calculateFeatureByName(String featureName, String[] queryTokenStream, DocumentMarco document) {
        FeatureCalculator calculator = featureCalculators.get(featureName);
        if (calculator == null) {
            throw new IllegalArgumentException("Фича с именем '" + featureName + "' не найдена в семействе " + getNameFamily());
        }
        return calculator.calculate(queryTokenStream, document);
    }

    // ===================================================================================
    // Исполняющие функции
    // ===================================================================================

    private FeatureBase calcExactMatchRatio(String[] queryTokenStream, DocumentMarco document) {
        Set<String> qWords = new HashSet<>(Arrays.asList(queryTokenStream));

        // Защита от пустого запроса или деления на ноль
        if (qWords.isEmpty()) {
            return new FeatureBase("ExactMatchRatio", 0.0f);
        }

        int queryLen = qWords.size();
        Set<String> dWords = new HashSet<>(document.getTokensBody());

        // Оставляем в qWords только те слова, которые есть и в запросе, и в документе
        qWords.retainAll(dWords);

        float ratio = (float) qWords.size() / queryLen;
        return new FeatureBase("ExactMatchRatio", ratio);
    }

    private FeatureBase calcExactMatchCount(String[] queryTokenStream, DocumentMarco document) {
        Set<String> qWords = new HashSet<>(Arrays.asList(queryTokenStream));
        Set<String> dWords = new HashSet<>(document.getTokensBody());

        qWords.retainAll(dWords);

        return new FeatureBase("ExactMatchCount", (float) qWords.size());
    }
}