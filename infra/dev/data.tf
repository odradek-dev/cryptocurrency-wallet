data "aws_s3_bucket" "s3" {
  bucket = "odradek-pay-terraform-state"
}

data "aws_vpc" "vpc" {
  id = local.vpc_id
}

data "aws_subnet_ids" "public_subnets" {
  vpc_id = data.aws_vpc.vpc.id

  filter {
    name   = "map-public-ip-on-launch"
    values = ["true"]
  }
}

data "aws_subnet" "public" {
  id = element(tolist(data.aws_subnet_ids.public_subnets.ids), 0)
}