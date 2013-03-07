package com.sprocomm.systemupdate;

public class RomUtils {
	private String mDevice;  
    private String mVersion;  
    private String mBuildTime;
    private String mMD5;
    private String mDownloadUrl;
      
    public String getDevice() {  
        return mDevice;  
    }  
  
    public void setDevice(String device) {  
        mDevice = device; 
    }  
  
    public String getVersion() {  
        return mVersion;  
    }  
  
    public void setVersion(String version) {  
        mVersion = version;  
    }  
  
    public String getBuildTime() {  
        return mBuildTime;  
    }  
  
    public void setBuildTime(String time) {  
        mBuildTime = time;  
    }
    
    public String getMD5(){
    	return mMD5;
    }
    
    public void setMD5(String md5){
    	mMD5 = md5;
    }
    
    public String getDownLoadUrl(){
    	return mDownloadUrl;
    }
    
    public void setDownLoadUrl(String url){
    	mDownloadUrl = url;
    }
 
}
