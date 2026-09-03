package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AppConfig {
    private static final Properties properties = new Properties();

    // Статический блок инициализируется один раз при загрузке класса
    static {
        try (FileInputStream fis = new FileInputStream("/Users/snikem/proga/FlexNeuART/java/src/main/java/edu/cmu/lti/oaqa/flexneuart/cand_providers/monoforest_candidate_provider/impl/.env")) {
            properties.load(fis);
        } catch (IOException e) {
            System.err.println("Предупреждение: Файл .env не найден в корне проекта. Убедитесь, что он создан.");
        }
    }

    /**
     * Получает значение ключа. Сначала ищет в системных переменных окружения,
     * затем в нашем .env файле.
     */
    public static String get(String key) {
        String envVar = System.getenv(key);
        if (envVar != null) {
            return envVar;
        }
        return properties.getProperty(key);
    }

    public static String getIndexDir() {
        String path = get("LUCENE_INDEX_DIR");
        if (path == null || path.isEmpty()) {
            throw new RuntimeException("ВНИМАНИЕ: LUCENE_INDEX_DIR не задан в .env файле!");
        }
        return path;
    }

    public static String getTextField() {
        String field = get("LUCENE_TEXT_FIELD");
        return (field != null && !field.isEmpty()) ? field : "text";
    }

    public static String getTitleField() {
        String field = get("LUCENE_TITLE_FIELD");
        return (field != null && !field.isEmpty()) ? field : "title";
    }

    public static String getInputDir() {
        String path = get("MSMARCO_INPUT_DIR");
        if (path == null || path.isEmpty()) {
            throw new RuntimeException("ВНИМАНИЕ: MSMARCO_INPUT_DIR не задан в .env файле!");
        }
        return path;
    }
}