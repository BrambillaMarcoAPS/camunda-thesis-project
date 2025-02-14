package com.example.camunda;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import android.util.Log;
import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends AppCompatActivity implements MessageClient.OnMessageReceivedListener {

    private Button buttonStartProcess;
    private TextView textViewResult;

    // Campo statico per salvare l'ultima batteria ricevuta dal watch
    private static volatile float lastWatchBatteryLevel = -1f;

    // Path per inviare la richiesta
    private static final String REQUEST_BATTERY_PATH = "/request_battery";
    // Path per ricevere la risposta del watch
    private static final String BATTERY_MESSAGE_PATH = "/battery_message";
    private ZeebeClient zeebeClient;
    private static final String TAG = "PhoneMainActivity";

    private volatile float phoneTempReading;
    private volatile boolean phoneSensorHasValue;
    private volatile float lastWatchTemperature;
    private volatile boolean watchTempReceived;

    private TextView tvBatteryFromWatch;
    private Button btnRequestBattery;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvBatteryFromWatch = findViewById(R.id.tvBatteryFromWatch);
        btnRequestBattery = findViewById(R.id.btnRequestBattery);
        // registro l'activity come listener del data Layer
        Wearable.getMessageClient(this).addListener(this);
        //da eliminare
        btnRequestBattery.setOnClickListener(v -> {requestBatteryFromWatch();});
        buttonStartProcess = findViewById(R.id.buttonStartProcess);
        textViewResult = findViewById(R.id.textViewResult);

        // inizializzo la connessione a Zeebe
        zeebeClient = ZeebeConfig.createZeebeClient();

        // avvio i job-worker in background
        startWorkers();

        // controlla i nodi connessi
        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
            for (com.google.android.gms.wearable.Node node : nodes) {
                Log.d(TAG, "Telefono vede nodo: " + node.getDisplayName());
            }
        });


        buttonStartProcess.setOnClickListener(view -> avviaProcesso());
    }

    /**
     * Invia un messaggio "/request_battery" al watch,
     * in modo che ci risponda con la batteria.
     */
    private void requestBatteryFromWatch() {
        // Trovo il watch
        Task<List<Node>> nodeListTask = Wearable.getNodeClient(this).getConnectedNodes();
        nodeListTask.addOnSuccessListener(nodes -> {
            for (Node node : nodes) {
                // invio un messaggio vuoto
                byte[] emptyPayload = new byte[0];
                Task<Integer> sendMessageTask =
                        Wearable.getMessageClient(this)
                                .sendMessage(node.getId(), REQUEST_BATTERY_PATH, emptyPayload);

                sendMessageTask.addOnSuccessListener(integer ->
                        Log.d(TAG, "Richiesta inviata al watch: " + node.getDisplayName()));
            }
        });
    }

    /**
     * Riceviamo i messaggi in arrivo dal watch.
     * Se path = /battery_message, significa che ci ha mandato il suo livello di batteria.
     */
    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived - path: " + messageEvent.getPath());
        if (messageEvent.getPath().equals(BATTERY_MESSAGE_PATH)) {
            String payloadString = new String(messageEvent.getData(), StandardCharsets.UTF_8);
            try {
                float batteryPct = Float.parseFloat(payloadString);
                Log.d(TAG, "Batteria del watch ricevuta: " + batteryPct);

                // aggiorno la UI
                runOnUiThread(() ->
                        tvBatteryFromWatch.setText("Batteria Watch: " + batteryPct + "%")
                );

                // salvo in lastWatchBatteryLevel per il worker "retrieve-smartwatch-battery-level"
                lastWatchBatteryLevel = batteryPct;

            } catch (NumberFormatException e) {
                Log.e(TAG, "Impossibile convertire payload in float", e);
            }
        }
    }


    private void startWorkers() {
        // Worker per "return-watch-temperature-to-app"
        zeebeClient
                .newWorker()
                .jobType("return-watch-temperature-to-app") // Job Type del service task finale
                .handler((jobClient, job) -> {
                    // ottengo la variabile resultData dal processo
                    Map<String, Object> vars = job.getVariablesAsMap();
                    String watchTemperature = String.valueOf(vars.get("watchTemperature"));

                    // aggiorno la UI
                    runOnUiThread(() -> {
                        textViewResult.setText("Temperature: " + watchTemperature + ".C" );
                    });

                    // completo il job
                    jobClient
                            .newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .open();

        // Worker per "return-phone-temperature-to-app"
        zeebeClient
                .newWorker()
                .jobType("return-phone-temperature-to-app") // Job Type del Service Task finale
                .handler((jobClient, job) -> {
                    // ottengo la variabile resultData dal processo
                    Map<String, Object> vars = job.getVariablesAsMap();
                    String phoneTemperature = String.valueOf(vars.get("phoneTemperature"));

                    // aggiorno la UI
                    runOnUiThread(() -> {
                        textViewResult.setText("Temperature: " + phoneTemperature );
                    });

                    // completo il job
                    jobClient
                            .newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .open();

        //retrieve-smartphone-battery-level
        zeebeClient
                .newWorker()
                .jobType("retrieve-smartphone-battery-level")
                .handler((jobClient, job) -> {
                    // leggo la batteria del telefono
                    float phoneBatt = getPhoneBatteryLevel();
                    Log.d(TAG, "retrieve-smartphone-battery-level -> phoneBatt: " + phoneBatt);

                    // salvo in una variabile di processo
                    Map<String, Object> vars = job.getVariablesAsMap();
                    vars.put("phoneBattery", phoneBatt);

                    // completo il job
                    jobClient.newCompleteCommand(job.getKey())
                            .variables(vars)
                            .send()
                            .join();
                })
                .open();


        //retrieve-smartwatch-battery-level
        zeebeClient
                .newWorker()
                .jobType("retrieve-smartwatch-battery-level")
                .handler((jobClient, job) -> {
                    // resetto lastWatchBatteryLevel prima di chiedere al watch perché potrebbe essere stato cambiato in precedenza
                    lastWatchBatteryLevel = -1f;

                    // mando richiesta al watch
                    requestBatteryFromWatch(); // asincrono

                    // aspetto la risposta per max 5sec (da migliorare con callback)
                    int maxAttempts = 50;
                    int attempt = 0;
                    while (attempt < maxAttempts && lastWatchBatteryLevel < 0) {
                        try {
                            Thread.sleep(100); // attesa 100ms a loop
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        attempt++;
                    }

                    //per debug se non va la richiesta al watch (simulo): int watchBatt = new Random().nextInt(100) + 1;

                    // Se lastWatchBatteryLevel >= 0, ho ricevuto la batteria
                    float watchBatt = lastWatchBatteryLevel;
                    Log.d(TAG, "retrieve-smartwatch-battery-level -> watchBatt: " + watchBatt);

                    Map<String, Object> vars = job.getVariablesAsMap();
                    if (watchBatt < 0) {
                        // Non è arrivata risposta
                        vars.put("watchBattery", "ERROR_NO_RESPONSE");
                    } else {
                        // Ok, ho la batteria
                        vars.put("watchBattery", watchBatt);
                    }

                    // completo il job
                    jobClient.newCompleteCommand(job.getKey())
                            .variables(vars)
                            .send()
                            .join();
                })
                .open();

        // Worker "retrieve-temperature-from-smartphone"
        zeebeClient
                .newWorker()
                .jobType("retrieve-temperature-from-smartphone")
                .handler((jobClient, job) -> {

                    // resetto una variabile di appoggio
                    phoneTempReading = Float.NaN;
                    phoneSensorHasValue = false;

                    // registro un listener per il sensore di temperatura
                    SensorManager sensorManager =
                            (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                    Sensor tempSensor =
                            sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

                    if (tempSensor == null) {
                        Log.w(TAG, "retrieve-temperature-from-smartphone: Nessun sensore di temperatura disponibile!");
                        // Metto un valore di errore
                        phoneTempReading = -999f;
                        phoneSensorHasValue = true;
                    } else {
                        // creo un listener
                        SensorEventListener phoneTempListener = new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent event) {
                                if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                                    float tempValue = event.values[0];  // gradi Celsius
                                    Log.d(TAG, "Smartphone temperature sensor: " + tempValue);
                                    phoneTempReading = tempValue;
                                    phoneSensorHasValue = true;
                                    // Dopo la prima lettura, deregistraro il listener
                                    sensorManager.unregisterListener(this, tempSensor);
                                }
                            }
                            @Override
                            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                        };
                        // registro il listener con un certo rate
                        sensorManager.registerListener(
                                phoneTempListener,
                                tempSensor,
                                SensorManager.SENSOR_DELAY_NORMAL
                        );
                    }

                    // aspetto fino a 2 secondi una lettura (da migliorare con callback)
                    int attempts = 0;
                    while (!phoneSensorHasValue && attempts < 20) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        attempts++;
                    }

                    // metto phoneTempReading nella variabile di processo
                    Map<String, Object> vars = job.getVariablesAsMap();
                    vars.put("phoneTemperature", phoneTempReading);

                    // Completo il job
                    jobClient.newCompleteCommand(job.getKey())
                            .variables(vars)
                            .send()
                            .join();

                })
                .open();

        // Worker "retrieve-temperature-from-smartwatch"
        zeebeClient
                .newWorker()
                .jobType("retrieve-temperature-from-smartwatch")
                .handler((jobClient, job) -> {

                    // reset di una variabile globale di classe che conterrà la risposta
                    lastWatchTemperature = Float.NaN;
                    watchTempReceived = false;

                    // mando un messaggio "richiesta temperatura" al watch
                    requestTemperatureFromWatch(); // simile a "requestBatteryFromWatch" ma con PATH "/request_temperature"

                    // attendo la risposta (max 3 sec)
                    int attempts = 0;
                    while (!watchTempReceived && attempts < 30) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        attempts++;
                    }
                    //se non va la richiesta al watch (simulo): float watchTempToStore = new Random().nextInt(100) + 1;
                    float watchTempToStore = (Float.isNaN(lastWatchTemperature))
                            ? -999f  // se non è arrivata risposta
                            : lastWatchTemperature;

                    Log.d(TAG, "retrieve-temperature-from-smartwatch -> watchTemp: " + watchTempToStore);

                    // metto la temperatura in "watchTemperature"

                    Map<String, Object> vars = job.getVariablesAsMap();
                    vars.put("watchTemperature", watchTempToStore);

                    // completo il job
                    jobClient.newCompleteCommand(job.getKey())
                            .variables(vars)
                            .send()
                            .join();

                })
                .open();

    }

    /**
     * Legge la batteria del telefono come float
     */
    private float getPhoneBatteryLevel() {
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

    private void requestTemperatureFromWatch() {
        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    for (Node node : nodes) {
                        // Manda "/request_temperature"
                        byte[] emptyPayload = new byte[0];
                        Wearable.getMessageClient(this)
                                .sendMessage(node.getId(), "/request_temperature", emptyPayload);
                    }
                });
    }


    /**
     * Avvia il processo BPMN su Camunda Cloud, passando una variabile "randomValue".
     */
    private void avviaProcesso() {
        textViewResult.setText("Processo su camunda avviato");


        // avvio del processo
        new Thread(() -> {
            try {
                // chiama il processo
                ProcessInstanceEvent event = zeebeClient
                        .newCreateInstanceCommand()
                        .bpmnProcessId("process_random_demo")
                        .latestVersion()
                        .send()
                        .join();

                long processInstanceKey = event.getProcessInstanceKey();

                runOnUiThread(() -> {
                    textViewResult.append("\nProcess instance key: " + processInstanceKey);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    textViewResult.setText("Errore avviando il processo: " + e.getMessage());
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        // Rimuovo il listener quando l'activity viene chiusa
        Wearable.getMessageClient(this).removeListener(this);
        super.onDestroy();

        if (zeebeClient != null) {
            zeebeClient.close();
        }
    }
}
