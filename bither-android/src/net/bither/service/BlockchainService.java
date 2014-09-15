/*
 * Copyright 2014 http://Bither.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bither.service;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import net.bither.BitherApplication;
import net.bither.R;
import net.bither.bitherj.core.AddressManager;
import net.bither.bitherj.core.BitherjSettings;
import net.bither.bitherj.core.BlockChain;
import net.bither.bitherj.core.PeerManager;
import net.bither.bitherj.exception.BlockStoreException;
import net.bither.bitherj.utils.LogUtil;
import net.bither.bitherj.utils.NotificationUtil;
import net.bither.preference.AppSharedPreference;
import net.bither.runnable.DownloadSpvRunnable;
import net.bither.util.BitherTimer;
import net.bither.util.BlockUtil;
import net.bither.util.BroadcastUtil;
import net.bither.util.NetworkUtil;
import net.bither.util.NetworkUtil.NetworkType;
import net.bither.util.TransactionsUtil;
import net.bither.util.UpgradeUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BlockchainService extends android.app.Service {

    public static final String ACTION_BEGIN_DOWLOAD_SPV_BLOCK = R.class
            .getPackage().getName() + ".dowload_block_api_begin";
    public static final String ACTION__TIMER_TASK_STAT = R.class.getPackage()
            .getName() + ".timer_task_stat";
    public static final String ACTION_TIMER_TASK_ISRUNNING = "timer_task_stat";
    private static final Logger log = LoggerFactory
            .getLogger(BlockchainService.class);
    private WakeLock wakeLock;

    private long serviceCreatedAt;
    private BitherTimer mBitherTimer;
    private SPVFinishedReceiver spvFinishedReceiver = null;
    private TickReceiver tickReceiver = null;
    private TxReceiver txReceiver = null;

    private boolean connectivityReceivered = false;

    private final IBinder mBinder = new LocalBinder(BlockchainService.this);

    @Override
    public void onCreate() {
        serviceCreatedAt = System.currentTimeMillis();
        log.info(".onCreate()");
        super.onCreate();
        final String lockName = getPackageName() + " blockchain sync";
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName);
        if (AppSharedPreference.getInstance().getAppMode() != BitherjSettings.AppMode.COLD) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    startPeer();
                }
            }).start();
            tickReceiver = new TickReceiver(BlockchainService.this);
            txReceiver = new TxReceiver(BlockchainService.this, tickReceiver);
            receiverConnectivity();
            registerReceiver(tickReceiver, new IntentFilter(
                    Intent.ACTION_TIME_TICK));
            registerReceiver(txReceiver, new IntentFilter(NotificationUtil.ACTION_ADDRESS_BALANCE));

        }
    }

    private void receiverConnectivity() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
        intentFilter
                .addAction(BroadcastUtil.ACTION_START_DOWLOAD_BLOCK_STATE);
        registerReceiver(connectivityReceiver, intentFilter);
        connectivityReceivered = true;

    }

    @Override
    public void onDestroy() {
        log.info(".onDestroy()");
        if (AppSharedPreference.getInstance().getAppMode() != BitherjSettings.AppMode.COLD) {
            BitherApplication.scheduleStartBlockchainService(this);
            PeerManager.instance().stop();
            pauseMarkTimerTask();
            mBitherTimer = null;
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
            if (connectivityReceivered) {
                unregisterReceiver(connectivityReceiver);
                connectivityReceivered = false;
            }
            unregisterReceiver(tickReceiver);
            unregisterReceiver(txReceiver);
            BroadcastUtil.removeMarketState();
        }
        super.onDestroy();

        log.info("service was up for "
                + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60)
                + " minutes");
    }

    @Override
    public void onLowMemory() {
        log.warn("low memory detected, stopping service");
        stopSelf();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags,
                              final int startId) {
        final String action = intent.getAction();
        if (action != null) {
            LogUtil.i("onStartCommand", "onStartCommand Service:" + action);
        }
        if (ACTION__TIMER_TASK_STAT.equals(action)) {
            boolean isRunning = intent.getBooleanExtra(
                    ACTION_TIMER_TASK_ISRUNNING, false);
            if (isRunning) {
                startMarkTimerTask();
            } else {
                pauseMarkTimerTask();
            }
        } else if (ACTION_BEGIN_DOWLOAD_SPV_BLOCK.equals(action)) {
            new Thread(new DownloadSpvRunnable(BlockchainService.this)).start();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        log.debug(".onBind()");
        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        log.debug(".onUnbind()");

        return super.onUnbind(intent);
    }

    private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        private boolean hasConnectivity;
        private boolean hasStorage = true;

        @Override
        public void onReceive(final Context context, final Intent intent) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        onReceive(intent);
                    } catch (BlockStoreException e) {
                        e.printStackTrace();
                    }

                }
            }).start();

        }

        private void onReceive(final Intent intent) throws BlockStoreException {
            final String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                hasConnectivity = !intent.getBooleanExtra(
                        ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                log.info("network is " + (hasConnectivity ? "up" : "down"));
                check();
            } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
                hasStorage = false;
                log.info("device storage low");

                check();
            } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
                hasStorage = true;
                log.info("device storage ok");

                check();
            } else if (BroadcastUtil.ACTION_START_DOWLOAD_BLOCK_STATE
                    .equals(action)) {
                hasStorage = true;
                check();
            }
        }

        @SuppressLint("Wakelock")
        private void check() throws BlockStoreException {
            BitherjSettings.AppMode mode = AppSharedPreference.getInstance().getAppMode();
            if (mode == BitherjSettings.AppMode.COLD) {
                return;
            }
            final boolean hasEverything = hasConnectivity && hasStorage;
            NetworkType networkType = NetworkUtil.isConnectedType();
            boolean networkIsAvailadble = (!AppSharedPreference.getInstance().getSyncBlockOnlyWifi())
                    || (networkType == NetworkType.Wifi);

            if (networkIsAvailadble) {
                if (hasEverything && BlockChain.getInstance() != null) {
                    log.debug("acquiring wakelock");
                    callWekelock();
                    if (!PeerManager.instance().isConnected()) {
                        startPeer();
                    }
                } else if (!hasEverything) {
                    PeerManager.instance().stop();
                }
            } else {
                PeerManager.instance().stop();
            }


        }
    };

    public void stopAndUnregister() {
        if (connectivityReceivered) {
            unregisterReceiver(connectivityReceiver);
            connectivityReceivered = false;
        }
        PeerManager.instance().stop();
    }

    public void startAndRegister() {
        receiverConnectivity();
        startPeer();
    }

    private void callWekelock() {
        if ((wakeLock != null) && // we have a WakeLock
                !wakeLock.isHeld()) { // but we don't hold it
            try {
                // WakeLock.acquireLocked(PowerManager.java:329) sdk16 nullpoint
                wakeLock.acquire();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    private boolean spvFinishedReceivered = false;

    private synchronized void startPeer() {
        try {
            if (UpgradeUtil.needUpgrade()) {
                return;
            }
            if (!AppSharedPreference.getInstance().getDownloadSpvFinish()) {
                BlockUtil.dowloadSpvBlock();
            }
            if (AppSharedPreference.getInstance().getAppMode() != BitherjSettings.AppMode.COLD) {
                if (!AppSharedPreference.getInstance().getBitherjDoneSyncFromSpv()) {
                    if (!PeerManager.instance().isConnected()) {
                        PeerManager.instance().start();
                        if (!spvFinishedReceivered) {
                            final IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(NotificationUtil.ACTION_SYNC_FROM_SPV_FINISHED);
                            spvFinishedReceiver = new SPVFinishedReceiver();
                            registerReceiver(spvFinishedReceiver, intentFilter);
                            spvFinishedReceivered = true;
                        }
                    }
                } else {
                    if (!AddressManager.getInstance().addressIsSyncComplete()) {
                        TransactionsUtil.getMyTxFromBither();
                    }
                    startPeerManager();

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void startPeerManager() {
        if (AddressManager.getInstance().addressIsSyncComplete()
                && AppSharedPreference.getInstance().getBitherjDoneSyncFromSpv()
                && AppSharedPreference.getInstance().getDownloadSpvFinish()) {
            NetworkType networkType = NetworkUtil.isConnectedType();
            boolean networkIsAvailadble = (!AppSharedPreference.getInstance().getSyncBlockOnlyWifi())
                    || (networkType == NetworkType.Wifi);
            if (networkIsAvailadble && !PeerManager.instance().isConnected()) {
                PeerManager.instance().start();
            }
        }

    }

    private void pauseMarkTimerTask() {
        if (mBitherTimer != null) {
            mBitherTimer.pauseTimer();
        }


    }

    public void startMarkTimerTask() {
        if (AppSharedPreference.getInstance().getAppMode() == BitherjSettings.AppMode.HOT) {
            if (mBitherTimer == null) {
                mBitherTimer = new BitherTimer(BlockchainService.this);
                mBitherTimer.startTimer();
            }
        }
    }


    public class SPVFinishedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogUtil.d("block", "sendBroadcastSyncSPVFinished onReceive");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!AddressManager.getInstance().addressIsSyncComplete()) {
                            TransactionsUtil.getMyTxFromBither();
                        }
                        startPeerManager();
                        NotificationUtil.removeBroadcastSyncSPVFinished();
                        if (spvFinishedReceiver != null && spvFinishedReceivered) {
                            unregisterReceiver(spvFinishedReceiver);
                            spvFinishedReceivered = false;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();


        }
    }


}
