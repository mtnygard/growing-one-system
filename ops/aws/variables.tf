variable "aws_region" {
  description = "The AWS Region to use"
  default = "us-east-2"
}

variable "public_key" {
  description = "The public half of an SSH key. Make sure it is in OpenSSH format (it should start with 'ssh', not 'BEGIN PUBLIC KEY')"
  default     = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQD1CiHI4lbyXagg7vr8448Eom+WLn+mt4lxZ5ncgXWVSd78d72PVvhOIMhP73YWGgQi6WRc1IgW2IF1wE2/7lp4dyDtotamGFq3axxMVDq+ZsF/9D1AxGaD40WclZuf4Pn3QiTG7Bd3WDMsqlG4SwP5dD/zS/TYWJK/Y1wCj4kcfSvQOr7J3QhMCT28POfHou4tHT2ARa9+YVoiD2elE8jybYNvjrW4ofVTT+c/BZ3I6OfqA7ewrFI546jjr17nRZg8MeQfOKu9lVFDpNgHjcalX11EpyA33evxsDWeAYbEC8f/emigffNhYG6Lj+5qPohqjj9xnlP1BS18RyGrb0zx mtnygard@spark-gap"
}
