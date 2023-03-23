resource "aws_security_group" "bitcoin_testnet" {
  name   = "BitcoinTestnetNodeSG"
  vpc_id = data.aws_vpc.vpc.id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow SSH from anywhere"
  }

  ingress {
    from_port   = 18332
    to_port     = 18332
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow Bitcoin RPC Connections from anywhere"
  }
  ingress {
    from_port   = 18333
    to_port     = 18333
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow Bitcoin Testnet Network from anywhere"
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "all"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "bitcoin_testnet" {
  ami                         = "ami-0499632f10efc5a62"
  instance_type               = "t3a.medium"
  associate_public_ip_address = true
  ebs_optimized               = true
  key_name                    = aws_key_pair.ssh_key.key_name
  security_groups             = [
    aws_security_group.bitcoin_testnet.id
  ]

  subnet_id = data.aws_subnet.public.id

  monitoring = true

  root_block_device {
    volume_size = 250
    volume_type = "gp3"
  }

  user_data = templatefile("${path.module}/startup.sh", { rpc_auth = local.rpc_auth })

  tags = merge({
    Name = "Bitcoin Testnet Node"
  }, local.tags)
}