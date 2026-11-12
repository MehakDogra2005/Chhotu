package com.dukaansaathi.voicelock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Turns a recorded WAV file into the numbers the speaker model reads: 16 kHz sound, silence cut out, then "fbank" features
 * (80 loudness values per 10 ms, the same recipe the model was trained with).
 */
final class VoiceFeatures {
    static final int SAMPLE_RATE = 16000;
    private static final int FRAME_LENGTH = 400;     // 25 ms of sound
    private static final int FRAME_SHIFT = 160;      // a new frame every 10 ms
    private static final int FFT_SIZE = 512;
    private static final int MEL_BANDS = 80;
    private static final double LOWEST_HZ = 20;
    private static final double PRE_EMPHASIS = 0.97;
    private static final int QUIET_FRAME = 320;      // 20 ms pieces used to find the quiet parts
    private static final double QUIET_BELOW = 0.01;  // a piece quieter than this share of the loudest piece is silence or background noise
    private static final int MAX_SECONDS = 6;        // more speech than this adds nothing, only time and memory

    private VoiceFeatures() {}

    /** The sound of a WAV file (16-bit) as numbers between -1 and 1 at 16 kHz, mono. */
    static float[] readWav(byte[] wav) {
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 44 || buffer.getInt(0) != 0x46464952) throw new IllegalArgumentException("This is not a WAV recording"); // "RIFF"
        int channels = 1;
        int rate = SAMPLE_RATE;
        int at = 12;
        while (at + 8 <= wav.length) {  // the file is a list of chunks: "fmt " says how the sound is stored, "data" holds it
            int id = buffer.getInt(at);
            int size = buffer.getInt(at + 4);
            if (id == 0x20746d66) {  // "fmt "
                channels = buffer.getShort(at + 10);
                rate = buffer.getInt(at + 12);
                if (buffer.getShort(at + 22) != 16) throw new IllegalArgumentException("Only 16-bit recordings can be used");
            } else if (id == 0x61746164) {  // "data"
                int length = Math.min(size < 0 ? Integer.MAX_VALUE : size, wav.length - at - 8);
                return resample(toMono(buffer, at + 8, length / 2, channels), rate);
            }
            at += 8 + size + (size & 1);
        }
        throw new IllegalArgumentException("This WAV recording has no sound in it");
    }

    private static float[] toMono(ByteBuffer buffer, int start, int values, int channels) {
        float[] mono = new float[values / channels];
        for (int i = 0; i < mono.length; i++) {
            float sum = 0;
            for (int c = 0; c < channels; c++) sum += buffer.getShort(start + 2 * (i * channels + c)) / 32768f;
            mono[i] = sum / channels;
        }
        return mono;
    }

    /** Changes the sound to 16 kHz by reading between neighbouring samples. */
    private static float[] resample(float[] sound, int rate) {
        if (rate == SAMPLE_RATE) return sound;
        float[] out = new float[(int) ((long) sound.length * SAMPLE_RATE / rate)];
        for (int i = 0; i < out.length; i++) {
            double position = (double) i * rate / SAMPLE_RATE;
            int before = (int) position;
            int after = Math.min(before + 1, sound.length - 1);
            out[i] = (float) (sound[before] + (sound[after] - sound[before]) * (position - before));
        }
        return out;
    }

    /** Keeps only the speaking parts (silence and steady background noise are cut out), at most MAX_SECONDS of it. */
    static float[] speechOnly(float[] sound) {
        int pieces = sound.length / QUIET_FRAME;
        double[] loudness = new double[pieces];
        double loudest = 0;
        for (int p = 0; p < pieces; p++) {
            double sum = 0;
            for (int i = 0; i < QUIET_FRAME; i++) sum += sound[p * QUIET_FRAME + i] * sound[p * QUIET_FRAME + i];
            loudness[p] = sum / QUIET_FRAME;
            loudest = Math.max(loudest, loudness[p]);
        }
        float[] kept = new float[Math.min(sound.length, MAX_SECONDS * SAMPLE_RATE)];
        int count = 0;
        for (int p = 0; p < pieces && count + QUIET_FRAME <= kept.length; p++) {
            if (loudness[p] < loudest * QUIET_BELOW) continue;
            System.arraycopy(sound, p * QUIET_FRAME, kept, count, QUIET_FRAME);
            count += QUIET_FRAME;
        }
        return java.util.Arrays.copyOf(kept, count);
    }

    /** 80 loudness values for every 10 ms of the sound, with the average of each value taken off (so the microphone's own colour does not matter). */
    static float[][] fbank(float[] sound, float scale) {
        int frames = 1 + (sound.length - FRAME_LENGTH) / FRAME_SHIFT;
        if (frames < 1) throw new IllegalArgumentException("That recording is too short to use");
        double[][] bands = melBands();
        double[] window = new double[FRAME_LENGTH];
        for (int i = 0; i < FRAME_LENGTH; i++) window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (FRAME_LENGTH - 1));

        float[][] features = new float[frames][MEL_BANDS];
        double[] real = new double[FFT_SIZE];
        double[] imaginary = new double[FFT_SIZE];
        double[] frame = new double[FRAME_LENGTH];
        for (int f = 0; f < frames; f++) {
            double mean = 0;
            for (int i = 0; i < FRAME_LENGTH; i++) {
                frame[i] = sound[f * FRAME_SHIFT + i] * scale;
                mean += frame[i];
            }
            mean /= FRAME_LENGTH;
            for (int i = 0; i < FRAME_LENGTH; i++) frame[i] -= mean;
            for (int i = FRAME_LENGTH - 1; i > 0; i--) frame[i] -= PRE_EMPHASIS * frame[i - 1];
            frame[0] -= PRE_EMPHASIS * frame[0];
            for (int i = 0; i < FFT_SIZE; i++) {
                real[i] = i < FRAME_LENGTH ? frame[i] * window[i] : 0;
                imaginary[i] = 0;
            }
            fft(real, imaginary);
            for (int b = 0; b < MEL_BANDS; b++) {
                double energy = 0;
                for (int i = 0; i < FFT_SIZE / 2; i++) energy += bands[b][i] * (real[i] * real[i] + imaginary[i] * imaginary[i]);
                features[f][b] = (float) Math.log(Math.max(energy, 1.1920929e-7));
            }
        }
        for (int b = 0; b < MEL_BANDS; b++) {
            double mean = 0;
            for (int f = 0; f < frames; f++) mean += features[f][b];
            mean /= frames;
            for (int f = 0; f < frames; f++) features[f][b] -= (float) mean;
        }
        return features;
    }

    private static double mel(double hertz) {
        return 1127 * Math.log(1 + hertz / 700);
    }

    /** 80 triangle-shaped filters spread over the hearing range: how much of each frequency counts towards each of the 80 values. */
    private static double[][] melBands() {
        double low = mel(LOWEST_HZ);
        double high = mel(SAMPLE_RATE / 2.0);
        double step = (high - low) / (MEL_BANDS + 1);
        double[][] bands = new double[MEL_BANDS][FFT_SIZE / 2];
        for (int b = 0; b < MEL_BANDS; b++) {
            double left = low + b * step;
            double middle = left + step;
            double right = middle + step;
            for (int i = 0; i < FFT_SIZE / 2; i++) {
                double m = mel((double) i * SAMPLE_RATE / FFT_SIZE);
                if (m > left && m < right) bands[b][i] = m <= middle ? (m - left) / (middle - left) : (right - m) / (right - middle);
            }
        }
        return bands;
    }

    /** The Fourier transform (which frequencies are in the sound), in place. The size must be a power of two. */
    private static void fft(double[] real, double[] imaginary) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {  // put the values in bit-reversed order
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double r = real[i]; real[i] = real[j]; real[j] = r;
                double m = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = m;
            }
        }
        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2 * Math.PI / length;
            for (int start = 0; start < n; start += length) {
                for (int k = 0; k < length / 2; k++) {
                    double cos = Math.cos(angle * k);
                    double sin = Math.sin(angle * k);
                    int a = start + k;
                    int b = start + k + length / 2;
                    double tr = real[b] * cos - imaginary[b] * sin;
                    double ti = real[b] * sin + imaginary[b] * cos;
                    real[b] = real[a] - tr;
                    imaginary[b] = imaginary[a] - ti;
                    real[a] += tr;
                    imaginary[a] += ti;
                }
            }
        }
    }
}
