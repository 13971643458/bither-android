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

package net.bither.model;

import net.bither.util.PrivateKeyUtil;
import net.bither.util.StringUtil;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.params.MainNetParams;

public class PasswordSeed {
	private String address;
	private String keyStr;

	public PasswordSeed(String str) {
		int indexOfSplit = str.indexOf(StringUtil.QR_CODE_SPLIT);
		address = str.substring(0, indexOfSplit);
		keyStr = str.substring(indexOfSplit + 1);
	}

	public PasswordSeed(BitherAddressWithPrivateKey address) {
		this.address = address.getAddress();
		this.keyStr = PrivateKeyUtil.getPrivateKeyString(
				address.getKeys().get(0).getEncryptedPrivateKey(),
				address.getKeyCrypter());
	}

	public boolean checkPassword(String password) {
		ECKey key = PrivateKeyUtil.getECKeyFromSingleString(keyStr, password);
		if (key == null) {
			return false;
		}
		return StringUtil.compareString(address,
				new Address(MainNetParams.get(), key.getPubKeyHash())
						.toString());
	}

	@Override
	public String toString() {
		return address + StringUtil.QR_CODE_SPLIT + keyStr;
	}

}
