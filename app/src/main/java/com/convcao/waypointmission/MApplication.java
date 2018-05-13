package com.convcao.waypointmission;

import android.app.Application;
import android.content.Context;

import com.secneo.sdk.Helper;

public class MApplication extends Application{

    private DJIApplication fpvApplication;

    @Override
    protected void attachBaseContext(Context paramContext){
        super.attachBaseContext(paramContext);

        Helper.install(MApplication.this);
        if (fpvApplication == null){
            fpvApplication = new DJIApplication();
            fpvApplication.setContext(this);
        }
    }

    @Override
    public void onCreate(){
        super.onCreate();
        fpvApplication.onCreate();
    }

}
