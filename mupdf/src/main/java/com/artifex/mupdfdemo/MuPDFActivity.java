package com.artifex.mupdfdemo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.yjhs.mupdf.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import file_manager.Activity_files;
import file_manager.SignActivity;
import file_manager.view.DragFloatActionButton;
import file_manager.view.SavePdf;
import file_manager.view.VDHDeepLayout;


class ThreadPerTaskExecutor implements Executor {
    public void execute(Runnable r) {
        new Thread(r).start();
    }
}

public class MuPDFActivity extends AppCompatActivity implements FilePicker.FilePickerSupport {
    /* The core rendering instance */
    enum TopBarMode {
        Main, Search, Annot, Delete, More, Accept
    }

    enum AcceptMode {Highlight, Underline, StrikeOut, Ink, CopyText}

    private final int OUTLINE_REQUEST = 0;
    private final int FILEPICK_REQUEST = 2;
    private final int PROOF_REQUEST = 3;
    private MuPDFCore core;
    private String mFileName;
    private MuPDFReaderView mDocView;
    private View mButtonsView;
    private boolean mButtonsVisible;
    private EditText mPasswordView;
    private TextView mFilenameView;
    private SeekBar mPageSlider;
    private int mPageSliderRes;
    private TextView mPageNumberView;
    private Toast mInfoView;
    private ImageButton mSearchButton;
    private ImageButton mOutlineButton;
    private TextView mAnnotTypeText;
    private ImageButton mAnnotButton;
    private ViewAnimator mTopBarSwitcher;
    private ImageButton mLinkButton;
    private TopBarMode mTopBarMode = TopBarMode.Main;
    private AcceptMode mAcceptMode;
    private ImageButton mSearchBack;
    private ImageButton mSearchFwd;
    private EditText mSearchText;
    private SearchTask mSearchTask;
    private ImageButton mSepsButton;
    private AlertDialog.Builder mAlertBuilder;
    private boolean mLinkHighlight = false;
    private final Handler mHandler = new Handler();
    private boolean mAlertsActive = false;
    private AsyncTask<Void, Void, MuPDFAlert> mAlertTask;
    private AlertDialog mAlertDialog;
    private FilePicker mFilePicker;
    private String mProofFile;
    private boolean mSepEnabled[][];
    private SharedPreferences sharedPref;

    static private AlertDialog.Builder gAlertBuilder;


    /*
     * 用于存储的异步,并上传更新
     * */
    class Save_Pdf extends android.os.AsyncTask {

        SavePdf savePdf;

        public Save_Pdf(SavePdf savePdf) {
            this.savePdf = savePdf;
        }

        @Override
        protected Object doInBackground(Object[] params) {
            savePdf.addText();
//            if (first == 1) {
////                update_path = in_path.substring(0, in_path.length() - 4) + ".pdf";
////                in_path = in_path.substring(0, in_path.length() - 4) + "2.pdf";
//                first++;
//            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
//            Toast.makeText(PDFActivity.this, "存储完成", Toast.LENGTH_SHORT).show();
            try {
                core = new MuPDFCore(MuPDFActivity.this, out_path);

                createUI(new Bundle());


                String temp = in_path;
                in_path = out_path;
                out_path = temp;

                SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                mDocView.setDisplayedViewIndex(prefs.getInt("page" + mFileName, 0));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static public AlertDialog.Builder getAlertBuilder() {
        return gAlertBuilder;
    }

    private String path;

    private View signLayout;//可拖动签名的布局
    private View pageNumView;//页码布
    private TextView tv_page_num;
    private DragFloatActionButton btn_sign, btn_clear;
    private ImageView image;
    VDHDeepLayout signView;
    private String btnSign = "sign";//sign是签名  save是保存
    String in_path = "";
    String out_path = "";
    private Save_Pdf save_pdf;
    private String pathToEdit="";


    public void createAlertWaiter() {
        mAlertsActive = true;
        // All mupdf library calls are performed on asynchronous tasks to avoid stalling
        // the UI. Some calls can lead to javascript-invoked requests to display an
        // alert dialog and collect a reply from the user. The task has to be blocked
        // until the user's reply is received. This method creates an asynchronous task,
        // the purpose of which is to wait of these requests and produce the dialog
        // in response, while leaving the core blocked. When the dialog receives the
        // user's response, it is sent to the core via replyToAlert, unblocking it.
        // Another alert-waiting task is then created to pick up the next alert.
        if (mAlertTask != null) {
            mAlertTask.cancel(true);
            mAlertTask = null;
        }
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }
        mAlertTask = new AsyncTask<Void, Void, MuPDFAlert>() {

            @Override
            protected MuPDFAlert doInBackground(Void... arg0) {
                if (!mAlertsActive)
                    return null;

                return core.waitForAlert();
            }

            @Override
            protected void onPostExecute(final MuPDFAlert result) {
                // core.waitForAlert may return null when shutting down
                if (result == null)
                    return;
                final MuPDFAlert.ButtonPressed pressed[] = new MuPDFAlert.ButtonPressed[3];
                for (int i = 0; i < 3; i++)
                    pressed[i] = MuPDFAlert.ButtonPressed.None;
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertDialog = null;
                        if (mAlertsActive) {
                            int index = 0;
                            switch (which) {
                                case AlertDialog.BUTTON1:
                                    index = 0;
                                    break;
                                case AlertDialog.BUTTON2:
                                    index = 1;
                                    break;
                                case AlertDialog.BUTTON3:
                                    index = 2;
                                    break;
                            }
                            result.buttonPressed = pressed[index];
                            // Send the user's response to the core, so that it can
                            // continue processing.
                            core.replyToAlert(result);
                            // Create another alert-waiter to pick up the next alert.
                            createAlertWaiter();
                        }
                    }
                };
                mAlertDialog = mAlertBuilder.create();
                mAlertDialog.setTitle(result.title);
                mAlertDialog.setMessage(result.message);
                switch (result.iconType) {
                    case Error:
                        break;
                    case Warning:
                        break;
                    case Question:
                        break;
                    case Status:
                        break;
                }
                switch (result.buttonGroupType) {
                    case OkCancel:
                        mAlertDialog.setButton(AlertDialog.BUTTON2, getString(R.string.cancel), listener);
                        pressed[1] = MuPDFAlert.ButtonPressed.Cancel;
                    case Ok:
                        mAlertDialog.setButton(AlertDialog.BUTTON1, getString(R.string.okay), listener);
                        pressed[0] = MuPDFAlert.ButtonPressed.Ok;
                        break;
                    case YesNoCancel:
                        mAlertDialog.setButton(AlertDialog.BUTTON3, getString(R.string.cancel), listener);
                        pressed[2] = MuPDFAlert.ButtonPressed.Cancel;
                    case YesNo:
                        mAlertDialog.setButton(AlertDialog.BUTTON1, getString(R.string.yes), listener);
                        pressed[0] = MuPDFAlert.ButtonPressed.Yes;
                        mAlertDialog.setButton(AlertDialog.BUTTON2, getString(R.string.no), listener);
                        pressed[1] = MuPDFAlert.ButtonPressed.No;
                        break;
                }
                mAlertDialog.setOnCancelListener(new OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        mAlertDialog = null;
                        if (mAlertsActive) {
                            result.buttonPressed = MuPDFAlert.ButtonPressed.None;
                            core.replyToAlert(result);
                            createAlertWaiter();
                        }
                    }
                });

