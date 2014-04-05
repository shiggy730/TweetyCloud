package com.tweetycloud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.os.Bundle;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import com.komodo.tagin.Tag;
import com.komodo.tagin.TagCloudView;

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
 * WordCloudActivity is the Android activity that displays the word cloud from
 * the tweets in the SQLite database. The word cloud is created using code from
 * the tagin! project: https://launchpad.net/tagin
 * 
 * @author Shigeru Sasao
 * 
 */
public class WordCloudActivity extends Activity {

	/** Number of words to display in the cloud */
	private static int WORDS_IN_CLOUD = 80;

	/** List of common words to ignore */
	private static List<String> ignoreWords = Arrays.asList("a", "able",
			"about", "above", "abroad", "according", "accordingly", "across",
			"actually", "adj", "after", "afterwards", "again", "against",
			"ago", "ahead", "ain't", "all", "allow", "allows", "almost",
			"alone", "along", "alongside", "already", "also", "although",
			"always", "am", "amid", "amidst", "among", "amongst", "an", "and",
			"another", "any", "anybody", "anyhow", "anyone", "anything",
			"anyway", "anyways", "anywhere", "apart", "appear", "appreciate",
			"appropriate", "are", "aren't", "around", "as", "a's", "aside",
			"ask", "asking", "associated", "at", "available", "away",
			"awfully", "b", "back", "backward", "backwards", "be", "became",
			"because", "become", "becomes", "becoming", "been", "before",
			"beforehand", "begin", "behind", "being", "believe", "below",
			"beside", "besides", "best", "better", "between", "beyond", "both",
			"brief", "but", "by", "c", "came", "can", "cannot", "cant",
			"can't", "caption", "cause", "causes", "certain", "certainly",
			"changes", "clearly", "c'mon", "co", "co.", "com", "come", "comes",
			"concerning", "consequently", "consider", "considering", "contain",
			"containing", "contains", "corresponding", "could", "couldn't",
			"course", "c's", "currently", "d", "dare", "daren't", "definitely",
			"described", "despite", "did", "didn't", "different", "directly",
			"do", "does", "doesn't", "doing", "done", "don't", "down",
			"downwards", "during", "e", "each", "edu", "eg", "eight", "eighty",
			"either", "else", "elsewhere", "end", "ending", "enough",
			"entirely", "especially", "et", "etc", "even", "ever", "evermore",
			"every", "everybody", "everyone", "everything", "everywhere", "ex",
			"exactly", "example", "except", "f", "fairly", "far", "farther",
			"few", "fewer", "fifth", "first", "five", "followed", "following",
			"follows", "for", "forever", "former", "formerly", "forth",
			"forward", "found", "four", "from", "further", "furthermore", "g",
			"get", "gets", "getting", "given", "gives", "great", "go", "goes",
			"going", "gone", "got", "gotten", "greetings", "h", "had",
			"hadn't", "half", "happens", "hardly", "has", "hasn't", "have",
			"haven't", "having", "he", "he'd", "he'll", "hello", "help",
			"hence", "her", "here", "hereafter", "hereby", "herein", "here's",
			"hereupon", "hers", "herself", "he's", "hi", "him", "himself",
			"his", "hither", "hopefully", "how", "howbeit", "however",
			"hundred", "i", "i'd", "ie", "if", "ignored", "i'll", "i'm",
			"immediate", "in", "inasmuch", "inc", "inc.", "indeed", "indicate",
			"indicated", "indicates", "inner", "inside", "insofar", "instead",
			"into", "inward", "is", "isn't", "it", "it'd", "it'll", "its",
			"it's", "itself", "i've", "j", "just", "k", "keep", "keeps",
			"kept", "know", "known", "knows", "l", "last", "lately", "later",
			"latter", "latterly", "least", "less", "lest", "let", "let's",
			"like", "liked", "likely", "likewise", "little", "look", "looking",
			"looks", "low", "lower", "ltd", "m", "made", "mainly", "make",
			"makes", "many", "may", "maybe", "mayn't", "me", "mean",
			"meantime", "meanwhile", "merely", "might", "mightn't", "mine",
			"minus", "miss", "more", "moreover", "most", "mostly", "mr", "mrs",
			"much", "must", "mustn't", "my", "myself", "n", "name", "namely",
			"nd", "near", "nearly", "necessary", "need", "needn't", "needs",
			"neither", "never", "neverf", "neverless", "nevertheless", "new",
			"next", "nine", "ninety", "no", "nobody", "non", "none",
			"nonetheless", "noone", "no-one", "nor", "normally", "not",
			"nothing", "notwithstanding", "novel", "now", "nowhere", "o",
			"obviously", "of", "off", "often", "oh", "ok", "okay", "old", "on",
			"once", "one", "ones", "one's", "only", "onto", "opposite", "or",
			"other", "others", "otherwise", "ought", "oughtn't", "our", "ours",
			"ourselves", "out", "outside", "over", "overall", "own", "p",
			"particular", "particularly", "past", "per", "perhaps", "placed",
			"please", "plus", "possible", "presumably", "probably", "provided",
			"provides", "q", "que", "quite", "qv", "r", "rather", "rd", "re",
			"really", "reasonably", "recent", "recently", "regarding",
			"regardless", "regards", "relatively", "respectively", "right",
			"round", "rt", "s", "said", "same", "saw", "say", "saying", "says",
			"second", "secondly", "see", "seeing", "seem", "seemed", "seeming",
			"seems", "seen", "self", "selves", "sensible", "sent", "serious",
			"seriously", "seven", "several", "shall", "shan't", "she", "she'd",
			"she'll", "she's", "should", "shouldn't", "since", "six", "so",
			"some", "somebody", "someday", "somehow", "someone", "something",
			"sometime", "sometimes", "somewhat", "somewhere", "soon", "sorry",
			"specified", "specify", "specifying", "still", "sub", "such",
			"sup", "sure", "t", "take", "taken", "taking", "tell", "tends",
			"th", "than", "thank", "thanks", "thanx", "that", "that'll",
			"thats", "that's", "that've", "the", "their", "theirs", "them",
			"themselves", "then", "thence", "there", "thereafter", "thereby",
			"there'd", "therefore", "therein", "there'll", "there're",
			"theres", "there's", "thereupon", "there've", "these", "they",
			"they'd", "they'll", "they're", "they've", "thing", "things",
			"think", "third", "thirty", "this", "thorough", "thoroughly",
			"those", "though", "three", "through", "throughout", "thru",
			"thus", "till", "to", "together", "too", "took", "toward",
			"towards", "tried", "tries", "truly", "try", "trying", "t's",
			"twice", "two", "u", "un", "under", "underneath", "undoing",
			"unfortunately", "unless", "unlike", "unlikely", "until", "unto",
			"up", "upon", "upwards", "us", "use", "used", "useful", "uses",
			"using", "usually", "v", "value", "various", "versus", "very",
			"via", "viz", "vs", "w", "want", "wants", "was", "wasn't", "way",
			"we", "we'd", "welcome", "well", "we'll", "went", "were", "we're",
			"weren't", "we've", "what", "whatever", "what'll", "what's",
			"what've", "when", "whence", "whenever", "where", "whereafter",
			"whereas", "whereby", "wherein", "where's", "whereupon",
			"wherever", "whether", "which", "whichever", "while", "whilst",
			"whither", "who", "who'd", "whoever", "whole", "who'll", "whom",
			"whomever", "who's", "whose", "why", "will", "willing", "wish",
			"with", "within", "without", "wonder", "won't", "would",
			"wouldn't", "x", "y", "yes", "yet", "you", "you'd", "you'll",
			"your", "you're", "yours", "yourself", "yourselves", "you've", "z",
			"zero");

