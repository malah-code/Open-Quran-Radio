/**
 * MainActivityFragment.java
 * Implements the main fragment of the main activity
 * This fragment is a list view of radio stations
 * <p>
 * This file is part of
 * TRANSISTOR - Radio App for Android
 * <p>
 * Copyright (c) 2015-17 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;

import org.y20k.transistor.core.Station;
import org.y20k.transistor.helpers.DialogAdd;
import org.y20k.transistor.helpers.DialogInitial;
import org.y20k.transistor.helpers.ImageHelper;
import org.y20k.transistor.helpers.LogHelper;
import org.y20k.transistor.helpers.NotificationHelper;
import org.y20k.transistor.helpers.PermissionHelper;
import org.y20k.transistor.helpers.SingletonProperties;
import org.y20k.transistor.helpers.SleepTimerService;
import org.y20k.transistor.helpers.StationFetcher;
import org.y20k.transistor.helpers.StorageHelper;
import org.y20k.transistor.helpers.TransistorKeys;
import org.y20k.transistor.sqlcore.StationsDbHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.y20k.transistor.R.id.buttonRefreshInitData;
import static org.y20k.transistor.sqlcore.StationsDbContract.searchSuggestIntentAction;


/**
 * MainActivityFragment class
 */
public final class MainActivityFragment extends Fragment {

    /* Define log tag */
    private static final String LOG_TAG = MainActivityFragment.class.getSimpleName();


    /* Main class variables */
    private Application mApplication;
    private Activity mActivity;
    private CollectionAdapter mCollectionAdapter = null;
    private File mFolder;
    private int mFolderSize;
    private View mRootView;
    private View mActionCallView;
    private RecyclerView mRecyclerView;
    private Button mButtonRefreshInitDataView;
    private RelativeLayout mRelativeEmptyView;
    private RecyclerView.LayoutManager mLayoutManager;
    private StaggeredGridLayoutManager mStaggeredGridLayoutManagerManager;
    private int RECYCLER_VIEW_LIST = 0;
    private int RECYCLER_VIEW_GRID = 1;

    private Parcelable mListState;
    private BroadcastReceiver mCollectionChangedReceiver;
    private BroadcastReceiver mImageChangeRequestReceiver;
    private BroadcastReceiver mSleepTimerStartedReceiver;
    private BroadcastReceiver mPlaybackStateChangedReceiver;
    private int mStationIDSelected;
    private int mTempStationID_Position;
    private Station mTempStation;
    private Uri mNewStationUri;
    private boolean mTwoPane;
    private boolean mPlayback;
    private boolean mSleepTimerRunning;
    private SleepTimerService mSleepTimerService;
    private String mSleepTimerNotificationMessage;
    private Snackbar mSleepTimerNotification;
    private ProgressDialog progressDialogLoading;
    private int mLayoutViewManager;

    /* Constructor (default) */
    public MainActivityFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        LogHelper.i("MainActivityFragment", "onCreate");

        super.onCreate(savedInstanceState);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        // fragment has options menu
        setHasOptionsMenu(true);

        // get activity and application contexts
        mActivity = getActivity();
        mApplication = mActivity.getApplication();

        // get notification message
        mSleepTimerNotificationMessage = mActivity.getString(R.string.snackbar_message_timer_set) + " ";

        // initiate sleep timer service
        mSleepTimerService = new SleepTimerService();

        // set list state null
        mListState = null;

        // initialize id of currently selected station
        mStationIDSelected = 0;

        // initialize temporary station image id
        mTempStationID_Position = -1;

        // initialize two pane
        mTwoPane = false;

        // load playback state
        loadAppState(mActivity);

        // get collection folder
        StorageHelper storageHelper = new StorageHelper(mActivity);
        mFolder = storageHelper.getCollectionDirectory();
        if (mFolder == null) {
            Toast.makeText(mActivity, mActivity.getString(R.string.toastalert_no_external_storage), Toast.LENGTH_LONG).show();
            mActivity.finish();
        }
        //get rows count from DB
        final StationsDbHelper mDbHelper = new StationsDbHelper(mActivity);
        mFolderSize = mDbHelper.GetStationsCount();//mFolder.listFiles().length;

        //progress bar loading (not used now, will be used in next versions to make loading progredd while import stations)
        progressDialogLoading = new ProgressDialog(mActivity);
        progressDialogLoading.setTitle("Loading Stations..");
        progressDialogLoading.setMessage("Please wait.");
        progressDialogLoading.setCancelable(false);

