
package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider;

import java.util.*;
import java.io.*;
import java.nio.file.Paths;

import edu.cmu.lti.oaqa.flexneuart.cand_providers.CandidateEntry;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.CandidateInfo;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.CandidateProvider;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.CatBoostInference;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.DocFeatureStore;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.FactorManager;
import edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl.MsMarcoRawReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.lti.oaqa.flexneuart.letor.CommonParams;
import edu.cmu.lti.oaqa.flexneuart.resources.RestrictedJsonConfig;
import edu.cmu.lti.oaqa.flexneuart.simil_func.BM25SimilarityLucene;
import edu.cmu.lti.oaqa.flexneuart.utils.Const;
import edu.cmu.lti.oaqa.flexneuart.utils.DataEntryFields;
import edu.cmu.lti.oaqa.flexneuart.utils.StringUtils;

import com.google.common.base.Splitter;

public class MonoforestCandidateProvide extends CandidateProvider {

    //TODO:hardcode
    public static final String inputPath = "/Volumes/Ex_Volume/msmarco/msmarco_v2_doc/";
    public static final String modelPath = "/Users/snikem/proga/FactorFactory/catboost_model.onnx";
    final Logger logger = LoggerFactory.getLogger(MonoforestCandidateProvide.class);

    private FactorManager fm;
    private DocFeatureStore docFeatureStore;

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    /**
     * Constructor.

     * @param addConf         additional/optional configuration: can be null
     * @throws Exception
     */
    public MonoforestCandidateProvide(RestrictedJsonConfig addConf) throws Exception {

        if (addConf == null) {
            mQueryFieldName = Const.DEFAULT_QUERY_TEXT_FIELD_NAME;
        } else {
            mQueryFieldName = addConf.getParam(CommonParams.QUERY_FIELD_NAME, Const.DEFAULT_QUERY_TEXT_FIELD_NAME);
        }

        fm = new FactorManager();
        logger.info("factor manager create with {} factors", fm.getTotalFeatureCount());
        //TODO: hardcode
        docFeatureStore = new DocFeatureStore("/Volumes/Ex_Volume/msmarcoProcces/doc_features.bin");
        logger.info("number of docs {}", docFeatureStore.getVectorSize());
    }

    /*
     *  The function getCandidates is thread-safe, because IndexSearcher is thread safe:
     *  https://wiki.apache.org/lucene-java/LuceneFAQ#Is_the_IndexSearcher_thread-safe.3F
     */
    @Override
    public boolean isThreadSafe() { return true; }

    @Override
    public CandidateInfo getCandidates(int queryNum,
                                       DataEntryFields queryFields,
                                       int maxQty) throws Exception {
        String queryID = queryFields.mEntryId;
        if (null == queryID) {
            throw new Exception("Query id  is undefined for query #: " + queryNum);
        }

        ArrayList<CandidateEntry> resArr = new ArrayList<CandidateEntry>();

        String query = queryFields.getString(mQueryFieldName);
        if (null == query) {
            throw new Exception(
                    String.format("Query (%s) is undefined for query # %d", mQueryFieldName, queryNum));
        }

        long    numFound = 0;

        if (query.isEmpty()) {
            logger.warn("Ignoring empty query #: " + queryNum);
        } else {
            float[] queryFactor = fm.extractQueryFactors(query);
            try (
                    MsMarcoRawReader reader = new MsMarcoRawReader(inputPath);
                    CatBoostInference model = new CatBoostInference(modelPath)
            ) {

                MsMarcoRawReader.DocEntry doc;
                int count = 0;

                // Простой цикл, как вы хотели
                while ((doc = reader.getNext()) != null) {
                    float[] docFactor = docFeatureStore.getFeatures(doc.id);
                    float[] jointFactor = fm.extractJointFactors(query, doc.title, doc.fullText, doc.id);
                    float[] totalVector = fm.mergeFactors(jointFactor, queryFactor, docFactor);

                    float score = model.predictProbability(totalVector);

                    resArr.add(new CandidateEntry(doc.id, score));
                    if (count % 1000000 == 0) {
                        logger.info("{} обработал ", count);
                    }
                    count++;
                }

                System.out.println("Done! Total docs: " + count);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        CandidateEntry[] results = resArr.toArray(new CandidateEntry[resArr.size()]);
        Arrays.sort(results);

        if (results.length > maxQty) {
            results = Arrays.copyOf(results, maxQty);
        }
        System.out.println("given candidate for query: " + query);
        return new CandidateInfo(numFound, results);

    }

    private String        mQueryFieldName;
}
