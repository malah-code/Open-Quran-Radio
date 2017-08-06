/**
 * CollectionAdapterViewHolder.java
 * Implements the a custom view holder
 * A CollectionAdapterViewHolder is an implementation of the Android "view holder" design pattern
 * <p>
 * This file is part of
 * TRANSISTOR - Radio App for Android
 * <p>
 * Copyright (c) 2015-17 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor;


import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import com.facebook.drawee.view.SimpleDraweeView;


/**
 * CollectionAdapterViewHolder.class
 */
public class CollectionAdapterViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

    /* Define log tag */
    private static final String LOG_TAG = CollectionAdapterViewHolder.class.getSimpleName();


    /* Main class variables */
    private final View mListItemLayout;
    private final SimpleDraweeView mStationImageView;
    private final TextView mStationNameView;
    private final LinearLayout mLayoutCategoryView;
    private final TextView mTxtCategoryView;
    private final TextView mStationDesciptionView;
    private final ImageView mPlaybackIndicator;
    private final ImageView mStationMenuView;
    private final FloatingActionButton mFabPlayButton;
    private final RatingBar mRatingBarView;
    private  ImageButton mFavoritButton;
    private ClickListener mClickListener;


    /* Interface for handling clicks - both normal and long ones. */
    public interface ClickListener {
        void onClick(View v, int position, boolean isLongClick);
    }


    /* Constructor */
    public CollectionAdapterViewHolder(View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);
        itemView.setClickable(true);
        mListItemLayout = itemView;
        mStationImageView = (SimpleDraweeView) itemView.findViewById(R.id.list_item_station_icon);
        mStationNameView = (TextView) itemView.findViewById(R.id.list_item_textview);
        mLayoutCategoryView = (LinearLayout) itemView.findViewById(R.id.layoutFavorit);
        mTxtCategoryView = (TextView) itemView.findViewById(R.id.txtFavorit);
        mRatingBarView = (RatingBar) itemView.findViewById(R.id.ratingBar);
        mFavoritButton = (ImageButton) itemView.findViewById(R.id.player_item_favorit_button);
        mStationDesciptionView = (TextView) itemView.findViewById(R.id.list_item_description);
        mPlaybackIndicator = (ImageView) itemView.findViewById(R.id.list_item_playback_indicator);
        mStationMenuView = (ImageView) itemView.findViewById(R.id.list_item_more_button);
        mFabPlayButton = (FloatingActionButton) itemView.findViewById(R.id.fabPlayButton);
    }


    @Override
    public void onClick(View v) {
        // if not long clicked, pass last variable as false.
        mClickListener.onClick(v, getAdapterPosition(), false);
    }


    @Override
    public boolean onLongClick(View v) {
        // if long clicked, passed last variable as true.
        mClickListener.onClick(v, getAdapterPosition(), true);
        return true;
    }

    /* Getter for Layout Category */
    public LinearLayout getLayoutCategoryView() {
        return mLayoutCategoryView;
    }
    /* Getter for Txt Category */
    public TextView getTxtCategoryView() {
        return mTxtCategoryView;
    }

    /* Getter for parent list item layout */
    public View getListItemLayout() {
        return mListItemLayout;
    }

    /* Getter for station image view */
    public SimpleDraweeView getStationImageView() {
        return mStationImageView;
    }


    /* Getter for station name view */
    public TextView getStationNameView() {
        return mStationNameView;
    }

    /* Getter for station Rating Bar view */
    public RatingBar getRatingBarView() {
        return mRatingBarView;
    }

    /* Getter for station Favorit Button view */
    public ImageButton getFavoritButtonView() {
        return mFavoritButton;
    }

    /* Getter for station FAB Play Button view */
    public FloatingActionButton getFabPlayButton() {
        return mFabPlayButton;
    }

    /* Getter for Station Desciption View */
    public TextView getmStationDesciptionView() {
        return mStationDesciptionView;
    }

    /* Getter for station playback indicator */
    public ImageView getPlaybackIndicator() {
        return mPlaybackIndicator;
    }


    /* Getter for station menu view */
    public ImageView getStationMenuView() {
        return mStationMenuView;
    }


    /* Setter for listener. */
    public void setClickListener(ClickListener clickListener) {
        mClickListener = clickListener;
    }

}