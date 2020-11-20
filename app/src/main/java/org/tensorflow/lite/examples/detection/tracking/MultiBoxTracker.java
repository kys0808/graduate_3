/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.detection.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.tensorflow.lite.examples.detection.R;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier.Recognition;

/** A tracker that handles non-max suppression and matches existing objects to new detections. */
public class MultiBoxTracker {

  // 탐지할 객체 리스트
  private static final String[] OBJECT_LIST = {
          "person",
          "motorcycle",
          "bus",
          "car",
          "truck",
          "bikerider",
          "bollard",
  };
  private static final ArrayList<String> DETECT_OBJECT_LIST = new ArrayList<String>(Arrays.asList(OBJECT_LIST));
  public static boolean isSpeeching = false;

  // 화면 구획
  private static final int BLOCK_SIZE = 3;
  private static final String[][] BLOCK_NAMES = {
          {"LT", "MT", "RT"},
          {"LM", "MM", "RM"},
          {"LB", "MB", "RB"}
  };

  private static final float TEXT_SIZE_DIP = 18;
  private static final float MIN_SIZE = 16.0f;
  private static final int[] COLORS = {
          Color.BLUE,
          Color.RED,
          Color.GREEN,
          Color.YELLOW,
          Color.CYAN,
          Color.MAGENTA,
          Color.WHITE,
          Color.parseColor("#55FF55"),
          Color.parseColor("#FFA500"),
          Color.parseColor("#FF8888"),
          Color.parseColor("#AAAAFF"),
          Color.parseColor("#FFFFAA"),
          Color.parseColor("#55AAAA"),
          Color.parseColor("#AA33AA"),
          Color.parseColor("#0D0068")
  };
  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
  private final Logger logger = new Logger();
  private final Queue<Integer> availableColors = new LinkedList<Integer>();
  private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
  private final Paint boxPaint = new Paint();
  private final float textSizePx;
  private final BorderedText borderedText;
  private Matrix frameToCanvasMatrix;
  private int frameWidth;
  private int frameHeight;
  private int sensorOrientation;

  private Context context;
  private static MediaPlayer mediaPlayer;

