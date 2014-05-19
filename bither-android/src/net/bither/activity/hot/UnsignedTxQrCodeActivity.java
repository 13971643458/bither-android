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

package net.bither.activity.hot;

import net.bither.BitherSetting;
import net.bither.QrCodeActivity;
import net.bither.R;
import net.bither.ScanQRCodeTransportActivity;
import android.content.Intent;

public class UnsignedTxQrCodeActivity extends QrCodeActivity {
	@Override
	protected void complete() {
		Intent intent = new Intent(this, ScanQRCodeTransportActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
		intent.putExtra(BitherSetting.INTENT_REF.TITLE_STRING,
				getString(R.string.scan_transaction_signature_title));
		startActivity(intent);
		finish();
	}

	@Override
	protected String getCompleteButtonTitle() {
		return getString(R.string.unsigned_transaction_qr_code_complete);
	}
}
