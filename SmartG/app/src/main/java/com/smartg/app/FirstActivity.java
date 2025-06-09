package com.smartg.app;
//LIBRARIES
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

// first activity is an activity class
// first activity is the android page that is the "landing" page for the app when users first open the app
// a simple picture is on display and underneath is a start button that navigates user to the next android page (second activity)
public class FirstActivity extends AppCompatActivity {

    /**************************************************************
     **                 LIFECYCLE methods of ACTIVITY            **
     **************************************************************/

    //    This method is called when the activity is first created.
    //    It is where you should perform initial setup, such as creating views, binding data to lists,
    //    and initializing variables.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        // setup intent for button that navigates first activity to second activity:
        // Declare Button object
        Button nextActivityButton = findViewById(R.id.start_button); // Initialize button using findViewById
        // Set click listener for the button
        nextActivityButton.setOnClickListener(v -> {
            // Create Intent to start SecondActivity
            Intent intent = new Intent(FirstActivity.this, SecondActivity.class);
            // Start the activity
            startActivity(intent);
        });
    }
}
