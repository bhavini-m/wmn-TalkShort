package wmn.com.example.TalkShort;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.apmem.tools.layouts.FlowLayout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import textrank.TextRank;

public class ArticleActivity extends AppCompatActivity {
    private final String TAG = "ArticleActivity";
    private WebView webview;
    private TextView authorText;
    private TextView summaryText;
    private ProgressBar progressBar;
    FlowLayout keywordsContainer;
    private Document document;
    private String summary;
    private String author;
    private String headline;
    private String[] keywords;
    private String selectedKeyword;
    private String url;
    private static TextRank tr;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        webview = (WebView)findViewById(R.id.webView);
        summaryText = (TextView)findViewById(R.id.summary);
        authorText = (TextView) findViewById(R.id.authors);
        keywordsContainer = (FlowLayout)findViewById(R.id.keywords);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        initializeTextRank();
        initializeWebView();
    }


    private void runAnalysis(){
        Element article = getArticleFromDocument();
        if(article!=null){
            extractAuthor(article);
            extractHeadline(article);
            summarizeArticle(article);
        }
        else{
            summary = "No article could be extracted.";
            author = "";
        }

        progressBar.setVisibility(View.GONE);
        setProgress(100);

        //Make the results seen in the slide-up layout
        showResults();
    }


    private void showResults(){
        //Show results in-app
        summaryText.setText(summary);
        authorText.setText(author);
        keywordsContainer.removeAllViews();
        if(keywords != null) {
            for (final String s : keywords) {
                TextView newButton = new Button(this);
                newButton.setText(s);
                newButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        ArticleActivity.this.selectedKeyword = s;
                        DialogFragment newFragment = new ResearchDialogFragment();
                        newFragment.show(getFragmentManager(), "research");
                    }
                });
                newButton.getBackground().setColorFilter(getResources().getColor(R.color.colorAccent), PorterDuff.Mode.MULTIPLY);
                keywordsContainer.addView(newButton);
            }
        }
    }



    /**
     * Check if TextRank instance exists. If not, create it.
     */
    private void initializeTextRank(){
        //Open raw resources to initialize OpenNLP tools for TextRank
        if(tr==null){
            InputStream sent = getResources().openRawResource(R.raw.en_sent);
            InputStream token = getResources().openRawResource(R.raw.en_token);
            InputStream stop = getResources().openRawResource(R.raw.stopwords);
            InputStream exstop = getResources().openRawResource(R.raw.extended_stopwords);
            try {
                tr = new TextRank(sent, token, stop, exstop);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Initialize the WebView with the url passed to the ArticleActivity
     */
    private void initializeWebView(){
        Intent intent = getIntent();
        Uri data = intent.getData();
        String url;
        //If there's data, the intent was from outside of the app
        if(data!=null) {
            String host = data.getHost();
            String path = data.getPath();
            url = host+path;
        }
        //Otherwise it was initialized from the MainActivity
        else {
            url = intent.getStringExtra("url");
        }
        //In case users didn't include http:// or https://
        //if(url.indexOf("http://") == -1 && url.indexOf("https://") == -1){
          //  url = "http://"+url;
        //}
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon){
                progressBar.setVisibility(View.VISIBLE);
                setProgress(0);
                FetchPageTask fetch = new FetchPageTask();
                ArticleActivity.this.url = url;
                fetch.execute(url);
            }
            @Override

            public void onPageFinished(WebView view, String url) {
            }

        });
        webview.loadUrl(url);
    }


    private class FetchPageTask extends AsyncTask<String, Void, Document> {

        @Override
        protected Document doInBackground(String... url) {
            Document doc = null;
            try {
                Log.v("JSoup", "Attempting to connect to "+ url[0]);
                doc = Jsoup.connect(url[0]).get();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return doc;
        }

        protected void onPostExecute(Document doc) {
            //New page, clear out past keywords
            keywords=null;
            //Call jsoupAnalysis
            if(doc!=null) {
                Log.v(TAG, "Loaded the page.");
                ArticleActivity.this.document = doc;
                //Only run analysis on successful document load
                runAnalysis();
            }
            else{
                Log.v(TAG, "Couldn't load page.");
                summary = "Page couldn't be loaded for info extraction.";
            }
        }
    }


    private Element getArticleFromDocument(){
        //Start specific. Some websites have smaller psuedo articles at the top of the page
        Element article = document.select("main").select("article").first();
        if(article == null)
            article = document.select("article").first();
        if(article == null)
            article = document.getElementsByAttributeValueContaining("class", "article").first();
        if(article == null)
            article = document.getElementsByAttributeValueContaining("class", "story").first();
        return article;
    }

    /**
     * Find author text based on common class names for authors
     */
    private void extractAuthor(Element article){
        Element authorContainer = article.getElementsByAttributeValueContaining("class", "byline").first();
        if(authorContainer == null) {
            authorContainer = article.getElementsByAttributeValueContaining("class", "author").first();
        }
        if(authorContainer == null) {
            authorContainer = document.getElementsByAttributeValueContaining("class", "byline").first();
        }
        if(authorContainer == null) {
            authorContainer = document.getElementsByAttributeValueContaining("class", "author").first();
        }
        if(authorContainer!=null) {
            Log.v("Author Information", authorContainer.text());
            author = authorContainer.text();
        }
        else {
            Log.v("Author Information", "Couldn't extract author data");
            author = "Couldn't extract author data.";
        }
    }


    /**
     * Find headline text based on common class names for headlines
     */
    private void extractHeadline(Element article){
        Element headlineContainer = article.getElementsByAttributeValueContaining("class", "headline").first();
        if(headlineContainer == null) {
            headlineContainer = article.getElementsByAttributeValueContaining("class", "title").first();
        }
        if(headlineContainer == null) {
            headlineContainer = document.getElementsByAttributeValueContaining("class", "headline").first();
        }
        if(headlineContainer == null) {
            headlineContainer = document.getElementsByAttributeValueContaining("class", "title").first();
        }
        if(headlineContainer!=null) {
            Log.v("Headline Information", headlineContainer.text());
            headline = headlineContainer.text();
        }
        else {
            Log.v("Headline Information", "Couldn't extract headline data");
            headline = "Couldn't extract headline data";
        }
    }

    private void summarizeArticle(Element article){
        String articleText = createArticleText(article);
        TextView summaryText = (TextView)findViewById(R.id.summary);
        if(articleText != null && articleText != ""){
            ArrayList<TextRank.SentenceVertex> rankedSentences = tr.sentenceExtraction(articleText);
            ArrayList<TextRank.TokenVertex> rankedTokens = tr.keywordExtraction(articleText);
            //Get summary
            summary = rankedSentences.get(0).getSentence();
            for(int i = 0; i < 5 && i < rankedSentences.size(); i ++){
                Log.v("Ranked Sentence #" + (i+1), rankedSentences.get(i).getSentence());
            }

            //Get best 8 keywords
            keywords = new String[8];
            for(int i = 0; i < rankedTokens.size() && i < keywords.length; i++){
                TextRank.TokenVertex tv = rankedTokens.get(i);
                keywords[i] = tv.getToken();
            }

            //Save to cache
            try {
                saveSummaryToCache();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            summary = "Unable to process this article";
        }
    }


    private void saveSummaryToCache() throws IOException {
        File cachedSummaries= new File(getCacheDir(), "summaries.txt");
        //Open PrintWriter in append mode.
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(cachedSummaries, true)));
        pw.println(headline);
        pw.println(summary);
        pw.println(author);
        pw.println(url);
        pw.close();
    }


    private String createArticleText(Element article){
        String articleText = "";
        Elements articleParagraphs = article.select("p");
        //If the article <p> elements don't make up most of the text in the article
        if((double)article.text().length()*0.4 >= articleParagraphs.text().length()){
            //Hope that they're under the tag paragraph (prime example, CNN, who just changed their article HTML layout this week)
            articleParagraphs = article.getElementsByAttributeValueContaining("class", "paragraph");
        }
        //Process each paragraph
        for(int i = 0; i < articleParagraphs.size(); i++){
            Element paragraph = articleParagraphs.get(i);
            //End all sentences with periods so as to ensure sentence separation
            //Otherwise, sentences will appear concatenated
            String pText = paragraph.text();
            if(pText.length()>0 && pText.charAt(pText.length()-1) != ' '){
                if(pText.charAt(pText.length()-1)== '.' || pText.charAt(pText.length()-1)=='"')
                    pText += " ";
                else
                    pText += ". ";
            }
            articleText += pText;
        }
        return articleText;
    }

    /**
     * The AlertDialog that allows one to research keywords of a summarized article
     */
    @SuppressLint("ValidFragment")
    private class ResearchDialogFragment extends DialogFragment {
        private int selectedItem;
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            builder.setTitle(R.string.research_dialog_title)
                    .setSingleChoiceItems(R.array.research_options, 0,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    selectedItem = which;
                                }
                            })
                    .setPositiveButton(R.string.research_dialog_positive, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            switch (selectedItem){
                                case 0: {
                                    Intent i = new Intent(Intent.ACTION_VIEW);
                                    i.setData(Uri.parse("https://www.google.com/search?aq=f&hl=en&gl=us&tbm=nws&btnmeta_news_search=1&q=" + ArticleActivity.this.selectedKeyword));
                                    startActivity(i);
                                } break;

                                case 1: {
                                    Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
                                    i.putExtra(SearchManager.QUERY, ArticleActivity.this.selectedKeyword);
                                    startActivity(i);
                                } break;

                                case 2: {
                                    Intent i = new Intent(Intent.ACTION_VIEW);
                                    i.setData(Uri.parse("https://en.wikipedia.org/wiki/" + ArticleActivity.this.selectedKeyword));
                                    startActivity(i);
                                } break;

                                case 3: {
                                    Intent i = new Intent(Intent.ACTION_VIEW);
                                    i.setData(Uri.parse("http://www.imdb.com/find?q=" + ArticleActivity.this.selectedKeyword + "&s=all"));
                                    startActivity(i);
                                } break;
                            }
                        }
                    })
                    .setNegativeButton(R.string.research_dialog_negative, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}