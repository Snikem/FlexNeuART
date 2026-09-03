package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.features;

public class FeatureBase {
    String name;
    public float value;

    public FeatureBase(String name, float value) {
        this.name = name;
        this.value = value;
    }
}
