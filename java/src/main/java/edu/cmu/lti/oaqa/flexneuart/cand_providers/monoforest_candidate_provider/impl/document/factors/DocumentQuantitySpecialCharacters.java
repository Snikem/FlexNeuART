package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.document.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.document.DocumentFactor;

public class DocumentQuantitySpecialCharacters extends DocumentFactor {

    @Override
    public String getName() {
        return "DocumentQuantitySpecialCharacters";
    }

    @Override
    public float[] calculateScore(String title, String document, String doc_id) {

        String text = title +
                document;

        // Удаляем английские буквы, цифры и пробелы
        String specialChars = text.replaceAll("[a-zA-Z0-9\\s]", "");

        return new float[] { (float) specialChars.length() };
    }

    @Override
    public int getFeatureQty() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Количество специальных символов в документе (заголовок + текст)";
    }

    @Override
    public void prepare() {
    }
}