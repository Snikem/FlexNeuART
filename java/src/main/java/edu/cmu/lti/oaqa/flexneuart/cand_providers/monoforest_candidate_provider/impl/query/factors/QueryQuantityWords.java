package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.factors;


import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.QueryFactor;
import java.util.Map;

public class QueryQuantityWords extends QueryFactor {

    @Override
    public String getName() {
        return "QuantityWordsFactor";
    }

    @Override
    public float[] calculateScore(String query) {
        float[] result = new float[1];

        result[0] = countWords(query);

        return result;
    }

    @Override
    public int getFeatureQty() {
        return 1; // Мы возвращаем 3 числа: len(query)
    }

    @Override
    public String getDescription() {
        return "Считает длину слов в запросе";
    }

    @Override
    public void prepare() {

    }

    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        // Делим по пробельным символам (пробел, таб, перенос строки)
        return text.trim().split("\\s+").length;
    }
}