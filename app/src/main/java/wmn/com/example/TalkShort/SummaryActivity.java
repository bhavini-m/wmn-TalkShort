package wmn.com.example.TalkShort;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;

public class SummaryActivity extends AppCompatActivity {
    private final int MAX_NUM_SUMMARIES = 20;
    private final int LINES_PER_SUMMARY = 4;
    //private int flag = 0;

   // String file=getIntent().getStringExtra("filename");
   private static final String TAG = "MainActivity";

   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       setContentView(R.layout.activity_summary);
       Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
       setSupportActionBar(toolbar);

       //Allow people to use keyboard search key to initialize the summarization Activity
       final EditText urlInput = (EditText) findViewById(R.id.url_bar);
       urlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
           @Override
           public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
               if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                   String url = urlInput.getText().toString();
                   Intent articleIntent = new Intent(SummaryActivity.this, ArticleActivity.class);
                   articleIntent.putExtra("url", url);
                   startActivity(articleIntent);
                   return true;
               }
               return false;
           }
       });

       final EditText fileInput = (EditText) findViewById(R.id.file_bar);
       fileInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
           @Override
           public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
               if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                   String new_url = fileInput.getText().toString();
                   File file = new File(getExternalFilesDir(null),new_url);

                  String fn_path=file.getAbsolutePath();
                   Intent articleIntent = new Intent(SummaryActivity.this, ArticleActivity.class);
                   articleIntent.putExtra("fn_path", fn_path);
                   Log.v(TAG, "index=" + fn_path);
                   startActivity(articleIntent);
                   return true;
               }
               return false;
           }
       });

       try {
            loadCachedSummaries();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads saved summaries (summaries that were completed succesfully)
     * @throws IOException
     */
    private void loadCachedSummaries() throws IOException {
        File cachedSummaries = new File(getCacheDir(), "summaries.txt");

        //Count number of lines
        LineNumberReader  lnr = new LineNumberReader(new FileReader(cachedSummaries));
        lnr.skip(Long.MAX_VALUE);
        int numLines = lnr.getLineNumber() + 1; //Add 1 because line index starts at 0
        // Finally, the LineNumberReader object should be closed to prevent resource leak
        lnr.close();

        //Create ArrayList to hold summaries
        ArrayList<String[]> summaries = new ArrayList<>();

        //Maintain cache to last MAX_NUM_SUMMARIES summaries and add those to ArrayList
        File tempFile = new File(getCacheDir(), "myTempFile.txt");
        BufferedReader reader = new BufferedReader(new FileReader(cachedSummaries));
        PrintWriter writer = new PrintWriter(new FileWriter(tempFile));

        String currentLine;
        int currentLineNum = 1;
        int i = 0;
        String[] currentStringArray = new String[4];
        while ((currentLine = reader.readLine()) != null) {
            if(numLines - currentLineNum <= MAX_NUM_SUMMARIES*LINES_PER_SUMMARY){
                writer.println(currentLine);
                currentStringArray[i%LINES_PER_SUMMARY] = currentLine;
                if(i%LINES_PER_SUMMARY == LINES_PER_SUMMARY-1){
                    summaries.add(currentStringArray);
                    currentStringArray = new String[4];
                }
                i++;
            }
            currentLineNum+=1;
        }
        writer.close();
        reader.close();
        boolean successful = tempFile.renameTo(cachedSummaries);

        //Make newest lines first
        Collections.reverse(summaries);

        //Populate the TextViews
        ArrayAdapter<String[]> summaryListViewAdapter = new ArrayAdapter<String[]>(this, R.layout.list_item_summary, R.id.list_item_summary_textview, summaries){
            @Override
            public View getView (int position, View convertView, ViewGroup parent){
                if(convertView == null) {
                    LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = vi.inflate(R.layout.list_item_summary, null);
                }
                TextView tv = (TextView)convertView.findViewById(R.id.list_item_summary_textview);
                String[] summaryLines = getItem(position);
                //Build the inner text of the TextView
                SpannableStringBuilder summaryItemText  = new SpannableStringBuilder();
                for(int i = 0 ; i < summaryLines.length; i++){
                    switch (i){
                        case 0: {
                            summaryItemText.append(summaryLines[i] + "\n");
                            summaryItemText.setSpan(new android.text.style.StyleSpan(Typeface.BOLD_ITALIC), 0, summaryItemText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } break;

                        case 1: {
                            summaryItemText.append(summaryLines[i] + "\n");
                        } break;

                        case 2: {
                            summaryItemText.append(summaryLines[i]);
                            summaryItemText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), summaryItemText.length() - summaryLines[i].length(), summaryItemText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } break;

                    }
                }
                //Set the TextView to navigate back to the article on-click
                final String url = summaryLines[LINES_PER_SUMMARY-1];
                tv.setText(summaryItemText);
                tv.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        startArticleIntent(url);
                    }
                });
                return convertView;
            }
        };
        ListView summaryListView = (ListView)findViewById(R.id.listView);
        summaryListView.setAdapter(summaryListViewAdapter);
    }


    public void handleButtonClick(View view){
        EditText urlInput = (EditText)findViewById(R.id.url_bar);
        String url = urlInput.getText().toString();
        startArticleIntent(url);
    }

    public void fileButtonClick(View view){
        EditText fn = (EditText)findViewById(R.id.file_bar);
        String fname = fn.getText().toString();
        startArticleIntent(fname);
    }

    private void startArticleIntent(String url){
        Intent articleIntent = new Intent(this, ArticleActivity.class);
        articleIntent.putExtra("url", url);
        startActivity(articleIntent);

    }

}