                mAlertDialog.show();
            }
        };

        mAlertTask.executeOnExecutor(new ThreadPerTaskExecutor());
    }

    public void destroyAlertWaiter() {
        mAlertsActive = false;
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }
        if (mAlertTask != null) {
            mAlertTask.cancel(true);
            mAlertTask = null;
        }
    }

    private MuPDFCore openFile(String path) {
        int lastSlashPos = path.lastIndexOf('/');
        mFileName = new String(lastSlashPos == -1
                ? path
                : path.substring(lastSlashPos + 1));
        System.out.println("Trying to open " + path);
        try {
            core = new MuPDFCore(this, path);
            // New file: drop the old outline data
            OutlineActivityData.set(null);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        } catch (OutOfMemoryError e) {
            //  out of memory is not an Exception, so we catch it separately.
            System.out.println(e);
            return null;
        }
        return core;
    }

    private MuPDFCore openBuffer(byte buffer[], String magic) {
        System.out.println("Trying to open byte buffer");
        try {
            core = new MuPDFCore(this, buffer, magic);
            // New file: drop the old outline data
            OutlineActivityData.set(null);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
        return core;
    }

    //  determine whether the current activity is a proofing activity.
    public boolean isProofing() {
        String format = core.fileFormat();
        return (format.equals("GPROOF"));
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PackageManager pm = getPackageManager();
        boolean isInstalled = isPackageInstalled("de.baumann.pdfcreator", pm);

        ActionBar actionBar = getSupportActionBar();

        if (isInstalled) {
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        PreferenceManager.setDefaultValues(this, R.xml.user_settings, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.edit().putString("menu", "yes").apply();

        mAlertBuilder = new AlertDialog.Builder(this);
        gAlertBuilder = mAlertBuilder;  //  keep a static copy of this that other classes can use

        if (core == null) {
            core = (MuPDFCore) getLastNonConfigurationInstance();

            if (savedInstanceState != null && savedInstanceState.containsKey("FileName")) {
                mFileName = savedInstanceState.getString("FileName");
            }
        }
        if (core == null) {
            Intent intent = getIntent();
            byte buffer[] = null;

            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                Uri uri = intent.getData();


                System.out.println("URI to open is: " + uri);
                if (uri.toString().startsWith("content://")) {

                    String reason = null;
                    try {
                        InputStream is = getContentResolver().openInputStream(uri);
                        assert is != null;
                        int len = is.available();
                        buffer = new byte[len];
                        is.read(buffer, 0, len);
                        is.close();
                    } catch (OutOfMemoryError e) {
                        System.out.println("Out of memory during buffer reading");
                        reason = e.toString();
                    } catch (Exception e) {
                        System.out.println("Exception reading from stream: " + e);

                        // Handle view requests from the Transformer Prime's file manager
                        // Hopefully other file managers will use this same scheme, if not
                        // using explicit paths.
                        // I'm hoping that this case below is no longer needed...but it's
                        // hard to test as the file manager seems to have changed in 4.x.
                        try {
                            Cursor cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
                            assert cursor != null;
                            if (cursor.moveToFirst()) {
                                String str = cursor.getString(0);
                                if (str == null) {
                                    reason = "Couldn't parse data in intent";
                                    cursor.close();
                                } else {
                                    uri = Uri.parse(str);
                                }
                            }
                        } catch (Exception e2) {
                            System.out.println("Exception in Transformer Prime file manager code: " + e2);
                            reason = e2.toString();
                        }
                    }
                    if (reason != null) {
                        Resources res = getResources();
                        AlertDialog alert = mAlertBuilder.create();
                        setTitle(String.format(res.getString(R.string.cannot_open_document_Reason), reason));
                        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                });
                        alert.show();
                        return;
                    }
                }
                if (buffer != null) {

                    String path = intent.getStringExtra("path");
                    String name = intent.getStringExtra("name");

                    if (path == null || name == null) {
                        sharedPref.edit().putString("menu", "no").apply();
                    } else {
                        setTitle(name);

                        sharedPref.edit().putString("pathPDF", path).apply();
                        sharedPref.edit().putString("title", name).apply();
                    }

                    core = openBuffer(buffer, intent.getType());
                } else {
                     pathToEdit = uri.getPath();
                    final String fileName = pathToEdit.substring(pathToEdit.lastIndexOf("/") + 1);

                    setTitle(fileName);

                    sharedPref.edit().putString("pathPDF", pathToEdit).apply();
                    sharedPref.edit().putString("title", fileName).apply();

                    String path = Uri.decode(uri.getEncodedPath());
                    if (path == null) {
                        path = uri.toString();
                    }
                    core = openFile(path);
                }
                SearchTaskResult.set(null);
            }
            if (core != null && core.needsPassword()) {
                requestPassword(savedInstanceState);
                return;
            }
            if (core != null && core.countPages() == 0) {
                core = null;
            }
        }
        if (core == null) {
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle(R.string.cannot_open_document);
            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
            alert.setOnCancelListener(new OnCancelListener() {

                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
            alert.show();
            return;
        }

        createUI(savedInstanceState);

    }

    public void requestPassword(final Bundle savedInstanceState) {
        mPasswordView = new EditText(this);
        mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        mPasswordView.setTransformationMethod(new PasswordTransformationMethod());

        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.enter_password);
        alert.setView(mPasswordView);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (core.authenticatePassword(mPasswordView.getText().toString())) {
                            createUI(savedInstanceState);
                        } else {
                            requestPassword(savedInstanceState);
                        }
                    }
                });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        alert.show();
    }

    public void createUI(Bundle savedInstanceState) {
        if (core == null)
            return;

        // Now create the UI.
        // First create the document view
        mDocView = new MuPDFReaderView(this) {
            @Override
            protected void onMoveToChild(int i) {
                if (core == null)
                    return;

                tv_page_num.setText(String.format("%d / %d", i + 1, core.countPages()));

                mPageNumberView.setText(String.format("%d / %d", i + 1,
                        core.countPages()));
                mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
                mPageSlider.setProgress(i * mPageSliderRes);
                super.onMoveToChild(i);
            }

            @Override
            protected void onTapMainDocArea() {
                if (!mButtonsVisible) {
                    showButtons();
                } else {
                    if (mTopBarMode == TopBarMode.Main)
                        hideButtons();
                }
            }

            @Override
            protected void onDocMotion() {
                hideButtons();
            }

            @Override
            protected void onHit(Hit item) {
                switch (mTopBarMode) {
                    case Annot:
                        if (item == Hit.Annotation) {
                            showButtons();
                            mTopBarMode = TopBarMode.Delete;
                            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
                        }
                        break;
                    case Delete:
                        mTopBarMode = TopBarMode.Annot;
                        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
                        // fall through
                    default:
                        // Not in annotation editing mode, but the pageview will
                        // still select and highlight hit annotations, so
                        // deselect just in case.
                        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
                        if (pageView != null)
                            pageView.deselectAnnotation();
                        break;
                }
            }
        };
        mDocView.setAdapter(new MuPDFPageAdapter(this, this, core));
        mDocView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPDF));

        mSearchTask = new SearchTask(this, core) {
            @Override
            protected void onTextFound(SearchTaskResult result) {
                SearchTaskResult.set(result);
                // Ask the ReaderView to move to the resulting page
                mDocView.setDisplayedViewIndex(result.pageNumber);
                // Make the ReaderView act on the change to SearchTaskResult
                // via overridden onChildSetup method.
                mDocView.resetupChildren();
            }
        };

        // Make the buttons overlay, and store all its
        // controls in variables
        makeButtonsView();

        // Set up the page slider
        int smax = Math.max(core.countPages() - 1, 1);
        mPageSliderRes = ((10 + smax - 1) / smax) * 2;

        // Set the file-name text
        mFilenameView.setText(mFileName);

        // Activate the seekbar
        mPageSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
                mDocView.setDisplayedViewIndex((seekBar.getProgress() + mPageSliderRes / 2) / mPageSliderRes);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                updatePageNumView((progress + mPageSliderRes / 2) / mPageSliderRes);
            }
        });

        // Activate the search-preparing button
        mSearchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                searchModeOn();
            }
        });


        if (core.fileFormat().startsWith("PDF") && core.isUnencryptedPDF() && !core.wasOpenedFromBuffer()) {
            mAnnotButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mTopBarMode = TopBarMode.Annot;
                    mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
                }
            });
        } else {
            mAnnotButton.setVisibility(View.GONE);
        }

        // Search invoking buttons are disabled while there is no text specified
        mSearchBack.setEnabled(false);
        mSearchFwd.setEnabled(false);
        mSearchBack.setColorFilter(Color.argb(0xFF, 250, 250, 250));
        mSearchFwd.setColorFilter(Color.argb(0xFF, 250, 250, 250));

        // React to interaction with the text widget
        mSearchText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                boolean haveText = s.toString().length() > 0;
                setButtonEnabled(mSearchBack, haveText);
                setButtonEnabled(mSearchFwd, haveText);

                // Remove any previous search results
                if (SearchTaskResult.get() != null && !mSearchText.getText().toString().equals(SearchTaskResult.get().txt)) {
                    SearchTaskResult.set(null);
                    mDocView.resetupChildren();
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }
        });

        //React to Done button on keyboard
        mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE)
                    search(1);
                return false;
            }
        });

        mSearchText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER)
                    search(1);
                return false;
            }
        });

        // Activate search invoking buttons
        mSearchBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                search(-1);
            }
        });
        mSearchFwd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                search(1);
            }
        });

        mLinkButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setLinkHighlight(!mLinkHighlight);
            }
        });

        if (core.hasOutline()) {
            mOutlineButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    OutlineItem outline[] = core.getOutline();
                    if (outline != null) {
                        OutlineActivityData.get().items = outline;
                        Intent intent = new Intent(MuPDFActivity.this, OutlineActivity.class);
                        startActivityForResult(intent, OUTLINE_REQUEST);
                    }
                }
            });
        } else {
            mOutlineButton.setVisibility(View.GONE);
        }

        // Reenstate last state if it was recorded
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
//        mDocView.setDisplayedViewIndex(prefs.getInt("page" + mFileName, 0));

        if (savedInstanceState == null || !savedInstanceState.getBoolean("ButtonsHidden", false))
            showButtons();

        if (savedInstanceState != null && savedInstanceState.getBoolean("SearchMode", false))
            searchModeOn();

        // 父布局
        RelativeLayout layout = new RelativeLayout(this);
        layout.addView(mDocView);

        LayoutInflater inflater = LayoutInflater.from(this);
        RelativeLayout.LayoutParams rlp;

        //页码布局
        pageNumView = inflater.inflate(R.layout.activity_page_num,
                null);
        pageNumView.setBackgroundColor(0x00000000);
        tv_page_num = (TextView) pageNumView.findViewById(R.id.tv_page_num);
        rlp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        rlp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        layout.addView(pageNumView, rlp);

        //签名布局
        signLayout = inflater.inflate(R.layout.activity_mupdf,
                null);
        signLayout.setBackgroundColor(0x00000000);
        btn_sign = (DragFloatActionButton) signLayout.findViewById(R.id.btn_sign);
        btn_clear = (DragFloatActionButton) signLayout.findViewById(R.id.btn_clear);
