package winterwell.jtwitter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import winterwell.json.JSONArray;
import winterwell.json.JSONException;
import winterwell.json.JSONObject;
import winterwell.jtwitter.TwitterException.E401;
import winterwell.jtwitter.TwitterException.E403;
import winterwell.jtwitter.TwitterException.SuspendedUser;

import com.tweetycloud.TweetyCloudActivity;

/**
 * Java wrapper for the Twitter API version {@value #version}
 * <p>
 * Example usage:<br>
 * First, you should get the user to authorise access via OAuth. There are a
 * couple of ways of doing this -- we show one below -- see
 * {@link OAuthSignpostClient} for more details.
 * <p>
 * Note that you don't need to do this for some operations - e.g. you can look
 * up public posts without logging in (use the {@link #Twitter()} constructor.
 * You can also - for now! - use username and password to login, but Twitter
 * plan to switch this off soon.
 * 
 * <code><pre>
	// First, OAuth to login: Make an oauth client
	OAuthSignpostClient oauthClient = new OAuthSignpostClient(JTWITTER_OAUTH_KEY, JTWITTER_OAUTH_SECRET, "oob");
    // open the authorisation page in the user's browser
    oauthClient.authorizeDesktop(); // Note: this only works on desktop PCs
    // or direct the user to the webpage given jby oauthClient.authorizeUrl()
    // get the pin from the user since we're using "oob" instead of a callback servlet
    String v = oauthClient.askUser("Please enter the verification PIN from Twitter");
    oauthClient.setAuthorizationCode(v);
	// You can store the authorisation token details for future use
    Object accessToken = client.getAccessToken();
</pre></code>
 * 
 * Now we can access Twitter: <code><pre>
	// Make a Twitter object
	Twitter twitter = new Twitter("my-name", oauthClient);
	// Print Winterstein's status
	System.out.println(twitter.getStatus("winterstein"));
	// Set my status
	twitter.updateStatus("Messing about in Java");
</pre></code>
 * 
 * <p>
 * If you can handle callbacks, then the OAuth login can be streamlined. You
 * need a webserver and a servlet (eg. use Jetty or Tomcat) to handle callbacks.
 * Replace "oob" with your callback url. Direct the user to
 * client.authorizeUrl(). Twitter will then call your callback with the request
 * token and verifier (authorisation code).
 * </p>
 * <p>
 * See {@link http://www.winterwell.com/software/jtwitter.php} for more
 * information about this wrapper. See {@link http://dev.twitter.com/doc} for
 * more information about the Twitter API.
 * <p>
 * Notes:
 * <ul>
 * <li>Takes care of all url-encoding/decoding.
 * <li>Will throw a runtime exception (TwitterException) if a
 * methods fails, e.g. it cannot connect to Twitter.com or you make a bad
 * request.
 * <li>Note that Twitter treats old-style retweets (those made by sending a
 * normal tweet beginning "RT @whoever") differently from new-style retweets
 * (those made using the retweet API). The differences are documented in various
 * methods.
 * <li>This class itself holds tweet-related methods.
 * Also see the "sub-classes" accessed via {@link #users()}, {@link #geo()}, and {@link #account()}. 
 * For list support see {@link TwitterList} - though {@link #getLists()} is here.
 * <li>This class is NOT thread safe. If you're using multiple threads, it is
 * best to create separate Twitter objects (which is fine).
 * </ul>
 * 
 * <h4>Copyright and License</h4>
 * This code is copyright (c) Winterwell Associates 2008/2009 and (c) winterwell
 * Mathematics Ltd, 2007 except where otherwise stated. It is released as
 * open-source under the LGPL license. See <a
 * href="http://www.gnu.org/licenses/lgpl.html"
 * >http://www.gnu.org/licenses/lgpl.html</a> for license details. This code
 * comes with no warranty or support.
 * 
 * <h4>Change List</h4>
 * The change list is kept online at: {@link http
 * ://www.winterwell.com/software/changelist.txt}
 * 
 * @author Daniel Winterstein
 */
public class Twitter implements Serializable {
	/**
	 * Use to register per-page callbacks for long-running searches. To stop the
	 * search, return true.
	 * 
	 */
	public interface ICallback {
		public boolean process(List<Status> statuses);
	}	
			
	/**
	 * How is the Twitter API today?
	 * See {@link https://dev.twitter.com/status} for more information. 
	 * @return map of {method: %uptime in the last 24 hours}.
	 * An empty map indicates this method itself failed!
	 * 
	 * @throws Exception This method is not officially supported! As such,
	 * it could break at some future point.
	 */
	public static Map<String,Double> getAPIStatus() throws Exception{
		HashMap<String,Double> map = new HashMap();
		// c.f. https://dev.twitter.com/status & https://status.io.watchmouse.com/7617
		// https://api.io.watchmouse.com/synth/current/39657/folder/7617/?fields=info;cur;24h.uptime;24h.status;last.date;daily.avg;daily.uptime;daily.status;daily.period
		String json = null;
		try {
			URLConnectionHttpClient client = new URLConnectionHttpClient();
			json = client.getPage("https://api.io.watchmouse.com/synth/current/39657/folder/7617/?fields=info;cur;24h.uptime", null, false);
			JSONObject jobj = new JSONObject(json);
			JSONArray jarr = jobj.getJSONArray("result");
			for(int i=0; i<jarr.length(); i++) {
				JSONObject jo = jarr.getJSONObject(i);
				String name = jo.getJSONObject("info").getString("name");
				JSONObject h24 = jo.getJSONObject("24h");
				double value = h24.getDouble("uptime");
				map.put(name, value);
			}
			return map;
		} catch (JSONException e) {
			throw new TwitterException.Parsing(json, e);
		} catch (Exception e) {
			return map;
		}		
	}
	
	/**
	 * Interface for an http client - e.g. allows for OAuth to be used instead.
	 * The standard version is {@link OAuthSignpostClient}.
	 * <p>
	 * If creating your own version, please provide support for throwing the
	 * right subclass of TwitterException - see
	 * {@link URLConnectionHttpClient#processError(java.net.HttpURLConnection)}
	 * for example code.
	 * 
	 * @author Daniel Winterstein
	 */
	public static interface IHttpClient {

		/**
		 * Whether this client is setup to do authentication when contacting the
		 * Twitter server. Note: This is a fast method that does not call the
		 * server, so it does not check whether the access token or password is
		 * valid. See {Twitter#isValidLogin()} or
		 * {@link Twitter_Account#verifyCredentials()} if you need to check a
		 * login.
		 * */
		boolean canAuthenticate();

