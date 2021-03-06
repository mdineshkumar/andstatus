/* 
 * Copyright (C) 2008 Torgny Bjers
 * Copyright (C) 2010 Brion N. Emde, "BLOA" example, http://github.com/brione/Brion-Learns-OAuth 
 * Copyright (C) 2010-2011 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.net.SocketTimeoutException;
import java.text.MessageFormat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;


import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import org.andstatus.app.TwitterUser.CredentialsVerified;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.net.ConnectionAuthenticationException;
import org.andstatus.app.net.ConnectionCredentialsOfOtherUserException;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionOAuth;
import org.andstatus.app.net.ConnectionUnavailableException;
import org.andstatus.app.net.OAuthKeys;
import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Application settings
 * 
 * @author torgny.bjers
 */
public class PreferencesActivity extends PreferenceActivity implements
        OnSharedPreferenceChangeListener, OnPreferenceChangeListener {

    private static final String TAG = PreferencesActivity.class.getSimpleName();

    public static final String INTENT_RESULT_KEY_AUTHENTICATION = "authentication";

    /** 
     * The URI is consistent with "scheme" and "host" in AndroidManifest
     */
    public static final Uri CALLBACK_URI = Uri.parse("andstatus-oauth://andstatus.org");

    /**
     * This is single list of (in fact, enums...) of Message/Dialog IDs
     */
    public static final int MSG_NONE = 7;

    public static final int MSG_ACCOUNT_VALID = 1;

    public static final int MSG_ACCOUNT_INVALID = 2;

    public static final int MSG_SERVICE_UNAVAILABLE_ERROR = 3;

    public static final int MSG_CONNECTION_EXCEPTION = 4;

    public static final int MSG_SOCKET_TIMEOUT_EXCEPTION = 5;

    public static final int MSG_CREDENTIALS_OF_OTHER_USER = 6;

    // End Of the list ----------------------------------------

    // private CheckBoxPreference mUseExternalStorage;

    private CheckBoxPreference mOAuth;

    private EditTextPreference mEditTextUsername;

    private EditTextPreference mEditTextPassword;

    private Preference mVerifyCredentials;

    private RingtonePreference mNotificationRingtone;

    private boolean onSharedPreferenceChanged_busy = false;
    
    /**
     * Use this flag to return from this activity to the TweetListAcivity
     */
    private boolean overrideBackButton = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        // Default values for the preferences will be set only once
        // and in one place: here
        MyPreferences.setDefaultValues(R.xml.preferences, false);
        if (!MyPreferences.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, MODE_PRIVATE).getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
          Log.e(TAG, "Default values were not set?!");   
        }
        
        mNotificationRingtone = (RingtonePreference) findPreference(MyPreferences.KEY_RINGTONE_PREFERENCE);
        mOAuth = (CheckBoxPreference) findPreference(MyPreferences.KEY_OAUTH);
        mEditTextUsername = (EditTextPreference) findPreference(MyPreferences.KEY_TWITTER_USERNAME_NEW);
        mEditTextPassword = (EditTextPreference) findPreference(MyPreferences.KEY_TWITTER_PASSWORD);
        mVerifyCredentials = (Preference) findPreference(MyPreferences.KEY_VERIFY_CREDENTIALS);

        mNotificationRingtone.setOnPreferenceChangeListener(this);

        /*
         * mUseExternalStorage = (CheckBoxPreference)
         * getPreferenceScreen().findPreference(KEY_EXTERNAL_STORAGE); if
         * (!Environment
         * .getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
         * mUseExternalStorage.setEnabled(false);
         * mUseExternalStorage.setChecked(false); }
         */
    }

    /**
     * Some "preferences" may be changed in TwitterUser object
     */
    private void showUserPreferences(TwitterUser tuIn) {
        TwitterUser tu = tuIn;
        if(tu == null) {
            tu = TwitterUser.getTwitterUser();
        }
        if (mEditTextUsername.getText() == null
                || tu.getUsername().compareTo(mEditTextUsername.getText()) != 0) {
            mEditTextUsername.setText(tu.getUsername());
        }
        StringBuilder sb = new StringBuilder(this.getText(R.string.summary_preference_username));
        if (tu.getUsername().length() > 0) {
            sb.append(": " + tu.getUsername());
        } else {
            sb.append(": (" + this.getText(R.string.not_set) + ")");
        }
        mEditTextUsername.setSummary(sb);

        // Disable Username if we have no user accounts yet
        mEditTextUsername.setEnabled(TwitterUser.countOfAuthenticatedUsers() > 0);

        if (tu.isOAuth() != mOAuth.isChecked()) {
            mOAuth.setChecked(tu.isOAuth());
        }
        // In fact, we should hide it if not enabled, but I couldn't find an easy way for this...
        mOAuth.setEnabled(tu.canChangeOAuth());

        if (mEditTextPassword.getText() == null
                || tu.getPassword().compareTo(mEditTextPassword.getText()) != 0) {
            mEditTextPassword.setText(tu.getPassword());
        }
        sb = new StringBuilder(this.getText(R.string.summary_preference_password));
        if (tu.getPassword().length() == 0) {
            sb.append(": (" + this.getText(R.string.not_set) + ")");
        }
        mEditTextPassword.setSummary(sb);
        mEditTextPassword.setEnabled(tu.getConnection().isPasswordNeeded());

        int titleResId;
        switch (tu.getCredentialsVerified()) {
            case SUCCEEDED:
                titleResId = R.string.title_preference_verify_credentials;
                sb = new StringBuilder(
                        this.getText((org.andstatus.app.util.Build.VERSION.SDK_INT >= 8) ? R.string.summary_preference_verify_credentials
                                : R.string.summary_preference_verify_credentials_2lines));
                break;
            default:
                if (tu.isTemporal()) {
                    titleResId = R.string.title_preference_verify_credentials_add;
                    sb = new StringBuilder(
                            this.getText(R.string.summary_preference_verify_credentials_add));
                } else {
                    titleResId = R.string.title_preference_verify_credentials_failed;
                    sb = new StringBuilder(
                            this.getText(R.string.summary_preference_verify_credentials_failed));
                }
                break;
        }
        mVerifyCredentials.setTitle(titleResId);

        mVerifyCredentials.setSummary(sb);
        mVerifyCredentials.setEnabled(tu.getCredentialsPresent()
                || tu.isOAuth());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Stop service to force preferences reload on the next start
        // Plus disable repeating alarms for awhile (till next start service...)
        MyServiceManager.stopAndStatusService(this, true);
        
        showAllPreferences();
        MyPreferences.getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        
        Uri uri = getIntent().getData();
        if (uri != null) {
            if (MyLog.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "uri=" + uri.toString());
            }
            if (CALLBACK_URI.getScheme().equals(uri.getScheme())) {
                // To prevent repeating of this task
                getIntent().setData(null);
                // This activity was started by Twitter ("Service Provider")
                // so start second step of OAuth Authentication process
                new OAuthAcquireAccessTokenTask().execute(uri);
                // and return back to default screen
                overrideBackButton = true;
            }
        }
    }

    /**
     * Verify credentials
     * 
     * @param true - Verify only if we didn't do this yet
     */
    private void verifyCredentials(boolean reVerify) {
        TwitterUser tu = TwitterUser.getTwitterUser();
        if (reVerify || tu.getCredentialsVerified() == CredentialsVerified.NEVER) {
            if (tu.getCredentialsPresent()) {
                // Credentials are present, so we may verify them
                // This is needed even for OAuth - to know Twitter Username
                new VerifyCredentialsTask().execute();
            } else {
                if (tu.isOAuth() && reVerify) {
                    // Credentials are not present,
                    // so start asynchronous OAuth Authentication process 
                    new OAuthAcquireRequestTokenTask().execute();
                }
            }

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyPreferences.getDefaultSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
    }

    /**
     * Show values of all preferences in the "summaries".
     * @see <a href="http://stackoverflow.com/questions/531427/how-do-i-display-the-current-value-of-an-android-preference-in-the-preference-sum"> 
       How do I display the current value of an Android Preference 
       in the Preference summary?</a>
     */
    protected void showAllPreferences() {
        showUserPreferences(null);
        showFrequency();
        showHistorySize();
        showHistoryTime();
        showRingtone(MyPreferences.getDefaultSharedPreferences().getString(
                MyPreferences.KEY_RINGTONE_PREFERENCE, null));
        showMinLogLevel();
    }
    
    protected void showHistorySize() {
        showListPreference(MyPreferences.KEY_HISTORY_SIZE, R.array.history_size_keys, R.array.history_size_display, R.string.summary_preference_history_size);
    }

    protected void showHistoryTime() {
        showListPreference(MyPreferences.KEY_HISTORY_TIME, R.array.history_time_keys, R.array.history_time_display, R.string.summary_preference_history_time);
    }

    protected void showFrequency() {
        showListPreference(MyPreferences.KEY_FETCH_FREQUENCY, R.array.fetch_frequency_keys, R.array.fetch_frequency_display, R.string.summary_preference_frequency);
    }

    protected void showMinLogLevel() {
        showListPreference(MyPreferences.KEY_MIN_LOG_LEVEL, R.array.log_level_keys, R.array.log_level_display, R.string.summary_preference_min_log_level);
    }

    protected void showListPreference(String keyPreference, int keysR, int displayR, int summaryR) {
        String displayParm = "";
        ListPreference lP = (ListPreference) findPreference(keyPreference);
        if (lP != null) {
            String[] k = getResources().getStringArray(keysR);
            String[] d = getResources().getStringArray(displayR);
            displayParm = d[0];
            String listValue = lP.getValue();
            for (int i = 0; i < k.length; i++) {
                if (listValue.equals(k[i])) {
                    displayParm = d[i];
                    break;
                }
            }
        } else {
            displayParm = keyPreference + " was not found";
        }
        MessageFormat sf = new MessageFormat(getText(summaryR)
                .toString());
        lP.setSummary(sf.format(new Object[] {
            displayParm
        }));
    }
    
    protected void showRingtone(Object newValue) {
        String ringtone = (String) newValue;
        Uri uri;
        Ringtone rt;
        if (ringtone == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else if ("".equals(ringtone)) {
            mNotificationRingtone.setSummary(R.string.summary_preference_no_ringtone);
        } else {
            uri = Uri.parse(ringtone);
            rt = RingtoneManager.getRingtone(this, uri);
            mNotificationRingtone.setSummary(rt.getTitle(this));
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PreferencesActivity.this.mCredentialsAreBeingVerified) {
            return;
        }
        if (onSharedPreferenceChanged_busy) {
            return;
        }
        onSharedPreferenceChanged_busy = true;

        try {
            String value = "(not set)";
            if (sharedPreferences.contains(key)) {
                try {
                    value = sharedPreferences.getString(key, "");
                } catch (ClassCastException e) {
                    value = "(not string)";
                }
            }
            MyLog.d(TAG, "onSharedPreferenceChanged: " + key + "='" + value + "'");

            // Remember when last changes were made
            sharedPreferences
                    .edit()
                    .putLong(MyPreferences.KEY_PREFERENCES_CHANGE_TIME,
                            java.lang.System.currentTimeMillis()).commit();
            
            TwitterUser tu = TwitterUser.getTwitterUser();
            String usernameNew = sharedPreferences.getString(MyPreferences.KEY_TWITTER_USERNAME_NEW, "");
            
            if (key.equals(MyPreferences.KEY_OAUTH)) {
                // Here and below:
                // Check if there are changes to avoid "ripples"
                if (tu.isOAuth() != mOAuth.isChecked()) {
                    tu = TwitterUser.getAddEditTwitterUser(usernameNew);
                    tu.setCurrentUser();
                    showUserPreferences(tu);
                }
            }
            if (key.equals(MyPreferences.KEY_TWITTER_USERNAME_NEW)) {
                String usernameOld = sharedPreferences.getString(MyPreferences.KEY_TWITTER_USERNAME, "");
                if (usernameNew.compareTo(usernameOld) != 0) {
                    // Try to find existing TwitterUser by the new Username 
                    // without clearing Auth information
                    tu = TwitterUser.getTwitterUser(usernameNew);
                    tu.setCurrentUser();
                    showUserPreferences(tu);
                }
            }
            if (key.equals(MyPreferences.KEY_TWITTER_PASSWORD)) {
                if (tu.getPassword().compareTo(mEditTextPassword.getText()) != 0) {
                    tu = TwitterUser.getAddEditTwitterUser(usernameNew);
                    tu.setCurrentUser();
                    showUserPreferences(tu);
                }
            }
            if (key.equals(MyPreferences.KEY_FETCH_FREQUENCY)) {
                showFrequency();
            }
            if (key.equals(MyPreferences.KEY_RINGTONE_PREFERENCE)) {
                // TODO: Try to move it here from onPreferenceChange...
                // updateRingtone();
            }
            if (key.equals(MyPreferences.KEY_HISTORY_SIZE)) {
                showHistorySize();
            }
            if (key.equals(MyPreferences.KEY_HISTORY_TIME)) {
                showHistoryTime();
            }
            if (key.equals(MyPreferences.KEY_MIN_LOG_LEVEL)) {
                showMinLogLevel();
            }
        } finally {
            onSharedPreferenceChanged_busy = false;
        }
    };

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(MyPreferences.KEY_RINGTONE_PREFERENCE)) {
            showRingtone(newValue);
            return true;
        }
        return false;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        int titleId = 0;
        int summaryId = 0;

        switch (id) {
            case MSG_ACCOUNT_INVALID:
                if (titleId == 0) {
                    titleId = R.string.dialog_title_authentication_failed;
                    summaryId = R.string.dialog_summary_authentication_failed;
                }
            case MSG_SERVICE_UNAVAILABLE_ERROR:
                if (titleId == 0) {
                    titleId = R.string.dialog_title_service_unavailable;
                    summaryId = R.string.dialog_summary_service_unavailable;
                }
            case MSG_SOCKET_TIMEOUT_EXCEPTION:
                if (titleId == 0) {
                    titleId = R.string.dialog_title_connection_timeout;
                    summaryId = R.string.dialog_summary_connection_timeout;
                }
            case MSG_CREDENTIALS_OF_OTHER_USER:
                if (titleId == 0) {
                    titleId = R.string.dialog_title_authentication_failed;
                    summaryId = R.string.error_credentials_of_other_user;
                }
                return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(titleId).setMessage(summaryId).setPositiveButton(
                                android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface Dialog, int whichButton) {
                                    }
                                }).create();

            default:
                return super.onCreateDialog(id);
        }
    }

    /**
     * This semaphore helps to avoid ripple effect: changes in TwitterUser cause
     * changes in this activity ...
     */
    private boolean mCredentialsAreBeingVerified = false;

    /*
     * (non-Javadoc)
     * @seeandroid.preference.PreferenceActivity#onPreferenceTreeClick(android.
     * preference.PreferenceScreen, android.preference.Preference)
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        MyLog.d(TAG, "Preference clicked:" + preference.toString());
        if (preference.getKey().compareTo(MyPreferences.KEY_VERIFY_CREDENTIALS) == 0) {
            verifyCredentials(true);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    };
   
    /**
     * Assuming we already have credentials to verify, verify them
     * @author yvolk
     *
     */
    private class VerifyCredentialsTask extends AsyncTask<Void, Void, JSONObject> {
        private ProgressDialog dlg;
        private boolean skip = false;

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(PreferencesActivity.this,
                    getText(R.string.dialog_title_checking_credentials),
                    getText(R.string.dialog_summary_checking_credentials), true, // indeterminate
                    // duration
                    false); // not cancel-able

            if (PreferencesActivity.this.mCredentialsAreBeingVerified) {
                skip = true;
            } else {
                PreferencesActivity.this.mCredentialsAreBeingVerified = true;
            }
        }

        @Override
        protected JSONObject doInBackground(Void... arg0) {
            JSONObject jso = null;

            int what = MSG_NONE;
            String message = "";
            
            if (!skip) {
                what = MSG_ACCOUNT_INVALID;
                String usernameUI = "";
                try {
                    usernameUI = MyPreferences
                    .getDefaultSharedPreferences().getString(MyPreferences.KEY_TWITTER_USERNAME_NEW, "");
                    TwitterUser tu  = TwitterUser.getTwitterUser();
                    if (tu.verifyCredentials(true)) {
                        what = MSG_ACCOUNT_VALID;
                        tu.setCurrentUser();
                        // Maybe after successful Verification we should change Current User?
                        if (tu.getUsername().compareTo(usernameUI) !=0) {
                            MyLog.v(TAG, "Changing Username for UI from '" + usernameUI 
                                    + "' to '" + tu.getUsername() + "'");
                            MyPreferences
                            .getDefaultSharedPreferences().edit().putString(MyPreferences.KEY_TWITTER_USERNAME_NEW, tu.getUsername()).commit();
                        }
                    }
                } catch (ConnectionException e) {
                    what = MSG_CONNECTION_EXCEPTION;
                    message = e.toString();
                } catch (ConnectionAuthenticationException e) {
                    what = MSG_ACCOUNT_INVALID;
                } catch (ConnectionCredentialsOfOtherUserException e) {
                    what = MSG_CREDENTIALS_OF_OTHER_USER;
                    // Switch to this user
                    // New User Name was stored in the Message of the Exception
                    TwitterUser tu  = TwitterUser.getTwitterUser(e.getMessage());
                    if (tu.getCredentialsVerified() != CredentialsVerified.SUCCEEDED) {
                        tu.setCredentialsVerified(CredentialsVerified.SUCCEEDED);
                    }
                    what = MSG_ACCOUNT_VALID;

                    tu.setCurrentUser();
                    MyLog.v(TAG, "Changing Username for UI from '" + usernameUI 
                            + "' to '" + tu.getUsername() + "'");
                    MyPreferences
                    .getDefaultSharedPreferences().edit().putString(MyPreferences.KEY_TWITTER_USERNAME_NEW, tu.getUsername()).commit();
                    
                } catch (ConnectionUnavailableException e) {
                    what = MSG_SERVICE_UNAVAILABLE_ERROR;
                } catch (SocketTimeoutException e) {
                    what = MSG_SOCKET_TIMEOUT_EXCEPTION;
                }
            }

            try {
                jso = new JSONObject();
                jso.put("what", what);
                jso.put("message", message);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return jso;
        }

        /**
         * Credentials were verified just now!
         * This is in the UI thread, so we can mess with the UI
         */
        protected void onPostExecute(JSONObject jso) {
            try {
                dlg.dismiss();
            } catch (Exception e1) { 
                // Ignore this error  
            }
            boolean succeeded = false;
            if (jso != null) {
                try {
                    int what = jso.getInt("what");
                    String message = jso.getString("message");

                    switch (what) {
                        case MSG_ACCOUNT_VALID:
                            Toast.makeText(PreferencesActivity.this, R.string.authentication_successful,
                                    Toast.LENGTH_SHORT).show();
                            succeeded = true;
                            break;
                        case MSG_ACCOUNT_INVALID:
                        case MSG_SERVICE_UNAVAILABLE_ERROR:
                        case MSG_SOCKET_TIMEOUT_EXCEPTION:
                        case MSG_CREDENTIALS_OF_OTHER_USER:
                            showDialog(what);
                            break;
                        case MSG_CONNECTION_EXCEPTION:
                            int mId = 0;
                            try {
                                mId = Integer.parseInt(message);
                            } catch (Exception e) {
                            }
                            switch (mId) {
                                case 404:
                                    mId = R.string.error_twitter_404;
                                    break;
                                default:
                                    mId = R.string.error_connection_error;
                                    break;
                            }
                            Toast.makeText(PreferencesActivity.this, mId, Toast.LENGTH_LONG).show();
                            break;

                    }
                    showUserPreferences(null);
                } catch (JSONException e) {
                    // Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (!skip) {
                if (succeeded) {
                    TwitterUser.getTwitterUser().setCredentialsVerified(CredentialsVerified.SUCCEEDED);
                } else {
                    TwitterUser.getTwitterUser().setCredentialsVerified(CredentialsVerified.FAILED);
                }
                PreferencesActivity.this.mCredentialsAreBeingVerified = false;
            }
        }
    }
    
    /**
     * Task 1 of 2 required for OAuth Authentication.
     * See http://www.snipe.net/2009/07/writing-your-first-twitter-application-with-oauth/
     * for good OAuth Authentication flow explanation.
     *  
     * During this task:
     * 1. AndStatus ("Consumer") Requests "Request Token" from Twitter ("Service provider"), 
     * 2. Waits that Request Token
     * 3. Consumer directs User to Service Provider: opens Twitter site in Internet Browser window
     *    in order to Obtain User Authorization.
     * 4. This task ends.
     * 
     * What will occur later:
     * 5. After User Authorized AndStatus in the Internet Browser,
     *    Twitter site will redirect User back to
     *    AndStatus and then the second OAuth task, , will start.
     *   
     * @author yvolk. This code is based on "BLOA" example,
     *         http://github.com/brione/Brion-Learns-OAuth yvolk: I had to move
     *         this code from OAuthActivity here in order to be able to show
     *         ProgressDialog and to get rid of any "Black blank screens"
     */
    private class OAuthAcquireRequestTokenTask extends AsyncTask<Void, Void, JSONObject> {
        private OAuthConsumer mConsumer = null;

        private OAuthProvider mProvider = null;

        private ProgressDialog dlg;

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(PreferencesActivity.this,
                    getText(R.string.dialog_title_acquiring_a_request_token),
                    getText(R.string.dialog_summary_acquiring_a_request_token), true, // indeterminate
                    // duration
                    false); // not cancel-able
        }

        @Override
        protected JSONObject doInBackground(Void... arg0) {
            JSONObject jso = null;

            // We don't need to worry about any saved states: we can reconstruct
            // the
            // state
            mConsumer = new CommonsHttpOAuthConsumer(OAuthKeys.TWITTER_CONSUMER_KEY,
                    OAuthKeys.TWITTER_CONSUMER_SECRET);

            mProvider = new CommonsHttpOAuthProvider(ConnectionOAuth.TWITTER_REQUEST_TOKEN_URL,
                    ConnectionOAuth.TWITTER_ACCESS_TOKEN_URL, ConnectionOAuth.TWITTER_AUTHORIZE_URL);

            // It turns out this was the missing thing to making standard
            // Activity
            // launch mode work
            mProvider.setOAuth10a(true);

            boolean requestSucceeded = false;
            String message = "";
            String message2 = "";
            try {
                TwitterUser tu = TwitterUser.getTwitterUser();

                // This is really important. If you were able to register your
                // real callback Uri with Twitter, and not some fake Uri
                // like I registered when I wrote this example, you need to send
                // null as the callback Uri in this function call. Then
                // Twitter will correctly process your callback redirection
                String authUrl = mProvider.retrieveRequestToken(mConsumer,
                        CALLBACK_URI.toString());
                saveRequestInformation(tu.getSharedPreferences(), mConsumer.getToken(), mConsumer.getTokenSecret());

                // Start Internet Browser
                PreferencesActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri
                        .parse(authUrl)));

                requestSucceeded = true;
            } catch (OAuthMessageSignerException e) {
                message = e.getMessage();
                e.printStackTrace();
            } catch (OAuthNotAuthorizedException e) {
                message = e.getMessage();
                e.printStackTrace();
            } catch (OAuthExpectationFailedException e) {
                message = e.getMessage();
                e.printStackTrace();
            } catch (OAuthCommunicationException e) {
                message = e.getMessage();
                e.printStackTrace();
            }

            try {
                // mSp.edit().putBoolean(ConnectionOAuth.REQUEST_SUCCEEDED,
                // requestSucceeded).commit();
                if (!requestSucceeded) {
                    message2 = PreferencesActivity.this
                            .getString(R.string.dialog_title_authentication_failed);
                    if (message != null && message.length() > 0) {
                        message2 = message2 + ": " + message;
                    }
                    MyLog.d(TAG, message2);
                }

                // This also works sometimes, but message2 may have quotes...
                // String jss = "{\n\"succeeded\": \"" + requestSucceeded
                // + "\",\n\"message\": \"" + message2 + "\"}";
                // jso = new JSONObject(jss);

                jso = new JSONObject();
                jso.put("succeeded", requestSucceeded);
                jso.put("message", message2);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return jso;
        }

        // This is in the UI thread, so we can mess with the UI
        protected void onPostExecute(JSONObject jso) {
            try {
                dlg.dismiss();
            } catch (Exception e1) { 
                // Ignore this error  
            }
            if (jso != null) {
                try {
                    boolean succeeded = jso.getBoolean("succeeded");
                    String message = jso.getString("message");

                    if (succeeded) {
                        // This may be necessary in order to start properly 
                        // after redirection from Twitter
                        // Because of initializations in onCreate...
                        PreferencesActivity.this.finish();
                    } else {
                        Toast.makeText(PreferencesActivity.this, message, Toast.LENGTH_LONG).show();

                        TwitterUser tu = TwitterUser.getTwitterUser();
                        tu.clearAuthInformation();
                        tu.setCredentialsVerified(CredentialsVerified.FAILED);
                        tu.setCurrentUser();
                        showUserPreferences(tu);
                    }
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Task 2 of 2 required for OAuth Authentication.
     *  
     * During this task:
     * 1. AndStatus ("Consumer") exchanges "Request Token", 
     *    obtained earlier from Twitter ("Service provider"),
     *    for "Access Token". 
     * 2. Stores the Access token for all future interactions with Twitter.
     * 
     * @author yvolk. This code is based on "BLOA" example,
     *         http://github.com/brione/Brion-Learns-OAuth yvolk: I had to move
     *         this code from OAuthActivity here in order to be able to show
     *         ProgressDialog and to get rid of any "Black blank screens"
     */
    private class OAuthAcquireAccessTokenTask extends AsyncTask<Uri, Void, JSONObject> {
        private OAuthConsumer mConsumer = null;

        private OAuthProvider mProvider = null;

        private ProgressDialog dlg;

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(PreferencesActivity.this,
                    getText(R.string.dialog_title_acquiring_an_access_token),
                    getText(R.string.dialog_summary_acquiring_an_access_token), true, // indeterminate
                    // duration
                    false); // not cancel-able
        }

        @Override
        protected JSONObject doInBackground(Uri... uris) {
            JSONObject jso = null;

            // We don't need to worry about any saved states: we can reconstruct
            // the state
            mConsumer = new CommonsHttpOAuthConsumer(OAuthKeys.TWITTER_CONSUMER_KEY,
                    OAuthKeys.TWITTER_CONSUMER_SECRET);

            mProvider = new CommonsHttpOAuthProvider(ConnectionOAuth.TWITTER_REQUEST_TOKEN_URL,
                    ConnectionOAuth.TWITTER_ACCESS_TOKEN_URL, ConnectionOAuth.TWITTER_AUTHORIZE_URL);

            // It turns out this was the missing thing to making standard
            // Activity launch mode work
            mProvider.setOAuth10a(true);

            String message = "";
            
            boolean authenticated = false;
            TwitterUser tu = TwitterUser.getTwitterUser();

            Uri uri = uris[0];
            if (uri != null && CALLBACK_URI.getScheme().equals(uri.getScheme())) {
                String token = tu.getSharedPreferences().getString(ConnectionOAuth.REQUEST_TOKEN, null);
                String secret = tu.getSharedPreferences().getString(ConnectionOAuth.REQUEST_SECRET, null);

                tu.clearAuthInformation();
                if (!tu.isOAuth()) {
                    Log.e(TAG, "Connection is not of OAuth type ???");
                } else {
                    try {
                        // Clear the request stuff, we've used it already
                        saveRequestInformation(tu.getSharedPreferences(), null, null);

                        if (!(token == null || secret == null)) {
                            mConsumer.setTokenWithSecret(token, secret);
                        }
                        String otoken = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
                        String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);

                        /*
                         * yvolk 2010-07-08: It appeared that this may be not true:
                         * Assert.assertEquals(otoken, mConsumer.getToken()); (e.g.
                         * if User denied access during OAuth...) hence this is not
                         * Assert :-)
                         */
                        if (otoken != null || mConsumer.getToken() != null) {
                            // We send out and save the request token, but the
                            // secret is not the same as the verifier
                            // Apparently, the verifier is decoded to get the
                            // secret, which is then compared - crafty
                            // This is a sanity check which should never fail -
                            // hence the assertion
                            // Assert.assertEquals(otoken,
                            // mConsumer.getToken());

                            // This is the moment of truth - we could throw here
                            mProvider.retrieveAccessToken(mConsumer, verifier);
                            // Now we can retrieve the goodies
                            token = mConsumer.getToken();
                            secret = mConsumer.getTokenSecret();
                            authenticated = true;
                        }
                    } catch (OAuthMessageSignerException e) {
                        message = e.getMessage();
                        e.printStackTrace();
                    } catch (OAuthNotAuthorizedException e) {
                        message = e.getMessage();
                        e.printStackTrace();
                    } catch (OAuthExpectationFailedException e) {
                        message = e.getMessage();
                        e.printStackTrace();
                    } catch (OAuthCommunicationException e) {
                        message = e.getMessage();
                        e.printStackTrace();
                    } finally {
                        if (authenticated) {
                            tu.saveAuthInformation(token, secret);
                        }
                    }
                }
            }

            try {
                jso = new JSONObject();
                jso.put("succeeded", authenticated);
                jso.put("message", message);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return jso;
        }

        // This is in the UI thread, so we can mess with the UI
        protected void onPostExecute(JSONObject jso) {
            try {
                dlg.dismiss();
            } catch (Exception e1) { 
                // Ignore this error  
            }
            if (jso != null) {
                try {
                    boolean succeeded = jso.getBoolean("succeeded");
                    String message = jso.getString("message");

                    MyLog.d(TAG, this.getClass().getName() + " ended, "
                            + (succeeded ? "authenticated" : "authentication failed"));
                    
                    if (succeeded) {
                        // Credentials are present, so we may verify them
                        // This is needed even for OAuth - to know Twitter Username
                        new VerifyCredentialsTask().execute();

                    } else {
                        String message2 = PreferencesActivity.this
                        .getString(R.string.dialog_title_authentication_failed);
                        if (message != null && message.length() > 0) {
                            message2 = message2 + ": " + message;
                            Log.d(TAG, message);
                        }
                        Toast.makeText(PreferencesActivity.this, message2, Toast.LENGTH_LONG).show();

                        TwitterUser tu = TwitterUser.getTwitterUser();
                        tu.clearAuthInformation();
                        tu.setCredentialsVerified(CredentialsVerified.FAILED);
                        tu.setCurrentUser();
                        showUserPreferences(tu);
                    }
                    
                    // Now we can return to the PreferencesActivity
                    // We need new Intent in order to forget that URI from OAuth Service Provider
                    //Intent intent = new Intent(PreferencesActivity.this, PreferencesActivity.class);
                    //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    //startActivity(intent);
                    
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    public static void saveRequestInformation(SharedPreferences settings, String token,
            String secret) {
        // null means to clear the old values
        SharedPreferences.Editor editor = settings.edit();
        if (token == null) {
            editor.remove(ConnectionOAuth.REQUEST_TOKEN);
            MyLog.d(TAG, "Clearing Request Token");
        } else {
            editor.putString(ConnectionOAuth.REQUEST_TOKEN, token);
            MyLog.d(TAG, "Saving Request Token: " + token);
        }
        if (secret == null) {
            editor.remove(ConnectionOAuth.REQUEST_SECRET);
            MyLog.d(TAG, "Clearing Request Secret");
        } else {
            editor.putString(ConnectionOAuth.REQUEST_SECRET, secret);
            MyLog.d(TAG, "Saving Request Secret: " + secret);
        }
        editor.commit();

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0
                && overrideBackButton) {
            finish();
            this.sendBroadcast(new Intent(this, TweetListActivity.class));
            return true;    
        }        
        // TODO Auto-generated method stub
        return super.onKeyDown(keyCode, event);
    }
    
}
