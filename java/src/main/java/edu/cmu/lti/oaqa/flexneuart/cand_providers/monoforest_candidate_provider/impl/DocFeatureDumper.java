package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

public class DocFeatureDumper {

    // === НАСТРОЙКИ ===
    // Путь к папке, где лежат файлы msmarco_doc_00.gz ... msmarco_doc_59.gz
    private static final String INPUT_DIR = "/Volumes/Ex_Volume/msmarco/msmarco_v2_doc/";

    // Куда сохранить результат
    private static final String OUTPUT_BINARY_FILE = "/Volumes/Ex_Volume/msmarcoProcces/doc_features.bin";

    public static void main(String[] args) {
        System.out.println("=== Starting Feature Dumper from GZ JSONL ===");

        // 1. Инициализация менеджера факторов
        System.out.println("Initializing FactorManager...");
        FactorManager manager = new FactorManager();
        int docFeatSize = manager.getDocFactorCount();
        System.out.println("Feature vector size: " + docFeatSize);

        int totalDocsProcessed = 0;
        long startTime = System.currentTimeMillis();

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(OUTPUT_BINARY_FILE))))) {

            // 2. Пишем заголовок (Header)
            // Плейсхолдер для количества документов (запишем 0, потом обновим)
            dos.writeInt(0);
            // Размер вектора признаков
            dos.writeInt(docFeatSize);

            // 3. Цикл по 60 файлам
            for (int i = 0; i < 60; i++) {
                // Формируем имя файла: msmarco_doc_00.gz
                String fileName = String.format("msmarco_doc_%02d.gz", i);
                File file = new File(INPUT_DIR, fileName);

                if (!file.exists()) {
                    System.err.println("Warning: File not found: " + file.getAbsolutePath());
                    continue;
                }

                System.out.println("Processing file (" + (i + 1) + "/60): " + fileName);

                // Читаем GZIP построчно
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(file.toPath())), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = br.readLine()) != null) {
                        try {
                            // 4. Парсинг JSON
                            JSONObject json = new JSONObject(line);

                            String docId = json.getString("docid");
                            String title = json.optString("title", "");
                            String headings = json.optString("headings", "");
                            String body = json.optString("body", "");

                            // Собираем полный текст документа: Заголовки + Тело
                            // Это важно, чтобы факторы учитывали весь контент
                            String fullBodyText = headings + "\n" + body;

                            // 5. Расчет факторов
                            // ВАЖНО: Убедитесь, что в FactorManager есть метод extractDocFeatures(title, document, docId)
                            float[] feats = manager.extractDocFactors(title, fullBodyText, docId);

                            // 6. Запись в бинарник
                            dos.writeUTF(docId);
                            for (float f : feats) {
                                dos.writeFloat(f);
                            }

                            totalDocsProcessed++;
                            if (totalDocsProcessed % 10000 == 0) {
                                System.out.print("\rTotal Docs: " + totalDocsProcessed);
                            }

                        } catch (Exception e) {
                            // Ошибки парсинга одной строки не должны ломать всё
                            // e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error reading file: " + fileName);
                    e.printStackTrace();
                }
            }

            // Сбрасываем буферы
            dos.flush();

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("\n\nAll files processed. Total docs: " + totalDocsProcessed);

        // 7. ОБНОВЛЕНИЕ ЗАГОЛОВКА
        // Теперь мы знаем точное количество документов, нужно вернуться в начало файла и записать его.
        System.out.println("Patching file header with total count...");
        try (RandomAccessFile raf = new RandomAccessFile(OUTPUT_BINARY_FILE, "rw")) {
            raf.seek(0); // Встаем в самое начало
            raf.writeInt(totalDocsProcessed); // Перезаписываем первые 4 байта (плейсхолдер)
        } catch (IOException e) {
            System.err.println("Error patching header!");
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Done in " + (endTime - startTime) / 1000 + " sec.");
        System.out.println("Output: " + OUTPUT_BINARY_FILE);
    }
}