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

package net.bither.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.bither.BitherApplication;
import net.bither.bitherj.BitherjSettings;
import net.bither.bitherj.core.AddressManager;
import net.bither.bitherj.core.In;
import net.bither.bitherj.core.Out;
import net.bither.bitherj.core.Tx;
import net.bither.bitherj.db.AbstractDb;
import net.bither.bitherj.db.ITxProvider;
import net.bither.bitherj.exception.AddressFormatException;
import net.bither.bitherj.utils.Base58;
import net.bither.bitherj.utils.Sha256Hash;
import net.bither.bitherj.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class TxProvider implements ITxProvider {

    private static TxProvider txProvider = new TxProvider(BitherApplication.mTxDbHelper);

    public static TxProvider getInstance() {
        return txProvider;
    }

    private SQLiteOpenHelper mDb;

    public TxProvider(SQLiteOpenHelper db) {
        this.mDb = db;
    }

    public List<Tx> getTxAndDetailByAddress(String address) {
        List<Tx> txItemList = new ArrayList<Tx>();
        HashMap<Sha256Hash, Tx> txDict = new HashMap<Sha256Hash, Tx>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        try {
            String sql = "select b.* from addresses_txs a, txs b" +
                    " where a.tx_hash=b.tx_hash and a.address=? order by ifnull(b.block_no,4294967295) desc";
            Cursor c = db.rawQuery(sql, new String[]{address});
            while (c.moveToNext()) {
                Tx txItem = TxHelper.applyCursor(c);
                txItem.setIns(new ArrayList<In>());
                txItem.setOuts(new ArrayList<Out>());
                txItemList.add(txItem);
                txDict.put(new Sha256Hash(txItem.getTxHash()), txItem);
            }
            c.close();
            addInForTxDetail(db, address, txDict);
            addOutForTxDetail(db, address, txDict);

        } catch (AddressFormatException e) {
            e.printStackTrace();
        }
        return txItemList;
    }

    private void addInForTxDetail(SQLiteDatabase db, String address, HashMap<Sha256Hash, Tx> txDict) throws AddressFormatException {
        String sql = "select b.* from addresses_txs a, ins b where a.tx_hash=b.tx_hash and a.address=? "
                + "order by b.tx_hash ,b.in_sn";
        Cursor c = db.rawQuery(sql, new String[]{address});
        while (c.moveToNext()) {
            In inItem = TxHelper.applyCursorIn(c);
            Tx tx = txDict.get(new Sha256Hash(inItem.getTxHash()));
            if (tx != null) {
                tx.getIns().add(inItem);
            }
        }
        c.close();
    }

    private void addOutForTxDetail(SQLiteDatabase db, String address, HashMap<Sha256Hash, Tx> txDict) throws AddressFormatException {
        String sql = "select b.* from addresses_txs a, outs b where a.tx_hash=b.tx_hash and a.address=? "
                + "order by b.tx_hash,b.out_sn";
        Cursor c = db.rawQuery(sql, new String[]{address});
        while (c.moveToNext()) {
            Out out = TxHelper.applyCursorOut(c);
            Tx tx = txDict.get(new Sha256Hash(out.getTxHash()));
            if (tx != null) {
                tx.getOuts().add(out);
            }
        }
        c.close();
    }

    @Override
    public List<Tx> getTxAndDetailByAddress(String address, int page) {
        List<Tx> txItemList = new ArrayList<Tx>();

        HashMap<Sha256Hash, Tx> txDict = new HashMap<Sha256Hash, Tx>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        try {
            String sql = "select b.* from addresses_txs a, txs b" +
                    " where a.tx_hash=b.tx_hash and a.address=? order by ifnull(b.block_no,4294967295) desc limit ?,? ";
            Cursor c = db.rawQuery(sql, new String[]{
                    address, Integer.toString((page - 1) * BitherjSettings.TX_PAGE_SIZE), Integer.toString(BitherjSettings.TX_PAGE_SIZE)
            });
            StringBuilder txsStrBuilder = new StringBuilder();
            while (c.moveToNext()) {
                Tx txItem = TxHelper.applyCursor(c);
                txItem.setIns(new ArrayList<In>());
                txItem.setOuts(new ArrayList<Out>());
                txItemList.add(txItem);
                txDict.put(new Sha256Hash(txItem.getTxHash()), txItem);
                txsStrBuilder.append("'").append(Base58.encode(txItem.getTxHash())).append("'").append(",");
            }
            c.close();

            if (txsStrBuilder.length() > 1) {
                String txs = txsStrBuilder.substring(0, txsStrBuilder.length() - 1);
                sql = Utils.format("select b.* from ins b where b.tx_hash in (%s)" +
                        " order by b.tx_hash ,b.in_sn", txs);
                c = db.rawQuery(sql, null);
                while (c.moveToNext()) {
                    In inItem = TxHelper.applyCursorIn(c);
                    Tx tx = txDict.get(new Sha256Hash(inItem.getTxHash()));
                    if (tx != null) {
                        tx.getIns().add(inItem);
                    }
                }
                c.close();
                sql = Utils.format("select b.* from outs b where b.tx_hash in (%s)" +
                        " order by b.tx_hash,b.out_sn", txs);
                c = db.rawQuery(sql, null);
                while (c.moveToNext()) {
                    Out out = TxHelper.applyCursorOut(c);
                    Tx tx = txDict.get(new Sha256Hash(out.getTxHash()));
                    if (tx != null) {
                        tx.getOuts().add(out);
                    }
                }
                c.close();
            }
        } catch (AddressFormatException e) {
            e.printStackTrace();
        }
        return txItemList;
    }

    public List<Tx> getPublishedTxs() {
        List<Tx> txItemList = new ArrayList<Tx>();
        HashMap<Sha256Hash, Tx> txDict = new HashMap<Sha256Hash, Tx>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select * from txs where block_no is null";
        try {
            Cursor c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                Tx txItem = TxHelper.applyCursor(c);
                txItem.setIns(new ArrayList<In>());
                txItem.setOuts(new ArrayList<Out>());
                txItemList.add(txItem);
                txDict.put(new Sha256Hash(txItem.getTxHash()), txItem);
            }
            c.close();

            sql = "select b.* from txs a, ins b  where a.tx_hash=b.tx_hash  and a.block_no is null "
                    + "order by b.tx_hash ,b.in_sn";
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                In inItem = TxHelper.applyCursorIn(c);
                Tx tx = txDict.get(new Sha256Hash(inItem.getTxHash()));
                tx.getIns().add(inItem);
            }
            c.close();

            sql = "select b.* from txs a, outs b where a.tx_hash=b.tx_hash and a.block_no is null "
                    + "order by b.tx_hash,b.out_sn";
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                Out out = TxHelper.applyCursorOut(c);
                Tx tx = txDict.get(new Sha256Hash(out.getTxHash()));
                tx.getOuts().add(out);
            }
            c.close();

        } catch (AddressFormatException e) {
            e.printStackTrace();
        } finally {

        }
        return txItemList;
    }

    public Tx getTxDetailByTxHash(byte[] txHash) {
        Tx txItem = null;
        String txHashStr = Base58.encode(txHash);
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select * from txs where tx_hash=?";
        Cursor c = db.rawQuery(sql, new String[]{txHashStr});
        try {
            if (c.moveToNext()) {
                txItem = TxHelper.applyCursor(c);
            }
            c.close();
            if (txItem != null) {
                TxHelper.addInsAndOuts(db, txItem);
            }
        } catch (AddressFormatException e) {
            e.printStackTrace();
        } finally {

        }
        return txItem;
    }

    @Override
    public long sentFromAddress(byte[] txHash, String address) {
        String sql = "select  sum(o.out_value) out_value from ins i,outs o where" +
                " i.tx_hash=? and o.tx_hash=i.prev_tx_hash and i.prev_out_sn=o.out_sn and o.out_address=?";
        long sum = 0;
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        Cursor cursor;

        cursor = db.rawQuery(sql, new String[]{Base58.encode(txHash),
                address});
        if (cursor.moveToNext()) {
            int idColumn = cursor.getColumnIndex(AbstractDb.OutsColumns.OUT_VALUE);
            if (idColumn != -1) {
                sum = cursor.getLong(idColumn);
            }
        }
        cursor.close();

        return sum;
    }


    public boolean isExist(byte[] txHash) {
        boolean result = false;
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select count(0) from txs where tx_hash=?";
        Cursor c = db.rawQuery(sql, new String[]{Base58.encode(txHash)});
        if (c.moveToNext()) {
            result = c.getInt(0) > 0;
        }
        c.close();
        return result;
    }

    public void add(Tx txItem) {
        SQLiteDatabase db = this.mDb.getWritableDatabase();
        db.beginTransaction();
        addTxToDb(db, txItem);
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void addTxs(List<Tx> txItems) {
        if (txItems.size() > 0) {
            SQLiteDatabase db = this.mDb.getWritableDatabase();
            db.beginTransaction();
            for (Tx txItem : txItems) {
                addTxToDb(db, txItem);
            }
            db.setTransactionSuccessful();
            db.endTransaction();
        }
    }

    private static void addTxToDb(SQLiteDatabase db, Tx txItem) {
//        HashSet<String> addressSet = AbstractDb.hdAccountAddressProvider.
//                getBelongAccountAddresses(txItem.getOutAddressList());
////        HashSet<String> coldHDAccountAddressSet = AbstractDb.hdAccountAddressProvider.
////                getBelongAccountAddresses(txItem.getOutAddressList());
//        for (Out out : txItem.getOuts()) {
//            if (addressSet.contains(out.getOutAddress())) {
//                out.setHDAccountId(AddressManager.getInstance().getHDAccountHot().getHdSeedId());
//            }
////            if (coldHDAccountAddressSet.contains(out.getOutAddress())) {
////                out.setColdHDAccountId(AddressManager.getInstance().getHDAccountMonitored()
////                        .getHdSeedId());
////            }
//        }

        TxHelper.insertTx(db, txItem);
        List<TxHelper.AddressTx> addressesTxsRels = new ArrayList<TxHelper.AddressTx>();
        List<TxHelper.AddressTx> temp = TxHelper.insertIn(db, txItem);
        if (temp != null && temp.size() > 0) {
            addressesTxsRels.addAll(temp);
        }
        temp = TxHelper.insertOut(db, txItem);
        if (temp != null && temp.size() > 0) {
            addressesTxsRels.addAll(temp);
        }
        for (TxHelper.AddressTx addressTx : addressesTxsRels) {
            String sql = "insert or ignore into addresses_txs(address, tx_hash) values(?,?)";
            db.execSQL(sql, new String[]{addressTx.getAddress(), addressTx.getTxHash()});
        }

    }

    public void remove(byte[] txHash) {
        String txHashStr = Base58.encode(txHash);
        List<String> txHashes = new ArrayList<String>();
        List<String> needRemoveTxHashes = new ArrayList<String>();
        txHashes.add(txHashStr);
        while (txHashes.size() > 0) {
            String thisHash = txHashes.get(0);
            txHashes.remove(0);
            needRemoveTxHashes.add(thisHash);
            List<String> temp = getRelayTx(thisHash);
            txHashes.addAll(temp);
        }
        SQLiteDatabase db = this.mDb.getWritableDatabase();
        db.beginTransaction();
        for (String str : needRemoveTxHashes) {
            removeSingleTx(db, str);
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    private void removeSingleTx(SQLiteDatabase db, String tx) {
        String deleteTx = "delete from txs where tx_hash='" + tx + "'";
        String deleteIn = "delete from ins where tx_hash='" + tx + "'";
        String deleteOut = "delete from outs where tx_hash='" + tx + "'";
        String deleteAddressesTx = "delete from addresses_txs where tx_hash='" + tx + "'";
        String inSql = "select prev_tx_hash,prev_out_sn from ins where tx_hash='" + tx + "'";
        String existOtherIn = "select count(0) cnt from ins where prev_tx_hash=? and prev_out_sn=?";
        String updatePrevOut = "update outs set out_status=%d where tx_hash=%s and out_sn=%d";
        Cursor c = db.rawQuery(inSql, new String[]{tx});
        List<Object[]> needUpdateOuts = new ArrayList<Object[]>();
        while (c.moveToNext()) {
            int idColumn = c.getColumnIndex(AbstractDb.InsColumns.PREV_TX_HASH);
            String prevTxHash = null;
            int prevOutSn = 0;
            if (idColumn != -1) {
                prevTxHash = c.getString(idColumn);
            }
            idColumn = c.getColumnIndex(AbstractDb.InsColumns.PREV_OUT_SN);
            if (idColumn != -1) {
                prevOutSn = c.getInt(idColumn);
            }
            needUpdateOuts.add(new Object[]{prevTxHash, prevOutSn});

        }
        c.close();
        db.execSQL(deleteAddressesTx);
        db.execSQL(deleteOut);
        db.execSQL(deleteIn);
        db.execSQL(deleteTx);
        for (Object[] array : needUpdateOuts) {
            c = db.rawQuery(existOtherIn, new String[]{array[0].toString(), array[1].toString()});
            while (c.moveToNext()) {
                if (c.getInt(0) == 0) {
                    String updateSql = Utils.format(updatePrevOut,
                            Out.OutStatus.unspent.getValue(), array[0].toString(), Integer.valueOf(array[1].toString()));
                    db.execSQL(updateSql);
                }

            }
            c.close();

        }
    }

    private List<String> getRelayTx(String txHash) {
        List<String> relayTxHashes = new ArrayList<String>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String relayTx = "select distinct tx_hash from ins where prev_tx_hash=?";
        Cursor c = db.rawQuery(relayTx, new String[]{txHash});
        while (c.moveToNext()) {
            relayTxHashes.add(c.getString(0));
        }
        c.close();
        return relayTxHashes;
    }

    public boolean isAddressContainsTx(String address, Tx txItem) {
        boolean result = false;
        String sql = "select count(0) from ins a, txs b where a.tx_hash=b.tx_hash and" +
                " b.block_no is not null and a.prev_tx_hash=? and a.prev_out_sn=?";
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        Cursor c;
        for (In inItem : txItem.getIns()) {
            c = db.rawQuery(sql, new String[]{Base58.encode(inItem.getPrevTxHash()), Integer.toString(inItem.getPrevOutSn())});
            if (c.moveToNext()) {
                if (c.getInt(0) > 0) {
                    c.close();
                    return false;
                }
            }
            c.close();
        }
        sql = "select count(0) from addresses_txs where tx_hash=? and address=?";
        c = db.rawQuery(sql, new String[]{
                Base58.encode(txItem.getTxHash()), address
        });
        int count = 0;
        if (c.moveToNext()) {
            count = c.getInt(0);
        }
        c.close();
        if (count > 0) {
            return true;
        }
        sql = "select count(0) from outs where tx_hash=? and out_sn=? and out_address=?";
        for (In inItem : txItem.getIns()) {
            c = db.rawQuery(sql, new String[]{Base58.encode(inItem.getPrevTxHash())
                    , Integer.toString(inItem.getPrevOutSn()), address});
            count = 0;
            if (c.moveToNext()) {
                count = c.getInt(0);
            }
            c.close();
            if (count > 0) {
                return true;
            }
        }
        return result;
    }

    public boolean isTxDoubleSpendWithConfirmedTx(Tx tx) {
        String sql = "select count(0) from ins a, txs b where a.tx_hash=b.tx_hash and" +
                " b.block_no is not null and a.prev_tx_hash=? and a.prev_out_sn=?";
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        Cursor c;
        for (In inItem : tx.getIns()) {
            c = db.rawQuery(sql, new String[]{Base58.encode(inItem.getPrevTxHash()), Integer.toString(inItem.getPrevOutSn())});
            if (c.moveToNext()) {
                if (c.getInt(0) > 0) {
                    c.close();
                    return true;
                }
            }
            c.close();

        }
        return false;
    }

    public List<String> getInAddresses(Tx tx) {
        List<String> result = new ArrayList<String>();
        String sql = "select out_address from outs where tx_hash=? and out_sn=?";
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        Cursor c;
        for (In inItem : tx.getIns()) {
            c = db.rawQuery(sql, new String[]{Base58.encode(inItem.getPrevTxHash())
                    , Integer.toString(inItem.getPrevOutSn())});
            if (c.moveToNext()) {
                if (!c.isNull(0)) {
                    result.add(c.getString(0));
                }
            }
            c.close();
        }
        return result;
    }

    public void confirmTx(int blockNo, List<byte[]> txHashes) {
        if (blockNo == Tx.TX_UNCONFIRMED || txHashes == null) {
            return;
        }
        String sql = "update txs set block_no=%d where tx_hash='%s'";
        String existSql = "select count(0) from txs where block_no=? and tx_hash=?";
        String doubleSpendSql = "select a.tx_hash from ins a, ins b where a.prev_tx_hash=b.prev_tx_hash " +
                "and a.prev_out_sn=b.prev_out_sn and a.tx_hash<>b.tx_hash and b.tx_hash=?";
        String blockTimeSql = "select block_time from blocks where block_no=?";
        String updateTxTimeThatMoreThanBlockTime = "update txs set tx_time=%d where block_no=%d and tx_time>%d";
        SQLiteDatabase db = this.mDb.getWritableDatabase();
        db.beginTransaction();
        Cursor c;
        for (byte[] txHash : txHashes) {
            c = db.rawQuery(existSql, new String[]{Integer.toString(blockNo), Base58.encode(txHash)});
            if (c.moveToNext()) {
                int cnt = c.getInt(0);
                c.close();
                if (cnt > 0) {
                    continue;
                }
            } else {
                c.close();
            }
            String updateSql = Utils.format(sql, blockNo, Base58.encode(txHash));
            db.execSQL(updateSql);
            c = db.rawQuery(doubleSpendSql, new String[]{Base58.encode(txHash)});
            List<String> txHashes1 = new ArrayList<String>();
            while (c.moveToNext()) {
                int idColumn = c.getColumnIndex("tx_hash");
                if (idColumn != -1) {
                    txHashes1.add(c.getString(idColumn));
                }
            }
            c.close();
            List<String> needRemoveTxHashes = new ArrayList<String>();
            while (txHashes1.size() > 0) {
                String thisHash = txHashes1.get(0);
                txHashes1.remove(0);
                needRemoveTxHashes.add(thisHash);
                List<String> temp = getRelayTx(thisHash);
                txHashes1.addAll(temp);
            }
            for (String each : needRemoveTxHashes) {
                removeSingleTx(db, each);
            }

        }
        c = db.rawQuery(blockTimeSql, new String[]{Integer.toString(blockNo)});
        if (c.moveToNext()) {
            int idColumn = c.getColumnIndex("block_time");
            if (idColumn != -1) {
                int blockTime = c.getInt(idColumn);
                c.close();
                String sqlTemp = Utils.format(updateTxTimeThatMoreThanBlockTime, blockTime, blockNo, blockTime);
                db.execSQL(sqlTemp);
            }
        } else {
            c.close();
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void unConfirmTxByBlockNo(int blockNo) {
        SQLiteDatabase db = this.mDb.getWritableDatabase();
        String sql = "update txs set block_no=null where block_no>=" + blockNo;
        db.execSQL(sql);
    }

    public List<Tx> getUnspendTxWithAddress(String address) {
        String unspendOutSql = "select a.*,b.tx_ver,b.tx_locktime,b.tx_time,b.block_no,b.source,ifnull(b.block_no,0)*a.out_value coin_depth " +
                "from outs a,txs b where a.tx_hash=b.tx_hash" +
                " and a.out_address=? and a.out_status=?";
        List<Tx> txItemList = new ArrayList<Tx>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        Cursor c = db.rawQuery(unspendOutSql, new String[]{address, Integer.toString(Out.OutStatus.unspent.getValue())});
        try {
            while (c.moveToNext()) {
                int idColumn = c.getColumnIndex("coin_depth");

                Tx txItem = TxHelper.applyCursor(c);
                Out outItem = TxHelper.applyCursorOut(c);
                if (idColumn != -1) {
                    outItem.setCoinDepth(c.getLong(idColumn));
                }
                outItem.setTx(txItem);
                txItem.setOuts(new ArrayList<Out>());
                txItem.getOuts().add(outItem);
                txItemList.add(txItem);

            }
            c.close();
        } catch (AddressFormatException e) {
            e.printStackTrace();
        }
        return txItemList;
    }

    public List<Out> getUnspendOutWithAddress(String address) {
        List<Out> outItems = new ArrayList<Out>();
        String unspendOutSql = "select a.* from outs a,txs b where a.tx_hash=b.tx_hash " +
                " and a.out_address=? and a.out_status=?";
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        Cursor c = db.rawQuery(unspendOutSql,
                new String[]{address, Integer.toString(Out.OutStatus.unspent.getValue())});
        try {
            while (c.moveToNext()) {
                outItems.add(TxHelper.applyCursorOut(c));
            }
            c.close();
        } catch (AddressFormatException e) {
            e.printStackTrace();
        }
        return outItems;
    }

    public long getConfirmedBalanceWithAddress(String address) {
        long sum = 0;
        String unspendOutSql = "select ifnull(sum(a.out_value),0) sum from outs a,txs b where a.tx_hash=b.tx_hash " +
                " and a.out_address=? and a.out_status=? and b.block_no is not null";
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        Cursor c = db.rawQuery(unspendOutSql,
                new String[]{address, Integer.toString(Out.OutStatus.unspent.getValue())});

        if (c.moveToNext()) {
            int idColumn = c.getColumnIndex("sum");
            if (idColumn != -1) {
                sum = c.getLong(idColumn);
            }
        }
        c.close();
        return sum;
    }

    public List<Tx> getUnconfirmedTxWithAddress(String address) {
        List<Tx> txList = new ArrayList<Tx>();

        HashMap<Sha256Hash, Tx> txDict = new HashMap<Sha256Hash, Tx>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        try {
            String sql = "select b.* from addresses_txs a, txs b " +
                    "where a.tx_hash=b.tx_hash and a.address=? and b.block_no is null " +
                    "order by b.block_no desc";
            Cursor c = db.rawQuery(sql, new String[]{address});
            while (c.moveToNext()) {
                Tx txItem = TxHelper.applyCursor(c);
                txItem.setIns(new ArrayList<In>());
                txItem.setOuts(new ArrayList<Out>());
                txList.add(txItem);
                txDict.put(new Sha256Hash(txItem.getTxHash()), txItem);
            }
            c.close();
            sql = "select b.tx_hash,b.in_sn,b.prev_tx_hash,b.prev_out_sn " +
                    "from addresses_txs a, ins b, txs c " +
                    "where a.tx_hash=b.tx_hash and b.tx_hash=c.tx_hash and c.block_no is null and a.address=? "
                    + "order by b.tx_hash ,b.in_sn";
            c = db.rawQuery(sql, new String[]{address});
            while (c.moveToNext()) {
                In inItem = TxHelper.applyCursorIn(c);
                Tx tx = txDict.get(new Sha256Hash(inItem.getTxHash()));
                if (tx != null) {
                    tx.getIns().add(inItem);
                }
            }
            c.close();

            sql = "select b.tx_hash,b.out_sn,b.out_value,b.out_address " +
                    "from addresses_txs a, outs b, txs c " +
                    "where a.tx_hash=b.tx_hash and b.tx_hash=c.tx_hash and c.block_no is null and a.address=? "
                    + "order by b.tx_hash,b.out_sn";
            c = db.rawQuery(sql, new String[]{address});
            while (c.moveToNext()) {
                Out out = TxHelper.applyCursorOut(c);
                Tx tx = txDict.get(new Sha256Hash(out.getTxHash()));
                if (tx != null) {
                    tx.getOuts().add(out);
                }
            }
            c.close();

        } catch (AddressFormatException e) {
            e.printStackTrace();
        }
        return txList;
    }

    public int txCount(String address) {
        int result = 0;
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select count(0) cnt from addresses_txs  where address=?";
        Cursor c = db.rawQuery(sql, new String[]{address});
        if (c.moveToNext()) {
            int idColumn = c.getColumnIndex("cnt");
            if (idColumn != -1) {
                result = c.getInt(idColumn);
            }
        }
        c.close();

        return result;
    }

    public long totalReceive(String address) {
        long result = 0;
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select sum(aa.receive-ifnull(bb.send,0)) sum" +
                "  from (select a.tx_hash,sum(a.out_value) receive " +
                "    from outs a where a.out_address=?" +
                "    group by a.tx_hash) aa LEFT OUTER JOIN " +
                "  (select b.tx_hash,sum(a.out_value) send" +
                "    from outs a, ins b" +
                "    where a.tx_hash=b.prev_tx_hash and a.out_sn=b.prev_out_sn and a.out_address=?" +
                "    group by b.tx_hash) bb on aa.tx_hash=bb.tx_hash " +
                "  where aa.receive>ifnull(bb.send, 0)";
        Cursor c = db.rawQuery(sql, new String[]{address, address});
        if (c.moveToNext()) {
            result = c.getLong(0);
        }
        c.close();
        return result;
    }

    public void txSentBySelfHasSaw(byte[] txHash) {
        SQLiteDatabase db = this.mDb.getWritableDatabase();
        String sql = "update txs set source=source+1 where tx_hash=? and source>=1";
        db.execSQL(sql, new String[]{Base58.encode(txHash)});
    }

    public List<Out> getOuts() {
        List<Out> outItemList = new ArrayList<Out>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select * from outs ";
        Cursor c = db.rawQuery(sql, null);
        try {
            while (c.moveToNext()) {
                outItemList.add(TxHelper.applyCursorOut(c));
            }
        } catch (AddressFormatException e) {
            e.printStackTrace();
        } finally {
            c.close();
        }

        return outItemList;
    }

    public List<In> getRelatedIn(String address) {
        List<In> list = new ArrayList<In>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select ins.* from ins,addresses_txs " +
                "where ins.tx_hash=addresses_txs.tx_hash and addresses_txs.address=? ";
        Cursor c = db.rawQuery(sql, new String[]{address});
        try {
            while (c.moveToNext()) {
                list.add(TxHelper.applyCursorIn(c));
            }
        } catch (AddressFormatException e) {
            e.printStackTrace();
        } finally {
            c.close();
        }
        return list;
    }

    public List<Tx> getRecentlyTxsByAddress(String address, int greateThanBlockNo, int limit) {
        List<Tx> txItemList = new ArrayList<Tx>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select b.* from addresses_txs a, txs b where a.tx_hash=b.tx_hash and a.address='%s' " +
                "and ((b.block_no is null) or (b.block_no is not null and b.block_no>%d)) " +
                "order by ifnull(b.block_no,4294967295) desc, b.tx_time desc " +
                "limit %d ";
        sql = Utils.format(sql, address, greateThanBlockNo, limit);
        Cursor c = db.rawQuery(sql, null);
        try {
            while (c.moveToNext()) {
                Tx txItem = TxHelper.applyCursor(c);
                txItemList.add(txItem);
            }

            for (Tx item : txItemList) {
                TxHelper.addInsAndOuts(db, item);
            }
        } catch (AddressFormatException e) {
            e.printStackTrace();
        } finally {
            c.close();
        }
        return txItemList;
    }

//    public List<Long> txInValues(byte[] txHash) {
//        List<Long> inValues = new ArrayList<Long>();
//        SQLiteDatabase db = this.mDb.getReadableDatabase();
//        String sql = "select b.out_value " +
//                "from ins a left outer join outs b on a.prev_tx_hash=b.tx_hash and a.prev_out_sn=b.out_sn " +
//                "where a.tx_hash=?";
//        Cursor c = db.rawQuery(sql, new String[]{Base58.encode(txHash)});
//        while (c.moveToNext()) {
//            int idColumn = c.getColumnIndex("out_value");
//            if (idColumn != -1) {
//                inValues.add(c.getLong(idColumn));
//            } else {
//                inValues.add(null);
//            }
//        }
//        c.close();
//        return inValues;
//    }

    public HashMap<Sha256Hash, Tx> getTxDependencies(Tx txItem) {
        HashMap<Sha256Hash, Tx> result = new HashMap<Sha256Hash, Tx>();
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        try {


            for (In inItem : txItem.getIns()) {
                Tx tx;
                String txHashStr = Base58.encode(inItem.getTxHash());
                String sql = "select * from txs where tx_hash=?";
                Cursor c = db.rawQuery(sql, new String[]{txHashStr});
                if (c.moveToNext()) {
                    tx = TxHelper.applyCursor(c);
                    c.close();
                } else {
                    c.close();
                    continue;
                }
                TxHelper.addInsAndOuts(db, tx);
                result.put(new Sha256Hash(tx.getTxHash()), tx);

            }
        } catch (AddressFormatException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void clearAllTx() {
        SQLiteDatabase db = mDb.getWritableDatabase();
        db.beginTransaction();
        db.execSQL("drop table " + AbstractDb.Tables.TXS + ";");
        db.execSQL("drop table " + AbstractDb.Tables.OUTS + ";");
        db.execSQL("drop table " + AbstractDb.Tables.INS + ";");
        db.execSQL("drop table " + AbstractDb.Tables.ADDRESSES_TXS + ";");
        db.execSQL("drop table " + AbstractDb.Tables.PEERS + ";");
        db.execSQL(AbstractDb.CREATE_TXS_SQL);
        db.execSQL(AbstractDb.CREATE_TX_BLOCK_NO_INDEX);
        db.execSQL(AbstractDb.CREATE_OUTS_SQL);
        db.execSQL(AbstractDb.CREATE_OUT_OUT_ADDRESS_INDEX);
        db.execSQL(AbstractDb.CREATE_INS_SQL);
        db.execSQL(AbstractDb.CREATE_IN_PREV_TX_HASH_INDEX);
        db.execSQL(AbstractDb.CREATE_ADDRESSTXS_SQL);
        db.execSQL(AbstractDb.CREATE_PEER_SQL);
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void completeInSignature(List<In> ins) {
        SQLiteDatabase db = this.mDb.getWritableDatabase();
        db.beginTransaction();
        String sql = "update ins set in_signature=? where tx_hash=? and in_sn=? and ifnull(in_signature,'')=''";
        for (In in : ins) {
            db.execSQL(sql, new String[]{Base58.encode(in.getInSignature())
                    , Base58.encode(in.getTxHash()), Integer.toString(in.getInSn())});
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public int needCompleteInSignature(String address) {
        int result = 0;
        SQLiteDatabase db = this.mDb.getReadableDatabase();
        String sql = "select max(txs.block_no) from outs,ins,txs where outs.out_address=? " +
                "and ins.prev_tx_hash=outs.tx_hash and ins.prev_out_sn=outs.out_sn " +
                "and ifnull(ins.in_signature,'')='' and txs.tx_hash=ins.tx_hash";
        Cursor c = db.rawQuery(sql, new String[]{address});
        if (c.moveToNext()) {
            result = c.getInt(0);
        }
        c.close();
        return result;
    }


}