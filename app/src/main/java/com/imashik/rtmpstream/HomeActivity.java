package com.imashik.rtmpstream;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        findViewById(R.id.btn_mobile).setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, MobileCamBroadcastActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        findViewById(R.id.btn_usb).setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, UsbCamBroadcastActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        findViewById(R.id.btn__multi_usb).setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, MultiUsbCamBroadcastActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

    }
}