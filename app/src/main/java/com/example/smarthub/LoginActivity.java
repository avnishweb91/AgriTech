package com.example.smarthub;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;
import android.widget.RadioButton;

import androidx.appcompat.app.AppCompatActivity;
import com.example.smarthub.auth.AuthenticationService;
import com.google.android.material.card.MaterialCardView;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText etMobile, etOTP;
    private Button btnSendOTP, btnVerifyOTP;
    private MaterialCardView otpLayout;
    private TextView tvMobileDisplay;
    private RadioGroup roleGroup;
    
    private AuthenticationService authService;
    private String phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_login);
        
        initViews();
        setupAuthService();
        setupClickListeners();
    }
    
    private void initViews() {
        etMobile = findViewById(R.id.et_mobile);
        etOTP = findViewById(R.id.et_otp);
        btnSendOTP = findViewById(R.id.btn_send_otp);
        btnVerifyOTP = findViewById(R.id.btn_verify_otp);
        otpLayout = findViewById(R.id.otp_layout);
        tvMobileDisplay = findViewById(R.id.tv_mobile_display);
        roleGroup = findViewById(R.id.role_group);
    }
    
    private void setupAuthService() {
        authService = new AuthenticationService(this);
        authService.setAuthenticationCallback(new AuthenticationService.AuthenticationCallback() {
            @Override
            public void onVerificationCodeSent(String phoneNumber) {
                // OTP sent successfully
                showOTPLayout();
                Toast.makeText(LoginActivity.this, "OTP भेज दिया गया है", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onVerificationCompleted() {
                onLoginSuccess();
            }
            
            @Override
            public void onVerificationFailed(Exception e) {
                Toast.makeText(LoginActivity.this, "OTP भेजने में त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            
            @Override
            public void onAuthSuccess(String userId, String phone) {
                onLoginSuccess();
            }
            
            @Override
            public void onAuthFailure(String error) {
                Toast.makeText(LoginActivity.this, "सत्यापन विफल: " + error, Toast.LENGTH_LONG).show();
            }
            
            @Override
            public void onSignOut() {
                // Not needed in login activity
            }
        });
    }
    
    private void setupClickListeners() {
        btnSendOTP.setOnClickListener(v -> {
            if (validateMobileInput()) {
                sendOTP();
            }
        });
        
        btnVerifyOTP.setOnClickListener(v -> {
            if (validateOTPInput()) {
                verifyOTP();
            }
        });
    }
    
    private boolean validateMobileInput() {
        phoneNumber = etMobile.getText().toString().trim();
        
        if (TextUtils.isEmpty(phoneNumber)) {
            etMobile.setError("मोबाइल नंबर दर्ज करें");
            return false;
        }
        
        if (phoneNumber.length() != 10) {
            etMobile.setError("10 अंकों का मोबाइल नंबर दर्ज करें");
            return false;
        }
        
        if (!phoneNumber.matches("^[6-9]\\d{9}$")) {
            etMobile.setError("सही मोबाइल नंबर दर्ज करें");
            return false;
        }
        
        return true;
    }
    
    private boolean validateOTPInput() {
        String otp = etOTP.getText().toString().trim();
        
        if (TextUtils.isEmpty(otp)) {
            etOTP.setError("OTP दर्ज करें");
            return false;
        }
        
        if (otp.length() != 6) {
            etOTP.setError("6 अंकों का OTP दर्ज करें");
            return false;
        }
        
        return true;
    }
    
    private void sendOTP() {
        // Show loading state
        btnSendOTP.setEnabled(false);
        btnSendOTP.setText("भेज रहा है...");
        
        // Add +91 prefix for Indian numbers
        String fullPhoneNumber = "+91" + phoneNumber;
        
        // Send OTP using AuthenticationService
        authService.startPhoneNumberVerification(fullPhoneNumber, this);
        
        btnSendOTP.setText("OTP भेजा गया");
    }
    
    private void verifyOTP() {
        String otp = etOTP.getText().toString().trim();
        
        // Show loading state
        btnVerifyOTP.setEnabled(false);
        btnVerifyOTP.setText("सत्यापित कर रहा है...");
        int selectedRoleId = roleGroup.getCheckedRadioButtonId();
        RadioButton selectedRole = findViewById(selectedRoleId);
        String role = selectedRole == null ? "farmer" : String.valueOf(selectedRole.getTag());
        authService.setSelectedRole(role);
        
        // Verify OTP using AuthenticationService
        authService.verifyPhoneNumberWithCode(otp);
        
    }
    
    private void showOTPLayout() {
        otpLayout.setVisibility(View.VISIBLE);
        tvMobileDisplay.setText("OTP भेजा गया: +91 " + phoneNumber);
        btnSendOTP.setVisibility(View.GONE);
    }
    
    private void onLoginSuccess() {
        Toast.makeText(this, "सफलतापूर्वक लॉगिन हो गया!", Toast.LENGTH_SHORT).show();
        
        // Save login state
        saveLoginState();
        
        // Navigate to main app
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
    
    private void saveLoginState() {
        // AuthenticationService persists the authoritative local session.
    }
}
