package com.hacks1ash.crypto.wallet.core.model.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NewTransaction {

  private String txId;

  private String walletName;

  private int blockHeight;

  private String coin;

}
