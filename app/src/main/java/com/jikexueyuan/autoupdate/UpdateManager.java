package com.jikexueyuan.autoupdate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.protocol.ResponseConnControl;
import org.json.JSONObject;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import android.R.integer;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

public class UpdateManager {
	
	private ProgressBar mProgressBar;
	private Dialog mDownloadDialog;
	
	private String mSavePath;
	private int mProgress;

	private boolean mIsCancel = false;
	
	private static final int DOWNLOADING = 1;
	private static final int DOWNLOAD_FINISH = 2;
	
	private static final String PATH = "http://172.31.27.82/autoupdate/version.html";

	private String mVersion_code;
	private String mVersion_name;
	private String mVersion_desc;
	private String mVersion_path;

	private Context mContext;

	public UpdateManager(Context context) {
		mContext = context;
	}

	private Handler mGetVersionHandler = new Handler(){
		public void handleMessage(Message msg) {
			JSONObject jsonObject = (JSONObject) msg.obj;
			System.out.println(jsonObject.toString());
			try {
				mVersion_code = jsonObject.getString("version_code");
				mVersion_name = jsonObject.getString("version_name");
				mVersion_desc = jsonObject.getString("version_desc");
				mVersion_path = jsonObject.getString("version_path");
				
				if (isUpdate()){
					Toast.makeText(mContext, "需要更新", Toast.LENGTH_SHORT).show();
					// 显示提示更新对话框
					showNoticeDialog();
				} else{
					Toast.makeText(mContext, "已是最新版本", Toast.LENGTH_SHORT).show();
				}
				
			} catch (Exception e){
				e.printStackTrace();
			}
		};
	};
	
	private Handler mUpdateProgressHandler = new Handler(){
		public void handleMessage(Message msg) {
			switch (msg.what){
			case DOWNLOADING:
				// 设置进度条
				mProgressBar.setProgress(mProgress);
				break;
			case DOWNLOAD_FINISH:
				// 隐藏当前下载对话框
				mDownloadDialog.dismiss();
				// 安装 APK 文件
				installAPK();
			}
		};
	};
	
	/*
	 * 检测软件是否需要更新
	 */
	public void checkUpdate() {
		RequestQueue requestQueue = Volley.newRequestQueue(mContext);
		JsonObjectRequest request = new JsonObjectRequest(PATH, null, new Listener<JSONObject>() {
			@Override
			public void onResponse(JSONObject jsonObject) {
				Message msg = Message.obtain();
				msg.obj = jsonObject;
				mGetVersionHandler.sendMessage(msg);
			}
			
		}, new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError arg0) {
				System.out.println(arg0.toString());
			}
		});
		requestQueue.add(request);
	}

	/*
	 * 与本地版本比较判断是否需要更新
	 */
	protected boolean isUpdate() {
		int serverVersion = Integer.parseInt(mVersion_code);
		int localVersion = 1;
		
		try {
			localVersion = mContext.getPackageManager().getPackageInfo("com.jikexueyuan.autoupdate", 0).versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		
		if (serverVersion > localVersion)
			return true;
		else
			return false;
	}

	/*
	 * 有更新时显示提示对话框
	 */
	protected void showNoticeDialog() {
		AlertDialog.Builder builder = new Builder(mContext);
		builder.setTitle("提示");
		String message = "软件有更新，要下载安装吗？\n" + mVersion_desc;
		builder.setMessage(message);
		
		builder.setPositiveButton("更新", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// 隐藏当前对话框
				dialog.dismiss();
				// 显示下载对话框
				showDownloadDialog();
			}
		});
		
		builder.setNegativeButton("下次再说", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// 隐藏当前对话框
				dialog.dismiss();
			}
		});
		
		builder.create().show();
	}

	/*
	 * 显示正在下载对话框
	 */
	protected void showDownloadDialog() {
		AlertDialog.Builder builder = new Builder(mContext);
		builder.setTitle("下载中");
		View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_progress, null);
		mProgressBar = (ProgressBar) view.findViewById(R.id.id_progress);
		builder.setView(view);
		
		builder.setNegativeButton("取消", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// 隐藏当前对话框
				dialog.dismiss();
				// 设置下载状态为取消
				mIsCancel = true;
			}
		});
		
		mDownloadDialog = builder.create();
		mDownloadDialog.show();
		
		// 下载文件
		downloadAPK();
	}

	/*
	 * 开启新线程下载文件
	 */
	private void downloadAPK() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try{
					if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
						String sdPath = Environment.getExternalStorageDirectory() + "/";
						mSavePath = sdPath + "jikedownload";
						
						File dir = new File(mSavePath);
						if (!dir.exists())
							dir.mkdir();
						
						// 下载文件
						HttpURLConnection conn = (HttpURLConnection) new URL(mVersion_path).openConnection();
						conn.connect();
						InputStream is = conn.getInputStream();
						int length = conn.getContentLength();
						
						File apkFile = new File(mSavePath, mVersion_name);
						FileOutputStream fos = new FileOutputStream(apkFile);
						
						int count = 0;
						byte[] buffer = new byte[1024];
						while (!mIsCancel){
							int numread = is.read(buffer);
							count += numread;
							// 计算进度条的当前位置
							mProgress = (int) (((float)count/length) * 100);
							// 更新进度条
							mUpdateProgressHandler.sendEmptyMessage(DOWNLOADING);
							
							// 下载完成
							if (numread < 0){
								mUpdateProgressHandler.sendEmptyMessage(DOWNLOAD_FINISH);
								break;
							}
							fos.write(buffer, 0, numread);
						}
						fos.close();
						is.close();
					}
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}).start();
	}

	/*
	 * 下载到本地后执行安装
	 */
	protected void installAPK() {
		File apkFile = new File(mSavePath, mVersion_name);
		if (!apkFile.exists())
			return;
		
		Intent intent = new Intent(Intent.ACTION_VIEW);
		Uri uri = Uri.parse("file://" + apkFile.toString());
		intent.setDataAndType(uri, "application/vnd.android.package-archive");
		mContext.startActivity(intent);
		
	}

}
