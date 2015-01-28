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

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.mokee.setupwizard.R;
import com.mokee.setupwizard.SetupWizardApp;
import com.mokee.setupwizard.util.SetupWizardUtils;

import java.io.IOException;

public class GmsAccountPage extends SetupPage {

    public static final String TAG = "GmsAccountPage";

    public GmsAccountPage(Context context, SetupDataCallbacks callbacks) {
        super(context, callbacks);
    }

    @Override
    public Fragment getFragment(FragmentManager fragmentManager, int action) {
        Fragment fragment = fragmentManager.findFragmentByTag(getKey());
        if (fragment == null) {
            Bundle args = new Bundle();
            args.putString(Page.KEY_PAGE_ARGUMENT, getKey());
            args.putInt(Page.KEY_PAGE_ACTION, action);
            fragment = new LoadingFragment();
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
        return R.string.loading;
    }

    @Override
    public int getNextButtonTitleResId() {
        return R.string.skip;
    }

    @Override
    public void doLoadAction(Activity context, int action) {
        if (action == Page.ACTION_PREVIOUS) {
            getCallbacks().onPreviousPage();
        } else {
            launchGmsAccountSetup(context);
            super.doLoadAction(context, action);
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SetupWizardApp.REQUEST_CODE_SETUP_GMS) {
            if (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_FIRST_USER) {
                if (SetupWizardUtils.accountExists(mContext, SetupWizardApp.ACCOUNT_TYPE_GMS)) {
                    setCompleted(true);
                }
                getCallbacks().onNextPage();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                getCallbacks().onPreviousPage();
            }
        }
        return true;
    }

    public void launchGmsAccountSetup(final Activity activity) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(SetupWizardApp.EXTRA_FIRST_RUN, true);
        bundle.putBoolean(SetupWizardApp.EXTRA_ALLOW_SKIP, true);
        bundle.putBoolean(SetupWizardApp.EXTRA_USE_IMMERSIVE, true);
        AccountManager
                .get(activity).addAccount(SetupWizardApp.ACCOUNT_TYPE_GMS, null, null,
                bundle, null, new AccountManagerCallback<Bundle>() {
                    @Override
                    public void run(AccountManagerFuture<Bundle> future) {
                        try {
                            Bundle result = future.getResult();
                            Intent intent = result
                                    .getParcelable(AccountManager.KEY_INTENT);
                            ActivityOptions options =
                                    ActivityOptions.makeCustomAnimation(activity,
                                            android.R.anim.fade_in,
                                            android.R.anim.fade_out);
                            activity.startActivityForResult(intent,
                                    SetupWizardApp.REQUEST_CODE_SETUP_GMS, options.toBundle());
                        } catch (OperationCanceledException e) {
                        } catch (IOException e) {
                        } catch (AuthenticatorException e) {
                        }
                    }
                }, null);
    }
}
