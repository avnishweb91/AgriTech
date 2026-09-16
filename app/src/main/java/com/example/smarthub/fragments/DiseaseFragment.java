package com.example.smarthub.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smarthub.R;
import com.example.smarthub.services.DiseaseDetectionService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.InputStream;

public class DiseaseFragment extends Fragment {
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_IMAGE_CAPTURE = 200;
    private static final int REQUEST_IMAGE_PICK = 201;

    private ImageView photoPreview;
    private MaterialButton cameraButton, galleryButton, analyzeButton;
    private ProgressBar progressBar;
    private LinearLayout analysisResultLayout;
    private TextView diseaseNameText, confidenceText, symptomsText, treatmentText, preventionText, severityText;
    private TextView cropTypeText, pesticidesText, organicText, immediateActionsText, expertAdviceText;
    
    private Bitmap selectedImage;
    private DiseaseDetectionService diseaseDetectionService;
    private boolean isAnalyzing = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_disease, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupClickListeners();
        
        // Initialize disease detection service
        diseaseDetectionService = new DiseaseDetectionService(requireContext());
    }

    private void initializeViews(View view) {
        photoPreview = view.findViewById(R.id.photo_preview);
        cameraButton = view.findViewById(R.id.btn_camera);
        galleryButton = view.findViewById(R.id.btn_gallery);
        analyzeButton = view.findViewById(R.id.btn_analyze);
        progressBar = view.findViewById(R.id.progress_bar);
        analysisResultLayout = view.findViewById(R.id.analysis_result_layout);
        
        diseaseNameText = view.findViewById(R.id.disease_name);
        confidenceText = view.findViewById(R.id.confidence_level);
        symptomsText = view.findViewById(R.id.symptoms);
        treatmentText = view.findViewById(R.id.treatment);
        preventionText = view.findViewById(R.id.prevention);
        severityText = view.findViewById(R.id.severity);
        cropTypeText = view.findViewById(R.id.crop_type);
        pesticidesText = view.findViewById(R.id.recommended_pesticides);
        organicText = view.findViewById(R.id.organic_alternatives);
        immediateActionsText = view.findViewById(R.id.immediate_actions);
        expertAdviceText = view.findViewById(R.id.expert_advice);
        
        // Initially hide analysis result and progress
        analysisResultLayout.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        cameraButton.setOnClickListener(v -> checkCameraPermissionAndOpen());
        galleryButton.setOnClickListener(v -> checkStoragePermissionAndOpen());
        analyzeButton.setOnClickListener(v -> analyzeImage());
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, 
                    REQUEST_CAMERA_PERMISSION);
        } else {
            openCamera();
        }
    }

    private void checkStoragePermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                    REQUEST_STORAGE_PERMISSION);
        } else {
            openGallery();
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(requireContext(), "कैमरा उपलब्ध नहीं है", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent pickImageIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageIntent.setType("image/*");
        startActivityForResult(pickImageIntent, REQUEST_IMAGE_PICK);
    }

    private void analyzeImage() {
        if (selectedImage == null) {
            Toast.makeText(requireContext(), "कृपया पहले एक फोटो चुनें", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isAnalyzing) {
            Toast.makeText(requireContext(), "विश्लेषण पहले से चल रहा है", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingState();
        
        // Use the real disease detection service
        diseaseDetectionService.analyzeImage(selectedImage, new DiseaseDetectionService.DiseaseDetectionCallback() {
            @Override
            public void onDetectionComplete(DiseaseDetectionService.DiseaseResult result) {
                requireActivity().runOnUiThread(() -> {
                    hideLoadingState();
                    showAnalysisResult(result);
                });
            }

            @Override
            public void onDetectionError(String error) {
                requireActivity().runOnUiThread(() -> {
                    hideLoadingState();
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showLoadingState() {
        isAnalyzing = true;
        progressBar.setVisibility(View.VISIBLE);
        analyzeButton.setEnabled(false);
        analyzeButton.setText("विश्लेषण हो रहा है...");
    }

    private void hideLoadingState() {
        isAnalyzing = false;
        progressBar.setVisibility(View.GONE);
        analyzeButton.setEnabled(true);
        analyzeButton.setText("विश्लेषण करें");
    }

    private void showAnalysisResult(DiseaseDetectionService.DiseaseResult result) {
        // Update UI with analysis results
        diseaseNameText.setText(result.diseaseName + " (" + result.diseaseNameHindi + ")");
        confidenceText.setText("विश्वास स्तर: " + String.format("%.1f%%", result.confidence * 100));
        symptomsText.setText("लक्षण: " + result.symptoms);
        treatmentText.setText("उपचार: " + result.treatment);
        preventionText.setText("रोकथाम: " + result.prevention);
        severityText.setText("गंभीरता: " + result.severity);
        
        // Display enhanced information
        cropTypeText.setText("फसल: " + result.cropType + " (" + result.cropTypeHindi + ")");
        pesticidesText.setText("अनुशंसित कीटनाशक: " + result.recommendedPesticides);
        organicText.setText("जैविक विकल्प: " + result.organicAlternatives);
        immediateActionsText.setText("तुरंत कार्रवाई: " + result.immediateActions);
        expertAdviceText.setText("विशेषज्ञ सलाह: " + result.expertAdvice);
        
        // Show the result layout
        analysisResultLayout.setVisibility(View.VISIBLE);
        
        // Scroll to result
        analysisResultLayout.post(() -> {
            analysisResultLayout.requestFocus();
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE && data != null) {
                // Handle camera result
                Bundle extras = data.getExtras();
                if (extras != null) {
                    selectedImage = (Bitmap) extras.get("data");
                    displaySelectedImage();
                }
            } else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                // Handle gallery result
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    try {
                        InputStream inputStream = requireContext().getContentResolver().openInputStream(selectedImageUri);
                        selectedImage = BitmapFactory.decodeStream(inputStream);
                        displaySelectedImage();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "फोटो लोड करने में त्रुटि", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void displaySelectedImage() {
        if (selectedImage != null) {
            photoPreview.setImageBitmap(selectedImage);
            photoPreview.setVisibility(View.VISIBLE);
            analyzeButton.setEnabled(true);
            
            // Hide previous analysis result
            analysisResultLayout.setVisibility(View.GONE);
            
            Toast.makeText(requireContext(), "फोटो चुना गया, अब विश्लेषण करें", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(requireContext(), "कैमरा अनुमति आवश्यक है", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(requireContext(), "स्टोरेज अनुमति आवश्यक है", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (diseaseDetectionService != null) {
            diseaseDetectionService.shutdown();
        }
    }
}
