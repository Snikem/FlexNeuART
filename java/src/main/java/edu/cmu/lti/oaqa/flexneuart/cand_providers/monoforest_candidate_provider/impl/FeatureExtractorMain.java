package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FeatureExtractorMain {

    // === НАСТРОЙКИ ПУТЕЙ ===
    // Папка с входными файлами (00..59)
    private static final String INPUT_DIR = "/Volumes/Ex_Volume/msmarco/negative_qrels_with_queries";

    // Куда сохранить итоговый файл
    private static final String OUTPUT_FILE = "/Users/snikem/Desktop/new_dataset/train_negative.tsv";

    public static void main(String[] args) {
        System.out.println("=== Feature Extraction Started ===");

        // 1. Инициализация менеджера (загрузка словарей, Lucene и т.д.)
        // Это делается один раз перед циклом!
        System.out.println("Initializing FactorManager...");
        FactorManager manager = new FactorManager();
        System.out.println("FactorManager ready. Total features: " + manager.getTotalFeatureCount());

        int totalProcessed = 0;
        int errorCount = 0;

        // Открываем файл для записи результатов
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(OUTPUT_FILE)))) {

            // 2. Цикл по 60 файлам (от 00 до 59)
            for (int i = 0; i < 60; i++) {
                // Формируем имя файла: qrels_doc_00_with_docs.tsv
                String fileName = String.format("qrels_doc_%02d_with_docs.tsv", i);
                File inputFile = new File(INPUT_DIR, fileName);

                if (!inputFile.exists()) {
                    System.out.println("Skipping missing file: " + fileName);
                    continue;
                }

                System.out.println("Processing file: " + fileName);

                // Читаем файл построчно
                try (BufferedReader br = Files.newBufferedReader(inputFile.toPath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        try {
                            // 3. Парсинг строки
                            // Формат: Query \t 0 \t DocID \t Label \t JSON
                            String[] parts = line.split("\t");

                            if (parts.length < 5) {
                                continue; // Битая строка
                            }

                            String queryText = parts[0];
                            String docId = parts[2];
                            String label = parts[3];
                            JSONObject json = getJsonObject(parts);

                            // Извлекаем поля для факторов
                            String title = json.optString("title", "");
                            String body = json.optString("body", "");
                            // Можно склеить headings и body, если нужно
                            String headings = json.optString("headings", "");
                            String fullDocumentText = headings + "\n" + body;

                            // 4. Расчет факторов
                            float[] features = manager.extractAll(queryText, title, fullDocumentText, docId);

                            // 5. Запись в выходной файл
                            writer.print(label);
                            writer.print("\t");
                            writer.print("\t");
                            writer.print(docId);

                            for (float f : features) {
                                writer.print("\t");
                                writer.print(f); // Java выведет 1.0, 0.5 и т.д.
                            }
                            writer.println();

                            totalProcessed++;

                            if (totalProcessed % 1000 == 0) {
                                System.out.print("\rProcessed lines: " + totalProcessed);
                            }

                        } catch (Exception e) {
                            // Ошибки парсинга отдельной строки не должны ломать весь процесс
                            errorCount++;
                            // e.printStackTrace(); // Раскомментируйте для отладки
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error reading file: " + fileName);
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            System.err.println("Error creating output file: " + OUTPUT_FILE);
            e.printStackTrace();
        }

        System.out.println("\n=== Done ===");
        System.out.println("Total lines processed: " + totalProcessed);
        System.out.println("Errors/Skipped lines: " + errorCount);
        System.out.println("Output saved to: " + OUTPUT_FILE);
    }

    @NotNull
    private static JSONObject getJsonObject(String[] parts) {
        String jsonRaw = parts[4];

        // Очистка JSON от CSV-экранирования
        // Строка приходит в виде: "{""url"": ...}"
        // Нужно убрать кавычки по краям и заменить двойные кавычки внутри
        if (jsonRaw.startsWith("\"") && jsonRaw.endsWith("\"")) {
            jsonRaw = jsonRaw.substring(1, jsonRaw.length() - 1);
        }
        jsonRaw = jsonRaw.replace("\"\"", "\"");

        // Парсим JSON
        JSONObject json = new JSONObject(jsonRaw);
        return json;
    }
}