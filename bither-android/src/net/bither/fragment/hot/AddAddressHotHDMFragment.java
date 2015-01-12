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

package net.bither.fragment.hot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import net.bither.R;
import net.bither.activity.hot.AddHotAddressActivity;
import net.bither.bitherj.core.AddressManager;
import net.bither.bitherj.core.HDMAddress;
import net.bither.bitherj.core.HDMBId;
import net.bither.bitherj.core.HDMKeychain;
import net.bither.bitherj.crypto.hd.DeterministicKey;
import net.bither.bitherj.crypto.hd.HDKeyDerivation;
import net.bither.bitherj.utils.Utils;
import net.bither.preference.AppSharedPreference;
import net.bither.qrcode.ScanActivity;
import net.bither.ui.base.DropdownMessage;
import net.bither.ui.base.dialog.DialogConfirmTask;
import net.bither.ui.base.dialog.DialogHDMServerUnsignedQRCode;
import net.bither.ui.base.dialog.DialogPassword;
import net.bither.ui.base.dialog.DialogProgress;
import net.bither.ui.base.dialog.DialogWithActions;
import net.bither.util.ThreadUtil;
import net.bither.util.UIUtil;
import net.bither.xrandom.HDMKeychainHotUEntropyActivity;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by songchenwen on 15/1/9.
 */
