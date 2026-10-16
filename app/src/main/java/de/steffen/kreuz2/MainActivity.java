package de.steffen.kreuz2;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView greeting = new TextView(this);
        greeting.setText(R.string.hello_world);
        greeting.setTextSize(32);
        greeting.setGravity(Gravity.CENTER);
        setContentView(greeting);
    }
}
