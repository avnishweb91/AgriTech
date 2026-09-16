package com.example.smarthub.services;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Disease identification is intentionally disabled until a validated crop-specific model
 * is configured. Color heuristics are not reliable enough to diagnose a plant or recommend
 * pesticide use.
 */
public class DiseaseDetectionService {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public interface DiseaseDetectionCallback {
        void onDetectionComplete(DiseaseResult result);
        void onDetectionError(String error);
    }

    /** Kept as a data contract for a future, validated inference provider. */
    public static class DiseaseResult {
        public final String diseaseName, diseaseNameHindi, symptoms, treatment, prevention, severity;
        public final float confidence;
        public final String cropType, cropTypeHindi, recommendedPesticides, organicAlternatives;
        public final String immediateActions, expertAdvice;

        public DiseaseResult(String diseaseName, String diseaseNameHindi, float confidence,
                String symptoms, String treatment, String prevention, String severity,
                String cropType, String cropTypeHindi, String recommendedPesticides,
                String organicAlternatives, String immediateActions, String expertAdvice) {
            this.diseaseName = diseaseName;
            this.diseaseNameHindi = diseaseNameHindi;
            this.confidence = confidence;
            this.symptoms = symptoms;
            this.treatment = treatment;
            this.prevention = prevention;
            this.severity = severity;
            this.cropType = cropType;
            this.cropTypeHindi = cropTypeHindi;
            this.recommendedPesticides = recommendedPesticides;
            this.organicAlternatives = organicAlternatives;
            this.immediateActions = immediateActions;
            this.expertAdvice = expertAdvice;
        }
    }

    public DiseaseDetectionService(android.content.Context context) { }

    public void analyzeImage(@NonNull Bitmap image, @NonNull DiseaseDetectionCallback callback) {
        executorService.execute(() -> callback.onDetectionError(
                "फोटो सुरक्षित है, लेकिन विश्वसनीय रोग-पहचान मॉडल अभी उपलब्ध नहीं है। " +
                "गलत दवा से बचने के लिए स्थानीय कृषि विशेषज्ञ से पुष्टि करें।"));
    }

    public void shutdown() { executorService.shutdown(); }
}
