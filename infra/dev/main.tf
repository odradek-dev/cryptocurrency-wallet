resource "aws_secretsmanager_secret" "secret" {
  name        = "${local.environment}/utxo-wallet-service"
  description = "UTXO Wallet Credentials"

  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "secret_version" {
  secret_id     = aws_secretsmanager_secret.secret.id
  secret_string = jsonencode({
    database_url_schema  = "mongodb+srv"
    database_user        = "utxo-wallet-user"
    database_pass        = ""
    database_host        = "utxo-wallet-dev.7yogfuw.mongodb.net"
    database_name        = "service"
    database_conn_params = "?authSource=admin&retryWrites=true&w=majority"

    bitcoin_enabled      = "true"
    bitcoin_rpc_host     = "10.100.0.22"
    bitcoin_rpc_port     = "18332"
    bitcoin_rpc_username = local.rpc_username
    bitcoin_rpc_password = local.rpc_password
    bitcoin_network      = "TESTNET"

    #    bitcoin_enabled      = "true"
    #    bitcoin_rpc_host     = aws_instance.bitcoin_testnet.private_ip
    #    bitcoin_rpc_port     = "18332"
    #    bitcoin_rpc_username = local.rpc_username
    #    bitcoin_rpc_password = local.rpc_password
    #    bitcoin_network      = "TESTNET"

    litecoin_enabled      = "false"
    litecoin_rpc_host     = ""
    litecoin_rpc_port     = ""
    litecoin_rpc_username = ""
    litecoin_rpc_password = ""
    litecoin_network      = ""

    kafka_bootstrap_servers = "pkc-zpjg0.eu-central-1.aws.confluent.cloud:9092"
    kafka_jaas_username     = ""
    kafka_jaas_password     = ""
  })
}