		/**
		 * Lower-level GET method.
		 * 
		 * @param url
		 * @param vars
		 * @param authenticate
		 * @return
		 * @throws IOException
		 */
		HttpURLConnection connect(String url, Map<String, String> vars,
				boolean authenticate) throws IOException;

		/**
		 * @return a copy of this client. The copy can share structure, but it
		 *         MUST be safe for passing to a new thread to be used in
		 *         parallel with the original.
		 */
		IHttpClient copy();

		/**
		 * Fetch a header from the last http request. This is inherently NOT
		 * thread safe. Headers from error messages should (probably) be cached.
		 * 
		 * @param headerName
		 * @return header value, or null if unset
		 */
		String getHeader(String headerName);

		/**
		 * Send an HTTP GET request and return the response body. Note that this
		 * will change all line breaks into system line breaks!
		 * 
		 * @param uri
		 *            The uri to fetch
		 * @param vars
		 *            get arguments to add to the uri
		 * @param authenticate
		 *            If true, use authentication. The authentication method
		 *            used depends on the implementation (basic-auth, OAuth). It
		 *            is an error to use true if no authentication details have
		 *            been set.
		 * 
		 * @throws TwitterException
		 *             for a variety of reasons
		 * @throws TwitterException.E404
		 *             for resource-does-not-exist errors
		 */
		String getPage(String uri, Map<String, String> vars,
				boolean authenticate) throws TwitterException;

		/**
		 * @see Twitter#getRateLimit(KRequestType) This is where the Twitter
		 *      method is implemented.
		 */
		RateLimit getRateLimit(KRequestType reqType);

		/**
		 * Send an HTTP POST request and return the response body.
		 * 
		 * @param uri
		 *            The uri to post to.
		 * @param vars
		 *            The form variables to send. These are URL encoded before
		 *            sending.
		 * @param authenticate
		 *            If true, send user authentication
		 * @return The response from the server.
		 * 
		 * @throws TwitterException
		 *             for a variety of reasons
		 * @throws TwitterException.E404
		 *             for resource-does-not-exist errors
		 */
		String post(String uri, Map<String, String> vars, boolean authenticate)
				throws TwitterException;

		/**
		 * Lower-level POST method.
		 * 
		 * @param uri
		 * @param vars
		 * @return a freshly opened authorised connection
		 * @throws TwitterException
		 */
		HttpURLConnection post2_connect(String uri, Map<String, String> vars)
				throws Exception;

		/**
		 * Set the timeout for a single get/post request. This is an optional
		 * method - implementations can ignore it!
		 * 
		 * @param millisecs
		 */
		void setTimeout(int millisecs);

	}

	/**
	 * This gives common access to features that are common to both
	 * {@link Message}s and {@link Status}es.
	 * 
	 * @author daniel
	 * 
	 */
	public static interface ITweet extends Serializable {

		Date getCreatedAt();

		/**
		 * Twitter IDs are numbers - but they can exceed the range of Java's
		 * signed long.
		 * 
		 * @return The Twitter id for this post. This is used by some API
		 *         methods. This may be a Long or a BigInteger.
		 */
		Number getId();

		/**
		 * @return the location of this tweet. Can be null, never blank. This
		 *         can come from geo-tagging or the user's location. This may be
		 *         a place name, or in the form "latitude,longitude" if it came
		 *         from a geo-tagged source.
		 *         <p>
		 *         Note: This will be set if Twitter supply any geo-information.
		 *         We extract a location from geo and place objects.
		 */
		String getLocation();

		/**
		 * @return list of screen-names this message is to. May be empty, never
		 *         null. For Statuses, this is anyone mentioned in the message.
		 *         For DMs, this is a wrapper round
		 *         {@link Message#getRecipient()}.
		 *         <p>
		 *         Notes: This method is in ITweet as a convenience to allow the
		 *         same code to process both Statuses and Messages where
		 *         possible. It would be better named "getRecipients()", but for
		 *         historical reasons it isn't.
		 */
		List<String> getMentions();

		/**
		 * @return more information on the location of this tweet. This is
		 *         usually null!
		 */
		Place getPlace();

		/** The actual status text. This is also returned by {@link #toString()} */
		String getText();

		/**
		 * Twitter wrap urls with their own url-shortener (as a defence against
		 * malicious tweets). You are recommended to direct people to the
		 * Twitter-url, but use the original url for display.
		 * <p>
		 * Entity support is off by default. Request entity support by setting
		 * {@link Twitter#setIncludeTweetEntities(boolean)}. Twitter do NOT
		 * support entities for search :(
		 * 
		 * @param type
		 *            urls, user_mentions, or hashtags
		 * @return the text entities in this tweet, or null if the info was not
		 *         supplied.
		 */
		List<TweetEntity> getTweetEntities(KEntityType type);

		/** The User who made the tweet */
		User getUser();

		/**
		 * @return text, with the t.co urls replaced.
		 * Use-case: for filtering based on text contents, when we want to
		 * match against the full url.
		 * Note: this does NOT resolve short urls from bit.ly etc. 
		 */
		String getDisplayText();

	}

	public static enum KEntityType {
		hashtags, urls, user_mentions
	}

	/**
	 * The different types of API request. These can have different rate limits.
	 */
	public static enum KRequestType {
		NORMAL(""), 
		SEARCH("Feature"),
		/** this is X-Feature Class "namesearch" in the response headers */
		SEARCH_USERS("Feature"), 
		SHOW_USER(""), 
		UPLOAD_MEDIA("Media");

		/**
		 * USed to find the X-?RateLimit header.
		 */
		final String rateLimit;

		private KRequestType(String rateLimit) {
			this.rateLimit = rateLimit;
		}
	}

	/**
	 * A special slice of text within a tweet.
	 * 
	 * @see Twitter#setIncludeTweetEntities(boolean)
	 */
	public final static class TweetEntity implements Serializable {
		private static final long serialVersionUID = 1L;

