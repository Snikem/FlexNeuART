package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl;

public abstract class BaseFactor {


    public abstract String getName();

    public abstract int getFeatureQty();

    public abstract String getDescription();

    public abstract void prepare();
}