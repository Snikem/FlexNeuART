package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.features.jointFeatures;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.AppConfig;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.DocumentMarco;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.features.FeatureBase;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.features.FeatureFamily;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class UnorderedWindowFamily implements FeatureFamily {

    private IndexSearcher searcher;
    private IndexReader reader;
    private Analyzer analyzer;

    // Определяем функциональный интерфейс для вычисления конкретной фичи
    @FunctionalInterface
    private interface FeatureCalculator {
        FeatureBase calculate(String[] queryTokenStream, DocumentMarco document);
    }

    // Мапа (реестр) всех фичей данного семейства
    private final Map<String, FeatureCalculator> featureCalculators = new LinkedHashMap<>();

    @Override
    public String getNameFamily() {
        return "JointUnorderedWindow";
    }

    @Override
    public String getDescription() {
        return "Семейство фичей на основе PMI N-грамм и неупорядоченных скользящих окон (размер 4 и 8).";
    }

    @Override
    public List<String> getAllFeaturesNames() {
        return new ArrayList<>(featureCalculators.keySet());
    }

    @Override
    public void prepare() {
        try {
            FSDirectory dir = FSDirectory.open(Paths.get(AppConfig.getIndexDir()));
            this.reader = DirectoryReader.open(dir);
            this.searcher = new IndexSearcher(reader);
            this.analyzer = new StandardAnalyzer();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Lucene index in FeatureFamily: " + getNameFamily(), e);
        }
    }

    public UnorderedWindowFamily() {
        // Регистрируем простые счетчики
        featureCalculators.put("BigramCount", this::calcBigramCount);
        featureCalculators.put("TrigramCount", this::calcTrigramCount);
        featureCalculators.put("UnorderedWindow4", this::calcWindow4);
        featureCalculators.put("UnorderedWindow8", this::calcWindow8);
    }

    @Override
    public Query buildLuceneQuery(String featureName, String[] queryTokenStream, Object... args) {
        var text_field = AppConfig.getTextField();
        if (queryTokenStream == null || queryTokenStream.length < 2) {
            return new BooleanQuery.Builder().build();
        }

        switch (featureName) {
            case "UnorderedWindow4":
                return buildWindowQuery(queryTokenStream, text_field, 4);
            case "UnorderedWindow8":
                return buildWindowQuery(queryTokenStream, text_field, 8);
            case "BigramCount":
                return buildNgramQuery(queryTokenStream, text_field, 2);
            case "TrigramCount":
                return buildNgramQuery(queryTokenStream, text_field, 3);
            default:
                throw new IllegalArgumentException("Неизвестная фича для построения запроса: " + featureName);
        }
    }

    /**
     * Создает Lucene-запрос, который ищет хотя бы одну точную N-грамму из запроса.
     */
    private Query buildNgramQuery(String[] tokens, String fieldName, int n) {
        if (tokens.length < n) {
            return new BooleanQuery.Builder().build();
        }

        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();

        // Проходим по запросу скользящим окном размера N
        for (int i = 0; i <= tokens.length - n; i++) {
            PhraseQuery.Builder pqBuilder = new PhraseQuery.Builder();
            for (int j = 0; j < n; j++) {
                pqBuilder.add(new Term(fieldName, tokens[i + j]));
            }
            // slop = 0 означает, что слова должны идти строго подряд
            pqBuilder.setSlop(0);
            booleanQueryBuilder.add(pqBuilder.build(), BooleanClause.Occur.SHOULD);
        }

        // Документ должен содержать хотя бы одну такую N-грамму
        booleanQueryBuilder.setMinimumNumberShouldMatch(1);
        return booleanQueryBuilder.build();
    }

    @Override
    public List<FeatureBase> calculateAllFeaturesInFamily(String[] queryTokenStream, DocumentMarco document) {
        List<FeatureBase> results = new ArrayList<>();
        // Проходим по всей мапе и вычисляем каждую фичу
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
    // Исполняющие функции (привязаны к Мапе)
    // ===================================================================================

    // ===================================================================================
    // Исполняющие функции (простые N-граммы)
    // ===================================================================================

    private FeatureBase calcBigramCount(String[] queryTokenStream, DocumentMarco document) {
        int count = countExactNgrams(Arrays.asList(queryTokenStream), document.getTokensBody(), 2);
        return createFeature("BigramCount", (float) count);
    }

    private FeatureBase calcTrigramCount(String[] queryTokenStream, DocumentMarco document) {
        int count = countExactNgrams(Arrays.asList(queryTokenStream), document.getTokensBody(), 3);
        return createFeature("TrigramCount", (float) count);
    }

    /**
     * Простой алгоритм подсчета точных N-грамм.
     * Возвращает количество раз, которое ЛЮБАЯ n-грамма из запроса встретилась в документе.
     */
    private int countExactNgrams(List<String> queryWords, List<String> docWords, int n) {
        if (docWords == null || queryWords.size() < n || docWords.size() < n) return 0;

        // 1. Собираем уникальные N-граммы из запроса (без учета порядка внутри запроса, но с сохранением порядка слов)
        Set<String> queryNgrams = new HashSet<>();
        for (int i = 0; i <= queryWords.size() - n; i++) {
            queryNgrams.add(join(queryWords, i, n));
        }

        // 2. Идем окном по документу и считаем совпадения
        int matchCount = 0;
        for (int i = 0; i <= docWords.size() - n; i++) {
            String docNgram = join(docWords, i, n);
            if (queryNgrams.contains(docNgram)) {
                matchCount++;
            }
        }

        return matchCount;
    }

    private FeatureBase calcWindow4(String[] queryTokenStream, DocumentMarco document) {
        Set<String> uniqueQueryTerms = new HashSet<>(Arrays.asList(queryTokenStream));
        float score = (float) countUnorderedWindow(document.getTokensBody(), uniqueQueryTerms, 4);
        return createFeature("UnorderedWindow4", score);
    }

    private FeatureBase calcWindow8(String[] queryTokenStream, DocumentMarco document) {
        Set<String> uniqueQueryTerms = new HashSet<>(Arrays.asList(queryTokenStream));
        float score = (float) countUnorderedWindow(document.getTokensBody(), uniqueQueryTerms, 8);
        return createFeature("UnorderedWindow8", score);
    }

    private FeatureBase createFeature(String name, float value) {
        // Теперь используем твой новый конструктор
        return new FeatureBase(name, value);
    }

    private int countUnorderedWindow(List<String> docWords, Set<String> queryTerms, int windowSize) {
        if (docWords == null || queryTerms.size() < 2) return 0;

        int matchCount = 0;
        int docLength = docWords.size();

        for (int i = 0; i < docLength; i++) {
            String w1 = docWords.get(i);
            if (!queryTerms.contains(w1)) continue;

            int endWindow = Math.min(i + windowSize + 2, docLength);
            for (int j = i + 1; j < endWindow; j++) {
                String w2 = docWords.get(j);
                if (queryTerms.contains(w2) && !w1.equals(w2)) {
                    matchCount++;
                }
            }
        }
        return matchCount;
    }

    private String join(List<String> words, int start, int n) {
        if (n == 2) return words.get(start) + " " + words.get(start + 1);
        return words.get(start) + " " + words.get(start + 1) + " " + words.get(start + 2);
    }

    // ===================================================================================
    // Логика построения Lucene Запросов
    // ===================================================================================

    private Query buildWindowQuery(String[] tokens, String fieldName, int windowSize) {
        Set<String> uniqueTerms = new HashSet<>(Arrays.asList(tokens));
        List<String> uniqueList = new ArrayList<>(uniqueTerms);

        if (uniqueList.size() < 2) {
            return new BooleanQuery.Builder().build();
        }

        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();

        for (int i = 0; i < uniqueList.size() - 1; i++) {
            for (int j = i + 1; j < uniqueList.size(); j++) {
                String w1 = uniqueList.get(i);
                String w2 = uniqueList.get(j);

                // Прямой порядок
                PhraseQuery.Builder pqBuilderForward = new PhraseQuery.Builder();
                pqBuilderForward.add(new Term(fieldName, w1));
                pqBuilderForward.add(new Term(fieldName, w2));
                pqBuilderForward.setSlop(windowSize);
                booleanQueryBuilder.add(pqBuilderForward.build(), BooleanClause.Occur.SHOULD);

                // Обратный порядок
                PhraseQuery.Builder pqBuilderBackward = new PhraseQuery.Builder();
                pqBuilderBackward.add(new Term(fieldName, w2));
                pqBuilderBackward.add(new Term(fieldName, w1));
                pqBuilderBackward.setSlop(windowSize);
                booleanQueryBuilder.add(pqBuilderBackward.build(), BooleanClause.Occur.SHOULD);
            }
        }

        booleanQueryBuilder.setMinimumNumberShouldMatch(1);
        return booleanQueryBuilder.build();
    }

}
