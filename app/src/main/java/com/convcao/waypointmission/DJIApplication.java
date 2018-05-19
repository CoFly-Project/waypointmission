package com.convcao.waypointmission;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.multidex.MultiDex;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.sdkmanager.DJISDKManager;

public class DJIApplication extends Application {
    private static final String TAG = DJIApplication.class.getName();

    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";

    private DJISDKManager.SDKManagerCallback sdkManagerCallback;
    private BaseProduct.BaseProductListener baseProductListener;
    private BaseComponent.ComponentListener componentListener;
    private static BaseProduct product;
    private Handler handler;

    private Application instance;

    public void setContext(Application application) { instance = application; }

    @Override
    public Context getApplicationContext(){return instance;}

    public DJIApplication(){

    }

    public static synchronized BaseProduct getProductInstance() {
        if (product == null){
            product = DJISDKManager.getInstance().getProduct();
        }
        return product;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler(Looper.getMainLooper());
        componentListener = new BaseComponent.ComponentListener() {

            @Override
            public void onConnectivityChange(boolean isConnected) {
                notifyStatusChange();
            }

        };



        baseProductListener = new BaseProduct.BaseProductListener() {
            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey,
                                          BaseComponent oldComponent, BaseComponent newComponent) {
                if(newComponent != null) {
                    newComponent.setComponentListener(componentListener);
                }
                notifyStatusChange();
            }

            @Override
            public void onConnectivityChange(boolean b) {
                notifyStatusChange();
            }
        };

        /**
         * When starting SDK services, an instance of interface DJISDKManager.DJISDKManagerCallback will be used to listen to
         * the SDK Registration result and the product changing.
         */
        sdkManagerCallback = new DJISDKManager.SDKManagerCallback() {

            //Listens to the SDK registration result
            @Override
            public void onRegister(DJIError djiError) {
                if(djiError == DJISDKError.REGISTRATION_SUCCESS) {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Register Success", Toast.LENGTH_LONG).show();
                        }
                    });

                    DJISDKManager.getInstance().startConnectionToProduct();

                } else {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Register sdk fails, check network is available", Toast.LENGTH_LONG).show();
                        }
                    });

                }
                Log.e("TAG", djiError.toString());
            }

            //Listens to the connected product changing, including two parts, component changing or product connection changing.
            @Override
            public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
                product = newProduct;
                if (product != null){
                    product.setBaseProductListener(baseProductListener);
                }
                notifyStatusChange();
            }
        };

        //Check the permissions before registering the application for android system 6.0 above.
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionCheck2 = ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (permissionCheck == 0 && permissionCheck2 == 0)) {
            //This is used to start SDK services and initiate SDK.
            DJISDKManager.getInstance().registerApp(getApplicationContext(), sdkManagerCallback);
            Toast.makeText(getApplicationContext(), "registering, pls wait...", Toast.LENGTH_LONG).show();

        } else {
            Toast.makeText(getApplicationContext(), "Please check if the permission is granted.", Toast.LENGTH_LONG).show();
        }
    }


    protected void attachBaseContext(Context base){
        super.attachBaseContext(base);
        MultiDex.install(this);
    }


    private void notifyStatusChange() {
        handler.removeCallbacks(updateRunnable);
        handler.postDelayed(updateRunnable, 500);
    }

    private Runnable updateRunnable = new Runnable(){

        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            getApplicationContext().sendBroadcast(intent);
        }
    };

}