        // create collection adapter
        if (mCollectionAdapter == null) {
            mCollectionAdapter = new CollectionAdapter(mActivity, mFolder);
        }


    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LogHelper.i("MainActivityFragment", "onCreateView");
        // get list state from saved instance
        if (savedInstanceState != null) {
            mListState = savedInstanceState.getParcelable(TransistorKeys.INSTANCE_LIST_STATE);
        }

        // inflate root view from xml
        mRootView = inflater.inflate(R.layout.fragment_main, container, false);

        // get reference to action call view from inflated root view
        mActionCallView = mRootView.findViewById(R.id.main_actioncall_layout);

        // get reference to recycler list view from inflated root view
        mRecyclerView = (RecyclerView) mRootView.findViewById(R.id.main_recyclerview_collection);


        mButtonRefreshInitDataView = (Button) mRootView.findViewById(buttonRefreshInitData);
        mRelativeEmptyView = (RelativeLayout) mRootView.findViewById(R.id.relativeEmpty);

        //btn refresh code
        mButtonRefreshInitDataView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runInitialDataRefreshIfFirstTime();
            }
        });

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        // TODO check if necessary here
        //mRecyclerView.setHasFixedSize(true);

        // set animator
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(mActivity);
        mStaggeredGridLayoutManagerManager = new StaggeredGridLayoutManager(2, 1);
        if (mLayoutViewManager == RECYCLER_VIEW_LIST) {
            mRecyclerView.setLayoutManager(mLayoutManager);
        } else {
            mRecyclerView.setLayoutManager(mStaggeredGridLayoutManagerManager);
        }

        // attach adapter to list view
        mRecyclerView.setAdapter(mCollectionAdapter);

        return mRootView;
    }

    //@AddTrace(name = "runInitialDataRefreshIfFirstTime", enabled = true/*Optional*/)
    public void runInitialDataRefreshIfFirstTime() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        Boolean initialDataLoaded = settings.getBoolean(TransistorKeys.PREF_INITIAL_DATA_LOADED, false);

        if (!initialDataLoaded) {
            StationsDbHelper dbHelper = new StationsDbHelper(mActivity);
            //dbHelper.DeleteAllStations();

            LogHelper.v(LOG_TAG, "initialDataLoaded = false");

            if (dbHelper.GetStationsCount() == 0) {
                LogHelper.v(LOG_TAG, "dbHelper.GetStationsCount() == 0");

                //if no records only we will try to import init data
                //First Show Init Layout and refresh button
                mRelativeEmptyView.setVisibility(View.VISIBLE);
                //load XML initial data
                //check for internet connection
                if (isOnline()) {
                    LogHelper.v(LOG_TAG, "User is Online.");
                    Toast.makeText(mActivity, "You're Online", Toast.LENGTH_SHORT).show();
                    //open new dialog to import/download init XML data
                    DialogInitial dialogInit = new DialogInitial(mActivity, mFolder);
                    dialogInit.show();
                } else {
                    LogHelper.v(LOG_TAG, "User is not Online.");
                    //Toast.makeText(this, "You're not Online", Toast.LENGTH_SHORT).show();
                    Toast.makeText(mActivity, "You're not Online :(", Toast.LENGTH_SHORT).show();
                }
            } else {
                //if there are records we shouldn't try again import init data
                save_PREF_INITIAL_DATA_LOADED_State(mActivity);
                LogHelper.v(LOG_TAG, "there are records we shouldn't try again import init data");
            }
        } else {
            toggleActionCall();
        }
    }

    /* Saves app state to save_PREF_INITIAL_DATA_LOADED_State */
    private void save_PREF_INITIAL_DATA_LOADED_State(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(TransistorKeys.PREF_INITIAL_DATA_LOADED, true);
        editor.apply();
        LogHelper.v(LOG_TAG, "Saving state. PREF_INITIAL_DATA_LOADED = true");
    }

    public boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }


    @Override
    public void onResume() {
        LogHelper.i("MainActivityFragment", "onResume");
        super.onResume();
        Log.v(LOG_TAG + "debugCollectionView", "start onResume");

        // refresh app state
        loadAppState(mActivity);

        // update collection adapter
        mCollectionAdapter.setTwoPane(mTwoPane);
        mCollectionAdapter.refresh();
        if (mCollectionAdapter.getItemCount() > 0) {
            mCollectionAdapter.setStationIDSelected(mStationIDSelected, mPlayback, false);
        }


        // handles the activity's intent
        Intent intent = mActivity.getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            handleStreamingLink(intent);
        } else if (TransistorKeys.ACTION_SHOW_PLAYER.equals(intent.getAction())) {
            handleShowPlayer(intent);
        }
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // Handle the normal search query case
            String query = intent.getStringExtra(SearchManager.QUERY);
        } else if (searchSuggestIntentAction.equals(intent.getAction())) {
            // Handle a suggestions click (because the suggestions all use ACTION_VIEW)
            String data = intent.getDataString();
            if (data != null && !data.isEmpty()) {
                int pos = mCollectionAdapter.getItemPosition(Long.parseLong(data));
                //simulate click item
                mCollectionAdapter.handleSingleClick(pos, null);
                //make the item selected
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                if (settings.getInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST) == RECYCLER_VIEW_LIST) {
                    mLayoutManager.scrollToPosition(pos);
                } else {
                    mStaggeredGridLayoutManagerManager.scrollToPosition(pos);
                }
            }
            if(mActivity instanceof MainActivity){
                ((MainActivity)mActivity).ClearSearch();
            }
        }

        // check if folder content has been changed
        //get rows count from DB
        StationsDbHelper mDbHelper = new StationsDbHelper(mActivity);
        int folderSize = mDbHelper.GetStationsCount();//mFolder.listFiles().length;
        if (mFolderSize != folderSize) {
            mFolderSize = folderSize;
            mCollectionAdapter = new CollectionAdapter(mActivity, mFolder);
            mRecyclerView.setAdapter(mCollectionAdapter);
        }

        // show call to action, if necessary
        toggleActionCall();

        // show notification bar if timer is running
        if (mSleepTimerRunning) {
            showSleepTimerNotification(-1);
        }

        //check if initial xml data loaded
        runInitialDataRefreshIfFirstTime();
    }

    @Override
    public void onStart() {
        LogHelper.i("MainActivityFragment", "onStart");
        super.onStart();

        // initialize broadcast receivers
        initializeBroadcastReceivers();
    }

    @Override
    public void onStop() {
        LogHelper.i("MainActivityFragment", "onStop");
        super.onStop();
        // unregister Broadcast Receivers
        unregisterBroadcastReceivers();
    }

    @Override
    public void onDestroy() {
        LogHelper.i("MainActivityFragment", "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        LogHelper.i("MainActivityFragment", "onDestroyView");
        super.onDestroyView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            // CASE TIMER
            case R.id.menu_timer:
                handleMenuSleepTimerClick();
                return true;

            // CASE ADD
            case R.id.menu_add:

                DialogAdd dialog = new DialogAdd(mActivity, mFolder);
                dialog.show();
                return true;

            // CASE ABOUT
            case R.id.menu_about:
                // get title and content
                String aboutTitle = mActivity.getString(R.string.header_about);
                // put title and content into intent and start activity
                Intent aboutIntent = new Intent(mActivity, InfosheetActivity.class);
                aboutIntent.putExtra(TransistorKeys.EXTRA_INFOSHEET_TITLE, aboutTitle);
                aboutIntent.putExtra(TransistorKeys.EXTRA_INFOSHEET_CONTENT, TransistorKeys.INFOSHEET_CONTENT_ABOUT);
                startActivity(aboutIntent);
                return true;

            // CASE HOWTO
            case R.id.menu_howto:
                // get title and content
                String howToTitle = mActivity.getString(R.string.header_howto);
                // put title and content into intent and start activity
                Intent howToIntent = new Intent(mActivity, InfosheetActivity.class);
                howToIntent.putExtra(TransistorKeys.EXTRA_INFOSHEET_TITLE, howToTitle);
                howToIntent.putExtra(TransistorKeys.EXTRA_INFOSHEET_CONTENT, TransistorKeys.INFOSHEET_CONTENT_HOWTO);
                startActivity(howToIntent);
                return true;

            // CASE menu_grid_view
            case R.id.menu_grid_view:
                //change the view
                mRecyclerView.setLayoutManager(mStaggeredGridLayoutManagerManager);
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_GRID);
                editor.apply();
                return true;
            // CASE menu_grid_view
            case R.id.menu_list_view:
                //change the view
                mRecyclerView.setLayoutManager(mLayoutManager);
                SharedPreferences settings_v = PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor_v = settings_v.edit();
                editor_v.putInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST);
                editor_v.apply();
                return true;
            // CASE DEFAULT
            default:
                return super.onOptionsItemSelected(item);
        }

    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        LogHelper.i("MainActivityFragment", "onSaveInstanceState");


        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        if (settings.getInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST) == RECYCLER_VIEW_LIST) {
            // save list view position
            mListState = mLayoutManager.onSaveInstanceState();
        } else {
            // save list view position
            mListState = mStaggeredGridLayoutManagerManager.onSaveInstanceState();
        }


        outState.putParcelable(TransistorKeys.INSTANCE_LIST_STATE, mListState);
        super.onSaveInstanceState(outState);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        LogHelper.i("MainActivityFragment", "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case TransistorKeys.PERMISSION_REQUEST_IMAGE_PICKER_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted - get system picker for images
                    Intent pickImageIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    mActivity.startActivityForResult(pickImageIntent, TransistorKeys.REQUEST_LOAD_IMAGE);
                } else {
                    // permission denied
                    Toast.makeText(mActivity, mActivity.getString(R.string.toastalert_permission_denied) + " READ_EXTERNAL_STORAGE", Toast.LENGTH_LONG).show();
                }
                break;
            }

            case TransistorKeys.PERMISSION_REQUEST_STATION_FETCHER_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted - fetch station from given Uri
                    fetchNewStation(mNewStationUri);
                } else {
                    // permission denied
                    Toast.makeText(mActivity, mActivity.getString(R.string.toastalert_permission_denied) + " READ_EXTERNAL_STORAGE", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogHelper.i("MainActivityFragment", "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);

        // retrieve selected image Uri from image picker
        Uri newImageUri = null;
        if (null != data) {
            newImageUri = data.getData();
        }

        if (requestCode == TransistorKeys.REQUEST_LOAD_IMAGE && resultCode == Activity.RESULT_OK && newImageUri != null) {

            ImageHelper imageHelper = new ImageHelper(newImageUri, mActivity, 500, 500);
            Bitmap newImage = imageHelper.getInputImage();

            if (newImage != null && mTempStationID_Position != -1) {
                // write image to storage
                File stationImageFile = mTempStation.getStationImageFileReference(mFolder);//get  station file with correct path according to UniqueID of the station
                try (FileOutputStream out = new FileOutputStream(stationImageFile)) {
                    newImage.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                    //remve image from fresco cache
                    ImagePipeline imagePipeline = Fresco.getImagePipeline();
                    imagePipeline.evictFromCache(Uri.parse(stationImageFile.toURI().toString()));
                } catch (IOException e) {
                    LogHelper.e(LOG_TAG, "Unable to save: " + newImage.toString());
                }

                // update adapter
                mCollectionAdapter.notifyItemChanged(mTempStationID_Position);
                Toast.makeText(mApplication, "Image Updated", Toast.LENGTH_SHORT).show();
            } else {
                LogHelper.e(LOG_TAG, "Unable to get image from media picker. Uri was:  " + newImageUri.toString());
            }

        } else {
            LogHelper.e(LOG_TAG, "Unable to get image from media picker. Did not receive an Uri");
        }
    }


    /* Show or hide call to action view if necessary */
    private void toggleActionCall() {
        // show call to action, if necessary
        if (mCollectionAdapter.getItemCount() == 0) {
            mRelativeEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
            if (mTwoPane && mActivity instanceof MainActivity) {
                ((MainActivity) mActivity).removePlayFragment();
            }
        } else {
            mRelativeEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        Boolean initialDataLoaded = settings.getBoolean(TransistorKeys.PREF_INITIAL_DATA_LOADED, false);

        if (!initialDataLoaded) {
            mButtonRefreshInitDataView.setVisibility(View.VISIBLE);
        } else {
            mButtonRefreshInitDataView.setVisibility(View.GONE);

        }
    }


    /* Check permissions and start image picker */
    private void selectFromImagePicker() {
        // request read permissions
        PermissionHelper permissionHelper = new PermissionHelper(mActivity, mRootView);
        if (permissionHelper.requestReadExternalStorage(TransistorKeys.PERMISSION_REQUEST_IMAGE_PICKER_READ_EXTERNAL_STORAGE)) {
            // get system picker for images
            Intent pickImageIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(pickImageIntent, TransistorKeys.REQUEST_LOAD_IMAGE);
        }
    }


    /* Handles tap on streaming link */
    private void handleStreamingLink(Intent intent) {
        mNewStationUri = intent.getData();

        // clear the intent
        intent.setAction("");

        // check for null and type "http"
        if (mNewStationUri != null && mNewStationUri.getScheme().startsWith("http")) {
            // download and add new station
            fetchNewStation(mNewStationUri);
        } else if (mNewStationUri != null && mNewStationUri.getScheme().startsWith("file")) {
            // check for read permission
            PermissionHelper permissionHelper = new PermissionHelper(mActivity, mRootView);
            if (permissionHelper.requestReadExternalStorage(TransistorKeys.PERMISSION_REQUEST_STATION_FETCHER_READ_EXTERNAL_STORAGE)) {
                // read and add new station
                fetchNewStation(mNewStationUri);
            }
        }
        // unsuccessful - log failure
        else {
            LogHelper.v(LOG_TAG, "Received an empty intent");
        }
    }


    /* Handles intent to show player from notification or from shortcut */
    private void handleShowPlayer(Intent intent) {
        // get station from intent
        Station station = null;
        if (intent.hasExtra(TransistorKeys.EXTRA_STATION)) {
            // get station from notification
            station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
        } else if (intent.hasExtra(TransistorKeys.EXTRA_STREAM_URI)) {
            // get Uri of station from home screen shortcut
            station = mCollectionAdapter.findStation(Uri.parse(intent.getStringExtra(TransistorKeys.EXTRA_STREAM_URI)));
        } else if (intent.hasExtra(TransistorKeys.EXTRA_LAST_STATION) && intent.getBooleanExtra(TransistorKeys.EXTRA_LAST_STATION, false)) {
            // try to get last station
            loadAppState(mActivity);
            long station_IDLast = SingletonProperties.getInstance().getLastRunningStation_ID();
            if (station_IDLast > -1 && mCollectionAdapter.getItemCount() > 0) {
                int posTmp = mCollectionAdapter.getItemPosition(station_IDLast);
                if (posTmp > -1) {
                    station = mCollectionAdapter.getStation(posTmp);
                }
            }
        }

        if (station == null) {
            Toast.makeText(mActivity, getString(R.string.toastalert_station_not_found), Toast.LENGTH_LONG).show();
        }

        // get playback action from intent
        boolean startPlayback;
        if (intent.hasExtra(TransistorKeys.EXTRA_PLAYBACK_STATE)) {
            startPlayback = intent.getBooleanExtra(TransistorKeys.EXTRA_PLAYBACK_STATE, false);
        } else {
            startPlayback = false;
        }

        // prepare arguments or intent
        if (mTwoPane && station != null) {
            mCollectionAdapter.setStationIDSelected(mCollectionAdapter.getItemPosition(station._ID), station.getPlaybackState(), startPlayback);
        } else if (station != null) {
            // start player activity - on phone
            Intent playerIntent = new Intent(mActivity, PlayerActivity.class);
            playerIntent.setAction(TransistorKeys.ACTION_SHOW_PLAYER);
            playerIntent.putExtra(TransistorKeys.EXTRA_STATION, station);
            playerIntent.putExtra(TransistorKeys.EXTRA_PLAYBACK_STATE, startPlayback);
            startActivity(playerIntent);
        }
    }


    /* Handles tap timer icon in actionbar */
    private void handleMenuSleepTimerClick() {
        // load app state
        loadAppState(mActivity);

        // set duration
        long duration = 900000; // equals 15 minutes

        // CASE: No station is playing, no timer is running
        if (!mPlayback && !mSleepTimerRunning) {
            // unable to start timer
            Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_timer_start_unable), Toast.LENGTH_SHORT).show();
        }
        // CASE: A station is playing, no sleep timer is running
        else if (mPlayback && !mSleepTimerRunning) {
            startSleepTimer(duration);
            Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_timer_activated), Toast.LENGTH_SHORT).show();
        }
        // CASE: A station is playing, Sleep timer is running
        else if (mPlayback) {
            startSleepTimer(duration);
            Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_timer_duration_increased) + " [+" + getReadableTime(duration) + "]", Toast.LENGTH_SHORT).show();
        }

    }


    /* Starts timer service and notification */
    private void startSleepTimer(long duration) {
        // start timer service
        if (mSleepTimerService == null) {
            mSleepTimerService = new SleepTimerService();
        }
        mSleepTimerService.startActionStart(mActivity, duration);

        // show timer notification
        showSleepTimerNotification(duration);
        mSleepTimerRunning = true;
        LogHelper.v(LOG_TAG, "Starting timer service and notification.");
    }


    /* Stops timer service and notification */
    private void stopSleepTimer() {
        // stop timer service
        if (mSleepTimerService != null) {
            mSleepTimerService.startActionStop(mActivity);
        }
        // cancel notification
        if (mSleepTimerNotification != null && mSleepTimerNotification.isShown()) {
            mSleepTimerNotification.dismiss();
        }
        mSleepTimerRunning = false;
        LogHelper.v(LOG_TAG, "Stopping timer service and notification.");
        Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_timer_cancelled), Toast.LENGTH_SHORT).show();
    }


    /* Shows notification for a running sleep timer */
    private void showSleepTimerNotification(long remainingTime) {

        // set snackbar message
        String message;
        if (remainingTime > 0) {
            message = mSleepTimerNotificationMessage + getReadableTime(remainingTime);
        } else {
            message = mSleepTimerNotificationMessage;
        }

        // show snackbar
        mSleepTimerNotification = Snackbar.make(mRootView, message, Snackbar.LENGTH_INDEFINITE);
        mSleepTimerNotification.setAction(R.string.dialog_generic_button_cancel, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // stop sleep timer service
                mSleepTimerService.startActionStop(mActivity);
                mSleepTimerRunning = false;
                saveAppState(mActivity);
                // notify user
                Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_timer_cancelled), Toast.LENGTH_SHORT).show();
                LogHelper.v(LOG_TAG, "Sleep timer cancelled.");
            }
        });
        mSleepTimerNotification.show();

    }


    /* Translates milliseconds into minutes and seconds */
    private String getReadableTime(long remainingTime) {
        return String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(remainingTime),
                TimeUnit.MILLISECONDS.toSeconds(remainingTime) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(remainingTime)));
    }


    /* Loads app state from preferences */
    private void loadAppState(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        mStationIDSelected = settings.getInt(TransistorKeys.PREF_STATION_ID_SELECTED, 0);
        mPlayback = settings.getBoolean(TransistorKeys.PREF_PLAYBACK, false);
        mTwoPane = settings.getBoolean(TransistorKeys.PREF_TWO_PANE, false);
        mSleepTimerRunning = settings.getBoolean(TransistorKeys.PREF_TIMER_RUNNING, false);
        mLayoutViewManager = settings.getInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST);
        LogHelper.v(LOG_TAG, "Loading state (" + SingletonProperties.getInstance().CurrentSelectedStation_ID + " / " + SingletonProperties.getInstance().getLastRunningStation_ID() + " / " + mPlayback + ")");
    }


    /* Saves app state to SharedPreferences */
    private void saveAppState(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(TransistorKeys.PREF_STATION_ID_CURRENTLY_PLAYING, SingletonProperties.getInstance().CurrentSelectedStation_ID);
        editor.putBoolean(TransistorKeys.PREF_PLAYBACK, mPlayback);
        editor.putBoolean(TransistorKeys.PREF_TIMER_RUNNING, mSleepTimerRunning);
        editor.apply();
        LogHelper.v(LOG_TAG, "Saving state (" + SingletonProperties.getInstance().CurrentSelectedStation_ID + " / " + SingletonProperties.getInstance().getLastRunningStation_ID() + " / " + mPlayback + ")");
    }


    /* Fetch new station with given Uri */
    private void fetchNewStation(Uri stationUri) {
        // download and add new station
        StationFetcher stationFetcher = new StationFetcher(mActivity, mFolder, stationUri);
        stationFetcher.execute();
    }


    /* Initializes broadcast receivers for onCreate */
    private void initializeBroadcastReceivers() {

        // RECEIVER: state of playback has changed
        mPlaybackStateChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(TransistorKeys.EXTRA_PLAYBACK_STATE_CHANGE)) {
                    handlePlaybackStateChanges(intent);
                }
            }
        };
        IntentFilter playbackStateChangedIntentFilter = new IntentFilter(TransistorKeys.ACTION_PLAYBACK_STATE_CHANGED);
        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mPlaybackStateChangedReceiver, playbackStateChangedIntentFilter);

        // RECEIVER: station added, deleted, or changed
        mCollectionChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && intent.hasExtra(TransistorKeys.EXTRA_COLLECTION_CHANGE)) {
                    handleCollectionChanges(intent);
                }
            }
        };
        IntentFilter collectionChangedIntentFilter = new IntentFilter(TransistorKeys.ACTION_COLLECTION_CHANGED);
        LocalBroadcastManager.getInstance(mApplication).registerReceiver(mCollectionChangedReceiver, collectionChangedIntentFilter);

        // RECEIVER: listen for request to change station image
        mImageChangeRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION) && intent.hasExtra(TransistorKeys.EXTRA_STATION_Position_ID)) {
                    // get station and id from intent
                    mTempStation = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                    mTempStationID_Position = intent.getIntExtra(TransistorKeys.EXTRA_STATION_Position_ID, -1);
                    // start image picker
                    selectFromImagePicker();
                }
            }
        };
        IntentFilter imageChangeRequestIntentFilter = new IntentFilter(TransistorKeys.ACTION_IMAGE_CHANGE_REQUESTED);
        LocalBroadcastManager.getInstance(mApplication).registerReceiver(mImageChangeRequestReceiver, imageChangeRequestIntentFilter);

        // RECEIVER: sleep timer service sends updates
        mSleepTimerStartedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // get duration from intent
                long remaining = intent.getLongExtra(TransistorKeys.EXTRA_TIMER_REMAINING, 0);
                if (mSleepTimerNotification != null && remaining > 0) {
                    // update existing notification
                    mSleepTimerNotification.setText(mSleepTimerNotificationMessage + getReadableTime(remaining));
                } else if (mSleepTimerNotification != null) {
                    // cancel notification
                    mSleepTimerNotification.dismiss();
                    // save state and update user interface
                    mPlayback = false;
                    mSleepTimerRunning = false;
                    saveAppState(mActivity);
                }

            }
        };
        IntentFilter sleepTimerIntentFilter = new IntentFilter(TransistorKeys.ACTION_TIMER_RUNNING);
        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mSleepTimerStartedReceiver, sleepTimerIntentFilter);

    }


    /* Unregisters broadcast receivers */
    private void unregisterBroadcastReceivers() {
        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mPlaybackStateChangedReceiver);
        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mCollectionChangedReceiver);
        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mImageChangeRequestReceiver);
        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mSleepTimerStartedReceiver);
    }

    /* Handles changes in state of playback, eg. start, stop, loading stream */
    private void handlePlaybackStateChanges(Intent intent) {
        switch (intent.getIntExtra(TransistorKeys.EXTRA_PLAYBACK_STATE_CHANGE, 1)) {
            // CASE: playback was stopped
            case TransistorKeys.PLAYBACK_STOPPED:
                // load app state
                loadAppState(mActivity);
                // stop sleep timer
                if (mSleepTimerRunning && mSleepTimerService != null) {
                    stopSleepTimer();
                }
                break;
        }
    }


    /* Handles adding, deleting and renaming of station */
    private void handleCollectionChanges(Intent intent) {

        // load app state
        loadAppState(mActivity);

        int newStationPosition = 0;

        switch (intent.getIntExtra(TransistorKeys.EXTRA_COLLECTION_CHANGE, 1)) {

            // CASE: station was added
            case TransistorKeys.STATION_ADDED:
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION)) {

                    // get station from intent
                    Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);

                    // add station to adapter, scroll to new position and update adapter
                    if (station != null && station.StreamURI != null && station.TITLE != null) {
                        newStationPosition = mCollectionAdapter.add(station);
                    } else if (intent.hasExtra(TransistorKeys.EXTRA_STATIONS)) {
                        //this is added as a batch from XML
                        ArrayList<Station> mInsertedStations = intent.getParcelableArrayListExtra(TransistorKeys.EXTRA_STATIONS);
                        if (mInsertedStations != null && mInsertedStations.size() > 0) {
                            for (int i = 0; i < mInsertedStations.size(); i++) {
                                newStationPosition = mCollectionAdapter.add(mInsertedStations.get(i));

                            }
                        }
                    }

                    if (mCollectionAdapter.getItemCount() > 0) {
                        toggleActionCall();
                    }

                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                    if (settings.getInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST) == RECYCLER_VIEW_LIST) {
                        // save list view position
                        mLayoutManager.scrollToPosition(newStationPosition);
                    } else {
                        // save list view position
                        mStaggeredGridLayoutManagerManager.scrollToPosition(newStationPosition);
                    }


                    mCollectionAdapter.setStationIDSelected(newStationPosition, mPlayback, false);
                    mCollectionAdapter.notifyDataSetChanged();
                }
                break;

            // CASE: station was renamed
            case TransistorKeys.STATION_RENAMED:
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION_NEW_NAME) && intent.hasExtra(TransistorKeys.EXTRA_STATION) && intent.hasExtra(TransistorKeys.EXTRA_STATION_Position_ID)) {

                    // get new name, station and station ID from intent
                    String newStationName = intent.getStringExtra(TransistorKeys.EXTRA_STATION_NEW_NAME);
                    Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                    int stationID_Position = intent.getIntExtra(TransistorKeys.EXTRA_STATION_Position_ID, 0);

                    // update notification
                    if (station.getPlaybackState()) {
                        NotificationHelper.update(station, stationID_Position, null, null);
                    }

                    // change station within in adapter, scroll to new position and update adapter
                    newStationPosition = mCollectionAdapter.rename(newStationName, station, stationID_Position);

                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                    if (settings.getInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST) == RECYCLER_VIEW_LIST) {
                        // save list view position
                        mLayoutManager.scrollToPosition(newStationPosition);
                    } else {
                        // save list view position
                        mStaggeredGridLayoutManagerManager.scrollToPosition(newStationPosition);
                    }

                    mCollectionAdapter.setStationIDSelected(newStationPosition, mPlayback, false);
                    mCollectionAdapter.notifyDataSetChanged();
                }
                break;
            case TransistorKeys.STATION_CHANGED_FAVORIT:
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION_FAVORIT_VALUE) && intent.hasExtra(TransistorKeys.EXTRA_STATION) && intent.hasExtra(TransistorKeys.EXTRA_STATION_Position_ID)) {

                    // get new Fav Value, station and station ID from intent
                    int newStationFavoritValue = intent.getIntExtra(TransistorKeys.EXTRA_STATION_FAVORIT_VALUE, 0);
                    Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                    int stationID_Position = intent.getIntExtra(TransistorKeys.EXTRA_STATION_Position_ID, 0);

                    // change station within in adapter, scroll to new position and update adapter
                    newStationPosition = mCollectionAdapter.changeFavoritValue(newStationFavoritValue, station, stationID_Position);

                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                    if (settings.getInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST) == RECYCLER_VIEW_LIST) {
                        // save list view position
                        mLayoutManager.scrollToPosition(newStationPosition);
                    } else {
                        // save list view position
                        mStaggeredGridLayoutManagerManager.scrollToPosition(newStationPosition);
                    }

                    mCollectionAdapter.setStationIDSelected(newStationPosition, mPlayback, false);
                    mCollectionAdapter.notifyDataSetChanged();
                }
                break;
            case TransistorKeys.STATION_CHANGED_RATING:
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION)) {

                    // get new name, station and station ID from intent
                    Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                    int stPossition = mCollectionAdapter.getItemPosition(station._ID);

                    // update notification
                    if (station.getPlaybackState()) {
                        NotificationHelper.update(station, stPossition, null, null);
                    }

                    // change station within in adapter, scroll to new position and update adapter
                    newStationPosition = mCollectionAdapter.updateItemAtPosition(station, stPossition);

                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                    if (settings.getInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST) == RECYCLER_VIEW_LIST) {
                        // save list view position
                        mLayoutManager.scrollToPosition(stPossition);
                    } else {
                        // save list view position
                        mStaggeredGridLayoutManagerManager.scrollToPosition(stPossition);
                    }

                    mCollectionAdapter.setStationIDSelected(stPossition, mPlayback, false);
                    // change station within in adapter, scroll to new position and update adapter
                    mCollectionAdapter.notifyItemChanged(stPossition);
                }
                break;
            case TransistorKeys.STATION_CHANGED_IMAGE:
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION) && intent.hasExtra(TransistorKeys.EXTRA_STATION_DB_ID)) {

                    // get new name, station and station ID from intent
                    Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                    int stationID = intent.getIntExtra(TransistorKeys.EXTRA_STATION_DB_ID, 0);
                    int stPossition = mCollectionAdapter.getItemPosition(stationID);

                    // update notification
                    if (station.getPlaybackState()) {
                        NotificationHelper.update(station, stPossition, null, null);
                    }

                    // change station within in adapter, scroll to new position and update adapter
                    mCollectionAdapter.notifyItemChanged(stPossition);
                }
                break;
            // CASE: station was deleted
            case TransistorKeys.STATION_DELETED:
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION) && intent.hasExtra(TransistorKeys.EXTRA_STATION_Position_ID)) {
                    // get station and station ID from intent
                    Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                    int stPossition = mCollectionAdapter.getItemPosition(station._ID);
                    int stationID_position = intent.getIntExtra(TransistorKeys.EXTRA_STATION_Position_ID, 0);

                    // dismiss notification
                    NotificationHelper.stop();

                    if (station.getPlaybackState()) {
                        // stop player service and notification using intent
                        Intent i = new Intent(mActivity, PlayerService.class);
                        i.setAction(TransistorKeys.ACTION_DISMISS);
                        mActivity.startService(i);
                        LogHelper.v(LOG_TAG, "Stopping player service.");
                    }

                    // remove station from adapter and update
                    newStationPosition = mCollectionAdapter.delete(station);

                    if (newStationPosition == -1 || mCollectionAdapter.getItemCount() == 0) {
                        // show call to action
                        toggleActionCall();
                    } else {
                        // scroll to new position
                        mCollectionAdapter.setStationIDSelected(newStationPosition, mPlayback, false);

                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                        if (settings.getInt(TransistorKeys.PREF_LAYOUT_VIEW_MANAGER, RECYCLER_VIEW_LIST) == RECYCLER_VIEW_LIST) {
                            // save list view position
                            mLayoutManager.scrollToPosition(newStationPosition);
                        } else {
                            // save list view position
                            mStaggeredGridLayoutManagerManager.scrollToPosition(newStationPosition);
                        }
                    }

                    //mCollectionAdapter.notifyItemRemoved(stPossition); //not working well (try to know why)
                    mCollectionAdapter.notifyItemChanged(stPossition);
                }
                break;
        }

    }

}
