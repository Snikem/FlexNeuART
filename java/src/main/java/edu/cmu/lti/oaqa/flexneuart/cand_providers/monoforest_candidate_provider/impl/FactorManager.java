package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.document.factors.*;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.document.DocumentFactor;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.JointFactor;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.joint.factors.*;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.QueryFactor;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.query.factors.*;

import java.util.ArrayList;
import java.util.List;


public class FactorManager {

    // Три отдельных списка
    private List<QueryFactor> queryFactors = new ArrayList<>();
    private List<DocumentFactor> docFactors = new ArrayList<>();
    private List<JointFactor> jointFactors = new ArrayList<>();

    private int totalFeatureCount = 0;

    public FactorManager() {
        // === РЕГИСТРАЦИЯ ФАКТОРОВ ===

        addQueryFactor(new QueryQuantityWords());
        addDocFactor(new DocumentQuantityWords());

        addQueryFactor(new QueryQuantityVowels());
        addDocFactor(new DocumentQuantityVowels());

        addQueryFactor(new QueryQuantitySpecialCharacters());
        addDocFactor(new DocumentQuantitySpecialCharacters());

        addDocFactor(new DocumentQuantityParagraphs());

        addQueryFactor(new QueryQuantityNumbers());
        addDocFactor(new DocumentQuantityNumbers());

        addQueryFactor(new QueryQuantityNouns());

        addDocFactor(new DocumentQuantityNonDictionaryWords());
        addQueryFactor(new QueryQuantityNonDictionaryWords());

        addDocFactor(new DocumentQuantityKByte());

        addQueryFactor(new QueryQuantityConsonants());

        addDocFactor(new DocumentNonDictionaryRatio());

        addDocFactor(new DocumentHasUrls());

        addQueryFactor(new QueryFrequencyWords());

        addJointFactor(new JointBM25());

        addJointFactor(new JointTFIDF());

        addJointFactor(new JointTitleMatchCount());

        addJointFactor(new JointQueryTfSum());

        addJointFactor(new JointExactMatchCount());


    }

    public int getQueryFactorCount() {
        return queryFactors.stream().mapToInt(QueryFactor::getFeatureQty).sum();
    }

    public int getDocFactorCount() {
        return docFactors.stream().mapToInt(DocumentFactor::getFeatureQty).sum();
    }

    public int getJointFactorCount() {
        return jointFactors.stream().mapToInt(JointFactor::getFeatureQty).sum();
    }

    public float[] extractDocFactors(String title, String document, String docId) {
        float[] features = new float[getDocFactorCount()];
        int currentIdx = 0;

        for (DocumentFactor f : docFactors) {
            float[] res = f.calculateScore(title, document, docId);
            System.arraycopy(res, 0, features, currentIdx, res.length);
            currentIdx += res.length;
        }
        return features;
    }

    public float[] extractQueryFactors(String queryText) {
        float[] features = new float[getQueryFactorCount()];
        int currentIdx = 0;

        for (QueryFactor f : queryFactors) {
            float[] res = f.calculateScore(queryText);
            System.arraycopy(res, 0, features, currentIdx, res.length);
            currentIdx += res.length;
        }
        return features;
    }

    public float[] extractJointFactors(String queryText, String title, String document, String docId) {
        float[] features = new float[getJointFactorCount()];
        int currentIdx = 0;

        for (JointFactor f : jointFactors) {
            float[] res = f.calculateScore(queryText, title, document, docId);
            System.arraycopy(res, 0, features, currentIdx, res.length);
            currentIdx += res.length;
        }
        return features;
    }

    public float[] mergeFactors(float[] joint, float[] query, float[] doc) {
        float[] all = new float[joint.length + query.length + doc.length];
        System.arraycopy(joint, 0, all, 0, joint.length);
        System.arraycopy(query, 0, all, joint.length, query.length);
        System.arraycopy(doc, 0, all, joint.length + query.length, doc.length);
        return all;
    }

    // Методы добавления
    private void addQueryFactor(QueryFactor f) {
        queryFactors.add(f);
        f.prepare();
        totalFeatureCount += f.getFeatureQty();
    }

    private void addDocFactor(DocumentFactor f) {
        docFactors.add(f);
        f.prepare();
        totalFeatureCount += f.getFeatureQty();
    }

    private void addJointFactor(JointFactor f) {
        jointFactors.add(f);
        f.prepare();
        totalFeatureCount += f.getFeatureQty();
    }

    public int getTotalFeatureCount() {
        return totalFeatureCount;
    }

    // Главный метод расчета
    public float[] extractAll(String queryText, String title, String document, String doc_id) {
        float[] allFeatures = new float[totalFeatureCount];
        int currentIdx = 0;

        for (JointFactor f : jointFactors) {
            float[] res = f.calculateScore(queryText, title, document, doc_id);
            System.arraycopy(res, 0, allFeatures, currentIdx, res.length);
            currentIdx += res.length;
        }

        for (QueryFactor f : queryFactors) {
            float[] res = f.calculateScore(queryText);
            System.arraycopy(res, 0, allFeatures, currentIdx, res.length);
            currentIdx += res.length;
        }
        //TODO не понятно что делать с массивом состоящим только из float
        //TODO: надо делать индексацию и брать данные из нее
        for (DocumentFactor f : docFactors) {
            float[] res = f.calculateScore(title, document, doc_id);
            System.arraycopy(res, 0, allFeatures, currentIdx, res.length);
            currentIdx += res.length;
        }


        return allFeatures;
    }
}