		/**
		 * 
		 * @param tweet
		 * @param rawText
		 * @param type
		 * @param jsonEntities
		 * @return Can be null if no entities of this type are specified
		 * @throws JSONException
		 */
		static List<TweetEntity> parse(ITweet tweet, String rawText, KEntityType type,
				JSONObject jsonEntities) throws JSONException 
		{
			assert type != null && tweet != null && rawText != null && jsonEntities!=null
								: tweet+"\t"+rawText+"\t"+type+"\t"+jsonEntities;
			try {
				JSONArray arr = jsonEntities.optJSONArray(type.toString());
				// e.g. "user_mentions":[{"id":19720954,"name":"Lilly Hunter","indices":[0,10],"screen_name":"LillyLyle"}
				if (arr==null || arr.length()==0) {
					return null;
				}
				ArrayList<TweetEntity> list = new ArrayList<TweetEntity>(
						arr.length());
				for (int i = 0; i < arr.length(); i++) {
					JSONObject obj = arr.getJSONObject(i);
					TweetEntity te = new TweetEntity(tweet, rawText, type, obj);
					list.add(te);
				}
				return list;
			} catch (Throwable e) {
				// whatever bogus data Twitter send, don't fail
				return null;
			}
		}

		final String display;
		/**
		 * end of the entity in the contents String, exclusive
		 */
		public final int end;
		/**
		 * start of the entity in the contents String, inclusive
		 */
		public final int start;
		private final ITweet tweet;

		public final KEntityType type;

		/**
		 * 
		 * @param tweet
		 * @param rawText Needed to undo the indexing errors created by entity encoding
		 * @param type
		 * @param obj
		 * @throws JSONException
		 */
		TweetEntity(ITweet tweet, String rawText, KEntityType type, JSONObject obj)
				throws JSONException 
		{
			this.tweet = tweet;
			this.type = type;
			switch (type) {
			case urls:
				Object eu = obj.opt("expanded_url");
				display = JSONObject.NULL.equals(eu) ? null : (String) eu;
				break;
			case user_mentions:
				display = obj.getString("name");
				break;
			default:
				display = null;
			}
			
			// start, end
			JSONArray indices = obj.getJSONArray("indices");
			int _start = indices.getInt(0);
			int _end = indices.getInt(1);
			assert _start >= 0 && _end >= _start : obj;
			// Sadly, due to entity encoding, start/end may be off!
			String text = tweet.getText();
			if (rawText.regionMatches(_start, text, _start, _end - _start)) {
				// normal case: all OK			
				start = _start; end = _end;
				return;
			}
			// oh well - let's correct start/end
			// Note: This correction go wrong in a particular case: 
			// encoding has messed up the indices & we have a repeated entity.
			// ??Do we care enough to fix such a rare corner case with moderately harmless side-effects?
			
			// Protect against (rare) dud data from Twitter
			_end = Math.min(_end, rawText.length());
			_start = Math.min(_start, _end);
			if (_start==_end) { // paranoia
				start = _start; 
				end = _end;
				return;
			}
				
			String entityText = rawText.substring(_start, _end);
			int i = text.indexOf(entityText);
			if (i==-1) {
				// This can't legitimately happen, but handle it anyway 'cos it does (rare & random)
				entityText = InternalUtils.unencode(entityText);
				i = text.indexOf(entityText);
				if (i==-1) i = _start; // give up gracefully
			}
			start = i; 
			end = start + _end - _start;		
		}

		/**
		 * Constructor for when you know exactly what you want (rare).
		 */
		TweetEntity(ITweet tweet, KEntityType type, int start, int end, String display) {
			this.tweet = tweet;
			this.end = end;
			this.start = start;
			this.type = type;			
			this.display = display;
		}

		/**
		 * @return For a url: the expanded version For a user-mention: the
		 *         user's name
		 */
		public String displayVersion() {
			return display == null ? toString() : display;
		}

		/**
		 * The slice of text in the tweet. E.g. for a url, this will be the
		 * *shortened* version.
		 * 
		 * @see #displayVersion()
		 */
		@Override
		public String toString() {
			// There is a strange bug where -- rarely -- end > tweet length!
			// I think this is now fixed (it was an encoding issue).
			String text = tweet.getText();
			int e = Math.min(end, text.length());
			int s = Math.min(start, e);
			return text.substring(s, e);
		}
	}

	/**
	 * This rather dangerous global toggle switches off lower-casing on Twitter
	 * screen-names.
	 * <p>
	 * Screen-names are case insensitive as far as Twitter is concerned. However
	 * you might want to preserve the case people use for display purposes.
	 * <p>
	 * false by default.
	 */
	public static boolean CASE_SENSITIVE_SCREENNAMES;

	static final Pattern contentTag = Pattern.compile(
			"<content>(.+?)<\\/content>", Pattern.DOTALL);

	static final Pattern idTag = Pattern.compile("<id>(.+?)<\\/id>",
			Pattern.DOTALL);

	/**
	 * The length of a url after t.co shortening. Currently 20 characters.
	 * <p>
	 * Use updateConfiguration() if you want to get the latest settings from
	 * Twitter.
	 */
	public static int LINK_LENGTH = 20;

	public static long PHOTO_SIZE_LIMIT;

	public static final String SEARCH_MIXED = "mixed";

	public static final String SEARCH_POPULAR = "popular";

	public static final String SEARCH_RECENT = "recent";

	private static final long serialVersionUID = 1L;

	/**
	 * Search has to go through a separate url (Twitter's decision, June 2010).
	 */
	private static final String TWITTER_SEARCH_URL = "http://search.twitter.com";

	/**
	 * JTwitter version
	 */
	public final static String version = "2.6.4";

	/**
	 * The maximum number of characters that a tweet can contain.
	 */
	public final static int MAX_CHARS = 140;

	/**
	 * Set to true to perform extra error-handling & correction.
	 */
	public static boolean WORRIED_ABOUT_TWITTER = false;

	/**
	 * Convenience method: Finds a user with the given screen-name from the
	 * list.
	 * 
	 * @param screenName
	 *            aka login name
	 * @param users
	 * @return User with the given name, or null.
	 */
	public static User getUser(String screenName, List<User> users) {
		assert screenName != null && users != null;
		for (User user : users) {
			if (screenName.equals(user.screenName))
				return user;
		}
		return null;
	}

	/**
	 * TODO merge with {@link #maxResults}??
	 */
	Integer count;

	/**
	 * Used by search
	 */
	private String geocode;
	final IHttpClient http;

	boolean includeRTs = true;

	private String lang;

	private BigInteger maxId;

	/**
	 * Provides support for fetching many pages
	 */
	private int maxResults = -1;

	private double[] myLatLong;

	/**
	 * Twitter login name. Can be null even if we have authentication when using
	 * OAuth.
	 */
	private String name;

