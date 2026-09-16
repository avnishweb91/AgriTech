package com.example.smarthub.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smarthub.R;
import com.example.smarthub.api.APIService;
import com.example.smarthub.database.AppDatabase;
import com.example.smarthub.database.dao.SupplyChainDao;
import com.example.smarthub.database.entities.ColdStorageLotEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class ColdStorageFragment extends Fragment {
    private EditText crop, quantity, storage, chamber, temperature;
    private LinearLayout list;
    private TextView empty;
    private SupplyChainDao dao;
    private APIService api;
    private ExecutorService executor;
    private String token;
    private String ownerId;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_cold_storage, container, false);
        crop = view.findViewById(R.id.cold_crop); quantity = view.findViewById(R.id.cold_quantity);
        storage = view.findViewById(R.id.cold_storage_name); chamber = view.findViewById(R.id.cold_chamber);
        temperature = view.findViewById(R.id.cold_temperature); list = view.findViewById(R.id.cold_list);
        empty = view.findViewById(R.id.cold_empty);
        dao = AppDatabase.getInstance(requireContext()).supplyChainDao(); api = new APIService(requireContext());
        executor = Executors.newSingleThreadExecutor();
        token = requireContext().getSharedPreferences("auth_prefs", 0).getString("auth_token", null);
        ownerId = requireContext().getSharedPreferences("auth_prefs", 0).getString("user_id", "local-user");
        ((Button) view.findViewById(R.id.cold_save)).setOnClickListener(v -> saveLot());
        dao.observeLots(ownerId).observe(getViewLifecycleOwner(), this::renderLots);
        syncRemoteLots();
        return view;
    }

    private void saveLot() {
        if (TextUtils.isEmpty(crop.getText()) || TextUtils.isEmpty(quantity.getText()) || TextUtils.isEmpty(storage.getText())) {
            Toast.makeText(requireContext(), "फसल, मात्रा और स्टोरेज भरें", Toast.LENGTH_SHORT).show(); return;
        }
        final ColdStorageLotEntity lot = new ColdStorageLotEntity();
        lot.ownerId = ownerId; lot.cropName = crop.getText().toString().trim(); lot.cropNameHindi = lot.cropName;
        lot.quantity = Double.parseDouble(quantity.getText().toString()); lot.unit = "quintal";
        lot.storageName = storage.getText().toString().trim(); lot.chamberCode = chamber.getText().toString().trim();
        lot.temperatureCelsius = TextUtils.isEmpty(temperature.getText()) ? 0 : Double.parseDouble(temperature.getText().toString());
        lot.status = "stored"; lot.storedAt = System.currentTimeMillis(); lot.expectedDispatchAt = 0;
        lot.syncState = "pending"; lot.updatedAt = System.currentTimeMillis();
        executor.execute(() -> { long localId = dao.insertLot(lot); lot.id = localId; syncLot(lot); requireActivity().runOnUiThread(() -> {
            crop.setText(""); quantity.setText(""); storage.setText(""); chamber.setText(""); temperature.setText("");
            Toast.makeText(requireContext(), "लॉट ऑफ़लाइन सहेजा गया; ऑनलाइन होने पर सिंक होगा", Toast.LENGTH_SHORT).show();
        }); });
    }

    private void syncRemoteLots() {
        if (token == null) return;
        executor.execute(() -> { try {
            Response<List<APIService.BackendColdStorageLot>> response = api.backend().getColdStorageLots("Bearer " + token).execute();
            if (response.isSuccessful() && response.body() != null) for (APIService.BackendColdStorageLot r : response.body()) {
                ColdStorageLotEntity local = new ColdStorageLotEntity(); local.remoteId = r.id; local.ownerId = ownerId;
                local.cropName = r.cropName; local.cropNameHindi = r.cropName; local.quantity = r.quantity; local.unit = r.unit;
                local.storageName = r.storageName; local.chamberCode = r.chamberCode; local.temperatureCelsius = r.temperatureC;
                local.status = r.status; local.storedAt = System.currentTimeMillis(); local.expectedDispatchAt = 0;
                local.syncState = "synced"; local.updatedAt = System.currentTimeMillis(); dao.insertLot(local);
            }
        } catch (Exception ignored) { }
        for (ColdStorageLotEntity pending : dao.getPendingLots()) syncLot(pending); });
    }

    private void syncLot(ColdStorageLotEntity lot) {
        if (token == null) return;
        try { Response<APIService.BackendColdStorageLot> response = api.backend().createColdStorageLot("Bearer " + token,
                new APIService.ColdStorageLotRequest(lot.cropName, lot.quantity, lot.unit, lot.storageName, lot.chamberCode, lot.temperatureCelsius, lot.status)).execute();
            if (response.isSuccessful() && response.body() != null) dao.updateLotRemoteId(lot.id, response.body().id, "synced");
            else dao.updateLotSyncState(lot.id, "failed");
        } catch (Exception e) { dao.updateLotSyncState(lot.id, "pending"); }
    }

    private void renderLots(List<ColdStorageLotEntity> lots) {
        if (!isAdded()) return; list.removeAllViews(); empty.setVisibility(lots == null || lots.isEmpty() ? View.VISIBLE : View.GONE);
        if (lots == null) return;
        for (ColdStorageLotEntity lot : lots) { LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0, 16, 0, 16);
            TextView text = new TextView(requireContext()); text.setText(lot.cropName + " • " + lot.quantity + " " + lot.unit + "\n" + lot.storageName + " / " + lot.chamberCode + "\nस्थिति: " + lot.status + " (" + lot.syncState + ")"); text.setTextSize(16);
            row.addView(text); if (lot.remoteId != null) { Button event = new Button(requireContext()); event.setText("डिस्पैच दर्ज करें"); event.setOnClickListener(v -> addDispatchEvent(lot.remoteId)); row.addView(event); } list.addView(row); }
    }

    private void addDispatchEvent(String remoteId) { if (token == null) return; executor.execute(() -> { try { Response<APIService.BasicResponse> r = api.backend().addColdStorageEvent("Bearer " + token, remoteId, new APIService.SupplyChainEventRequest("dispatched", "App से डिस्पैच दर्ज" )).execute(); requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), r.isSuccessful() ? "डिस्पैच दर्ज हो गया" : "इवेंट दर्ज नहीं हुआ", Toast.LENGTH_SHORT).show()); } catch (Exception e) { } }); }

    @Override public void onDestroyView() { super.onDestroyView(); if (executor != null) executor.shutdownNow(); }
}
