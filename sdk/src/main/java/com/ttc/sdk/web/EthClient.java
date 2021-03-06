package com.ttc.sdk.web;

import com.ttc.sdk.model.TransactionResult;
import com.ttc.sdk.util.Constants;
import com.ttc.sdk.util.TTCLogger;
import com.ttc.sdk.util.TTCSp;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 发送交易，查询交易是否成功，内存中nonce的更新
 */
public class EthClient {

    public static BigDecimal getBalance(String address) {
        BigDecimal res = new BigDecimal("0");

        EthGetBalance send = null;
        try {
            Web3j web3 = Web3jFactory.build(new HttpService(Constants.TOKEN_RPC_URL));
            send = web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            BigInteger balance = send.getBalance();  //Wei
            res = new BigDecimal(balance.divide(new BigInteger(Constants.ONE_QUINTILLION)).toString()); //ttc
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;

    }


    public static BigInteger getNonce(String rpcUrl, String from) throws IOException {
        Web3j web3 = Web3jFactory.build(new HttpService(rpcUrl));
        try {
            EthGetTransactionCount ethGetTransactionCount = web3.ethGetTransactionCount(from,
                    DefaultBlockParameterName.LATEST).send();
            if (ethGetTransactionCount != null) {
                return ethGetTransactionCount.getTransactionCount();
            }
        } catch (ClientConnectionException e) {
            e.printStackTrace();
        }
        return new BigInteger("0");
    }

    /**
     * 交易
     *
     * @param rpcUrl
     * @param from
     * @param to
     * @param fromPrivateKey
     * @return
     * @throws IOException
     */
    public static TransactionResult sendTransaction(String rpcUrl, String from, String to, String fromPrivateKey,
                                                    String gasPrice, int gasLimit, String data) {

        data = stringToHex(data);
        Web3j web3 = Web3jFactory.build(new HttpService(rpcUrl));
        Credentials credentials = Credentials.create(fromPrivateKey);
        BigInteger nonce = null;
        BigInteger nextNonce = TTCSp.getNextNonce();
        try {
            nonce = getNonce(rpcUrl, from);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        if (nonce.compareTo(nextNonce) <= 0) {
            nonce = nextNonce;  //nonce从0开始
        }

        TTCLogger.d("send transaction, nonce=" + nonce);
        // create our transaction
        RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, new BigInteger(gasPrice), new
                BigInteger(gasLimit + ""), to, data);

        // sign & send our transaction
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);
        try {
            EthSendTransaction ethSendTransaction = web3.ethSendRawTransaction(hexValue).send();
            if (ethSendTransaction != null) {
                String transactionHash = ethSendTransaction.getTransactionHash();
                if (transactionHash == null) {
                    Response.Error error = ethSendTransaction.getError();
                    if (error != null) {
                        TTCLogger.e(error.getMessage());
                    }
                    return null;
                }
                TTCSp.setNextNonce(nonce.add(new BigInteger("1")));
                return new TransactionResult(transactionHash, nonce);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String stringToHex(String str) {
        StringBuilder sb = new StringBuilder();
        int length = str.length();
        for (int i = 0; i < length; i++) {
            char ch = str.charAt(i);
            sb.append(Integer.toHexString(ch));
        }
        return sb.toString();
    }

    public static boolean isTransactionSuccess(String rpcUrl, String hash) {
        Web3j web3 = Web3jFactory.build(new HttpService(rpcUrl));
        Request<?, EthGetTransactionReceipt> request = web3.ethGetTransactionReceipt(hash);
        if (request != null) {
            try {
                EthGetTransactionReceipt ethGetTransactionReceipt = request.send();
                if (ethGetTransactionReceipt != null) {
                    TransactionReceipt transactionReceipt = ethGetTransactionReceipt.getTransactionReceipt();
                    if (transactionReceipt != null) {
                        if ("0x1".equals(transactionReceipt.getStatus())) {
                            return true;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
