package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.JointFactor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class JointBM25AndTFIDF extends JointFactor {

    private static final String INDEX_PATH = "/Volumes/Ex_Volume/msmarcoProcces/lucene_index";

    // 1. ИСПРАВЛЕНО: Точное имя поля из Инспектора
    private static final String TEXT_FIELD = "text";

    private static final float K1 = 1.2f;
    private static final float B = 0.75f;

    private IndexSearcher searcher;
    private IndexReader reader;
    private Analyzer analyzer;

    private float avgDl = 0f;
    private long totalDocs = 0;
    private boolean isReady = false;

    @Override
    public String getName() {
        return "Joint_BM25_TFIDF";
    }

    @Override
    public int getFeatureQty() {
        return 2;
    }

    @Override
    public String getDescription() {
        return "";
    }


    @Override
    public float[] calculateScore(String queryText, String title, String document, String doc_id) {
        ArrayList<String> q = new ArrayList<>(1);
        q.add(queryText);
        return calculateForQueries(q, title, document, doc_id).get(0);
    }

    // =================================================================
    // 1. МЕТОД РАСЧЕТА (Оптимизированный)
    // =================================================================
    @Override
    public ArrayList<float[]> calculateForQueries(ArrayList<String> queries, String title, String document, String doc_id) {
        int numQueries = queries.size();
        ArrayList<float[]> results = new ArrayList<>(numQueries);

        // Проверки на готовность
        if (!isReady) return fillWithZeros(results, numQueries);

        // Склеиваем заголовок и текст
        String docText = (title != null ? title + " " : "") + (document != null ? document : "");
        if (docText.trim().isEmpty()) return fillWithZeros(results, numQueries);

        try {
            // А. Токенизация ДОКУМЕНТА
            // Важно: используем analyzeWithLucene, чтобы токенизация совпадала с индексом
            List<String> docTokens = analyzeWithLucene(docText);
            int docLen = docTokens.size();

            if (docLen == 0) return fillWithZeros(results, numQueries);

            // Б. Считаем частоты (TF) для документа
            // Размер Map ставим заранее, чтобы избежать rehashing
            Map<String, Integer> docTf = new HashMap<>((int)(docLen / 0.75f) + 1);
            for (String t : docTokens) {
                docTf.merge(t, 1, Integer::sum);
            }

            // В. Предрасчет нормализации длины (BM25 Denominator Part)
            // K1 * (1 - b + b * L/avgL)
            float docLenNorm = K1 * (1.0f - B + B * (docLen / avgDl));

            // Константа для числителя (K1 + 1)
            float k1PlusOne = K1 + 1.0f;

            // Г. Кэш IDF для текущей пачки запросов (чтобы не дергать Lucene лишний раз)
            Map<String, Float> idfCache = new HashMap<>();

            // Д. Цикл по всем запросам
            for (int i = 0; i < numQueries; i++) {
                String query = queries.get(i);

                // Пустой запрос -> 0
                if (query == null || query.isEmpty()) {
                    results.add(new float[]{0f, 0f});
                    continue;
                }

                // Е. Токенизация ЗАПРОСА
                List<String> queryTokens = analyzeWithLucene(query);

                float bm25Score = 0f;
                float tfidfScore = 0f;

                for (String qTerm : queryTokens) {
                    // 1. Быстрая проверка: есть ли слово в документе?
                    Integer tfVal = docTf.get(qTerm);

                    if (tfVal == null) continue; // Слова нет -> вклад 0

                    float tf = tfVal.floatValue();

                    // 2. Получаем IDF (из кэша или из индекса)
                    Float idf = idfCache.get(qTerm);
                    if (idf == null) {
                        idf = getIDF(qTerm);
                        idfCache.put(qTerm, idf);
                    }

                    // Если IDF = 0 (слова нет в глобальном индексе), оно не дает вклада
                    if (idf <= 0f) continue;

                    // 3. Считаем BM25
                    // Formula: IDF * (TF * (K1 + 1)) / (TF + Norm)
                    float numerator = tf * k1PlusOne;
                    float denominator = tf + docLenNorm;
                    bm25Score += idf * (numerator / denominator);

                    // 4. Считаем TF-IDF
                    tfidfScore += (tf * idf);
                }

                results.add(new float[]{bm25Score, tfidfScore});
            }
            return results;

        } catch (Exception e) {
            e.printStackTrace();
            return fillWithZeros(results, numQueries);
        }
    }

    // =================================================================
    // 2. КРИТИЧЕСКИ ВАЖНЫЕ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // =================================================================

    /**
     * Токенизирует текст ТОЧНО ТАК ЖЕ, как это делает Lucene при индексации.
     * Это гарантирует, что "word," превратится в "word", как в индексе.
     */
    private List<String> analyzeWithLucene(String text) {
        List<String> tokens = new ArrayList<>();
        try (TokenStream tokenStream = analyzer.tokenStream(TEXT_FIELD, text)) {
            CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                tokens.add(attr.toString());
            }
            tokenStream.end();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tokens;
    }

    /**
     * Инициализация (Fix для проблемы с нулями!)
     */
    @Override
    public void prepare() {
        try {
            FSDirectory dir = FSDirectory.open(Paths.get(INDEX_PATH));
            reader = DirectoryReader.open(dir);
            searcher = new IndexSearcher(reader);

            // ВАЖНО: CharArraySet.EMPTY_SET отключает удаление стоп-слов!
            // Если этого не сделать, слова "the", "to", "in" исчезнут, и скор будет 0.
            this.analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);

            totalDocs = reader.numDocs();
            CollectionStatistics stats = searcher.collectionStatistics(TEXT_FIELD);
            if (stats != null && stats.docCount() > 0) {
                avgDl = (float) stats.sumTotalTermFreq() / stats.docCount();
            } else {
                avgDl = 1000f;
            }
            isReady = true;
            System.out.println("JointBM25+TFIDF Prepared. AvgDL: " + avgDl + " (StopWords: DISABLED)");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<float[]> fillWithZeros(ArrayList<float[]> list, int targetSize) {
        while (list.size() < targetSize) {
            list.add(new float[]{0f, 0f});
        }
        return list;
    }

    private float getIDF(String termText) throws IOException {
        Term term = new Term(TEXT_FIELD, termText);
        int docFreq = reader.docFreq(term);
        if (docFreq == 0) return 0f;
        return (float) Math.log(1 + (totalDocs - docFreq + 0.5D) / (docFreq + 0.5D));
    }
}