public class AddAddressHotHDMFragment extends Fragment implements AddHotAddressActivity
        .AddAddress, DialogPassword.PasswordGetter.PasswordGetterDelegate {
    private static final int XRandomRequestCode = 1552;
    private static final int ScanColdRequestCode = 1623;
    private static final int ServerQRCodeRequestCode = 1135;

    private FrameLayout flContainer;
    private View vBg;
    private View llHot;
    private View llCold;
    private View llServer;
    private DialogPassword.PasswordGetter passwordGetter;
    private DialogProgress dp;

    private boolean isServerClicked = false;

    private HDMBId hdmBid;

    private byte[] coldRoot;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_add_address_hot_hdm, container, false);
        initView(v);
        findCurrentStep();
        return v;
    }

    private void initView(View v) {
        flContainer = (FrameLayout) v.findViewById(R.id.fl_container);
        vBg = v.findViewById(R.id.v_bg);
        llHot = v.findViewById(R.id.ll_hot);
        llCold = v.findViewById(R.id.ll_cold);
        llServer = v.findViewById(R.id.ll_server);
        ViewGroup.LayoutParams lpContainer = flContainer.getLayoutParams();
        int size = Math.min(UIUtil.getScreenHeight(), UIUtil.getScreenWidth());
        lpContainer.height = size;
        lpContainer.width = size;
        llHot.setOnClickListener(hotClick);
        llCold.setOnClickListener(coldClick);
        llServer.setOnClickListener(serverClick);
        dp = new DialogProgress(getActivity(), R.string.please_wait);
        dp.setCancelable(false);
        passwordGetter = new DialogPassword.PasswordGetter(getActivity(), this);
    }

    private View.OnClickListener hotClick = new DialogWithActions.DialogWithActionsClickListener() {
        @Override
        protected List<DialogWithActions.Action> getActions() {
            ArrayList<DialogWithActions.Action> actions = new ArrayList<DialogWithActions.Action>();
            actions.add(new DialogWithActions.Action(R.string.hdm_keychain_add_hot_from_xrandom,
                    new Runnable() {
                @Override
                public void run() {
                    HDMKeychainHotUEntropyActivity.passwordGetter = passwordGetter;
                    startActivityForResult(new Intent(getActivity(),
                            HDMKeychainHotUEntropyActivity.class), XRandomRequestCode);
                }
            }));
            actions.add(new DialogWithActions.Action(R.string
                    .hdm_keychain_add_hot_not_from_xrandom, new Runnable() {
                @Override
                public void run() {
                    new Thread() {
                        @Override
                        public void run() {
                            HDMKeychain keychain = new HDMKeychain(new SecureRandom(),
                                    passwordGetter.getPassword());
                            AddressManager.getInstance().setHDMKeychain(keychain);
                            if (AppSharedPreference.getInstance().getPasswordSeed() == null) {
                                AppSharedPreference.getInstance().setPasswordSeed(keychain
                                        .createPasswordSeed(passwordGetter.getPassword()));
                            }
                            ThreadUtil.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    if(dp.isShowing()) {
                                        dp.dismiss();
                                    }
                                    moveToCold(true);
                                }
                            });
                        }
                    }.start();
                }
            }));
            return actions;
        }
    };

    private View.OnClickListener coldClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            new DialogConfirmTask(getActivity(), getString(R.string.hdm_keychain_add_scan_cold),
                    new Runnable() {

                @Override
                public void run() {
                    ThreadUtil.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(getActivity(), ScanActivity.class);
                            startActivityForResult(intent, ScanColdRequestCode);
                        }
                    });
                }
            }).show();
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (XRandomRequestCode == requestCode && resultCode == Activity.RESULT_OK &&
                AddressManager.getInstance().getHdmKeychain() != null) {
            llCold.postDelayed(new Runnable() {
                @Override
                public void run() {
                    moveToCold(true);
                }
            }, 500);
        }
        if (ScanColdRequestCode == requestCode && resultCode == Activity.RESULT_OK) {
            String result = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            try {
                coldRoot = Utils.hexStringToByteArray(result);
                final int count = 100 - AddressManager.getInstance().getHdmKeychain()
                        .uncompletedAddressCount();
                if (!dp.isShowing() && passwordGetter.hasPassword() && count > 0) {
                    dp.show();
                }
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            if (count > 0) {
                                AddressManager.getInstance().getHdmKeychain().prepareAddresses
                                        (count, passwordGetter.getPassword(), Arrays.copyOf(coldRoot, coldRoot.length));
                            }
                            initHDMBidFromColdRoot();
                            ThreadUtil.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (dp.isShowing()) {
                                        dp.dismiss();
                                    }
                                    if (isServerClicked) {
                                        serverClick.onClick(llServer);
                                    } else {
                                        moveToServer(true);
                                    }
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            coldRoot = null;
                            ThreadUtil.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (dp.isShowing()) {
                                        dp.dismiss();
                                    }
                                    DropdownMessage.showDropdownMessage(getActivity(),
                                            R.string.hdm_keychain_add_scan_cold);
                                }
                            });
                        }
                    }
                }.start();
            } catch (Exception e) {
                e.printStackTrace();
                coldRoot = null;
                DropdownMessage.showDropdownMessage(getActivity(),
                        R.string.hdm_keychain_add_scan_cold);
            }
        }
        if (ServerQRCodeRequestCode == requestCode && resultCode == Activity.RESULT_OK) {
            final String result = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            if (hdmBid == null) {
                return;
            }
            if (!dp.isShowing()) {
                dp.show();
            }
            new Thread() {
                @Override
                public void run() {
                    try {
                        hdmBid.setSignString(result, passwordGetter.getPassword());
                        final List<HDMAddress> as = AddressManager.getInstance().getHdmKeychain()
                                .completeAddresses(1, passwordGetter.getPassword(),
                                        new HDMKeychain.HDMFetchRemotePublicKeys() {
                                            @Override
                                            public void completeRemotePublicKeys(CharSequence
                                                                                         password, List<HDMAddress.Pubs> partialPubs) {
                                                //TODO get pubs from server for hdm
                                            }
                                        });
                        ThreadUtil.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                if (dp.isShowing()) {
                                    dp.dismiss();
                                }
                                if (as.size() > 0) {

                                    moveToFinal();
                                }
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        ThreadUtil.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                if (dp.isShowing()) {
                                    dp.dismiss();
                                }
                            }
                        });
                    }
                }
            }.start();
        }
    }

    private View.OnClickListener serverClick = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (coldRoot == null && hdmBid == null) {
                isServerClicked = true;
                coldClick.onClick(llCold);
                return;
            }
            if (!dp.isShowing()) {
                dp.show();
            }
            new Thread() {
                @Override
                public void run() {
                    try {
                        initHDMBidFromColdRoot();
                        final String preSign = hdmBid.getPreSignString();
                        ThreadUtil.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                if (dp.isShowing()) {
                                    dp.dismiss();
                                }
                                new DialogHDMServerUnsignedQRCode(getActivity(), preSign,
                                        new DialogHDMServerUnsignedQRCode
                                                .DialogHDMServerUnsignedQRCodeListener() {
                                    @Override
                                    public void scanSignedHDMServerQRCode() {
                                        startActivityForResult(new Intent(getActivity(),
                                                ScanActivity.class), ServerQRCodeRequestCode);
                                    }
                                }).show();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (dp.isShowing()) {
                            dp.dismiss();
                        }
                    }
                }
            }.start();
        }
    };

    private void findCurrentStep() {
        moveToHot(false);
        if (AddressManager.getInstance().getHdmKeychain() != null) {
            moveToCold(false);
            if (AddressManager.getInstance().getHdmKeychain().uncompletedAddressCount() > 0) {
                moveToServer(false);
            }
        }
    }

    private void moveToHot(boolean anim) {
        llHot.setEnabled(true);
        llHot.setSelected(false);
        llCold.setEnabled(false);
        llCold.setSelected(false);
        llServer.setEnabled(false);
        llServer.setSelected(false);
    }

    private void moveToCold(boolean anim) {
        llHot.setEnabled(false);
        llHot.setSelected(true);
        llCold.setEnabled(true);
        llCold.setSelected(false);
        llServer.setEnabled(false);
        llServer.setSelected(false);
    }

    private void moveToServer(boolean anim) {
        if (llServer.isEnabled()) {
            return;
        }
        llHot.setEnabled(false);
        llHot.setSelected(true);
        llCold.setEnabled(false);
        llCold.setSelected(true);
        llServer.setEnabled(true);
        llServer.setSelected(false);
    }

    private void moveToFinal() {
        llHot.setEnabled(false);
        llHot.setSelected(true);
        llCold.setEnabled(false);
        llCold.setSelected(true);
        llServer.setEnabled(false);
        llServer.setSelected(true);
        llServer.postDelayed(new Runnable() {
            @Override
            public void run() {
                ((AddHotAddressActivity) getActivity()).save();
            }
        }, 500);
    }

    private void initHDMBidFromColdRoot() {
        if(hdmBid != null){
            return;
        }
        DeterministicKey root = HDKeyDerivation.createMasterPubKeyFromExtendedBytes(Arrays.copyOf
                (coldRoot, coldRoot.length));
        DeterministicKey key = root.deriveSoftened(0);
        String address = Utils.toAddress(key.getPubKeyHash());
        root.wipe();
        key.wipe();
        hdmBid = new HDMBId(address);
    }

    @Override
    public ArrayList<String> getAddresses() {
        List<HDMAddress> as = AddressManager.getInstance().getHdmKeychain().getAddresses();
        ArrayList<String> s = new ArrayList<String>();
        for (HDMAddress a : as) {
            s.add(a.getAddress());
        }
        return s;
    }


    @Override
    public void onDestroyView() {
        if (passwordGetter != null) {
            passwordGetter.wipe();
        }
        if (coldRoot != null) {
            Utils.wipeBytes(coldRoot);
        }
        super.onDestroyView();
    }


    @Override
    public void beforePasswordDialogShow() {
        if (dp != null && dp.isShowing()) {
            dp.dismiss();
        }
    }

    @Override
    public void afterPasswordDialogDismiss() {
        if (dp != null && !dp.isShowing()) {
            dp.show();
        }
    }
}
