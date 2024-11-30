package com.alltobid.amusic;

import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    public static final String ua = "amusic_" + BuildConfig.VERSION_NAME;

    private final MediaPlayer mMediaPlayer = new MediaPlayer();
    private boolean mMediaPrepared = false;
    private Menu mOptionsMenu;

    private String getUAID() {
        String a2bPref = "a2bPref";
        SharedPreferences sharedPreferences = getSharedPreferences(a2bPref, 0);
        String uaid = sharedPreferences.getString("uaid", "0");
        if (uaid == "0") {
            uaid = String.valueOf(new Random().nextInt(65536) + 1024);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("uaid", uaid);
            editor.commit();
        }
        return ua + "_" + uaid;
    }

    private String getTimeString(long millis) {
        StringBuffer stringBuffer = new StringBuffer();

        int hours = (int) (millis / (1000 * 60 * 60));
        int minutes = (int) ((millis % (1000 * 60 * 60)) / (1000 * 60));
        int seconds = (int) (((millis % (1000 * 60 * 60)) % (1000 * 60)) / 1000);

        stringBuffer
                .append(String.format("%02d", hours))
                .append(":")
                .append(String.format("%02d", minutes))
                .append(":")
                .append(String.format("%02d", seconds));

        return stringBuffer.toString();
    }

    private void pauseMp3() {
        mOptionsMenu.findItem(R.id.action_play).setIcon(R.drawable.ic_menu_play);
        mMediaPlayer.pause();
    }

    private void continueMp3() {
        mOptionsMenu.findItem(R.id.action_play).setIcon(R.drawable.ic_menu_pause);
        mMediaPlayer.start();
    }

    private void stopMp3() {
        pauseMp3();
        mMediaPlayer.stop();
        mMediaPrepared = false;
    }

    private void startMp3(String url) {
        SeekBar a2bSeekBar = (SeekBar) findViewById(R.id.seekBar);
        mMediaPlayer.stop();
        mMediaPlayer.reset();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            Map<String, String> headerMap = new HashMap<String, String>();
            headerMap.put("User-Agent", getUAID());
            mMediaPlayer.setDataSource(getApplicationContext(), Uri.parse(url), headerMap);
            Toast.makeText(getApplicationContext(), "Streaming..", Toast.LENGTH_LONG).show();
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                a2bSeekBar.setMax(mp.getDuration());
                Toast.makeText(getApplicationContext(), "Playing..", Toast.LENGTH_LONG).show();
                continueMp3();
                mMediaPrepared = true;
            }
        });

        mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                double ratio = percent / 100.0;
                a2bSeekBar.setSecondaryProgress((int) (mp.getDuration() * ratio));
            }
        });

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stopMp3();
            }
        });

        Handler seekBarHandler = new Handler();
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mMediaPlayer.isPlaying()) {
                    a2bSeekBar.setProgress(mMediaPlayer.getCurrentPosition());
                    getTimeString(mMediaPlayer.getCurrentPosition());
                    String mediaDuration = " / " + getTimeString(mMediaPlayer.getDuration());
                    setTitle(getTimeString(mMediaPlayer.getCurrentPosition()) + mediaDuration);
                }
                seekBarHandler.postDelayed(this, 1000);
            }
        });
    }

    private void setProgressVisible(boolean visible) {
        SeekBar a2bSeekBar = (SeekBar) findViewById(R.id.seekBar);
        ProgressBar a2bProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        if (visible == true) {
            a2bSeekBar.setVisibility(View.GONE);
            a2bProgressBar.setVisibility(View.VISIBLE);
        } else {
            a2bSeekBar.setVisibility(View.VISIBLE);
            a2bProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setProgressVisible(false);

        WebView a2bWebView = (WebView) findViewById(R.id.webView);
        WebSettings a2bWebViewSettings = a2bWebView.getSettings();
        a2bWebViewSettings.setUserAgentString(getUAID());
        a2bWebViewSettings.setJavaScriptEnabled(true);
        a2bWebViewSettings.setDomStorageEnabled(true);
        a2bWebViewSettings.setAllowContentAccess(true);
        a2bWebViewSettings.setMediaPlaybackRequiresUserGesture(false);
        a2bWebViewSettings.setPluginState(WebSettings.PluginState.ON);

        a2bWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                ProgressBar a2bProgressBar = (ProgressBar) findViewById(R.id.progressBar);
                a2bProgressBar.setProgress(progress);
            }
        });
        a2bWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view,
                                      String url,
                                      Bitmap favicon) {
                setProgressVisible(true);
            }

            @Override
            public void onPageFinished(WebView view,
                                       String url) {
                setProgressVisible(false);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                stopMp3();

                if (url.endsWith(".mp3")) {
                    startMp3(url);
                } else {
                    view.loadUrl(url);
                }

                return true;
            }
        });

        a2bWebView.loadUrl("https://alltobid.529000.xyz/yt/");

        a2bWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "aMusic");
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                downloadManager.enqueue(request);
                Toast.makeText(getApplicationContext(), "Downloading..", Toast.LENGTH_LONG).show();
            }
        });

        SeekBar a2bSeekBar = (SeekBar) findViewById(R.id.seekBar);
        a2bSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if ((mMediaPrepared == true) && (fromUser == true)) {
                    mMediaPlayer.seekTo(progress);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.player_control, menu);
        mOptionsMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_play:
                if (mMediaPrepared == true) {
                    if (mMediaPlayer.isPlaying()) {
                        pauseMp3();
                    } else {
                        continueMp3();
                    }
                }
                return true;
            case R.id.action_refresh:
                WebView a2bWebView = (WebView) findViewById(R.id.webView);
                a2bWebView.reload();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        WebView a2bWebView = (WebView) findViewById(R.id.webView);
        if (a2bWebView.copyBackForwardList().getCurrentIndex() > 0) {
            a2bWebView.goBack();
        } else {
            stopMp3();
            super.onBackPressed();
        }
    }
}