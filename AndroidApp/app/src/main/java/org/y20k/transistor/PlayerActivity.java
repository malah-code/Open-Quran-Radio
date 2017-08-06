/**
 * PlayerActivity.java
 * Implements the app's player activity
 * The player activity sets up the now playing view for phone  mode and inflates a menu bar menu
 * <p>
 * This file is part of
 * TRANSISTOR - Radio App for Android
 * <p>
 * Copyright (c) 2015-17 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.view.DraweeTransition;
import com.facebook.drawee.view.SimpleDraweeView;

import org.y20k.transistor.core.Station;
import org.y20k.transistor.helpers.LogHelper;
import org.y20k.transistor.helpers.TransistorKeys;

import java.io.File;


/**
 * PlayerActivity class
 */
public final class PlayerActivity extends AppCompatActivity   {

    /* Define log tag */
    private static final String LOG_TAG = PlayerActivity.class.getSimpleName();
    private boolean mPlayback;
    private FloatingActionButton fabPlay;
    private FloatingActionButton fabPlay_nsNestedScrollView;
    private BroadcastReceiver mPlaybackStateChangedReceiver;
    private BroadcastReceiver mCollectionChangedReceiver;
    private Station mStation;
    private TextView txtSubTitleView;
    private SimpleDraweeView backdrop;
    private CollapsingToolbarLayout collapsingToolbarLayout;
    private AppBarLayout mAppBarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        // set content view
        setContentView(R.layout.activity_player);

        // get intent
        Intent intent = getIntent();

        //Mal:toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        txtSubTitleView = (TextView) findViewById(R.id.txtSubTitle);
        backdrop = (SimpleDraweeView) findViewById(R.id.backdrop);


        fabPlay = (FloatingActionButton) findViewById(R.id.fabPlay);
        fabPlay_nsNestedScrollView = (FloatingActionButton) findViewById(R.id.fabPlay_nsNestedScrollView);
        mAppBarLayout = (AppBarLayout) findViewById(R.id.appbar);

        //This part to detect if toolbar collapsed then move pay button to the bottom of the activity
        mAppBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (Math.abs(verticalOffset)-appBarLayout.getTotalScrollRange() == 0)
                {
                    //  Collapsed
                    LogHelper.v(LOG_TAG, "addOnOffsetChangedListener COLLAPSED");
                    fabPlay_nsNestedScrollView.setVisibility(View.VISIBLE);
                }
                else
                {
                    //Expanded
                    LogHelper.v(LOG_TAG, "addOnOffsetChangedListener Expanded");
                    fabPlay_nsNestedScrollView.setVisibility(View.GONE);
                }
            }
        });
        // CASE: show player in phone mode
        if (intent != null && TransistorKeys.ACTION_SHOW_PLAYER.equals(intent.getAction())) {

            // get station from intent

            if (intent.hasExtra(TransistorKeys.EXTRA_STATION)) {
                mStation = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                setStationImageUi();
                setStationTitleUi();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    //to fix transaction problem with Fresco (Facebook library)
                    getWindow().setSharedElementEnterTransition(DraweeTransition.createTransitionSet(ScalingUtils.ScaleType.CENTER_CROP,
                            ScalingUtils.ScaleType.CENTER_CROP));

                    getWindow().setSharedElementReturnTransition(DraweeTransition.createTransitionSet(ScalingUtils.ScaleType.CENTER_CROP,
                            ScalingUtils.ScaleType.CENTER_CROP));
                }
            } else {
                mStation = null;
            }

            // get id of station from intent
            int stationID_Position = 0;
            if (intent.hasExtra(TransistorKeys.EXTRA_STATION_Position_ID)) {
                stationID_Position = intent.getIntExtra(TransistorKeys.EXTRA_STATION_Position_ID, 0);
            }

            // get playback action from intent (if started from shortcut)
            boolean startPlayback;
            if (intent.hasExtra(TransistorKeys.EXTRA_PLAYBACK_STATE)) {
                startPlayback = intent.getBooleanExtra(TransistorKeys.EXTRA_PLAYBACK_STATE, false);

                // enable the Up button
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setDisplayHomeAsUpEnabled(true);
                }

            } else {
                startPlayback = false;
            }

            // create bundle for player activity fragment
            Bundle args = new Bundle();
            args.putParcelable(TransistorKeys.ARG_STATION, mStation);
            args.putInt(TransistorKeys.ARG_STATION_ID, stationID_Position);
            args.putBoolean(TransistorKeys.ARG_PLAYBACK, startPlayback);

            PlayerActivityFragment playerActivityFragment = new PlayerActivityFragment();
            playerActivityFragment.setArguments(args);

            getFragmentManager().beginTransaction()
                    .add(R.id.player_container, playerActivityFragment, getString(R.string.playerFragmentTag))
                    .commit();


            //update FAB
            if (mStation != null) {
                UpdateUiStatus(mStation);
                fabPlay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onFabClick();
                    }
                });
                fabPlay_nsNestedScrollView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onFabClick();
                    }
                });
            }
        }

        // initialize broadcast receivers
        initializeBroadcastReceivers();
    }

    private void onFabClick() {
        PlayerActivityFragment fragment = (PlayerActivityFragment)
                getFragmentManager().findFragmentByTag(getString(R.string.playerFragmentTag));
        fragment.handlePlaybackButtonClick();
    }

    private void setStationTitleUi() {
        //set title subtitle
        getSupportActionBar().setTitle(mStation.TITLE);
        collapsingToolbarLayout.setTitle(mStation.TITLE);
        getSupportActionBar().setSubtitle(mStation.SUBTITLE);
        txtSubTitleView.setText(mStation.SUBTITLE);
    }

    private void setStationImageUi() {
        // set station image
        File stationImageFile = mStation.getStationImage(this);
        if (stationImageFile != null && stationImageFile.exists()) {
            backdrop.setImageURI(stationImageFile.toURI().toString());//.setImageBitmap(stationImageSmall);
        } else if (mStation.IMAGE_PATH != null && mStation.IMAGE_PATH != "") {
            backdrop.setImageURI(mStation.IMAGE_PATH);//.setImageBitmap(stationImageSmall);
        }
    }

    public void UpdateUiStatus(Station station) {
        mPlayback = station.getPlaybackState();
        if (mPlayback) {
            // this station is running
            // change playback button image to stop
            fabPlay.setImageResource(R.drawable.smbl_stop);
            fabPlay_nsNestedScrollView.setImageResource(R.drawable.smbl_stop);
        } else {
            // playback stopped
            // change playback button image to play
            fabPlay.setImageResource(R.drawable.smbl_play);
            fabPlay_nsNestedScrollView.setImageResource(R.drawable.smbl_play);
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_player_actionbar, menu);
        return super.onCreateOptionsMenu(menu);
    }


