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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.backup.IBackupManager;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;

import com.mokee.setupwizard.R;
import com.mokee.setupwizard.ui.SetupPageFragment;

import java.util.Observable;
import java.util.Observer;

public class OtherSettingsPage extends SetupPage {

    private static final String TAG = "OtherSettingsPage";

    public OtherSettingsPage(Context context, SetupDataCallbacks callbacks) {
        super(context, callbacks);
    }

    @Override
    public Fragment getFragment(FragmentManager fragmentManager, int action) {
        Fragment fragment = fragmentManager.findFragmentByTag(getKey());
        if (fragment == null) {
            Bundle args = new Bundle();
            args.putString(Page.KEY_PAGE_ARGUMENT, getKey());
            args.putInt(Page.KEY_PAGE_ACTION, action);
            fragment = new OtherSettingsFragment();
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public String getKey() {
        return TAG;
    }

    @Override
    public int getTitleResId() {
        return R.string.setup_other;
    }

    public static class OtherSettingsFragment extends SetupPageFragment {

        private View mBackupRow;
        private View mLocationRow;
        private View mGpsRow;
        private View mNetworkRow;
        private CheckBox mBackup;
        private CheckBox mNetwork;
        private CheckBox mGps;
        private CheckBox mLocationAccess;

        private ContentResolver mContentResolver;

        private IBackupManager mBackupManager;

        // These provide support for receiving notification when Location Manager settings change.
        // This is necessary because the Network Location Provider can change settings
        // if the user does not confirm enabling the provider.
        private ContentQueryMap mContentQueryMap;
        private Observer mSettingsObserver;


        private View.OnClickListener mBackupClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onToggleBackup(!mBackup.isChecked());
            }
        };

        private View.OnClickListener mLocationClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onToggleLocationAccess(!mLocationAccess.isChecked());
            }
        };

        private View.OnClickListener mGpsClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Settings.Secure.setLocationProviderEnabled(mContentResolver,
                        LocationManager.GPS_PROVIDER, !mGps.isChecked());
            }
        };

        private View.OnClickListener mNetworkClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Settings.Secure.setLocationProviderEnabled(mContentResolver,
                        LocationManager.NETWORK_PROVIDER, !mNetwork.isChecked());
            }
        };

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            mContentResolver = getActivity().getContentResolver();
            mBackupManager = IBackupManager.Stub.asInterface(
                    ServiceManager.getService(Context.BACKUP_SERVICE));
            getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
        }

        @Override
        protected void initializePage() {
            mBackupRow = mRootView.findViewById(R.id.backup);
            mBackupRow.setOnClickListener(mBackupClickListener);
            mBackup = (CheckBox) mRootView.findViewById(R.id.backup_checkbox);
            mLocationRow = mRootView.findViewById(R.id.location);
            mLocationRow.setOnClickListener(mLocationClickListener);
            mLocationAccess = (CheckBox) mRootView.findViewById(R.id.location_checkbox);
            mGpsRow = mRootView.findViewById(R.id.gps);
            mGpsRow.setOnClickListener(mGpsClickListener);
            mGps = (CheckBox) mRootView.findViewById(R.id.gps_checkbox);
            mNetworkRow = mRootView.findViewById(R.id.network);
            mNetworkRow.setOnClickListener(mNetworkClickListener);
            mNetwork = (CheckBox) mRootView.findViewById(R.id.network_checkbox);
        }

        @Override
        protected int getLayoutResource() {
            return R.layout.location_settings;
        }

        @Override
        public void onResume() {
            super.onResume();
            updateLocationToggles();
            updateBackupToggle();
            if (mSettingsObserver == null) {
                mSettingsObserver = new Observer() {
                    public void update(Observable o, Object arg) {
                        updateLocationToggles();
                        updateBackupToggle();
                    }
                };
            }

            mContentQueryMap.addObserver(mSettingsObserver);
        }

        @Override
        public void onStart() {
            super.onStart();
            // listen for Location Manager settings changes
            Cursor settingsCursor = getActivity().getContentResolver().query(Settings.Secure.CONTENT_URI, null,
                    "(" + Settings.System.NAME + "=?)",
                    new String[]{Settings.Secure.LOCATION_PROVIDERS_ALLOWED},
                    null);
            mContentQueryMap = new ContentQueryMap(settingsCursor, Settings.System.NAME, true, null);
        }

        @Override
        public void onStop() {
            super.onStop();
            if (mSettingsObserver != null) {
                mContentQueryMap.deleteObserver(mSettingsObserver);
            }
            mContentQueryMap.close();
        }

        private boolean isBackupRestoreEnabled() {
            try {
                return mBackupManager.isBackupEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void updateBackupToggle() {
            mBackup.setChecked(isBackupRestoreEnabled());
        }

        private void onToggleBackup(boolean checked) {
            try {
                mBackupManager.setBackupEnabled(checked);
            } catch (RemoteException e) {}
            updateBackupToggle();
        }

        private void updateLocationToggles() {
            boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
                    mContentResolver, LocationManager.GPS_PROVIDER);
            boolean networkEnabled = Settings.Secure.isLocationProviderEnabled(
                    mContentResolver, LocationManager.NETWORK_PROVIDER);
            mGps.setChecked(gpsEnabled);
            mNetwork.setChecked(networkEnabled);
            mLocationAccess.setChecked(gpsEnabled || networkEnabled);
        }

        private void onToggleLocationAccess(boolean checked) {
            Settings.Secure.setLocationProviderEnabled(mContentResolver,
                    LocationManager.GPS_PROVIDER, checked);
            mGps.setEnabled(checked);
            mGpsRow.setEnabled(checked);
            Settings.Secure.setLocationProviderEnabled(mContentResolver,
                    LocationManager.NETWORK_PROVIDER, checked);
            mNetwork.setEnabled(checked);
            mNetworkRow.setEnabled(checked);
            updateLocationToggles();
        }

    }
}
