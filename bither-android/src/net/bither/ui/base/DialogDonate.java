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

package net.bither.ui.base;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bither.BitherSetting;
import net.bither.R;
import net.bither.SendActivity;
import net.bither.activity.hot.GenerateUnsignedTxActivity;
import net.bither.activity.hot.SelectAddressToSendActivity;
import net.bither.model.BitherAddress;
import net.bither.util.GenericUtils;
import net.bither.util.UIUtil;
import net.bither.util.WalletUtils;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.bitcoin.core.Wallet.BalanceType;

public class DialogDonate extends CenterDialog implements OnDismissListener,
		OnShowListener {
	private static final int ListItemHeight = UIUtil.dip2pix(45);
	private static final int MinListHeight = UIUtil.dip2pix(100);
	private static final int MaxListHeight = Math.min(UIUtil.dip2pix(360),
			UIUtil.getScreenHeight() - UIUtil.dip2pix(70));

	private ListView lv;
	private ProgressBar pb;
	private TextView tvNoAddress;
	private FrameLayout fl;
	private ArrayList<AddressBalance> addresses = new ArrayList<AddressBalance>();
	private Intent intent;

	public DialogDonate(Context context) {
		super(context);
		setContentView(R.layout.dialog_donate);
		setOnDismissListener(this);
		setOnShowListener(this);
		tvNoAddress = (TextView) findViewById(R.id.tv_no_address);
		lv = (ListView) findViewById(R.id.lv);
		pb = (ProgressBar) findViewById(R.id.pb);
		fl = (FrameLayout) findViewById(R.id.fl);
		lv.setAdapter(adapter);
	}

	@Override
	public void onShow(DialogInterface dialog) {
		loadData();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		if (intent != null) {
			if (getContext() instanceof Activity) {
				Activity a = (Activity) getContext();
				a.startActivityForResult(intent,
						SelectAddressToSendActivity.SEND_REQUEST_CODE);
			} else {
				getContext().startActivity(intent);
			}
		}
	}

	private void loadData() {
		pb.setVisibility(View.VISIBLE);
		lv.setVisibility(View.INVISIBLE);
		tvNoAddress.setVisibility(View.GONE);
		addresses.clear();
		new Thread() {
			public void run() {
				List<BitherAddress> as = WalletUtils.getBitherAddressList(true);
				ArrayList<AddressBalance> availableAddresses = new ArrayList<AddressBalance>();
				for (BitherAddress a : as) {
					BigInteger balance = a.getBalance(BalanceType.AVAILABLE);
					if (balance.compareTo(BigInteger.ZERO) > 0) {
						availableAddresses.add(new AddressBalance(a, balance));
					}
				}
				addresses.addAll(availableAddresses);
				Collections.sort(addresses, Collections.reverseOrder());
				lv.post(new Runnable() {
					@Override
					public void run() {
						fl.getLayoutParams().height = getFlHeight();
						adapter.notifyDataSetChanged();
						if (addresses.size() > 0) {
							lv.setVisibility(View.VISIBLE);
							tvNoAddress.setVisibility(View.GONE);
						} else {
							lv.setVisibility(View.GONE);
							tvNoAddress.setVisibility(View.VISIBLE);
						}
						pb.setVisibility(View.GONE);
					}
				});
			}
		}.start();
	}

	private BaseAdapter adapter = new BaseAdapter() {
		private LayoutInflater inflater;

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (inflater == null) {
				inflater = LayoutInflater.from(getContext());
			}
			ViewHolder h;
			if (convertView != null) {
				h = (ViewHolder) convertView.getTag();
			} else {
				convertView = inflater.inflate(
						R.layout.list_item_select_address_to_send, null);
				h = new ViewHolder(convertView);
				convertView.setTag(h);
			}
			AddressBalance a = getItem(position);
			h.tvAddress.setText(a.address.getShortAddress());
			h.tvBalance.setText(GenericUtils.formatValueWithBold(a.balance));
			if (a.address.hasPrivateKey()) {
				h.ivType.setImageResource(R.drawable.address_type_private);
			} else {
				h.ivType.setImageResource(R.drawable.address_type_watchonly);
			}
			h.ibtnAddressFull.setVisibility(View.GONE);
			convertView.setOnClickListener(new ListItemClick(a));
			return convertView;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public AddressBalance getItem(int position) {
			return addresses.get(position);
		}

		@Override
		public int getCount() {
			return addresses.size();
		}

		class ViewHolder {
			TextView tvAddress;
			TextView tvBalance;
			ImageView ivType;
			ImageButton ibtnAddressFull;

			public ViewHolder(View v) {
				tvAddress = (TextView) v.findViewById(R.id.tv_address);
				tvBalance = (TextView) v.findViewById(R.id.tv_balance);
				ivType = (ImageView) v.findViewById(R.id.iv_type);
				ibtnAddressFull = (ImageButton) v
						.findViewById(R.id.ibtn_address_full);
				tvAddress.setTextColor(Color.WHITE);
				tvBalance.setTextColor(Color.WHITE);
			}
		}
	};

	private class ListItemClick implements View.OnClickListener {
		private AddressBalance address;

		public ListItemClick(AddressBalance address) {
			this.address = address;
		}

		@Override
		public void onClick(View v) {
			int position;
			Class<?> target;
			if (address.address.hasPrivateKey()) {
				position = WalletUtils.getPrivateAddressList().indexOf(
						address.address);
				target = SendActivity.class;
			} else {
				position = WalletUtils.getWatchOnlyAddressList().indexOf(
						address.address);
				target = GenerateUnsignedTxActivity.class;
			}
			intent = new Intent(getContext(), target);
			intent.putExtra(
					BitherSetting.INTENT_REF.ADDRESS_POSITION_PASS_VALUE_TAG,
					position);
			intent.putExtra(SelectAddressToSendActivity.INTENT_EXTRA_ADDRESS,
					BitherSetting.DONATE_ADDRESS);
			if (address.balance.subtract(BitherSetting.DONATE_AMOUNT).signum() > 0) {
				intent.putExtra(
						SelectAddressToSendActivity.INTENT_EXTRA_AMOUNT,
						BitherSetting.DONATE_AMOUNT);
			} else {
				intent.putExtra(
						SelectAddressToSendActivity.INTENT_EXTRA_AMOUNT,
						address.balance);
			}
			dismiss();
		}
	}

	private static final class AddressBalance implements
			Comparable<AddressBalance> {
		public BitherAddress address;
		public BigInteger balance;

		public AddressBalance(BitherAddress address, BigInteger balance) {
			this.address = address;
			this.balance = balance;
		}

		@Override
		public int compareTo(AddressBalance another) {
			if (address.hasPrivateKey() && !another.address.hasPrivateKey()) {
				return 1;
			}
			if (!address.hasPrivateKey() && another.address.hasPrivateKey()) {
				return -1;
			}
			return balance.compareTo(another.balance);
		}
	}

	private int getFlHeight() {
		int listHeight = addresses.size() * ListItemHeight
				+ (addresses.size() - 1) * lv.getDividerHeight();
		return Math.min(MaxListHeight, Math.max(listHeight, MinListHeight));
	}
}
