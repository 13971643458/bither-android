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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ProgressBar;

import net.bither.BitherSetting;
import net.bither.R;
import net.bither.bitherj.core.Address;
import net.bither.bitherj.crypto.ECKey;
import net.bither.bitherj.crypto.XRandom;
import net.bither.bitherj.utils.LogUtil;
import net.bither.bitherj.utils.PrivateKeyUtil;
import net.bither.preference.AppSharedPreference;
import net.bither.runnable.ThreadNeedService;
import net.bither.service.BlockchainService;
import net.bither.ui.base.dialog.DialogPassword;
import net.bither.ui.base.dialog.DialogProgress;
import net.bither.util.KeyUtil;
import net.bither.util.SecureCharSequence;
import net.bither.xrandom.audio.AudioVisualizerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by songchenwen on 14-9-11.
 */
public class UEntropyActivity extends Activity implements UEntropyCollector
        .UEntropyCollectorListener, DialogPassword.DialogPasswordListener {
    public static final String PrivateKeyCountKey = UEntropyActivity.class.getName() + "" +
            ".private_key_count_key";
    private static final Logger log = LoggerFactory.getLogger(UEntropyActivity.class);

    private static final long VIBRATE_DURATION = 50L;

    private Vibrator vibrator;

    private UEntropyCollector entropyCollector;
    private GenerateThread generateThread;

    private View vOverlay;
    private ProgressBar pb;
    private DialogProgress dpCancel;

    private int targetCount;


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, R.anim.scanner_in_exit);
        targetCount = getIntent().getExtras().getInt(PrivateKeyCountKey, 0);
        if (targetCount <= 0) {
            finish();
            return;
        }
        setContentView(R.layout.activity_uentropy);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vOverlay = findViewById(R.id.v_overlay);
        pb = (ProgressBar) findViewById(R.id.pb);
        findViewById(R.id.ibtn_cancel).setOnClickListener(cancelClick);
        dpCancel = new DialogProgress(this, R.string.xrandom_stopping);
        dpCancel.setCancelable(false);

        entropyCollector = new UEntropyCollector(this);

        entropyCollector.addSources(
                new UEntropyCamera((SurfaceView) findViewById(R.id.scan_activity_preview), entropyCollector),
                new UEntropyMic(entropyCollector, (AudioVisualizerView) findViewById(R.id.v_mic)),
                new UEntropyMotion(this, entropyCollector)
        );
        generateThread = new GenerateThread();

        vOverlay.postDelayed(new Runnable() {
            @Override
            public void run() {
                DialogPassword dialogPassword = new DialogPassword(UEntropyActivity.this,
                        UEntropyActivity.this);
                dialogPassword.setNeedCancelEvent(true);
                dialogPassword.show();
            }
        }, 600);
    }

    @Override
    protected void onResume() {
        super.onResume();
        entropyCollector.onResume();
    }

    @Override
    protected void onPause() {
        entropyCollector.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (generateThread.isAlive()) {
            cancelGenerate();
        } else {
            finish();
        }
    }

    private View.OnClickListener cancelClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onBackPressed();
        }
    };

    private void cancelGenerate() {
        dpCancel.show();
        generateThread.cancel(cancelRunnable);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_CAMERA:
                // don't launch camera app
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void vibrate() {
        vibrator.vibrate(VIBRATE_DURATION);
    }

    public void finish() {
        if (dpCancel != null && dpCancel.isShowing()) {
            dpCancel.dismiss();
        }
        super.finish();
        overridePendingTransition(R.anim.scanner_out_enter, 0);
    }

    @Override
    public void onUEntropySourceError(Exception e, IUEntropySource source) {
        log.warn("UEntropyCollectorError source: {}, {}", source.type().name(), e.getMessage());
    }

    private void startAnimation() {
        AlphaAnimation anim = new AlphaAnimation(1, 0);
        anim.setFillAfter(true);
        anim.setDuration(500);
        vOverlay.startAnimation(anim);
    }

    private void stopAnimation(final Runnable finishRun) {
        AlphaAnimation anim = new AlphaAnimation(0, 1);
        anim.setFillAfter(true);
        anim.setDuration(500);
        vOverlay.startAnimation(anim);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                finishRun.run();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private void onProgress(final double progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int p = (int) (pb.getMax() * progress);
                LogUtil.i(UEntropyActivity.class.getSimpleName(), "progress " + p);
                pb.setProgress(p);
            }
        });
    }

    private void onSuccess(final ArrayList<String> addresses) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopAnimation(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent();
                        intent.putExtra(BitherSetting.INTENT_REF
                                .ADD_PRIVATE_KEY_SUGGEST_CHECK_TAG,
                                AppSharedPreference.getInstance().getPasswordSeed() == null);
                        intent.putExtra(BitherSetting.INTENT_REF.ADDRESS_POSITION_PASS_VALUE_TAG,
                                addresses);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                });
            }
        });
    }

    private void onFailed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopAnimation(new Runnable() {
                    @Override
                    public void run() {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                });
            }
        });
    }

    private Runnable cancelRunnable = new Runnable() {
        @Override
        public void run() {
            if (dpCancel.isShowing()) {
                dpCancel.dismiss();
            }
            finish();
        }
    };

    @Override
    public void onPasswordEntered(final SecureCharSequence password) {
        if (password == null) {
            setResult(RESULT_CANCELED);
            finish();
        } else {
            startAnimation();
            generateThread.setPassword(password);
            generateThread.start();
        }
    }

    private class GenerateThread extends ThreadNeedService {
        private double saveProgress = 0.1;
        private double startProgress = 0.01;
        private double progressKeyRate = 0.5;
        private double progressEntryptRate = 0.5;

        private SecureCharSequence password;
        private Runnable cancelRunnable;

        public GenerateThread() {
            super(null, UEntropyActivity.this);
        }

        public void setPassword(SecureCharSequence password) {
            this.password = password;
        }

        @Override
        public synchronized void start() {
            if (password == null) {
                throw new IllegalStateException("GenerateThread does not have password");
            }
            super.start();
            onProgress(startProgress);
        }

        public void cancel(Runnable cancelRunnable) {
            this.cancelRunnable = cancelRunnable;
        }

        private void finishGenerate(BlockchainService service) {
            if (service != null) {
                service.startAndRegister();
            }
            entropyCollector.stop();
        }

        @Override
        public void runWithService(BlockchainService service) {
            boolean success = false;
            final ArrayList<String> addressStrs = new ArrayList<String>();
            double progress = startProgress;
            double itemProgress = (1.0 - startProgress - saveProgress) / (double) targetCount;

            try {
                entropyCollector.start();
                if (service != null) {
                    service.stopAndUnregister();
                }

                List<Address> addressList = new ArrayList<Address>();
                for (int i = 0;
                     i < targetCount;
                     i++) {
                    if (cancelRunnable != null) {
                        finishGenerate(service);
                        runOnUiThread(cancelRunnable);
                        return;
                    }

                    XRandom xRandom = new XRandom(entropyCollector);
                    ECKey ecKey = ECKey.generateECKey(xRandom);

                    progress += itemProgress * progressKeyRate;
                    onProgress(progress);
                    if (cancelRunnable != null) {
                        finishGenerate(service);
                        runOnUiThread(cancelRunnable);
                        return;
                    }


                    // start encrypt
                    ecKey = PrivateKeyUtil.encrypt(ecKey, password);
                    Address address = new Address(ecKey.toAddress(), ecKey.getPubKey(),
                            PrivateKeyUtil.getPrivateKeyString(ecKey));
                    addressList.add(address);
                    addressStrs.add(0, address.getAddress());

                    progress += itemProgress * progressEntryptRate;
                    onProgress(progress);
                }
                entropyCollector.stop();

                if (cancelRunnable != null) {
                    finishGenerate(service);
                    runOnUiThread(cancelRunnable);
                    return;
                }

                KeyUtil.addAddressList(null, addressList);

                finishGenerate(service);
                success = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            password.wipe();
            password = null;
            if (success) {
                onProgress(1);
                onSuccess(addressStrs);
            } else {
                onFailed();
            }
        }
    }
}