//        btn_save = (ImageView) signLayout.findViewById(R.id.btn_save);
        image = (ImageView) signLayout.findViewById(R.id.image);
        signView = (VDHDeepLayout) signLayout.findViewById(R.id.signView1);

        rlp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, (int) core.getPageSize(0).y);
        rlp.addRule(RelativeLayout.CENTER_IN_PARENT);
        signView.setLayoutParams(rlp);





        layout.addView(mButtonsView);

        layout.addView(signLayout, RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);

        setContentView(layout);

        if (isProofing()) {
            //  go to the current page
            int currentPage = getIntent().getIntExtra("startingPage", 0);
            mDocView.setDisplayedViewIndex(currentPage);
        }

        btn_sign.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onClick(View v) {


//                image.setImageResource(R.drawable.qq);
                if (!btn_sign.getIsDrag()) {//非滑动状态


                    if (btnSign.equals("sign")) {//签名
                        Intent intent = new Intent(MuPDFActivity.this, SignActivity.class);
                        startActivityForResult(intent, 100);
                    } else if (btnSign.equals("save")) {//保存
                        //               保存pdf的代码  勿删
                        in_path =pathToEdit;
                        out_path = in_path.substring(0, in_path.length() - 4) + "1.pdf";
//                        out_path = System.currentTimeMillis()+in_path;
                        SavePdf savePdf = new SavePdf(in_path, out_path);
                        savePdf.setScale(1.0f);
                        savePdf.setPageNum(mDocView.getDisplayedViewIndex() + 1);

                        savePdf.setWidthScale(1.0f * mDocView.getScaleX() / mDocView.getDisplayedView().getWidth());//计算宽偏移的百分比
                        savePdf.setHeightScale(1.0f * mDocView.getScaleY() / mDocView.getHeight());//计算长偏移的百分比

                        //计算分辨率密度
                        DisplayMetrics metric = new DisplayMetrics();
                        getWindowManager().getDefaultDisplay().getMetrics(metric);
                        float density = metric.density;
                        savePdf.setDensity(density);
                        Bitmap bitmap = Bitmap.createBitmap(signView.getWidth(), signView.getHeight(),
                                Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        signView.draw(canvas);
                        savePdf.setBitmap(bitmap);
                        save_pdf = new Save_Pdf(savePdf);
                        save_pdf.execute();
//                popupWindow.dismiss();
//                iBack = true;
//                        btn_save.setVisibility(View.GONE);
                        btn_clear.setVisibility(View.GONE);
                        signView.setVisibility(View.GONE);

                        mDocView.setAdapter(new MuPDFPageAdapter(MuPDFActivity.this, MuPDFActivity.this, core));

                    }
                }
            }
        });


        btn_clear.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onClick(View v) {

                if (!btn_clear.getIsDrag()) {
                    btnSign="sign";
                    btn_sign.setImageResource(R.mipmap.sign);
                    image.setImageDrawable(null);
                    signView.setVisibility(View.GONE);
//                    btn_save.setVisibility(View.GONE);

                    btn_clear.setVisibility(View.GONE);

                }
            }
        });
        tv_page_num.setText(String.format("%d / %d", 1, core.countPages()));

    }

    @SuppressLint("RestrictedApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case OUTLINE_REQUEST:
                if (resultCode >= 0)
                    mDocView.setDisplayedViewIndex(resultCode);
                break;
            case FILEPICK_REQUEST:
                if (mFilePicker != null && resultCode == RESULT_OK)
                    mFilePicker.onPick(data.getData());
            case PROOF_REQUEST:
                //  we're returning from a proofing activity

                if (mProofFile != null) {
                    core.endProof(mProofFile);
                    mProofFile = null;
                }

                //  return the top bar to default
                mTopBarMode = TopBarMode.Main;
                mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());

            case 100:
                if (data != null && data.getExtras() != null) {
                    Bundle b = data.getExtras();
                    byte[] bytes = b.getByteArray("bitmap");
//                    btn_save.setVisibility(View.VISIBLE);
                    btn_clear.setVisibility(View.VISIBLE);

                    signView.setVisibility(View.VISIBLE);

                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    ByteArrayOutputStream baos = null;


                    image.setImageBitmap(bmp);
                    btnSign = "save";
                    btn_sign.setImageResource(R.mipmap.qd);
//                    RelativeLayout.MarginLayoutParams margin = new RelativeLayout.MarginLayoutParams(
//                            image.getLayoutParams());
//                    WindowManager wm = (WindowManager) this
//                            .getSystemService(Context.WINDOW_SERVICE);
//                    int width = wm.getDefaultDisplay().getWidth();
//                    margin.leftMargin = (int) (bean.getData().getQmx() * width);
//                    margin.bottomMargin = (int) (bean.getData().getQmy() * (core.getPageSize(0).y));
//                    RelativeLayout.LayoutParams layoutParams9 = new RelativeLayout.LayoutParams(margin);
//                    layoutParams9.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
//                    image.setLayoutParams(layoutParams9);

                } else {
//                    btn_save.setVisibility(View.GONE);
                    btn_clear.setVisibility(View.GONE);

                    signView.setVisibility(View.GONE);
                }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (mFileName != null && mDocView != null) {
            outState.putString("FileName", mFileName);

            // Store current page in the prefs against the file name,
            // so that we can pick it up each time the file is loaded
            // Other info is needed only for screen-orientation change,
            // so it can go in the bundle
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
            edit.commit();
        }

        if (!mButtonsVisible)
            outState.putBoolean("ButtonsHidden", true);

        if (mTopBarMode == TopBarMode.Search)
            outState.putBoolean("SearchMode", true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSearchTask != null)
            mSearchTask.stop();

        if (mFileName != null && mDocView != null) {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
            edit.commit();
        }
    }

    public void onDestroy() {
        if (mDocView != null) {
            mDocView.applyToChildren(new ReaderView.ViewMapper() {
                void applyToView(View view) {
                    ((MuPDFView) view).releaseBitmaps();
                }
            });
        }
        if (core != null)
            core.onDestroy();
        if (mAlertTask != null) {
            mAlertTask.cancel(true);
            mAlertTask = null;
        }
        core = null;
        super.onDestroy();
    }

    private void setButtonEnabled(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setColorFilter(enabled ? Color.argb(0xFF, 250, 250, 250) : Color.argb(0xFF, 250, 250, 250));
    }

    private void setLinkHighlight(boolean highlight) {
        mLinkHighlight = highlight;
        // LINK_COLOR tint
        mLinkButton.setColorFilter(highlight ? Color.argb(0xFF, 255, 160, 0) : Color.argb(0xFF, 255, 255, 255));
        // Inform pages of the change.
        mDocView.setLinksEnabled(highlight);
    }

    private void showButtons() {
        if (core == null)
            return;
        if (!mButtonsVisible) {
            mButtonsVisible = true;
            // Update page number text and slider
            int index = mDocView.getDisplayedViewIndex();
            updatePageNumView(index);
            mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
            mPageSlider.setProgress(index * mPageSliderRes);
            if (mTopBarMode == TopBarMode.Search) {
                mSearchText.requestFocus();
                showKeyboard();
            }

            Animation anim = new TranslateAnimation(0, 0, -mTopBarSwitcher.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                }
            });
            mTopBarSwitcher.startAnimation(anim);

            anim = new TranslateAnimation(0, 0, mPageSlider.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mPageSlider.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mPageNumberView.setVisibility(View.VISIBLE);
                }
            });
            mPageSlider.startAnimation(anim);
        }
    }

    private void hideButtons() {
        if (mButtonsVisible) {
            mButtonsVisible = false;
            hideKeyboard();

            Animation anim = new TranslateAnimation(0, 0, 0, -mTopBarSwitcher.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.INVISIBLE);
                }
            });
            mTopBarSwitcher.startAnimation(anim);

            anim = new TranslateAnimation(0, 0, 0, mPageSlider.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mPageNumberView.setVisibility(View.INVISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mPageSlider.setVisibility(View.INVISIBLE);
                }
            });
            mPageSlider.startAnimation(anim);
        }
    }

    private void searchModeOn() {
        if (mTopBarMode != TopBarMode.Search) {
            mTopBarMode = TopBarMode.Search;
            //Focus on EditTextWidget
            mSearchText.requestFocus();
            showKeyboard();
            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        }
    }

    private void searchModeOff() {
        if (mTopBarMode == TopBarMode.Search) {
            mTopBarMode = TopBarMode.Main;
            hideKeyboard();
            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
            SearchTaskResult.set(null);
            // Make the ReaderView act on the change to mSearchTaskResult
            // via overridden onChildSetup method.
            mDocView.resetupChildren();
        }
    }

    private void updatePageNumView(int index) {
        if (core == null)
            return;
        mPageNumberView.setText(String.format("%d / %d", index + 1, core.countPages()));
    }


    private void showInfo(String message) {

        LayoutInflater inflater = getLayoutInflater();

        View toastLayout = inflater.inflate(R.layout.toast,
                (ViewGroup) findViewById(R.id.toast_root_view));

        TextView header = (TextView) toastLayout.findViewById(R.id.toast_message);
        header.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(toastLayout);
        toast.show();
    }

    private void makeButtonsView() {
        mButtonsView = getLayoutInflater().inflate(R.layout.buttons, null);
        mFilenameView = (TextView) mButtonsView.findViewById(R.id.docNameText);
        mPageSlider = (SeekBar) mButtonsView.findViewById(R.id.pageSlider);
        mPageNumberView = (TextView) mButtonsView.findViewById(R.id.pageNumber);
        mSearchButton = (ImageButton) mButtonsView.findViewById(R.id.searchButton);
        mOutlineButton = (ImageButton) mButtonsView.findViewById(R.id.outlineButton);
        mAnnotButton = (ImageButton) mButtonsView.findViewById(R.id.reflowButton);
        mAnnotTypeText = (TextView) mButtonsView.findViewById(R.id.annotType);
        mTopBarSwitcher = (ViewAnimator) mButtonsView.findViewById(R.id.switcher);
        mSearchBack = (ImageButton) mButtonsView.findViewById(R.id.searchBack);
        mSearchFwd = (ImageButton) mButtonsView.findViewById(R.id.searchForward);
        mSearchText = (EditText) mButtonsView.findViewById(R.id.searchText);
        mLinkButton = (ImageButton) mButtonsView.findViewById(R.id.linkButton);
        mSepsButton = (ImageButton) mButtonsView.findViewById(R.id.sepsButton);
        mTopBarSwitcher.setVisibility(View.INVISIBLE);
        mPageNumberView.setVisibility(View.INVISIBLE);

        mPageSlider.setVisibility(View.INVISIBLE);

        mSepsButton.setVisibility(View.INVISIBLE);
    }

    public void OnCancelMoreButtonClick(View v) {
        mTopBarMode = TopBarMode.Main;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }


    public void OnSepsButtonClick(final View v) {
        if (isProofing()) {

            //  get the current page
            final int currentPage = mDocView.getDisplayedViewIndex();

            //  buid a popup menu based on the given separations
            final PopupMenu menu = new PopupMenu(this, v);

            //  This makes the popup menu display icons, which by default it does not do.
            //  I worry that this relies on the internals of PopupMenu, which could change.
            try {
                Field[] fields = menu.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(menu);
                        Class<?> classPopupHelper = Class.forName(menuPopupHelper
                                .getClass().getName());
                        Method setForceIcons = classPopupHelper.getMethod(
                                "setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            //  get the maximum number of seps on any page.
            //  We use this to dimension an array further down
            int maxSeps = 0;
            int numPages = core.countPages();
            for (int page = 0; page < numPages; page++) {
                int numSeps = core.getNumSepsOnPage(page);
                if (numSeps > maxSeps)
                    maxSeps = numSeps;
            }

            //  if this is the first time, create the "enabled" array
            if (mSepEnabled == null) {
                mSepEnabled = new boolean[numPages][maxSeps];
                for (int page = 0; page < numPages; page++) {
                    for (int i = 0; i < maxSeps; i++)
                        mSepEnabled[page][i] = true;
                }
            }

            //  count the seps on this page
            int numSeps = core.getNumSepsOnPage(currentPage);

            //  for each sep,
            for (int i = 0; i < numSeps; i++) {

//				//  Robin use this to skip separations
//				if (i==12)
//					break;

                //  get the name
                Separation sep = core.getSep(currentPage, i);
                String name = sep.name;

                //  make a checkable menu item with that name
                //  and the separation index as the id
                MenuItem item = menu.getMenu().add(0, i, 0, name + "    ");
                item.setCheckable(true);

                //  set an icon that's the right color
                int iconSize = 48;
                int alpha = (sep.rgba >> 24) & 0xFF;
                int red = (sep.rgba >> 16) & 0xFF;
                int green = (sep.rgba >> 8) & 0xFF;
                int blue = (sep.rgba) & 0xFF;
                int color = (alpha << 24) | (red << 16) | (green << 8) | (blue);

                ShapeDrawable swatch = new ShapeDrawable(new RectShape());
                swatch.setIntrinsicHeight(iconSize);
                swatch.setIntrinsicWidth(iconSize);
                swatch.setBounds(new Rect(0, 0, iconSize, iconSize));
                swatch.getPaint().setColor(color);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                item.setIcon(swatch);

                //  check it (or not)
                item.setChecked(mSepEnabled[currentPage][i]);

                //  establishing a menu item listener
                item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        //  someone tapped a menu item.  get the ID
                        int sep = item.getItemId();

                        //  toggle the sep
                        mSepEnabled[currentPage][sep] = !mSepEnabled[currentPage][sep];
                        item.setChecked(mSepEnabled[currentPage][sep]);
                        core.controlSepOnPage(currentPage, sep, !mSepEnabled[currentPage][sep]);

                        //  prevent the menu from being dismissed by these items
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
                        item.setActionView(new View(v.getContext()));
                        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                            @Override
                            public boolean onMenuItemActionExpand(MenuItem item) {
                                return false;
                            }

                            @Override
                            public boolean onMenuItemActionCollapse(MenuItem item) {
                                return false;
                            }
                        });
                        return false;
                    }
                });

                //  tell core to enable or disable each sep as appropriate
                //  but don't refresh the page yet.
                core.controlSepOnPage(currentPage, i, !mSepEnabled[currentPage][i]);
            }

            //  add one for done
            MenuItem itemDone = menu.getMenu().add(0, 0, 0, "Done");
            itemDone.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    //  refresh the view
                    mDocView.refresh(false);
                    return true;
                }
            });

            //  show the menu
            menu.show();
        }
    }

    public void OnCopyTextButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.CopyText;
        mDocView.setMode(MuPDFReaderView.Mode.Selecting);
        mAnnotTypeText.setText(getString(R.string.copy_text));
        showInfo(getString(R.string.select_text));
    }

    public void OnEditAnnotButtonClick(View v) {
        mTopBarMode = TopBarMode.Main;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnHighlightButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.Highlight;
        mDocView.setMode(MuPDFReaderView.Mode.Selecting);
        mAnnotTypeText.setText(R.string.highlight);
        showInfo(getString(R.string.select_text));
    }

    public void OnUnderlineButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.Underline;
        mDocView.setMode(MuPDFReaderView.Mode.Selecting);
        mAnnotTypeText.setText(R.string.underline);
        showInfo(getString(R.string.select_text));
    }

    public void OnStrikeOutButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.StrikeOut;
        mDocView.setMode(MuPDFReaderView.Mode.Selecting);
        mAnnotTypeText.setText(R.string.strike_out);
        showInfo(getString(R.string.select_text));
    }

    public void OnInkButtonClick(View v) {
        mTopBarMode = TopBarMode.Accept;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mAcceptMode = AcceptMode.Ink;
        mDocView.setMode(MuPDFReaderView.Mode.Drawing);
        mAnnotTypeText.setText(R.string.ink);
        showInfo(getString(R.string.draw_annotation));
    }

    public void OnCancelAcceptButtonClick(View v) {
        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
        if (pageView != null) {
            pageView.deselectText();
            pageView.cancelDraw();
        }
        mDocView.setMode(MuPDFReaderView.Mode.Viewing);
        switch (mAcceptMode) {
            case CopyText:
                mTopBarMode = TopBarMode.Main;
                break;
            default:
                mTopBarMode = TopBarMode.Annot;
                break;
        }
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnAcceptButtonClick(View v) {
        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
        boolean success = false;
        switch (mAcceptMode) {
            case CopyText:
                if (pageView != null)
                    success = pageView.copySelection();
                mTopBarMode = TopBarMode.Main;
                showInfo(success ? getString(R.string.copied_to_clipboard) : getString(R.string.no_text_selected));
                break;

            case Highlight:
                if (pageView != null)
                    success = pageView.markupSelection(Annotation.Type.HIGHLIGHT);
                mTopBarMode = TopBarMode.Annot;
                if (!success)
                    showInfo(getString(R.string.no_text_selected));
                break;

            case Underline:
                if (pageView != null)
                    success = pageView.markupSelection(Annotation.Type.UNDERLINE);
                mTopBarMode = TopBarMode.Annot;
                if (!success)
                    showInfo(getString(R.string.no_text_selected));
                break;

            case StrikeOut:
                if (pageView != null)
                    success = pageView.markupSelection(Annotation.Type.STRIKEOUT);
                mTopBarMode = TopBarMode.Annot;
                if (!success)
                    showInfo(getString(R.string.no_text_selected));
                break;

            case Ink:
                if (pageView != null)
                    success = pageView.saveDraw();
                mTopBarMode = TopBarMode.Annot;
                if (!success)
                    showInfo(getString(R.string.nothing_to_save));
                break;
        }
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        mDocView.setMode(MuPDFReaderView.Mode.Viewing);
    }

    public void OnCancelSearchButtonClick(View v) {
        searchModeOff();
    }

    public void OnDeleteButtonClick(View v) {
        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
        if (pageView != null)
            pageView.deleteSelectedAnnotation();
        mTopBarMode = TopBarMode.Annot;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    public void OnCancelDeleteButtonClick(View v) {
        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
        if (pageView != null)
            pageView.deselectAnnotation();
        mTopBarMode = TopBarMode.Annot;
        mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.showSoftInput(mSearchText, 0);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
    }

    private void search(int direction) {
        hideKeyboard();
        int displayPage = mDocView.getDisplayedViewIndex();
        SearchTaskResult r = SearchTaskResult.get();
        int searchPage = r != null ? r.pageNumber : -1;
        mSearchTask.go(mSearchText.getText().toString(), direction, displayPage, searchPage);
    }

    @Override
    public boolean onSearchRequested() {
        if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
            hideButtons();
        } else {
            showButtons();
            searchModeOn();
        }
        return super.onSearchRequested();
    }

    private boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_pdf, menu);
        return true;
    }

    private static Uri getUriForFile(Context context, File file) {
        if (context == null || file == null) {
            throw new NullPointerException();
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(context.getApplicationContext(), "de.baumann.pdfcreator.pdfview.provider", file);
        } else {
            uri = Uri.fromFile(file);
        }
        return uri;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        String path = sharedPref.getString("pathPDF", "");
        String FileTitle = path.substring(path.lastIndexOf("/") + 1);
        File pdfFile = new File(path);

        int id = item.getItemId();

        if (id == R.id.action_share) {
            String text = getString(R.string.action_share_Text);

//            Uri myUri = Uri.fromFile(pdfFile);
            Uri myUri = getUriForFile(MuPDFActivity.this,pdfFile);
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("application/pdf");
            sharingIntent.putExtra(Intent.EXTRA_STREAM, myUri);
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, FileTitle);
            sharingIntent.putExtra(Intent.EXTRA_TEXT, text + " " + FileTitle);
            sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(sharingIntent, getString(R.string.action_share_with)));
        }

        if (id == R.id.action_folder) {
            Intent intent = new Intent(MuPDFActivity.this, Activity_files.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        }

        if (id == android.R.id.home) {
            Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage("de.baumann.pdfcreator");
            LaunchIntent.setAction("pdf_openFolder");
            LaunchIntent.putExtra("path", path);
            LaunchIntent.putExtra("name", FileTitle);
            startActivity(LaunchIntent);
            System.exit(1);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        PackageManager pm = getPackageManager();
        boolean isInstalled = isPackageInstalled("de.baumann.pdfcreator", pm);

        if (isInstalled) {
            menu.findItem(R.id.action_folder).setVisible(false);
        }

        if (sharedPref.getString("menu", "").equals("no")) {
            menu.findItem(R.id.action_share).setVisible(false);
        }

        if (mButtonsVisible && mTopBarMode != TopBarMode.Search) {
            hideButtons();
        } else {
            showButtons();
            searchModeOff();
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        if (core != null) {
            core.startAlerts();
            createAlertWaiter();
        }

        super.onStart();
    }

    @Override
    protected void onStop() {
        if (core != null) {
            destroyAlertWaiter();
            core.stopAlerts();
        }

        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (core != null && core.hasChanges()) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (which == AlertDialog.BUTTON_POSITIVE)
                        core.save();

                    finish();
                }
            };
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle(R.string.app_name);
            alert.setMessage(getString(R.string.document_has_changes_save_them_));
            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), listener);
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), listener);
            alert.show();
        } else {
            System.exit(1);
        }
    }

    @Override
    public void performPickFor(FilePicker picker) {
        mFilePicker = picker;
        Intent intent = new Intent(this, ChoosePDFActivity.class);
        intent.setAction(ChoosePDFActivity.PICK_KEY_FILE);
        startActivityForResult(intent, FILEPICK_REQUEST);
    }

}
