package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.factors;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.QueryFactor;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.io.InputStream;

public class QueryQuantityNouns extends QueryFactor {

    private POSTaggerME tagger;
    private SimpleTokenizer tokenizer;
    private boolean isReady = false;

    @Override
    public String getName() {
        return "QueryQuantityNouns";
    }

    @Override
    public int getFeatureQty() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Количество существительных в запросе (требует OpenNLP)";
    }

    @Override
    public void prepare() {
        try {
            // Загружаем модель POS-таггера из ресурсов
            // Файл en-pos-maxent.bin должен лежать в папке src/main/resources
            InputStream modelIn = getClass().getResourceAsStream("/en-pos-maxent.bin");
            if (modelIn != null) {
                POSModel model = new POSModel(modelIn);
                this.tagger = new POSTaggerME(model);
                this.tokenizer = SimpleTokenizer.INSTANCE;
                this.isReady = true;
            } else {
                System.err.println("Warning: POS Model not found in resources (/en-pos-maxent.bin). Feature will return 0.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public float[] calculateScore(String query) {
        if (!isReady || query == null || query.isEmpty()) {
            return new float[]{0f};
        }

        // 1. Токенизация (разбиваем на слова)
        String[] tokens = tokenizer.tokenize(query);

        // 2. POS-теггинг (определяем части речи)
        String[] tags = tagger.tag(tokens);

        // 3. Подсчет существительных
        int nounCount = 0;
        for (String tag : tags) {
            // В Penn Treebank теги существительных начинаются с NN:
            // NN (noun, singular), NNS (noun plural), NNP (proper noun), NNPS...
            if (tag.startsWith("NN")) {
                nounCount++;
            }
        }

        return new float[] { (float) nounCount };
    }
}