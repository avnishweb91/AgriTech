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

import java.util.UUID;

/** Local-first authentication. Replace the OTP methods with a REST API adapter for production SMS. */
public class AuthenticationService {
    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_PHONE = "phone_number";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private final Context context;
    private final SharedPreferences preferences;
    private String pendingPhone;
    private String pendingOtp;
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
    }

    public void setAuthenticationCallback(AuthenticationCallback callback) { authCallback = callback; }

    /** Demo OTP is 123456. A backend adapter should send the generated code over SMS. */
    public void startPhoneNumberVerification(String phoneNumber, Activity activity) {
        if (TextUtils.isEmpty(phoneNumber)) {
            if (authCallback != null) authCallback.onVerificationFailed(new IllegalArgumentException("Phone number is required"));
            return;
        }
        pendingPhone = phoneNumber;
        pendingOtp = "123456";
        if (authCallback != null) authCallback.onVerificationCodeSent(phoneNumber);
    }

    public void verifyPhoneNumberWithCode(String code) {
        if (pendingPhone == null || pendingOtp == null) {
            if (authCallback != null) authCallback.onAuthFailure("पहले OTP भेजें");
        } else if (!pendingOtp.equals(code)) {
            if (authCallback != null) authCallback.onAuthFailure("OTP गलत है");
        } else {
            String userId = preferences.getString(KEY_USER_ID, null);
            if (userId == null) userId = UUID.nameUUIDFromBytes(pendingPhone.getBytes()).toString();
            saveAuthState(true, userId, pendingPhone);
            if (authCallback != null) authCallback.onAuthSuccess(userId, pendingPhone);
        }
    }

    public void resendVerificationCode(String phoneNumber, Activity activity) { startPhoneNumberVerification(phoneNumber, activity); }
    public void signOut() { saveAuthState(false, null, null); if (authCallback != null) authCallback.onSignOut(); }
    public boolean isUserLoggedIn() { return preferences.getBoolean(KEY_IS_LOGGED_IN, false); }
    public String getCurrentUserId() { return preferences.getString(KEY_USER_ID, null); }
    public String getCurrentUserPhone() { return preferences.getString(KEY_PHONE, null); }

    private void saveAuthState(boolean loggedIn, String userId, String phone) {
        preferences.edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).putString(KEY_USER_ID, userId)
                .putString(KEY_PHONE, phone).apply();
    }

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
