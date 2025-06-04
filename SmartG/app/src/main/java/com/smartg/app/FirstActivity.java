package com.smartg.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class FirstActivity extends AppCompatActivity {

    //    This method is called when the activity is first created.
    //    It is where you should perform initial setup, such as creating views, binding data to lists,
    //    and initializing variables.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        // Initialize button using findViewById
        // Declare Button object
        Button nextActivityButton = findViewById(R.id.start_button);

        // Set click listener for the button
        nextActivityButton.setOnClickListener(v -> {
            // Create Intent to start SecondActivity
            Intent intent = new Intent(FirstActivity.this, SecondActivity.class);
            // Start the activity
            startActivity(intent);
        });
    }
}
