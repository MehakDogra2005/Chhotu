package com.dukaansaathi.voicelock;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * The small speaker model (WeSpeaker ResNet34, about 25 MB, voicelock/speaker-model.onnx). It turns a few seconds of speech into
 * 256 numbers, the voiceprint: the same person gives numbers that point the same way, a different person does not.
 * It is loaded the first time it is needed, so a shop that never uses Voice lock does not pay for it in memory.
 */
@Component
public class SpeakerModel {
    private OrtEnvironment environment;
    private OrtSession session;
    private float soundScale = 32768f; // the model was trained on 16-bit sample values; its own metadata can say otherwise

    /**
     * The voiceprint of a WAV recording: 256 numbers, scaled so their length is 1. Fails (IllegalArgumentException) if it is not a WAV or
     * holds less than minSeconds of speaking, because a click or a breath says nothing about whose voice it is.
     */
    public synchronized float[] voiceprint(byte[] wav, double minSeconds) throws OrtException, IOException {
        float[] speech = VoiceFeatures.speechOnly(VoiceFeatures.readWav(wav));
        if (speech.length < minSeconds * VoiceFeatures.SAMPLE_RATE) throw new IllegalArgumentException("That recording has too little speech in it");
        return voiceprintOf(speech);
    }

    /** How many seconds of speaking the recording holds (silence cut out). */
    public double secondsOfSpeech(byte[] wav) {
        return VoiceFeatures.speechOnly(VoiceFeatures.readWav(wav)).length / (double) VoiceFeatures.SAMPLE_RATE;
    }

    private float[] voiceprintOf(float[] speech) throws OrtException, IOException {
        load();
        float[][] features = VoiceFeatures.fbank(speech, soundScale);
        String input = session.getInputNames().iterator().next();
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, new float[][][] {features});
             OrtSession.Result result = session.run(Map.of(input, tensor))) {
            float[] numbers = ((float[][]) result.get(0).getValue())[0];
            return unitLength(numbers);
        }
    }

    private void load() throws OrtException, IOException {
        if (session != null) return;
        environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(1);              // a small server: one thread, and no big reserved memory pools
        options.setCPUArenaAllocator(false);
        options.setMemoryPatternOptimization(false);
        try (InputStream model = new ClassPathResource("voicelock/speaker-model.onnx").getInputStream()) {
            session = environment.createSession(model.readAllBytes(), options);
        }
        String normalised = session.getMetadata().getCustomMetadata().get("normalize_samples");
        if ("1".equals(normalised)) soundScale = 1f;
    }

    /** How alike two voiceprints are, from -1 to 1 (1 = the same voice). Both must have length 1. */
    public static double similarity(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    /** The average of several voiceprints, with length 1 again. */
    public static float[] average(java.util.List<float[]> prints) {
        float[] sum = new float[prints.get(0).length];
        for (float[] print : prints) for (int i = 0; i < sum.length; i++) sum[i] += print[i];
        return unitLength(sum);
    }

    private static float[] unitLength(float[] numbers) {
        double length = 0;
        for (float n : numbers) length += n * n;
        length = Math.sqrt(length);
        float[] out = new float[numbers.length];
        for (int i = 0; i < out.length; i++) out[i] = (float) (numbers[i] / length);
        return out;
    }
}
