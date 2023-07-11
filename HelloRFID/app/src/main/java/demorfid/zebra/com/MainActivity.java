package demorfid.zebra.com;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.zebra.rfid.api3.*;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static Readers readers;
    private static ArrayList availableRFIDReaderList;
    private static ReaderDevice readerDevice;
    private static RFIDReader reader;
    private static String TAG = "DEMO";
    TextView textView;
    private EventHandler eventHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /*
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        */

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        //
        // UI
        textView = findViewById(R.id.tagText);
        // SDK
        if (readers == null) {
            readers = new Readers(this, ENUM_TRANSPORT.SERVICE_SERIAL);
        }

        ExecutorService service = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (readers != null) {
                        if (readers.GetAvailableRFIDReaderList() != null) {
                            availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
                            if (availableRFIDReaderList.size() != 0) {
                                // get first reader from list
                                readerDevice = (ReaderDevice) availableRFIDReaderList.get(0);
                                reader = readerDevice.getRFIDReader();
                                if (!reader.isConnected()) {
                                    // Establish connection to the RFID Reader
                                    reader.connect();
                                    ConfigureReader();
                                }
                            }
                        }
                    }
                } catch (InvalidUsageException e) {
                    e.printStackTrace();
                } catch (OperationFailureException e) {
                    e.printStackTrace();
                    Log.d(TAG, "OperationFailureException " + e.getVendorMessage());
                }

                handler.post(() -> {
                    Toast.makeText(getApplicationContext(), "Reader Connected", Toast.LENGTH_LONG).show();
                    textView.setText("Reader connected");
                });
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void ConfigureReader() {
        if (reader.isConnected()) {
            TriggerInfo triggerInfo = new TriggerInfo();
            triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
            triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
            try {
                // receive events from reader
                if (eventHandler == null)
                    eventHandler = new EventHandler();
                reader.Events.addEventsListener(eventHandler);
                // HH event
                reader.Events.setHandheldEvent(true);
                // tag event with tag data
                reader.Events.setTagReadEvent(true);
                // application will collect tag using getReadTags API
                reader.Events.setAttachTagDataWithReadEvent(false);
                // set trigger mode as rfid so scanner beam will not come
                reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true);
                // set start and stop triggers
                reader.Config.setStartTrigger(triggerInfo.StartTrigger);
                reader.Config.setStopTrigger(triggerInfo.StopTrigger);
            } catch (InvalidUsageException | OperationFailureException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (reader != null) {
                reader.Events.removeEventsListener(eventHandler);
                reader.disconnect();
                Toast.makeText(getApplicationContext(), "Disconnecting reader", Toast.LENGTH_LONG).show();
                reader = null;
                readers.Dispose();
                readers = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Read/Status Notify handler
    // Implement the RfidEventsLister class to receive event notifications
    public static class EventHandler implements RfidEventsListener {
        // Read Event Notification
        public void eventReadNotify(RfidReadEvents e) {
            // Recommended to use new method getReadTagsEx for better performance in case of large tag population
            TagData[] myTags = reader.Actions.getReadTags(100);
            if (myTags != null) {
                for (TagData myTag : myTags) {
                    Log.d(TAG, "Tag ID " + myTag.getTagID());
                    if (myTag.getOpCode() == ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ &&
                            myTag.getOpStatus() == ACCESS_OPERATION_STATUS.ACCESS_SUCCESS) {
                        if (myTag.getMemoryBankData().length() > 0) {
                            Log.d(TAG, " Mem Bank Data " + myTag.getMemoryBankData());
                        }
                    }
                }
            }
        }

        // Status Event Notification
        @SuppressLint("StaticFieldLeak")
        public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
            Log.d(TAG, "Status Notification: " + rfidStatusEvents.StatusEventData.getStatusEventType());
            if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
                ExecutorService service = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());

                if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {

                    service.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                reader.Actions.Inventory.perform();
                            } catch (InvalidUsageException | OperationFailureException e) {
                                e.printStackTrace();
                            }
                        }
                    });

                }
                if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                    service.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                reader.Actions.Inventory.stop();
                            } catch (InvalidUsageException | OperationFailureException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        }
    }
}