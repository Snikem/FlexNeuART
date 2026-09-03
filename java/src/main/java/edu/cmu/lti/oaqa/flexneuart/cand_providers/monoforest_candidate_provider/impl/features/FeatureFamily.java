package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.features;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.DocumentMarco;
import org.apache.lucene.search.*;

import java.util.List;

public interface FeatureFamily {
    String getNameFamily();
    String getDescription();
    List<String> getAllFeaturesNames();
    Query buildLuceneQuery(String featureName, String[] queryTokenStream, Object... args);
    void prepare();
    List<FeatureBase> calculateAllFeaturesInFamily(String[] queryTokenStream, DocumentMarco document);
    FeatureBase calculateFeatureByName(String FeatureName, String[] queryTokenStream, DocumentMarco document);

}
