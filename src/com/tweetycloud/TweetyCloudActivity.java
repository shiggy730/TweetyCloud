package com.tweetycloud;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import winterwell.jtwitter.OAuthSignpostClient;
import winterwell.jtwitter.Status;
import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.TwitterList;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

/*
 Copyright (c) 2012 Shigeru Sasao

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 The Software shall be used for Good, not Evil.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */

/**
 * This is the main Activity class for the TweetyCloud application. This class
 * is responsible for authentication with Twitter, as well as providing the
 * Twitter list of the authenticated user and retrieving the tweets from the
 * selected list.
 * 
 * @author Shigeru Sasao
 * 
 */
public class TweetyCloudActivity extends ListActivity {

	/**
	 * Strings to hold TweetyCloud's OAuth keys Registered at
	 * http://dev.twitter.com Ensure the callback URL is the same as the
	 * Callback URL registered in the website.
	 */
	public static final String MY_TWITTER_KEY = ""; // ENTER KEY HERE
	public static final String MY_TWITTER_SECRET = ""; // ENTER SECRET HERE
	public static final String MY_TWITTER_CALLBACK = ""; // ENTER CALLBACK HERE

	/**
	 * Messages from non-main threads to be captured by the handler
	 */
	private static final int HANDLER_MESSAGE_LIST_CREATED = 0;
	private static final int HANDLER_MESSAGE_TOKENS = 1;
	private static final int HANDLER_MESSAGE_COMPLETED = 2;
	private static final int HANDLER_MESSAGE_ERROR = 3;
	private static final int HANDLER_MESSAGE_STOP_THREAD = 4;

	/** File name for application preferences */
	public static final String PREFS_NAME = "TwitterCloudPrefsFile";

	/**
	 * Preference keys for user's oAuth tokens
	 */
	public static final String USER_TOKEN = "user_token";
	public static final String USER_TOKEN_SECRET = "user_token_secret";

	/** Number of most recent tweets to retrieve */
	private static final int PER_PAGE = 200;

	/** Reference to self */
	private TweetyCloudActivity self;

	/** jTwitter library instance */
	private Twitter jtwitter;

	/** Application preference settings */
	private SharedPreferences settings;

	/** oAuth client */
	private OAuthSignpostClient client;

	/**
	 * oAuth verifier. Need to be a member variable to access from non-main
	 * thread
	 */
	String verifier;

	/** User tokens obtained after successful authentication */
	String[] tokens;

	/** Holds user's Twitter lists */
	private List<TwitterList> myList;

	/** Adapter for the Twitter list */
	private ListAdapter myListAdapter;

	/** Data access object for retrieved tweets */
	private TweetDataSource dao;

	/** Twitter list selected by the user */
	private TwitterList selectedList;

	/** Handles messages coming from non-main threads */
	private Handler threadHandler;

	/** Progress dialog that shows after user selects a list */
	private ProgressDialog progDialog;

	/** Signals that the user requested to cancel the tweet retrieval */
	public boolean stopThread;

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		this.self = this;

