/*
    This file is part of the Browser webview app.

    HHS Moodle WebApp is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    HHS Moodle WebApp is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with the Browser webview app.

    If not, see <http://www.gnu.org/licenses/>.
 */

package file_manager;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.Snackbar;
import com.yjhs.mupdf.R;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import helper.helper_main;
import com.artifex.mupdfdemo.MuPDFActivity;
import pub.devrel.easypermissions.EasyPermissions;
import pub.devrel.easypermissions.EasyPermissions.PermissionCallbacks;

import static android.content.ContentValues.TAG;
import static java.lang.String.valueOf;


public class Activity_files extends AppCompatActivity implements PermissionCallbacks {

    private ListView listView = null;
    private DbAdapter_Files db;
    private SimpleCursorAdapter adapter;
    private SharedPreferences sharedPref;

    private EditText filter;
    private RelativeLayout filter_layout;

    private String [] perms = {Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkPermission(perms,0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        }

//        PackageManager pm = getPackageManager();
//        boolean isInstalled = isPackageInstalled("de.baumann.pdfcreator", pm);
//
//        if (isInstalled) {
//            PackageManager p = getPackageManager();
//            p.setComponentEnabledSetting(getComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
//            Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage("de.baumann.pdfcreator");
//            LaunchIntent.setAction("pdf_openFolder");
//            startActivity(LaunchIntent);
//            finish();
//        }

        setContentView(R.layout.activity_file_manager);

        PreferenceManager.setDefaultValues(this, R.xml.user_settings, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.edit().putString("files_startFolder",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()).apply();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listView = (ListView)findViewById(R.id.listNotes);
        filter_layout = (RelativeLayout) findViewById(R.id.filter_layout);
        filter_layout.setVisibility(View.GONE);
        filter = (EditText) findViewById(R.id.myFilter);

        ImageButton ib_hideKeyboard =(ImageButton) findViewById(R.id.ib_hideKeyboard);
        ib_hideKeyboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                filter_layout.setVisibility(View.GONE);
                setFilesList();
            }
        });

        //calling Notes_DbAdapter
