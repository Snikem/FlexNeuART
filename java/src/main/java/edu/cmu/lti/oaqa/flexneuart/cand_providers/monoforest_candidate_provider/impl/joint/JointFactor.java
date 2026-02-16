package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.BaseFactor;

import java.util.ArrayList;

public abstract class JointFactor extends BaseFactor {

    public abstract float[] calculateScore(String query, String title, String document, String doc_id);
    public abstract ArrayList<float[]> calculateForQueries(ArrayList<String> queries, String title, String document, String doc_id);

}