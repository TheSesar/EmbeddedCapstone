package com.smartg.app;


// Interface allows service class to communicate with fourth activity's UI element: the textview that displays the received audio transcript
public interface TextUpdateListener {

    //function parameter: read characteristic value
    //updates the UI element (nestedTextView_In_act4_1) to display the read value
    void onTextReceived(String text);
}
