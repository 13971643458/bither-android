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

package net.bither.runnable;

import net.bither.bitherj.core.Address;
import net.bither.bitherj.core.AddressManager;
import net.bither.bitherj.core.BitherjSettings;
import net.bither.bitherj.crypto.SecureCharSequence;
import net.bither.bitherj.utils.PrivateKeyUtil;
import net.bither.bitherj.crypto.PasswordSeed;
import net.bither.preference.AppSharedPreference;
import net.bither.util.BackupUtil;
import net.bither.util.ThreadUtil;

import java.util.List;

public class EditPasswordThread extends Thread {
    private SecureCharSequence oldPassword;
    private SecureCharSequence newPassword;
    private EditPasswordListener listener;

    public EditPasswordThread(SecureCharSequence oldPassword, SecureCharSequence newPassword,
                              EditPasswordListener listener) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
        this.listener = listener;
    }

    @Override
    public void run() {
        final boolean result = editPassword(oldPassword, newPassword);
        oldPassword.wipe();
        newPassword.wipe();
        if (listener != null) {
            ThreadUtil.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (result) {
                        listener.onSuccess();
                    } else {
                        listener.onFailed();
                    }
                }
            });
        }
    }

    public static interface EditPasswordListener {
        public void onSuccess();

        public void onFailed();
    }

    public boolean editPassword(SecureCharSequence oldPassword, SecureCharSequence newPassword) {
        List<Address> privKeyAddresses = AddressManager.getInstance().getPrivKeyAddresses();
        List<Address> trashAddresses = AddressManager.getInstance().getTrashAddresses();
        if (privKeyAddresses.size() + trashAddresses.size() == 0) {
            return true;
        }
        try {
            for (Address a : privKeyAddresses) {
                String encryptedStr = a.getEncryptPrivKey();
                String newEncryptedStr = PrivateKeyUtil.changePassword(encryptedStr, oldPassword, newPassword);
                if (newEncryptedStr == null) {
                    return false;
                }
                a.setEncryptPrivKey(newEncryptedStr);
            }
            for (Address a : trashAddresses) {
                String encryptedStr = a.getEncryptPrivKey();
                String newEncryptedStr = PrivateKeyUtil.changePassword(encryptedStr, oldPassword, newPassword);
                if (newEncryptedStr == null) {
                    return false;
                }
                a.setEncryptPrivKey(newEncryptedStr);
            }
            if (privKeyAddresses.size() > 0) {
                AppSharedPreference.getInstance().setPasswordSeed(new PasswordSeed(privKeyAddresses.get
                        (0)));
            } else {
                AppSharedPreference.getInstance().setPasswordSeed(new PasswordSeed(trashAddresses.get
                        (0)));
            }

            for (Address address : privKeyAddresses) {
                address.savePrivateKey();
            }
            for (Address address : trashAddresses) {
                address.saveTrashKey();
            }
            if (AppSharedPreference.getInstance().getAppMode() == BitherjSettings.AppMode.COLD) {
                BackupUtil.backupColdKey(false);
            } else {
                BackupUtil.backupHotKey();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}
