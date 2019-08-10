package com.example.rtp_poc;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

public class AudioCommand {

    private static final String LOG_TAG = AudioCommand.class.getSimpleName();

    private Activity context;

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);

    private static final String LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt";
    private static final String MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.tflite";

    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;

    private List<String> labels = new ArrayList<String>();
    private Interpreter tfLite;

    private RoundRobinBuffer audioBuffer = new RoundRobinBuffer(RECORDING_LENGTH * 2);
    public PublishSubject<String> aWakeStream = PublishSubject.create();

    private CompositeDisposable cd = new CompositeDisposable();

    /** Memory-map the model file in Assets. */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public AudioCommand(Activity context, Observable<byte[]> audioStream) {
        this.context = context;
        // Load the labels for the model, but only display those that don't start
        // with an underscore.
        String actualLabelFilename = LABEL_FILENAME.split("file:///android_asset/", -1)[1];
        Log.i(LOG_TAG, "Reading labels from: " + actualLabelFilename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(context.getAssets().open(actualLabelFilename)));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        }

        String actualModelFilename = MODEL_FILENAME.split("file:///android_asset/", -1)[1];
        Log.i(LOG_TAG, "Reading model from: " + actualModelFilename);
        try {
            tfLite = new Interpreter(loadModelFile(context.getAssets(), actualModelFilename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        tfLite.resizeInput(0, new int[] {RECORDING_LENGTH, 1});
        tfLite.resizeInput(1, new int[] {1});
        cd.add(
            audioStream.subscribeOn(Schedulers.io())
                    .subscribe(new Consumer<byte[]>() {
                @Override
                public void accept(byte[] bytes) throws Exception {
                    audioBuffer.put(bytes);
                }
            })
        );
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                while (shouldContinueRecognition) {
                                    short[] shortBuffer = audioBuffer.getShortBuffer();
                                    String rec = recognize(shortBuffer);
                                    aWakeStream.onNext(rec);
                                }

                            }
                        });
        recognitionThread.start();
    }

    public synchronized void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;
    }

    private String recognize(short[] inputBuffer) {
//        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[][] floatInputBuffer = new float[RECORDING_LENGTH][1];
        float[][] outputScores = new float[1][labels.size()];
        int[] sampleRateList = new int[] {SAMPLE_RATE};

        // We need to feed in float values between -1.0f and 1.0f, so divide the
        // signed 16-bit inputs.
        for (int i = 0; i < RECORDING_LENGTH; ++i) {
            floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
        }


        Object[] inputArray = {floatInputBuffer, sampleRateList};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputScores);

        // Run the model.
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        float[] result = outputScores[0];
        Log.d(LOG_TAG, Arrays.toString(result));

        int goCommand = 11;
        float max = 0;
        int maxIndex = 0;

        for (int i= 0; i<result.length; i++) {
            if (result[i] >max ) {
                max = result[i];
                maxIndex = i;
            }
        }

        if (max > 0.5) {
            return labels.get(maxIndex);
        }
        return "";

    }



}
