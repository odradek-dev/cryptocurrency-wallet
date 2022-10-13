package com.hacks1ash.crypto.wallet.core.model.request;

import com.hacks1ash.crypto.wallet.core.WalletException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class WalletCreationRequest implements AbstractRequest {

  @Schema(name = "name", required = true, nullable = false, description = "Wallet Alias Name")
  private String name;

  @Schema(name = "currency", required = true, nullable = false, description = "Choose network you want to create wallet for.")
  private String currency;

  @Schema(name = "hdSeed", required = false, nullable = true, description = "Specify 12 mnemonic words to derive addresses from")
  private String hdSeed;

  @Override
  public WalletCreationRequest validate() {
    if (this.name == null || this.name.isEmpty()) {
      throw new WalletException.ParameterRequired("name");
    }

    if (this.currency == null || this.currency.isEmpty()) {
      throw new WalletException.ParameterRequired("currency");
    }

    if (this.hdSeed != null && this.hdSeed.isEmpty()) {
      throw new WalletException.InvalidParameter("hdSeed", "mustn't be blank, should contain 12 words seperated with space");
    }

    return this;
  }


}
