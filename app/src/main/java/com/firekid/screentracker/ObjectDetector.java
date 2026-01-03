package com.firekid.screentracker;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ObjectDetector {

    private static final String MODEL_FILE = "model.tflite";
    private static final int INPUT_SIZE = 300;
    private static final float CONFIDENCE_THRESHOLD = 0.5f;

    private Interpreter interpreter;
    private int inputWidth;
    private int inputHeight;

    public static class Detection {
        public float x1, y1, x2, y2;
        public int centerX, centerY;
        public float confidence;
        public int classId;

        public Detection(float x1, float y1, float x2, float y2, float confidence, int classId) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.centerX = (int) ((x1 + x2) / 2);
            this.centerY = (int) ((y1 + y2) / 2);
            this.confidence = confidence;
            this.classId = classId;
        }
    }

    public ObjectDetector(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(modelBuffer, options);

            int[] inputShape = interpreter.getInputTensor(0).shape();
            inputHeight = inputShape[1];
            inputWidth = inputShape[2];
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public Detection[] detect(Bitmap bitmap) {
        if (interpreter == null || bitmap == null) {
            return new Detection[0];
        }

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true);
        ByteBuffer inputBuffer = bitmapToByteBuffer(resizedBitmap);

        float[][][] boxes = new float[1][10][4];
        float[][] scores = new float[1][10];
        float[][] classes = new float[1][10];
        float[] numDetections = new float[1];

        Object[] inputs = {inputBuffer};
        java.util.Map<Integer, Object> outputs = new java.util.HashMap<>();
        outputs.put(0, boxes);
        outputs.put(1, classes);
        outputs.put(2, scores);
        outputs.put(3, numDetections);

        try {
            interpreter.runForMultipleInputsOutputs(inputs, outputs);
        } catch (Exception e) {
            e.printStackTrace();
            return new Detection[0];
        }

        int numDet = Math.min((int) numDetections[0], 10);
        java.util.ArrayList<Detection> detectionList = new java.util.ArrayList<>();

        for (int i = 0; i < numDet; i++) {
            float confidence = scores[0][i];

            if (confidence > CONFIDENCE_THRESHOLD) {
                float ymin = boxes[0][i][0] * bitmap.getHeight();
                float xmin = boxes[0][i][1] * bitmap.getWidth();
                float ymax = boxes[0][i][2] * bitmap.getHeight();
                float xmax = boxes[0][i][3] * bitmap.getWidth();
                int classId = (int) classes[0][i];

                detectionList.add(new Detection(xmin, ymin, xmax, ymax, confidence, classId));
            }
        }

        return detectionList.toArray(new Detection[0]);
    }

    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * 3);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputWidth * inputHeight];
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        int pixel = 0;
        for (int i = 0; i < inputHeight; i++) {
            for (int j = 0; j < inputWidth; j++) {
                int val = pixels[pixel++];

                buffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                buffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                buffer.putFloat((val & 0xFF) / 255.0f);
            }
        }

        return buffer;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}
