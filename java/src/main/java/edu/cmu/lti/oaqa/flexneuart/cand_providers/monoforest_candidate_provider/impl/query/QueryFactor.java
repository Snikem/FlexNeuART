package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.BaseFactor;

public abstract class QueryFactor extends BaseFactor {

    public abstract float[] calculateScore(String query);

}