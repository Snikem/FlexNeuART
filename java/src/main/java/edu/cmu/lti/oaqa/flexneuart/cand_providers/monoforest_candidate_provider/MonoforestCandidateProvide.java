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
    private CatBoostInference catBoostModel;
    private static final int EXPECTED_FEATURES = 24;
    private static final String BINARIES_DIR = "/Volumes/Ex_Volume/msmarcoProcces/final_binaries/";

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
        catBoostModel = new CatBoostInference(modelPath);

    }

    @Override
    public boolean isThreadSafe() { return true; }

    @Override
    public CandidateInfo getCandidates(int queryNum,
                                       DataEntryFields queryFields,
                                       int maxQty) throws Exception {
        String queryID = queryFields.mEntryId;
        if (queryID == null) throw new Exception("Query id is undefined for query #: " + queryNum);

        String query = queryFields.getString(mQueryFieldName);
        if (query == null) throw new Exception("Query is undefined for query #: " + queryNum);

        if (query.isEmpty()) {
            logger.warn("Ignoring empty query #: {}", queryNum);
            return new CandidateInfo(0, new CandidateEntry[0]);
        }

        java.nio.file.Path binPath = java.nio.file.Paths.get(BINARIES_DIR, queryID + ".bin");
        if (!java.nio.file.Files.exists(binPath)) {
            logger.warn("Бинарный файл не найден: {}", binPath);
            return new CandidateInfo(0, new CandidateEntry[0]);
        }

        ArrayList<CandidateEntry> resArr = new ArrayList<>();
        long numFound = 0;

        // ⚡ ОПТИМИЗАЦИЯ ПАМЯТИ: Создаем массив один раз и переиспользуем его
        float[] featureVector = new float[EXPECTED_FEATURES];

        try (java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(binPath), 65536))) {
            // Увеличили буфер для ускорения I/O

            try {
                // В Java чтение DataInputStream до EOFException — это стандартный паттерн,
                // если в начале файла не записано количество элементов.
                while (true) {
                    String fileQueryId = dis.readUTF();
                    String docId = dis.readUTF();
                    int totalFloats = dis.readInt();

                    if (totalFloats != EXPECTED_FEATURES) {
                        logger.error("Пропуск doc {}: ожидалось {} фичей, получено {}", docId, EXPECTED_FEATURES, totalFloats);
                        // Пропускаем "битые" байты, чтобы не сломать чтение следующих документов
                        for (int i = 0; i < totalFloats; i++) dis.readFloat();
                        continue;
                    }

                    // ⚡ Записываем данные прямо в переиспользуемый массив
                    for (int i = 0; i < EXPECTED_FEATURES; i++) {
                        featureVector[i] = dis.readFloat();
                    }

                    // Отправляем в уже загруженную модель
                    float score = catBoostModel.predictProbability(featureVector);
                    resArr.add(new CandidateEntry(docId, score));
                    numFound++;
                }
            } catch (java.io.EOFException e) {
                // Конец файла. Обработка завершена штатно.
            }
        } catch (java.io.IOException e) {
            logger.error("Ошибка ввода-вывода при чтении: " + queryID, e);
        }

        CandidateEntry[] results = resArr.toArray(new CandidateEntry[0]);
        Arrays.sort(results);

        if (results.length > maxQty) {
            results = Arrays.copyOf(results, maxQty);
        }

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