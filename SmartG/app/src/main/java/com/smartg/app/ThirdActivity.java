package com.smartg.app;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.Translation;

public class ThirdActivity extends AppCompatActivity {

    /************************************
     **           GLOBALS              **
     ************************************/
    // translator functionality
    private Translator translator;
    private final Map<String, String> LANG_MAP = new HashMap<>();

    //gatt service binding
    private GATTService gattService; // Service class
    public static final String SERVICE_TAG = "BLEService";
    private boolean isServiceBound = false;
    private TextView connectedDeviceTextView;

    /************************************
     **      helper functions    **
     ************************************/
    //translate text helper method
    private void translateText(String inputText, final TextView outputTextView) {
        translator.translate(inputText)
                .addOnSuccessListener(outputTextView::setText)
                .addOnFailureListener(e ->
                        outputTextView.setText("Translation failed"));
    }


    /************************************
     **      LIFECYCLE of ACTIVITY      **
     ************************************/

    //lifecycle of activity/page
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_third);

        // Initialize button using findViewById
        Button bleActivity = findViewById(R.id.ble_button);

        // Set click listener for the button
        bleActivity.setOnClickListener(v -> {
            // Intent to navigate back to SecondActivity
            Intent intent = new Intent(ThirdActivity.this, SecondActivity.class);
            // Start the activity
            startActivity(intent);
        });

        // Bind to gatt service
        Intent gattserviceIntent = new Intent(this, GATTService.class);
        bindService(gattserviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        if (gattService == null) {
            Toast.makeText(this, "GATT service not binded", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "GATT service binded", Toast.LENGTH_LONG).show();
        }


        // Initialize the TextView reference
        connectedDeviceTextView = findViewById(R.id.connectedDevice_Act3);

        //Translation page elements
        EditText inputEditText = findViewById(R.id.inputText);
        Button   translateButton = findViewById(R.id.translate_button);
        TextView outputTextView  = findViewById(R.id.outputText);
        Spinner languageSpinner = findViewById(R.id.languageSpinner);

        /* ---------- 1. populate language list ---------- */
        List<String> languages = Arrays.asList(
                "English", "Spanish", "Arabic", "Japanese", "French", "Vietnamese"
        );
        LANG_MAP.put("English", TranslateLanguage.ENGLISH);
        LANG_MAP.put("Spanish", TranslateLanguage.SPANISH);
        LANG_MAP.put("Arabic",  TranslateLanguage.ARABIC);
        LANG_MAP.put("Japanese",  TranslateLanguage.JAPANESE);
        LANG_MAP.put("French",   TranslateLanguage.FRENCH);
        LANG_MAP.put("Vietnamese", TranslateLanguage.VIETNAMESE);

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item,
                        languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter); ///causing null pointer exception error!!!

        /* ---------- 2. prepare download conditions ---------- */
        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        /* ---------- 3. translate on button click ---------- */
        translateButton.setOnClickListener(v -> {

            //gets text data from user via the inputEditText box
            String textToTranslate = inputEditText.getText().toString().trim();

            //error message when no text data is available
            if (textToTranslate.isEmpty()) {
                outputTextView.setText("Enter text first");
                return;
            }

            String langName  = languageSpinner.getSelectedItem().toString();
            String langCode  = LANG_MAP.get(langName);

            // clean up any old translator
            if (translator != null) {
                translator.close();
            }

            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(langCode)
                    .build();
            translator = Translation.getClient(options);

            translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener(unused ->
                            translateText(textToTranslate, outputTextView))
                    .addOnFailureListener(e ->
                            outputTextView.setText("Model download failed"));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translator != null) {
            translator.close();
        }
    }



    /************************************
     **  BLUETOOTH SERVICE CONNECTION  **
     ************************************/

    //ServiceConnection
        private final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                gattService = ((GATTService.LocalBinder) service).getService();
                isServiceBound = true;

                // Check if device is already connected
                if (gattService.isConnected()) {
                    // Device is connected, ready for reads/writes
                    String deviceName = gattService.getConnectedDeviceName();
                    // Update UI accordingly
                    // Update the TextView on the UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (deviceName != null && !deviceName.isEmpty()) {
                                connectedDeviceTextView.setText(deviceName);
                            } else {
                                connectedDeviceTextView.setText("Unknown Device");
                            }
                        }
                    });
                }
            }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            gattService = null;
            isServiceBound = false;
            Log.i("Activity", "Service unbound");
        }
    };
}