package com.example.smarthub.auth;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.smarthub.api.APIService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.UUID;

/** Local-first authentication. Replace the OTP methods with a REST API adapter for production SMS. */
public class AuthenticationService {
    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_PHONE = "phone_number";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_TOKEN = "auth_token";
    private final Context context;
    private final SharedPreferences preferences;
    private String pendingPhone;
    private String pendingOtp;
    private String selectedRole = "farmer";
    private final APIService apiService;
    private AuthenticationCallback authCallback;

    public interface AuthenticationCallback {
        void onVerificationCodeSent(String phoneNumber);
        void onVerificationCompleted();
        void onVerificationFailed(Exception e);
        void onAuthSuccess(String userId, String phoneNumber);
        void onAuthFailure(String error);
        void onSignOut();
    }

    public interface BiometricCallback {
        void onBiometricSuccess();
        void onBiometricError(String error);
        void onBiometricNotAvailable();
    }

    public AuthenticationService(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        apiService = new APIService(this.context);
    }

    public void setAuthenticationCallback(AuthenticationCallback callback) { authCallback = callback; }
    public void setSelectedRole(String role) {
        if ("farmer".equals(role) || "buyer".equals(role) || "processor".equals(role)) selectedRole = role;
    }

    /** Requests a real SMS through the Railway API and Twilio Verify. */
    public void startPhoneNumberVerification(String phoneNumber, Activity activity) {
        if (TextUtils.isEmpty(phoneNumber)) {
            if (authCallback != null) authCallback.onVerificationFailed(new IllegalArgumentException("Phone number is required"));
            return;
        }
        pendingPhone = phoneNumber;
        apiService.backend().requestOtp(new APIService.OtpRequest(phoneNumber)).enqueue(new Callback<APIService.BasicResponse>() {
            @Override public void onResponse(@NonNull Call<APIService.BasicResponse> call, @NonNull Response<APIService.BasicResponse> response) {
                if (response.isSuccessful()) {
                    if (authCallback != null) authCallback.onVerificationCodeSent(phoneNumber);
                } else if (authCallback != null) {
                    authCallback.onVerificationFailed(new Exception("OTP service rejected the request (HTTP " + response.code() + ")"));
                }
            }
            @Override public void onFailure(@NonNull Call<APIService.BasicResponse> call, @NonNull Throwable t) {
                if (authCallback != null) authCallback.onVerificationFailed(new Exception("Network error: " + t.getMessage(), t));
            }
        });
    }

    public void verifyPhoneNumberWithCode(String code) {
        if (pendingPhone == null) {
            if (authCallback != null) authCallback.onAuthFailure("पहले OTP भेजें");
        } else {
            apiService.backend().verifyOtp(new APIService.OtpVerifyRequest(pendingPhone, code, selectedRole)).enqueue(new Callback<APIService.AuthResponse>() {
                @Override public void onResponse(@NonNull Call<APIService.AuthResponse> call, @NonNull Response<APIService.AuthResponse> response) {
                    APIService.AuthResponse body = response.body();
                    if (response.isSuccessful() && body != null && body.token != null && body.user != null) {
                        saveAuthState(true, body.user.id, body.user.phone, body.token);
                        if (authCallback != null) authCallback.onAuthSuccess(body.user.id, body.user.phone);
                    } else if (authCallback != null) {
                        authCallback.onAuthFailure(response.code() == 401 ? "OTP गलत है" : "सत्यापन सेवा उपलब्ध नहीं है");
                    }
                }
                @Override public void onFailure(@NonNull Call<APIService.AuthResponse> call, @NonNull Throwable t) {
                    if (authCallback != null) authCallback.onAuthFailure("Network error: " + t.getMessage());
                }
            });
        }
    }

    public void resendVerificationCode(String phoneNumber, Activity activity) { startPhoneNumberVerification(phoneNumber, activity); }
    public void signOut() { saveAuthState(false, null, null); if (authCallback != null) authCallback.onSignOut(); }
    public boolean isUserLoggedIn() { return preferences.getBoolean(KEY_IS_LOGGED_IN, false); }
    public String getCurrentUserId() { return preferences.getString(KEY_USER_ID, null); }
    public String getCurrentUserPhone() { return preferences.getString(KEY_PHONE, null); }

    private void saveAuthState(boolean loggedIn, String userId, String phone) {
        saveAuthState(loggedIn, userId, phone, null);
    }

    private void saveAuthState(boolean loggedIn, String userId, String phone, String token) {
        preferences.edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).putString(KEY_USER_ID, userId)
                .putString(KEY_PHONE, phone).putString(KEY_TOKEN, token).apply();
    }

    public String getAuthToken() { return preferences.getString(KEY_TOKEN, null); }

    public void checkBiometricAvailability(BiometricCallback callback) {
        int result = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (result == BiometricManager.BIOMETRIC_SUCCESS) callback.onBiometricSuccess();
        else callback.onBiometricNotAvailable();
    }

    public void authenticateWithBiometric(FragmentActivity activity, BiometricCallback callback) {
        BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationError(int code, @NonNull CharSequence message) { callback.onBiometricError(message.toString()); }
                    @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) { callback.onBiometricSuccess(); }
                    @Override public void onAuthenticationFailed() { callback.onBiometricError("Authentication failed"); }
                });
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("बायोमेट्रिक प्रमाणीकरण")
                .setSubtitle("अपनी पहचान सत्यापित करें").setNegativeButtonText("रद्द करें").build());
    }
}
