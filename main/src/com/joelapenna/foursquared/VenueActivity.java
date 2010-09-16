/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.joelapenna.foursquare.types.Mayor;
import com.joelapenna.foursquare.types.Stats;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.location.LocationUtils;
import com.joelapenna.foursquared.preferences.Preferences;
import com.joelapenna.foursquared.util.MenuUtils;
import com.joelapenna.foursquared.util.NotificationsUtil;
import com.joelapenna.foursquared.util.RemoteResourceManager;
import com.joelapenna.foursquared.util.StringFormatters;
import com.joelapenna.foursquared.util.UiUtil;
import com.joelapenna.foursquared.util.UserUtils;
import com.joelapenna.foursquared.widget.HorizontalViewStrip;

/**
 * We may be given a pre-fetched venue ready to display, or we might also get just
 * a venue ID. If we only get a venue ID, then we need to fetch it immediately from
 * the API.
 * 
 * @author Joe LaPenna (joe@joelapenna.com)
 * @author Mark Wyszomierski (markww@gmail.com)
 *         -Replaced shout activity with CheckinGatherInfoActivity (3/10/2010).
 *         -Redesign to remove tabbed layout (9/15/2010).
 */
public class VenueActivity extends Activity {
    private static final String TAG = "VenueActivity";

    private static final boolean DEBUG = FoursquaredSettings.DEBUG;

    private static final int DIALOG_TIPADD = 1;

    private static final int MENU_CHECKIN = 1;
    private static final int MENU_TIPADD = 2;
    private static final int MENU_CALL = 3;
    private static final int MENU_EDIT_VENUE = 4;
    private static final int MENU_MYINFO = 5;

    private static final int RESULT_CODE_ACTIVITY_CHECKIN_EXECUTE = 1;
    

    public static final String INTENT_EXTRA_VENUE_ID = Foursquared.PACKAGE_NAME
            + ".VenueActivity.INTENT_EXTRA_VENUE_ID";
    public static final String INTENT_EXTRA_VENUE_PARTIAL = Foursquared.PACKAGE_NAME
            + ".VenueActivity.INTENT_EXTRA_VENUE_PARTIAL";
    public static final String INTENT_EXTRA_VENUE = Foursquared.PACKAGE_NAME
            + ".VenueActivity.INTENT_EXTRA_VENUE";
    
    private StateHolder mStateHolder;
    private ProgressDialog mDlgProgress;
    private ImageAdapter mPhotoAdapter;

    private RemoteResourceManager mRrm;
    private RemoteResourceManagerObserver mResourcesObserver;
    

