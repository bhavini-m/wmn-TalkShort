package wmn.com.example.TalkShort;

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView txvResult;
    private EditText editText;
    private ArrayList<String> result;
    private String filename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txvResult = (TextView) findViewById(R.id.txvResult);
        editText = (EditText) findViewById(R.id.editText);
    }

    public void getSpeechInput(View view) {

        filename = editText.getText().toString();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        //intent.putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR");
        //intent.putExtra("android.speech.extra.GET_AUDIO", true);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, 10);
        } else {
            Toast.makeText(this, "Your Device Don't Support Speech Input", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 10:
                if (resultCode == RESULT_OK && data != null) {
                    result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    //txvResult.setText(result.get(0));
                }
                break;
        }
        // the resulting text is in the getExtras:
		/*Bundle bundle = data.getExtras();
		ArrayList<String> matches = bundle.getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
		// the recording url is in getData:
		Uri audioUri = data.getData();
		ContentResolver contentResolver = getContentResolver();
		try {
			InputStream filestream = contentResolver.openInputStream(audioUri);
			//TODO: read audio file from inputstream
			byte[] buffer = new byte[filestream.available()];
			filestream.read(buffer);
			File audioFile = new File(this.getExternalFilesDir(null), "audio.wav");
			OutputStream outStream = new FileOutputStream(audioFile);
			outStream.write(buffer);
		} catch (Exception e){

		}*/
        saveFile();
    }

    private void saveFile()
    {
        File file = new File(getExternalFilesDir(null), "timestamp.txt");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar c = Calendar.getInstance();
        String currentTime = simpleDateFormat.format(c.getTime());
        try {
            // Creates a file in the primary external storage space of the current application.
            // If the file does not exists, it is created.
            File testFile = new File(this.getExternalFilesDir(null), filename);
            if (!testFile.exists())
                testFile.createNewFile();
            // Adds a line to the file
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(testFile, false
                    /*append*/));
            bufferedWriter.write(currentTime + "\n" + result.get(0));
            bufferedWriter.close();
            Toast.makeText(getBaseContext(),"file saved",Toast.LENGTH_SHORT).show();
            MediaScannerConnection.scanFile(this,
                    new String[]{testFile.toString()},
                    null,
                    null);
            //startActivity(new Intent(DisplayInfo.this, FileReader.class));
        } catch (IOException e) {
            Log.e("ReadWriteFile", "Unable to write to the TestFile.txt file.");
        }




    }



    public void getSummary(View view) {

        Intent downloadIntent = new Intent(this, SummaryActivity.class);
        startActivity(downloadIntent);
    }
}
