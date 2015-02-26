/*
 * Copyright (C) 2015 The MoKee OpenSource Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mokee.setupwizard.setup;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import com.mokee.setupwizard.R;
import com.mokee.setupwizard.SetupWizardApp;
import com.mokee.setupwizard.ui.LoadingFragment;
import com.mokee.setupwizard.ui.SetupWizardActivity;
import com.mokee.setupwizard.util.SetupWizardUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class WifiSetupPage extends SetupPage {

    public static final String TAG = "WifiSetupPage";

    private static final String DEFAULT_SERVER = "download.mokeedev.com";
    private static final int CAPTIVE_PORTAL_SOCKET_TIMEOUT_MS = 10000;

    private static final String CAPTIVE_PORTAL_LOGIN_ACTION
            = "android.net.action.captive_portal_login";

    private LoadingFragment mLoadingFragment;

    private URL mCaptivePortalUrl;

    private boolean mIsCaptivePortal = false;

    private final Handler mHandler = new Handler();

    private Runnable mFinishCaptivePortalCheckRunnable = new Runnable() {
        @Override
        public void run() {
            final Activity activity = mContext;
            if (mIsCaptivePortal) {
                try {
                    int netId = ConnectivityManager.from(activity)
                            .getNetworkForType(ConnectivityManager.TYPE_WIFI).netId;
                    Intent intent = new Intent(CAPTIVE_PORTAL_LOGIN_ACTION);
                    intent.putExtra(Intent.EXTRA_TEXT, String.valueOf(netId));
                    intent.putExtra("status_bar_color",
                            mContext.getResources().getColor(R.color.primary_dark));
                    intent.putExtra("action_bar_color", mContext.getResources().getColor(
                            R.color.primary_dark));
                    intent.putExtra("progress_bar_color", mContext.getResources().getColor(
                            R.color.accent));
                    ActivityOptions options =
                            ActivityOptions.makeCustomAnimation(mContext,
                                    android.R.anim.fade_in,
                                    android.R.anim.fade_out);
                    activity.startActivityForResult(intent,
                            SetupWizardApp.REQUEST_CODE_SETUP_CAPTIVE_PORTAL,
                            options.toBundle());
                } catch (Exception e) {
                    //Oh well
                    Log.e(TAG, "No captive portal activity found" + e);
                    getCallbacks().onNextPage();
                }
            } else {
                getCallbacks().onNextPage();
            }
        }
    };

    public WifiSetupPage(SetupWizardActivity context, SetupDataCallbacks callbacks) {
        super(context, callbacks);
        String server = Settings.Global.getString(context.getContentResolver(), "captive_portal_server");
        if (server == null) server = DEFAULT_SERVER;
        try {
            mCaptivePortalUrl = new URL("http://" + server + "/generate_204");
        } catch (MalformedURLException e) {
            Log.e(TAG, "Not a valid url" + e);
        }
    }

    @Override
    public Fragment getFragment(FragmentManager fragmentManager, int action) {
        mLoadingFragment = (LoadingFragment)fragmentManager.findFragmentByTag(getKey());
        if (mLoadingFragment == null) {
            Bundle args = new Bundle();
            args.putString(Page.KEY_PAGE_ARGUMENT, getKey());
            args.putInt(Page.KEY_PAGE_ACTION, action);
            mLoadingFragment = new LoadingFragment();
            mLoadingFragment.setArguments(args);
        }
        return mLoadingFragment;
    }

    @Override
    public int getNextButtonTitleResId() {
        return R.string.skip;
    }

    @Override
    public String getKey() {
        return TAG;
    }

    @Override
    public int getTitleResId() {
        return R.string.loading;
    }

    @Override
    public void doLoadAction(SetupWizardActivity context, int action) {
        super.doLoadAction(context, action);
        SetupWizardUtils.launchWifiSetup(context);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SetupWizardApp.REQUEST_CODE_SETUP_WIFI) {
            if (resultCode == Activity.RESULT_CANCELED) {
                getCallbacks().onPreviousPage();
            } else if (resultCode == Activity.RESULT_OK) {
                checkForCaptivePortal();
            } else {
                getCallbacks().onNextPage();
            }
        } else if (requestCode == SetupWizardApp.REQUEST_CODE_SETUP_CAPTIVE_PORTAL) {
            if (resultCode == Activity.RESULT_CANCELED) {
                SetupWizardUtils.launchWifiSetup((Activity)mContext);
            } else {
                getCallbacks().onNextPage();
            }
        }  else {
            return false;
        }
        return true;
    }

    private void checkForCaptivePortal() {
        new Thread() {
            @Override
            public void run() {
                mIsCaptivePortal = isCaptivePortal();
                mHandler.post(mFinishCaptivePortalCheckRunnable);
            }
        }.start();
    }

    // Don't run on UI thread
    private boolean isCaptivePortal() {
        if (mCaptivePortalUrl == null) return false;
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) mCaptivePortalUrl.openConnection();
            urlConnection.setInstanceFollowRedirects(false);
            urlConnection.setConnectTimeout(CAPTIVE_PORTAL_SOCKET_TIMEOUT_MS);
            urlConnection.setReadTimeout(CAPTIVE_PORTAL_SOCKET_TIMEOUT_MS);
            urlConnection.setUseCaches(false);
            urlConnection.getInputStream();
            // We got a valid response, but not from the real google
            return urlConnection.getResponseCode() != 204;
        } catch (IOException e) {
            Log.e(TAG, "Captive portal check - probably not a portal: exception "
                    + e);
            return false;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