    private BroadcastReceiver mLoggedOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive: " + intent);
            finish();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.venue_activity);
        registerReceiver(mLoggedOutReceiver, new IntentFilter(Foursquared.INTENT_ACTION_LOGGED_OUT));

        StateHolder holder = (StateHolder) getLastNonConfigurationInstance();
        if (holder != null) {
        	mStateHolder = holder;
        } else {
        	mStateHolder = new StateHolder();
            if (getIntent().hasExtra(INTENT_EXTRA_VENUE)) {
            	mStateHolder.setLoadType(StateHolder.LOAD_TYPE_VENUE_FULL);
    	    	mStateHolder.setVenue((Venue)getIntent().getParcelableExtra(INTENT_EXTRA_VENUE));
            } else if (getIntent().hasExtra(INTENT_EXTRA_VENUE_PARTIAL)) {
            	mStateHolder.setLoadType(StateHolder.LOAD_TYPE_VENUE_PARTIAL);
    	    	mStateHolder.setVenue((Venue)getIntent().getParcelableExtra(INTENT_EXTRA_VENUE_PARTIAL));
    	    	mStateHolder.startTaskVenue(this);
    	    } else if (getIntent().hasExtra(INTENT_EXTRA_VENUE_ID)) {
            	mStateHolder.setLoadType(StateHolder.LOAD_TYPE_VENUE_ID);
    	    	mStateHolder.setVenueId(getIntent().getStringExtra(INTENT_EXTRA_VENUE_ID));
    	    	mStateHolder.startTaskVenue(this);
    	    } else {
    	    	Log.e(TAG, "VenueActivity must be given a venue id or a venue parcel as intent extras.");
    	    	finish();
    	    	return;
    	    }
        }
        
        ensureUi();
        
        mRrm = ((Foursquared) getApplication()).getRemoteResourceManager();
        mResourcesObserver = new RemoteResourceManagerObserver();
        mRrm.addObserver(mResourcesObserver);
        
        /*
        mVenueView = (VenueView) findViewById(R.id.venue);
        mVenueView.setCheckinButtonOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // This is a quick checkin, so we can just execute the checkin directly.
                // There's a setting in preferences which can block this behavior though.
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(
                        VenueActivity.this);
                if (settings.getBoolean(Preferences.PREFERENCE_IMMEDIATE_CHECKIN, false)) {
                    startCheckinQuick();
                } else {
                    startCheckin();   
                }
            }
        });
        mVenueView.setSpecialOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showWebViewForSpecial();
            }
        });
        */
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    
    @Override 
    public void onResume() {
        super.onResume();
        ensureUiCheckinButton();
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	
    	if (isFinishing()) {
            unregisterReceiver(mLoggedOutReceiver);
            mRrm.deleteObserver(mResourcesObserver);
        }
    }
    
    private void ensureUi() {
    	TextView tvVenueTitle = (TextView)findViewById(R.id.venueActivityName);
    	TextView tvVenueAddress = (TextView)findViewById(R.id.venueActivityAddress);
    	Button btnCheckin = (Button)findViewById(R.id.venueActivityButtonCheckin);
    	LinearLayout progress = (LinearLayout)findViewById(R.id.venueActivityDetailsProgress);
    	ScrollView svDetails = (ScrollView)findViewById(R.id.venueActivityDetails);
    	RelativeLayout rlMayor = (RelativeLayout)findViewById(R.id.venueActivityMayor);
    	TextView tvMayorTitle = (TextView)findViewById(R.id.venueActivityMayorName);
    	TextView tvMayorText = (TextView)findViewById(R.id.venueActivityMayorText);
    	ImageView ivMayorPhoto = (ImageView)findViewById(R.id.venueActivityMayorPhoto);
    	RelativeLayout llPeople = (RelativeLayout)findViewById(R.id.venueActivityPeople);
    	TextView tvPeopleText = (TextView)findViewById(R.id.venueActivityPeopleText);
    	HorizontalViewStrip psPeoplePhotos = (HorizontalViewStrip)findViewById(R.id.venueActivityPeoplePhotos);
    	RelativeLayout rlTips = (RelativeLayout)findViewById(R.id.venueActivityTips);
    	TextView tvTipsText = (TextView)findViewById(R.id.venueActivityTipsText);
    	RelativeLayout rlMore = (RelativeLayout)findViewById(R.id.venueActivityMore);
    	
    	UiUtil.buildListViewItemSelectable(this, rlMayor);
    	UiUtil.buildListViewItemSelectable(this, llPeople);
    	UiUtil.buildListViewItemSelectable(this, rlTips);
    	UiUtil.buildListViewItemSelectable(this, rlMore);

    	btnCheckin.setEnabled(false);
		progress.setVisibility(View.VISIBLE);
		
		
    	Venue venue = mStateHolder.getVenue();
    	if (mStateHolder.getLoadType() == StateHolder.LOAD_TYPE_VENUE_FULL || 
    		mStateHolder.getLoadType() == StateHolder.LOAD_TYPE_VENUE_PARTIAL) {
	    	
	    	tvVenueTitle.setText(venue.getName());
	    	tvVenueAddress.setText(StringFormatters.getVenueLocationCrossStreetOrCity(venue));
	    	
	    	if (!mStateHolder.getCheckedInHere()) {
	    		btnCheckin.setEnabled(true);
	    		btnCheckin.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						startCheckin();
					}
	    		});
	    	}

	    	if (mStateHolder.getLoadType() == StateHolder.LOAD_TYPE_VENUE_FULL) {
	    	
		    	Stats stats = venue.getStats();
		    	Mayor mayor = stats != null ? stats.getMayor() : null;
		    	
		    	if (mayor != null) {
		    		tvMayorTitle.setText(StringFormatters.getUserFullName(mayor.getUser()));
		    		tvMayorText.setText("is the mayor");
		    		
		    		String photoUrl = mayor.getUser().getPhoto();
		        	Uri uriPhoto = Uri.parse(photoUrl);
		            if (mRrm.exists(uriPhoto)) {
		                try {
		                    Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(Uri.parse(photoUrl)));
		                    ivMayorPhoto.setImageBitmap(bitmap);
		                } catch (IOException e) {
		                }
		            } else {
		            	ivMayorPhoto.setImageResource(UserUtils.getDrawableByGenderForUserThumbnail(mayor.getUser()));
		            	ivMayorPhoto.setTag(photoUrl);
		            	mRrm.request(uriPhoto);
		            }
		    		
		    		rlMayor.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent intent = new Intent(VenueActivity.this, UserDetailsActivity.class);
					        intent.putExtra(UserDetailsActivity.EXTRA_USER_PARCEL, 
					        		mStateHolder.getVenue().getStats().getMayor().getUser());
					        intent.putExtra(UserDetailsActivity.EXTRA_SHOW_ADD_FRIEND_OPTIONS, true);
					        startActivity(intent);
						}
		    		});
		    	} else {
		    		tvMayorTitle.setText("This place has no mayor.");
		    		tvMayorText.setText("Check in here and it could be you");
		    	}
		    	
		    	if (venue.getCheckins() != null && venue.getCheckins().size() > 0) {
		    		llPeople.setVisibility(View.VISIBLE);
		    		tvPeopleText.setText(venue.getCheckins().size() + " people are here.");
		    		
		    		if (mPhotoAdapter == null) {
		    			mPhotoAdapter = new ImageAdapter();
		    			psPeoplePhotos.setAdapter(mPhotoAdapter);
		    		}
		    	} else {
		    		llPeople.setVisibility(View.GONE);
		    	}
		    	
		    	if (venue.getTips() != null && venue.getTips().size() > 0) {
		    		tvTipsText.setText(venue.getTips().size() + " tips here.");
		    	} else {
		    		tvTipsText.setText("No tips here.");
		    	}
		    	
	    		progress.setVisibility(View.GONE);
	    		svDetails.setVisibility(View.VISIBLE);
	    	}
    	}
    	
    	//if (mStateHolder.getIsRunningTaskVenue()) {
    	//	startProgressBar();
    	//}
    }
    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(Menu.NONE, MENU_CHECKIN, 1, R.string.checkin_action_label) //
                .setIcon(R.drawable.ic_menu_checkin);

        menu.add(Menu.NONE, MENU_TIPADD, 2, R.string.add_a_tip).setIcon(
                android.R.drawable.ic_menu_set_as);

        menu.add(Menu.NONE, MENU_CALL, 3, R.string.call).setIcon(android.R.drawable.ic_menu_call);

        menu.add(Menu.NONE, MENU_EDIT_VENUE, 4, R.string.edit_venue).setIcon(
                android.R.drawable.ic_menu_edit);
        
        int sdk = new Integer(Build.VERSION.SDK).intValue();
        if (sdk < 4) {
            int menuIcon = UserUtils.getDrawableForMeMenuItemByGender(
                ((Foursquared) getApplication()).getUserGender());
            menu.add(Menu.NONE, MENU_MYINFO, Menu.NONE, R.string.myinfo_label) //
                    .setIcon(menuIcon);
        }
        
        MenuUtils.addPreferencesToMenu(this, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean checkinEnabled = (mStateHolder.getVenue() != null) && !mStateHolder.getCheckedInHere();
        menu.findItem(MENU_CHECKIN).setEnabled(checkinEnabled);

        boolean callEnabled = mStateHolder.getVenue() != null
                && !TextUtils.isEmpty(mStateHolder.getVenue().getPhone());
        menu.findItem(MENU_CALL).setEnabled(callEnabled);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_CHECKIN:   
                startCheckin();
                return true;
            case MENU_TIPADD:
                showDialog(DIALOG_TIPADD);
                return true;
            case MENU_CALL:
                try {
                    Intent dial = new Intent();
                    dial.setAction(Intent.ACTION_DIAL);
                    dial.setData(Uri.parse("tel:" + mStateHolder.getVenue().getPhone()));
                    startActivity(dial);
                } catch (Exception ex) {
                    Log.e(TAG, "Error starting phone dialer intent.", ex);
                    Toast.makeText(this, "Sorry, we couldn't find any app to place a phone call!",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            case MENU_EDIT_VENUE:
                Intent intentEditVenue = new Intent(this, EditVenueOptionsActivity.class);
                intentEditVenue.putExtra(
                        EditVenueOptionsActivity.EXTRA_VENUE_PARCELABLE, mStateHolder.getVenue());
                startActivity(intentEditVenue);
                return true;
            case MENU_MYINFO:
                Intent intentUser = new Intent(VenueActivity.this, UserDetailsActivity.class);
                intentUser.putExtra(UserDetailsActivity.EXTRA_USER_ID,
                        ((Foursquared) getApplication()).getUserId());
                startActivity(intentUser);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Dialog onCreateDialog(int id) {
    	/*
        LayoutInflater inflater = (LayoutInflater) getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View layout;

        switch (id) {
            case DIALOG_TIPADD:
                layout = inflater.inflate(R.layout.tip_add_dialog,
                        (ViewGroup) findViewById(R.id.layout_root));

                final EditText editText = (EditText) layout.findViewById(R.id.editText);
                final Spinner spinner = (Spinner) layout.findViewById(R.id.spinner);

                return new AlertDialog.Builder(this) //
                        .setView(layout) //
                        .setIcon(android.R.drawable.ic_dialog_alert) // icon
                        .setTitle("Add a Tip") // title
                        .setPositiveButton("Add", new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String tip = editText.getText().toString();
                                String type = ((String) spinner.getSelectedItem()).toLowerCase();
                                editText.setText("");
                                spinner.setSelection(0);
                                new TipAddTask().execute(tip, type);
                            }
                        }) //
                        .setNegativeButton("Cancel", new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                editText.setText("");
                                spinner.setSelection(0);
                                dismissDialog(DIALOG_TIPADD);
                            }
                        }).create();
        }
        return null;
        */
    	
    	return null;
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
        // If the tip add was a success we must have set mStateHolder.tip. If
        // that is the case, then
        // we clear the dialog because clearly they're looking to add a new tip
        // and not post the
        // same one again.
//        if (id == DIALOG_TIPADD && mStateHolder.tip != null) {
//            ((EditText) dialog.findViewById(R.id.editText)).setText("");
//            mStateHolder.tip = null;
//        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        mStateHolder.setActivityForTasks(null);
        return mStateHolder;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_CODE_ACTIVITY_CHECKIN_EXECUTE:
                if (resultCode == Activity.RESULT_OK) {
                    mStateHolder.setCheckedInHere(true);
                    ensureUiCheckinButton();
                }
                break;
        }
    }
    
    private void ensureUiCheckinButton() {
    	Button btnCheckin = (Button)findViewById(R.id.venueActivityButtonCheckin);
    	if (mStateHolder.getCheckedInHere()) {
    		btnCheckin.setEnabled(false);
    	} else {
    		if (mStateHolder.getLoadType() == StateHolder.LOAD_TYPE_VENUE_ID) {
        		btnCheckin.setEnabled(false);
    		} else {
        		btnCheckin.setEnabled(true);
    		}
    	}
    }

    private void startProgressBar() {
        if (mDlgProgress == null) {
            mDlgProgress = ProgressDialog.show(
                this, 
                "Title", "Doing something...");
        }
        mDlgProgress.show();
        setProgressBarIndeterminateVisibility(true);
    }

    private void stopProgressBar() {
        if (mDlgProgress != null) {
            mDlgProgress.dismiss();
            mDlgProgress = null;
        }
        setProgressBarIndeterminateVisibility(false);
    }
    
    /*
    private void onVenueSet() {
        Venue venue = mStateHolder.venue;
        if (DEBUG) Log.d(TAG, "onVenueSet:" + venue.getName());
        setTitle(venue.getName() + " - Foursquare");
        mVenueView.setVenue(venue);
        mVenueView.setCheckinButtonEnabled(mStateHolder.venueId != null);
    }

    private void setVenue(Venue venue) {
        mStateHolder.venue = venue;
        mStateHolder.venueId = venue.getId();
        venueObservable.notifyObservers(venue);
        onVenueSet();
    }
    */
    private void startCheckin() {
        Intent intent = new Intent(this, CheckinOrShoutGatherInfoActivity.class);
        intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_IS_CHECKIN, true);
        intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_VENUE_ID, mStateHolder.getVenue().getId());
        intent.putExtra(CheckinOrShoutGatherInfoActivity.INTENT_EXTRA_VENUE_NAME, mStateHolder.getVenue().getName());
        startActivityForResult(intent, RESULT_CODE_ACTIVITY_CHECKIN_EXECUTE);
    }
    
    private void startCheckinQuick() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        boolean tellFriends = settings.getBoolean(Preferences.PREFERENCE_SHARE_CHECKIN, true);
        boolean tellTwitter = settings.getBoolean(Preferences.PREFERENCE_TWITTER_CHECKIN, false);
        boolean tellFacebook = settings.getBoolean(Preferences.PREFERENCE_FACEBOOK_CHECKIN, false);
        
        Intent intent = new Intent(VenueActivity.this, CheckinExecuteActivity.class);
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_VENUE_ID, mStateHolder.getVenue().getId());
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_SHOUT, "");
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_TELL_FRIENDS, tellFriends);
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_TELL_TWITTER, tellTwitter);
        intent.putExtra(CheckinExecuteActivity.INTENT_EXTRA_TELL_FACEBOOK, tellFacebook);
        startActivityForResult(intent, RESULT_CODE_ACTIVITY_CHECKIN_EXECUTE);
    }
    
    private void showWebViewForSpecial() {
        
        Intent intent = new Intent(this, SpecialWebViewActivity.class);
        intent.putExtra(SpecialWebViewActivity.EXTRA_CREDENTIALS_USERNAME, 
                PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.PREFERENCE_LOGIN, ""));
        intent.putExtra(SpecialWebViewActivity.EXTRA_CREDENTIALS_PASSWORD, 
                PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.PREFERENCE_PASSWORD, ""));
        intent.putExtra(SpecialWebViewActivity.EXTRA_SPECIAL_ID, 
                mStateHolder.getVenue().getSpecials().get(0).getId());
        startActivity(intent);
    }

    private static class TaskVenue extends AsyncTask<String, Void, Venue> {

        private VenueActivity mActivity;
        private Exception mReason;

        public TaskVenue(VenueActivity activity) {
        	mActivity = activity;
        }
        
        @Override
        protected void onPreExecute() {
        	//mActivity.startProgressBar();
        }

        @Override
        protected Venue doInBackground(String... params) {
            try {
            	Foursquared foursquared = (Foursquared)mActivity.getApplication();
                return foursquared.getFoursquare().venue(
                        params[0],
                        LocationUtils.createFoursquareLocation(foursquared.getLastKnownLocation()));
            } catch (Exception e) {
                Log.e(TAG, "Error getting venue details.", e);
                mReason = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Venue venue) {
        	//mActivity.stopProgressBar();
        	mActivity.mStateHolder.setIsRunningTaskVenue(false);
        	if (venue != null) {
        		mActivity.mStateHolder.setLoadType(StateHolder.LOAD_TYPE_VENUE_FULL);
        		mActivity.mStateHolder.setVenue(venue);
        		mActivity.ensureUi();
        		
        	} else {
        		NotificationsUtil.ToastReasonForFailure(mActivity, mReason);
        		mActivity.finish();
        	}
        }

        @Override
        protected void onCancelled() {
            mActivity.stopProgressBar();
        }
        
        public void setActivity(VenueActivity activity) {
        	mActivity = activity;
        }
    }


    private static final class StateHolder {
        private Venue mVenue;
        private String mVenueId;
        private boolean mCheckedInHere;
        
        private VenueActivity mActivity;
        private TaskVenue mTaskVenue;
        private boolean mIsRunningTaskVenue;
        
        private int mLoadType;
        
        public static final int LOAD_TYPE_VENUE_ID      = 0;
        public static final int LOAD_TYPE_VENUE_PARTIAL = 1;
        public static final int LOAD_TYPE_VENUE_FULL    = 2;
        
        
        public Venue getVenue() {
        	return mVenue;
        }
        
        public void setVenue(Venue venue) {
        	mVenue = venue;
        }
        
        public String getVenueId() {
        	return mVenueId;
        }
        
        public void setVenueId(String venueId) {
        	mVenueId = venueId;
        }
        
        public boolean getCheckedInHere() {
        	return mCheckedInHere;
        }
        
        public void setCheckedInHere(boolean checkedInHere) {
        	mCheckedInHere = checkedInHere;
        }
        
        public boolean getIsRunningTaskVenue() {
        	return mIsRunningTaskVenue;
        }
        
        public void setIsRunningTaskVenue(boolean isRunningTaskVenue) {
        	mIsRunningTaskVenue = isRunningTaskVenue;
        }
        
        public void startTaskVenue(VenueActivity activity) {
        	mIsRunningTaskVenue = true;
            mTaskVenue = new TaskVenue(activity);
            if (mLoadType == LOAD_TYPE_VENUE_ID) {
            	mTaskVenue.execute(mVenueId); 
            } else if (mLoadType == LOAD_TYPE_VENUE_PARTIAL) {
            	mTaskVenue.execute(mVenue.getId());
            }
        }
        
        public void setActivityForTasks(VenueActivity activity) {
        	if (mTaskVenue != null) {
        		mTaskVenue.setActivity(activity);
        	}
        }
        
        public int getLoadType() {
        	return mLoadType;
        }
        
        public void setLoadType(int loadType) {
        	mLoadType = loadType;
        }
    }
    
    
    
    private class ImageAdapter extends BaseAdapter {
        public ImageAdapter() {
        }

        public int getCount() {
        	if (mStateHolder.getVenue() != null && mStateHolder.getVenue().getCheckins() != null) {
        	    return mStateHolder.getVenue().getCheckins().size();	
        	} else {
        		return 0;
        	}
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return 0;
        }
 
        public View getView(int position, View convertView, ViewGroup parent) {
        	
        	ImageView iv = null;
        	if (convertView == null) {
        	    iv = (ImageView)VenueActivity.this.getLayoutInflater().inflate(
            		R.layout.user_photo, null);
                iv.setImageResource(R.drawable.blank_boy);
                iv.setLayoutParams(new LinearLayout.LayoutParams(44, 44));
        	} else {
        		iv = (ImageView)convertView;
        	}
        	
            String photoUrl = mStateHolder.getVenue().getCheckins().get(position).getUser().getPhoto();
        	Uri uriPhoto = Uri.parse(photoUrl);
            if (mRrm.exists(uriPhoto)) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(Uri.parse(photoUrl)));
                    iv.setImageBitmap(bitmap);
                } catch (IOException e) {
                }
            } else {
                mRrm.request(uriPhoto);
            }
        	
        	return iv;
        }
    }
    
    private class RemoteResourceManagerObserver implements Observer {
        @Override
        public void update(Observable observable, Object data) {
        	HorizontalViewStrip psv = (HorizontalViewStrip)VenueActivity.this.findViewById(R.id.venueActivityPeoplePhotos);
            psv.getHandler().post(new Runnable() {
                @Override
                public void run() {
                	if (mPhotoAdapter != null) {
                		mPhotoAdapter.notifyDataSetInvalidated();
                	}
                	
                	ImageView ivMayorPhoto = (ImageView)findViewById(R.id.venueActivityMayorPhoto);
                	if (ivMayorPhoto.getTag() != null) {
                		String mayorPhotoUrl = (String)ivMayorPhoto.getTag();
                		try {
                            Bitmap bitmap = BitmapFactory.decodeStream(mRrm.getInputStream(Uri.parse(mayorPhotoUrl)));
                            ivMayorPhoto.setImageBitmap(bitmap);
                            ivMayorPhoto.setTag(null);
                            ivMayorPhoto.invalidate();
                        } catch (IOException e) {
                        }
                	}
                }
            });
        }
    }
}
