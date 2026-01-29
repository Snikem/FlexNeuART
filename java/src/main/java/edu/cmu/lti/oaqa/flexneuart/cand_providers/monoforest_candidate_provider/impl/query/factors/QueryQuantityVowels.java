package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.QueryFactor;

public class QueryQuantityVowels extends QueryFactor {

    @Override
    public String getName() {
        return "QueryQuantityVowels";
    }

    @Override
    public float[] calculateScore(String query) {
        float[] result = new float[1];
        result[0] = countVowels(query);
        return result;
    }

    @Override
    public int getFeatureQty() {
        return 1; // Возвращаем 1 число: кол-во гласных в запросе
    }

    @Override
    public String getDescription() {
        return "Считает количество гласных в запросе.";
    }

    @Override
    public void prepare() {
        // Нет предварительной подготовки
    }

    private int countVowels(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        String vowels = "aeiouAEIOU";
        for (int i = 0; i < text.length(); i++) {
            if (vowels.indexOf(text.charAt(i)) != -1) {
                count++;
            }
        }
        return count;
    }
}