	/**
	 * Gets used once then reset to null by
	 * {@link #addStandardishParameters(Map)}. Gets updated in the while loops
	 * of methods doing a get-all-pages.
	 */
	Integer pageNumber;

	private String resultType;

	/**
	 * The user. Can be null. Can be a "fake-user" (screenname-only) object.
	 */
	User self;

	private Date sinceDate;

	private Number sinceId;

	private String sourceApp = "jtwitterlib";

	boolean tweetEntities = true;

	private String twitlongerApiKey;

	private String twitlongerAppName;

	/**
	 * Change this to access sites other than Twitter that support the Twitter
	 * API. <br>
	 * Note: Does not include the final "/"
	 */
	String TWITTER_URL = "http://api.twitter.com/1";

	private Date untilDate;

	private Number untilId;

	/**
	 * Create a Twitter client without specifying a user. This is an easy way to
	 * access public posts. But you can't post of course.
	 */
	public Twitter() {
		this(null, new URLConnectionHttpClient());
	}

	/**
	 * Java wrapper for the Twitter API.
	 * 
	 * @param name
	 *            the authenticating user's name, if known. Can be null.
	 * @param client
	 * @see OAuthSignpostClient
	 */
	public Twitter(String name, IHttpClient client) {
		this.name = name;
		http = client;
		assert client != null;
	}

	/**
	 * Copy constructor. Use this to pass cloned Twitter objects for
	 * multi-threaded work.
	 * 
	 * @param jtwit
	 */
	public Twitter(Twitter jtwit) {
		this(jtwit.getScreenName(), jtwit.http.copy());
	}

	/**
	 * API methods relating to your account.
	 */
	public Twitter_Account account() {
		return new Twitter_Account(this);
	}
	
	/**
	 * API methods for Twitter stats.
	 */
	public Twitter_Analytics analytics() {
		return new Twitter_Analytics(http);
	}

	/**
	 * Add in since_id, page and count, if set. This is called by methods that
	 * return lists of statuses or messages.
	 * 
	 * @param vars
	 * @return vars
	 */
	private Map<String, String> addStandardishParameters(
			Map<String, String> vars) {
		if (sinceId != null) {
			vars.put("since_id", sinceId.toString());
		}
		if (untilId != null) {
			vars.put("max_id", untilId.toString());
		}
		if (pageNumber != null) {
			vars.put("page", pageNumber.toString());
			// this is used once only
			pageNumber = null;
		}
		if (count != null) {
			vars.put("count", count.toString());
		}
		if (tweetEntities) {
			vars.put("include_entities", "1");
		}
		if (includeRTs) {
			vars.put("include_rts", "1");
		}
		return vars;
	}
	
	/**
	 * Filter keeping only those messages that come between sinceDate and
	 * untilDate (if either or both are set). The Twitter API used to offer
	 * this, but we now have to do it client side.
	 * 
	 * @see #setSinceId(Number)
	 * 
	 * @param list
	 * @return filtered list (a copy)
	 */
	private <T extends ITweet> List<T> dateFilter(List<T> list) {
		if (sinceDate == null && untilDate == null)
			return list;
		ArrayList<T> filtered = new ArrayList<T>(list.size());
		for (T message : list) {
			// assume OK if Twitter is being stingy on the info
			if (message.getCreatedAt() == null) {
				filtered.add(message);
				continue;
			}
			if (untilDate != null && untilDate.before(message.getCreatedAt())) {
				continue;
			}
			if (sinceDate != null && sinceDate.after(message.getCreatedAt())) {
				continue;
			}
			// ok
			filtered.add(message);
		}
		return filtered;
	}

	/**
	 * Have we got enough results for the current search?
	 * 
	 * @param list
	 * @return false if maxResults is set to -1 (ie, unlimited) or if list
	 *         contains less than maxResults results.
	 */
	boolean enoughResults(List list) {
		return (maxResults != -1 && list.size() >= maxResults);
	}

	void flush() {
		// This seems to prompt twitter to update in some cases!
		http.getPage("http://twitter.com/" + name, null, true);
	}
	
	@Override
	public String toString() {
		return name==null? "Twitter" : "Twitter["+name+"]";
	}

	/**
	 * Geo-location API methods.
	 * Doesn't require a logged in user.
	 */
	public Twitter_Geo geo() {
		return new Twitter_Geo(this);
	}

	/**
	 * Returns the 20 most recent statuses posted in the last 24 hours from the
	 * authenticating user and that user's friends, including retweets.
	 */
	public List<Status> getHomeTimeline(TweetyCloudActivity tweety) throws TwitterException {
		assert http.canAuthenticate();
		return getStatuses(TWITTER_URL + "/statuses/home_timeline.json",
				standardishParameters(), true, tweety);
	}

	/**
	 * Provides access to the {@link IHttpClient} which manages the low-level
	 * authentication, posts and gets.
	 */
	public IHttpClient getHttpClient() {
		return http;
	}
	
	/**
	 * 
		Returns <i>all</i> lists the authenticating or specified user subscribes to, 
		including their own.
	   @param user can be null for the authenticating user.
	   @see #getLists(String)
	 */
	public List<TwitterList> getListsAll(User user, TweetyCloudActivity tweety) {		
		assert user!=null || http.canAuthenticate() : "No authenticating user";
		try {
			String url = TWITTER_URL + "/lists/all.json";
			Map<String, String> vars = user.screenName==null?
					InternalUtils.asMap("user_id", user.id)
					: InternalUtils.asMap("screen_name", user.screenName);
			String listsJson = http.getPage(url, vars, http.canAuthenticate());
			//JSONObject wrapper = new JSONObject(listsJson);
			//JSONArray jarr = (JSONArray) wrapper.get("lists");
			JSONArray jarr = new JSONArray(listsJson, tweety);
			List<TwitterList> lists = new ArrayList<TwitterList>();
			for (int i = 0; i < jarr.length(); i++) {
				JSONObject li = jarr.getJSONObject(i);
				TwitterList twList = new TwitterList(li, this);
				lists.add(twList);
			}
			return lists;
		} catch (JSONException e) {
			throw new TwitterException.Parsing(null, e);
		}
	}
	
	/**
	 * Provides support for fetching many pages. -1 indicates "give me as much
	 * as Twitter will let me have."
	 */
	public int getMaxResults() {
		return maxResults;
	}

