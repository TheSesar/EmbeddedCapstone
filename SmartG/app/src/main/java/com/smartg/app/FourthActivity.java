package com.smartg.app;
import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
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
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.languageid.LanguageIdentification;    // NEW usman -- requires new dependency in the gradle files
import com.google.mlkit.nl.translate.TranslatorOptions;
import android.speech.tts.TextToSpeech;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// fourth activity is an activity class
// fourth activity also implements the interface TextUpdateListener
// this activity is the android page that displays the received audio transcript transmitted by the paired BLE device
// this activity also translates the transcript to desired target language and provides audio playback of the translation.
public class FourthActivity extends AppCompatActivity implements TextUpdateListener {

    /************************************
     **           GLOBALS              **
     ************************************/
    // translator functionality
    private Translator translator;
    private LanguageIdentifier languageIdentifier;               // NEW usman
    private final Map<String, String> LANG_MAP = new HashMap<>();
    private final Map<String, Locale> TTS_LANG_MAP = new HashMap<>();

    // Instead of creating a new translator every time, store previously loaded models in a map:
    private static final int MAX_CACHE_SIZE = 5; // New caylan
    private LinkedHashMap<String, Translator> translatorCache = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) { //New Caylan
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Translator> eldest) {
            return size() > MAX_CACHE_SIZE; // Remove oldest entry when cache exceeds limit
        }
    };

    //gatt service binding
    private GATTService gattService; // Service class
    public static final String SERVICE_TAG = "ACT4";
    private boolean isServiceBound = false;
    private boolean isServiceReady = false;
    private boolean shouldStartDisplay = false;
    private TextView connectedDeviceTextView;
    private TextView inputTextView;
    private String textToTranslate = null;

    //Text to Audio functionality
    private TextToSpeech textToSpeech;

    // Set initial values
    private float speechRate = 1.0f;
    private float pitch = 1.0f;

    /*********************************************************
     **    public interface (TextUpdateListener) methods     **
     *********************************************************/

    //passes characteristic value to the input text display
    @Override
    public void onTextReceived(String text) {
        textToTranslate = text;
        runOnUiThread(() -> updateTextView(text));
    }

    //helper method for onTextReceived()
    //updates the input display to the received speech transcript
    public void updateTextView(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                inputTextView.setText(text);
            }
        });
    }


    /**************************************************************
     **                 LIFECYCLE methods of ACTIVITY            **
     **************************************************************/

    //when activity/page is created
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fourth);


        // setup intent for button that navigates fourth activity to second activity
        // Initialize button using findViewById
        Button bleActivity = findViewById(R.id.ble_button2);
        // Set click listener for the button
        bleActivity.setOnClickListener(v -> {
            // Intent to navigate back to SecondActivity
            Intent intent = new Intent(FourthActivity.this, SecondActivity.class);
            // Start the activity
            startActivity(intent);
        });

        // setup intent for button that navigates fourth activity to third activity
        // Initialize button using findViewById
        Button displayActivity = findViewById(R.id.translator_button2);
        // Set click listener for the button
        displayActivity.setOnClickListener(v -> {
            // Intent to navigate back to ThirdActivity
            Intent intent = new Intent(FourthActivity.this, ThirdActivity.class);
            // Start the activity
            startActivity(intent);
        });

        // Bind activity to gatt service
        Intent gattserviceIntent = new Intent(this, GATTService.class);
        bindService(gattserviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Initialize the TextView reference
        connectedDeviceTextView = findViewById(R.id.connectedDevice_Act4);

        //Translation page UI elements
        inputTextView = findViewById(R.id.nestedTextView_In_act4_1);
        Button   translateButton = findViewById(R.id.translate_button2);
        TextView outputTextView  = findViewById(R.id.nested_TextView_Out_act4_2);
        Spinner languageSpinner = findViewById(R.id.languageSpinner2);

        //audio speaker UI elements
        Button audioButton = findViewById(R.id.save_button2);
        SeekBar speechRateSlider = findViewById(R.id.speechRateSlider2);
        SeekBar pitchSlider = findViewById(R.id.pitchSlider2);
        TextView speechRateValue = findViewById(R.id.speechRateValue2);
        TextView pitchValue = findViewById(R.id.pitchValue2);

        // Handle speech rate adjustment
        speechRateSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speechRate = progress / 100.0f; // Scale to match TTS range
                speechRateValue.setText("Speed: " + speechRate);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Handle pitch adjustment
        pitchSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                pitch = progress / 100.0f; // Scale for pitch control
                pitchValue.setText("Pitch: " + pitch);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });


        // update input text display to characteristic value read from BLE device (gatt server)
        if(isServiceReady) {
            // Assuming gattService is your instance of GattService
            gattService.setTextUpdateListener(this); // FourthActivity sets itself as the listener
            textToTranslate = "Receive speech transcript...";
            // fill in the display with the received audio text
            if (gattService.availableAudioText()) {
                textToTranslate = gattService.getAudioText();
                Log.e(SERVICE_TAG, "ACT4: serviceConnection -- there is available audio text: " + textToTranslate);
            }
            Log.e(SERVICE_TAG, "ACT4: onCreate -- input audio text display is: " + textToTranslate);
            //inputTextView.setText(textToTranslate);
            updateTextView(textToTranslate);
        } else {
            shouldStartDisplay = true;
        }


        /* ---------- 0. set up ML Kit language-ID client ---------- */
        languageIdentifier = LanguageIdentification.getClient();            // NEW usman

        /* ---------- 1. populate language list ---------- */
        List<String> languages = Arrays.asList(
                "English", "Spanish", "Arabic", "Japanese", "French", "Vietnamese"
        );

        //languages for the translator save to map for translator
        LANG_MAP.put("English", TranslateLanguage.ENGLISH);
        LANG_MAP.put("Spanish", TranslateLanguage.SPANISH);
        LANG_MAP.put("Arabic",  TranslateLanguage.ARABIC);
        LANG_MAP.put("Japanese",  TranslateLanguage.JAPANESE);
        LANG_MAP.put("French",   TranslateLanguage.FRENCH);
        LANG_MAP.put("Vietnamese", TranslateLanguage.VIETNAMESE);

        //languages for the audio speaker save to map for audio speaker
        TTS_LANG_MAP.put("English", Locale.ENGLISH);
        TTS_LANG_MAP.put("Spanish", new Locale("spa", "MEX"));
        TTS_LANG_MAP.put("Arabic", new Locale("ar"));
        TTS_LANG_MAP.put("Japanese", Locale.JAPANESE);
        TTS_LANG_MAP.put("French", Locale.FRENCH);
        TTS_LANG_MAP.put("Vietnamese", new Locale("vi"));

        /* ---------- initialize and setup the adapter for the language selection spinner ---------- */
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item,
                        languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        /* ---------- 2. prepare download conditions ---------- */
        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        /* ---------- 3. translate on button click ---------- */
        translateButton.setOnClickListener(v -> {

            // Check if service is ready
            if (!isServiceReady) {
                outputTextView.setText("Translation service is not ready yet.");
                return;
            }

            // Check if available speech transcript exist
            if (!gattService.availableAudioText()) {
                outputTextView.setText("No speech transcript to translate");
                return;
            }

            //error message when no text data is available
            if (textToTranslate.isEmpty()) {
                outputTextView.setText("Enter text first");
                return;
            }

            //save selected target language
            String targetLangName = languageSpinner.getSelectedItem().toString();
            String targetLangCode = LANG_MAP.get(targetLangName);


            //3.0) dynamically detect the input's language
            languageIdentifier.identifyLanguage(textToTranslate)        // NEW usman
                    .addOnSuccessListener(langTag -> {                // NEW usman
                        //source language detection failure check
                        if ("und".equals(langTag)) {                        // NEW usman
                            outputTextView.setText("Could not detect language"); // NEW usman
                            return;                                         // NEW usman
                        }                    // NEW usman
                        String sourceLangCode = TranslateLanguage.fromLanguageTag(langTag); // NEW usman
                        // unsupported source language check
                        if (sourceLangCode == null) {                       // NEW usman
                            outputTextView.setText("Unsupported source language"); // NEW usman
                            return;                                         // NEW usman
                        }
                        //3.1) Before downloading a new model, check if a translator for the requested language pair already exists:
                        String cacheKey = sourceLangCode + "_" + targetLangCode;
                        // **Check if a cached translator exists for this language pair, if so, use the saved translator and execute the translation**
                        if (translatorCache.containsKey(cacheKey)) {
                            translator = translatorCache.get(cacheKey);
                            translateText(textToTranslate, outputTextView);
                        } else {

                            /* 3.2) otherwise, if the language pair is new, create new translator and cache the new translator in the map of translators */
                            // A: clean up any old translator (this clean up step replaced by usage of Least-Recently-Used LinkedHashMap Eviction caching strategy)
                            //  if (translator != null) {
                            //      translator.close();
                            //  }
                            // B: define the translator's configurations
                            TranslatorOptions options = new TranslatorOptions.Builder()
                                    //.setSourceLanguage(TranslateLanguage.sourceLangCode)
                                    .setSourceLanguage(sourceLangCode)
                                    .setTargetLanguage(targetLangCode)
                                    .build();
                            // C: set the global translator to the configured translator
                            translator = Translation.getClient(options);
                            // D: Save/Cache the translator to the map
                            translatorCache.put(cacheKey, translator);
                            translator.downloadModelIfNeeded(conditions)
                                    .addOnSuccessListener(unused -> translateText(textToTranslate, outputTextView))
                                    .addOnFailureListener(e -> outputTextView.setText("Model download failed"));
                        }
                        //});
                    }) .addOnFailureListener(e -> outputTextView.setText("Language ID failed"));  // NEW usman
        });

        /* ---------- 4. audio on button click ---------- */
        // Adding OnClickListener
        audioButton.setOnClickListener(v ->  {

            // Get translated text from outputTextView
            String textToSpeak = outputTextView.getText().toString().trim();

            //error message when no text transcript is available
            if (textToSpeak.isEmpty()) {
                outputTextView.setText("Enter text first");
                return;
            }

            // Get selected language from spinner
            String langName = languageSpinner.getSelectedItem().toString();
            Locale ttsLocale = TTS_LANG_MAP.get(langName); // Ensure this map exists

            // verify target language is valid
            if (ttsLocale == null) {
                outputTextView.setText("Selected language is not supported for speech.");
                return;
            }

            // clean up any old audio speaker
            if (textToSpeech != null) {
                textToSpeech.shutdown();
                textToSpeech = null; // Reset to avoid memory leaks
            }

            // Initialize TextToSpeech if needed
            textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
                if (status != TextToSpeech.ERROR && ttsLocale != null) {
                    textToSpeech.setLanguage(ttsLocale);
                    textToSpeech.setSpeechRate(speechRate);
                    textToSpeech.setPitch(pitch);
                    // Speak the text after initialization is complete
                    textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });
        });
    }

    //when activity/page is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translator != null) {
            translator.close();
        }
        if(textToSpeech != null){
            textToSpeech.shutdown();
        }
        if(languageIdentifier != null) {
            languageIdentifier.close();
        }

        // Clear language maps
        LANG_MAP.clear();
        TTS_LANG_MAP.clear();

        // Close and clear translators from cache
        for (Translator translator : translatorCache.values()) {
            translator.close();
        }
        translatorCache.clear();
    }

    /*********************************************************
     **             Translator helper functions             **
     *********************************************************/
    //translate text helper method
    private void translateText(String inputText, final TextView outputTextView) {
        translator.translate(inputText)
                .addOnSuccessListener(outputTextView::setText)
                .addOnFailureListener(e ->
                        outputTextView.setText("Translation failed"));
    }

    /*********************************************************
     **             BLUETOOTH SERVICE CONNECTION            **
     *********************************************************/

    //ServiceConnection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            gattService = ((GATTService.LocalBinder) service).getService();
            isServiceBound = true;

            // Check if device is already connected
            if (gattService.isConnected()) {

            // Retrieve connected device from Singleton instead of gattService
                BluetoothGatt bluetoothGatt = GATTSingleton.getInstance().getBluetoothGatt();

                if (bluetoothGatt == null) {
                    //Toast.makeText(this, "GATT service not bind", Toast.LENGTH_LONG).show();
                    Log.i("ACT3", "BluetoothGatt is null");
                } else {
                    //Toast.makeText(this, "GATT service bind", Toast.LENGTH_LONG).show();
                    Log.i("ACT3", "BluetoothGatt is defined!" + bluetoothGatt);
                }

                BluetoothDevice currentlyConnected = (bluetoothGatt != null) ? bluetoothGatt.getDevice() : null;
                if (currentlyConnected != null) {
                    String deviceName = currentlyConnected.getName();
                    isServiceReady = true;
                    gattService.setTextUpdateListener(FourthActivity.this); // FourthActivity sets itself as the listener
                    // Update UI using Singleton references
                    runOnUiThread(() -> {
                        if (deviceName != null && !deviceName.isEmpty()) {
                            connectedDeviceTextView.setText(deviceName);
                        } else {
                            connectedDeviceTextView.setText("Unknown Device");
                        }
                    });

                    // Execute deferred actions
                    if (shouldStartDisplay) {
                        Log.e(SERVICE_TAG, "deferred input audio text display is now being executed.");
                        // fill in the display with the received audio text
                        if (gattService.availableAudioText()) {
                            Log.e(SERVICE_TAG, "ACT4: serviceConnection -- there is available audio text to display!");
                            textToTranslate = gattService.getAudioText();
                        }
                        Log.e(SERVICE_TAG, "ACT4: serviceConnection -- deferred input audio text display is: " + textToTranslate);
                        //inputTextView.setText(textToTranslate);
                        updateTextView(textToTranslate);
                        shouldStartDisplay = false;
                    } else {
                        Log.e(SERVICE_TAG, "no deferred input audio text display exists.");
                    }
                }
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