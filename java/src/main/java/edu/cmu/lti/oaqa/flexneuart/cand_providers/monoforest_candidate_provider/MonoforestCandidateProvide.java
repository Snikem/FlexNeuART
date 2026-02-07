package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider;

import java.nio.file.Files;
import java.util.*;
import java.io.*;
import java.nio.file.Paths;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.CandidateEntry;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.CandidateInfo;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.CandidateProvider;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.CatBoostInference;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.DocFeatureStore;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.FactorManager;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.MsMarcoRawReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.lti.oaqa.flexneuart.letor.CommonParams;
import edu.cmu.lti.oaqa.flexneuart.resources.RestrictedJsonConfig;
import edu.cmu.lti.oaqa.flexneuart.utils.Const;
import edu.cmu.lti.oaqa.flexneuart.utils.DataEntryFields;

public class MonoforestCandidateProvide extends CandidateProvider {

    //TODO:hardcode
    public static final String inputPath = "/Volumes/Ex_Volume/msmarco/msmarco_v2_doc/";
    public static final String modelPath = "/Users/snikem/proga/FactorFactory/catboost_model.cbm";
    // Путь к файлу для логов скоринга
    public static final String debugPath = "/Volumes/Ex_Volume/msmarcoProcces/results/dev/my_tests/monoforest_run/debug_score.log";

    final Logger logger = LoggerFactory.getLogger(MonoforestCandidateProvide.class);

    private FactorManager fm;
    private DocFeatureStore docFeatureStore;
    private String mQueryFieldName;

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    /**
     * Constructor.
     * @param addConf additional/optional configuration: can be null
     * @throws Exception
     */
    // Исправил конструктор, добавив String uri (нужен для совместимости с ResourceManager)
    public MonoforestCandidateProvide(String uri, RestrictedJsonConfig addConf) throws Exception {

        if (addConf == null) {
            mQueryFieldName = Const.DEFAULT_QUERY_TEXT_FIELD_NAME;
        } else {
            mQueryFieldName = addConf.getParam(CommonParams.QUERY_FIELD_NAME, Const.DEFAULT_QUERY_TEXT_FIELD_NAME);
        }

        fm = new FactorManager();
        logger.info("factor manager create with {} factors", fm.getTotalFeatureCount());
        //TODO: hardcode
        docFeatureStore = new DocFeatureStore("/Volumes/Ex_Volume/msmarcoProcces/doc_features.bin");
        logger.info("number of docs {}", docFeatureStore.getVectorSize());
    }

    @Override
    public boolean isThreadSafe() { return true; }