	/**
	 * Returns the 20 most recent statuses from non-protected users who have set
	 * a custom user icon. Does not require authentication.
	 * <p>
	 * Note: Twitter cache-and-refresh this every 60 seconds, so there is little
	 * point calling it more frequently than that.
	 */
	public List<Status> getPublicTimeline() throws TwitterException {
		return getStatuses(TWITTER_URL + "/statuses/public_timeline.json",
				standardishParameters(), false, null);
	}

	/**
	 * What is the current rate limit status? Do we need to throttle back our
	 * usage? This is the cached info from the last call of that type.
	 * <p>
	 * Note: The RateLimit object is created using cached info from a previous
	 * Twitter call. So this method is quick (it doesn't require a fresh call to
	 * Twitter), but the RateLimit object isn't available until after you make a
	 * call of the right type to Twitter.
	 * <p>
	 * Status: Heading towards stable, but still a bit experimental.
	 * 
	 * @param reqType
	 *            Different methods have separate rate limits.
	 * @return the last rate limit advice received, or null if unknown.
	 * @see #getRateLimitStatus()
	 */
	public RateLimit getRateLimit(KRequestType reqType) {
		return http.getRateLimit(reqType);
	}

	/**
	 * How many normal rate limit calls do you have left? This calls Twitter,
	 * which makes it slower than {@link #getRateLimit(KRequestType)} but it's
	 * up-to-date and safe against threads and other-programs using the same
	 * allowance.
	 * <p>
	 * This may update getRateLimit(KRequestType) for NORMAL requests, but sadly
	 * it doesn't fetch rate-limit info on other request types.
	 * 
	 * @return the remaining number of API requests available to the
	 *         authenticating user before the API limit is reached for the
	 *         current hour. <i>If this is zero or negative you should stop
	 *         using Twitter with this login for a bit.</i> Note: Calls to
	 *         rate_limit_status do not count against the rate limit.
	 * @see #getRateLimit(KRequestType)
	 */
	public int getRateLimitStatus() {
		String json = http.getPage(TWITTER_URL
				+ "/account/rate_limit_status.json", null,
				http.canAuthenticate());
		try {
			JSONObject obj = new JSONObject(json);
			int hits = obj.getInt("remaining_hits");
			// Update the RateLimit objects
			// http.updateRateLimits(KRequestType.NORMAL); no header info sent!
			if (http instanceof URLConnectionHttpClient) {
				URLConnectionHttpClient _http = (URLConnectionHttpClient) http;
				RateLimit rateLimit = new RateLimit(
						obj.getString("hourly_limit"), Integer.toString(hits),
						obj.getString("reset_time"));
				_http.rateLimits.put(KRequestType.NORMAL, rateLimit);
			}
			return hits;
		} catch (JSONException e) {
			throw new TwitterException.Parsing(json, e);
		}
	}

	/**
	 * @return Login name of the authenticating user, or null if not set.
	 *         <p>
	 *         Will call Twitter to find out if null but oauth is set.
	 * @see #getSelf()
	 */
	public String getScreenName() {
		if (name != null)
			return name;
		// load if need be
		getSelf();
		return name;
	}
	
	/**
	 * Equivalent to {@link #getScreenName()} except this won't ever do
	 * an API call.
	 * @return screenName or null
	 * @see #getScreenName()
	 */
	public String getScreenNameIfKnown() {
		return name;
	}

	/**
	 * @param searchTerm
	 * @param rpp
	 * @return
	 */
	private Map<String, String> getSearchParams(String searchTerm, int rpp) {
		Map<String, String> vars = InternalUtils.asMap("rpp",
				Integer.toString(rpp), "q", searchTerm);
		if (sinceId != null) {
			vars.put("since_id", sinceId.toString());
		}
		if (untilId != null) {
			// It's unclear from the docs whether this will work
			// c.f. https://dev.twitter.com/docs/api/1/get/search
			vars.put("max_id", untilId.toString());
		}
		// since date is no longer supported. until is though?!
		// if (sinceDate != null) vars.put("since", df.format(sinceDate));
		if (untilDate != null) {
			vars.put("until", InternalUtils.df.format(untilDate));
		}
		if (lang != null) {
			vars.put("lang", lang);
		}
		if (geocode != null) {
			vars.put("geocode", geocode);
		}
		if (resultType != null) {
			vars.put("result_type", resultType);
		}
		addStandardishParameters(vars);
		return vars;
	}

	/**
	 * @return you, or null if this is an anonymous Twitter object.
	 *         <p>
	 *         This will cache the result if it makes an API call.
	 */
	public User getSelf() {
		if (self != null)
			return self;
		if (!http.canAuthenticate()) {
			if (name != null) {
				// not sure this case makes sense, but we may as well handle it
				self = new User(name);
				return self;
			}
			return null;
		}
		account().verifyCredentials();
		name = self.getScreenName();
		return self;
	}

	/**
	 * Does the grunt work for paged status fetching
	 * 
	 * @param url
	 * @param var
	 * @param authenticate
	 * @return
	 */
	private List<Status> getStatuses(final String url, Map<String, String> var,
			boolean authenticate, TweetyCloudActivity tweety) {
		// Default: 1 page
		if (maxResults < 1) {
			List<Status> msgs = Status.getStatuses(http.getPage(url, var,
					authenticate), tweety);
			msgs = dateFilter(msgs);
			return msgs;
		}
		// Fetch all pages until we reach the desired maxResults, or run out
		// -- or Twitter complains in which case you'll get an exception
		// Use status ids for paging, rather than page number, because this
		// allows for "drift" when new tweets are posted during the paging.
		maxId = null;
		// pageNumber = 1;
		List<Status> msgs = new ArrayList<Status>();
		//var.put("per_page", Integer.toString(maxResults));
		while (msgs.size() <= maxResults) {
			if (tweety.stopThread) {
				return null;
			}
			String json = http.getPage(url, var, authenticate);
			List<Status> nextpage = Status.getStatuses(json, tweety);
			// This test replaces size<20. It requires an extra call to Twitter.
			// But it fixes a bug whereby retweets aren't counted and can thus
			// cause
			// the system to quit early.
			if (nextpage.size() == 0) {
				break;
			}
			// Next page must start strictly before this one
			maxId = nextpage.get(nextpage.size() - 1).id
					.subtract(BigInteger.ONE);
			// System.out.println(maxId + " -> " + nextpage.get(0).id);

			msgs.addAll(dateFilter(nextpage));
			// pageNumber++;
			var.put("max_id", maxId.toString());
		}
		return msgs;
	}

