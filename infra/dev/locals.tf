locals {
  environment = "dev"
  tags        = {
    Environment = local.environment
    Terraform   = "true"
  }

  vpc_id = "vpc-0befdb582f8fa3ef8"

  rpc_username     = "bitcoin"
  rpc_password     = "uXoTDTtSFuNDFebNXZMU"
  rpc_auth         = "bitcoin:48f2d332d3d81706fbbd56aa3421cb8f\\$d07d6b2e36b2b632ca9398800946200d581ae798f43c1c1bc6c77a6be4144ca9"
  ssh_keypair_name = "${local.environment}-ssh-keypair"
}