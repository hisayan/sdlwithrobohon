package jp.co.anotherbrain.sdlwithrobohon;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.TTSChunkFactory;
import com.smartdevicelink.proxy.rpc.AddCommand;
import com.smartdevicelink.proxy.rpc.BeltStatus;
import com.smartdevicelink.proxy.rpc.GetVehicleData;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.MenuParams;
import com.smartdevicelink.proxy.rpc.OnCommand;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnVehicleData;
import com.smartdevicelink.proxy.rpc.Speak;
import com.smartdevicelink.proxy.rpc.SubscribeVehicleData;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.ComponentVolumeStatus;
import com.smartdevicelink.proxy.rpc.enums.ElectronicParkBrakeStatus;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.PRNDL;
import com.smartdevicelink.proxy.rpc.enums.VehicleDataEventStatus;
import com.smartdevicelink.proxy.rpc.enums.WiperStatus;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Vector;

public class SdlService extends Service {

    private static final String TAG 					= "SDL Service";

    private static final String APP_NAME 				= "SDL with RoBoHoN";
    private static final String APP_ID 					= "1234567";    //  TODO: change APP_ID

    private static final String WELCOME_SHOW            = "Welcome";

    private static final String ICON_FILENAME 			= "sdl_with_robohon_icon.png";
    private static final String SDL_IMAGE_FILENAME  	= "sdl_with_robohon_icon.png";

    private static final int FOREGROUND_SERVICE_ID = 111;   // TODO: change FOREGROUND_SERVICE_ID

    // TCP/IP transport config
    // The default port is 12345
    // The IP is of the machine that is running SDL Core
    private String DEV_MACHINE_IP_ADDRESS;
    private int TCP_PORT;

    // variable to create and call functions of the SyncProxy
    private SdlManager sdlManager = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.DEV_MACHINE_IP_ADDRESS =  prefs.getString("connect_uri", "m.sdl.tools");
        this.TCP_PORT = Integer.parseInt(prefs.getString("connect_port", "12345"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterForeground();
        }
    }

    // Helper method to let the service enter foreground mode
    @SuppressLint("NewApi")
    public void enterForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Notification serviceNotification = new Notification.Builder(this, channel.getId())
                        .setContentTitle("Connected through SDL")
                        .setSmallIcon(R.drawable.ic_sdl)
                        .build();
                startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startProxy();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        if (sdlManager != null) {
            sdlManager.dispose();
        }

