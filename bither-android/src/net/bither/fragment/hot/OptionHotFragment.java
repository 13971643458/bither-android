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

package net.bither.fragment.hot;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import net.bither.BitherSetting;
import net.bither.R;
import net.bither.activity.hot.CheckPrivateKeyActivity;
import net.bither.activity.hot.NetworkMonitorActivity;
import net.bither.fragment.Selectable;
import net.bither.image.glcrop.CropImageGlActivity;
import net.bither.model.Market;
import net.bither.preference.AppSharedPreference;
import net.bither.ui.base.DialogConfirmTask;
import net.bither.ui.base.DialogDonate;
import net.bither.ui.base.DialogProgress;
import net.bither.ui.base.DialogSetAvatar;
import net.bither.ui.base.DropdownMessage;
import net.bither.ui.base.SettingSelectorView;
import net.bither.ui.base.SettingSelectorView.SettingSelector;
import net.bither.util.ExchangeUtil.ExchangeType;
import net.bither.util.LogUtil;
import net.bither.util.MarketUtil;
import net.bither.util.ThreadUtil;
import net.bither.util.TransactionsUtil.TransactionFeeMode;
import net.bither.util.UIUtil;
import net.bither.util.WalletUtils;

import java.util.List;

public class OptionHotFragment extends Fragment implements Selectable, DialogSetAvatar.SetAvatarDelegate {
    private SettingSelectorView ssvCurrency;
    private SettingSelectorView ssvMarket;
    private SettingSelectorView ssvWifi;
    private SettingSelectorView ssvTransactionFee;
    private Button btnAvatar;
    private Button btnCheck;
    private Button btnDonate;
    private TextView tvWebsite;
    private TextView tvVersion;
    private ImageView ivLogo;

