variable "region" {
  description = "AWS region"
  type        = string
  default     = "ap-southeast-7"
}

variable "access_key" {}
variable "secret_key" {}

variable "name" {
  default = "eks-training"
}

variable "vpc_id" {}