        super.onDestroy();

    }

    private void startProxy() {
        // This logic is to select the correct transport and security levels defined in the selected build flavor
        // Build flavors are selected by the "build variants" tab typically located in the bottom left of Android Studio
        // Typically in your app, you will only set one of these.
        if (sdlManager == null) {
            Log.i(TAG, "Starting SDL Proxy");
            BaseTransportConfig transport = null;
            if (BuildConfig.TRANSPORT.equals("MULTI")) {
                int securityLevel;
                if (BuildConfig.SECURITY.equals("HIGH")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
                } else if (BuildConfig.SECURITY.equals("MED")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
                } else if (BuildConfig.SECURITY.equals("LOW")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
                } else {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
                }
                transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);
            } else if (BuildConfig.TRANSPORT.equals("TCP")) {
                transport = new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true);
            } else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
                MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
                mtc.setRequiresHighBandwidth(true);
                transport = mtc;
            }

            // The app type to be used
            Vector<AppHMIType> appType = new Vector<>();
            appType.add(AppHMIType.MEDIA);

            // The manager listener helps you know when certain events that pertain to the SDL Manager happen
            // Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
            SdlManagerListener listener = new SdlManagerListener() {
                @Override
                public void onStart() {
                    // HMI Status Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnHMIStatus status = (OnHMIStatus) notification;
                            if (status.getHmiLevel() == HMILevel.HMI_FULL && ((OnHMIStatus) notification).getFirstRun()) {
                                performWelcomeShow();
                            }
                        }
                    });


                    // SubscribeVehicleData
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_VEHICLE_DATA, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnVehicleData onVehicleDataNotification = (OnVehicleData) notification;

                            PRNDL prndl = onVehicleDataNotification.getPrndl();
                            if (prndl != null) {
                                Log.i("SdlService", "PRNDL was updated to: " + prndl.toString());
                                if (prndl.equals(PRNDL.REVERSE)) {
                                    Log.i("SdlService", "PRNDL was REVERSE");
                                    broadcastSDLEvent(MainActivity.SDLEvents.REVERSE);
                                }
                            }

                            Double speed = onVehicleDataNotification.getSpeed();
                            if (speed != null) {
                                Log.i("SdlService", "Speed was updated to: " + speed);
                                if (speed >= 105.00 ) {
                                    Log.i("SdlService", "over 105.00");
                                    // キンコーン
                                    broadcastSDLEvent(MainActivity.SDLEvents.KINKON);
                                }
                            }

                            Integer odometeer = onVehicleDataNotification.getOdometer();
                            if (odometeer != null) {
                                Log.i("SdlService", "Odometeer was updated to: " + speed);
                                if (odometeer == 77777 ) {
                                    Log.i("SdlService", "77777");
                                    // キリ番
                                    broadcastSDLEvent(MainActivity.SDLEvents.KIRI_77777);
                                }
                            }

                            // ワイパー
                            WiperStatus wiperStatus = onVehicleDataNotification.getWiperStatus();
                            if (wiperStatus != null) {
                                Log.i("SdlService", "Wiper status was updated to: " + wiperStatus);
                                // TODO: OFF から ON になったときだけ イベント発生させるか検討
                                if (! (wiperStatus.equals(WiperStatus.OFF)
                                        || wiperStatus.equals(WiperStatus.AUTO_OFF)
                                        || wiperStatus.equals(WiperStatus.OFF_MOVING)
                                        // || wiperStatus.equals(WiperStatus.MAN_INT_OFF)
                                    ) ) {
                                    broadcastSDLEvent(MainActivity.SDLEvents.RAIN);
                                }
                            }

                            // ガソリン量
                            ComponentVolumeStatus fuelLevelState = onVehicleDataNotification.getFuelLevelState();
                            if (fuelLevelState != null) {
                                Log.i("SdlService", "fuelLevel status was updated to: " + fuelLevelState);                                // TODO: OFF から ON になったときだけ イベント発生させるか検討
                                if (fuelLevelState.equals(ComponentVolumeStatus.LOW)) {
                                    broadcastSDLEvent(MainActivity.SDLEvents.FUEL);
                                }
                            }

                            // パーキングブレーキ解除
                            if (onVehicleDataNotification.getElectronicParkBrakeStatus() != null) {
                                Log.i("SdlService", "ElectronicParkBrake status was updated to: " + onVehicleDataNotification.getElectronicParkBrakeStatus());
                                if (onVehicleDataNotification.getElectronicParkBrakeStatus().equals(ElectronicParkBrakeStatus.OPEN)) {
                                    Log.i("SdlService", "get Belt status");
                                    // Driver Buckle Belted
                                    GetVehicleData vdRequest = new GetVehicleData();
                                    vdRequest.setBeltStatus(true);
                                    vdRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
                                        @Override
                                        public void onResponse(int correlationId, RPCResponse response) {
                                            if (response.getSuccess()) {
                                                BeltStatus beltStatus = ((GetVehicleDataResponse) response).getBeltStatus();
                                                Log.i("SdlService", "DriverBuckledBelted status: " + beltStatus.getDriverBuckleBelted().toString());
                                                if (beltStatus.getDriverBuckleBelted().equals(VehicleDataEventStatus.YES)) {
                                                    broadcastSDLEvent(MainActivity.SDLEvents.START);
                                                } else {
                                                    broadcastSDLEvent(MainActivity.SDLEvents.SEATBELT);
                                                }
                                            } else {
                                                Log.i("SdlService", "GetVehicleData was rejected.");
                                            }
                                        }
                                    });
                                    sdlManager.sendRPC(vdRequest);

                                }
                            }
                        }
                    });
                }

                @Override
                public void onDestroy() {
                    Log.v(TAG, "sdlManagerListener.onDestroy()");

                    SdlService.this.stopSelf();
                }

                @Override
                public void onError(String info, Exception e) {
                }
            };

            // Create App Icon, this is set in the SdlManager builder
            SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

            // The manager builder sets options for your session
            SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
            builder.setAppTypes(appType);
            builder.setTransportType(transport);
            builder.setAppIcon(appIcon);
            sdlManager = builder.build();
            sdlManager.start();

        }
    }

    /**
     * Use the Screen Manager to set the initial screen text and set the image.
     * Because we are setting multiple items, we will call beginTransaction() first,
     * and finish with commit() when we are done.
     */
    private void performWelcomeShow() {
        sdlManager.getScreenManager().beginTransaction();
        sdlManager.getScreenManager().setTextField1(APP_NAME);
        sdlManager.getScreenManager().setTextField2(WELCOME_SHOW);
        sdlManager.getScreenManager().setPrimaryGraphic(new SdlArtwork(SDL_IMAGE_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true));
        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                if (success){
                    Log.i(TAG, "welcome show successful");

                    // Subscribe Vehicle Data
                    SubscribeVehicleData subscribeRequest = new SubscribeVehicleData();
                    subscribeRequest.setElectronicParkBrakeStatus(true);    // OPEN
                    subscribeRequest.setSpeed(true);    // 105kph or 60kph
                    subscribeRequest.setPrndl(true);    // Back
                    subscribeRequest.setFuelLevelState(true);   // LOW
                    subscribeRequest.setOdometer(true); //   12345, 77777
                    subscribeRequest.setWiperStatus(true);
                    subscribeRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
                        @Override
                        public void onResponse(int correlationId, RPCResponse response) {
                            if(response.getSuccess()){
                                Log.i("SdlService", "Successfully subscribed to vehicle data.");
                            }else{
                                Log.i("SdlService", "Request to subscribe to vehicle data was rejected.");
                            }
                        }
                    });
                    sdlManager.sendRPC(subscribeRequest);

                }
            }
        });
    }

    /**
     * Will show a sample test message on screen as well as speak a sample test message
     */
    private void appendLog(String message){
        final DateFormat df = new SimpleDateFormat("HH:mm:ss");
        final Date date = new Date(System.currentTimeMillis());

        sdlManager.getScreenManager().beginTransaction();
        sdlManager.getScreenManager().setTextField1("Event");
        sdlManager.getScreenManager().setTextField2(df.format(date) + " " + message);
        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                if (success){
                    Log.i(TAG, "test command done successful");
                }
            }
        });
    }

    private void broadcastSDLEvent(MainActivity.SDLEvents sdlEvent) {
        Log.i(TAG, "broadcast SDL Event :  " + sdlEvent.toString());

        appendLog(sdlEvent.toString());

        Intent broadcast = new Intent();
        broadcast.putExtra("message", sdlEvent);
        broadcast.setAction(MainActivity.ACTION_SDL_EVENT);
        getBaseContext().sendBroadcast(broadcast);
    }

}
