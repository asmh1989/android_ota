package com.sprocomm.systemupdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class SystemUpdate extends PreferenceActivity implements OnPreferenceChangeListener {
	private static final String TAG="SUNUPDATE";
	private static String UPDATES_CATEGORY = "updates_category";
	private SharedPreferences mPrefs;
	private ListPreference mUpdateCheck;

	private static final String DL_ID = "downloadId";
	private static long mDownloadId;

	private static final int MENU_REFRESH = 0;
	private static final int MENU_DELETE_ALL = 1;
	private static final int MENU_SYSTEM_INFO = 2;
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
	private String mSystemRom;

	private File mUpdateFolder;
	private ProgressDialog mProgressDialog;

	private RomUtils mRom;
	private boolean mIsCheckMd5 = false;
	private boolean mCheckFile = false;
	private boolean mExistRom = false;
	private int mDownloadBytes;
	private int mTotalSize = -1;

	private Handler mHandler= new Handler(){
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case DOWNLOAD_XML:
				mWhichDownload = DOWNLOAD_XML;
				Log.d(TAG, "start download xml");
				startDownload(Constants.XML_DOWNLOAD_URL);
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
					if (t != -1 ){
						mTotalSize = t/1024;
					}
					if(mDownloadBytes < mTotalSize || (mTotalSize == -1)){
						if(mTotalSize > 0){
							mProgressDialog.setProgress(mDownloadBytes * 100 / mTotalSize);
							Log.d(TAG, "totalsize = "+mTotalSize/1024 + " downloadsize = "+mDownloadBytes/1024+" % = "+ mDownloadBytes * 100 / mTotalSize);
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
		addPreferencesFromResource(R.xml.main);
		PreferenceScreen prefSet = getPreferenceScreen();
		PreferenceCategory mUpdatesList = (PreferenceCategory) prefSet.findPreference(UPDATES_CATEGORY);

		// Load the stored preference data
		mPrefs = getSharedPreferences("SpUpdate", Context.MODE_MULTI_PROCESS);
		mUpdateCheck = (ListPreference) findPreference(Constants.UPDATE_CHECK_PREF);
		if (mUpdateCheck != null) {
			int check = mPrefs.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_WEEKLY);
			mUpdateCheck.setValue(String.valueOf(check));
			mUpdateCheck.setSummary(mapCheckValue(check));
			mUpdateCheck.setOnPreferenceChangeListener(this);
		}

		mSystemMod = SysUtils.getSystemProperty(Customization.BOARD);
		mSystemRom = SysUtils.getSystemProperty(Customization.SYS_PROP_MOD_VERSION);


		mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

		final ActionBar bar = getActionBar();
		bar.setDisplayHomeAsUpEnabled(true);

		checkSDStatus();
		checkNetworkInfo();

		invalidateOptionsMenu();
		updateLayout();

		if(mPrefs.contains(DL_ID)){
			mDownloadId = mPrefs.getLong(DL_ID, 0);
			mDownloadManager.remove(mDownloadId);
			mPrefs.edit().clear().commit();
		}
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

	private String mapCheckValue(Integer value) {
		Resources resources = getResources();
		String[] checkNames = resources.getStringArray(R.array.update_check_entries);
		String[] checkValues = resources.getStringArray(R.array.update_check_values);
		for (int i = 0; i < checkValues.length; i++) {
			if (Integer.decode(checkValues[i]).equals(value)) {
				return checkNames[i];
			}
		}
		return getString(R.string.unknown);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_REFRESH, 0, R.string.menu_refresh)
		.setIcon(R.drawable.ic_menu_refresh)
		.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS
				| MenuItem.SHOW_AS_ACTION_WITH_TEXT);

		menu.add(0, MENU_DELETE_ALL, 0, R.string.menu_delete_all)
		.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

		menu.add(0, MENU_SYSTEM_INFO, 0, R.string.menu_system_info)
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

		case MENU_SYSTEM_INFO:
			showSysInfo();
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
				// We are OK to delete, trigger it
				deleteOldUpdates();
				updateLayout();
			}
		});
		builder.setNegativeButton(R.string.dialog_no, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private boolean deleteOldUpdates() {
		boolean success;
		//mUpdateFolder: Foldername with fullpath of SDCARD
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
		// The directory is now empty so delete it
		return dir.delete();
	}

	private void showSysInfo() {
		// Build the message
		Date lastCheck = new Date(mPrefs.getLong(Constants.LAST_UPDATE_CHECK_PREF, 0));
		String message = getString(R.string.sysinfo_device) + " " + mSystemMod + "\n\n"
				+ getString(R.string.sysinfo_running)+ " "+ mSystemRom + "\n\n"
				+ getString(R.string.sysinfo_last_check) + " " + lastCheck.toString();

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.menu_system_info);
		builder.setMessage(message);
		builder.setPositiveButton(R.string.dialog_ok, null);
		AlertDialog dialog = builder.create();
		dialog.show();
		((TextView)dialog.findViewById(android.R.id.message)).setTextAppearance(this,
				android.R.style.TextAppearance_DeviceDefault_Small);
	}

	public void checkForUpdates(int status) {
		if(status == DOWNLOAD_XML){
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setTitle(R.string.checking_for_updates);
			mProgressDialog.setMessage(this.getResources().getString(R.string.checking_for_updates));
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setCancelable(true);
			mProgressDialog.setCanceledOnTouchOutside(false);
			mProgressDialog.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
				}
			});
			mProgressDialog.show();
			mHandler.sendEmptyMessage(DOWNLOAD_XML);
		} else if (status == DOWNLOAD_ROM){
			if(mProgressDialog != null){
				mProgressDialog.dismiss();
			}
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setMessage("Downloading...");
			mProgressDialog.setIndeterminate(false);
			mProgressDialog.setCancelable(true);
			mProgressDialog.setCanceledOnTouchOutside(false);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setMax(100);
			mProgressDialog.setProgress(0);
			mProgressDialog.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					new AlertDialog.Builder(SystemUpdate.this).setMessage("Cancel download")
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
		
						@Override
						public void onClick(DialogInterface dialog, int which) {
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
			mProgressDialog.show();
			mHandler.sendEmptyMessage(DOWNLOAD_ROM);
			mHandler.sendEmptyMessage(UPDATE_UI);
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
				startParseXML();
				Log.d(TAG, "found : currentSYstemid = "+mSystemRom+"Downloaded Rom build id = "+mRom.getBuildTime()+"\n");
				if(mProgressDialog != null){
					mProgressDialog.dismiss();
				}
				if(mRom != null){
					String message = "Device: "+mRom.getDevice() + "\n" + "Version: " + mRom.getVersion()+"\n"
							+"BuildDate: "+ mRom.getBuildTime()+" \n"
							+"MD5: "+ mRom.getMD5();

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
				}
			}
			else if(mWhichDownload == DOWNLOAD_ROM){
				if (!mIsCheckMd5){
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
							SystemUpdate.this.finish();
						}
					}).show();
				}else {
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

	public void updateLayout() {

		// Read existing Updates
		List<String> existingFilenames = null;
		mUpdateFolder = new File(Environment.getExternalStorageDirectory() + "/spupdate");
		FilenameFilter f = new UpdateFilter(".zip");
		File[] files = mUpdateFolder.listFiles(f);

		// If Folder Exists and Updates are present(with md5files)
		if (mUpdateFolder.exists() && mUpdateFolder.isDirectory() && files != null && files.length > 0) {
			//To show only the Filename. Otherwise the whole Path with /sdcard/cm-updates will be shown
			existingFilenames = new ArrayList<String>();
			for (File file : files) {
				if (file.isFile()) {
					existingFilenames.add(file.getName());
				}
			}
			//For sorting the Filenames, have to find a way to do natural sorting
			existingFilenames = Collections.synchronizedList(existingFilenames);
			Collections.sort(existingFilenames, Collections.reverseOrder());
		}
		files = null;

		// Clear the notification if one exists

	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference == mUpdateCheck) {
			int value = Integer.valueOf((String) newValue);
			mPrefs.edit().putInt(Constants.UPDATE_CHECK_PREF, value).apply();
			mUpdateCheck.setSummary(mapCheckValue(value));
			//            scheduleUpdateService(value * 1000);
			return true;
		}
		return false;
	}

}