		// Initialize handler to handle messages from non-main threads
		threadHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {

				// The Twitter list is ready to be shown to user
				case HANDLER_MESSAGE_LIST_CREATED:
					self.setListAdapter(myListAdapter);
					break;

				// Authorization succeeded and tokens have been retrieved
				case HANDLER_MESSAGE_TOKENS:
					onSuccess();
					break;

				// Tweets have been retrieved and saved to database
				case HANDLER_MESSAGE_COMPLETED:
					progDialog.dismiss();
					self.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
					break;

				// User requested to cancel the tweet retrieval
				case HANDLER_MESSAGE_STOP_THREAD:
					progDialog.dismiss();
					self.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
					stopThread = false;
					break;

				// Error occured while trying to retrieve tweets
				case HANDLER_MESSAGE_ERROR:
					progDialog.dismiss();
					Toast.makeText(self, "Something went wrong! Try again.",
							Toast.LENGTH_LONG).show();
					break;

				default:
					Log.w("TweetyCloud", "Warning: message type \"" + msg.what
							+ "\" not supported");
				}
			}
		};

		// Initialize data source
		dao = new TweetDataSource(this);

		// Skip authentication if tokens already exist
		settings = getSharedPreferences(PREFS_NAME, 0);
		String userToken = settings.getString(USER_TOKEN, null);
		String userTokenSecret = settings.getString(USER_TOKEN_SECRET, null);
		if (userToken != null && userTokenSecret != null) {
			Log.i("TweetyCloud", "User token exists!");
			client = new OAuthSignpostClient(MY_TWITTER_KEY, MY_TWITTER_SECRET,
					userToken, userTokenSecret);
			jtwitter = new Twitter(null, client);
			Log.i("TweetyCloud", "Authorised :)");
			tokens = null;
			onSuccess();
			return;
		}

		// Authenticate with Twitter and save user tokens on success
		client = new OAuthSignpostClient(MY_TWITTER_KEY, MY_TWITTER_SECRET,
				MY_TWITTER_CALLBACK);
		Log.i("TweetyCloud", "TwitterAuth run!");
		final WebView webview = new WebView(this);
		webview.setBackgroundColor(Color.BLACK);
		webview.setVisibility(View.VISIBLE);
		final Dialog dialog = new Dialog(this,
				android.R.style.Theme_Black_NoTitleBar_Fullscreen);
		dialog.setContentView(webview);
		dialog.show();
		webview.getSettings().setJavaScriptEnabled(true);
		webview.setWebViewClient(new WebViewClient() {

			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				Log.d("TweetyCloud", "url: " + url);
				if (!url.contains(MY_TWITTER_CALLBACK))
					return;
				Uri uri = Uri.parse(url);
				verifier = uri.getQueryParameter("oauth_verifier");
				if (verifier == null) {
					// denied!
					Log.i("TweetyCloud", "Auth-fail: " + url);
					dialog.dismiss();
					onFail(new Exception(url));
					return;
				}
				new Thread() {
					@Override
					public void run() {
						client.setAuthorizationCode(verifier);
						tokens = client.getAccessToken();
						jtwitter = new Twitter(null, client);
						Log.i("TweetyCloud", "Authorised :)");
						Message msg = Message.obtain();
						msg.what = HANDLER_MESSAGE_TOKENS;
						threadHandler.sendMessage(msg);
					}
				}.start();
				dialog.dismiss();
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				Log.i("TweetyCloud", "url finished: " + url);
			}
		});
		// Workaround for http://code.google.com/p/android/issues/detail?id=7189
		webview.requestFocus(View.FOCUS_DOWN);
		webview.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				if (e.getAction() == MotionEvent.ACTION_DOWN
						|| e.getAction() == MotionEvent.ACTION_UP) {
					if (!v.hasFocus()) {
						v.requestFocus();
					}
				}
				return false;
			}
		});

		// Load login URL
		new Thread() {
			@Override
			public void run() {
				try {
					URI authUrl = client.authorizeUrl();
					webview.loadUrl(authUrl.toString());
				} catch (Exception e) {
					onFail(e);
				}
			}
		}.start();
	}

	/**
	 * onSuccess is called if authorization is successful or user token already
	 * exists.
	 */
	protected void onSuccess() {

		// save tokens if provided
		if (tokens != null) {
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(USER_TOKEN, tokens[0]);
			editor.putString(USER_TOKEN_SECRET, tokens[1]);
			editor.commit();
		}

		// get twitter list for the user
		new Thread() {
			public void run() {
				myList = jtwitter.getListsAll(jtwitter.getSelf(), self);
				List<String> myListString = new ArrayList<String>();
				myListString.add("All");
				for (TwitterList l : myList) {
					myListString.add(l.getName());
				}
				Log.i("TweetyCloud", "Fetched lists");

				// show the list to user
				myListAdapter = new ArrayAdapter<String>(self, R.layout.main,
						R.id.text1, myListString);
				Message msg = Message.obtain();
				msg.what = HANDLER_MESSAGE_LIST_CREATED;
				threadHandler.sendMessage(msg);
			}
		}.start();
	}

	/**
	 * onFail is called when Twitter authorisation failed.
	 * 
	 * @param e
	 *            Exception thrown while authenticating with Twitter
	 */
	protected void onFail(Exception e) {
		Toast.makeText(this, "Twitter authorisation failed!", Toast.LENGTH_LONG)
				.show();
		Log.w("TweetyCloud", e.toString());
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		if (position == 0) {
			selectedList = null;
			Log.i("TweetyCloud", "All clicked!");
		} else {
			selectedList = myList.get(position - 1);
			Log.i("TweetyCloud", selectedList.getName() + " clicked!");
		}

		// Lock orientation to avoid progress dialog to lose window
		int currentOrientation = getResources().getConfiguration().orientation;
		if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		}

		// Show the progress dialog
		progDialog = ProgressDialog.show(this, "Retrieving tweets",
				"please wait....", true, true);
		progDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

			@Override
			public void onCancel(DialogInterface dialog) {
				stopThread = true;
			}
		});

		// Thread for retrieving tweets from the selected list
		stopThread = false;
		new Thread() {
			public void run() {
				List<Status> statuses;
				try {
					// 'All' is selected if selectedList is null. Otherwise a
					// specific Twitter list is selected.
					if (selectedList == null) {
						jtwitter.setMaxResults(PER_PAGE);
						statuses = jtwitter.getHomeTimeline(self);
					} else {
						statuses = selectedList.getStatuses(self, PER_PAGE);
					}
					if (stopThread) {
						Message msg = Message.obtain();
						msg.what = HANDLER_MESSAGE_STOP_THREAD;
						threadHandler.sendMessage(msg);
						return;
					}

					// Save tweets to data source
					dao.open();
					dao.clearTweets();
					for (Status s : statuses) {
						dao.saveTweet(s);
					}
					dao.close();
				} catch (Exception e) {
					Log.w("TweetyCloud", e.toString());
					Message msg = Message.obtain();
					msg.what = HANDLER_MESSAGE_ERROR;
					threadHandler.sendMessage(msg);
					return;
				}

				// Stop from going to the next Activity if user requested to
				// cancel the action. Otherwise proceed.
				if (stopThread) {
					Message msg = Message.obtain();
					msg.what = HANDLER_MESSAGE_STOP_THREAD;
					threadHandler.sendMessage(msg);
					return;
				} else {
					Message msg = Message.obtain();
					msg.what = HANDLER_MESSAGE_COMPLETED;
					threadHandler.sendMessage(msg);
				}

				// Start tag cloud activity
				Intent myIntent = new Intent(self, WordCloudActivity.class);
				self.startActivity(myIntent);
			}
		}.start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.layout.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		// Handle item selection
		switch (item.getItemId()) {

		case R.id.clear_token:
			clearTokens();
			return true;
		case R.id.about:
			showAbout();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Logout, clear the user tokens so next time users need to login again with
	 * Twitter
	 */
	public void clearTokens() {
		SharedPreferences.Editor editor = settings.edit();
		editor.remove(USER_TOKEN);
		editor.remove(USER_TOKEN_SECRET);
		editor.commit();
		finish();
	}

	/**
	 * Show the 'about this app' dialog
	 */
	public void showAbout() {
		AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setTitle("About");
		SpannableString s = null;
		try {
			s = new SpannableString(
					"TweetyCloud version "
							+ getPackageManager().getPackageInfo(
									getPackageName(), 0).versionName
							+ "\n(c) Shigeru Sasao\n2012 All Rights Reserved"
							+ "\n\nPowered by:\n\ntagin!\n"
							+ "https://launchpad.net/tagin\n\n" + "JTwitter\n"
							+ "http://www.winterwell.com/software/jtwitter.php");
		} catch (NameNotFoundException e) {
			Log.d("TweetyCloud", e.getMessage());
		}
		Linkify.addLinks(s, Linkify.WEB_URLS);
		alertDialog.setMessage(s);
		alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}
		});
		alertDialog.show();
	}
}
