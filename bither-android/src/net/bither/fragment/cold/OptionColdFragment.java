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

package net.bither.fragment.cold;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Utils;

import net.bither.BitherSetting;
import net.bither.QrCodeActivity;
import net.bither.R;
import net.bither.ScanActivity;
import net.bither.ScanQRCodeTransportActivity;
import net.bither.activity.cold.ColdActivity;
import net.bither.activity.cold.SignTxActivity;
import net.bither.fragment.Refreshable;
import net.bither.fragment.Selectable;
import net.bither.model.BitherAddressWithPrivateKey;
import net.bither.preference.AppSharedPreference;
import net.bither.ui.base.DialogConfirmTask;
import net.bither.ui.base.DialogPassword;
import net.bither.ui.base.DialogPassword.DialogPasswordListener;
import net.bither.ui.base.DialogProgress;
import net.bither.ui.base.DropdownMessage;
import net.bither.util.BackupUtil;
import net.bither.util.BackupUtil.BackupListener;
import net.bither.util.DateTimeUtil;
import net.bither.util.FileUtil;
import net.bither.util.PrivateKeyUtil;
import net.bither.util.StringUtil;
import net.bither.util.WalletUtils;

import java.util.Date;
import java.util.List;

public class OptionColdFragment extends Fragment implements Selectable {
    private int ONE_HOUR = 1 * 60 * 60 * 1000;

    private Button btnGetSign;
    private Button btnCloneTo;
    private Button btnCloneFrom;
    private Button btnBackupTime;
    private FrameLayout flBackTime;
    private ProgressBar pbBackTime;
    private TextView tvWebsite;
    private TextView tvVersion;
    private LinearLayout llQrForAll;