    @Override
    public CandidateInfo getCandidates(int queryNum,
                                       DataEntryFields queryFields,
                                       int maxQty) throws Exception {
        String queryID = queryFields.mEntryId;
        if (null == queryID) {
            throw new Exception("Query id  is undefined for query #: " + queryNum);
        }

        ArrayList<CandidateEntry> resArr = new ArrayList<CandidateEntry>();

        String query = queryFields.getString(mQueryFieldName);
        if (null == query) {
            throw new Exception(
                    String.format("Query (%s) is undefined for query # %d", mQueryFieldName, queryNum));
        }

        long numFound = 0;

        if (query.isEmpty()) {
            logger.warn("Ignoring empty query #: " + queryNum);
        } else {
            float[] queryFactor = fm.extractQueryFactors(query);

            // Добавляем BufferedWriter в try-with-resources
            try (
                    MsMarcoRawReader reader = new MsMarcoRawReader(inputPath);
                    CatBoostInference model = new CatBoostInference(modelPath);
                    // Открываем файл в режиме append (true), чтобы не перезаписывать его каждым запросом
                    BufferedWriter writer = new BufferedWriter(new FileWriter(debugPath, true))
            ) {

                MsMarcoRawReader.DocEntry doc;
                int count = 0;

                while ((doc = reader.getNext()) != null) {
                    float[] docFactor = docFeatureStore.getFeatures(doc.id);
                    float[] jointFactor = fm.extractJointFactors(query, doc.title, doc.fullText, doc.id);
                    float[] totalVector = fm.mergeFactors(jointFactor, queryFactor, docFactor);

                    float score = model.predictProbability(totalVector);

                    resArr.add(new CandidateEntry(doc.id, score));

                    // --- Логирование каждого 100-го документа ---
                    if (count % 10000 == 0 ) {
                        logger.info("Query:{}| DocID:{}| Score: {}", query, doc.id, score);
                    }
                    // Логируем в консоль реже, чтобы не спамить
                    if (count % 10000 == 0) {
                        logger.info("{} обработал ", count);
                    }

                    count++;
                }

                numFound = count; // Обновляем общее количество найденных документов
                System.out.println("Done! Total docs: " + count);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        CandidateEntry[] results = resArr.toArray(new CandidateEntry[resArr.size()]);
        Arrays.sort(results);

        if (results.length > maxQty) {
            results = Arrays.copyOf(results, maxQty);
        }
        System.out.println("given candidate for query: " + query);

        return new CandidateInfo(numFound, results);
    }
    //TODO: удалить
    public static final String debugDocFile = "/Volumes/Ex_Volume/msmarcoProcces/target_doc_body.txt";

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("STARTING SINGLE DOC TEST (FROM FILE)");
        System.out.println("==================================================");

        // Входные данные
        String targetDocId = "msmarco_doc_16_2049958358";
        String testQuery = "androgen receptor define";
        String docTitle = "Test Title (can be empty if unknown)"; // Если знаете заголовок, впишите, это влияет на фичи

        try {
            // 1. Читаем текст документа из файла
            System.out.println("1. Reading document text from: " + debugDocFile);
            String docBody = new String(Files.readAllBytes(Paths.get(debugDocFile)));

            System.out.println("   Text length: " + docBody.length() + " chars");
            if (docBody.length() > 100) System.out.println("   Snippet: " + docBody.substring(0, 100) + "...");

            // 2. Инициализация
            System.out.println("2. Initializing Provider...");
            MonoforestCandidateProvide provider = new MonoforestCandidateProvide("dummy", null);

            System.out.println("3. Loading Model...");
            try (CatBoostInference model = new CatBoostInference(modelPath)) {

                // 4. Расчет факторов
                System.out.println("4. Calculating features...");

                // A. Query Factors
                float[] queryFactors = provider.fm.extractQueryFactors(testQuery);
                System.out.println("   Query Factors: " + Arrays.toString(queryFactors));

                // B. Doc Factors (из базы pre-computed)
                float[] docFactors = provider.docFeatureStore.getFeatures(targetDocId);
                if (docFactors == null) {
                    System.err.println("WARNING: Doc features not found in .bin file for ID: " + targetDocId);
                    System.err.println("Using zero vector for doc features.");
                    docFactors = new float[provider.docFeatureStore.getVectorSize()];
                }
                System.out.println("   Doc Factors: " + Arrays.toString(docFactors));

                // C. Joint Factors (самое важное - тут работает текст из файла)
                float[] jointFactors = provider.fm.extractJointFactors(testQuery, docTitle, docBody, targetDocId);
                System.out.println("   Joint Factors: " + Arrays.toString(jointFactors));

                // 5. Слияние
                float[] totalVector = provider.fm.mergeFactors(jointFactors, queryFactors, docFactors);
                System.out.println("   TOTAL VECTOR: " + Arrays.toString(totalVector));

                // 6. ПРЕДСКАЗАНИЕ
                float score = model.predictProbability(totalVector);

                System.out.println("\n--------------------------------------------------");
                System.out.println(">>> PREDICTED SCORE: " + String.format("%.6f", score) + " <<<");
                System.out.println("--------------------------------------------------");
            }

        } catch (IOException e) {
            System.err.println("ERROR READING FILE: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}