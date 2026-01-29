package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.QueryFactor;

public class QueryQuantitySpecialCharacters extends QueryFactor {

    @Override
    public String getName() {
        return "QueryQuantitySpecialCharacters";
    }

    @Override
    public float[] calculateScore(String query) {
        if (query == null) {
            return new float[]{0f};
        }

        // Логика Python: re.sub(r'[a-zA-Z0-9\s]', '', query)
        // Заменяем все a-z, A-Z, 0-9 и пробелы на пустоту. Оставшееся - спецсимволы.
        String specialChars = query.replaceAll("[a-zA-Z0-9\\s]", "");

        return new float[] { (float) specialChars.length() };
    }

    @Override
    public int getFeatureQty() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Количество специальных символов в запросе";
    }

    @Override
    public void prepare() {
    }
}