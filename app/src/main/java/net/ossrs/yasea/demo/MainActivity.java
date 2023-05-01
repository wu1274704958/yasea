package net.ossrs.yasea.demo;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.faucamp.simplertmp.RtmpHandler;
import com.seu.magicfilter.utils.MagicFilterType;
import com.wws.remotecamad.OnGetError;
import com.wws.remotecamad.RemoteCamAgent;

import net.ossrs.yasea.SrsCameraView;
import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsPublisher;
import net.ossrs.yasea.SrsRecordHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements RtmpHandler.RtmpListener,
                        SrsRecordHandler.SrsRecordListener, SrsEncodeHandler.SrsEncodeListener {

    private static final String TAG = "Yasea";
    public final static int RC_CAMERA = 100;
    private static final String CMD_SAVE_TAG = "CMD_SAVE_TAG";

    private Button btnPublish;
    private Button btnSwitchCamera;
    private Button btnRecord;
    private Button btnSwitchEncoder;
    private Button btnPause;

    private SharedPreferences sp;
    private String rtmpUrl = "rtmp://129.211.8.222/live/livestream";//"rtmp://ossrs.net/" + getRandomAlphaString(3) + '/' + getRandomAlphaDigitString(5);
    private String recPath = Environment.getExternalStorageDirectory().getPath() + "/test.mp4";

    private SrsPublisher mPublisher;
    private SrsCameraView mCameraView;

    private int mWidth = 1920;
    private int mHeight = 1080;
    private boolean isPermissionGranted = false;
    ////////////////////////////////
    private MyHandle myHandle;
    private RemoteCamAgent agent;
    private ScrollView m_Scroll;
    private TextView m_logTx;
    private EditText m_agent_url;
    private Button m_agent_btn;
    private EditText efu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideStatusBar(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // response screen rotation event
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        myHandle = new MyHandle(this);
        agent = new RemoteCamAgent(this,myHandle);
        agent.registe();
        agent.setOnGetError(new OnGetError() {
            @Override
            public void OnError(int code) {
                agent.sendLog("error : " + code,0);
            }
        });

        requestPermission();
    }

    private void requestPermission() {
        //1. 检查是否已经有该权限
        if (Build.VERSION.SDK_INT >= 23 && (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            //2. 权限没有开启，请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_CAMERA);
        }else{
            //权限已经开启，做相应事情
            isPermissionGranted = true;
            init();
        }
    }

    //3. 接收申请成功或者失败回调
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //权限被用户同意,做相应的事情
                isPermissionGranted = true;
                init();
            } else {
                //权限被用户拒绝，做相应的事情
                finish();
            }
        }
    }

    private void init() {
        // restore data.
        sp = getSharedPreferences("Yasea", MODE_PRIVATE);
        rtmpUrl = sp.getString("rtmpUrl", rtmpUrl);

        // initialize url.
        efu = (EditText) findViewById(R.id.url);
        efu.setText(rtmpUrl);

        btnPublish = (Button) findViewById(R.id.publish);
        btnSwitchCamera = (Button) findViewById(R.id.swCam);
        btnRecord = (Button) findViewById(R.id.record);
        btnSwitchEncoder = (Button) findViewById(R.id.swEnc);
        btnPause = (Button) findViewById(R.id.pause);
        btnPause.setEnabled(false);
        mCameraView = (SrsCameraView) findViewById(R.id.glsurfaceview_camera);
        initAgent();
        initPublisher();

        mCameraView.setCameraCallbacksHandler(new SrsCameraView.CameraCallbacksHandler(){
            @Override
            public void onCameraParameters(Camera.Parameters params) {
                //params.setFocusMode("custom-focus");
                //params.setWhiteBalance("custom-balance");
                //etc...
            }
        });

        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnClickPublish();
            }
        });
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                OnClickPause();
            }
        });

        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnClickSwitch();
            }
        });

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnRecord.getText().toString().contentEquals("record")) {
                    if (mPublisher.startRecord(recPath)) {
                        btnRecord.setText("pause");
                    }
                } else if (btnRecord.getText().toString().contentEquals("pause")) {
                    mPublisher.pauseRecord();
                    btnRecord.setText("resume");
                } else if (btnRecord.getText().toString().contentEquals("resume")) {
                    mPublisher.resumeRecord();
                    btnRecord.setText("pause");
                }
            }
        });

        btnSwitchEncoder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnSwitchEncoder.getText().toString().contentEquals("soft encoder")) {
                    mPublisher.switchToSoftEncoder();
                    btnSwitchEncoder.setText("hard encoder");
                } else if (btnSwitchEncoder.getText().toString().contentEquals("hard encoder")) {
                    mPublisher.switchToHardEncoder();
                    btnSwitchEncoder.setText("soft encoder");
                }
            }
        });
        agent.launch(GetSavedCmd(CMD_SAVE_TAG));
    }

    private void initPublisher() {
        mPublisher = new SrsPublisher(mCameraView);
        mPublisher.setEncodeHandler(new SrsEncodeHandler(this));
        mPublisher.setRtmpHandler(new RtmpHandler(this));
        mPublisher.setRecordHandler(new SrsRecordHandler(this));
        mPublisher.setPreviewResolution(mWidth, mHeight);
        mPublisher.setOutputResolution(mHeight, mWidth); // 这里要和preview反过来
        mPublisher.setVideoHDMode();
    }

    private void OnClickSwitch() {
        mPublisher.switchCameraFace((mPublisher.getCameraId() + 1) % Camera.getNumberOfCameras());
    }

    private void OnClickPause() {
        if(btnPause.getText().toString().equals("Pause")){
            mPublisher.pausePublish();
            btnPause.setText("resume");
        }else{
            mPublisher.resumePublish();
            btnPause.setText("Pause");
        }
    }

    private void OnClickPublish() {
        if (btnPublish.getText().toString().contentEquals("publish")) {
            rtmpUrl = efu.getText().toString();
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("rtmpUrl", rtmpUrl);
            editor.apply();

            mPublisher.startPublish(rtmpUrl);
            mPublisher.startCamera();

            if (btnSwitchEncoder.getText().toString().contentEquals("soft encoder")) {
                Toast.makeText(getApplicationContext(), "Use hard encoder", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Use soft encoder", Toast.LENGTH_SHORT).show();
            }
            btnPublish.setText("stop");
            btnSwitchEncoder.setEnabled(false);
            btnPause.setEnabled(true);
            mCameraView.setVisibility(View.VISIBLE);
        } else if (btnPublish.getText().toString().contentEquals("stop")) {
            mPublisher.stopPublish();
            mPublisher.stopRecord();
            btnPublish.setText("publish");
            btnRecord.setText("record");
            btnSwitchEncoder.setEnabled(true);
            btnPause.setEnabled(false);
            mCameraView.setVisibility(View.GONE);
        }
    }

    private void initAgent() {
        m_Scroll = (ScrollView)findViewById(R.id.scroll);
        m_logTx = (TextView)findViewById(R.id.log_tx);
        m_agent_url = (EditText)findViewById(R.id.agent_url);
        m_agent_btn = (Button) findViewById(R.id.agent_btn);

        m_agent_url.setText(GetSavedCmd(m_agent_url.getText().toString()));
        m_agent_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String cmd = m_agent_url.getText().toString();
                agent.launch(cmd);
                SaveCmd(cmd);
            }
        });
    }
    public void log(String str)
    {
        if(m_logTx.getLineCount() >= m_logTx.getMaxLines())
            m_logTx.setText("");
        if(str.length() > 0 && str.charAt(str.length() - 1) != '\n')
            str += '\n';
        m_logTx.append(str);
        m_Scroll.fullScroll(View.FOCUS_DOWN);
    }

    private String GetSavedCmd(String def)
    {
        SharedPreferences a = getSharedPreferences(CMD_SAVE_TAG,MODE_PRIVATE);
        return a.getString("cmd",def);
    }

    private void SaveCmd(String cmd)
    {
        SharedPreferences a = getSharedPreferences(CMD_SAVE_TAG,MODE_PRIVATE);
        SharedPreferences.Editor ed = a.edit();
        ed.putString("cmd",cmd);
        ed.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        } else {
            switch (id) {
                case R.id.cool_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.COOL);
                    break;
                case R.id.beauty_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.BEAUTY);
                    break;
                case R.id.early_bird_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.EARLYBIRD);
                    break;
                case R.id.evergreen_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.EVERGREEN);
                    break;
                case R.id.n1977_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.N1977);
                    break;
                case R.id.nostalgia_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.NOSTALGIA);
                    break;
                case R.id.romance_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.ROMANCE);
                    break;
                case R.id.sunrise_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.SUNRISE);
                    break;
                case R.id.sunset_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.SUNSET);
                    break;
                case R.id.tender_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.TENDER);
                    break;
                case R.id.toast_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.TOASTER2);
                    break;
                case R.id.valencia_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.VALENCIA);
                    break;
                case R.id.walden_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.WALDEN);
                    break;
                case R.id.warm_filter:
                    mPublisher.switchCameraFilter(MagicFilterType.WARM);
                    break;
                case R.id.original_filter:
                default:
                    mPublisher.switchCameraFilter(MagicFilterType.NONE);
                    break;
            }
        }
        setTitle(item.getTitle());

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(mPublisher.getCamera() == null && isPermissionGranted){
            //if the camera was busy and available again
            mPublisher.startCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Button btn = (Button) findViewById(R.id.publish);
        btn.setEnabled(true);
        mPublisher.resumeRecord();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPublisher.pauseRecord();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPublisher.stopPublish();
        mPublisher.stopRecord();
        agent.unregiste();
        agent = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onOrientationChanged(newConfig.orientation);

    }
    private void onOrientationChanged(int orientation)
    {
        mPublisher.stopEncode();
        mPublisher.stopRecord();
        btnRecord.setText("record");
        mPublisher.setScreenOrientation(orientation);
        if (btnPublish.getText().toString().contentEquals("stop")) {
            mPublisher.startEncode();
            mPublisher.startCamera();
        }
    }

    private static String getRandomAlphaString(int length) {
        String base = "abcdefghijklmnopqrstuvwxyz";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    private static String getRandomAlphaDigitString(int length) {
        String base = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    private void handleException(Exception e) {
        try {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            mPublisher.stopPublish();
            mPublisher.stopRecord();
            btnPublish.setText("publish");
            btnRecord.setText("record");
            btnSwitchEncoder.setEnabled(true);
        } catch (Exception e1) {
            //
        }
    }

    // Implementation of SrsRtmpListener.

    @Override
    public void onRtmpConnecting(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpConnected(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpVideoStreaming() {
    }

    @Override
    public void onRtmpAudioStreaming() {
    }

    @Override
    public void onRtmpStopped() {
        Toast.makeText(getApplicationContext(), "Stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpDisconnected() {
        Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpVideoFpsChanged(double fps) {
        Log.i(TAG, String.format("Output Fps: %f", fps));
    }

    @Override
    public void onRtmpVideoBitrateChanged(double bitrate) {
        int rate = (int) bitrate;
        if (rate / 1000 > 0) {
            Log.i(TAG, String.format("Video bitrate: %f kbps", bitrate / 1000));
        } else {
            Log.i(TAG, String.format("Video bitrate: %d bps", rate));
        }
    }

    @Override
    public void onRtmpAudioBitrateChanged(double bitrate) {
        int rate = (int) bitrate;
        if (rate / 1000 > 0) {
            Log.i(TAG, String.format("Audio bitrate: %f kbps", bitrate / 1000));
        } else {
            Log.i(TAG, String.format("Audio bitrate: %d bps", rate));
        }
    }

    @Override
    public void onRtmpSocketException(SocketException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIOException(IOException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIllegalStateException(IllegalStateException e) {
        handleException(e);
    }

    // Implementation of SrsRecordHandler.

    @Override
    public void onRecordPause() {
        Toast.makeText(getApplicationContext(), "Record paused", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordResume() {
        Toast.makeText(getApplicationContext(), "Record resumed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordStarted(String msg) {
        Toast.makeText(getApplicationContext(), "Recording file: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordFinished(String msg) {
        Toast.makeText(getApplicationContext(), "MP4 file saved: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordIOException(IOException e) {
        handleException(e);
    }

    @Override
    public void onRecordIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    // Implementation of SrsEncodeHandler.

    @Override
    public void onNetworkWeak() {
        Toast.makeText(getApplicationContext(), "Network weak", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNetworkResume() {
        Toast.makeText(getApplicationContext(), "Network resume", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }
/////////////////////远程代理逻辑///////////////////////////////////
    private static class MyHandle extends Handler {
        WeakReference<MainActivity> mainActivityWeakReference;
        public MyHandle(MainActivity activity)
        {
            mainActivityWeakReference = new WeakReference<>(activity);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.arg1)
            {
                case 1:
                    Runnable r = (Runnable) msg.obj;
                    if(r != null)
                        r.run();
                    break;
                case 2:
                    String logStr = (String) msg.obj;
                    if(logStr != null)
                    {
                        MainActivity activity = mainActivityWeakReference.get();
                        if(activity != null)
                            activity.log(logStr);
                    }
                    break;
            }
        }
    }

    public String GetAgentData()
    {
        JSONObject object = new JSONObject();
        try {
            object.put("url",efu.getText().toString());
            object.put("cam",mPublisher.getCameraId());
            object.put("publish_st",btnPublish.getText());
            object.put("pause_st",btnPause.getText());
            object.put("w",mWidth);
            object.put("h",mHeight);
            object.put("rot",mCameraView.getRotateDeg());
            object.put("flash",mCameraView.getFlashMode());
            object.put("bit_rate",mPublisher.getmBitRate());
            object.put("battery",getBattery());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return object.toString();
    }

    private int getBattery() {
        BatteryManager mBatteryManager = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            mBatteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mBatteryManager != null) {
                return mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            }
        }
        return -1;
    }

    public void exec_cmd(final String cmd)
    {
        agent.RunUIThread(new Runnable() {
            @Override
            public void run() {
                exec_cmd_real(cmd);
            }
        });
    }
    public void exec_cmd_real(String cmd)
    {
        switch (cmd)
        {
            case "publish":
                OnClickPublish();
                break;
            case "pause":
                OnClickPause();
                break;
            case "switch":
                OnClickSwitch();
                break;
            case "reinitcam":
            {
                if(btnPublish.getText().toString().contains("stop"))
                    OnClickPublish();
                initPublisher();
                break;
            }
        }
    }
    public void set_agent_data(final String key, final String val)
    {
        agent.RunUIThread(new Runnable() {
            @Override
            public void run() {
                set_agent_data_real(key,val);
            }
        });
    }
    public void set_agent_data_real(String key,String val)
    {
        switch (key)
        {
            case "url":
                efu.setText(val);
                break;
            case "cam":
                try {
                    int a = Integer.parseInt(val);
                    mPublisher.switchCameraFace(a % Camera.getNumberOfCameras());
                }catch (Exception e)
                {
                    log(e.getMessage());
                }
                break;
            case "w":
                try {
                    int a = Integer.parseInt(val);
                    mWidth = a;
                }catch (Exception e)
                {
                    log(e.getMessage());
                }
                break;
            case "h":
                try {
                    int a = Integer.parseInt(val);
                    mHeight = a;
                }catch (Exception e)
                {
                    log(e.getMessage());
                }
            case "rot":
                try {
                    int a = Integer.parseInt(val);
                    mCameraView.setRotateDeg(a);
                    onOrientationChanged(Configuration.ORIENTATION_LANDSCAPE);
                }catch (Exception e)
                {
                    log(e.getMessage());
                }
            case "flash":
                FlashlightManager.switchFlashlight(val.equals("on"));
                break;
            case "bit_rate":
                try {
                    int a = Integer.parseInt(val);
                    mPublisher.setmBitRate(a);
                }catch (Exception e)
                {
                    log(e.getMessage());
                }
                break;
        }
    }

    public static void hideStatusBar(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView()
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        WindowManager.LayoutParams lp = window.getAttributes();
        window.setAttributes(lp);
    }

}