	/**
	 * @return the untilDate
	 */
	public Date getUntilDate() {
		return untilDate;
	}

	/**
	 * Returns the most recent statuses from the authenticating user. 20 by
	 * default.
	 */
	public List<Status> getUserTimeline() throws TwitterException {
		return getStatuses(TWITTER_URL + "/statuses/user_timeline.json",
				standardishParameters(), true, null);
	}

	/**
	 * Equivalent to {@link #getUserTimeline(String)}, but takes a numeric
	 * user-id instead of a screen-name.
	 * 
	 * @param userId
	 * @return tweets by userId
	 */
	public List<Status> getUserTimeline(Long userId) throws TwitterException {
		Map<String, String> vars = InternalUtils.asMap("user_id", userId);
		addStandardishParameters(vars);
		// Authenticate if we can (for protected streams)
		boolean authenticate = http.canAuthenticate();
		try {
			return getStatuses(TWITTER_URL + "/statuses/user_timeline.json",
					vars, authenticate, null);
		} catch (E401 e) {
			// Bug in Twitter: this can be a suspended user...
			// In which case the call below would generate a SuspendedUser exception
			// ...but do we want to conserve our api limit??
			// isSuspended(userId);
			throw e;
		}
	}

	/**
	 * Are we rate-limited, based on cached info from previous requests?
	 * @param type
	 * @param minCalls
	 *            Standard value = 1. The minimum number of calls which should
	 *            be available.
	 * @return true if this is currently rate-limited, & should not be used for
	 *         a while. false = OK
	 *         
	 * @see #getRateLimit(KRequestType) for more info
	 * @see #getRateLimitStatus() for guaranteed up-to-date info
	 */
	public boolean isRateLimited(KRequestType reqType, int minCalls) {
		// Check NORMAL first
		if (reqType != KRequestType.NORMAL) {
			boolean isLimited = isRateLimited(KRequestType.NORMAL, minCalls);
			if (isLimited)
				return true;
		}
		RateLimit rl = getRateLimit(reqType);
		// assume things are OK (except for NORMAL which we quickly check by
		// calling Twitter)
		if (rl == null) {
			if (reqType == KRequestType.NORMAL) {
				int rls = getRateLimitStatus();
				return rls >= minCalls;
			}
			return false;
		}
		// in credit?
		if (rl.getRemaining() >= minCalls)
			return false;
		// out of date?
		if (rl.getReset().getTime() < System.currentTimeMillis())
			return false;
		// nope - you're over the limit
		return true;
	}

	/**
	 * @return true if {@link #setupTwitlonger(String, String)} has been used to
	 *         provide twitlonger.com details.
	 * @see #updateLongStatus(String, long)
	 */
	public boolean isTwitlongerSetup() {
		return twitlongerApiKey != null && twitlongerAppName != null;
	}

	/**
	 * Are the login details used for authentication valid?
	 * 
	 * @return true if OK, false if unset or invalid
	 * @see Twitter_Account#verifyCredentials() which returns user info
	 */
	public boolean isValidLogin() {
		if (!http.canAuthenticate())
			return false;
		try {
			Twitter_Account ta = new Twitter_Account(this);
			User u = ta.verifyCredentials();
			return true;
		} catch (TwitterException.E403 e) {
			return false;
		} catch (TwitterException.E401 e) {
			return false;
		} catch (TwitterException e) {
			throw e;
		}
	}

	/**
	 * Wrapper for {@link IHttpClient#post(String, Map, boolean)}.
	 */
	private String post(String uri, Map<String, String> vars,
			boolean authenticate) throws TwitterException {
		String page = http.post(uri, vars, authenticate);
		return page;
	}

	/**
	 * Perform a search of Twitter. Convenience wrapper for
	 * {@link #search(String, ICallback, int)} with no callback and fetching one
	 * pages worth of results.
	 */
	public List<Status> search(String searchTerm) {
		return search(searchTerm, null, 100);
	}

	/**
	 * Perform a search of Twitter.
	 * <p>
	 * Warning: the User objects returned by a search (as part of the Status
	 * objects) are dummy-users. The only information that is set is the user's
	 * screen-name and a profile image url. This reflects the current behaviour
	 * of the Twitter API. If you need more info, call users().show()
	 * with the screen names.
	 * <p>
	 * This supports {@link #maxResults} and pagination. A language filter can
	 * be set via {@link #setLanguage(String)} Location can be set via
	 * {@link #setSearchLocation(double, double, String)}
	 * 
	 * Other advanced search features can be done via the query string. E.g.<br>
	 * "from:winterstein" - tweets from user winterstein<br>
	 * "to:winterstein" - tweets start with @winterstein<br>
	 * "source:jtwitter" - originating from the application JTwitter - your
	 * query must also must contain at least one keyword parameter. <br>
	 * "filter:links" - tweets contain a link<br>
	 * "apples OR pears" - or ("apples pears" would give you apples <i>and</i>
	 * pears).
	 * 
	 * @param searchTerm
	 *            This can include several space-separated keywords, #tags and @username
	 *            (for mentions), and use quotes for \"exact phrase\" searches.
	 * @param callback
	 *            an object whose process() method will be called on each new
	 *            page of results.
	 * @param rpp
	 *            results per page. 100 is the default
	 * @return search results - up to maxResults if maxResults is positive, or
	 *         rpp if maxResults is negative/zero. See
	 *         {@link #setMaxResults(int)} to use > 100.
	 */
	public List<Status> search(String searchTerm, ICallback callback, int rpp) {
		if (rpp > 100 && maxResults < rpp)
			throw new IllegalArgumentException(
					"You need to switch on paging to fetch more than 100 search results. First call setMaxResults() to raise the limit above "
							+ rpp);
		searchTerm = search2_bugHack(searchTerm);
		Map<String, String> vars;
		if (maxResults < 100 && maxResults > 0) {
			// Default: 1 page
			vars = getSearchParams(searchTerm, maxResults);
		} else {
			vars = getSearchParams(searchTerm, rpp);
		}
		// Fetch all pages until we run out
		// -- or Twitter complains in which case you'll get an exception
		List<Status> allResults = new ArrayList<Status>(Math.max(maxResults,
				rpp));
		String url = TWITTER_SEARCH_URL + "/search.json";
		int localPageNumber = 1; // pageNumber is nulled by getSearchParams
		do {
			pageNumber = localPageNumber;
			vars.put("page", Integer.toString(pageNumber));
			String json = http.getPage(url, vars, false);
			List<Status> stati = Status.getStatusesFromSearch(this, json);
			int numResults = stati.size();
			stati = dateFilter(stati);
			allResults.addAll(stati);
			if (callback != null) {
				// the callback may tell us to stop, by returning true
				if (callback.process(stati)) {
					break;
				}
			}
			if (numResults < rpp) { // We've reached the end of the results
				break;
			}
			// paranoia
			localPageNumber++;
		} while (allResults.size() < maxResults);
		// null for the next method
		pageNumber = null;
		return allResults;
	}

