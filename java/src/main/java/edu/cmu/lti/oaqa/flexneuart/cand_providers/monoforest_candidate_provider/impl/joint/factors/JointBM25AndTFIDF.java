package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.factors;

import com.google.common.base.Splitter;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.FactorManager;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.ParallelFeatureDumper;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.JointFactor;
import edu.cmu.lti.oaqa.flexneuart.utils.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JointBM25AndTFIDF extends JointFactor {

    private static final String INDEX_PATH = "/Volumes/Ex_Volume/msmarcoProcces/lucene_index";

    // 1. ИСПРАВЛЕНО: Точное имя поля из Инспектора
    private static final String TEXT_FIELD = "text";
    private static final String INDEX_FIED = "DOCNO";

    private static final float K1 = 1.2f;
    private static final float B = 0.75f;

    private IndexSearcher searcher;
    private IndexReader reader;
    private IndexSearcher bm25Searcher;
    private IndexSearcher tfidfSearcher;
    private Analyzer analyzer;

    private final ArrayList<QueryWeights> queryCache = new ArrayList<>();

    // Класс для хранения готовых "вычисляторов" (Weights) для целого запроса
    static class QueryWeights {
        Weight bm25Weight;
        Weight tfidfWeight;

        public QueryWeights(Weight bm25Weight, Weight tfidfWeight) {
            this.bm25Weight = bm25Weight;
            this.tfidfWeight = tfidfWeight;
        }
    }

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

    @Override
    public ArrayList<float[]> calculateForQueries(ArrayList<String> queries, String title, String document, String doc_id) {
        int numQueries = queries.size();
        ArrayList<float[]> results = new ArrayList<>(numQueries);
        try {
            int globalDocId = getLuceneDocId(doc_id);
            if (globalDocId == -1){
                System.out.println("net doc in lucene");
                return new ArrayList<float[]>();
            }
            LeafReaderContext targetContext = null;
            for (LeafReaderContext ctx : reader.leaves()) {
                if (globalDocId >= ctx.docBase && globalDocId < ctx.docBase + ctx.reader().maxDoc()) {
                    targetContext = ctx;
                    break;
                }
            }

            if (targetContext == null) {
                System.out.println("net context in lucene");
                return new ArrayList<float[]>();
            }

            int localDocId = globalDocId - targetContext.docBase;

            for( QueryWeights weight : queryCache) {
                float bm25Score = 0f;
                float tfidfScore = 0f;

                // --- Расчет BM25 ---
                // Создаем Scorer для текущего сегмента
                Scorer bm25Scorer = weight.bm25Weight.scorer(targetContext);
                if (bm25Scorer!=null) {
                    int doc = bm25Scorer.iterator().advance(localDocId);
                    if (doc == localDocId) {
                        bm25Score = bm25Scorer.score(); // Вуаля! Lucene всё посчитал сам.
                    }
                }



                // --- Расчет TF-IDF ---
                Scorer tfidfScorer = weight.tfidfWeight.scorer(targetContext);
                if(tfidfScorer!=null) {
                    int docF = tfidfScorer.iterator().advance(localDocId);
                    if (docF == localDocId) {
                        tfidfScore = tfidfScorer.score();

                    }
                }



                results.add(new float[]{bm25Score, tfidfScore});
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("ASDASDA");
            return new ArrayList<float[]>();
        }
        return results;
    }

    /**
     * Инициализация (Fix для проблемы с нулями!)
     */

    static class QueriesLeaf {
        Term term;
        long docFreq;
        long totalTermFreq;
        TermStatistics termStats;
        Similarity.SimScorer bm25Scorer;
        Similarity.SimScorer tfidfScorer;


        public QueriesLeaf(Term term, long docFreq, long totalTermFreq, TermStatistics termStats, Similarity.SimScorer bm25Scorer, Similarity.SimScorer tfidfScorer) {
            this.term = term;
            this.docFreq = docFreq;
            this.totalTermFreq = totalTermFreq;
            this.termStats = termStats;
            this.bm25Scorer = bm25Scorer;
            this.tfidfScorer = tfidfScorer;
        }
    }

    private List<QueriesLeaf> leafs = new ArrayList<>();


    @Override
    public void prepare() {
        try {

            FSDirectory dir = FSDirectory.open(Paths.get(INDEX_PATH));
            reader = DirectoryReader.open(dir);
            searcher = new IndexSearcher(reader);

            bm25Searcher = new IndexSearcher(reader);
            bm25Searcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));

            tfidfSearcher = new IndexSearcher(reader);
            tfidfSearcher.setSimilarity(new ClassicSimilarity());

            // ВАЖНО: CharArraySet.EMPTY_SET отключает удаление стоп-слов!
            // Если этого не сделать, слова "the", "to", "in" исчезнут, и скор будет 0.
            this.analyzer = new StandardAnalyzer();



            List<String> queries = loadQueriesFromJson(QUERIES_FILE);

            for (String query : queries) {
                query = StringUtils.removePunct(query.trim());
                query = StringUtils.removeLuceneSpecialOps(query);
                ArrayList<String> toks = new ArrayList<String>();

                for (String s : mSpaceSplit.split(query)) {
                    toks.add(s);
                }
                if (2 * toks.size() > BooleanQuery.getMaxClauseCount()) {
                    // This a heuristic, but it should work fine in many cases
                    BooleanQuery.setMaxClauseCount(2 * toks.size());
                }

                // QueryParser cannot be shared among threads!
                QueryParser parser = new QueryParser(TEXT_FIELD, analyzer);
                parser.setDefaultOperator(QueryParser.OR_OPERATOR);

                Query parsedQuery = parser.parse(query);
                Weight bm25Weight = bm25Searcher.createWeight(parsedQuery, ScoreMode.COMPLETE, 1.0f);
                Weight tfidfWeight = tfidfSearcher.createWeight(parsedQuery, ScoreMode.COMPLETE, 1.0f);

                QueryWeights elem = new QueryWeights(bm25Weight, tfidfWeight);
                queryCache.add(elem);



            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String ID_FIELD = "DOCNO";

    private int getLuceneDocId(String docIdStr) throws IOException {
        // Создаем запрос на точное совпадение ID
        Query idQuery = new TermQuery(new Term(ID_FIELD, docIdStr));

        // Ищем только 1 результат
        TopDocs hits = searcher.search(idQuery, 1);

        if (hits.totalHits.value == 0) {
            System.out.println("ti daun");
            return -1; // Не найден
        }
        return hits.scoreDocs[0].doc;
    }

    private static final String QUERIES_FILE = "/Volumes/Ex_Volume/msmarcoProcces/input_data/dev/QuestionFields.jsonl";
    private static List<String> loadQueriesFromJson(String filePath) {
        List<String> list = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JSONObject json = new JSONObject(line);
                    String id = json.optString("DOCNO");
                    String text = json.optString("text_raw");
                    if (!id.isEmpty() && !text.isEmpty()) {
                        list.add(text);
                    }
                } catch (Exception e) {}
            }
        } catch (IOException e) { e.printStackTrace(); }
        return list;
    }

    private static Splitter mSpaceSplit = Splitter.on(' ').omitEmptyStrings().trimResults();
}