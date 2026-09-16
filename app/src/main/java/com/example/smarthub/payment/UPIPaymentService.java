package com.example.smarthub.payment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;

import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

import java.util.ArrayList;
import java.util.List;

public class UPIPaymentService {
    private static final String TAG = "UPIPaymentService";
    private static final int UPI_PAYMENT_REQUEST_CODE = 1001;
    
    private final Context context;
    private final PaymentsClient paymentsClient;
    
    public interface PaymentCallback {
        void onPaymentSuccess(String transactionId, String amount);
        void onPaymentFailure(String error);
        void onPaymentCancelled();
    }
    
    public UPIPaymentService(Context context) {
        this.context = context;
        
        // Initialize Google Pay
        Wallet.WalletOptions walletOptions = new Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST) // Use ENVIRONMENT_PRODUCTION for live
            .build();
        
        this.paymentsClient = Wallet.getPaymentsClient(context, walletOptions);
    }
    
    public void initiateUPIPayment(Activity activity, String amount, String merchantId, 
                                 String merchantName, String transactionId, PaymentCallback callback) {
        try {
            Log.d(TAG, "Initiating UPI payment: " + amount + " to " + merchantName);
            
            // Create UPI payment intent
            String upiUrl = buildUPIUrl(amount, merchantId, merchantName, transactionId);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(upiUrl));
            
            // Check if any UPI app is available
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                activity.startActivityForResult(intent, UPI_PAYMENT_REQUEST_CODE);
                
                // Store callback for result handling
                PaymentResultHandler.getInstance().setCallback(callback);
            } else {
                Log.e(TAG, "No UPI app found");
                callback.onPaymentFailure("कोई UPI ऐप नहीं मिला। कृपया Google Pay या PhonePe इंस्टॉल करें।");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initiating UPI payment: " + e.getMessage(), e);
            callback.onPaymentFailure("UPI भुगतान शुरू करने में त्रुटि: " + e.getMessage());
        }
    }
    
    public void initiateGooglePayPayment(Activity activity, String amount, String merchantId,
                                       String merchantName, String transactionId, PaymentCallback callback) {
        try {
            Log.d(TAG, "Initiating Google Pay payment: " + amount + " to " + merchantName);
            
            // Check if Google Pay is available
            if (isGooglePayAvailable()) {
                // Use Google Pay API
                initiateGooglePayTransaction(activity, amount, merchantId, merchantName, transactionId, callback);
            } else {
                // Fallback to regular UPI
                initiateUPIPayment(activity, amount, merchantId, merchantName, transactionId, callback);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initiating Google Pay payment: " + e.getMessage(), e);
            callback.onPaymentFailure("Google Pay भुगतान शुरू करने में त्रुटि: " + e.getMessage());
        }
    }
    
    private String buildUPIUrl(String amount, String merchantId, String merchantName, String transactionId) {
        StringBuilder upiUrl = new StringBuilder("upi://pay?");
        upiUrl.append("pa=").append(merchantId); // Payee address (UPI ID)
        upiUrl.append("&pn=").append(merchantName); // Payee name
        upiUrl.append("&tn=").append("Agri-Tech Smart Hub"); // Transaction note
        upiUrl.append("&am=").append(amount); // Amount
        upiUrl.append("&cu=").append("INR"); // Currency
        upiUrl.append("&tr=").append(transactionId); // Transaction reference ID
        
        return upiUrl.toString();
    }
    
    private boolean isGooglePayAvailable() {
        try {
            return paymentsClient != null;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Google Pay availability: " + e.getMessage());
            return false;
        }
    }
    
    private void initiateGooglePayTransaction(Activity activity, String amount, String merchantId,
                                            String merchantName, String transactionId, PaymentCallback callback) {
        // This would integrate with Google Pay API
        // For now, fallback to regular UPI
        Log.d(TAG, "Google Pay API integration not implemented, falling back to UPI");
        initiateUPIPayment(activity, amount, merchantId, merchantName, transactionId, callback);
    }
    
    public void handlePaymentResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UPI_PAYMENT_REQUEST_CODE) {
            PaymentCallback callback = PaymentResultHandler.getInstance().getCallback();
            if (callback == null) {
                Log.e(TAG, "Payment callback not found");
                return;
            }
            
            if (resultCode == Activity.RESULT_OK) {
                // Payment successful
                String transactionId = generateTransactionId();
                String amount = extractAmountFromResult(data);
                
                Log.d(TAG, "Payment successful: " + transactionId + " for amount: " + amount);
                callback.onPaymentSuccess(transactionId, amount);
                
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // Payment cancelled
                Log.d(TAG, "Payment cancelled by user");
                callback.onPaymentCancelled();
                
            } else {
                // Payment failed
                String error = extractErrorFromResult(data);
                Log.e(TAG, "Payment failed: " + error);
                callback.onPaymentFailure(error);
            }
        }
    }
    
    private String generateTransactionId() {
        return "TXN_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    private String extractAmountFromResult(Intent data) {
        // Extract amount from payment result
        // This would depend on the UPI app's response format
        return "0.00"; // Placeholder
    }
    
    private String extractErrorFromResult(Intent data) {
        // Extract error from payment result
        // This would depend on the UPI app's response format
        return "भुगतान में त्रुटि हुई। कृपया पुनः प्रयास करें।";
    }
    
    public void createPaymentRequest(String amount, String merchantId, String merchantName) {
        try {
            // Create a payment request for the marketplace
            PaymentRequest request = new PaymentRequest(
                generateTransactionId(),
                amount,
                merchantId,
                merchantName,
                System.currentTimeMillis(),
                "pending"
            );
            
            // Save to local database or send to server
            savePaymentRequest(request);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating payment request: " + e.getMessage(), e);
        }
    }
    
    private void savePaymentRequest(PaymentRequest request) {
        // TODO: Save payment request to local database or send to server
        Log.d(TAG, "Payment request created: " + request.transactionId);
    }
    
    public static class PaymentRequest {
        public String transactionId;
        public String amount;
        public String merchantId;
        public String merchantName;
        public long timestamp;
        public String status;
        
        public PaymentRequest(String transactionId, String amount, String merchantId, 
                           String merchantName, long timestamp, String status) {
            this.transactionId = transactionId;
            this.amount = amount;
            this.merchantId = merchantId;
            this.merchantName = merchantName;
            this.timestamp = timestamp;
            this.status = status;
        }
    }
    
    // Singleton class to handle payment callbacks
    public static class PaymentResultHandler {
        private static PaymentResultHandler instance;
        private PaymentCallback callback;
        
        private PaymentResultHandler() {}
        
        public static PaymentResultHandler getInstance() {
            if (instance == null) {
                instance = new PaymentResultHandler();
            }
            return instance;
        }
        
        public void setCallback(PaymentCallback callback) {
            this.callback = callback;
        }
        
        public PaymentCallback getCallback() {
            return callback;
        }
        
        public void clearCallback() {
            this.callback = null;
        }
    }
}