	/**
	 * This fixes a couple of bugs in Twitter's search API:
	 * 
	 * 1. Searches using OR and a location return gibberish, unless they also
	 * include a -term. Strangely that seems to fix things. So we just add one
	 * if needed.<br>
	 * 
	 * 2. Searches that start and end with quotes, and use an OR have problems:
	 * they become AND searches with the OR turned into a keyword. E.g. /"apple"
	 * OR "pear"/ acts like /"apple" AND or AND "pear"/
	 * <p>
	 * It should be tested periodically whether we need this. See
	 * {@link TwitterTest#testSearchBug()}, {@link TwitterTest#testSearchBug2()}
	 * 
	 * @param searchTerm
	 * @return e.g. "apples OR pears" (near Edinburgh) goes to
	 *         "apples OR pears -kfz" (near Edinburgh)
	 */
	private String search2_bugHack(String searchTerm) {
		// zero-length is valid with location
		if (searchTerm.length()==0)
			return searchTerm;
		// bug 1: a OR b near X fails
		if (searchTerm.contains(" OR ") && !searchTerm.contains("-")
				&& geocode != null)
			return searchTerm + " -kfz"; // add a -gibberish term
		// bug 2: "a" OR "b" fails
		if (searchTerm.contains(" OR ") && searchTerm.charAt(0) == '"'
				&& searchTerm.charAt(searchTerm.length() - 1) == '"')
			return searchTerm + " -kfz"; // add a -gibberish term
		// hopefully fine as-is
		return searchTerm;
	}

	/**
	 * Set this to access sites other than Twitter that support the Twitter API.
	 * E.g. WordPress or Identi.ca. Note that not all methods may work! Also,
	 * search uses a separate url and is not affected by this method (it will
	 * continue to point to Twitter).
	 * 
	 * @param url
	 *            Format: "http://domain-name", e.g. "http://twitter.com" by
	 *            default.
	 */
	public void setAPIRootUrl(String url) {
		assert url.startsWith("http://") || url.startsWith("https://");
		assert !url.endsWith("/") : "Please remove the trailing / from " + url;
		TWITTER_URL = url;
	}

	/**
	 * *Some* methods - the timeline ones for example - allow a count of
	 * number-of-tweets to return.
	 * 
	 * @param count
	 *            null for default behaviour. 200 is the current maximum.
	 *            Twitter may reject or ignore high counts.
	 */
	public void setCount(Integer count) {
		this.count = count;
	}

	public void setFavorite(Status status, boolean isFavorite) {
		try {
			String uri = isFavorite ? TWITTER_URL + "/favorites/create/"
					+ status.id + ".json" : TWITTER_URL + "/favorites/destroy/"
					+ status.id + ".json";
			http.post(uri, null, true);
		} catch (E403 e) {
			// already a favorite?
			if (e.getMessage() != null
					&& e.getMessage().contains("already favorited"))
				throw new TwitterException.Repetition(
						"You have already favorited this status.");
			// just a normal 403
			throw e;
		}
	}

	/**
	 * true by default. If true, lists of tweets will include new-style
	 * retweets. If false, they won't (execpt for the retweet-specific calls).
	 * 
	 * @param includeRTs
	 */
	public void setIncludeRTs(boolean includeRTs) {
		this.includeRTs = includeRTs;
	}

	/**
	 * Note: does NOT work for search() methods (not supported by Twitter).
	 * 
	 * @param tweetEntities
	 *            Set to true to enable
	 *            {@link Status#getTweetEntities(KEntityType)}, false if you
	 *            don't care. Default is true.
	 */
	public void setIncludeTweetEntities(boolean tweetEntities) {
		this.tweetEntities = tweetEntities;
	}

	/**
	 * Set a language filter for search results. Note: This only applies to
	 * search results.
	 * 
	 * @param language
	 *            ISO code for language. Can be null for all languages.
	 *            <p>
	 *            Note: there are multiple different ISO codes! Twitter supports
	 *            ISO 639-1. http://en.wikipedia.org/wiki/ISO_639-1
	 */
	public void setLanguage(String language) {
		lang = language;
	}

	/**
	 * @param maxResults
	 *            if greater than zero, requests will attempt to fetch as many
	 *            pages as are needed! -1 by default, in which case most methods
	 *            return the first 20 statuses/messages. Zero is not allowed.
	 *            <p>
	 *            If setting a high figure, you should usually also set a
	 *            sinceId or sinceDate to limit your Twitter usage. Otherwise
	 *            you can easily exceed your rate limit.
	 */
	public void setMaxResults(int maxResults) {
		assert maxResults != 0;
		this.maxResults = maxResults;
	}

	/**
	 * Set the location for your tweets.<br>
	 * 
	 * Warning: geo-tagging parameters are ignored if geo_enabled for the user
	 * is false (this is the default setting for all users unless the user has
	 * enabled geolocation in their settings)!
	 * 
	 * @param latitudeLongitude
	 *            Can be null (which is the default), in which case your tweets
	 *            will not carry location data.
	 *            <p>
	 *            The valid ranges for latitude is -90.0 to +90.0 (North is
	 *            positive) inclusive. The valid ranges for longitude is -180.0
	 *            to +180.0 (East is positive) inclusive.
	 * 
	 * @see #setSearchLocation(double, double, String) which is completely
	 *      separate.
	 */
	public void setMyLocation(double[] latitudeLongitude) {
		myLatLong = latitudeLongitude;
		if (myLatLong == null)
			return;
		if (Math.abs(myLatLong[0]) > 90)
			throw new IllegalArgumentException(myLatLong[0]
					+ " is not within +/- 90");
		if (Math.abs(myLatLong[1]) > 180)
			throw new IllegalArgumentException(myLatLong[1]
					+ " is not within +/- 180");
	}

	/**
	 * @param pageNumber
	 *            null (the default) returns the first page. Pages are indexed
	 *            from 1. This is used once only! Then it is reset to null
	 */
	public void setPageNumber(Integer pageNumber) {
		this.pageNumber = pageNumber;
	}

