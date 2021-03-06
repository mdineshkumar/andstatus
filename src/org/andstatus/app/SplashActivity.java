/* 
 * Copyright (C) 2008 Torgny Bjers
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

package org.andstatus.app;

import org.andstatus.app.TwitterUser.CredentialsVerified;

import java.text.MessageFormat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author torgny.bjers
 *
 */
public class SplashActivity extends Activity {

	public static final String TAG = "SplashActivity";

	private LinearLayout mContainer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.splash);

		/*
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			if (!mSP.getBoolean("confirmed_external_storage_use", false)) {
				showDialog(TimelineActivity.DIALOG_EXTERNAL_STORAGE);
			}
		}
		*/

		mContainer = (LinearLayout) findViewById(R.id.splash_container);

		TextView payoff = (TextView) findViewById(R.id.splash_payoff_line);
		TextView version = (TextView) findViewById(R.id.splash_application_version);

		try {
			PackageManager pm = getPackageManager();
			PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
			version.setText(MessageFormat.format("{0} {1}", new Object[] {pi.packageName, pi.versionName}));
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Unable to obtain package information", e);
		}

		payoff.setText(R.string.splash_payoff_line);

		Button getstarted = (Button) findViewById(R.id.button_splash_get_started);
		getstarted.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startActivity(new Intent(SplashActivity.this, PreferencesActivity.class));
			}
		});

		Button learn_more = (Button) findViewById(R.id.button_splash_learn_more);
		learn_more.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				AlphaAnimation anim = (AlphaAnimation) AnimationUtils.loadAnimation(SplashActivity.this, R.anim.fade_out);
				anim.setAnimationListener(new AnimationListener() {
					public void onAnimationEnd(Animation animation) {
						mContainer.setVisibility(View.INVISIBLE);
						startActivity(new Intent(SplashActivity.this, SplashMoreActivity.class));
					}
					public void onAnimationRepeat(Animation animation) {}
					public void onAnimationStart(Animation animation) {}
				});
				mContainer.startAnimation(anim);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
        if (!TwitterUser.getTwitterUser().isTemporal()) {
			Intent intent = new Intent(this, TweetListActivity.class);
			startActivity(intent);
			finish();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		mContainer.setVisibility(View.VISIBLE);
	}
}
