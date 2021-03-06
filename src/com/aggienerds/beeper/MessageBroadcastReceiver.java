/*
 * Copyright (C) 2012-2014 William Reading
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

package com.aggienerds.beeper;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class MessageBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "MessageBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // If we aren't on call, don't bother
        SharedPreferences prefs = context.getSharedPreferences("com.aggienerds.beeper_preferences", 0);
        if (prefs.getBoolean("pref_oncall", false)) {
            TelephonyMessageRetriever retriever = new TelephonyMessageRetriever(context, intent);
            Message message = retriever.getMessage();
            if (message != null) {
                if (doesMessageMatch(prefs, message)) {
                    broadcastMessage(context, prefs, message);
                }
            } else {
                Log.d(TAG, "Told we got a message, but couldn't find it.");
            }
        }
    }

    private boolean doesMessageMatch(SharedPreferences prefs, Message message) {
        String matchText = prefs.getString("pref_match", "");

        String comparison = message.getAddress() + " " + message.getSubject()
                + " " + message.getBody();

        if (prefs.getBoolean("pref_regex", false) != true) {
            // Nothing provided. Match everything.
            if (comparison == "") {
                return true;
            }
            // Compare in a case insensitive way
            comparison = comparison.toLowerCase(Locale.getDefault());
            matchText = matchText.toLowerCase(Locale.getDefault());
            if (comparison.contains(matchText)) {
                return true;
            }
        } else {
            // Run it through the regex
            try {
                Pattern pattern = Pattern.compile("^.*" + matchText + ".*$", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(comparison);
                if (matcher.matches()) {
                    return true;
                }
            } catch (PatternSyntaxException e) {
                Log.d(TAG, "Invalid regex in match pattern. Ignoring.");
            }
        }

        return false;
    }

    private void broadcastMessage(Context context,
            SharedPreferences preferences, Message message) {
        Log.d(TAG, "Setting up notification message");

        boolean haveSubject = false;
        if ((message.getSubject() != null)
                && (message.getSubject().length() > 0)) {
            haveSubject = true;
        }

        boolean haveBody = false;
        if ((message.getBody() != null) && (message.getBody().length() > 0)) {
            haveBody = true;
        }

        String subject = "";
        String body = "";

        // Use what we have. If we don't, then set reasonable defaults.
        if (haveSubject && haveBody) {
            subject = message.getSubject();
            body = message.getBody();
        } else if (haveSubject && !haveBody) {
            subject = message.getAddress();
            body = message.getSubject();
        } else if (haveBody && !haveSubject) {
            subject = message.getAddress();
            body = message.getBody();
        } else {
            subject = "Incoming Page";
            body = message.getAddress();
        }

        // Shove the subject and body that we'd like to broadcast into extras
        // and pass off to the notifier service
        Intent myIntent = new Intent(context, NotifierService.class);
        myIntent.putExtra("intentType", "textMessage");
        myIntent.putExtra("textSubject", subject);
        myIntent.putExtra("textBody", body);
        context.startService(myIntent);
    }
}