	/**
	 * Restricts {@link #search(String)} to tweets by users located within a
	 * given radius of the given latitude/longitude.
	 * <p>
	 * The location of a tweet is preferably taken from the Geotagging API, but
	 * will fall back to the Twitter profile.
	 * 
	 * @param latitude
	 * @param longitude
	 * @param radius
	 *            E.g. 3.5mi or 2km. Must be <2500km
	 */
	public void setSearchLocation(double latitude, double longitude,
			String radius) {
		assert radius.endsWith("mi") || radius.endsWith("km") : radius;
		geocode = latitude + "," + longitude + "," + radius;
	}

	/**
	 * Optional. Specifies what type of search results you would prefer to
	 * receive. The current default is "mixed." Valid values:<br>
	 * {@link #SEARCH_MIXED}: Include both popular and real time results in the
	 * response.<br> {@link #SEARCH_RECENT}: return only the most recent results in
	 * the response<br> {@link #SEARCH_POPULAR}: return only the most popular
	 * results in the response.<br>
	 * 
	 * @param resultType
	 */
	public void setSearchResultType(String resultType) {
		this.resultType = resultType;
	}

	/**
	 * Narrows the returned results to just those statuses created after the
	 * specified status id. This will be used until it is set to null. Default
	 * is null.
	 * <p>
	 * If using this, you probably also want to increase
	 * {@link #setMaxResults(int)} (otherwise you just get the most recent 20).
	 * 
	 * @param statusId
	 */
	public void setSinceId(Number statusId) {
		sinceId = statusId;
	}

	/**
	 * Set the source application. This will be mentioned on Twitter alongside
	 * status updates (with a small label saying source: myapp).
	 * 
	 * <i>In order for this to work, you must first register your app with
	 * Twitter and get a source name from them! You must also use OAuth to
	 * connect.</i>
	 * 
	 * @param sourceApp
	 *            jtwitterlib by default. Set to null for no source.
	 */
	public void setSource(String sourceApp) {
		this.sourceApp = sourceApp;
	}

	/**
	 * If set, return results older than this.
	 * 
	 * @param untilId
	 *            aka max_id
	 */
	public void setUntilId(Number untilId) {
		this.untilId = untilId;
	}

	/**
	 * Set this to allow the use of twitlonger via
	 * {@link #updateLongStatus(String, long)}. To get an api-key for your app,
	 * contact twitlonger as described here: http://www.twitlonger.com/api
	 * 
	 * @param twitlongerAppName
	 * @param twitlongerApiKey
	 */
	public void setupTwitlonger(String twitlongerAppName,
			String twitlongerApiKey) {
		this.twitlongerAppName = twitlongerAppName;
		this.twitlongerApiKey = twitlongerApiKey;
	}

	/**
	 * Split a long message up into shorter chunks suitable for use with
	 * {@link #setStatus(String)} or {@link #sendMessage(String, String)}.
	 * 
	 * @param longStatus
	 * @return longStatus broken into a list of max 140 char strings
	 */
	public List<String> splitMessage(String longStatus) {
		// Is it really long?
		if (longStatus.length() <= 140)
			return Collections.singletonList(longStatus);
		// Multiple tweets for a longer post
		List<String> sections = new ArrayList<String>(4);
		StringBuilder tweet = new StringBuilder(140);
		String[] words = longStatus.split("\\s+");
		for (String w : words) {
			// messages have a max length of 140
			// plus the last bit of a long tweet tends to be hidden on
			// twitter.com, so best to chop 'em short too
			if (tweet.length() + w.length() + 1 > 140) {
				// Emit
				tweet.append("...");
				sections.add(tweet.toString());
				tweet = new StringBuilder(140);
				tweet.append(w);
			} else {
				if (tweet.length() != 0) {
					tweet.append(" ");
				}
				tweet.append(w);
			}
		}
		// Final bit
		if (tweet.length() != 0) {
			sections.add(tweet.toString());
		}
		return sections;
	}

	/**
	 * Map with since_id, page and count, if set. This is called by methods that
	 * return lists of statuses or messages.
	 */
	private Map<String, String> standardishParameters() {
		return addStandardishParameters(new HashMap<String, String>());
	}

	// /**
	// * The length of an https url after t.co shortening.
	// * This is just 1 more than {@link #LINK_LENGTH}
	// * <p>
	// * Use updateConfiguration() if you want to get the latest settings from
	// Twitter.
	// */
	// public static int LINK_LENGTH_HTTPS = LINK_LENGTH+1;

	/**
	 * Update info on Twitter's configuration -- such as shortened url lengths.
	 */
	public void updateConfiguration() {
		String json = http.getPage(TWITTER_URL + "/help/configuration.json",
				null, false);
		try {
			JSONObject jo = new JSONObject(json);
			LINK_LENGTH = jo.getInt("short_url_length");
			// LINK_LENGTH_HTTPS = jo.getInt("short_url_length_https");
			// LINK_LENGTH + 1
			// characters_reserved_per_media -- this is just LINK_LENGTH
			// max_media_per_upload // 1!
			PHOTO_SIZE_LIMIT = jo.getLong("photo_size_limit");
			// photo_sizes
			// short_url_length_https
		} catch (JSONException e) {
			throw new TwitterException.Parsing(json, e);
		}
	}
	
	/**
	 * Compute the effective size of a message, given that Twitter treat things that
	 * smell like a URL as 20 characters.
	 * 
	 * @param message
	 * @return the effective message length in characters
	 */
	public static int countCharacters(String statusText) {
		int shortLength = statusText.length();
		Matcher m = InternalUtils.URL_REGEX.matcher(statusText);
		while(m.find()) {
			shortLength += LINK_LENGTH - m.group().length(); 
		}
		return shortLength;
	}
	
	/**
	 * User and social-network related API methods.
	 */
	public Twitter_Users users() {
		return new Twitter_Users(this);
	}
	
	/**
     * @see Twitter_Users#show(Number)
     */
    @Deprecated
    public User show(Number userId) {
            return users().show(userId);
    }
    
    /**
     * @param untilDate
     *            the untilDate to set. This is NOT
     *            properly supported. It operates by post filtering
     *            results client-side.
     * @see #setUntilId(Number) which is better
     */
    @Deprecated
    public void setUntilDate(Date untilDate) {
            this.untilDate = untilDate;
    }


}
