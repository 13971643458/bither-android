/*
 *
 *  * Copyright 2014 http://Bither.net
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package net.bither.xrandom;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;

import net.bither.bitherj.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by songchenwen on 14-9-11.
 */
public class UEntropyMotion implements SensorEventListener, IUEntropySource {

    private UEntropyCollector collector;
    private SensorManager sensorManager;
    private List<Sensor> sensors;

    private boolean paused = true;

    public UEntropyMotion(Context context, UEntropyCollector collector) {
        this.collector = collector;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensors = new ArrayList<Sensor>();
    }

    private void registerAllSensors() {
        if (sensors.size() == 0) {
            sensors.clear();
            sensors.addAll(sensorManager.getSensorList(Sensor.TYPE_ALL));
        }
        ArrayList<Sensor> unregisteredSensors = new ArrayList<Sensor>();
        for (Sensor sensor : sensors) {
            boolean registered = sensorManager.registerListener(this, sensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            if (!registered) {
                unregisteredSensors.add(sensor);
            }
            LogUtil.i(UEntropyMotion.class.getSimpleName(), (registered ? "" : "fail to ") +
                    "register sensor " + sensor.getName());
        }
        sensors.removeAll(unregisteredSensors);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event != null && event.values != null) {
            byte[] data = new byte[event.values.length * Ints.BYTES];
            byte[] everyData;
            for (int i = 0;
                 i < event.values.length;
                 i++) {
                int hash = Floats.hashCode(event.values[i]);
                everyData = Ints.toByteArray(hash);
                for (int j = 0;
                     j < Ints.BYTES;
                     j++) {
                    if (everyData.length > j) {
                        data[i * Ints.BYTES + j] = everyData[j];
                    } else {
                        data[i * Ints.BYTES + j] = 0;
                    }
                }
            }
            collector.onNewData(data, UEntropyCollector.UEntropySource.Motion);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onResume() {
        if (paused) {
            registerAllSensors();
        }
    }

    @Override
    public void onPause() {
        if (!paused) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public UEntropyCollector.UEntropySource type() {
        return UEntropyCollector.UEntropySource.Motion;
    }
}
