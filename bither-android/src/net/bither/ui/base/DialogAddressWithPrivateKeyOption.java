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

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;

import net.bither.R;
import net.bither.model.BitherAddress;

public class DialogAddressWithPrivateKeyOption extends CenterDialog implements View
        .OnClickListener, DialogInterface.OnDismissListener, DialogPassword.DialogPasswordListener {
    private DialogFancyQrCode dialogQr;
    private DialogPrivateKeyQrCode dialogPrivateKey;
    private BitherAddress address;
    private LinearLayout llOriginQRCode;
    private Activity activity;
    private int clickedView;

    public DialogAddressWithPrivateKeyOption(Activity context,
                                             BitherAddress address) {
        super(context);
        this.activity = context;
        this.address = address;
        setOnDismissListener(this);
        setContentView(R.layout.dialog_address_with_private_key_option);
        llOriginQRCode = (LinearLayout) findViewById(R.id.ll_origin_qr_code);
        findViewById(R.id.tv_view_on_blockchaininfo).setOnClickListener(this);
        findViewById(R.id.tv_private_key_qr_code).setOnClickListener(this);
        llOriginQRCode.setOnClickListener(this);
        findViewById(R.id.tv_close).setOnClickListener(this);
        dialogQr = new DialogFancyQrCode(context, address.getAddress(), false, true);
        dialogPrivateKey = new DialogPrivateKeyQrCode(context, address.getKeys().get(0));
    }

    @Override
    public void show() {
        clickedView = 0;
        super.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        switch (clickedView) {
            case R.id.ll_origin_qr_code:
                dialogQr.show();
                break;
            case R.id.tv_view_on_blockchaininfo:
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://blockchain.info/address/"
                                + address.getAddress())
                )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    getContext().startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    DropdownMessage.showDropdownMessage(activity,
                            R.string.find_browser_error);
                }
                break;
            case R.id.tv_private_key_qr_code:
                new DialogPassword(activity, this).show();
                break;
            default:
                return;
        }
    }

    @Override
    public void onClick(View v) {
        clickedView = v.getId();
        dismiss();
    }


    @Override
    public void onPasswordEntered(String password) {
        dialogPrivateKey.show();
    }
}
