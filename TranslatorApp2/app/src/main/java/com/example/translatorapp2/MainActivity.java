package com.example.translatorapp2;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.Translation;

public class MainActivity extends AppCompatActivity {

    private Translator translator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final EditText inputEditText = findViewById(R.id.inputEditText);
        final Button translateButton = findViewById(R.id.translateButton);
        final TextView outputTextView = findViewById(R.id.outputText);

        // Configure source/target languages
        TranslatorOptions options =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(TranslateLanguage.SPANISH)
                        .build();
        translator = Translation.getClient(options);

        // Only download model over Wi-Fi
        DownloadConditions conditions =
                new DownloadConditions.Builder()
                        .requireWifi()
                        .build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        translateButton.setOnClickListener(v -> {
                            String textToTranslate = inputEditText.getText().toString();
                            translateText(textToTranslate, outputTextView);
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        outputTextView.setText("Model download failed");
                    }
                });
    }

    private void translateText(String inputText, final TextView outputTextView) {
        translator.translate(inputText)
                .addOnSuccessListener(new OnSuccessListener<String>() {
                    @Override
                    public void onSuccess(String translatedText) {
                        outputTextView.setText(translatedText);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        outputTextView.setText("Translation failed");
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translator != null) {
            translator.close();
        }
    }
}
