resource "tls_private_key" "ssh_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "ssh_key" {
  key_name   = local.ssh_keypair_name
  public_key = tls_private_key.ssh_key.public_key_openssh
}

resource "aws_s3_object" "ssh_key" {
  bucket  = data.aws_s3_bucket.s3.id
  key     = "dev/${local.ssh_keypair_name}.pem"
  content = tls_private_key.ssh_key.private_key_pem
}