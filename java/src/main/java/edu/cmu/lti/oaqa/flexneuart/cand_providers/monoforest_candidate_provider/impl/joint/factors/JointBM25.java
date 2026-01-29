package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.JointFactor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.ReaderUtil;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class JointBM25 extends JointFactor {

    //TODO: хардкод
    private static final String INDEX_PATH = "/Volumes/Ex_Volume/msmarcoProcces/lucene_index";
    private static final String ID_FIELD = "DOCNO"; // Поле идентификатора в вашем индексе
    private static final String TEXT_FIELD = "text"; // Поле с текстом

    // Параметры BM25
    private static final float K1 = 1.2f;
    private static final float B = 0.75f;

    private IndexReader reader;
    private IndexSearcher searcher;
    private Analyzer analyzer;
    private QueryParser queryParser;
    private boolean isReady = false;

    @Override
    public String getName() {
        return "JointBM25_Lucene";
    }

    @Override
    public float[] calculateScore(String queryText, String title, String document, String doc_id) {
        if (!isReady || doc_id == null || queryText == null) {
            return new float[]{0f};
        }

        try {
            // 1. Находим внутренний ID документа (Lucene DocID) по строковому doc_id
            int luceneDocId = getLuceneDocId(doc_id);
            if (luceneDocId == -1) {
                // Документ не найден в индексе
                return new float[]{0f};
            }

            // 2. Считаем BM25 для этого конкретного документа
            float score = calculateBM25ForDoc(queryText, luceneDocId);

            return new float[]{score};

        } catch (Exception e) {
            e.printStackTrace();
            return new float[]{0f};
        }
    }

    @Override
    public int getFeatureQty() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "BM25 score calculated directly via Lucene Index";
    }

    @Override
    public void prepare() {
        try {
            // 1. Открываем индекс
            FSDirectory dir = FSDirectory.open(Paths.get(INDEX_PATH));
            reader = DirectoryReader.open(dir);
            searcher = new IndexSearcher(reader);

            // 2. Устанавливаем модель BM25
            searcher.setSimilarity(new BM25Similarity(K1, B));

            // 3. Инициализируем анализатор и парсер
            // WhitespaceAnalyzer - стандарт для FlexNeuART, так как текст обычно уже токенизирован
            analyzer = new WhitespaceAnalyzer();
            queryParser = new QueryParser(TEXT_FIELD, analyzer);

            // Чтобы парсер не падал на спецсимволах (/ + - && ||), включаем режим экранирования (опционально)
            // или просто будем экранировать строку запроса вручную.

            isReady = true;
            System.out.println("JointBM25 prepared. Index: " + INDEX_PATH);

        } catch (IOException e) {
            System.err.println("CRITICAL ERROR: Could not open Lucene index at " + INDEX_PATH);
            e.printStackTrace();
        }
    }

    // === ВНУТРЕННЯЯ ЛОГИКА ===

    /**
     * Ищет внутренний int ID документа по его строковому DOCNO.
     */
    private int getLuceneDocId(String docIdStr) throws IOException {
        // Создаем запрос на точное совпадение ID
        Query idQuery = new TermQuery(new Term(ID_FIELD, docIdStr));

        // Ищем только 1 результат
        TopDocs hits = searcher.search(idQuery, 1);

        if (hits.totalHits.value == 0) {
            return -1; // Не найден
        }
        return hits.scoreDocs[0].doc;
    }

    /**
     * Вычисляет скор запроса для КОНКРЕТНОГО документа (по известному ID).
     * Это гораздо быстрее, чем делать поиск по всему индексу.
     */
    private float calculateBM25ForDoc(String queryText, int luceneDocId) throws IOException {
        Query query;
        try {
            // Экранируем спецсимволы Lucene, чтобы запрос не падал
            String safeQuery = QueryParser.escape(queryText);
            query = queryParser.parse(safeQuery);
        } catch (ParseException e) {
            // Если запрос распарсить не удалось, возвращаем 0
            return 0f;
        }

        // Переписываем запрос (важно для Wildcard/Prefix запросов и корректного веса)
        Query rewrittenQuery = searcher.rewrite(query);

        // Создаем Weight (объект, который умеет считать скоры)
        // ScoreMode.COMPLETE означает, что нам нужен точный скор
        Weight weight = searcher.createWeight(rewrittenQuery, ScoreMode.COMPLETE, 1.0f);

        // Находим лист (сегмент), в котором живет этот документ
        List<LeafReaderContext> leaves = reader.leaves();
        int leafIndex = ReaderUtil.subIndex(luceneDocId, leaves);
        LeafReaderContext leaf = leaves.get(leafIndex);

        // Вычисляем локальный ID внутри сегмента
        int docInLeaf = luceneDocId - leaf.docBase;

        // Получаем Scorer для этого сегмента
        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) {
            return 0f; // Термы запроса вообще не встречаются в этом сегменте
        }

        // Пытаемся промотать итератор к нашему документу
        DocIdSetIterator iterator = scorer.iterator();
        if (iterator.advance(docInLeaf) == docInLeaf) {
            // Если документ найден среди матчей — возвращаем его скор
            return scorer.score();
        }

        // Если итератор перескочил наш документ, значит, документ не релевантен запросу
        return 0f;
    }
}