package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.document;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.BaseFactor;

public abstract class DocumentFactor extends BaseFactor {

    public abstract float[] calculateScore(String title, String document, String doc_id);

}