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

package com.mokee.setupwizard.ui;

import android.animation.Animator;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.Button;

import com.mokee.setupwizard.R;
import com.mokee.setupwizard.SetupWizardApp;
import com.mokee.setupwizard.setup.MKSetupWizardData;
import com.mokee.setupwizard.setup.Page;
import com.mokee.setupwizard.setup.SetupDataCallbacks;
import com.mokee.setupwizard.util.EnableAccessibilityController;
import com.mokee.setupwizard.util.SetupWizardUtils;

public class SetupWizardActivity extends Activity implements SetupDataCallbacks {

    private static final String TAG = SetupWizardActivity.class.getSimpleName();

    private View mRootView;
    private View mButtonBar;
    private Button mNextButton;
    private Button mPrevButton;
    private View mReveal;

    private EnableAccessibilityController mEnableAccessibilityController;

    private MKSetupWizardData mSetupData;

    private final Handler mHandler = new Handler();

    private boolean mIsGuestUser = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_main);
        getWindow().setWindowAnimations(android.R.anim.fade_in);
        mRootView = findViewById(R.id.root);
        mButtonBar = findViewById(R.id.button_bar);
        ((SetupWizardApp)getApplicationContext()).disableStatusBar();
        mSetupData = (MKSetupWizardData)getLastNonConfigurationInstance();
        if (mSetupData == null) {
            mSetupData = new MKSetupWizardData(this);
        }
        mNextButton = (Button) findViewById(R.id.next_button);
        mPrevButton = (Button) findViewById(R.id.prev_button);
        mReveal = findViewById(R.id.reveal);
        setupRevealImage();
        mSetupData.registerListener(this);
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableButtonBar(false);
                mSetupData.onNextPage();
            }
        });
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableButtonBar(false);
                mSetupData.onPreviousPage();
            }
        });
        if (savedInstanceState == null) {
            Page page = mSetupData.getCurrentPage();
            page.doLoadAction(this, Page.ACTION_NEXT);
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("data")) {
            mSetupData.load(savedInstanceState.getBundle("data"));
        }
        if (EnableAccessibilityController.canEnableAccessibilityViaGesture(this)) {
            mEnableAccessibilityController =
                    EnableAccessibilityController.getInstance(getApplicationContext());
            mRootView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return mEnableAccessibilityController.onInterceptTouchEvent(event);
                }
            });
        }
        // Since this is a new component, we need to disable here if the user
        // has already been through setup on a previous version.
        try {
            if (Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.USER_SETUP_COMPLETE) == 1) {
                finishSetup();
            }
        } catch (Settings.SettingNotFoundException e) {
            // Continue with setup
        }
        mIsGuestUser =  SetupWizardUtils.isGuestUser(this);
        if (mIsGuestUser) {
            finishSetup();
        }
        registerReceiver(mSetupData, mSetupData.getIntentFilter());
    }

    @Override
    protected void onResume() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        super.onResume();
        onPageTreeChanged();
        enableButtonBar(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSetupData.unregisterListener(this);
        unregisterReceiver(mSetupData);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mSetupData.getCurrentPage().onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mSetupData;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("data", mSetupData.save());
    }

    @Override
    public void onBackPressed() {
        if (!mSetupData.isFirstPage()) {
            mSetupData.onPreviousPage();
        }
    }

    @Override
    public void onNextPage() {
        Page page = mSetupData.getCurrentPage();
        page.doLoadAction(this, Page.ACTION_NEXT);
    }

    @Override
    public void onPreviousPage() {
        Page page = mSetupData.getCurrentPage();
        page.doLoadAction(this, Page.ACTION_PREVIOUS);
    }

    @Override
    public void onPageLoaded(Page page) {
        updateButtonBar();
        enableButtonBar(true);
    }

    @Override
    public void onPageTreeChanged() {
        updateButtonBar();
    }

    private void enableButtonBar(boolean enabled) {
        mNextButton.setEnabled(enabled);
        mPrevButton.setEnabled(enabled);
    }

    private void updateButtonBar() {
        Page page = mSetupData.getCurrentPage();
        mNextButton.setText(page.getNextButtonTitleResId());
        if (page.getPrevButtonTitleResId() != -1) {
            mPrevButton.setText(page.getPrevButtonTitleResId());
        } else {
            mPrevButton.setText("");
        }
        if (mSetupData.isFirstPage()) {
            mPrevButton.setCompoundDrawables(null, null, null, null);
            mPrevButton.setVisibility(SetupWizardUtils.hasTelephony(this) ?
                    View.VISIBLE : View.INVISIBLE);
        } else {
            mPrevButton.setCompoundDrawablesWithIntrinsicBounds(
                    getDrawable(R.drawable.ic_chevron_left_dark),
                    null, null, null);
        }
        final Resources resources = getResources();
        if (mSetupData.isLastPage()) {
            mButtonBar.setBackgroundResource(R.color.primary);
            mNextButton.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    getDrawable(R.drawable.ic_chevron_right_wht), null);
            mNextButton.setTextColor(resources.getColor(R.color.white));
            mPrevButton.setCompoundDrawablesWithIntrinsicBounds(
                    getDrawable(R.drawable.ic_chevron_left_wht), null,
                    null, null);
            mPrevButton.setTextColor(resources.getColor(R.color.white));
        } else {
            mButtonBar.setBackgroundResource(R.color.button_bar_background);
            mNextButton.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    getDrawable(R.drawable.ic_chevron_right_dark), null);
            mNextButton.setTextColor(resources.getColor(R.color.primary_text));
            mPrevButton.setTextColor(resources.getColor(R.color.primary_text));
        }
    }

    @Override
    public Page getPage(String key) {
        return mSetupData.getPage(key);
    }

    @Override
    public Page getPage(int key) {
        return mSetupData.getPage(key);
    }

    @Override
    public void onFinish() {
        animateOut();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void setupRevealImage() {
        Thread t = new Thread() {
            @Override
            public void run() {
                Point p = new Point();
                getWindowManager().getDefaultDisplay().getRealSize(p);
                final Drawable drawable = WallpaperManager.getInstance(SetupWizardActivity.this)
                        .getBuiltInDrawable(
                                p.x, p.y, false, 0, 0);
                if (drawable != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mReveal.setBackground(drawable);
                        }
                    });
                }
            }
        };
        t.start();
    }

    private void animateOut() {
        int cx = (mReveal.getLeft() + mReveal.getRight()) / 2;
        int cy = (mReveal.getTop() + mReveal.getBottom()) / 2;
        int finalRadius = Math.max(mReveal.getWidth(), mReveal.getHeight());
        Animator anim =
                ViewAnimationUtils.createCircularReveal(mReveal, cx, cy, 0, finalRadius);
        anim.setDuration(900);
        anim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mReveal.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        finishSetup();
                    }
                });
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });
        anim.start();
    }

    private void finishSetup() {
        SetupWizardApp setupWizardApp = (SetupWizardApp)getApplication();
        if (!mIsGuestUser) {
            setupWizardApp.sendBroadcastAsUser(new Intent(SetupWizardApp.ACTION_FINISHED),
                    UserHandle.getCallingUserHandle());
        }
        mSetupData.finishPages();
        Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
        Settings.Secure.putInt(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1);
        setupWizardApp.enableStatusBar();
        finish();
        if (mEnableAccessibilityController != null) {
            mEnableAccessibilityController.onDestroy();
        }
        SetupWizardUtils.disableGMSSetupWizard(this);
        SetupWizardUtils.disableSetupWizard(this);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }
}