	/** Data access object */
	private TweetDataSource dao;
	
	/** The word cloud view */
	private TagCloudView mTagCloudView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Initialize data source
		dao = new TweetDataSource(this);

		// Step0: to get a full-screen View:
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// Step1: get screen resolution:
		Display display = getWindowManager().getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();

		// Step2: create the required TagList:
		// notice: All tags must have unique text field
		// if not, only the first occurrence will be added and the rest will be
		// ignored
		List<Tag> myTagList = createTags();

		// Step3: create our TagCloudview and set it as the content of our
		// MainActivity
		mTagCloudView = new TagCloudView(this, width, height, myTagList);

		setContentView(mTagCloudView);
		mTagCloudView.requestFocus();
		mTagCloudView.setFocusableInTouchMode(true);

		// Step4: (Optional) adding a new tag and resetting the whole 3D
		// TagCloud
		// you can also add individual tags later:
		// mTagCloudView.addTag(new Tag("AAA", 5, "http://www.aaa.com"));
		// .... (several other tasg can be added similarly )
		// indivual tags will be placed along with the previous tags without
		// moving
		// old ones around. Thus, after adding many individual tags, the
		// TagCloud
		// might not be evenly distributed anymore. reset() re-positions all the
		// tags:
		// mTagCloudView.reset();

