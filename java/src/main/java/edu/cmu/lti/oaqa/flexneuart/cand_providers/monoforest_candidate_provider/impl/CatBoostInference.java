package edu.cmu.lti.oaqa.flexneuart.cand_providers.monoforest_candidate_provider.impl;

import ai.onnxruntime.*;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CatBoostInference implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    public CatBoostInference(String modelPath) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        // Используем базовую оптимизацию для ускорения
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);

        this.session = env.createSession(modelPath, opts);

        // Обычно у CatBoost вход называется "features", но лучше получить имя динамически
        this.inputName = session.getInputNames().iterator().next();
        System.out.println("CatBoost model loaded. Input name: " + inputName);
    }

    /**
     * Возвращает вероятность класса "1" (релевантно).
     */
    public float predictProbability(float[] features) throws OrtException {
        // Форма входа: [1 строка, N признаков]
        long[] shape = new long[]{1, features.length};

        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(inputName, tensor);

        // Запускаем
        try (OrtSession.Result results = session.run(inputs)) {
            // CatBoost Classifier в ONNX обычно возвращает 2-й элемент (probabilities)
            // Это тензор размерности [1, 2] (для бинарной классификации: [prob_0, prob_1])

            // Получаем выход "probabilities" (обычно это второй выход, индекс 1)
            // Но надежнее найти его по имени или типу. Обычно он идет вторым.
            OnnxValue probValue = results.get(1);

            float[][] probs = (float[][]) probValue.getValue();

            // probs[0][1] — это вероятность класса 1
            return probs[0][1];
        }
    }

    @Override
    public void close() throws OrtException {
        if (session != null) session.close();
        if (env != null) env.close();
    }
}