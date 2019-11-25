package com.yjhs.documentviewer;

import android.Manifest;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;


import com.tencent.smtt.sdk.WebView;
import com.yjhs.documentviewer.fileviewer.FileViewer;
import com.yjhs.documentviewer.fileviewer.tbs.TBSFileViewActivity;

import java.io.File;
import java.io.IOException;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import pub.devrel.easypermissions.PermissionRequest;



public class FileViewDemoMainActivity extends AppCompatActivity {

    private static final int RC_WRITE_STOREGE = 1;
    private static final String TAG = "TBSInit";
    private static final String TBS_ZIP_FILE_PATH = "/sdcard/app_tbs.zip";
    protected WebView tbsWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        super.setContentView(R.layout.activity_main);
        App.FILE_DIR = "/sdcard/Download/test/";
        initView();
        initWebView();
        requestPermissions();
    }

    private void requestPermissions() {
        String[] perms = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE
        };
        if (!EasyPermissions.hasPermissions(this, perms)) {
            EasyPermissions.requestPermissions(new PermissionRequest.Builder(FileViewDemoMainActivity.this, RC_WRITE_STOREGE, perms)
                            .setRationale("请求权限")
                            .setPositiveButtonText("确认")
                            .setNegativeButtonText("取消")
                            .build());
        } else {
//            RunnableUtils.executeOnWorkThread(() -> {
//                Log.d(TAG, "Start Copy TBS zip files");
//                boolean unzip = false;
//                try {
//                    unzip = ZipUtils.UnZipFolder(TBS_ZIP_FILE_PATH,
//                            getApplicationContext().getFilesDir().getParentFile().getAbsolutePath() + File.separator);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                Log.d(TAG, "End Copy TBS zip files: result = " + unzip);
//            });
        }
    }


    private void initWebView() {
        tbsWebView = (WebView) findViewById(R.id.tbs_webView);
        tbsWebView.loadUrl("file:///android_asset/test.html");
        tbsWebView.setDrawingCacheEnabled(true);
    }


    private void initView() {
        findViewById(R.id.btn_open_pdf_with_mupdf).setOnClickListener(view -> {
            String fileName = getFilePath("TestPDF.pdf");
            startMuPDFActivityWithExampleFile(fileName);
        });
        findViewById(R.id.btn_open_pdf_with_tbs).setOnClickListener((view) -> {
            openFileWithTbs(getFilePath("TestPDF.pdf"));
        });
        findViewById(R.id.btn_open_doc_with_tbs).setOnClickListener(view -> openFileWithTbs(getFilePath("TestDoc.doc")));
        findViewById(R.id.btn_open_ppt_with_tbs).setOnClickListener(view -> openFileWithTbs(getFilePath("TestPPT.ppt")));
        findViewById(R.id.btn_open_excel_with_tbs).setOnClickListener(view -> openFileWithTbs(getFilePath("TestExcel.xls")));
    }

    @NonNull
    private String getFilePath(String fileName) {

        return new File(App.FILE_DIR + fileName).getAbsolutePath();
    }

    private void openFileWithTbs(String filePath) {
        Log.d(TAG,"Open File: " + filePath);
        TBSFileViewActivity.viewFile(this, filePath);
    }

    public void startMuPDFActivityWithExampleFile(String fileName) {
        File file = new File(fileName);
        if (!file.isFile()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.fromFile(file);
        FileViewer.startMuPDFActivityByUri(this, uri);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @AfterPermissionGranted(RC_WRITE_STOREGE)
    private void methodRequiresTwoPermission() {
        String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            // ...
            try {
                FileUtils.copyAssetsDir(this, "test", App.FILE_DIR);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, "请求权限",
                    RC_WRITE_STOREGE, perms);
        }
    }



}