		// Step5: (Optional) Replacing one of the previous tags with a new tag
		// you have to create a newTag and pass it in together
		// with the Text of the existing Tag that you want to replace
		// Tag newTag=new Tag("Illinois", 9, "http://www.illinois.com");
		// in order to replace previous tag with text "Google" with this new
		// one:
		// boolean result=mTagCloudView.Replace(newTag, "google");
		// result will be true if "google" was found and replaced. else result
		// is false
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	/**
	 * Read from database and create a list of words to show in the cloud
	 * 
	 * @return List containing words to show in the cloud
	 */
	private List<Tag> createTags() {

		// Obtain tweets
		dao.open();
		List<Tweet> tweets = dao.getAllTweets();
		dao.close();

		// Find most popular words
		Map<String, Integer> wordMap = new HashMap<String, Integer>();
		String tweetText;
		for (Tweet tweet : tweets) {
			tweetText = tweet.getTweet().toLowerCase();
			List<String> words = new ArrayList<String>(Arrays.asList(tweetText
					.split(" ")));

			for (String word : words) {
				if (!word.matches(".*[a-zA-Z]+.*")
						|| ignoreWords.contains(word)) {
					continue;
				}
				if (wordMap.containsKey(word)) {
					wordMap.put(word, wordMap.get(word) + 1);
				} else {
					wordMap.put(word, 1);
				}
			}
		}

		// create the list of tags with popularity values and related url
		List<Tag> tempList = new ArrayList<Tag>();
		Map<String, Integer> sortedWordMap = this.sortByValue(wordMap);
		Iterator it = sortedWordMap.entrySet().iterator();
		int count = 0;
		while (it.hasNext() && count < WORDS_IN_CLOUD) {
			Map.Entry pairs = (Map.Entry) it.next();
			String link = null;
			if (((String) pairs.getKey()).startsWith("#")) {
				link = "https://mobile.twitter.com/search/"
						+ ((String) pairs.getKey()).substring(1).replaceAll(
								" ", "+");
			} else if (((String) pairs.getKey()).startsWith("http")) {
				link = (String) pairs.getKey();
			} else {
				link = "https://mobile.twitter.com/search/"
						+ ((String) pairs.getKey()).replaceAll(" ", "+");
			}
			tempList.add(new Tag((String) pairs.getKey(), ((Integer) pairs
					.getValue()).intValue() * 2 + 5, link));
			count++;
		}
		return tempList;
	}

	/**
	 * Sort the contents of the Map, where the key is the word and the value is
	 * the number of words. Most popular words will be at the beginning
	 * (descending sort).
	 * 
	 * @param map
	 *            The unsorted map
	 * @return The sorted map
	 */
	public Map<String, Integer> sortByValue(Map<String, Integer> map) {
		List<Map.Entry<String, Integer>> list = new LinkedList<Map.Entry<String, Integer>>(
				map.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {

			public int compare(Map.Entry<String, Integer> m1,
					Map.Entry<String, Integer> m2) {
				return (m2.getValue()).compareTo(m1.getValue());
			}
		});

		Map<String, Integer> result = new LinkedHashMap<String, Integer>();
		for (Map.Entry<String, Integer> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}	
}