  public MultiBoxTracker(final Context context) {
    this.context = context;
    this.mediaPlayer = MediaPlayer.create(context, R.raw.bollard);

    for (final int color : COLORS) {
      availableColors.add(color);
    }

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(10.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  public synchronized void setFrameConfiguration(
          final int width, final int height, final int sensorOrientation) {
    frameWidth = width;
    frameHeight = height;
    this.sensorOrientation = sensorOrientation;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  public synchronized void trackResults(final List<Recognition> results, final long timestamp) {
//    logger.i("Processing %d results from %d", results.size(), timestamp);
    List<Recognition> tmp_results = new ArrayList<Recognition>();

    for(int i = 0; i < results.size(); i++){
      Recognition recog = results.get(i);
      String title = results.get(i).getTitle();

      if(DETECT_OBJECT_LIST.indexOf(title) >= 0){
        tmp_results.add(recog);
      }
    }
    processResults(tmp_results);

    // 이미지 처리
    processDetectedObject(tmp_results);
//    processResults(results);
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  private void processDetectedObject(List<Recognition> results) {
    if(results.size() == 0) return;
    HashMap<String, ArrayList<Pair<String, Float>>> objectCounter = new HashMap<>();
    String maxTitle = "";
    int maxCount = 0;

    for(int i = 0; i < results.size(); i++) {
      Recognition recog = results.get(i);
      String title = results.get(i).getTitle();
      float confidence = recog.getConfidence();
      RectF location = recog.getLocation();

      int xIndex = (int)Math.floor(location.centerX() / (frameWidth / BLOCK_SIZE));
      int yIndex = (int)Math.floor(location.centerY() / (frameHeight / BLOCK_SIZE));

      float areaSize = location.width() * location.height();
      String position = BLOCK_NAMES[xIndex][BLOCK_SIZE - yIndex - 1];

      ArrayList<Pair<String, Float>> postionList = objectCounter.getOrDefault(title, new ArrayList<>());
      Pair<String, Float> dataPair = new Pair<>(position, areaSize);

      postionList.add(dataPair);
      if(postionList.size() > maxCount) {
        maxCount = postionList.size();
        maxTitle = title;
      }

      objectCounter.put(title, postionList);

      logger.i("push " + title + " " + confidence + " " + location);
      logger.i("max" + maxTitle + maxCount);
    }
    // 이미 음성을 송출 중이면, 스킵
    if(isSpeeching) return;

    // {person=[Pair{LM 8816.249}, Pair{LM 12037.989}]}
    logger.i(objectCounter.toString(), objectCounter.size());


    // 객체의 종류가 1개일 때
    if(objectCounter.size() == 1) {
      ArrayList<Pair<String, Float>> detectedList = objectCounter.get(maxTitle);

      if(DETECT_OBJECT_LIST.get(0) == maxTitle) {
        // """
        // person
        // """

        if(detectedList.size() > 4) {
          // 사람이 많을 때

          // 전방에 사람이 많으니 주의하세요.
        } else {
          boolean isMiddle = false;
          for(int i = 0; i < detectedList.size(); i++) {
            String position = detectedList.get(i).first;
            float areaSize = detectedList.get(i).second;

            // 화면의 중앙에 있고, 사이즈가 3만 이상. (총 프레임사이즈는 약 30만)
            if ((position == "MM" || position == "MB") && areaSize > 30000) {
              isMiddle = true;
            }
          }

          if(isMiddle) {
            // 전방의 사람에 주의하세요.
          }
        }

      } else if(DETECT_OBJECT_LIST.get(1) == maxTitle
              || DETECT_OBJECT_LIST.get(2) == maxTitle
              || DETECT_OBJECT_LIST.get(3) == maxTitle
              || DETECT_OBJECT_LIST.get(4) == maxTitle) {
        // """
        // motorcycle, bus, car, truck,
        // """

        boolean isMiddle = false;
        for(int i = 0; i < detectedList.size(); i++) {
          String position = detectedList.get(i).first;
          float areaSize = detectedList.get(i).second;

          // 화면의 중앙에 있고, 사이즈가 10만 이상. (총 프레임사이즈는 약 30만)
          if ((position == "MM" || position == "MB") && areaSize > 100000) {
            isMiddle = true;
          }
        }

        if(isMiddle) {
          // 전방에 차가 있습니다. 주의하세요.
        }
      } else if(DETECT_OBJECT_LIST.get(5) == maxTitle) {
        // """
        // bikerider
        // """

        boolean isMiddle = false;
        for(int i = 0; i < detectedList.size(); i++) {
          String position = detectedList.get(i).first;
          float areaSize = detectedList.get(i).second;

          // 화면의 중앙에 있고, 사이즈가 5만 이상. (총 프레임사이즈는 약 30만)
          if ((position == "MM" || position == "MB") && areaSize > 50000) {
            isMiddle = true;
          }
        }

        if(isMiddle) {
          // 자전거를 탄 사람이 있습니다. 주의하세요.
        }
      } else if(DETECT_OBJECT_LIST.get(6) == maxTitle) {
        // """
        // bollard
        // """

        boolean isBottom = false;
        for(int i = 0; i < detectedList.size(); i++) {
          String position = detectedList.get(i).first;

          // 화면의 아래에 있을 때
          if (position == "LB" || position == "MB" || position == "RB") {
            isBottom = true;
          }
        }

        if(isBottom) {
          // 전방에 볼라드가 있습니다.
        }
      }

    } else {
      // 장애물이 많을 때
    }

    this.isSpeeching = true;

    this.mediaPlayer.start();
    this.mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        MultiBoxTracker.isSpeeching = false;
      }
    });
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  public synchronized void draw(final Canvas canvas) {
    final boolean rotated = sensorOrientation % 180 == 90;
    final float multiplier =
            Math.min(
                    canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                    canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
            ImageUtils.getTransformationMatrix(
                    frameWidth,
                    frameHeight,
                    (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                    (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                    sensorOrientation,
                    false);
    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos = new RectF(recognition.location);

      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
      canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

      final String labelString =
              !TextUtils.isEmpty(recognition.title)
                      ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
                      : String.format("%.2f", (100 * recognition.detectionConfidence));
      //            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top,
      // labelString);
      borderedText.drawText(
              canvas, trackedPos.left + cornerSize, trackedPos.top, labelString + "%", boxPaint);
    }
  }

  private void processResults(final List<Recognition> results) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

      logger.v(
              "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
    }

    trackedObjects.clear();
    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }

    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      final TrackedRecognition trackedRecognition = new TrackedRecognition();
      trackedRecognition.detectionConfidence = potential.first;
      trackedRecognition.location = new RectF(potential.second.getLocation());
      trackedRecognition.title = potential.second.getTitle();
      trackedRecognition.color = COLORS[trackedObjects.size()];
      trackedObjects.add(trackedRecognition);

      if (trackedObjects.size() >= COLORS.length) {
        break;
      }
    }
  }

  private static class TrackedRecognition {
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }
}
