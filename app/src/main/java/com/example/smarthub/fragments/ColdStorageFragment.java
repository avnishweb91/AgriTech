package com.example.smarthub.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
    private AutoCompleteTextView crop, unit, storage, chamber, temperature;
    private EditText quantity;
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
        unit = view.findViewById(R.id.cold_unit);
        storage = view.findViewById(R.id.cold_storage_name); chamber = view.findViewById(R.id.cold_chamber);
        temperature = view.findViewById(R.id.cold_temperature); list = view.findViewById(R.id.cold_list);
        empty = view.findViewById(R.id.cold_empty);
        dao = AppDatabase.getInstance(requireContext()).supplyChainDao(); api = new APIService(requireContext());
        executor = Executors.newSingleThreadExecutor();
        token = requireContext().getSharedPreferences("auth_prefs", 0).getString("auth_token", null);
        ownerId = requireContext().getSharedPreferences("auth_prefs", 0).getString("user_id", "local-user");
        setupDropdowns();
        ((Button) view.findViewById(R.id.cold_save)).setOnClickListener(v -> saveLot());
        dao.observeLots(ownerId).observe(getViewLifecycleOwner(), this::renderLots);
        syncRemoteLots();
        return view;
    }

    private void saveLot() {
        String cropName = crop.getText().toString().trim();
        String quantityText = quantity.getText().toString().trim();
        String storageName = storage.getText().toString().trim();
        String unitName = unit.getText().toString().trim();
        if (cropName.isEmpty() || quantityText.isEmpty() || storageName.isEmpty() || unitName.isEmpty()) {
            Toast.makeText(requireContext(), "फसल, मात्रा, इकाई और स्टोरेज चुनें या लिखें", Toast.LENGTH_SHORT).show(); return;
        }
        final ColdStorageLotEntity lot = new ColdStorageLotEntity();
        try {
            lot.quantity = Double.parseDouble(quantityText);
            if (lot.quantity <= 0) throw new NumberFormatException();
            lot.temperatureCelsius = TextUtils.isEmpty(temperature.getText()) ? 0 : Double.parseDouble(temperature.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "मात्रा और तापमान सही संख्या में लिखें", Toast.LENGTH_SHORT).show(); return;
        }
        lot.ownerId = ownerId; lot.cropName = cropName; lot.cropNameHindi = lot.cropName;
        lot.unit = unitName;
        lot.storageName = storageName; lot.chamberCode = chamber.getText().toString().trim();
        lot.status = "stored"; lot.storedAt = System.currentTimeMillis(); lot.expectedDispatchAt = 0;
        lot.syncState = "pending"; lot.updatedAt = System.currentTimeMillis();
        rememberChoice("storage_names", storageName);
        rememberChoice("chamber_codes", lot.chamberCode);
        setChoices(storage, choicesWithPrompt("storage_names", "पहली बार? स्टोरेज का नाम लिखें"));
        setChoices(chamber, choicesWithPrompt("chamber_codes", "पहली बार? चैंबर कोड लिखें (वैकल्पिक)"));
        executor.execute(() -> { long localId = dao.insertLot(lot); lot.id = localId; syncLot(lot); requireActivity().runOnUiThread(() -> {
            crop.setText(""); quantity.setText(""); storage.setText(""); chamber.setText(""); temperature.setText("");
            unit.setText("क्विंटल", false);
            Toast.makeText(requireContext(), "लॉट ऑफ़लाइन सहेजा गया; ऑनलाइन होने पर सिंक होगा", Toast.LENGTH_SHORT).show();
        }); });
    }

    private void setupDropdowns() {
        setChoices(crop, java.util.Arrays.asList("धान", "गेहूँ", "मक्का", "आलू", "प्याज", "टमाटर", "सोयाबीन", "चना", "सरसों", "अन्य फसल (लिखें)"));
        crop.setOnItemClickListener((parent, view, position, id) -> {
            if ("अन्य फसल (लिखें)".equals(String.valueOf(parent.getItemAtPosition(position)))) crop.setText("");
        });
        setChoices(unit, java.util.Arrays.asList("क्विंटल", "किलोग्राम", "टन", "बोरी"));
        unit.setText("क्विंटल", false);
        setChoices(storage, choicesWithPrompt("storage_names", "पहली बार? स्टोरेज का नाम लिखें"));
        storage.setOnItemClickListener((parent, view, position, id) -> {
            if ("पहली बार? स्टोरेज का नाम लिखें".equals(String.valueOf(parent.getItemAtPosition(position)))) storage.setText("");
        });
        setChoices(chamber, choicesWithPrompt("chamber_codes", "पहली बार? चैंबर कोड लिखें (वैकल्पिक)"));
        chamber.setOnItemClickListener((parent, view, position, id) -> {
            if ("पहली बार? चैंबर कोड लिखें (वैकल्पिक)".equals(String.valueOf(parent.getItemAtPosition(position)))) chamber.setText("");
        });
        setChoices(temperature, java.util.Arrays.asList("-5", "0", "2", "4", "8", "10"));
        for (AutoCompleteTextView field : new AutoCompleteTextView[]{crop, unit, storage, chamber, temperature}) {
            field.setOnClickListener(v -> field.showDropDown());
        }
    }

    private void setChoices(AutoCompleteTextView field, List<String> values) {
        field.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, values));
        field.setThreshold(0);
    }

    private List<String> choicesWithPrompt(String key, String prompt) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(requireContext()
                .getSharedPreferences("cold_storage_choices", 0).getStringSet(key, java.util.Collections.emptySet()));
        values.add(prompt);
        return new java.util.ArrayList<>(values);
    }

    private void rememberChoice(String key, String value) {
        if (TextUtils.isEmpty(value)) return;
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("cold_storage_choices", 0);
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(prefs.getStringSet(key, java.util.Collections.emptySet()));
        values.remove(value); values.add(value);
        while (values.size() > 8) values.remove(values.iterator().next());
        prefs.edit().putStringSet(key, values).apply();
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
