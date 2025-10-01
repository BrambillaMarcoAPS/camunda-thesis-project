package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity
        implements MessageClient.OnMessageReceivedListener {

    private static final String TAG = "WearMainActivity";
    // Path di richiesta
    private static final String REQUEST_BATTERY_PATH = "/request_battery";
    // Path di risposta
    private static final String BATTERY_MESSAGE_PATH = "/battery_message";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    for (Node n : nodes) {
                        Log.d(TAG, "Watch vede nodo: " + n.getDisplayName());
                    }
                });
        // Registro questa activity come listener per i messaggi dal phone
        Wearable.getMessageClient(this).addListener(this);
    }

    /**
     * Se arriva un messaggio dal telefono con "/request_battery",
     * leggo la batteria e rispondo con "/battery_message".
     */
    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived - path: " + messageEvent.getPath());
        if (messageEvent.getPath().equals(REQUEST_BATTERY_PATH)) {
            float batteryPct = getWatchBatteryLevel();
            Log.d(TAG, "Richiesta batteria ricevuta. Batteria attuale: " + batteryPct);

            // Rispondo con "/battery_message"
            sendBatteryToPhone(batteryPct, messageEvent.getSourceNodeId());
        }

        if (messageEvent.getPath().equals("/request_temperature")) {
            // leggo il sensore di temperatura e poi rispondo
            float watchTemp = readWatchTemperatureSensor();
            replyTemperatureToPhone(watchTemp, messageEvent.getSourceNodeId());
        }
    }

    /**
     * Legge la batteria dal watch
     */
    private float getWatchBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus == null) {
            return -1f;
        }
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level == -1 || scale == -1) {
            return -1f;
        }
        return (level / (float) scale) * 100f;
    }




    private float readWatchTemperatureSensor() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

        if (tempSensor == null) {
            Log.w("WatchMainActivity", "Nessun sensore di temperatura sullo smartwatch!");
            return -999f;
        }


        return event.values[0];
    }

    private void replyTemperatureToPhone(float temperature, String nodeId) {
        String payloadString = String.valueOf(temperature);
        byte[] payload = payloadString.getBytes(StandardCharsets.UTF_8);

        Wearable.getMessageClient(this)
                .sendMessage(nodeId, "/temperature_message", payload)
                .addOnSuccessListener(integer ->
                        Log.d("WatchMainActivity", "Temperatura watch inviata al telefono: " + temperature)
                );
    }

    /**
     * Invia un messaggio al telefono con "/battery_message"
     */
    private void sendBatteryToPhone(float batteryPct, String nodeId) {
        String payloadString = String.valueOf(batteryPct);
        byte[] payload = payloadString.getBytes(StandardCharsets.UTF_8);

        Task<Integer> sendMessageTask =
                Wearable.getMessageClient(this).sendMessage(nodeId, BATTERY_MESSAGE_PATH, payload);

        sendMessageTask.addOnSuccessListener(integer -> Log.d(TAG, "Batteria inviata al telefono: " + batteryPct));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Rimuovo il listener
        Wearable.getMessageClient(this).removeListener(this);
    }
}
