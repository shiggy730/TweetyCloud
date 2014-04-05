package com.tweetycloud;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

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
 * TweetyCloudSQLiteHelper is the helper class for the model containing the
 * actual SQL statements for retrieving and saving tweets into the database
 * 
 * @author Shigeru Sasao
 * 
 */
public class TweetyCloudSQLiteHelper extends SQLiteOpenHelper {

	/** Table and table columns */
	public static final String TABLE_TWEETS = "tweets";
	public static final String COLUMN_ID = "_id";
	public static final String COLUMN_TWEET_ID = "tweet_id";
	public static final String COLUMN_USER = "tweet_user";
	public static final String COLUMN_TEXT = "tweet";

	/** Database information */
	private static final String DATABASE_NAME = "twittercloud.db";
	private static final int DATABASE_VERSION = 1;

	/** Database creation sql statement */
	private static final String DATABASE_CREATE = "create table "
			+ TABLE_TWEETS + "(" + COLUMN_ID
			+ " integer primary key autoincrement, " + COLUMN_TWEET_ID
			+ " text, " + COLUMN_USER + " text, " + COLUMN_TEXT
			+ " text not null);";

	/** Database drop if exists sql statement */
	private static final String DATABASE_DROP = "DROP TABLE IF EXISTS "
			+ TABLE_TWEETS;

	/**
	 * Constructor
	 * 
	 * @param context
	 *            Android context
	 */
	public TweetyCloudSQLiteHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase database) {
		database.execSQL(DATABASE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(TweetyCloudSQLiteHelper.class.getName(),
				"Upgrading database from version " + oldVersion + " to "
						+ newVersion + ", which will destroy all old data");
		db.execSQL(DATABASE_DROP);
		onCreate(db);
	}

	/**
	 * Purge the database table
	 * 
	 * @param db
	 *            SQLite database
	 */
	public void purge(SQLiteDatabase db) {
		db.execSQL(DATABASE_DROP);
		onCreate(db);
	}
}