    private DialogProgress dp;
    private OnClickListener logoClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getActivity(),
                    NetworkMonitorActivity.class);
            startActivity(intent);
        }
    };
    private SettingSelector currencySelector = new SettingSelector() {

        @Override
        public void onOptionIndexSelected(int index) {
            if (index == 0) {
                AppSharedPreference.getInstance().setExchangeType(
                        ExchangeType.USD);
            } else {
                AppSharedPreference.getInstance().setExchangeType(
                        ExchangeType.CNY);
            }
        }

        @Override
        public String getSettingName() {
            return getString(R.string.setting_name_currency);
        }

        @Override
        public String getOptionName(int index) {
            if (index == 0) {
                return "USD";
            }
            return "CNY";
        }

        @Override
        public int getOptionCount() {
            return 2;
        }

        @Override
        public int getCurrentOptionIndex() {
            return AppSharedPreference.getInstance().getDefaultExchangeRate()
                    .ordinal();
        }

        @Override
        public String getOptionNote(int index) {
            return null;
        }
    };
    private SettingSelector wifiSelector = new SettingSelector() {

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
            boolean onlyUseWifi = AppSharedPreference.getInstance()
                    .getSyncBlockOnlyWifi();
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
    };
    private SettingSelector marketSelector = new SettingSelector() {
        private List<Market> markets = MarketUtil.getMarkets();

        @Override
        public void onOptionIndexSelected(int index) {
            AppSharedPreference.getInstance().setMarketType(
                    markets.get(index).getMarketType());
        }

        @Override
        public String getSettingName() {
            return getString(R.string.setting_name_market);
        }

        @Override
        public String getOptionName(int index) {
            return markets.get(index).getName();
        }

        @Override
        public int getOptionCount() {
            return markets.size();
        }

        @Override
        public int getCurrentOptionIndex() {
            return markets.indexOf(MarketUtil.getDefaultMarket());
        }

        @Override
        public String getOptionNote(int index) {
            return null;
        }
    };
    private SettingSelector transactionFeeModeSelector = new SettingSelector() {

        @Override
        public void onOptionIndexSelected(int index) {
            if (index == TransactionFeeMode.Low.ordinal()) {
                DialogConfirmTask dialog = new DialogConfirmTask(
                        getActivity(),
                        getString(R.string.setting_name_transaction_fee_low_warn),
                        new Runnable() {
                            @Override
                            public void run() {
                                ssvTransactionFee.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        AppSharedPreference.getInstance()
                                                .setTransactionFeeMode(
                                                        TransactionFeeMode.Low);
                                        ssvTransactionFee.loadData();
                                    }
                                });
                            }
                        }
                );
                dialog.show();
            } else {
                AppSharedPreference.getInstance().setTransactionFeeMode(
                        getModeByIndex(index));
            }
        }

        @Override
        public String getSettingName() {
            return getString(R.string.setting_name_transaction_fee);
        }

        @Override
        public String getOptionName(int index) {
            switch (getModeByIndex(index)) {
                case Low:
                    return getString(R.string.setting_name_transaction_fee_low);
                default:
                    return getString(R.string.setting_name_transaction_fee_normal);
            }
        }

        @Override
        public int getOptionCount() {
            return TransactionFeeMode.values().length;
        }

        @Override
        public int getCurrentOptionIndex() {
            return AppSharedPreference.getInstance().getTransactionFeeMode()
                    .ordinal();
        }

        private TransactionFeeMode getModeByIndex(int index) {
            if (index >= 0 && index < TransactionFeeMode.values().length) {
                return TransactionFeeMode.values()[index];
            }
            return TransactionFeeMode.Normal;
        }

        @Override
        public String getOptionNote(int index) {
            switch (getModeByIndex(index)) {
                case Low:
                    return getString(R.string.setting_name_transaction_fee_low_note);
                default:
                    return getString(R.string.setting_name_transaction_fee_normal_note);
            }
        }
    };
    private OnClickListener checkClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (WalletUtils.getPrivateAddressList() == null
                    || WalletUtils.getPrivateAddressList().size() == 0) {
                DropdownMessage.showDropdownMessage(getActivity(),
                        R.string.private_key_is_empty);
                return;
            }
            Intent intent = new Intent(getActivity(),
                    CheckPrivateKeyActivity.class);
            startActivity(intent);
        }
    };
    private OnClickListener donateClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            DialogDonate dialog = new DialogDonate(getActivity());
            dialog.show();
        }
    };
    private OnClickListener avatarClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            DialogSetAvatar dialog = new DialogSetAvatar(getActivity(), OptionHotFragment.this);
            dialog.show();
        }
    };
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_hot_option, container,
                false);
        initView(view);
        return view;
    }

    private void initView(View view) {
        ssvCurrency = (SettingSelectorView) view
                .findViewById(R.id.ssv_currency);
        ssvMarket = (SettingSelectorView) view.findViewById(R.id.ssv_market);
        ssvWifi = (SettingSelectorView) view.findViewById(R.id.ssv_wifi);
        ssvTransactionFee = (SettingSelectorView) view
                .findViewById(R.id.ssv_transaction_fee);
        tvVersion = (TextView) view.findViewById(R.id.tv_version);
        tvWebsite = (TextView) view.findViewById(R.id.tv_website);
        tvWebsite.getPaint().setFlags(Paint.UNDERLINE_TEXT_FLAG);
        ivLogo = (ImageView) view.findViewById(R.id.iv_logo);
        btnAvatar = (Button) view.findViewById(R.id.btn_avatar);
        btnCheck = (Button) view.findViewById(R.id.btn_check_private_key);
        btnDonate = (Button) view.findViewById(R.id.btn_donate);
        ssvCurrency.setSelector(currencySelector);
        ssvMarket.setSelector(marketSelector);
        ssvWifi.setSelector(wifiSelector);
        ssvTransactionFee.setSelector(transactionFeeModeSelector);
        dp = new DialogProgress(getActivity(), R.string.please_wait);
        dp.setCancelable(false);
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
        btnCheck.setOnClickListener(checkClick);
        btnDonate.setOnClickListener(donateClick);
        btnAvatar.setOnClickListener(avatarClick);
        tvWebsite.setOnClickListener(websiteClick);
        ivLogo.setOnClickListener(logoClickListener);
        updateAvatar();
    }

    @Override
    public void avatarFromCamera() {
        // TODO avatar from camera
    }

    @Override
    public void avatarFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, BitherSetting.REQUEST_CODE_IMAGE);
    }

    private void updateAvatar() {
        // TODO get avatar here
        Bitmap avatar = null;
        if (avatar != null) {
            new UpdateAvatarThread(avatar).start();
        } else {
            btnAvatar.setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable(R.drawable.avatar_button_icon), null);
        }
    }

    @Override
    public void onSelected() {
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == BitherSetting.REQUEST_CODE_IMAGE || requestCode == BitherSetting.REQUEST_CODE_CAMERA) && resultCode == Activity.RESULT_OK) {
            LogUtil.d("image", "REQUEST_CODE_IMAGE");
            Intent intent = new Intent(getActivity(), CropImageGlActivity.class);
            intent.setData(data.getData());
            intent.setAction(data.getAction());
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(intent);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class UpdateAvatarThread extends Thread {
        private Bitmap avatar;

        private UpdateAvatarThread(Bitmap avatar) {
            this.avatar = avatar;
        }

        @Override
        public void run() {
            int borderPadding = UIUtil.dip2pix(2);
            Bitmap bmpBorder = BitmapFactory.decodeResource(getResources(), R.drawable.avatar_button_icon_border);
            Bitmap result = Bitmap.createBitmap(bmpBorder.getWidth(), bmpBorder.getHeight(), bmpBorder.getConfig());
            Canvas c = new Canvas(result);
            c.drawBitmap(avatar, null, new Rect(borderPadding, borderPadding, result.getWidth() - borderPadding, result.getHeight() - borderPadding), null);
            c.drawBitmap(bmpBorder, 0, 0, null);
            final BitmapDrawable d = new BitmapDrawable(getResources(), result);
            ThreadUtil.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    btnAvatar.setCompoundDrawablesWithIntrinsicBounds(null, null, d, null);
                }
            });
        }
    }

}
