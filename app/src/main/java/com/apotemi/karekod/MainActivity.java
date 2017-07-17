package com.apotemi.karekod;

import android.Manifest;
import android.Manifest.permission;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
/*import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;*/
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;
import com.apotemi.karekod.karekod.BarcodeCaptureActivity;
import java.io.File;
import java.io.StringReader;

import pl.droidsonroids.gif.GifTextView;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;


import static android.R.attr.name;


//public class MainActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener {
public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int BARCODE_READER_REQUEST_CODE = 1;
    private static final String TAG = "Main";
    private FloatingActionButton fab;
    private BroadcastReceiver downloadCompleteReceiver;
    private DownloadManager dm;
    private long enqueue;
    private String fileName;
//    private PDFView pdfView;
    private GifTextView loadingGif;
    private Tracker mTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Obtain the shared Tracker instance.
        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        mTracker = application.getDefaultTracker();
        setContentView(R.layout.activity_main);
//        pdfView = (PDFView) findViewById(R.id.pdfView);
        loadingGif = (GifTextView) findViewById(R.id.downloading);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                loadingGif.setVisibility(View.VISIBLE);
                Intent intent = new Intent(getApplicationContext(), BarcodeCaptureActivity.class);
                startActivityForResult(intent, BARCODE_READER_REQUEST_CODE);
            }
        });

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,permission.INTERNET}, 1);

        downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                try {
                    if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById(enqueue);
                        Cursor c = dm.query(query);
                        if (c.moveToFirst()) {
                            int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex) && !fileName.equals("")) {
                                loadingGif.setVisibility(View.INVISIBLE);
                                fromFileOpenNative( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + fileName.replace("%20"," "));

                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        registerReceiver(downloadCompleteReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean shownBefore = prefs.getBoolean(getString(R.string.pref_shown_before), false);
        if(!shownBefore) {
            showInfo();
        }
        else {
            Intent intent = new Intent(getApplicationContext(), BarcodeCaptureActivity.class);
            startActivityForResult(intent, BARCODE_READER_REQUEST_CODE);
        }

    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first
        Log.i(TAG, "Setting screen name: " + name);
        mTracker.setScreenName("Image~" + name);
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BARCODE_READER_REQUEST_CODE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    try {
                        fileName = downloadFile(barcode.displayValue);
                    } catch (Exception e) {
                        Log.e(TAG,"Error downloading file");
                    }
                }
            }
            else Log.e(LOG_TAG, String.format(getString(R.string.barcode_error_format),CommonStatusCodes.getStatusCodeString(resultCode)));
        }
        else super.onActivityResult(requestCode, resultCode, data);
    }

/*    private void fromFile(String path) {
        try {
            pdfView.fromFile(new File(path))
                    .onLoad(this)
                    .onPageChange(this)
                    .enableAnnotationRendering(true)
                    .load();
            pdfView.useBestQuality(true);
        } catch (Exception e) {
            Log.e(TAG,"Error opening PDF file" + e.getStackTrace());
        }
    }*/


    private void fromFileOpenNative(String path) {
        Uri uri = Uri.fromFile(new File(path));
        Intent pdfOpenintent = new Intent(Intent.ACTION_VIEW);
        pdfOpenintent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        pdfOpenintent.setDataAndType(uri, "application/pdf");
        try {
            startActivity(pdfOpenintent);
        }
        catch (ActivityNotFoundException e) {
            Intent intent = new Intent(getApplicationContext(), BarcodeCaptureActivity.class);
            startActivityForResult(intent, BARCODE_READER_REQUEST_CODE);
        }
    }

    private String  downloadFile(String url){
        DownloadManager.Request request = null;
        String fileName = "";
        try {
            request = new DownloadManager.Request(Uri.parse(url));
            fileName = url.substring(url.lastIndexOf('/') + 1);
            if (!(url.endsWith(".pdf")&&url.contains("apotemi"))){
                showAlert();
                loadingGif.setVisibility(View.INVISIBLE);
                return "";
            }
            request.setDescription("Apotemi");
            request.setTitle(fileName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            }

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert();
            loadingGif.setVisibility(View.INVISIBLE);
        }


        if(isStoragePermissionGranted() && isInternetPermissionGranted()){
            try {

                dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                enqueue = dm.enqueue(request);
            } catch (Exception e) {
                Log.e(TAG,"Problem occured downloading file: " + e.getStackTrace());
            }
        }
        return fileName;
    }

    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    public  boolean isInternetPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(permission.INTERNET)== PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{permission.INTERNET}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    private void showDialog(String title, String message){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                this);

        alertDialogBuilder.setTitle(title);
        alertDialogBuilder
                .setMessage(message)
                .setCancelable(false)
                .setNeutralButton("Tamam",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        Intent intent = new Intent(getApplicationContext(), BarcodeCaptureActivity.class);
                        startActivityForResult(intent, BARCODE_READER_REQUEST_CODE);
                    }
                })
        ;

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void showAlert(){
        showDialog("Karekod Hatası","Geçerli bir bağlantı değil. Lütfen karekodunuzu kontrol ediniz.");
    }

    public void showInfo(){
        showDialog("Apotemi Karekod","Cihazınızın kamerasını karekoda yaklaştırınız.");
    }

/*    @Override
    public void loadComplete(int nbPages) {
        // left blank intentionally
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        // left blank intentionally
    }*/
}