    private DialogProgress dp;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cold_option, container,
                false);
        initView(view);
        return view;
    }

    @Override
    public void onSelected() {
        configureCloneButton();
        configureQrForAll();
    }

    @Override
    public void onResume() {
        super.onResume();
        configureCloneButton();
        configureQrForAll();
        showBackupTime();
    }

    private void initView(View view) {
        btnGetSign = (Button) view.findViewById(R.id.btn_get_sign);
        btnCloneTo = (Button) view.findViewById(R.id.btn_clone_to);
        btnCloneFrom = (Button) view.findViewById(R.id.btn_clone_from);
        llQrForAll = (LinearLayout) view.findViewById(R.id.ll_qr_all_keys);
        tvVersion = (TextView) view.findViewById(R.id.tv_version);
        tvWebsite = (TextView) view.findViewById(R.id.tv_website);
        tvWebsite.getPaint().setFlags(Paint.UNDERLINE_TEXT_FLAG);
        flBackTime = (FrameLayout) view.findViewById(R.id.ll_back_up);
        pbBackTime = (ProgressBar) view.findViewById(R.id.pb_back_up);
        String version = null;
        try {
            version = getActivity().getPackageManager().getPackageInfo(
                    getActivity().getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        if (version != null) {
            tvVersion.setText(version);
            tvVersion.setVisibility(View.VISIBLE);
        } else {
            tvVersion.setVisibility(View.GONE);
        }
        tvWebsite.setOnClickListener(websiteClick);
        dp = new DialogProgress(getActivity(), R.string.please_wait);
        btnGetSign.setOnClickListener(toSignActivityClickListener);
        btnCloneTo.setOnClickListener(cloneToClick);
        btnCloneFrom.setOnClickListener(cloneFromClick);
        llQrForAll.setOnClickListener(qrForAllClick);
        btnBackupTime = (Button) view.findViewById(R.id.btn_backup_time);
        btnBackupTime.setOnClickListener(backupTimeListener);
        showBackupTime();

    }

    private void showBackupTime() {

        if (FileUtil.existSdCardMounted()) {
            Date date = AppSharedPreference.getInstance()
                    .getLastBackupkeyTime();
            if (date == null) {
                flBackTime.setVisibility(View.GONE);
            } else {
                flBackTime.setVisibility(View.VISIBLE);
                String relativeDate = DateTimeUtil.getRelativeDate(
                        getActivity(), date).toString();
                btnBackupTime.setText(StringUtil.format(
                        getString(R.string.last_time_of_back_up) + " ",
                        relativeDate));
            }
        } else {
            flBackTime.setVisibility(View.VISIBLE);
            btnBackupTime.setText(R.string.no_sd_card_of_back_up);
        }

    }

    private OnClickListener toSignActivityClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (WalletUtils.getPrivateAddressList() == null
                    || WalletUtils.getPrivateAddressList().size() == 0) {
                DropdownMessage.showDropdownMessage(getActivity(),
                        R.string.private_key_is_empty);
                return;
            }
            Intent intent = new Intent(getActivity(), SignTxActivity.class);
            startActivity(intent);
        }
    };

    private OnClickListener cloneToClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            String content = PrivateKeyUtil
                    .getPrivateKeyStringFromAllPrivateAddresses();
            Intent intent = new Intent(getActivity(), QrCodeActivity.class);
            intent.putExtra(BitherSetting.INTENT_REF.TITLE_STRING,
                    getString(R.string.clone_to_title));
            intent.putExtra(BitherSetting.INTENT_REF.QR_CODE_STRING, content);
            startActivity(intent);
        }
    };

    private OnClickListener cloneFromClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getActivity(),
                    ScanQRCodeTransportActivity.class);
            intent.putExtra(BitherSetting.INTENT_REF.TITLE_STRING,
                    getString(R.string.clone_from_title));
            startActivityForResult(intent,
                    BitherSetting.INTENT_REF.CLONE_FROM_REQUEST_CODE);
        }

        ;
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BitherSetting.INTENT_REF.CLONE_FROM_REQUEST_CODE
                && resultCode == Activity.RESULT_OK) {
            String content = data
                    .getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            DialogPassword dialogPassword = new DialogPassword(getActivity(),
                    new CloneFromPasswordListener(content));
            dialogPassword.setCheckPre(false);
            dialogPassword.setTitle(R.string.clone_from_password);
            dialogPassword.show();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }



    private class CloneFromPasswordListener implements DialogPasswordListener {
        private String content;

        public CloneFromPasswordListener(String content) {
            this.content = content;
        }

        @Override
        public void onPasswordEntered(String password) {
            if (dp != null && !dp.isShowing()) {
                dp.setMessage(R.string.clone_from_waiting);
                CloneThread cloneThread = new CloneThread(content, password);
                dp.setThread(cloneThread);
                dp.show();
                cloneThread.start();
            }
        }
    }

    private class CloneThread extends Thread {
        private String content;
        private String password;

        public CloneThread(String content, String password) {
            this.content = content;
            this.password = password;
        }

        public void run() {
            List<ECKey> keys = PrivateKeyUtil.getECKeysFromString(content,
                    password);
            if (keys == null || keys.size() == 0) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dp != null && dp.isShowing()) {
                            dp.setThread(null);
                            dp.dismiss();
                        }
                        DropdownMessage.showDropdownMessage(getActivity(),
                                R.string.clone_from_failed);
                    }
                });
                return;
            }
            for (int i = keys.size() - 1; i >= 0; i--) {
                ECKey key = keys.get(i);
                BitherAddressWithPrivateKey wallet = new BitherAddressWithPrivateKey(
                        false);
                wallet.setKeyCrypter(key.getKeyCrypter());
                wallet.addKey(key);
                WalletUtils.addAddressWithPrivateKey(null, wallet);
            }

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    configureCloneButton();
                    configureQrForAll();
                    if (dp != null && dp.isShowing()) {
                        dp.setThread(null);
                        dp.dismiss();
                    }
                    DropdownMessage.showDropdownMessage(getActivity(),
                            R.string.clone_from_success);
                    if (getActivity() instanceof ColdActivity) {
                        ColdActivity activity = (ColdActivity) getActivity();
                        Fragment f = activity.getFragmentAtIndex(1);
                        if (f != null && f instanceof Refreshable) {
                            Refreshable r = (Refreshable) f;
                            r.doRefresh();
                        }
                        activity.scrollToFragmentAt(1);
                    }
                }
            });
        }
    }

    private OnClickListener qrForAllClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            String content = "";
            List<BitherAddressWithPrivateKey> addresses = WalletUtils
                    .getPrivateAddressList();
            for (int i = 0; i < addresses.size(); i++) {
                String pubStr = Utils.bytesToHexString(addresses.get(i)
                        .getKeys().get(0).getPubKey());
                content += pubStr;
                if (i < addresses.size() - 1) {
                    content += StringUtil.QR_CODE_SPLIT;
                }
            }
            Intent intent = new Intent(getActivity(), QrCodeActivity.class);
            intent.putExtra(BitherSetting.INTENT_REF.QR_CODE_STRING, content);
            intent.putExtra(BitherSetting.INTENT_REF.TITLE_STRING,
                    getString(R.string.qr_code_for_all_addresses_title));
            startActivity(intent);
        }
    };

    private void configureCloneButton() {
        if (WalletUtils.getPrivateAddressList() != null
                && WalletUtils.getPrivateAddressList().size() > 0) {
            btnCloneFrom.setVisibility(View.GONE);
            btnCloneTo.setVisibility(View.VISIBLE);
        } else {
            btnCloneFrom.setVisibility(View.VISIBLE);
            btnCloneTo.setVisibility(View.GONE);
        }
    }

    private void configureQrForAll() {
        if (WalletUtils.getPrivateAddressList() != null
                && WalletUtils.getPrivateAddressList().size() > 0) {
            llQrForAll.setVisibility(View.VISIBLE);
        } else {
            llQrForAll.setVisibility(View.GONE);
        }
    }

    private OnClickListener backupTimeListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            long backupTime = AppSharedPreference.getInstance()
                    .getLastBackupkeyTime().getTime();
            if (backupTime + ONE_HOUR < System.currentTimeMillis()) {
                backupPrivateKey();
            } else {
                DialogConfirmTask dialogConfirmTask = new DialogConfirmTask(
                        getActivity(), getString(R.string.backup_again),
                        new Runnable() {
                            public void run() {
                                backupPrivateKey();
                            }
                        }
                );
                dialogConfirmTask.show();
            }

        }
    };


    private void backupPrivateKey() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pbBackTime.setVisibility(View.VISIBLE);
            }
        });
        BackupUtil.backupColdKey(false, new BackupListener() {

            @Override
            public void backupSuccess() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pbBackTime.setVisibility(View.GONE);
                        showBackupTime();
                    }
                }, 1000);
            }

            @Override
            public void backupError() {

            }
        });
    }

    private OnClickListener websiteClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://bither.net/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                DropdownMessage.showDropdownMessage(getActivity(),
                        R.string.find_browser_error);
            }
        }
    };
}
