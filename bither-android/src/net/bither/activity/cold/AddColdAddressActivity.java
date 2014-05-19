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

package net.bither.activity.cold;

import java.util.ArrayList;

import net.bither.BitherSetting;
import net.bither.R;
import net.bither.model.BitherAddressWithPrivateKey;
import net.bither.ui.base.AddAddressPrivateKeyView;
import net.bither.ui.base.AddPrivateKeyActivity;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageButton;

public class AddColdAddressActivity extends AddPrivateKeyActivity {

	private FrameLayout flContainer;
	private ImageButton ibtnCancel;

	private AddAddressPrivateKeyView vPrivateKey;
	private InputMethodManager imm;

	@Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);
		overridePendingTransition(R.anim.activity_in_drop,
				R.anim.activity_out_back);
		setContentView(R.layout.activity_add_cold_address);
		initView();
		flContainer.postDelayed(new Runnable() {
			@Override
			public void run() {
				imm.showSoftInput(
						flContainer.getChildAt(0)
								.findViewById(R.id.et_password), 0);
			}
		}, 400);
	}

	private void initView() {
		flContainer = (FrameLayout) findViewById(R.id.fl_container);
		ibtnCancel = (ImageButton) findViewById(R.id.btn_cancel);
		imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		ibtnCancel.setOnClickListener(cancelClick);

		flContainer.addView(getPrivateKeyView(), LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT);
	}

	private AddAddressPrivateKeyView getPrivateKeyView() {
		if (vPrivateKey == null) {
			vPrivateKey = new AddAddressPrivateKeyView(this);
		}
		return vPrivateKey;
	}

	private OnClickListener cancelClick = new OnClickListener() {

		@Override
		public void onClick(View v) {
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
	};

	public void save() {
		View c = flContainer.getChildAt(0);
		if (c instanceof AddAddressPrivateKeyView) {
			ArrayList<BitherAddressWithPrivateKey> addresses = getPrivateKeyView()
					.getAddresses();
			ArrayList<String> as = new ArrayList<String>();
			if (addresses.size() > 0) {
				for (BitherAddressWithPrivateKey address : addresses) {
					as.add(address.getAddress());
				}
				Intent intent = new Intent();
				intent.putExtra(
						BitherSetting.INTENT_REF.ADDRESS_POSITION_PASS_VALUE_TAG,
						as);
				setResult(Activity.RESULT_OK, intent);
				finish();
			}
		} else {
			return;
		}
	}

	public void finish() {
		imm.hideSoftInputFromWindow(flContainer.getWindowToken(), 0);
		super.finish();
		overridePendingTransition(0, R.anim.slide_out_bottom);
	};

}
