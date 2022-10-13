package com.hacks1ash.crypto.wallet.core.service;

import com.hacks1ash.crypto.wallet.blockchain.UTXORPCClient;
import com.hacks1ash.crypto.wallet.blockchain.factory.UTXOClientFactory;
import com.hacks1ash.crypto.wallet.core.BlockListener;
import com.hacks1ash.crypto.wallet.core.model.CryptoCurrency;
import com.hacks1ash.crypto.wallet.core.model.request.NewBlock;
import com.hacks1ash.crypto.wallet.core.storage.WalletRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Slf4j
@Service
@AllArgsConstructor
public class BlockSubscriptionService implements BlockListener {

  private WalletRepository walletRepository;

  private UTXOClientFactory utxoClientFactory;

  @PostConstruct
  void onStartup() {
  }

  @Override
  public void onBlock(NewBlock newBlock) {
    CryptoCurrency cryptoCurrency = CryptoCurrency.cryptoCurrencyFromShortName(newBlock.getCoin());
    UTXORPCClient rpcClient = utxoClientFactory.getClient(cryptoCurrency.getUtxoProvider());
    log.info(newBlock.toString());
  }

}
