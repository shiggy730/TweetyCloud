package com.tweetycloud;

import java.util.ArrayList;
import java.util.List;

import winterwell.jtwitter.Status;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

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
 * Data source object for saving/retrieving tweets into SQLite
 * 
 * @author Shigeru Sasao
 * 
 */
public class TweetDataSource {

	/** The SQLite database */
	private SQLiteDatabase database;

	/** Helper class containing the actual SQL statements */
	private TweetyCloudSQLiteHelper dbHelper;

	/** All columns in the table **/
	private String[] allColumns = { TweetyCloudSQLiteHelper.COLUMN_TWEET_ID,
			TweetyCloudSQLiteHelper.COLUMN_USER,
			TweetyCloudSQLiteHelper.COLUMN_TEXT };

	/**
	 * Constructor
	 * 
	 * @param context
	 *            Android context
	 */
	public TweetDataSource(Context context) {
		dbHelper = new TweetyCloudSQLiteHelper(context);
	}

	/**
	 * Open database connection
	 * 
	 * @throws SQLException
	 */
	public void open() throws SQLException {
		database = dbHelper.getWritableDatabase();
	}

	/**
	 * Close database connection
	 */
	public void close() {
		dbHelper.close();
	}

	/**
	 * Save a tweet into the database
	 * 
	 * @param status
	 *            Status class containing the tweet
	 */
	public void saveTweet(Status status) {
		ContentValues values = new ContentValues();
		values.put(TweetyCloudSQLiteHelper.COLUMN_TWEET_ID, status.getId()
				.toString());
		values.put(TweetyCloudSQLiteHelper.COLUMN_USER, status.getUser()
				.getName());
		values.put(TweetyCloudSQLiteHelper.COLUMN_TEXT, status.getText());
		database.insert(TweetyCloudSQLiteHelper.TABLE_TWEETS, null, values);
	}

	/**
	 * Get all tweets in the database table
	 * 
	 * @return List of all tweets in the database table
	 */
	public List<Tweet> getAllTweets() {
		List<Tweet> tweets = new ArrayList<Tweet>();

		Cursor cursor = database.query(TweetyCloudSQLiteHelper.TABLE_TWEETS,
				allColumns, null, null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Tweet t = cursorToTweet(cursor);
			tweets.add(t);
			cursor.moveToNext();
		}
		// Make sure to close the cursor
		cursor.close();
		return tweets;
	}

	/**
	 * Clear the database table
	 */
	public void clearTweets() {
		dbHelper.purge(database);
	}

	/**
	 * Convert from database cursor to the Tweet class
	 * 
	 * @param cursor
	 *            database cursor
	 * @return Tweet class containing the tweet
	 */
	private Tweet cursorToTweet(Cursor cursor) {
		Tweet t = new Tweet();
		t.setId(cursor.getString(0));
		t.setUser(cursor.getString(1));
		t.setTweet(cursor.getString(2));
		return t;
	}
}