//    @Override
//    public void onBackPressed() {
//        super.onBackPressed();
//        Intent playerIntent = new Intent(this, MainActivity.class);
//        startActivity(playerIntent);
//    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // make sure that PlayerActivityFragment's onActivityResult() gets called
        super.onActivityResult(requestCode, resultCode, data);
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
        LocalBroadcastManager.getInstance(this).registerReceiver(mPlaybackStateChangedReceiver, playbackStateChangedIntentFilter);

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
        LocalBroadcastManager.getInstance(this).registerReceiver(mCollectionChangedReceiver, collectionChangedIntentFilter);
    }

    /* Handles adding, deleting and renaming of station */
    private void handleCollectionChanges(Intent intent) {
        switch (intent.getIntExtra(TransistorKeys.EXTRA_COLLECTION_CHANGE, 1)) {
            // CASE: station was renamed
            case TransistorKeys.STATION_RENAMED:
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION_NEW_NAME) && intent.hasExtra(TransistorKeys.EXTRA_STATION)  ) {
                    Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                    if (station._ID == mStation._ID) {
                        //set title subtitle
                        mStation.TITLE = station.TITLE;
                        mStation.SUBTITLE = station.SUBTITLE;
                        setStationTitleUi();
                    }
                }
                break;

            // CASE: station was deleted
            case TransistorKeys.STATION_DELETED:

                break;
            case TransistorKeys.STATION_CHANGED_IMAGE:
                if (intent.hasExtra(TransistorKeys.EXTRA_STATION) && intent.hasExtra(TransistorKeys.EXTRA_STATION_DB_ID)) {
                    //update image
                    // get new name, station and station ID from intent
                    Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);
                    int stationID = intent.getIntExtra(TransistorKeys.EXTRA_STATION_DB_ID, 0);
                    if (station._ID == mStation._ID) {
                        setStationImageUi();
                    }
                }
                break;
        }
    }

    /* Handles changes in state of playback, eg. start, stop, loading stream */
    private void handlePlaybackStateChanges(Intent intent) {

        // get station from intent
        Station station = intent.getParcelableExtra(TransistorKeys.EXTRA_STATION);

        switch (intent.getIntExtra(TransistorKeys.EXTRA_PLAYBACK_STATE_CHANGE, 1)) {

            // CASE: player is preparing stream
            case TransistorKeys.PLAYBACK_LOADING_STATION:
                if (mStation != null && mStation.getStreamUri().equals(station.getStreamUri())) {
                    // set playback true
                    mPlayback = true;
                    ////comment unneeded setPlaybackState
                    //mStation.setPlaybackState(true);
                    UpdateUiStatus(mStation);
                }
                break;

            // CASE: playback has started
            case TransistorKeys.PLAYBACK_STARTED:
                if (mStation != null && mStation.getStreamUri().equals(station.getStreamUri())) {
                    // set playback true
                    UpdateUiStatus(mStation);
                }
                break;

            // CASE: playback was stopped
            case TransistorKeys.PLAYBACK_STOPPED:
                if (mStation != null && mStation.getStreamUri().equals(station.getStreamUri())) {
                    // set playback falseMediaB
                    mPlayback = false;
                    ////comment unneeded setPlaybackState
                    //mStation.setPlaybackState(false);
                    UpdateUiStatus(mStation);
                }
                break;
        }
    }

    /* Unregisters broadcast receivers */
    private void unregisterBroadcastReceivers() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPlaybackStateChangedReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCollectionChangedReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBroadcastReceivers();
    }
}
