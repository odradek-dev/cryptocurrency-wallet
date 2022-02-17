package com.hacks1ash.crypto.wallet.core.impl;

import co.elastic.apm.api.CaptureSpan;
import com.hacks1ash.crypto.wallet.blockchain.GenericRpcException;
import com.hacks1ash.crypto.wallet.blockchain.UTXOAddressManager;
import com.hacks1ash.crypto.wallet.blockchain.UTXORPCClient;
import com.hacks1ash.crypto.wallet.blockchain.bitcoin.model.request.*;
import com.hacks1ash.crypto.wallet.blockchain.bitcoin.model.response.FundRawTransactionResponse;
import com.hacks1ash.crypto.wallet.blockchain.bitcoin.model.response.GetTrasactionResponse;
import com.hacks1ash.crypto.wallet.blockchain.bitcoin.model.response.ListTransactionResponse;
import com.hacks1ash.crypto.wallet.blockchain.bitcoin.model.response.SignRawTransactionWithWalletResponse;
import com.hacks1ash.crypto.wallet.blockchain.factory.UTXOClientFactory;
import com.hacks1ash.crypto.wallet.blockchain.model.AddressType;
import com.hacks1ash.crypto.wallet.blockchain.model.AddressWithPrivate;
import com.hacks1ash.crypto.wallet.core.WalletException;
import com.hacks1ash.crypto.wallet.core.WalletManager;
import com.hacks1ash.crypto.wallet.core.model.Address;
import com.hacks1ash.crypto.wallet.core.model.CryptoCurrency;
import com.hacks1ash.crypto.wallet.core.model.request.AddressCreationRequest;
import com.hacks1ash.crypto.wallet.core.model.request.TransactionRequest;
import com.hacks1ash.crypto.wallet.core.model.request.WalletCreationRequest;
import com.hacks1ash.crypto.wallet.core.model.response.*;
import com.hacks1ash.crypto.wallet.core.storage.WalletRepository;
import com.hacks1ash.crypto.wallet.core.storage.document.Wallet;
import com.hacks1ash.crypto.wallet.core.utils.CurrencyUtils;
import com.hacks1ash.crypto.wallet.core.utils.MnemonicWords;
import com.hacks1ash.crypto.wallet.core.utils.WalletUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.wallet.DeterministicSeed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WalletManagerBean implements WalletManager {

  private WalletRepository walletRepository;

  private MnemonicWords mnemonicWords;

  private UTXOClientFactory utxoClientFactory;

  @Override
  @CaptureSpan
  public WalletResponse createWallet(WalletCreationRequest request) {
    synchronized (request.getName()) {
      CryptoCurrency cryptoCurrency = CryptoCurrency.cryptoCurrencyFromShortName(request.getCurrency());
      UTXORPCClient rpcClient = utxoClientFactory.getClient(cryptoCurrency.getUtxoProvider());
      UTXOAddressManager addressManager = utxoClientFactory.getAddressManager(cryptoCurrency.getUtxoProvider());

      List<String> hdSeed;
      if (request.getHdSeed() == null) {
        hdSeed = mnemonicWords.getRandomWords(12);
      } else {
        hdSeed = Arrays.stream(request.getHdSeed().split(" ")).collect(Collectors.toList());
        if (hdSeed.size() != 12) {
          throw new WalletException.InvalidHDSeed(hdSeed.size());
        }
      }

      long creationTimestamp = Instant.now().getEpochSecond();
      String nodeWalletName = UUID.randomUUID().toString();
      try {
        rpcClient.createWallet(new CreateWalletRequest(cryptoCurrency.getUtxoProvider(), nodeWalletName, true));
      } catch (GenericRpcException ex) {
        throw new WalletException(ex.getErrorKey(), ex.getErrorMessage(), ex.getErrorCode());
      }



      AddressWithPrivate address = addressManager.createAddress(hdSeed, AddressType.BECH_32, cryptoCurrency.getNetworkParams(), 0, creationTimestamp);

      rpcClient.importMulti(
        cryptoCurrency.getUtxoProvider(),
        nodeWalletName,
        Collections.singletonList(
          new ImportMultiRequest(
            new HashMap<String, String>() {{
              put("address", address.getAddress());
            }},
            null,
            null,
            Collections.singletonList(address.getPrivateKey()),
            true,
            null
          )
        ),
        false
      );

      Wallet wallet = walletRepository.save(
        new Wallet(
          request.getName(),
          nodeWalletName,
          cryptoCurrency,
          hdSeed,
          creationTimestamp,
          new ArrayList<>(),
          address.getAddress()
        )
      );

      return new WalletResponse(wallet.getId(), wallet.getName(), wallet.getCurrency(), String.join(" ", wallet.getHdSeed()));
    }
  }

  @Override
  @CaptureSpan
  public List<WalletResponse> listWallets() {
    List<Wallet> wallets = walletRepository.findAll();
    return wallets
      .stream()
      .map(w -> new WalletResponse(w.getId(), w.getName(), w.getCurrency(), String.join(" ", w.getHdSeed())))
      .collect(Collectors.toList());
  }

  @Override
  @CaptureSpan
  public BigInteger getBalance(String walletId) {
    Optional<Wallet> optionalWallet = walletRepository.findById(walletId);
    if (optionalWallet.isPresent()) {
      Wallet wallet = optionalWallet.get();
      UTXORPCClient rpcClient = utxoClientFactory.getClient(wallet.getCurrency().getUtxoProvider());
      synchronized (wallet) {
        BigDecimal resp = rpcClient.getBalance(new GetBalanceRequest(wallet.getCurrency().getUtxoProvider(), wallet.getNodeWalletNameAlias()));
        return CurrencyUtils.toMinorUnit(wallet.getCurrency(), resp);
      }
    }
    throw new WalletException.WalletNotFound(walletId);
  }

  @Override
  @CaptureSpan
  public AddressResponse createAddress(String walletId, AddressCreationRequest request) {
    Optional<Wallet> optionalWallet = walletRepository.findById(walletId);
    if (optionalWallet.isPresent()) {
      Wallet wallet = optionalWallet.get();
      synchronized (wallet) {
        UTXORPCClient rpcClient = utxoClientFactory.getClient(wallet.getCurrency().getUtxoProvider());
        UTXOAddressManager addressManager = utxoClientFactory.getAddressManager(wallet.getCurrency().getUtxoProvider());
        // Addition of 1 is needed because of change address, that is generated on 0 index at wallet creation
        int addressIndex = wallet.getAddresses().size() + 1;
        long creationTimestamp = Instant.now().getEpochSecond();
        AddressWithPrivate address = addressManager.createAddress(wallet.getHdSeed(), AddressType.valueOf(request.getAddressType().name()), wallet.getCurrency().getNetworkParams(), addressIndex, creationTimestamp);

        rpcClient.importMulti(
          wallet.getCurrency().getUtxoProvider(),
          wallet.getNodeWalletNameAlias(),
          Collections.singletonList(
            new ImportMultiRequest(
              new HashMap<String, String>() {{
                put("address", address.getAddress());
              }},
              null,
              null,
              Collections.singletonList(address.getPrivateKey()),
              false,
              request.getName()
            )
          ),
          false
        );

        rpcClient.importPrivateKey(
          new ImportPrivateKeyRequest(
            wallet.getCurrency().getUtxoProvider(),
            walletId,
            address.getPrivateKey(),
            request.getName(),
            false
          )
        );

        wallet.getAddresses().add(new Address(request.getName(), address.getAddress(), request.getAddressType(), addressIndex));
        walletRepository.save(wallet);
        return new AddressResponse(wallet.getId(), request.getName(), address.getAddress(), request.getAddressType());
      }
    }
    throw new WalletException.WalletNotFound(walletId);
  }

  @Override
  @CaptureSpan
  public List<AddressResponse> getAddresses(String walletId) {
    Optional<Wallet> optionalWallet = walletRepository.findById(walletId);
    if (optionalWallet.isPresent()) {
      Wallet wallet = optionalWallet.get();
      return wallet.getAddresses()
        .stream()
        .map(a -> new AddressResponse(walletId, a.getName(), a.getAddress(), a.getType()))
        .collect(Collectors.toList());
    }
    throw new WalletException.WalletNotFound(walletId);
  }

  @Override
  @CaptureSpan
  public EstimateFeeResponse estimateFee(String walletId, TransactionRequest request) {
    Optional<Wallet> optionalWallet = walletRepository.findById(walletId);
    if (optionalWallet.isPresent()) {
      Wallet wallet = optionalWallet.get();
      synchronized (wallet) {
        CryptoCurrency currency = wallet.getCurrency();
        UTXORPCClient rpcClient = utxoClientFactory.getClient(wallet.getCurrency().getUtxoProvider());
        try {
          FundRawTransactionResponse fundRawTransactionResponse = WalletUtils.fundRawTransaction(request, wallet, currency, rpcClient);
          return new EstimateFeeResponse(CurrencyUtils.toMinorUnit(currency, fundRawTransactionResponse.getFee()), request.getFeePerByte(), currency.getFeeUnit());
        } catch (GenericRpcException ex) {
          throw new WalletException(ex.getErrorKey(), ex.getErrorMessage(), ex.getErrorCode());
        }
      }
    }
    throw new WalletException.WalletNotFound(walletId);
  }

  @Override
  @CaptureSpan
  public SendTransactionResponse sendTransaction(String walletId, TransactionRequest request) {
    Optional<Wallet> optionalWallet = walletRepository.findById(walletId);
    if (optionalWallet.isPresent()) {
      Wallet wallet = optionalWallet.get();
      CryptoCurrency currency = wallet.getCurrency();
      UTXORPCClient rpcClient = utxoClientFactory.getClient(wallet.getCurrency().getUtxoProvider());
      synchronized (wallet) {
        try {
          FundRawTransactionResponse fundRawTransactionResponse = WalletUtils.fundRawTransaction(request, wallet, currency, rpcClient);
          SignRawTransactionWithWalletResponse singRawTransactionWithWallet = rpcClient.singRawTransactionWithWallet(currency.getUtxoProvider(), wallet.getNodeWalletNameAlias(), fundRawTransactionResponse.getHex());
          String finalTxId = rpcClient.sendRawTransaction(currency.getUtxoProvider(), singRawTransactionWithWallet.getTxHex());
          GetTransactionResponse transaction = getTransaction(wallet.getId(), finalTxId);
          return new SendTransactionResponse(transaction.getTxId(), transaction.getParticipants(), transaction.getBlockchainFee(), currency.getFeeUnit());
        } catch (GenericRpcException ex) {
          throw new WalletException(ex.getErrorKey(), ex.getErrorMessage(), ex.getErrorCode());
        }
      }
    }
    throw new WalletException.WalletNotFound(walletId);
  }

  @Override
  @CaptureSpan
  public GetTransactionResponse getTransaction(String walletId, String txId) {
    Optional<Wallet> optionalWallet = walletRepository.findById(walletId);
    if (optionalWallet.isPresent()) {
      Wallet wallet = optionalWallet.get();
      CryptoCurrency currency = wallet.getCurrency();
      UTXORPCClient rpcClient = utxoClientFactory.getClient(wallet.getCurrency().getUtxoProvider());
      synchronized (wallet) {
        GetTrasactionResponse getTrasactionResponse = rpcClient.getTransaction(new GetTransactionRequest(currency.getUtxoProvider(), wallet.getNodeWalletNameAlias(), txId));
        return new GetTransactionResponse(getTrasactionResponse, currency);
      }
    }
    throw new WalletException.WalletNotFound(walletId);
  }

  @Override
  public List<GetTransactionResponse> getTransactions(String walletId) {
    Optional<Wallet> optionalWallet = walletRepository.findById(walletId);
    if (optionalWallet.isPresent()) {
      Wallet wallet = optionalWallet.get();
      CryptoCurrency currency = wallet.getCurrency();
      UTXORPCClient rpcClient = utxoClientFactory.getClient(wallet.getCurrency().getUtxoProvider());
      synchronized (wallet) {
        List<ListTransactionResponse> listTransactionResponses = rpcClient.listTransactions(
          new ListTransactionRequest(
            currency.getUtxoProvider(),
            wallet.getNodeWalletNameAlias(),
            Integer.MAX_VALUE
          )
        );
        List<GetTransactionResponse> result = new ArrayList<>();
        for (ListTransactionResponse listTransactionResponse : listTransactionResponses) {
          result.add(new GetTransactionResponse(listTransactionResponse, currency));
        }
        return WalletUtils.formatTransactions(result);
      }
    }
    throw new WalletException.WalletNotFound(walletId);
  }

  @Autowired
  public void setWalletRepository(WalletRepository walletRepository) {
    this.walletRepository = walletRepository;
  }

  @Autowired
  public void setMnemonicWords(MnemonicWords mnemonicWords) {
    this.mnemonicWords = mnemonicWords;
  }

  @Autowired
  public void setUTXOClientFactory(UTXOClientFactory utxoClientFactory) {
    this.utxoClientFactory = utxoClientFactory;
  }
}
