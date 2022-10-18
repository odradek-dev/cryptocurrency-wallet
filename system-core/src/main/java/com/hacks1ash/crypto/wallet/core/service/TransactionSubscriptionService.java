package com.hacks1ash.crypto.wallet.core.service;

import com.hacks1ash.crypto.wallet.blockchain.UTXORPCClient;
import com.hacks1ash.crypto.wallet.blockchain.factory.UTXOClientFactory;
import com.hacks1ash.crypto.wallet.blockchain.model.request.GetTransactionRequest;
import com.hacks1ash.crypto.wallet.blockchain.model.response.GetTrasactionResponse;
import com.hacks1ash.crypto.wallet.core.TransactionListener;
import com.hacks1ash.crypto.wallet.core.WalletException;
import com.hacks1ash.crypto.wallet.core.WalletManager;
import com.hacks1ash.crypto.wallet.core.model.CryptoCurrency;
import com.hacks1ash.crypto.wallet.core.model.WebhookStatus;
import com.hacks1ash.crypto.wallet.core.model.WebhookTXStatus;
import com.hacks1ash.crypto.wallet.core.model.request.NewTransaction;
import com.hacks1ash.crypto.wallet.core.model.response.GetTransactionResponse;
import com.hacks1ash.crypto.wallet.core.storage.WalletRepository;
import com.hacks1ash.crypto.wallet.core.storage.WebhookRepository;
import com.hacks1ash.crypto.wallet.core.storage.WebhookSubscriptionRepository;
import com.hacks1ash.crypto.wallet.core.storage.document.Wallet;
import com.hacks1ash.crypto.wallet.core.storage.document.Webhook;
import com.hacks1ash.crypto.wallet.core.storage.document.WebhookSubscription;
import com.odradek.pay.kafka.intergation.KafkaTopicProperties;
import com.odradek.pay.kafka.intergation.message.TransactionMessage;
import com.odradek.pay.kafka.intergation.model.WebhookTransactionDetails;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class TransactionSubscriptionService implements TransactionListener {

  private WalletRepository walletRepository;

  private UTXOClientFactory utxoClientFactory;

  private WebhookSubscriptionRepository webhookSubscriptionRepository;

  private WalletManager walletManager;

  private WebhookRepository webhookRepository;

  private KafkaTemplate transactionKafkaTemplate;

  private KafkaTopicProperties kafkaTopicProperties;


  @Override
  public void onTransaction(NewTransaction newTransaction) {

    log.info(String.format("Received Webhook for wallet %s", newTransaction.getWalletName()));
    log.info(newTransaction.toString());

    CryptoCurrency cryptoCurrency = CryptoCurrency.cryptoCurrencyFromShortName(newTransaction.getCoin());
    UTXORPCClient rpcClient = utxoClientFactory.getClient(cryptoCurrency.getUtxoProvider());
    Wallet wallet = walletRepository.findByNodeWalletNameAlias(newTransaction.getWalletName()).orElseThrow(() -> new WalletException.WalletNotFound(newTransaction.getWalletName()));
    Optional<WebhookSubscription> optionalWebhookSubscription = webhookSubscriptionRepository.findByWalletId(wallet.getId());
    GetTrasactionResponse transaction = rpcClient.getTransaction(new GetTransactionRequest(cryptoCurrency.getUtxoProvider(), wallet.getNodeWalletNameAlias(), newTransaction.getTxId()), wallet.getCurrency().getNetworkParams());

    WebhookTXStatus txStatus;

    if (transaction.getConfirmations() == 0) {
      txStatus = WebhookTXStatus.MEMPOOL;
    } else if (transaction.getConfirmations() < 1) {
      txStatus = WebhookTXStatus.PENDING;
    } else {
      txStatus = WebhookTXStatus.CONFIRMED;
    }

    GetTransactionResponse getTransactionResponse = walletManager.getTransaction(wallet.getId(), newTransaction.getTxId());
    getTransactionResponse.setConfirmations(transaction.getConfirmations());

    Webhook webhook = new Webhook(
        wallet.getId(),
        transaction.getTxid(),
        transaction.isReplaceable(),
        transaction.isReplaceable(),
        transaction.getConfirmations(),
        transaction.getBlockHeight(),
        txStatus,
        null,
        getTransactionResponse
    );

    if (optionalWebhookSubscription.isPresent()) {
      WebhookSubscription webhookSubscription = optionalWebhookSubscription.get();
      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.add("x-wallet-id", wallet.getId());
      httpHeaders.add("x-provider", "OWS");
      httpHeaders.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<GetTransactionResponse> httpEntity = new HttpEntity<>(getTransactionResponse, httpHeaders);
      ResponseEntity<Void> response = restTemplate.postForEntity(webhookSubscription.getEndpoint(), httpEntity, Void.class);
      WebhookStatus webhookStatus = response.getStatusCode().is2xxSuccessful() ? WebhookStatus.DELIEVERED : WebhookStatus.FAILED;
      webhook.setWebhookStatus(webhookStatus);
    }

    String topic;

    if (txStatus == WebhookTXStatus.MEMPOOL || txStatus == WebhookTXStatus.PENDING) {
      topic = kafkaTopicProperties.getPendingTransaction();
    } else {
      topic = kafkaTopicProperties.getConfirmedTransaction();
    }

    transactionKafkaTemplate.send(topic, new TransactionMessage(new WebhookTransactionDetails(wallet.getId(), transaction.getTxid())));
    log.info(webhookRepository.save(webhook).toString());
  }

}
