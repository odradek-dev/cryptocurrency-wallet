terraform {
  backend "s3" {
    bucket         = "odradek-pay-terraform-state"
    key            = "utxo-wallet/dev/terraform.tfstate"
    region         = "eu-central-1"
    encrypt        = true
    dynamodb_table = "terraform_state_lock_dev_utxo_wallet"
  }
}

resource "aws_dynamodb_table" "terraform_state_lock" {
  name         = "terraform_state_lock_dev_utxo_wallet"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = {
    TerraformStateLock = "true"
  }
}