package rehab.reality.alsuti;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.liquidplayer.javascript.JSException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class UploadActivity extends AppCompatActivity {

    SharedPreferences prefs;
    String waitingFileName;
    Encrypter encrypter;
    boolean encrypted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        View decorView = getWindow().getDecorView();

        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        setContentView(R.layout.activity_upload);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        String action = intent.getAction();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (intent.ACTION_SEND.equals(action) && extras.containsKey(Intent.EXTRA_STREAM)) {
            Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
            waitingFileName = parseUriToFilename(uri);
        }
    }

    public void hideButtons() {
        EditText progressBox = (EditText) findViewById(R.id.editText);
        Button rawUploadButton = (Button) findViewById(R.id.rawUploadButton);
        Button encUploadButton = (Button) findViewById(R.id.encryptUploadButton);

        rawUploadButton.setVisibility(View.INVISIBLE);
        encUploadButton.setVisibility(View.INVISIBLE);
        progressBox.setVisibility(View.VISIBLE);
    }

    public void onEncUploadBtn(View v) throws IOException, JSException {
        hideButtons();

        EditText progressBox = (EditText) findViewById(R.id.editText);
        String password = RandomStringUtils.randomAlphanumeric(10);

        progressBox.setText("Encrypting");
        encrypted = true;

        try {
            encrypter = new Encrypter(this, getBaseContext(), waitingFileName, password);
            encrypter.runEncryption();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "unable to load things", Toast.LENGTH_LONG).show();
        }
    }

    public void onRawUploadBtn(View v) throws IOException {
        hideButtons();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
        } else {
            postFile();
        }
    }

    public void doneEncryption(final String fName, final String password) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            EditText progressBox = (EditText) findViewById(R.id.editText);
            waitingFileName = fName; // move this to an argument
            progressBox.setText("Uploading...");
            postFile(password);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    postFile();
                } else {

                }
                return;
            }
        }
    }

    // Taken from http://twigstechtips.blogspot.co.uk/2011/10/android-sharing-images-or-files-through.html
    public String parseUriToFilename(Uri uri) {
        String selectedImagePath = null;
        String filemanagerPath = uri.getPath();

        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = managedQuery(uri, projection, null, null, null);

        if (cursor != null) {
            // Here you will get a null pointer if cursor is null
            // This can be if you used OI file manager for picking the media
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            selectedImagePath = cursor.getString(column_index);
        }

        if (selectedImagePath != null) {
            return selectedImagePath;
        } else if (filemanagerPath != null) {
            return filemanagerPath;
        }
        return null;
    }

    private void postFile() {
        postFile("");
    }

    private void postFile(final String password) {
        AsyncHttpClient client = new AsyncHttpClient();

        if(prefs.getBoolean("useTor", false)) {
            client.setProxy("localhost", 8118);
        }

        RequestParams params = new RequestParams();

        client.setBasicAuth(this.prefs.getString("apiUsername", ""), this.prefs.getString("apiPassword", ""));
        client.addHeader("api", "true");
        try {
            params.put("file", new File(waitingFileName));
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Error: Cannot find file", Toast.LENGTH_LONG).show();
        }

        if(encrypted == true) {
            params.put("encrypted", true);
        }

        client.setConnectTimeout(60000);
        client.setResponseTimeout(60000);
        client.setTimeout(60000);

        client.post(prefs.getString("apiEndpoint", "") + "/upload", params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody) {
                EditText linkBox = (EditText) findViewById(R.id.editText);

                if(responseBody != null) {
                    try {
                        JSONObject response = new JSONObject(new String(responseBody));

                        if(response.getBoolean("error") != false) {
                            linkBox.setText("Error: " + response.getString("error"));
                        } else {
                            String link = prefs.getString("apiEndpoint", "") + "/" + response.getString("fileName");
                            if (password != "") {
                                link += "#" + password;
                            }
                            System.out.println(password);

                            linkBox.setText(link);

                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText(link, link);
                            clipboard.setPrimaryClip(clip);

                            Toast.makeText(getBaseContext(), "Link copied to clipboard", Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        linkBox.setText("Failed");
                        Toast.makeText(getBaseContext(), "Failed to parse response", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody, Throwable error) {
                if (responseBody != null) {
                    try {
                        Toast.makeText(getBaseContext(), new String(responseBody, "UTF-8"), Toast.LENGTH_LONG).show();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(getBaseContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }
}