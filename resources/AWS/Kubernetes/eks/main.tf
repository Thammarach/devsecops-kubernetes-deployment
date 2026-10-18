
provider "aws" {
  region     = var.region
  access_key = var.access_key
  secret_key = var.secret_key
}


data "aws_availability_zones" "available" {
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

locals {
  cluster_name = "${var.name}"
}


# module "vpc" {
#   source  = "terraform-aws-modules/vpc/aws"
#   version = "5.0.0"

#   name = "${var.name}-vpc"

#   cidr = "10.0.0.0/16"
#   azs  = slice(data.aws_availability_zones.available.names, 0, 3)

#   private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
#   public_subnets  = ["10.0.4.0/24", "10.0.5.0/24", "10.0.6.0/24"]

#   enable_nat_gateway   = true
#   single_nat_gateway   = true
#   enable_dns_hostnames = true

#   public_subnet_tags = {
#     "kubernetes.io/cluster/${local.cluster_name}" = "shared"
#     "kubernetes.io/role/elb"                      = 1
#   }

#   private_subnet_tags = {
#     "kubernetes.io/cluster/${local.cluster_name}" = "shared"
#     "kubernetes.io/role/internal-elb"             = 1
#   }
# }

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "19.15.3"
  # version = "21.26.0"

  cluster_name    = local.cluster_name
  cluster_version = "1.36"

  vpc_id                         = var.vpc_id
  subnet_ids                     = ["subnet-0732355901fa7c381","subnet-00a95d58d217d8e33"]
  # vpc_id                         = module.vpc.vpc_id
  # subnet_ids                     = module.vpc.private_subnets
  cluster_endpoint_public_access = true
  # authentication_mode                      = "API_AND_CONFIG_MAP"
  # enable_cluster_creator_admin_permissions = true

  eks_managed_node_group_defaults = {
    ami_type = "AL2023_x86_64_STANDARD" #"AL2_x86_64"
  }

  eks_managed_node_groups = {

    one = {
      name = "${var.name}-group-1"

      instance_types = ["t3.small"]

      min_size     = 1
      max_size     = 4
      desired_size = 2
      max_pods = 110
      
    }

    two = {
      name = "${var.name}-group-2"

      instance_types = ["t3.small"]

      min_size     = 1
      max_size     = 4
      desired_size = 2
      max_pods = 110
    }
  }

}

data "aws_iam_policy" "ebs_csi_policy" {
  arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

module "irsa-ebs-csi" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-assumable-role-with-oidc"
  version = "4.7.0"

  create_role                   = true
  role_name                     = "AmazonEKSTFEBSCSIRole-${module.eks.cluster_name}"
  provider_url                  = module.eks.oidc_provider
  role_policy_arns              = [data.aws_iam_policy.ebs_csi_policy.arn]
  oidc_fully_qualified_subjects = ["system:serviceaccount:kube-system:ebs-csi-controller-sa"]
}

resource "aws_eks_addon" "ebs-csi" {
  cluster_name             = module.eks.cluster_name
  addon_name               = "aws-ebs-csi-driver"
  # addon_version            = "v1.66.0-eksbuild.1" #"v1.20.0-eksbuild.1"
  service_account_role_arn = module.irsa-ebs-csi.iam_role_arn
  tags = {
    "eks_addon" = "ebs-csi"
    "terraform" = "true"
  }
}

module "irsa-lb-controller" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name                              = "eks-training-lb-controller"
  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }
}

output "lb_controller_role_arn" {
  value = module.irsa-lb-controller.iam_role_arn
}

# เปลี่ยน config_map เป็น api_and_config_map
# aws eks update-cluster-config --name eks-training --region ap-southeast-7 \
#   --access-config authenticationMode=API_AND_CONFIG_MAP
# arn:aws:iam::638846798909:root

# aws eks create-access-entry \
#   --cluster-name eks-training --region ap-southeast-7 \
#   --principal-arn arn:aws:iam::638846798909:root

# aws eks associate-access-policy \
#   --cluster-name eks-training --region ap-southeast-7 \
#   --principal-arn arn:aws:iam::638846798909:root \
#   --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy \
#   --access-scope type=cluster

# helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
#   --namespace kube-system \
#   --set clusterName=eks-training \
#   --set region=ap-southeast-7 \
#   --set vpcId=vpc-07f8135e9a5a0d0a6 \
#   --set serviceAccount.create=true \
#   --set serviceAccount.name=aws-load-balancer-controller \
#   --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=arn:aws:iam::638846798909:role/eks-training-lb-controller


aws eks update-nodegroup-config --cluster-name eks-training --region ap-southeast-7 \
  --nodegroup-name eks-training-group-1-20261003104521078700000018 \
  --scaling-config minSize=1,maxSize=2,desiredSize=2