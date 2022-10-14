package com.chinonso.wearos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


import static com.chinonso.wearos.Global.BufferSize;
import static com.chinonso.wearos.Global.Channel;
import static com.chinonso.wearos.Global.Encoding;
import static com.chinonso.wearos.Global.HeadChirpBeginFrequency;
import static com.chinonso.wearos.Global.HeadChirpEndFrequency;
import static com.chinonso.wearos.Global.HeadChirpLength;
import static com.chinonso.wearos.Global.OutputFileName;
import static com.chinonso.wearos.Global.ProfileChirpLength;
import static com.chinonso.wearos.Global.RawFileName;
import static com.chinonso.wearos.Global.RecordFileName;
import static com.chinonso.wearos.Global.SamplingRate;
import static com.chinonso.wearos.Global.ReadWaveFile;
import static com.chinonso.wearos.Global.GenerateAudioFile;
import static com.chinonso.wearos.Global.WriteWaveFileHeader;


public class MainActivity extends Activity implements SensorEventListener {


    Button StartRecordButton;
    Button StopRecordButton;

    private static final String TAG = "MainActivity";
    private TextView mTextViewDecode;
    MediaPlayer mediaPlayer;

    private IMUConfig mConfig = new IMUConfig();
    private IMUSession mIMUSession;

    String text = "111111111111111";

    boolean Recording = false;

    private MediaPlayer.OnCompletionListener onCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer m) {
            if (mediaPlayer == null)
                return;
            mediaPlayer.release();
            mediaPlayer = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.round_activity_main);
        // Keep the Wear screen always on (for testing only!)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mTextViewDecode = (TextView) findViewById(R.id.decode);

        StartRecordButton = (Button) findViewById(R.id.start_recording);
        StopRecordButton = (Button) findViewById(R.id.stop_recording);
        super.onCreate(savedInstanceState);

        ActivityCompat.requestPermissions(this, new String[]{
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        }, 0);

        //getStepCount();
        mIMUSession = new IMUSession(this);
        final Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        //double[] signal = Modulate.Encode(text);
//        double[] signal = new double[ProfileChirpLength];
//        Modulate.Chirp(1000, 8000, signal, 0, ProfileChirpLength);
       // GenerateAudioFile(signal, directory);

        StartRecordButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                Long tsLong = System.currentTimeMillis()/1000;
                String ts = tsLong.toString();
                String directory = "/storage/emulated/0/data/" + ts + '/';

                File newFile = new File(directory);
                if (!newFile.exists()){
                    newFile.mkdirs();}
                StopRecordButton.setEnabled(true);
                StartRecordButton.setEnabled(false);
//                try {
//                    mediaPlayer = new MediaPlayer();
//                    mediaPlayer.setDataSource(directory + OutputFileName);
//                    mediaPlayer.setOnCompletionListener(onCompletionListener);
//                    mediaPlayer.setLooping(true);
//                    mediaPlayer.prepare();
//                    mediaPlayer.start();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mConfig.setOutputFolder(directory);
                        // start each session
                        mIMUSession.startSession(directory);
                        // Vibrate with pattern
//                       long[] pattern = {250, 250, 250, 250};
//                       vibrator.vibrate(pattern, 1);
                        long[] wave_time = {100, 100, 100, 100, 100, 100, 100, 100, 100, 100};
                        VibrationEffect vibrationEffect = null;
                        vibrationEffect = VibrationEffect.createWaveform(wave_time, 0);
                        vibrator.vibrate(vibrationEffect);
                        AudioRecord(directory);
                        WriteWaveFile(directory + RecordFileName, directory);
                    }
                });
                thread.start();
            }
        });

        StopRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Recording = false;
                StopRecordButton.setEnabled(false);
                StartRecordButton.setEnabled(true);
                mIMUSession.stopSession();
                vibrator.cancel();
//                mediaPlayer.release();
//                try {
//                    double[] signal = ReadWaveFile(directory + RecordFileName);
//                    mTextViewDecode.setText(Demodulate.Decode(signal));
//                } catch (IOException e) {
//                    Log.e("AcousticCommunication", "read record file failed");
//                }
            }
        });
    }

    private void getStepCount() {
        SensorManager mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));

        Sensor GYROSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor ACCSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, GYROSensor, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, ACCSensor, SensorManager.SENSOR_DELAY_FASTEST);

    }



    void AudioRecord(String path) {
        File file = new File(path + RawFileName);
        if (file.exists())
            file.delete();
        try {
            file.createNewFile();
        } catch (IOException e) {
            Log.e("AcousticCommunication", "failed to create file " + file.toString());
            Log.e("AcousticCommunication", e.getMessage());
            //e.printStackTrace();
        }
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SamplingRate, Channel, Encoding, BufferSize);
            byte[] buffer = new byte[BufferSize];
            audioRecord.startRecording();
            Recording = true;
            while (Recording) {
                int length = audioRecord.read(buffer, 0, BufferSize);
                for (int i = 0; i < length; i++)
                    dataOutputStream.write(buffer[i]);

            }
            audioRecord.stop();
            dataOutputStream.flush();
            dataOutputStream.close();
        } catch (Throwable t) {
            Log.e("AcousticCommunication", "record failed");
        }
    }



    private void WriteWaveFile(String name, String path) {
        File file = new File(path + RecordFileName);
        if (file.exists())
            file.delete();
        int channels = 1;
        long byteRate = 16 * SamplingRate * channels / 8;
        byte[] data = new byte[BufferSize];
        try {
            file.createNewFile();
            FileInputStream fileInputStream = new FileInputStream(path + RawFileName);
            FileOutputStream fileOutputStream = new FileOutputStream(name);
            long audioLength = fileInputStream.getChannel().size();
            long dataLength = audioLength + 36;
            WriteWaveFileHeader(fileOutputStream, audioLength, dataLength, (long) SamplingRate, channels, byteRate);
            while (fileInputStream.read(data) != -1)
                fileOutputStream.write(data);
            fileInputStream.close();
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            Log.e("AcousticCommunication", "audio file not found");
        } catch (IOException e) {
            Log.e("AcousticCommunication", "write wave file failed");
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }


    public void onSensorChanged(SensorEvent event) {
    }

}