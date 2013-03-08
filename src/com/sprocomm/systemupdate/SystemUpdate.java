package com.sprocomm.systemupdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.inputmethodservice.Keyboard.Key;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.WebHistoryItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class SystemUpdate extends PreferenceActivity implements OnPreferenceClickListener {
	private static final String TAG="SUNUPDATE";
	private SharedPreferences mPrefs;

	private static final String DL_ID = "downloadId";
	private static long mDownloadId;

	private static final int MENU_REFRESH = 0;
	private static final int MENU_DELETE_ALL = 1;
	private static final long NEED_MIN_SIZE = 300; //300M

	private static final int DOWNLOAD_XML=1;
	private static final int DOWNLOAD_ROM=2;
	private static final int CHECK_MD5_FINISH=10;
	private static final int UPDATE_UI=11;
	private static final int DOWNLOAD_FINISH=100;
	private static final int DOWNLOAD_FAILED=101;
	private static String mDownloadPath;
	private static int mWhichDownload;

	private DownloadManager mDownloadManager;

	private String mSystemMod;
	private String mSystemBuild;
	private String mSystemVersion;

	private File mUpdateFolder;
	private ProgressDialog mProgressDialog;

	private RomUtils mRom;
	private boolean mIsCheckMd5 = false;
	private boolean mCheckFile = false;
	private boolean mExistRom = false;
	private boolean mOnekey = false;
	private int mDownloadBytes;
	private int mTotalSize = -1;

	private static final String LOCAL_SYSTEM_INFO="current_system_info";
	private static final String SERVER_SYSTEM_INFO="server_system_info";
	private static final String GENERAL_UPDATE="general_update";
	private static final String ONE_KEY_UPDATE="one_key_update";

	private Preference mLocalSystemInfo;
	private Preference mServerSystemInfo;
	private Preference mGengeralUpdate;
	private Preference mOnekeyUpdate;

	private Handler mHandler= new Handler(){
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case DOWNLOAD_XML:
				mWhichDownload = DOWNLOAD_XML;
				Log.d(TAG, "start download xml");
				startDownload(Customization.XML_DOWNLOAD_URL);
				break;
			case DOWNLOAD_ROM:
				startDownload(mRom.getDownLoadUrl());
				mWhichDownload = DOWNLOAD_ROM;
				break;
			case CHECK_MD5_FINISH:
				checkForUpdates(DOWNLOAD_FINISH);
				break;
			case UPDATE_UI:
				DownloadManager.Query query = new DownloadManager.Query();   
				query.setFilterById(mPrefs.getLong(DL_ID, 0));   
				Cursor c = mDownloadManager.query(query);   
				if(c.moveToFirst()) {  
					mDownloadBytes = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))/1024;
					int t = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
					Log.d(TAG, "totalsize = "+t + " downloadsize = "+mDownloadBytes);
					if (t != -1 ){
						mTotalSize = t/1024;
					}
					if(mDownloadBytes < mTotalSize || (mTotalSize == -1)){
						if(mTotalSize > 0){
							mProgressDialog.setProgress(mDownloadBytes * 100 / mTotalSize);
							Log.d(TAG, "totalsize = "+mTotalSize + " downloadsize = "+mDownloadBytes+" % = "+ mDownloadBytes * 100 / mTotalSize);
						}
						mHandler.sendEmptyMessageDelayed(UPDATE_UI, 1000);
					}
					else 
						mHandler.removeMessages(UPDATE_UI);
				}
				c.close();
				break;
			default:
				break;
			}
		};
	};

	class checkThread extends Thread{
		@Override
		public void run() {
			super.run();
			mCheckFile = MD5.checkMD5(mRom.getMD5(), new File(mDownloadPath));
			mIsCheckMd5 = true;
			mHandler.sendEmptyMessage(CHECK_MD5_FINISH);
		}
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {   
		@Override   
		public void onReceive(Context context, Intent intent) {   
			Log.v(TAG, "........"+intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0));  
			queryDownloadStatus();   
		}   
	};   

	private  void startDownload(String url){
		if(!mPrefs.contains(DL_ID)) {   
			Uri resource = Uri.parse(url);   
			DownloadManager.Request request = new DownloadManager.Request(resource);   
			request.setAllowedNetworkTypes(Request.NETWORK_MOBILE | Request.NETWORK_WIFI);   
			request.setAllowedOverRoaming(false);   
			MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();  
			String mimeString = mimeTypeMap.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url));  
			request.setMimeType(mimeString);  
			String filename = url.substring(url.lastIndexOf("/"));
			File file = new File("/sdcard/spupdate"+filename);
			mDownloadPath  = file.getPath();
			Log.d(TAG, "download path = "+mDownloadPath);
			request.setShowRunningNotification(false);  
			request.setVisibleInDownloadsUi(true);
			if(filename.contains(".xml")){
				if(file.exists()){
					Log.d(TAG, "delete "+mDownloadPath);
					file.delete();
				}
			} else {
				if(file.exists() && !mExistRom){
					mExistRom = true;
					Log.d(TAG, "rom has exits.. check it md5");
					new checkThread().start();
					return;
				} else if(mExistRom){
					file.delete();
				}
			}

			request.setDestinationInExternalPublicDir("/spupdate", filename);
			mDownloadId = mDownloadManager.enqueue(request);   
			mPrefs.edit().putLong(DL_ID, mDownloadId).commit();  
		} else {   
			queryDownloadStatus();   
		}   
	}

	private boolean checkSDStatus() {
		String state=Environment.getExternalStorageState();
		if(state.equals(Environment.MEDIA_MOUNTED)){
			File sdFile=Environment.getExternalStorageDirectory();
			StatFs statfs=new StatFs(sdFile.getPath());
			Log.d(TAG, "path = "+sdFile.getPath());
			long available=statfs.getAvailableBlocks() * statfs.getBlockSize();
			Log.d(TAG, "available sd size : "+available / 1024 / 1024);
			if (available / 1024 / 1024 < NEED_MIN_SIZE){
				new AlertDialog.Builder(this).setMessage("please clear sd card and try again")
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						SystemUpdate.this.finish();
					}
				}).show();
				return false;
			}
			mUpdateFolder = new File(sdFile.getPath()+"/spupdate");
		} else {
			Log.d(TAG, "no sd card ...");
			new AlertDialog.Builder(this).setMessage("please insert SD..")
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					SystemUpdate.this.finish();
				}
			}).show();
			return false;
		}
		return true;
	}

	@Override
	protected void onResume(){
		super.onResume();
		registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));  
	}

	@Override  
	protected void onPause() {  
		super.onPause();
		unregisterReceiver(receiver);
	}  

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(mDownloadId > 0){ 
			mPrefs.edit().clear().commit();
			Log.d(TAG, "exit will delete DownloadId = "+ mDownloadId);
			mDownloadManager.remove(mDownloadId);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.system_update);
		PreferenceScreen prefSet = getPreferenceScreen();

		// Load the stored preference data
		mPrefs = getSharedPreferences("SpUpdate", Context.MODE_MULTI_PROCESS);

		mSystemMod = SysUtils.getSystemProperty(Customization.BOARD);
		mSystemBuild = SysUtils.getSystemProperty(Customization.BUILD_DATE);
		mSystemVersion = SysUtils.getSystemProperty(Customization.SYS_PROP_MOD_VERSION);

		mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

		mLocalSystemInfo = findPreference(LOCAL_SYSTEM_INFO);
		mLocalSystemInfo.setOnPreferenceClickListener( this);
		mServerSystemInfo = findPreference(SERVER_SYSTEM_INFO);
		mServerSystemInfo.setOnPreferenceClickListener(this);
		mGengeralUpdate = findPreference(GENERAL_UPDATE);
		mGengeralUpdate.setOnPreferenceClickListener(this);
		mOnekeyUpdate = findPreference(ONE_KEY_UPDATE);
		mOnekeyUpdate.setOnPreferenceClickListener(this);

		final ActionBar bar = getActionBar();
		bar.setDisplayHomeAsUpEnabled(true);

		checkSDStatus();
		checkNetworkInfo();

		invalidateOptionsMenu();

		if(mPrefs.contains(DL_ID)){
			mDownloadId = mPrefs.getLong(DL_ID, 0);
			mDownloadManager.remove(mDownloadId);
			mPrefs.edit().clear().commit();
		}

		checkForUpdates(DOWNLOAD_XML);
	}

	private void checkNetworkInfo(){
		ConnectivityManager conMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		//mobile 3G Data Network
		State mobile = conMan.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
		//wifi
		State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();

		if(!(wifi==State.CONNECTED || mobile==State.CONNECTED)){
			new AlertDialog.Builder(this).setTitle("Warnning...")
			.setMessage("please open network first!")
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					SystemUpdate.this.finish();
				}
			}).show();
			return;  
		}
		//        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));//进入无线网络配置界面
		//startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); //进入手机中的wifi网络设置界面
	}


	private void queryDownloadStatus() {   
		DownloadManager.Query query = new DownloadManager.Query();   
		query.setFilterById(mPrefs.getLong(DL_ID, 0));   
		Cursor c = mDownloadManager.query(query);   
		if(c.moveToFirst()) {   
			int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));   
			switch(status) {   
			case DownloadManager.STATUS_PAUSED:   
				Log.v(TAG, "STATUS_PAUSED");  
			case DownloadManager.STATUS_PENDING:   
				Log.v(TAG, "STATUS_PENDING");  
			case DownloadManager.STATUS_RUNNING:   
				Log.v(TAG, "STATUS_RUNNING");  
				break;   
			case DownloadManager.STATUS_SUCCESSFUL:   
				Log.v(TAG, "STATUS_SUCCESSFUL"); 
				mDownloadManager.remove(mPrefs.getLong(DL_ID, 0));   
				mPrefs.edit().clear().commit(); 
				if(mWhichDownload == DOWNLOAD_XML){
					mProgressDialog.dismiss();
					startParseXML();
				}else
					checkForUpdates(DOWNLOAD_FINISH);
				mDownloadId = 0;
				mHandler.removeMessages(UPDATE_UI);
				break;   
			case DownloadManager.STATUS_FAILED:   
				Log.v(TAG, "STATUS_FAILED");  
				mDownloadManager.remove(mPrefs.getLong(DL_ID, 0));   
				mPrefs.edit().clear().commit();
				checkForUpdates(DOWNLOAD_FAILED);
				mDownloadId = 0;
				mHandler.removeMessages(UPDATE_UI);
				break;   
			}   
		}  
		c.close();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_REFRESH, 0, R.string.menu_refresh)
		.setIcon(R.drawable.ic_menu_refresh)
		.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS
				| MenuItem.SHOW_AS_ACTION_WITH_TEXT);

		menu.add(0, MENU_DELETE_ALL, 0, R.string.menu_delete_all)
		.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_REFRESH:
			checkForUpdates(DOWNLOAD_XML);
			return true;

		case MENU_DELETE_ALL:
			confirmDeleteAll();
			return true;

		case android.R.id.home:
			SystemUpdate.this.onBackPressed();
			return true;
		}
		return false;
	}

	private void confirmDeleteAll() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.confirm_delete_dialog_title);
		builder.setMessage(R.string.confirm_delete_all_dialog_message);
		builder.setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				deleteOldUpdates();
			}
		});
		builder.setNegativeButton(R.string.dialog_no, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private boolean deleteOldUpdates() {
		boolean success;
		if (mUpdateFolder.exists() && mUpdateFolder.isDirectory()) {
			deleteDir(mUpdateFolder);
			mUpdateFolder.mkdir();
			success = true;
			Toast.makeText(this, R.string.delete_updates_success_message, Toast.LENGTH_SHORT).show();
		} else if (!mUpdateFolder.exists()) {
			success = false;
			Toast.makeText(this, R.string.delete_updates_noFolder_message, Toast.LENGTH_SHORT).show();
		} else {
			success = false;
			Toast.makeText(this, R.string.delete_updates_failure_message, Toast.LENGTH_SHORT).show();
		}
		return success;
	}

	private static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (String aChildren : children) {
				boolean success = deleteDir(new File(dir, aChildren));
				if (!success) {
					return false;
				}
			}
		}
		return dir.delete();
	}

	public void checkForUpdates(int status) {
		if(status == DOWNLOAD_XML){
			String message = getResources().getString(R.string.checking_for_updates);
			showProgressDialog(false, message,  message);
			mHandler.sendEmptyMessage(DOWNLOAD_XML);
		} else if (status == DOWNLOAD_ROM){
			mHandler.sendEmptyMessage(DOWNLOAD_ROM);
//			if(!mOnekey){
				showProgressDialog(true, "Downloading...", null);
				mHandler.sendEmptyMessageDelayed(UPDATE_UI,400);
//			}
			
		} else if (status == DOWNLOAD_FAILED){
			if(mProgressDialog != null){
				mProgressDialog.dismiss();
			}
			new AlertDialog.Builder(this).setTitle("Warnning...")
			.setMessage("please check your network, and try it again!")
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					SystemUpdate.this.finish();
				}
			}).show();
		} else if (status == DOWNLOAD_FINISH){
			if(mWhichDownload == DOWNLOAD_XML){
				Log.d(TAG, "found : currentSYstemid = "+mSystemBuild+"Downloaded Rom build id = "+mRom.getBuildTime()+"\n");
				if(mRom != null){
					String message = "Device: "+mRom.getDevice() + "\n" + "Version: " + mRom.getVersion()+"\n"
							+"BuildDate: "+ mRom.getBuildTime();
					if(true || mSystemBuild.compareTo(mRom.getBuildTime()) < 0){
						if(mOnekey){
							checkForUpdates(DOWNLOAD_ROM);
							mProgressDialog.setMessage("Update...");
							return;
						}
						AlertDialog.Builder builder = new AlertDialog.Builder(this);
						builder.setTitle("Found System Update");
						builder.setMessage(message);
						builder.setPositiveButton("Download Now", new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which) {
								checkForUpdates(DOWNLOAD_ROM);
							}
						});
						AlertDialog dialog = builder.create();
						dialog.show();
						((TextView)dialog.findViewById(android.R.id.message)).setTextAppearance(this,
								android.R.style.TextAppearance_DeviceDefault_Small);
					} else {
						new AlertDialog.Builder(this).setMessage("No Found System Update..")
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which) {
							}
						}).show();
					}
				}
			} else if (mWhichDownload == DOWNLOAD_ROM){
				Log.d(TAG, " download rom from server  has finished...");
				if (!mIsCheckMd5){
					if(mOnekey){
						mProgressDialog.setMessage("Verify...");
					}
					new checkThread().start();
					return;
				}

				if(!mCheckFile){
					if(mExistRom){
						Log.d(TAG, "this rom is old, will download the new");
						mIsCheckMd5 = false;
						checkForUpdates(DOWNLOAD_ROM);
						return;
					}
					if(mProgressDialog != null){
						mProgressDialog.dismiss();
					}
					new AlertDialog.Builder(SystemUpdate.this).setTitle("Check ZIP failed!!")
					.setPositiveButton("Closed", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							deleteOldUpdates();
						}
					}).show();
				}else {
					if(mOnekey){
						mProgressDialog.setMessage("reboot and Update");
						Log.d(TAG, "reboot 。。。。");
						SystemUpdate.this.finish();
						return;
					}
					if(mProgressDialog != null){
						mProgressDialog.dismiss();
					}
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle("Everything is Ok")
					.setMessage("Next operator will reboot phone to update system")
					.setPositiveButton("Update Now", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							SystemUpdate.this.finish();
						}
					}).show();
				}
			}
		}
	}

	private void startParseXML() {
		try {
			if(mDownloadPath == null){
				Log.e(TAG, "ERROR No download_path");
			}
			Log.d(TAG, "startParseXML: DOWNLOAD_PATH = "+mDownloadPath);
			File f = new File(mDownloadPath);
			InputStream ip =  new FileInputStream(f);
			PullRomParser parser = new PullRomParser();
			mRom = parser.parse(ip);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void showDailog(String title, String message, final boolean btn){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setPositiveButton(btn? "Download Now" : getString(android.R.string.ok), new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				if(btn){
					checkForUpdates(DOWNLOAD_ROM);
				}
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
		((TextView)dialog.findViewById(android.R.id.message)).setTextAppearance(this,
				android.R.style.TextAppearance_DeviceDefault_Small);
	}

	private void showProgressDialog(boolean showdown, String message, String title){
		if(mProgressDialog != null){
			mProgressDialog.dismiss();
		}
		mProgressDialog = new ProgressDialog(this);
		if(message != null)
			mProgressDialog.setMessage(message);
		if(title != null)
			mProgressDialog.setTitle(title);
		mProgressDialog.setCancelable(true);
		mProgressDialog.setCanceledOnTouchOutside(false);
		if(showdown){
			mProgressDialog.setIndeterminate(false);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setMax(100);
			mProgressDialog.setProgress(0);
			mProgressDialog.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					new AlertDialog.Builder(SystemUpdate.this).setMessage("Cancel download")
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							mHandler.removeMessages(UPDATE_UI);
							SystemUpdate.this.finish();
						}
					}).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							mHandler.removeMessages(UPDATE_UI);
							checkForUpdates(DOWNLOAD_ROM);
						}
					}).show();
				}
			});
		}else{
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					SystemUpdate.this.finish();
				}
			});
		}
		mProgressDialog.show();
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if(preference == mLocalSystemInfo){
			String message = "Device: "+mSystemMod + "\n" + "Version: " + mSystemVersion+"\n"
					+"BuildDate: "+ mSystemBuild;
			String title = getString(R.string.current_system_info);
			showDailog(title, message, false);
		} else if (preference == mServerSystemInfo){
			if(mRom != null){
				String message = "Device: "+mRom.getDevice() + "\n" + "Version: " + mRom.getVersion()+"\n"
						+"BuildDate: "+ mRom.getBuildTime();
				String title = getString(R.string.server_system_info);
				showDailog(title, message, false);
			}
		} else if (preference == mGengeralUpdate){
			mOnekey = false;
			checkForUpdates(DOWNLOAD_FINISH);
		} else if (preference == mOnekeyUpdate){
			mOnekey = true;
			showProgressDialog(false, getResources().getString(R.string.checking_for_updates), null);
			checkForUpdates(DOWNLOAD_FINISH);
		}		
		return false;
	}

}
