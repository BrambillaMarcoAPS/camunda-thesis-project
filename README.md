**README - Applicazione Android + Wear OS con integrazione Camunda BPMN
Questo progetto consiste in un'applicazione Android (modulo “phone”) e in un’applicazione Wear OS (modulo “watch”) che collaborano per raccogliere e scambiare dati (come il livello di batteria e la temperatura) e integrano un motore BPMN tramite Camunda (usando Zeebe).

CONTENUTI DEL PROGETTO:

MODULO PHONE: codice Android principale (smartphone), che:
-Avvia e gestisce i processi BPMN su Camunda Cloud.
-Comunica col Wearable via Google Play Services / Wearable API per ricevere dati di batteria e temperatura dal watch.
-Legge batteria e temperatura dal dispositivo Android stesso.
-Esegue dei Job Worker registrati sul broker Zeebe per arricchire o completare il flusso di processo definito in BPMN.

MODULO WATCH: codice Wear OS, che:
-Risponde alle richieste provenienti dall’app phone (livello di batteria, temperatura).
-Comunica col telefono.


------------------------------------------------------------------------------------------------------------------------
MODULO PHONE

MainActivity.java
-Attiva la connessione con Zeebe tramite ZeebeConfig.createZeebeClient().
-Avvia i Job Worker per gestire i compiti BPMN:
---return-watch-temperature-to-app
---return-phone-temperature-to-app
---retrieve-smartphone-battery-level
---retrieve-smartwatch-battery-level
---retrieve-temperature-from-smartphone
---retrieve-temperature-from-smartwatch
-Definisce le funzioni per leggere la batteria del telefono e per inviare/ricevere messaggi dal Watch.
-Con il pulsante “Start Process”, crea un’istanza di processo su Camunda BPMN (con id: process_random_demo).


ZeebeConfig.java 
Classe di configurazione Zeebe che ritorna il ZeebeClient configurato, con le credenziali per Camunda Cloud.
--------------------------------------------------------------------------------------------------------------------------

MODULO WATCH (com.example.myapplication)

MainActivity.java:
-Riceve i messaggi dal telefono tramite l’API MessageClient e gestisce due tipologie di path:
---/request_battery: al ricevimento, legge la batteria del watch e risponde con /battery_message.
---/request_temperature: al ricevimento, legge la temperatura del watch e risponde con /temperature_message.
-Invia messaggi di risposta con i dati richiesti usando sendMessage().

---------------------------------------------------------------------------------------------------------------------------

FLUSSO DI FUNZIONAMENTO

1)Avvio Processo
L'utente preme il pulsante “Start Process” nell’app telefono.
Il metodo avviaProcesso() invoca zeebeClient.newCreateInstanceCommand() per avviare l’istanza BPMN definita da process_random_demo.

2)Esecuzione dei Service Task
Il motore BPMN (Zeebe) incontra i vari task di tipo Service; grazie alla subscription (i Worker attivati nell’onCreate di MainActivity), l’app Android ottiene i compiti come:
--retrieve-smartphone-battery-level: legge la batteria del telefono.
--retrieve-smartwatch-battery-level: invia un messaggio di richiesta al Watch (path /request_battery), attende la risposta (path /battery_message) e memorizza il livello di batteria in una variabile di processo.
--retrieve-temperature-from-smartphone: attiva il sensore di temperatura sul telefono (se disponibile) e salva il valore ottenuto.
--retrieve-temperature-from-smartwatch: invia un messaggio (path /request_temperature), riceve la risposta (path /temperature_message), quindi memorizza la temperatura in variabile di processo.
--return-watch-temperature-to-app //// return-phone-temperature-to-app: prendono la variabile di processo (watchTemperature e phoneTemperature) e la mostrano in UI (textViewResult).

3)Scambio di dati con Wear OS
Il telefono utilizza Wearable.getMessageClient(this).sendMessage(...) per mandare messaggi al Watch.
Il Watch registra un listener onMessageReceived e risponde immediatamente con i valori richiesti (sendMessage() verso il telefono).

4)Una volta che i vari Task sono completati, il flusso BPMN prosegue fino a una End Event.





DIAGRAMMA BPMN PRESENTE SU CAMUNDA CLOUD:
![image](https://github.com/user-attachments/assets/50e16cfe-6731-4bf3-97fa-42de0886559a)
