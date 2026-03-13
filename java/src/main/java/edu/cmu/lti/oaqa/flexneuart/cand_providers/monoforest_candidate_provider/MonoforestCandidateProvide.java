package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
    private static final int EXPECTED_FEATURES = 27;
    private static final String BINARIES_DIR = "/Volumes/Ex_Volume/msmarcoProcces/final2/";

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

        Path jsonPath = Paths.get(BINARIES_DIR, queryID + ".json");
        if (!Files.exists(jsonPath)) {
            logger.warn("JSON файл не найден: {}", jsonPath);
            return new CandidateInfo(0, new CandidateEntry[0]);
        }

        ArrayList<CandidateEntry> resArr = new ArrayList<>();
        long numFound = 0;
        float[] featureVector = new float[EXPECTED_FEATURES];

        try (BufferedReader br = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    // Создаем JSONObject прямо из строки
                    JSONObject jsonObject = new JSONObject(line);

                    String docId = jsonObject.getString("doc_id");
                    JSONArray featuresArray = jsonObject.optJSONArray("features");

                    // В org.json для размера массива используется метод length()
                    if (featuresArray == null || featuresArray.length() != EXPECTED_FEATURES) {
                        int got = (featuresArray != null) ? featuresArray.length() : 0;
                        logger.error("Пропуск doc {}: ожидалось {} фичей, получено {}", docId, EXPECTED_FEATURES, got);
                        continue;
                    }

                    // Заполняем массив
                    for (int i = 0; i < EXPECTED_FEATURES; i++) {
                        // getDouble возвращает double, кастим во float для модели
                        featureVector[i] = (float) featuresArray.getDouble(i);
                    }

                    float score = catBoostModel.predictProbability(featureVector);
                    resArr.add(new CandidateEntry(docId, score));
                    numFound++;

                } catch (JSONException e) {
                    logger.error("Ошибка парсинга JSON в строке: " + line, e);
                }
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
        try {

            MonoforestCandidateProvide provider = new MonoforestCandidateProvide("local_test", null);

            String queryId = "2";

            // Для работы провайдера также требуется текст запроса (хотя ваш код сейчас читает из бинарника)
            // Создаем объект полей запроса
            DataEntryFields queryFields = new DataEntryFields(queryId);

            System.out.println("Поиск кандидатов для запроса: " + queryId + "...");

            // 4. Получение кандидатов (запрашиваем топ-10)
            int maxQty = 10;
            CandidateInfo info = provider.getCandidates(0, queryFields, maxQty);

            // 5. Вывод результатов
            System.out.println("\n--- Топ 10 документов ---");
            if (info.mEntries.length == 0) {
                System.out.println("Документы не найдены или бинарный файл отсутствует.");
            } else {
                for (int i = 0; i < info.mEntries.length; i++) {
                    CandidateEntry entry = info.mEntries[i];
                    System.out.printf("%d. DocID: %s | Score: %.6f%n",
                            (i + 1), entry.mDocId, entry.mScore);
                }
            }

            System.out.println("\nВсего обработано документов в файле: " + info.mNumFound);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}