//        db = new DbAdapter_Files(this);
//        db.open();

        setTitle();
        setFilesList();
    }

    private boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void setTitle() {
        if (sharedPref.getString("sortDBF", "title").equals("title")) {
            setTitle(getString(R.string.choose_titleMain) + " | " + getString(R.string.sort_title));
        } else if (sharedPref.getString("sortDBF", "title").equals("file_ext")) {
            setTitle(getString(R.string.choose_titleMain) + " | " + getString(R.string.sort_extension));
        } else {
            setTitle(getString(R.string.choose_titleMain) + " | " + getString(R.string.sort_date));
        }
    }

    private void setFilesList() {

        deleteDatabase("files_DB_v01.db");

        String folder = sharedPref.getString("folder",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());

        File f = new File(sharedPref.getString("files_startFolder", folder));
        final File[] files = f.listFiles();

        if (files == null || files.length == 0) {
            Snackbar.make(listView, R.string.toast_files, Snackbar.LENGTH_LONG).show();
        } else {
            // looping through all items <item>
            for (File file : files) {

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                String file_Name = file.getName();
                String file_Size = getReadableFileSize(file.length());
                String file_date = formatter.format(new Date(file.lastModified()));
                String file_path = file.getAbsolutePath();

                String file_ext;
                if (file.isDirectory()) {
                    file_ext = ".";
                } else {
                    file_ext = file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf("."));
                }

                db.open();

                if (file_ext.equals(".") ||
                    file_ext.equals(".pdf") ||
                    file_ext.equals(".") ||
                    file_ext.equals(".jpg") ||
                    file_ext.equals(".JPG") ||
                    file_ext.equals(".jpeg") ||
                        file_ext.equals(".docx") ||
                        file_ext.equals(".xslx") ||
                    file_ext.equals(".png")) {
                    if(db.isExist(file_Name)) {
                        Log.i(TAG, "Entry exists" + file_Name);
                    } else {
                        db.insert(file_Name, file_Size, file_ext, file_path, file_date);
                    }
                }
            }
        }

        try {
            db.insert("...", "", "", "", "");
        } catch (Exception e) {
            Snackbar.make(listView, R.string.toast_directory, Snackbar.LENGTH_LONG).show();
        }

        //display data
        final int layoutstyle= R.layout.list_item;
        int[] xml_id = new int[] {
                R.id.textView_title_notes,
                R.id.textView_des_notes,
                R.id.textView_create_notes
        };
        String[] column = new String[] {
                "files_title",
                "files_content",
                "files_creation"
        };
        final Cursor row = db.fetchAllData(this);
        adapter = new SimpleCursorAdapter(this, layoutstyle,row,column, xml_id, 0) {
            @Override
            public View getView (final int position, View convertView, ViewGroup parent) {

                Cursor row2 = (Cursor) listView.getItemAtPosition(position);
                final String files_icon = row2.getString(row2.getColumnIndexOrThrow("files_icon"));
                final String files_attachment = row2.getString(row2.getColumnIndexOrThrow("files_attachment"));
                final String files_title = row2.getString(row2.getColumnIndexOrThrow("files_title"));

                final File pathFile = new File(files_attachment);

                View v = super.getView(position, convertView, parent);
                final ImageView iv = (ImageView) v.findViewById(R.id.icon_notes);

                iv.setVisibility(View.VISIBLE);

                if (pathFile.isDirectory()) {
                    iv.setImageResource(R.drawable.folder);
                } else {
                    switch (files_icon) {
                        case ".gif":case ".bmp":case ".tiff":case ".svg":
                        case ".png":case ".jpg":case ".JPG":case ".jpeg":
                            try {
                                Uri uri = Uri.fromFile(pathFile);
//                                Picasso.with(Activity_files.this).load(uri).resize(76, 76).centerCrop().into(iv);
                                RequestOptions requestOptions = new RequestOptions().centerCrop().override(76, 76);
                                Glide.with(Activity_files.this).load(uri).apply(requestOptions).into(iv);
                            } catch (Exception e) {
                                Log.w("HHS_Moodle", "Error Load image", e);
                            }
                            break;
                        case ".pdf":
                            iv.setImageResource(R.drawable.file_pdf);
                            break;
                        default:
                            iv.setImageResource(R.drawable.arrow_up_dark);
                            break;
                    }
                }

                if (files_title.equals("...")) {
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            iv.setImageResource(R.drawable.arrow_up_dark);
                        }
                    }, 200);
                }
                return v;
            }
        };

        //display data by filter
        final String note_search = sharedPref.getString("filter_filesBY", "files_title");
        sharedPref.edit().putString("filter_filesBY", "files_title").apply();
        filter.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s.toString());
            }
        });
        adapter.setFilterQueryProvider(new FilterQueryProvider() {
            public Cursor runQuery(CharSequence constraint) {
                return db.fetchDataByFilter(constraint.toString(),note_search);
            }
        });

        listView.setAdapter(adapter);
        //onClick function
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterview, View view, int position, long id) {

                Cursor row2 = (Cursor) listView.getItemAtPosition(position);
                final String files_icon = row2.getString(row2.getColumnIndexOrThrow("files_icon"));
                final String files_attachment = row2.getString(row2.getColumnIndexOrThrow("files_attachment"));

                final File pathFile = new File(files_attachment);

                if(pathFile.isDirectory()) {
                    try {
                        sharedPref.edit().putString("files_startFolder", files_attachment).apply();
                        setFilesList();
                    } catch (Exception e) {
                        Snackbar.make(listView, R.string.toast_directory, Snackbar.LENGTH_LONG).show();
                    }
                } else if(files_attachment.equals("")) {
                    try {
                        final File pathActual = new File(sharedPref.getString("files_startFolder",
                                Environment.getExternalStorageDirectory().getPath()));
                        sharedPref.edit().putString("files_startFolder", pathActual.getParent()).apply();
                        setFilesList();
                    } catch (Exception e) {
                        Snackbar.make(listView, R.string.toast_directory, Snackbar.LENGTH_LONG).show();
                    }
                } else if(files_icon.equals(".pdf")) {
                    Uri uri = Uri.fromFile(pathFile);
                    Intent intent = new Intent(Activity_files.this, MuPDFActivity.class);
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(uri);
                    startActivity(intent);
                } else {
                    helper_main.open(files_icon, Activity_files.this, pathFile, listView);
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

                Cursor row2 = (Cursor) listView.getItemAtPosition(position);
                final String files_title = row2.getString(row2.getColumnIndexOrThrow("files_title"));
                final String files_attachment = row2.getString(row2.getColumnIndexOrThrow("files_attachment"));

                final File pathFile = new File(files_attachment);

                if (pathFile.isDirectory()) {
                    Snackbar snackbar = Snackbar
                            .make(listView, R.string.toast_remove_confirmation, Snackbar.LENGTH_LONG)
                            .setAction(R.string.toast_yes, new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    deleteRecursive(pathFile);
                                    setFilesList();
                                }
                            });
                    snackbar.show();

                } else {
                    final CharSequence[] options = {
                            getString(R.string.choose_menu_2),
                            getString(R.string.choose_menu_3),
                            getString(R.string.choose_menu_4)};

                    final AlertDialog.Builder dialog = new AlertDialog.Builder(Activity_files.this);
                    dialog.setPositiveButton(R.string.toast_cancel, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.cancel();
                        }
                    });
                    dialog.setItems(options, new DialogInterface.OnClickListener() {
                        @SuppressWarnings("ResultOfMethodCallIgnored")
                        @Override
                        public void onClick(DialogInterface dialog, int item) {

                            if (options[item].equals(getString(R.string.choose_menu_2))) {

                                if (pathFile.exists()) {
                                    Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                                    sharingIntent.setType("image/png");
                                    sharingIntent.putExtra(Intent.EXTRA_SUBJECT, files_title);
                                    sharingIntent.putExtra(Intent.EXTRA_TEXT, files_title);
                                    Uri bmpUri = Uri.fromFile(pathFile);
                                    sharingIntent.putExtra(Intent.EXTRA_STREAM, bmpUri);
                                    startActivity(Intent.createChooser(sharingIntent, (getString(R.string.app_share_file))));
                                }
                            }
                            if (options[item].equals(getString(R.string.choose_menu_4))) {

                                Snackbar snackbar = Snackbar
                                        .make(listView, R.string.toast_remove_confirmation, Snackbar.LENGTH_LONG)
                                        .setAction(R.string.toast_yes, new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                pathFile.delete();
                                                setFilesList();
                                            }
                                        });
                                snackbar.show();
                            }
                            if (options[item].equals(getString(R.string.choose_menu_3))) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(Activity_files.this);
                                View dialogView = View.inflate(Activity_files.this, R.layout.dialog_edit_file, null);

                                final EditText edit_title = (EditText) dialogView.findViewById(R.id.pass_title);
                                edit_title.setText(files_title);

                                builder.setView(dialogView);
                                builder.setTitle(R.string.choose_title);
                                builder.setPositiveButton(R.string.toast_yes, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        String inputTag = edit_title.getText().toString().trim();

                                        File dir = pathFile.getParentFile();
                                        File to = new File(dir,inputTag);

                                        pathFile.renameTo(to);
                                        pathFile.delete();
                                        setFilesList();
                                    }
                                });
                                builder.setNegativeButton(R.string.toast_cancel, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        dialog.cancel();
                                    }
                                });
                                AlertDialog dialog2 = builder.create();
                                // Display the custom alert dialog on interface
                                dialog2.show();
                                helper_main.showKeyboard(Activity_files.this,edit_title);
                            }
                        }
                    });
                    dialog.show();
                }

                return true;
            }
        });
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    private static String getReadableFileSize(long size) {
        final int BYTES_IN_KILOBYTES = 1024;
        final DecimalFormat dec = new DecimalFormat("###.#");
        final String KILOBYTES = " KB";
        final String MEGABYTES = " MB";
        final String GIGABYTES = " GB";
        float fileSize = 0;
        String suffix = KILOBYTES;

        if (size > BYTES_IN_KILOBYTES) {
            fileSize = size / BYTES_IN_KILOBYTES;
            if (fileSize > BYTES_IN_KILOBYTES) {
                fileSize = fileSize / BYTES_IN_KILOBYTES;
                if (fileSize > BYTES_IN_KILOBYTES) {
                    fileSize = fileSize / BYTES_IN_KILOBYTES;
                    suffix = GIGABYTES;
                } else {
                    suffix = MEGABYTES;
                }
            }
        }
        return valueOf(dec.format(fileSize) + suffix);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_file, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int itemId = item.getItemId();
        if (itemId == R.id.filter_title) {
            sharedPref.edit().putString("filter_filesBY", "files_title").apply();
            setFilesList();
            filter_layout.setVisibility(View.VISIBLE);
            filter.setText("");
            filter.setHint(R.string.action_filter_title);
            filter.requestFocus();
            helper_main.showKeyboard(Activity_files.this, filter);
            return true;
        } else if (itemId == R.id.filter_ext) {
            sharedPref.edit().putString("filter_filesBY", "files_icon").apply();
            setFilesList();
            filter_layout.setVisibility(View.VISIBLE);
            filter.setText("");
            filter.setHint(R.string.action_filter_ext);
            filter.requestFocus();
            helper_main.showKeyboard(Activity_files.this, filter);
            return true;
        } else if (itemId == R.id.filter_today) {
            setTitle(getString(R.string.choose_titleMain) + " | " + getString(R.string.filter_today));
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            final String search = dateFormat.format(cal.getTime());
            sharedPref.edit().putString("filter_filesBY", "files_creation").apply();
            setFilesList();
            filter.setText(search);
            return true;
        } else if (itemId == R.id.filter_yesterday) {
            setTitle(getString(R.string.choose_titleMain) + " | " + getString(R.string.filter_yesterday));
            DateFormat dateFormat2 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar cal2 = Calendar.getInstance();
            cal2.add(Calendar.DATE, -1);
            final String search2 = dateFormat2.format(cal2.getTime());
            sharedPref.edit().putString("filter_filesBY", "files_creation").apply();
            setFilesList();
            filter.setText(search2);
            return true;
        } else if (itemId == R.id.filter_before) {
            setTitle(getString(R.string.choose_titleMain) + " | " + getString(R.string.filter_before));
            DateFormat dateFormat3 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar cal3 = Calendar.getInstance();
            cal3.add(Calendar.DATE, -2);
            final String search3 = dateFormat3.format(cal3.getTime());
            sharedPref.edit().putString("filter_filesBY", "files_creation").apply();
            setFilesList();
            filter.setText(search3);
            return true;
        } else if (itemId == R.id.filter_month) {
            setTitle(getString(R.string.choose_titleMain) + " | " + getString(R.string.filter_month));
            DateFormat dateFormat4 = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Calendar cal4 = Calendar.getInstance();
            final String search4 = dateFormat4.format(cal4.getTime());
            sharedPref.edit().putString("filter_filesBY", "files_creation").apply();
            setFilesList();
            filter.setText(search4);
            return true;
        } else if (itemId == R.id.filter_own) {
            setTitle(getString(R.string.choose_titleMain) + " | " + getString(R.string.filter_own));
            sharedPref.edit().putString("filter_filesBY", "files_creation").apply();
            setFilesList();
            filter_layout.setVisibility(View.VISIBLE);
            filter.setText("");
            filter.setHint(R.string.action_filter_create);
            filter.requestFocus();
            helper_main.showKeyboard(Activity_files.this, filter);
            return true;
        } else if (itemId == R.id.filter_clear) {
            filter.setText("");
            setFilesList();
            return true;
        } else if (itemId == R.id.sort_title) {
            sharedPref.edit().putString("sortDBF", "title").apply();
            setTitle();
            setFilesList();
            return true;
        } else if (itemId == R.id.sort_ext) {
            sharedPref.edit().putString("sortDBF", "file_ext").apply();
            setTitle();
            setFilesList();
            return true;
        } else if (itemId == R.id.sort_creation) {
            sharedPref.edit().putString("sortDBF", "file_date").apply();
            setTitle();
            setFilesList();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        if (requestCode == 0){
            if (!perms.isEmpty()){
                PackageManager pm = getPackageManager();
                boolean isInstalled = isPackageInstalled("de.baumann.pdfcreator", pm);

                if (isInstalled) {
                    PackageManager p = getPackageManager();
                    p.setComponentEnabledSetting(getComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
                    Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage("de.baumann.pdfcreator");
                    LaunchIntent.setAction("pdf_openFolder");
                    startActivity(LaunchIntent);
                    finish();
                }

                db = new DbAdapter_Files(this);
                db.open();

            }
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {

    }
    private void checkPermission(String[] perms, int requestCode){
        EasyPermissions.requestPermissions(this, "应用需要以下权限，请允许", requestCode,perms);
    }



}