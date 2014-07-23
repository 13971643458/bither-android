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

package net.bither.activity.hot;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.google.bitcoin.core.ECKey;

import net.bither.BitherSetting;
import net.bither.R;
import net.bither.ScanActivity;
import net.bither.ScanQRCodeTransportActivity;
import net.bither.model.BitherAddressWithPrivateKey;
import net.bither.preference.AppSharedPreference;
import net.bither.runnable.ThreadNeedService;
import net.bither.service.BlockchainService;
import net.bither.ui.base.DialogEditPassword;
import net.bither.ui.base.DialogImportPrivateKeyText;
import net.bither.ui.base.DialogPassword;
import net.bither.ui.base.DialogProgress;
import net.bither.ui.base.DropdownMessage;
import net.bither.ui.base.SettingSelectorView;
import net.bither.ui.base.SwipeRightActivity;
import net.bither.ui.base.listener.BackClickListener;
import net.bither.util.PrivateKeyUtil;
import net.bither.util.WalletUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by songchenwen on 14-7-23.
 */
public class HotAdvanceActivity extends SwipeRightActivity {
    private SettingSelectorView ssvWifi;
    private Button btnEditPassword;
    private SettingSelectorView ssvImportPrivateKey;
    private DialogProgress dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hot_advance_options);
        initView();
    }

    private void initView() {
        findViewById(R.id.ibtn_back).setOnClickListener(new BackClickListener());
        ssvWifi = (SettingSelectorView) findViewById(R.id.ssv_wifi);
        btnEditPassword = (Button) findViewById(R.id.btn_edit_password);
        ssvImportPrivateKey = (SettingSelectorView) findViewById(R.id.ssv_import_private_key);
        ssvWifi.setSelector(wifiSelector);
        ssvImportPrivateKey.setSelector(importPrivateKeySelector);
        btnEditPassword.setOnClickListener(editPasswordClick);
        dp = new DialogProgress(this, R.string.please_wait);
    }

    private SettingSelectorView.SettingSelector wifiSelector = new SettingSelectorView
            .SettingSelector() {

        @Override
        public void onOptionIndexSelected(int index) {
            final boolean isOnlyWifi = index == 1;
            AppSharedPreference.getInstance().setSyncBlockOnlyWifi(isOnlyWifi);
        }

        @Override
        public String getSettingName() {
            return getString(R.string.setting_name_wifi);
        }

        @Override
        public String getOptionName(int index) {
            if (index == 1) {
                return getString(R.string.setting_name_wifi_yes);
            } else {
                return getString(R.string.setting_name_wifi_no);

            }
        }

        @Override
        public int getOptionCount() {
            return 2;
        }

        @Override
        public int getCurrentOptionIndex() {
            boolean onlyUseWifi = AppSharedPreference.getInstance().getSyncBlockOnlyWifi();
            if (onlyUseWifi) {
                return 1;
            } else {
                return 0;
            }

        }

        @Override
        public String getOptionNote(int index) {
            return null;
        }

        @Override
        public Drawable getOptionDrawable(int index) {
            return null;
        }
    };

    private View.OnClickListener editPasswordClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            DialogEditPassword dialog = new DialogEditPassword(HotAdvanceActivity.this);
            dialog.show();
        }
    };

    private SettingSelectorView.SettingSelector importPrivateKeySelector = new
            SettingSelectorView.SettingSelector() {
        @Override
        public int getOptionCount() {
            return 2;
        }

        @Override
        public String getOptionName(int index) {
            switch (index) {
                case 0:
                    return getString(R.string.import_private_key_qr_code);
                case 1:
                    return getString(R.string.import_private_key_text);
                default:
                    return "";
            }
        }

        @Override
        public String getOptionNote(int index) {
            return null;
        }

        @Override
        public Drawable getOptionDrawable(int index) {
            switch (index) {
                case 0:
                    return getResources().getDrawable(R.drawable.scan_button_icon);
                case 1:
                    return getResources().getDrawable(R.drawable.import_private_key_text_icon);
                default:
                    return null;
            }
        }

        @Override
        public String getSettingName() {
            return getString(R.string.setting_name_import_private_key);
        }

        @Override
        public int getCurrentOptionIndex() {
            return -1;
        }

        @Override
        public void onOptionIndexSelected(int index) {
            switch (index) {
                case 0:
                    importPrivateKeyFromQrCode();
                    return;
                case 1:
                    importPrivateKeyFromText();
                    return;
                default:
                    return;
            }
        }
    };

    private void importPrivateKeyFromQrCode() {
        Intent intent = new Intent(this, ScanQRCodeTransportActivity.class);
        intent.putExtra(BitherSetting.INTENT_REF.TITLE_STRING,
                getString(R.string.import_private_key_qr_code_scan_title));
        startActivityForResult(intent, BitherSetting.INTENT_REF.IMPORT_PRIVATE_KEY_REQUEST_CODE);
    }

    private void importPrivateKeyFromText() {
        new DialogImportPrivateKeyText(this).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case BitherSetting.INTENT_REF.IMPORT_PRIVATE_KEY_REQUEST_CODE:
                String content = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                DialogPassword dialogPassword = new DialogPassword(this,
                        new ImportPrivateKeyPasswordListener(content));
                dialogPassword.setCheckPre(false);
                dialogPassword.setTitle(R.string.import_private_key_qr_code_password);
                dialogPassword.show();
                break;
        }
    }


    private class ImportPrivateKeyPasswordListener implements DialogPassword
            .DialogPasswordListener {
        private String content;

        public ImportPrivateKeyPasswordListener(String content) {
            this.content = content;
        }

        @Override
        public void onPasswordEntered(String password) {
            if (dp != null && !dp.isShowing()) {
                dp.setMessage(R.string.import_private_key_qr_code_importing);
                ImportPrivateKeyThread importPrivateKeyThread = new ImportPrivateKeyThread(dp,
                        content, password);
                importPrivateKeyThread.start();
            }
        }
    }

    private class ImportPrivateKeyThread extends ThreadNeedService {
        private String content;
        private String password;
        private DialogProgress dp;

        public ImportPrivateKeyThread(DialogProgress dp, String content, String password) {
            super(dp, HotAdvanceActivity.this);
            this.dp = dp;
            this.content = content;
            this.password = password;
        }

        @Override
        public void runWithService(BlockchainService service) {
            ECKey key = PrivateKeyUtil.getECKeyFromSingleString(content, password);
            if (key == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dp != null && dp.isShowing()) {
                            dp.setThread(null);
                            dp.dismiss();
                        }
                        DropdownMessage.showDropdownMessage(HotAdvanceActivity.this,
                                R.string.import_private_key_qr_code_failed);
                    }
                });
                return;
            }
            BitherAddressWithPrivateKey wallet = new BitherAddressWithPrivateKey(false);
            wallet.setKeyCrypter(key.getKeyCrypter());
            wallet.addKey(key);
            if (WalletUtils.getWatchOnlyAddressList().contains(wallet)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dp != null && dp.isShowing()) {
                            dp.setThread(null);
                            dp.dismiss();
                        }
                        DropdownMessage.showDropdownMessage(HotAdvanceActivity.this,
                                R.string.import_private_key_qr_code_failed_monitored);
                    }
                });
                return;
            } else if (WalletUtils.getPrivateAddressList().contains(wallet)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dp != null && dp.isShowing()) {
                            dp.setThread(null);
                            dp.dismiss();
                        }
                        DropdownMessage.showDropdownMessage(HotAdvanceActivity.this,
                                R.string.import_private_key_qr_code_failed_duplicate);
                    }
                });
                return;
            } else {
                if (!AppSharedPreference.getInstance().getPasswordSeed().checkPassword(password)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (dp != null && dp.isShowing()) {
                                dp.setThread(null);
                                dp.dismiss();
                            }
                            DropdownMessage.showDropdownMessage(HotAdvanceActivity.this,
                                    R.string.import_private_key_qr_code_failed_different_password);
                        }
                    });
                    return;
                }
                List<BitherAddressWithPrivateKey> wallets = new
                        ArrayList<BitherAddressWithPrivateKey>();
                wallets.add(wallet);
                WalletUtils.addAddressWithPrivateKey(service, wallets);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (dp != null && dp.isShowing()) {
                        dp.setThread(null);
                        dp.dismiss();
                    }
                    DropdownMessage.showDropdownMessage(HotAdvanceActivity.this,
                            R.string.import_private_key_qr_code_success);
                }
            });
        }
    }
}
